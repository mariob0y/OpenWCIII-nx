package com.etheller.warsmash.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

/**
 * warsmash-nx: the few Java 9+ library calls OpenWCIII uses, written for Java 8. The Switch 2 build compiles
 * core with -source 8 against Clearwing's runtime, which has no Float.isFinite, Collection.removeIf,
 * Set.of, streams, InputStream.readAllBytes/transferTo, Boolean.getBoolean or Base64. Same results as the
 * JDK methods on the PC.
 */
public final class JavaCompat {
	private JavaCompat() {
	}

	public static boolean isFinite(final float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}

	public static boolean isFinite(final double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	/** Boolean.getBoolean: true when the system property is "true", ignoring case. */
	public static boolean getBoolean(final String property) {
		final String value = System.getProperty(property);
		return (value != null) && value.equalsIgnoreCase("true");
	}

	/** An unmodifiable set, like Set.of (without its duplicate check). */
	@SafeVarargs
	public static <E> Set<E> setOf(final E... elements) {
		final Set<E> set = new HashSet<>();
		for (final E element : elements) {
			set.add(element);
		}
		return Collections.unmodifiableSet(set);
	}

	/** Collection.removeIf for any iterable whose iterator supports remove. */
	public static <E> boolean removeIf(final Iterable<E> items, final Predicate<? super E> filter) {
		boolean removed = false;
		for (final Iterator<E> it = items.iterator(); it.hasNext();) {
			if (filter.test(it.next())) {
				it.remove();
				removed = true;
			}
		}
		return removed;
	}

	public static byte[] readAllBytes(final InputStream stream) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buffer = new byte[8192];
		int read;
		while ((read = stream.read(buffer)) > 0) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	/** Feeds the rest of the stream to the digest (stream.transferTo(new DigestOutputStream(...))). */
	public static void digest(final InputStream stream, final MessageDigest digest) throws IOException {
		final byte[] buffer = new byte[8192];
		int read;
		while ((read = stream.read(buffer)) > 0) {
			digest.update(buffer, 0, read);
		}
	}

	private static final char[] BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
			.toCharArray();

	/** Base64.getUrlEncoder().withoutPadding().encodeToString(bytes). */
	public static String base64UrlNoPadding(final byte[] bytes) {
		final StringBuilder out = new StringBuilder(((bytes.length * 4) + 2) / 3);
		int i = 0;
		for (; (i + 2) < bytes.length; i += 3) {
			final int n = ((bytes[i] & 0xFF) << 16) | ((bytes[i + 1] & 0xFF) << 8) | (bytes[i + 2] & 0xFF);
			out.append(BASE64_URL[(n >>> 18) & 63]).append(BASE64_URL[(n >>> 12) & 63])
					.append(BASE64_URL[(n >>> 6) & 63]).append(BASE64_URL[n & 63]);
		}
		final int left = bytes.length - i;
		if (left == 1) {
			final int n = (bytes[i] & 0xFF) << 16;
			out.append(BASE64_URL[(n >>> 18) & 63]).append(BASE64_URL[(n >>> 12) & 63]);
		}
		else if (left == 2) {
			final int n = ((bytes[i] & 0xFF) << 16) | ((bytes[i + 1] & 0xFF) << 8);
			out.append(BASE64_URL[(n >>> 18) & 63]).append(BASE64_URL[(n >>> 12) & 63])
					.append(BASE64_URL[(n >>> 6) & 63]);
		}
		return out.toString();
	}
}
