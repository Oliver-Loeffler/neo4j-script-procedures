package scripts;

import static java.util.stream.Collectors.*;
import static org.neo4j.internal.kernel.api.procs.Neo4jTypes.*;
import static scripts.Scripts.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.internal.kernel.api.procs.DefaultParameterValue;
import org.neo4j.internal.kernel.api.procs.FieldSignature;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.internal.kernel.api.procs.UserFunctionSignature;
import org.neo4j.kernel.api.proc.CallableUserFunction;
import org.neo4j.kernel.api.proc.Context;
import org.neo4j.kernel.impl.util.ValueUtils;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.logging.internal.LogService;
import org.neo4j.values.AnyValue;

/**
 * A wrapper around a script that is to be executed as a function.
 *
 * @author Michael J. Simons
 */
public class ScriptFunction implements CallableUserFunction {

	private final static String UDF_BINDING_NAME = "__udf__";

	private final UserFunctionSignature signature;


	private final String language = Language.JAVASCRIPT.id;
	private final String name;
	private final String sourceCode;

	private transient volatile Source source;

	public ScriptFunction(String name, String sourceCode) {

		this.name = name;
		this.sourceCode = sourceCode;
		this.signature = this.generateSignature();
	}

	Source getSource() {

		Source theSource = this.source;
		if (theSource == null) {
			synchronized (this) {
				theSource = this.source;
				if (theSource == null) {
					try {
						this.source = Source.newBuilder(this.language, this.sourceCode, this.name).build();
						theSource = this.source;
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			}
		}
		return theSource;
	}

	private UserFunctionSignature generateSignature() {

		// DotName
		final QualifiedName qualifiedName = new QualifiedName(Arrays.asList("scripts", "fn"), name);

		// Input
		// Need a context to compute number of arguments
		// TODO Only valid for JS at the moment...
		org.graalvm.polyglot.Context context = PolyglotContext.newInstance();
		Value bindings = context.getBindings(language);

		bindings.putMember(UDF_BINDING_NAME, context.eval(this.getSource()));
		int numberOfArguments = context.eval(language, String.format("%s[\"length\"];", name)).asInt();

		final List<FieldSignature> input = IntStream.range(0, numberOfArguments)
			.mapToObj(i -> FieldSignature.inputField("p" + i, NTAny, DefaultParameterValue.nullValue(NTAny)))
			.collect(toList());

		return new UserFunctionSignature(qualifiedName, input, NTAny, null, new String[0], null, false);
	}


	@Override
	public UserFunctionSignature signature() {
		return signature;
	}

	@Override
	public AnyValue apply(Context ctx, AnyValue[] input) throws ProcedureException {

		try (org.graalvm.polyglot.Context context = PolyglotContext.newInstance()) {

			GraphDatabaseAPI db = ctx.get(Context.DATABASE_API);
			Log log = ctx.get(Context.DEPENDENCY_RESOLVER).resolveDependency(LogService.class)
				.getUserLog(ScriptFunction.class);

			Value bindings = context.getPolyglotBindings();
			bindings.putMember("db", db);
			bindings.putMember("log", log);

			System.out.println(input);
			System.out.println(input[0]);
			Value jsBindings = context.getBindings(Language.JAVASCRIPT.id);
			//	bindings.putMember("arguments", input[0]);
			String code = "function users() { return collection(Polyglot.import(\"db\").findNodes(label('User'))); }";
			try {
				jsBindings.putMember("__udf__", context.eval(Language.JAVASCRIPT.id, code));
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("Noe executing ");
			try {
				Value result = jsBindings.getMember("users").execute();

				//System.out.println(result.asString());
				if (result.isNull()) {
					return null;
				}
				if (result.isHostObject()) {
					return ValueUtils.asAnyValue(result.asHostObject());
				}
				if (result.isNumber()) {
					return ValueUtils.asAnyValue(result.asDouble());
				}
				if (result.isBoolean()) {
					return ValueUtils.asAnyValue(result.asBoolean());
				}

				return ValueUtils.asAnyValue(result.asString());
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
	}
}
