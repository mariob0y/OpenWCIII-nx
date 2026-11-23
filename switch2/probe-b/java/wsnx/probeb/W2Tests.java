package wsnx.probeb;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import wsnx.probeb.gson.GsonModel;

/**
 * Probe B step "w2" (Warsmash W2, 2026-11-21): what changed in Clearwing for the first transpile and
 * C++ compile of Warsmash, plus Gson as Warsmash's ability builder uses it.
 * - cpp1: primitive conversions emitted as C casts (inside larger expressions), lone surrogates and
 *   octal escapes in string literals;
 * - cpp1b/cpp1c: String bytes <-> chars as JDK 17's UTF-8 coder (malformed input -> U+FFFD);
 * - jdk2*: the JDK methods Gson and ANTLR call;
 * - Gson: plain fields, enums, RuntimeTypeAdapterFactory, List and Map fields.
 * Each part catches its own failure, so one failure does not hide the rest.
 */
final class W2Tests {
	private W2Tests() {
	}

	interface Part {
		void run(ProbeB.Out out) throws Exception;
	}

	static void run(final ProbeB.Out out) {
		part(out, "casts", W2Tests::casts);
		part(out, "literals", W2Tests::literals);
		part(out, "decode", W2Tests::decode);
		part(out, "encode", W2Tests::encode);
		part(out, "jdk2", W2Tests::jdk2);
		part(out, "calendar", W2Tests::calendar);
		part(out, "gson-plain", W2Tests::gsonPlain);
		part(out, "gson-enum", W2Tests::gsonEnum);
		part(out, "gson-poly", W2Tests::gsonPoly);
		part(out, "gson-list", W2Tests::gsonList);
		part(out, "gson-shapes", W2Tests::gsonShapes);
		part(out, "gson-map", W2Tests::gsonMap);
	}

	static void part(final ProbeB.Out out, final String name, final Part part) {
		try {
			part.run(out);
		}
		catch (final Throwable t) {
			out.line("R w2 " + name + " FAILED: " + t);
		}
	}

	// ---- cpp1: primitive conversions inside expressions (C casts; CUnit hit the most vexing parse) ----

	// Non-final so javac cannot fold anything.
	static int[] castInts = { 0x12345687, -1, 0x7fffff80, 200, -129, 65535, 0x10000, Integer.MIN_VALUE };
	static long[] castLongs = { 0x123456789abcdefL, -1L, Long.MIN_VALUE, (1L << 40) | 0xff, 0xffffffffL };
	static float[] castFloats = { 1.5e10f, -2.75f, 300.9f, Float.NaN, -1e30f, 65535.9f };
	static double[] castDoubles = { 1e300, -0.5, 65536.75, 4294967296.5, Double.NEGATIVE_INFINITY };
	static int mask = 0x0f;

	// Bits, not toString: JDK 17 prints some exponent-form values with extra digits (plan.md, jdk1i).
	static String fb(final float f) {
		return Integer.toHexString(Float.floatToRawIntBits(f));
	}

	static String db(final double d) {
		return Long.toHexString(Double.doubleToRawLongBits(d));
	}

	static void casts(final ProbeB.Out out) {
		final StringBuilder s = new StringBuilder();
		for (final int x : castInts) {
			final int a = (byte) x & mask;
			final int b = (short) x >> 3;
			final int c = (char) x + 1;
			final long d = (long) x * 3;
			final float e = (float) x / 7;
			final double f = (double) x * 0.5;
			final int g = (byte) (x >>> 4) | (short) x;
			final int h = (char) (byte) x;
			final int i = (x & 0xff) == 0 ? (short) x : (byte) x & (char) x;
			s.append(a).append(' ').append(b).append(' ').append(c).append(' ').append(d).append(' ').append(fb(e)).append(' ')
					.append(db(f)).append(' ').append(g).append(' ').append(h).append(' ').append(i).append(" | ");
		}
		for (final long l : castLongs) {
			s.append((int) l & mask).append(' ').append((byte) l).append(' ').append((short) (l >> 8)).append(' ')
					.append((int) (char) l).append(' ').append(fb((float) l)).append(' ').append(db((double) l)).append(' ')
					.append((int) (l >>> 33) ^ (byte) l).append(" | ");
		}
		for (final float f : castFloats) {
			s.append((int) f & 0xff).append(' ').append((long) f).append(' ').append((byte) f).append(' ').append((int) (char) f)
					.append(' ').append((short) f).append(' ').append(db((double) f * 2)).append(" | ");
		}
		for (final double d : castDoubles) {
			s.append((int) d).append(' ').append((long) d).append(' ').append(fb((float) d)).append(' ').append((byte) d).append(' ')
					.append((int) (char) d).append(" | ");
		}
		out.line("R w2 casts " + s.toString().trim());
	}

	// ---- cpp1: string literals (lone surrogates, octal escapes, NUL) ----

	static final String PACKED = "\3\1\2\udfe6\1￿\4¦\0\0"; // JFlex style: (count, value) pairs

	static String codes(final String s) {
		final StringBuilder b = new StringBuilder().append(s.length()).append(':');
		for (int i = 0; i < s.length(); i++) {
			b.append(Integer.toHexString(s.charAt(i))).append(i + 1 < s.length() ? "," : "");
		}
		return b.toString();
	}

	static void literals(final ProbeB.Out out) {
		final String[] literals = { "\udfe6", "a\ud800b", "\ud800", "x\udc00", "😀", "😀\ud83d",
				"\u0000x\u0000", "¦b", "¦¦7f", "é中", "\u0080߿ࠀ￿", "￿￾",
				"tab\tnl\n\"q\"\\", "\ud800𐀀" };
		final StringBuilder s = new StringBuilder();
		for (final String literal : literals) {
			s.append(codes(literal)).append(' ');
		}
		int unpacked = 0;
		long hash = 0;
		for (int i = 0; i < PACKED.length(); i += 2) {
			for (int n = PACKED.charAt(i); n > 0; n--) {
				unpacked++;
				hash = (hash * 31) + PACKED.charAt(i + 1);
			}
		}
		out.line("R w2 literals " + s + "| packed " + unpacked + " " + Long.toHexString(hash) + " | equal "
				+ "\udfe6".equals(new String(new char[] { (char) 0xdfe6 })) + " "
				+ "é中".equals(new String(new char[] { (char) 0xe9, (char) 0x4e2d })) + " hash "
				+ "é中\udfe6".hashCode() + " codepoint " + Integer.toHexString("😀".codePointAt(0)));
	}

	// ---- cpp1b/cpp1c: bytes -> chars as JDK 17 (malformed input -> U+FFFD) ----

	static byte[] bytes(final int... values) {
		final byte[] b = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			b[i] = (byte) values[i];
		}
		return b;
	}

	static void decode(final ProbeB.Out out) throws Exception {
		final byte[][] inputs = { bytes(0xC0, 0x80), bytes(0xED, 0xA0, 0x80), bytes(0xE0, 0x80, 0x80), bytes(0xE2, 0x82, 0x41),
				bytes(0xE2, 0x82), bytes(0xF0, 0x9F, 0x98), bytes(0xF0, 0x9F, 0x98, 0x80), bytes(0xF4, 0x90, 0x80, 0x80),
				bytes(0xF8, 0x88, 0x80, 0x80, 0x80), bytes(0xFF, 0xFE, 0x41), bytes(0x63, 0x61, 0x66, 0xE9, 0x20, 0x6E),
				bytes(0xC3, 0xA9), bytes(0xE9), bytes(0x80), bytes(0xF0, 0x90, 0x41), bytes(0xED, 0x9F, 0xBF),
				bytes(0xE0, 0xA0), bytes(0xF1, 0x80, 0x80, 0xE1, 0x80, 0xC2) };
		final StringBuilder s = new StringBuilder();
		for (final byte[] input : inputs) {
			s.append(codes(new String(input, StandardCharsets.UTF_8))).append(' ');
		}
		final byte[] padded = bytes(0x41, 0xE2, 0x82, 0xAC, 0xC0, 0x42);
		final String middle = new String(padded, 1, 4, StandardCharsets.UTF_8);
		final String named = new String(bytes(0xC0, 0x80, 0xE2, 0x82, 0xAC), "UTF-8");
		// A WTS/JASS file saved as Latin-1, read as UTF-8 line by line (what Warsmash does).
		final byte[] latin1 = bytes(0x54, 0x52, 0x49, 0x47, 0x53, 0x54, 0x52, 0x5F, 0x30, 0x30, 0x31, 0x0D, 0x0A, 0x7B, 0x0A,
				0x43, 0x61, 0x66, 0xE9, 0x20, 0x4D, 0xFC, 0x6E, 0x63, 0x68, 0x65, 0x6E, 0x0A, 0x7D);
		final List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(latin1), StandardCharsets.UTF_8))) {
			for (String line; (line = reader.readLine()) != null;) {
				lines.add(codes(line));
			}
		}
		out.line("R w2 decode " + s + "| offset " + codes(middle) + " | named " + codes(named) + " | reader " + lines);
	}

	// ---- cpp1b: chars -> bytes (a surrogate without its partner becomes '?') ----

	static String hex(final byte[] b) {
		final StringBuilder s = new StringBuilder();
		for (final byte x : b) {
			s.append(Integer.toHexString((x & 0xff) | 0x100).substring(1));
		}
		return s.toString();
	}

	static void encode(final ProbeB.Out out) {
		final String[] inputs = { "\ud800x", "\udc00", "😀", "a\ud83d", "\udc00\ud800", "é中\u0000",
				new String(new char[] { (char) 0xdbff, (char) 0xdfff }) };
		final StringBuilder s = new StringBuilder();
		for (final String input : inputs) {
			s.append(hex(input.getBytes(StandardCharsets.UTF_8))).append(' ');
		}
		final String roundTrip = new String("😀 é \ud800".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
		out.line("R w2 encode " + s + "| round-trip " + codes(roundTrip));
	}

	// ---- jdk2: methods Gson and ANTLR call ----

	static class Base {
	}

	static class Sub extends Base {
	}

	static void jdk2(final ProbeB.Out out) {
		final AtomicIntegerArray ints = new AtomicIntegerArray(3);
		ints.set(1, 5);
		final String atomicInts = ints.decrementAndGet(1) + " " + ints.get(1) + " " + ints.getAndDecrement(1) + " " + ints.get(1)
				+ " " + ints.incrementAndGet(2) + " " + ints.addAndGet(0, -9);
		final AtomicLongArray longs = new AtomicLongArray(new long[] { 1L << 40, -1 });
		final String atomicLongs = longs.incrementAndGet(0) + " " + longs.decrementAndGet(1) + " " + longs.getAndAdd(0, 10) + " "
				+ longs.get(0) + " " + longs.compareAndSet(1, -2, 7) + " " + longs.get(1) + " " + longs.length();

		final StringBuilder codePoints = new StringBuilder().appendCodePoint('A').appendCodePoint(0x1F600).appendCodePoint(0xE9);
		String badCodePoint;
		try {
			new StringBuilder().appendCodePoint(0x110000);
			badCodePoint = "no exception";
		}
		catch (final IllegalArgumentException e) {
			badCodePoint = e.getMessage();
		}

		final ConcurrentSkipListMap<String, Integer> map = new ConcurrentSkipListMap<>();
		for (final String k : new String[] { "peon", "grunt", "archer", "footman", "ghoul" }) {
			map.put(k, k.length());
		}
		final String skipList = map + " first " + map.firstKey() + " last " + map.lastKey() + " ceiling(h) " + map.ceilingKey("h")
				+ " floor(h) " + map.floorKey("h") + " head(g) " + map.headMap("g") + " putIfAbsent "
				+ map.putIfAbsent("peon", 9) + " desc " + map.descendingMap().keySet() + " poll " + map.pollFirstEntry() + " size "
				+ map.size();

		final StringBuilder start = new StringBuilder(), part = new StringBuilder();
		for (int c = 0; c < 128; c += 4) {
			int ns = 0, np = 0;
			for (int k = 0; k < 4; k++) {
				ns |= Character.isUnicodeIdentifierStart(c + k) ? 1 << k : 0;
				np |= Character.isUnicodeIdentifierPart((char) (c + k)) ? 1 << k : 0;
			}
			start.append(Integer.toHexString(ns));
			part.append(Integer.toHexString(np));
		}

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final PrintStream print = new PrintStream(bytes);
		print.format("%d-%s-%.2f", 3, "x", 1.5).format("|%5s|", "ab");
		print.flush();

		final Class<?> enclosing = Sub.class.getEnclosingClass();
		out.line("R w2 jdk2 atomic-int " + atomicInts + " | atomic-long " + atomicLongs + " | codepoints " + codes(codePoints.toString())
				+ " bad: " + badCodePoint + " | skiplist " + skipList + " | ident " + start + " " + part + " | format "
				+ new String(bytes.toByteArray(), StandardCharsets.UTF_8) + " | enclosing "
				+ (enclosing == null ? "null" : enclosing.getName()) + " " + W2Tests.class.getEnclosingClass() + " "
				+ int[].class.getEnclosingClass() + " | generic-super " + Sub.class.getGenericSuperclass() + " params "
				+ Sub.class.getTypeParameters().length);
	}

	static void calendar(final ProbeB.Out out) {
		final GregorianCalendar c = new GregorianCalendar(2026, Calendar.NOVEMBER, 21, 13, 5, 7);
		final String fields = c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH) + "-" + c.get(Calendar.DAY_OF_MONTH) + " "
				+ c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE) + ":" + c.get(Calendar.SECOND) + "."
				+ c.get(Calendar.MILLISECOND) + " dow " + c.get(Calendar.DAY_OF_WEEK);
		final GregorianCalendar lenient = new GregorianCalendar(2026, 13, 1);
		lenient.getTime();
		final String rolled = lenient.get(Calendar.YEAR) + "-" + lenient.get(Calendar.MONTH);
		final GregorianCalendar strict = new GregorianCalendar(2026, 0, 1);
		strict.setLenient(false);
		strict.set(Calendar.MONTH, 13);
		String strictResult;
		try {
			strict.getTime();
			strictResult = "no exception";
		}
		catch (final IllegalArgumentException e) {
			strictResult = e.getClass().getName();
		}
		out.line("R w2 calendar " + fields + " | lenient month 13 -> " + rolled + " " + new GregorianCalendar().isLenient()
				+ " | strict month 13 -> " + strictResult + " " + strict.isLenient());
	}

	// ---- Gson, as AbilityBuilderGsonBuilder uses it ----

	static Gson gson() {
		final RuntimeTypeAdapterFactory<GsonModel.Shape> shapes = RuntimeTypeAdapterFactory.of(GsonModel.Shape.class, "type")
				.registerSubtype(GsonModel.Circle.class, "circle").registerSubtype(GsonModel.Square.class, "square");
		return new GsonBuilder().registerTypeAdapterFactory(shapes).create();
	}

	static void gsonPlain(final ProbeB.Out out) {
		final Gson gson = gson();
		final GsonModel.Plain plain = gson.fromJson("{\"name\":\"Grunt\",\"count\":3,\"speed\":1.5,\"ratio\":0.1,\"flag\":true,"
				+ "\"big\":9007199254740993,\"ints\":[4,-5,6],\"unknown\":{\"x\":[1]},\"skipped\":99,"
				+ "\"child\":{\"name\":\"Peon\",\"count\":-1}}", GsonModel.Plain.class);
		out.line("R w2 gson-plain " + GsonModel.describe(plain) + " | " + gson.toJson(plain));
	}

	static void gsonEnum(final ProbeB.Out out) {
		final Gson gson = gson();
		final GsonModel.WithEnum value = gson.fromJson("{\"kind\":\"MOVE\",\"kinds\":[\"CAST\",\"ATTACK\",\"nope\"]}",
				GsonModel.WithEnum.class);
		final StringBuilder kinds = new StringBuilder();
		for (final GsonModel.Kind k : value.kinds) {
			kinds.append(k).append(' ');
		}
		out.line("R w2 gson-enum " + value.kind + " [" + kinds.toString().trim() + "] | " + gson.toJson(value));
	}

	static void gsonPoly(final ProbeB.Out out) {
		final Gson gson = gson();
		final GsonModel.Holder holder = gson.fromJson(
				"{\"main\":{\"type\":\"circle\",\"radius\":2.5},\"other\":{\"type\":\"square\",\"side\":4,\"label\":\"s\"}}",
				GsonModel.Holder.class);
		String unknown;
		try {
			gson.fromJson("{\"main\":{\"type\":\"hexagon\"}}", GsonModel.Holder.class);
			unknown = "no exception";
		}
		catch (final RuntimeException e) {
			unknown = e.getClass().getName();
		}
		out.line("R w2 gson-poly " + GsonModel.describe(holder.main) + " " + GsonModel.describe(holder.other) + " | "
				+ gson.toJson(holder) + " | unknown label: " + unknown);
	}

	static void gsonList(final ProbeB.Out out) {
		final Gson gson = gson();
		final GsonModel.WithList value = gson.fromJson("{\"tags\":[\"a\",\"b\",\"c\"]}", GsonModel.WithList.class);
		out.line("R w2 gson-list " + GsonModel.describeList(value.tags) + " | " + gson.toJson(value));
	}

	static void gsonShapes(final ProbeB.Out out) {
		final Gson gson = gson();
		final GsonModel.WithShapes value = gson.fromJson(
				"{\"shapes\":[{\"type\":\"circle\",\"radius\":1},{\"type\":\"square\",\"side\":2},{\"type\":\"circle\",\"radius\":3}]}",
				GsonModel.WithShapes.class);
		out.line("R w2 gson-shapes " + GsonModel.describeShapes(value.shapes) + " | " + gson.toJson(value));
	}

	static void gsonMap(final ProbeB.Out out) {
		final Gson gson = gson();
		final GsonModel.WithMap value = gson.fromJson("{\"counts\":{\"b\":2,\"a\":1,\"c\":3}}", GsonModel.WithMap.class);
		out.line("R w2 gson-map " + GsonModel.describeMap(value.counts) + " | " + gson.toJson(value));
	}
}
