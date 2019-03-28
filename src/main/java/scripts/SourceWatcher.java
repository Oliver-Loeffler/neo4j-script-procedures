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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;

/**
 * Watches the scripts folder and loads scripts found in there.
 *
 * @author Michael J. Simons
 */
class SourceWatcher implements Runnable {

	enum Target {
		FUNCTIONS("functions"),
		PROCEDURES("procedures");

		private final String dirName;

		private Target(String dirName) {
			this.dirName = dirName;
		}

		public String getDirName() {
			return dirName;
		}
	}

	private final Log log;

	private final WatchService watchService;
	private final Path pathToWatch;
	private final Target target;

	SourceWatcher(final LogService logService, final File scriptsDir, final Target target) {

		try {
			this.watchService = FileSystems.getDefault().newWatchService();
			this.pathToWatch = getTargetPath(new File(scriptsDir, target.getDirName()));
			this.pathToWatch.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_MODIFY);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		this.log = logService.getUserLog(SourceWatcher.class);
		this.target = target;
	}

	private static Path getTargetPath(final File targetDir) throws IOException {

		if (targetDir.exists() && targetDir.isFile()) {
			throw new IOException(
				String.format("Target directory %s exists but is a file.", targetDir.getAbsolutePath()));
		}

		if (!targetDir.isDirectory() && targetDir.mkdirs()) {
			throw new IOException(
				String.format("Target directory %s could not be created.", targetDir.getAbsolutePath()));
		}

		return targetDir.toPath();
	}

	@Override
	public void run() {
		try {
			WatchKey watchKey;
			while ((watchKey = watchService.take()) != null) {

				for (WatchEvent<?> event : watchKey.pollEvents()) {
					System.out.println(
						"Event kind:" + event.kind()
							+ ". File affected: " + event.context() + ".");
				}

				if (!watchKey.reset()) {
					break;
				}
			}
		} catch (InterruptedException ie) {
			log.info("Stopped watching for changed scripts.");
		}
	}
}
