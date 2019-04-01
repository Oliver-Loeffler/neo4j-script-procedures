package scripts;

import org.graalvm.polyglot.*;

public class Runner {
	public static void main(String...a) {
		try (Context ctx = Context.newBuilder().allowAllAccess(true).build()) {
            Value jsBindings = ctx.getBindings("js");
            jsBindings.putMember("foo", ctx.eval("js", "function poef(x,y) {console.log(x)};"));
			System.out.println(">> " + jsBindings.getMember("foo").getMetaObject());
			for(String key : jsBindings.getMemberKeys())
				System.out.println(key + " = " + jsBindings.getMember(key));
			System.out.println("---");
			for(String s : jsBindings.getMember("poef").getMetaObject().getMemberKeys())
			System.out.println(s);

			System.out.println("_--");
			ctx.eval("js", "console.log(Object.getOwnPropertyNames(poef));");
			ctx.eval("js", "console.log(poef[\"length\"]);");
			System.out.println("---");

//			System.out.println(jsBindings.getMember("poef").getMetaObject().getMember("description"));
			System.out.println(ctx.eval("js", "function poef(x,y) {console.log(x)};").getMember("length"));
            /*
		  ctx.eval("js", "print('Hello JavaScript!');");
		  ctx.eval("R", "print('Hello R!');");
		  ctx.eval("ruby", "puts 'Hello Ruby!'");
		  ctx.eval("python", "print('Hello Python!')");
                */
		}
	}
}