package scripts;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.neo4j.logging.FormattedLogProvider;
import org.neo4j.logging.Level;
import org.neo4j.logging.internal.SimpleLogService;

/**
 * @author Michael J. Simons
 */
class SourceWatcherTest {

	public static void main(String...a) throws IOException {
		SourceWatcher sourceWatcher = new SourceWatcher(new SimpleLogService(FormattedLogProvider.withDefaultLogLevel(
			Level.DEBUG).toOutputStream(System.out)), new File("/Users/msimons/Desktop/f"));

		sourceWatcher.run();
	}
}
