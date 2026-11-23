package java.lang.invoke;

// warsmash-nx javac-stubs: compile-time symbols only. Clearwing has no java.lang.invoke (its
// transpiler turns lambda and method-reference bootstraps into C++ by name), but javac needs these
// classes to compile a lambda for -target 8 against the console runtime. Never transpiled.
public final class LambdaMetafactory {
	public static final int FLAG_SERIALIZABLE = 1, FLAG_MARKERS = 2, FLAG_BRIDGES = 4;

	public static CallSite metafactory(MethodHandles.Lookup caller, String invokedName, MethodType invokedType,
			MethodType samMethodType, MethodHandle implMethod, MethodType instantiatedMethodType)
			throws LambdaConversionException {
		throw new UnsupportedOperationException();
	}

	public static CallSite altMetafactory(MethodHandles.Lookup caller, String invokedName, MethodType invokedType,
			Object... args) throws LambdaConversionException {
		throw new UnsupportedOperationException();
	}
}
