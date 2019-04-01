package scripts;

import static scripts.Scripts.Language.*;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * A helper class for bringing up the polyglot context, including some global helper methods in various languages.
 *
 * @author Michael J. Simons
 */
final class PolyglotContext {

	private final static String[][] JAVASCRIPT_HELPERS = new String[][] {
		{ "label", "s => org.neo4j.graphdb.Label.label(s)" },
		{ "type", "s => org.neo4j.graphdb.RelationshipType.withName(s)" },
		{ "collection", "it => { r=[]; while (it.hasNext()) r.push(it.next());  return Java.to(r); }" }
	};

	static Context newInstance() {
		final Context context = Context.newBuilder().allowAllAccess(true).build();

		final Value helperFunctions = context.getBindings(JAVASCRIPT.id);
		for (String[] javaScriptHelper : JAVASCRIPT_HELPERS) {
			helperFunctions.putMember(javaScriptHelper[0], context.eval(JAVASCRIPT.id, javaScriptHelper[1]));
		}

		return context;
	}

	private PolyglotContext() {
	}

}
