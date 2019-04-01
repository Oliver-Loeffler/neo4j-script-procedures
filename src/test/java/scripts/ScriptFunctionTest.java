package scripts;

import static org.assertj.core.api.Assertions.*;
import static org.neo4j.driver.v1.Values.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilders;

/**
 * @author Michael J. Simons
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ScriptFunctionTest {
	private static final Config driverConfig = Config.build().withoutEncryption().toConfig();

	private ServerControls embeddedDatabaseServer;

	@BeforeAll
	void initializeNeo4j() {

		this.embeddedDatabaseServer = TestServerBuilders.newInProcessBuilder()
			.withConfig("dbms.security.procedures.unrestricted", "scripts.*")
			.withConfig("scripts.enable_watcher", "false")
			.withProcedure(Scripts.class)
			.withFunction(Scripts.class)
			.newServer();
	}

	@Test
	public void shouldAllowCreatingAndRunningJSProcedures() throws Throwable {
		try (Driver driver = GraphDatabase.driver(this.embeddedDatabaseServer.boltURI(), driverConfig);
			Session session = driver.session())
		{
			long nodeId = session.run("CREATE (p:User {name:'Brookreson'}) RETURN id(p)")
				.single()
				.get(0).asLong();

			// Brute force register stuff, will be replaced by the watcher facility,
			session.run("RETURN scripts.test('listNodes', 'function listNodes(l) { return collection(db.findNodes(label(l))); }')");

			StatementResult result = session.run("RETURN scripts.fn.listNodes('User')");
			System.out.println(result.single());
		}
	}
}
