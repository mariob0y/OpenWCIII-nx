package com.etheller.warsmash.viewer5.handlers.w3x.simulation.save;

import java.io.IOException;
import java.security.MessageDigest;

/**
 * warsmash-nx console build: stands in for core's EngineBinaryDigest, which hashes the engine's class files
 * (there are none in the .nro). Adds nothing: a console save is checked against the map scripts only.
 */
final class EngineBinaryDigest {
	private EngineBinaryDigest() {
	}

	static void update(final MessageDigest digest, final Class<?>... types) throws IOException {
	}
}
