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
 * @author Michael Hunger
 * @author Michael J. Simons
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ScriptsTest {

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

            session.run("CALL scripts.function({name}, {code})", parameters("name", "users", "code", "function users() { return collection(db.findNodes(label('User'))); }"));

            StatementResult result = session.run("CALL scripts.run('users',null)");
            Value value = result.single().get("value");
            System.out.println(value.asObject());
            assertThat(value.asNode().id()).isEqualTo(nodeId);
            result = session.run("RETURN scripts.run('users') AS value");
            value = result.single().get("value");
            System.out.println(value.asObject());
            assertThat(value.get(0).asNode().id()).isEqualTo(nodeId);
        }
    }
}
