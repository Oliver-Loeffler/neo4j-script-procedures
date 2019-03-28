package scripts;

import static org.neo4j.internal.kernel.api.procs.Neo4jTypes.*;
import static scripts.Scripts.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.polyglot.Value;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.internal.kernel.api.procs.DefaultParameterValue;
import org.neo4j.internal.kernel.api.procs.FieldSignature;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.internal.kernel.api.procs.UserFunctionSignature;
import org.neo4j.kernel.api.proc.CallableUserFunction;
import org.neo4j.kernel.api.proc.Context;
import org.neo4j.kernel.impl.api.state.ValuesContainer;
import org.neo4j.kernel.impl.util.DefaultValueMapper;
import org.neo4j.kernel.impl.util.ValueUtils;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.ArrayValue;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.VirtualValues;

/**
 * A wrapper around a script that is to be executed as a function.
 *
 * @author Michael J. Simons
 */
public class ScriptFunction implements CallableUserFunction {

	private final UserFunctionSignature signature;

	public ScriptFunction(String name) {

		final QualifiedName qualifiedName = new QualifiedName(Arrays.asList("scripts", "fn"), name);
		final List<FieldSignature> input = Collections
			.singletonList(FieldSignature.inputField("params", NTList(NTAny), DefaultParameterValue
				.ntList(Collections.emptyList(), NTAny)));
		this.signature = new UserFunctionSignature(qualifiedName, input, NTAny,
			null, new String[0], null, false);
	}

	@Override
	public UserFunctionSignature signature() {
		return signature;
	}

	@Override
	public AnyValue apply(Context ctx, AnyValue[] input) throws ProcedureException {

		try (org.graalvm.polyglot.Context context = polyglotContext()) {

			GraphDatabaseAPI db = ctx.get(Context.DATABASE_API);
			Log log = ctx.get(Context.DEPENDENCY_RESOLVER).resolveDependency(LogService.class)
				.getUserLog(ScriptFunction.class);

			Value bindings = context.getPolyglotBindings();
			bindings.putMember("db", db);
			bindings.putMember("log", log);
			System.out.println(input);
			System.out.println(input[0]);
			//	bindings.putMember("arguments", input[0]);
			String code = "function users() { return collection(db.findNodes(label('User'))); }";
			Value result = context.eval(Scripts.Language.JAVASCRIPT.id,
				String.format("() => {"
					+ "  var db = Polyglot.import(\"db\"); "
					+ "  var log = Polyglot.import(\"log\"); "
					+ "  return (%s).apply(this, Polyglot.import(\"arguments\"));"
					+ "}", code))
				.execute();
			// System.out.println(result.asString());
			if (result.isNull()) {
				return null;
			}
			if (result.isHostObject()) {
				return VirtualValues.list(AnyValue.)
			}
			if (result.isNumber()) {
				return ValueUtils.asAnyValue(result.asDouble());
			}
			if (result.isBoolean()) {
				return ValueUtils.asAnyValue(result.asBoolean());
			}

			return ValueUtils.asAnyValue(result.asString());
		}
	}
}
