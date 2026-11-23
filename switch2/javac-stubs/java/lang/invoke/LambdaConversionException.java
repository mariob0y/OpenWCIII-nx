package java.lang.invoke;

// warsmash-nx javac-stubs: compile-time symbols only. Clearwing has no java.lang.invoke (its
// transpiler turns lambda and method-reference bootstraps into C++ by name), but javac needs these
// classes to compile a lambda for -target 8 against the console runtime. Never transpiled.
public class LambdaConversionException extends Exception {
}
