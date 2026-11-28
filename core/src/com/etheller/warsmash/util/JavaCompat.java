package com.etheller.warsmash.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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

	/** An unmodifiable list, like List.of. */
	@SafeVarargs
	public static <E> List<E> listOf(final E... elements) {
		if (elements.length == 0) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(elements)));
	}

	/** An unmodifiable copy, like List.copyOf. */
	public static <E> List<E> listCopyOf(final Collection<? extends E> items) {
		return Collections.unmodifiableList(new ArrayList<E>(items));
	}

	/** stream().allMatch(filter). */
	public static <E> boolean allMatch(final Iterable<E> items, final Predicate<? super E> filter) {
		for (final E item : items) {
			if (!filter.test(item)) {
				return false;
			}
		}
		return true;
	}

	/** stream().anyMatch(filter). */
	public static <E> boolean anyMatch(final Iterable<E> items, final Predicate<? super E> filter) {
		for (final E item : items) {
			if (filter.test(item)) {
				return true;
			}
		}
		return false;
	}

	/** stream().filter(filter).count(). */
	public static <E> int count(final Iterable<E> items, final Predicate<? super E> filter) {
		int count = 0;
		for (final E item : items) {
			if (filter.test(item)) {
				count++;
			}
		}
		return count;
	}

	/** stream().filter(filter).toList(): an unmodifiable list. */
	public static <E> List<E> filter(final Iterable<E> items, final Predicate<? super E> filter) {
		final List<E> result = new ArrayList<>();
		for (final E item : items) {
			if (filter.test(item)) {
				result.add(item);
			}
		}
		return Collections.unmodifiableList(result);
	}

	/** stream().filter(filter).findFirst().orElse(fallback). */
	public static <E> E findFirst(final Iterable<E> items, final Predicate<? super E> filter, final E fallback) {
		for (final E item : items) {
			if (filter.test(item)) {
				return item;
			}
		}
		return fallback;
	}

	/** stream().filter(filter).min(order).orElse(null): the first of equal minimums, as the JDK. */
	public static <E> E min(final Iterable<E> items, final Predicate<? super E> filter,
			final Comparator<? super E> order) {
		E best = null;
		for (final E item : items) {
			if (filter.test(item) && ((best == null) || (order.compare(item, best) < 0))) {
				best = item;
			}
		}
		return best;
	}

	/** stream().filter(filter).max(order).orElse(null): the first of equal maximums, as the JDK. */
	public static <E> E max(final Iterable<E> items, final Predicate<? super E> filter,
			final Comparator<? super E> order) {
		E best = null;
		for (final E item : items) {
			if (filter.test(item) && ((best == null) || (order.compare(best, item) < 0))) {
				best = item;
			}
		}
		return best;
	}

	/** String.strip: removes leading and trailing Character.isWhitespace characters. */
	public static String strip(final String text) {
		int start = 0;
		int end = text.length();
		while ((start < end) && Character.isWhitespace(text.charAt(start))) {
			start++;
		}
		while ((end > start) && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(start, end);
	}

	/** String.lines().toList(): split at \n, \r or \r\n, with no empty last line. */
	public static List<String> lines(final String text) {
		final List<String> lines = new ArrayList<>();
		int start = 0;
		final int length = text.length();
		for (int i = 0; i < length; i++) {
			final char c = text.charAt(i);
			if ((c == '\n') || (c == '\r')) {
				lines.add(text.substring(start, i));
				if ((c == '\r') && ((i + 1) < length) && (text.charAt(i + 1) == '\n')) {
					i++;
				}
				start = i + 1;
			}
		}
		if (start < length) {
			lines.add(text.substring(start));
		}
		return Collections.unmodifiableList(lines);
	}

	/** InputStream.readNBytes(length): up to length bytes, fewer only at the end of the stream. */
	public static byte[] readNBytes(final InputStream stream, final int length) throws IOException {
		final byte[] buffer = new byte[length];
		int total = 0;
		while (total < length) {
			final int read = stream.read(buffer, total, length - total);
			if (read < 0) {
				break;
			}
			total += read;
		}
		return total == length ? buffer : Arrays.copyOf(buffer, total);
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
