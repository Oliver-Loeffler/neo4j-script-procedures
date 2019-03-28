/*
 * Copyright (c) 2016-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scripts;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.extension.ExtensionType;
import org.neo4j.kernel.extension.KernelExtensionFactory;
import org.neo4j.kernel.impl.spi.KernelContext;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;
import org.neo4j.scheduler.Group;
import org.neo4j.scheduler.JobHandle;
import org.neo4j.scheduler.JobScheduler;

/**
 * Configures and starts the {@link SourceWatcher}.
 *
 * @author Michael J. Simons
 */
public class ScriptsKernelExtensionFactory extends KernelExtensionFactory<ScriptsKernelExtensionFactory.Dependencies> {

	public static final String EXTENSION_KEY = "Scripts";
	private static final String CONFIG_KEY_PLUGINS = "dbms.directories.plugins";
	private static final String CONFIG_KEY_SCRIPTS_ENABLE_WATCHER = "scripts.enable_watcher";

	public interface Dependencies {

		GraphDatabaseAPI graphDatabaseApi();

		JobScheduler jobScheduler();

		LogService logService();
	}

	public ScriptsKernelExtensionFactory() {
		super(ExtensionType.DATABASE, EXTENSION_KEY);
	}

	@Override
	public Lifecycle newInstance(KernelContext kernelContext, Dependencies dependencies) {
		return new ScriptsLifecycle(
			dependencies.logService(),
			dependencies.graphDatabaseApi(),
			dependencies.jobScheduler());
	}

	static class ScriptsLifecycle extends LifecycleAdapter {

		private final Log log;

		private final GraphDatabaseAPI databaseAPI;

		private final LogService logService;

		private final JobScheduler scheduler;

		private final List<JobHandle> jobHandles = new ArrayList<>();

		ScriptsLifecycle(LogService logService, GraphDatabaseAPI databaseAPI, JobScheduler scheduler) {
			this.log = logService.getUserLog(ScriptsLifecycle.class);

			this.logService = logService;
			this.databaseAPI = databaseAPI;
			this.scheduler = scheduler;
		}

		@Override
		public void start() {

			final Config config = databaseAPI
				.getDependencyResolver()
				.resolveDependency(Config.class, DependencyResolver.SelectionStrategy.ONLY);

			if (!config.getRaw(CONFIG_KEY_SCRIPTS_ENABLE_WATCHER).map(Boolean::valueOf).orElse(true)) {
				log.warn("Scripts watcher is disabled.");
				return;
			}

			final File scriptsDir = config.getValue(CONFIG_KEY_PLUGINS).map(File.class::cast)
				.map(plugins -> new File(plugins, "scripts"))
				.filter(scripts -> scripts.isDirectory() || scripts.mkdir())
				.orElseThrow(() -> new RuntimeException("Invalid scripts directory!"));

			log.info("Configuring script folder at %s", scriptsDir);

			SourceWatcher sourceWatcher = new SourceWatcher(logService, scriptsDir, SourceWatcher.Target.FUNCTIONS);
			this.jobHandles.add(this.scheduler.schedule(Group.FILE_IO_HELPER, sourceWatcher));

			sourceWatcher = new SourceWatcher(logService, scriptsDir, SourceWatcher.Target.PROCEDURES);
			this.jobHandles.add(this.scheduler.schedule(Group.FILE_IO_HELPER, sourceWatcher));
		}

		@Override
		public void stop() {

			this.jobHandles.forEach(handle -> handle.cancel(true));
		}
	}

}
