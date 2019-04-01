package scripts;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.script.ScriptException;

import org.graalvm.polyglot.Value;
import org.neo4j.kernel.impl.core.EmbeddedProxySPI;
import org.neo4j.kernel.impl.core.GraphProperties;
import org.neo4j.kernel.impl.core.GraphPropertiesProxy;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.UserFunction;

public class Scripts {

    enum Language {
        JAVASCRIPT("js");

        public final String id;

        Language(String id) {
            this.id = id;
        }
    }

    private static String PREFIX = "script.function.";
    public static final Object[] NO_OBJECTS = new Object[0];

    @Context
    public GraphDatabaseAPI db;

    @Context
    public Log log;

    private static final GraphPropertiesProxy NO_GRAPH_PROPERTIES = new GraphPropertiesProxy(null);
    private static GraphProperties graphProperties = NO_GRAPH_PROPERTIES;

    private GraphProperties graphProperties() {
        if (graphProperties == NO_GRAPH_PROPERTIES)
            graphProperties = this.db.getDependencyResolver().resolveDependency(EmbeddedProxySPI.class).newGraphPropertiesProxy();
        return graphProperties;
    }

    @UserFunction("scripts.run")
    public Object runFunction(@Name("name") String name, @Name(value="params",defaultValue="[]") List<Object> params)  {
        try (org.graalvm.polyglot.Context context = PolyglotContext.newInstance()) {
            String code = (String) graphProperties().getProperty(PREFIX + name, null);
            if (code == null)
                throw new RuntimeException("Function " + name + " not defined, use CALL function('name','code') ");

            Value bindings = context.getPolyglotBindings();
            bindings.putMember("db", db);
            bindings.putMember("log", log);
            bindings.putMember("arguments", params == null ? NO_OBJECTS : params.toArray());

            Value result = context.eval(Language.JAVASCRIPT.id,
                String.format("() => {"
                    + "  var db = Polyglot.import(\"db\"); "
                    + "  var log = Polyglot.import(\"log\"); "
                    + "  return (%s).apply(this, Polyglot.import(\"arguments\"));"
                    + "}", code))
                .execute();

            if (result.isNull()) {
                return null;
            }
            if (result.isHostObject()) {
                return result.asHostObject();
            }
            if (result.isNumber()) {
                return result.asDouble();
            }
            if (result.isBoolean()) {
                return result.asBoolean();
            }

            return result.asString();
        }
    }

    @Procedure
    public Stream<Result> run(@Name("name") String name, @Name(value="params",defaultValue="[]") List<Object> params) throws ScriptException, NoSuchMethodException {
        Object value = runFunction(name, params);
        if (value instanceof Object[]) {
             return Stream.of((Object[]) value).map(Result::new);
         }
         if (value instanceof Iterable) {
             return StreamSupport.stream(((Iterable<?>)value).spliterator(),false).map(Result::new);
         }
         return Stream.of(new Result(value));
    }

    @Procedure(mode=Mode.WRITE)
    public Stream<Result> function(@Name("name") String name, @Name("code") String code) throws ScriptException {
        try (org.graalvm.polyglot.Context context = PolyglotContext.newInstance()) {
            context.eval(Language.JAVASCRIPT.id, "var tmp = " + code);
        }
        GraphProperties props = graphProperties();
        boolean replaced = props.hasProperty(PREFIX + name);
        props.setProperty(PREFIX + name, code);
        return Stream.of(new Result(String.format("%s Function %s", replaced ? "Updated" : "Added", name)));
    }

    @Procedure(mode=Mode.WRITE)
    public Stream<Result> delete(@Name("name") String name) {
        GraphProperties props = graphProperties();
        props.removeProperty(PREFIX + name);
        return Stream.of(new Result(String.format("Function '%s' removed", name)));
    }

    @Procedure
    public Stream<Result> list() {
        return StreamSupport.stream(graphProperties.getPropertyKeys().spliterator(), false).filter(s -> s.startsWith(PREFIX )).map(Result::new);
    }

    @UserFunction("scripts.test")
    public boolean registerFunction(@Name("name") String name, @Name("source") String source) {

        try {
            Procedures procedures = db.getDependencyResolver().resolveDependency(Procedures.class);
            procedures.register(new ScriptFunction(name, source));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static class Result {
        public Object value;

        public Result(Object value) {
            this.value = value;
        }

    }

}
