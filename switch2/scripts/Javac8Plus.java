import java.lang.reflect.Field;
import javax.tools.ToolProvider;

/**
 * javac for the console build that takes OpenWCIII's newer syntax at {@code -source 8 -target 8}.
 *
 * <p>Upstream OpenWCIII is written in Java 17 (var, instanceof patterns, switch rules and
 * expressions). The console needs Java 8 class files compiled against Clearwing's runtime, and javac
 * allows {@code -bootclasspath} only for target 8. These features are syntax only: javac lowers them
 * to ordinary Java 8 bytecode (casts, local variables, tableswitch/lookupswitch), with no
 * invokedynamic and no new class-file attributes. So the minimum source level of just those
 * features is lowered to 8 here, and javac runs unchanged in this JVM. This keeps the upstream
 * sources as they are, so later merges from OpenWCIII do not conflict over Java 8 rewrites.
 *
 * <p>Not enabled: records (their equals/hashCode/toString use the ObjectMethods bootstrap, which
 * Clearwing cannot transpile), sealed classes and pattern switches. Those still have to be written
 * as Java 8 in the sources.
 *
 * <p>Run: {@code java --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED Javac8Plus.java
 * <javac arguments>}
 */
public class Javac8Plus {
	private static final String[] FEATURES = { "LOCAL_VARIABLE_TYPE_INFERENCE", "VAR_SYNTAX_IMPLICIT_LAMBDAS",
			"PATTERN_MATCHING_IN_INSTANCEOF", "REIFIABLE_TYPES_INSTANCEOF", "SWITCH_RULE", "SWITCH_EXPRESSION",
			"SWITCH_MULTIPLE_CASE_LABELS", "TEXT_BLOCKS", "EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES",
			"DIAMOND_WITH_ANONYMOUS_CLASS_CREATION" };

	public static void main(final String[] args) throws Exception {
		final Class<?> source = Class.forName("com.sun.tools.javac.code.Source");
		final Class<?> feature = Class.forName("com.sun.tools.javac.code.Source$Feature");
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final Object jdk8 = Enum.valueOf((Class) source, "JDK8");
		final Field minLevel = feature.getDeclaredField("minLevel");
		minLevel.setAccessible(true);
		for (final String name : FEATURES) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			final Object f = Enum.valueOf((Class) feature, name);
			minLevel.set(f, jdk8);
		}
		System.exit(ToolProvider.getSystemJavaCompiler().run(null, null, null, args));
	}
}
