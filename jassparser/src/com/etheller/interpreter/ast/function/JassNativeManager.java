package com.etheller.interpreter.ast.function;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.interpreter.ast.definition.JassParameterDefinition;
import com.etheller.interpreter.ast.scope.Scope;
import com.etheller.interpreter.ast.type.JassTypeToken;
import com.etheller.interpreter.ast.value.JassType;

public class JassNativeManager {
	private final Map<String, JassFunction> nameToNativeCode;
	private final Set<String> registeredNativeNames = new HashSet<>();

	public JassNativeManager() {
		this.nameToNativeCode = new HashMap<>();
	}

	/** warsmash-nx: whether a native body is registered (and not yet bound to a declaration). */
	public boolean hasNative(final String name) {
		return this.nameToNativeCode.containsKey(name);
	}

	/** warsmash-nx: a registered native body that no script declared (e.g. LoadScriptFile), or null. */
	public JassFunction getUnboundNative(final String name) {
		return this.nameToNativeCode.get(name);
	}

	public void createNative(final String name, final JassFunction nativeCode) {
		this.nameToNativeCode.put(name, nativeCode);
	}

	public JassFunction getNative(final String name) {
		return this.nameToNativeCode.get(name);
	}

	public void registerNativeCode(final int lineNo, final String sourceFile, final String name,
			final List<JassParameterDefinition> parameterDefinitions, final JassTypeToken returnTypeToken,
			final Scope globals) {
		if (this.registeredNativeNames.contains(name)) {
			System.err.println("Native already registered: " + name);
			return;
//			throw new RuntimeException("Native already registered: " + name);
		}
		final JassFunction nativeCode = this.nameToNativeCode.remove(name);
		final List<JassParameter> parameters = JassParameterDefinition.resolve(parameterDefinitions, globals);
		final JassType returnType = returnTypeToken.resolve(globals);
		globals.defineFunction(lineNo, sourceFile, name,
				new NativeJassFunction(parameters, returnType, name, nativeCode));
		this.registeredNativeNames.add(name);
	}

	public void checkUnregisteredNatives() {
		// TODO maybe do this later
	}
}
