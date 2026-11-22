package com.etheller.warsmash.viewer5.handlers.w3x.simulation.save;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Adds the engine's class files to a mission checkpoint fingerprint, so a save made by another build of the
 * engine is refused. (Moved out of MissionCheckpoint by warsmash-nx: the Switch 2 build has no class files
 * and uses a stand-in that adds nothing; its saves are tied to the build by the .nro instead.)
 */
final class EngineBinaryDigest {
	private EngineBinaryDigest() {
	}

	static void update(final MessageDigest digest, final Class<?>... types) throws IOException {
		try {
			final Set<Path> sources = new HashSet<>();
			for (final Class<?> type : types) {
				final Path binary = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
				if (!sources.add(binary)) {
					continue;
				}
				if (Files.isDirectory(binary)) {
					try (java.util.stream.Stream<Path> files = Files.walk(binary)) {
						for (final Path file : files.filter(path -> path.toString().endsWith(".class")).sorted()
								.toList()) {
							digest.update(binary.relativize(file).toString()
									.getBytes(java.nio.charset.StandardCharsets.UTF_8));
							digest.update(Files.readAllBytes(file));
						}
					}
				}
				else {
					try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(binary.toFile())) {
						for (final java.util.zip.ZipEntry entry : archive.stream()
								.filter(item -> item.getName().endsWith(".class"))
								.sorted(java.util.Comparator.comparing(java.util.zip.ZipEntry::getName)).toList()) {
							digest.update(entry.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
							try (InputStream stream = archive.getInputStream(entry)) {
								com.etheller.warsmash.util.JavaCompat.digest(stream, digest);
							}
						}
					}
				}
			}
		}
		catch (final java.net.URISyntaxException error) {
			throw new IOException(error);
		}
	}
}
