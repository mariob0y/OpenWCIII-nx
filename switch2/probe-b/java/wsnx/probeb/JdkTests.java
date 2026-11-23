package wsnx.probeb;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;

/**
 * Probe B step "jdk" (Warsmash W1, 2026-11-20): the JDK classes and methods added to Clearwing's
 * runtime for Warsmash (runtime-additions/, jdk-overlay/, patches/clearwing-3.1.2-jdk1*.edits.py).
 * Every R line must match the PC JVM. Paths are printed with '/' (the PC is Windows); files are
 * written only below the probe's own folder.
 */
final class JdkTests {
	private JdkTests() {
	}

	static void run(final ProbeB.Out out) throws Exception {
		paths(out);
		files(out);
		javaIoFile(out);
		numbersAndStrings(out);
		random(out);
		checksums(out);
		bundle(out);
		reflection(out);
		strictMath(out);
		mathAndParsing(out);
		networking(out);
	}

	// ---- jdk1h: Math.round/toRadians/toDegrees/nextAfter, parseInt/parseLong, File.toString ----

	static String parse(final String s, final int radix, final boolean asLong) {
		try {
			return asLong ? Long.toString(Long.parseLong(s, radix)) : Integer.toString(Integer.parseInt(s, radix));
		}
		catch (final NumberFormatException e) {
			return "NFE(" + e.getMessage() + ")";
		}
	}

	static void mathAndParsing(final ProbeB.Out out) {
		out.line("R jdk round " + Math.round(0.49999999999999994) + " " + Math.round(4503599627370497.0) + " "
				+ Math.round(-4503599627370497.0) + " " + Math.round(-2.5) + " " + Math.round(9.3e18) + " "
				+ Math.round(Double.NaN) + " | " + Math.round(0.49999997f) + " " + Math.round(8388609f) + " "
				+ Math.round(-8388609f) + " " + Math.round(-2.5f) + " " + Math.round(3e9f) + " " + Math.round(Float.NaN));
		long angles = 17;
		final Random random = new Random(99);
		for (int i = 0; i < 100000; i++) {
			final double x = (random.nextDouble() - 0.5) * 2000;
			angles = (angles * 31) + Double.doubleToLongBits(Math.toRadians(x)) + Double.doubleToLongBits(Math.toDegrees(x));
		}
		out.line("R jdk angles toRadians(90) " + Double.doubleToLongBits(Math.toRadians(90)) + " toDegrees(PI) "
				+ Math.toDegrees(Math.PI) + " hash " + Long.toHexString(angles) + " nextAfter " + Math.nextAfter(1.0, 2.0) + " "
				+ Math.nextAfter(0.0, -1.0) + " " + Math.nextAfter(1.0f, 0.0) + " " + Math.nextAfter(5.0, 5.0));
		final StringBuilder parsed = new StringBuilder();
		for (final String s : new String[] { "+5", "-+5", "-", "", "2147483648", "-2147483648", "+9223372036854775807",
				"-9223372036854775808", "9223372036854775808", "ff" }) {
			parsed.append(s).append('=').append(parse(s, 10, false)).append('/').append(parse(s, 10, true)).append(' ');
		}
		parsed.append("ff/16=").append(parse("ff", 16, false)).append(" fffffffff/16=").append(parse("fffffffff", 16, false))
				.append(" radix1=").append(parse("5", 1, true)).append(" null=").append(parse(null, 10, false));
		String unsigned;
		try {
			unsigned = Long.toString(Long.parseUnsignedLong("12345678901234567x9"));
		}
		catch (final NumberFormatException e) {
			unsigned = e.getMessage();
		}
		out.line("R jdk parse " + parsed + " unsigned " + unsigned);
		// jdk1i: two significant digits in exponent form (values where JDK 17 is not quirky)
		final StringBuilder printed = new StringBuilder();
		for (final double d : new double[] { Double.MIN_VALUE, -Double.MIN_VALUE, 3 * Double.MIN_VALUE, 1e10, -2.5e-7, 1e21,
				123456789.0, 1e-3, 9999999.0, 1e7, 0.1 + 0.2, Math.PI * 1e20, 2.2250738585072014E-308 }) {
			printed.append(d).append(' ');
		}
		for (final float f : new float[] { Float.MIN_VALUE, -Float.MIN_VALUE, 3 * Float.MIN_VALUE, 1e10f, 1e-5f, 0.1f + 0.2f,
				3.4028235e38f }) {
			printed.append(f).append(' ');
		}
		out.line("R jdk tostring " + printed.toString().trim());
		out.line("R jdk file-string " + (new File("data/units") + " | " + ("dir: " + new File("a", "b.txt"))).replace('\\', '/'));
	}

	private static String p(final Path path) {
		return path == null ? "null" : path.toString().replace('\\', '/');
	}

	private static String name(final Throwable t) {
		return t.getClass().getName();
	}

	// ---- java.nio.file.Path (relative paths: the same rules on Windows and Unix) ----

	static void paths(final ProbeB.Out out) {
		final Path a = Paths.get("data", "units", "human");
		out.line("R jdk path " + p(a) + " parent " + p(a.getParent()) + " name " + p(a.getFileName()) + " count "
				+ a.getNameCount() + " name0 " + p(a.getName(0)) + " sub " + p(a.subpath(1, 3)) + " root "
				+ p(a.getRoot()) + " absolute " + a.isAbsolute());
		out.line("R jdk path resolve " + p(a.resolve("footman.mdx")) + " | " + p(a.resolve(Paths.get(""))) + " | "
				+ p(Paths.get("").resolve("x")) + " | sibling " + p(a.resolveSibling("orc")));
		out.line("R jdk path relativize " + p(Paths.get("data/units").relativize(Paths.get("data/units/human/x.mdx")))
				+ " | " + p(Paths.get("a/b/c").relativize(Paths.get("a/d"))) + " | "
				+ p(Paths.get("a/b").relativize(Paths.get("a/b"))) + " | " + p(Paths.get("").relativize(Paths.get("q/r"))));
		out.line("R jdk path normalize " + p(Paths.get("a/./b/../c").normalize()) + " | "
				+ p(Paths.get("../x/../y").normalize()) + " | " + p(Paths.get("a/b/../..").normalize()) + " | "
				+ p(Paths.get("./a").normalize()));
		out.line("R jdk path tests " + Paths.get("a/b/c").startsWith("a/b") + " " + Paths.get("a/b/c").startsWith("a/b/c/d")
				+ " " + Paths.get("a/b/c").startsWith("a/bb") + " " + Paths.get("a/b/c").endsWith("b/c") + " "
				+ Paths.get("a/b/c").endsWith("c") + " " + Paths.get("a/b/c").endsWith("bc"));
		final StringBuilder names = new StringBuilder();
		for (final Path part : Paths.get("w/x/y")) {
			names.append('[').append(p(part)).append(']');
		}
		final Path empty = Paths.get("");
		out.line("R jdk path iterate " + names + " empty '" + p(empty) + "' name '" + p(empty.getFileName()) + "' count "
				+ empty.getNameCount() + " parent " + p(empty.getParent()) + " joined " + p(Paths.get("a", "", "b"))
				+ " equal " + Paths.get("a/b").equals(Paths.get("a//b/")) + " compare "
				+ Integer.signum(Paths.get("a/b").compareTo(Paths.get("a/c"))) + " file " + p(a.toFile().toPath()));
	}

	// ---- java.nio.file.Files and FileChannel on the card ----

	static void files(final ProbeB.Out out) throws IOException {
		final Path dir = Paths.get(out.dir(), "jdk-test");
		Files.createDirectories(dir);
		final Path bin = dir.resolve("a.bin");
		final Path text = dir.resolve("b.txt");
		final Path missing = dir.resolve("missing.bin");
		final byte[] data = new byte[100000];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) ((i * 7) ^ (i >>> 5));
		}
		Files.deleteIfExists(bin);
		Files.deleteIfExists(text);
		Files.write(bin, data);
		final byte[] back = Files.readAllBytes(bin);
		out.line("R jdk files write+read " + back.length + " equal " + Arrays.equals(data, back) + " size " + Files.size(bin)
				+ " exists " + Files.exists(bin) + Files.exists(missing) + " regular " + Files.isRegularFile(bin)
				+ Files.isRegularFile(dir) + Files.isRegularFile(missing) + " dir " + Files.isDirectory(dir)
				+ Files.isDirectory(bin) + Files.isDirectory(missing));

		final List<String> errors = new ArrayList<>();
		try {
			Files.newInputStream(missing).close();
			errors.add("none");
		}
		catch (final IOException e) {
			errors.add(name(e));
		}
		try {
			FileChannel.open(missing, StandardOpenOption.READ).close();
			errors.add("none");
		}
		catch (final IOException e) {
			errors.add(name(e));
		}
		try {
			FileChannel.open(bin, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).close();
			errors.add("none");
		}
		catch (final IOException e) {
			errors.add(name(e));
		}
		try {
			Files.copy(new ByteArrayInputStream(new byte[3]), bin);
			errors.add("none");
		}
		catch (final IOException e) {
			errors.add(name(e));
		}
		out.line("R jdk files errors " + errors);

		try (FileChannel channel = FileChannel.open(bin, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING)) {
			final long afterTruncate = channel.size();
			channel.write(ByteBuffer.wrap(data, 0, 300));
			final long written = channel.size();
			channel.truncate(120);
			channel.force(true);
			out.line("R jdk channel truncated-at-open " + afterTruncate + " written " + written + " truncated "
					+ channel.size() + " position " + channel.position());
		}
		try (FileChannel channel = FileChannel.open(bin, StandardOpenOption.READ)) {
			final ByteBuffer buffer = ByteBuffer.allocate(200);
			final int n = channel.read(buffer);
			buffer.flip();
			boolean same = n == 120;
			for (int i = 0; (i < n) && same; i++) {
				same = buffer.get(i) == data[i];
			}
			out.line("R jdk channel read " + n + " same " + same + " eof " + channel.read(ByteBuffer.allocate(4)));
		}
		final long copied = Files.copy(new ByteArrayInputStream(data, 0, 777), bin, StandardCopyOption.REPLACE_EXISTING);

		Files.write(text, "first line\nsecond: ü\n\nfourth".getBytes(StandardCharsets.UTF_8));
		final List<String> lines = Files.readAllLines(text);
		out.line("R jdk files copy " + copied + " size " + Files.size(bin) + " lines " + lines.size() + " [" + lines.get(0)
				+ "] utf8-line " + lines.get(1).equals("second: ü") + " empty-line " + lines.get(2).isEmpty() + " ["
				+ lines.get(3) + "]");

		final Set<String> all = new TreeSet<>(), bins = new TreeSet<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (final Path entry : stream) {
				all.add(p(entry.getFileName()));
			}
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{bin,dat}")) {
			for (final Path entry : stream) {
				bins.add(p(entry.getFileName()));
			}
		}
		Files.delete(bin);
		Files.delete(text);
		final boolean again = Files.deleteIfExists(bin);
		Files.delete(dir);
		out.line("R jdk files list " + all + " glob " + bins + " deleted-again " + again + " dir-gone " + !Files.exists(dir));
	}

	// ---- java.io.File stubs that were wrong ----

	static void javaIoFile(final ProbeB.Out out) throws IOException {
		final File dir = new File(out.dir());
		final File fresh = new File(dir, "jdk-new.tmp");
		fresh.delete();
		final boolean created = fresh.createNewFile();
		final boolean createdAgain = fresh.createNewFile();
		final boolean isFile = fresh.isFile();
		fresh.delete();
		final Set<File> set = new HashSet<>(Arrays.asList(new File("x/y"), new File("x/y"), new File("x/z")));
		final List<File> sorted = new ArrayList<>(Arrays.asList(new File("b"), new File("c"), new File("a")));
		Collections.sort(sorted);
		out.line("R jdk file created " + created + " again " + createdAgain + " isFile " + isFile + " missing-isFile "
				+ new File(dir, "nope.bin").isFile() + " dir-isFile " + dir.isFile() + " set " + set.size()
				+ " equals " + new File("x/y").equals(new File("x/y")) + " sorted " + sorted);
	}

	// ---- Integer/Long/Byte, String, Character ----

	static void numbersAndStrings(final ProbeB.Out out) {
		final List<String> parsed = new ArrayList<>();
		for (final String s : new String[] { "4294967295", "-1", "4294967296", "", "123" }) {
			try {
				parsed.add(Integer.toString(Integer.parseUnsignedInt(s)));
			}
			catch (final NumberFormatException e) {
				parsed.add(e.getMessage());
			}
		}
		for (final String s : new String[] { "18446744073709551615", "18446744073709551616", "99999999999999999999" }) {
			try {
				parsed.add(Long.toString(Long.parseUnsignedLong(s)));
			}
			catch (final NumberFormatException e) {
				parsed.add(e.getMessage());
			}
		}
		parsed.add(Long.toString(Long.parseUnsignedLong("ffffffffffffffff", 16)));
		out.line("R jdk numbers bytes " + Integer.BYTES + "/" + Long.BYTES + "/" + Byte.BYTES + " unsigned "
				+ Integer.toUnsignedString(-1) + " " + Integer.toUnsignedString(-16, 16) + " parsed " + parsed);

		// Clearwing's Long.MIN_VALUE was one too high (jdk1g): the constant javac inlines, and the
		// runtime code that uses it.
		String minParsed;
		try {
			minParsed = Long.toString(Long.parseLong("-9223372036854775808"));
		}
		catch (final NumberFormatException e) {
			minParsed = "NumberFormatException " + e.getMessage();
		}
		final long shifted = 1L << 63;
		out.line("R jdk long-min " + Long.MIN_VALUE + " is-1<<63 " + (Long.MIN_VALUE == shifted) + " parse " + minParsed
				+ " toString " + Long.toString(shifted) + " abs " + Math.abs(shifted) + " compareUnsigned "
				+ Long.compareUnsigned(-1L, 1L) + " " + Long.compareUnsigned(shifted, Long.MAX_VALUE) + " "
				+ Long.compareUnsigned(3L, 5L) + " int-min " + Integer.MIN_VALUE + " "
				+ (Integer.MIN_VALUE == (1 << 31)));
		final List<String> withNull = new ArrayList<>(Arrays.asList("p", null, "q"));
		final String utf = "Grunt ü ж";
		out.line("R jdk strings " + String.join(",", "a", null, "c") + " | " + String.join("-", withNull) + " | blank "
				+ "  \t\n".isBlank() + " " + " x ".isBlank() + " " + "".isBlank() + " | sb-empty "
				+ new StringBuilder().isEmpty() + " " + new StringBuilder("z").isEmpty() + " | alpha "
				+ Character.isAlphabetic('x') + " " + Character.isAlphabetic('1') + " | utf8 "
				+ utf.getBytes(StandardCharsets.UTF_8).length + " "
				+ new String(utf.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).equals(utf));
	}

	// ---- Random.nextFloat(bound): Java 17, used by Warsmash's simulation ----

	static void random(final ProbeB.Out out) {
		final Random random = new Random(12345);
		long hash = 17;
		final StringBuilder first = new StringBuilder();
		for (int i = 0; i < 2000; i++) {
			final float bound = (i % 3) == 0 ? 1f : (i % 3) == 1 ? 7.3f : (float) (Math.PI * 2);
			final float value = random.nextFloat(bound);
			hash = (hash * 31) + Float.floatToIntBits(value);
			if (i < 3) {
				first.append(Float.floatToIntBits(value)).append(' ');
			}
		}
		String bad;
		try {
			random.nextFloat(0f);
			bad = "no exception";
		}
		catch (final IllegalArgumentException e) {
			bad = e.getMessage();
		}
		out.line("R jdk random nextFloat(bound) first " + first + "hash " + Long.toHexString(hash) + " zero-bound: " + bad);
	}

	// ---- CRC32C and Checksum.update(ByteBuffer / byte[]) ----

	static void checksums(final ProbeB.Out out) {
		final CRC32C c = new CRC32C();
		c.update("123456789".getBytes(StandardCharsets.US_ASCII));
		final long check = c.getValue();
		final byte[] data = new byte[1 << 20];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) ((i * 131) ^ (i >>> 3));
		}
		c.reset();
		c.update(data, 0, data.length);
		final long whole = c.getValue();
		c.reset();
		c.update(ByteBuffer.wrap(data, 0, 1000));
		c.update(ByteBuffer.wrap(data, 1000, data.length - 1000));
		final long buffers = c.getValue();
		final ByteBuffer direct = ByteBuffer.allocateDirect(5000);
		direct.put(data, 0, 5000).flip();
		c.reset();
		c.update(direct);
		final long fromDirect = c.getValue();
		final CRC32 plain = new CRC32();
		plain.update(data);
		out.line("R jdk crc32c check " + Long.toHexString(check) + " 1MB " + Long.toHexString(whole) + " buffers "
				+ Long.toHexString(buffers) + " direct " + Long.toHexString(fromDirect) + " remaining " + direct.remaining()
				+ " crc32 " + Long.toHexString(plain.getValue()));
	}

	// ---- PropertyResourceBundle: Warsmash's WorldEditStrings ----

	static void bundle(final ProbeB.Out out) throws IOException {
		final PropertyResourceBundle bundle = new PropertyResourceBundle(new StringReader(
				"KEY=Value\nWESTRING_A=\"quoted text\"\n# comment\nspaced : colon value\nmulti=one \\\n    two\n"));
		String missing;
		try {
			missing = bundle.getString("NOPE");
		}
		catch (final MissingResourceException e) {
			missing = name(e) + " key " + e.getKey();
		}
		final List<String> keys = new ArrayList<>(Collections.list(bundle.getKeys()));
		Collections.sort(keys);
		out.line("R jdk bundle " + bundle.getString("KEY") + " | " + bundle.getString("WESTRING_A") + " | "
				+ bundle.getString("spaced") + " | " + bundle.getString("multi") + " | missing: " + missing + " | keys "
				+ keys);
	}

	// ---- reflective exceptions: hierarchy and Class.newInstance unwrapping ----

	public static final class Thrower {
		public Thrower() {
			throw new IllegalStateException("from the constructor");
		}

		public static void fail() {
			throw new UnsupportedOperationException("from the method");
		}
	}

	static void reflection(final ProbeB.Out out) throws Exception {
		String forName;
		try {
			Class.forName("does.not.Exist");
			forName = "found";
		}
		catch (final ReflectiveOperationException e) {
			forName = "ReflectiveOperationException caught: " + name(e);
		}
		String newInstance;
		try {
			Thrower.class.newInstance();
			newInstance = "no exception";
		}
		catch (final IllegalStateException e) {
			newInstance = name(e) + ": " + e.getMessage();
		}
		catch (final Throwable t) {
			newInstance = "other: " + name(t);
		}
		String invoke;
		try {
			final Method fail = Thrower.class.getMethod("fail");
			fail.invoke(null);
			invoke = "no exception";
		}
		catch (final InvocationTargetException e) {
			invoke = name(e) + " cause " + name(e.getCause());
		}
		out.line("R jdk reflection forName: " + forName + " | newInstance: " + newInstance + " | invoke: " + invoke);
	}

	// ---- StrictMath (delegates to Math on the console) ----

	static void strictMath(final ProbeB.Out out) {
		out.line("R jdk strictmath exact " + StrictMath.sqrt(2) + " " + StrictMath.ceil(2.1) + " " + StrictMath.floor(-2.1)
				+ " " + StrictMath.round(2.5) + " " + StrictMath.round(-2.5f) + " " + (StrictMath.toRadians(180) == StrictMath.PI)
				+ " " + StrictMath.abs(-3) + " " + StrictMath.min(1.5, -0.0) + " " + StrictMath.max(-0.0f, 0.0f));
		long hash = 0;
		for (int i = 0; i < 1000; i++) {
			final double x = (i - 500) * 0.013;
			hash = (hash * 31) + Double.doubleToLongBits(StrictMath.sin(x)) + Double.doubleToLongBits(StrictMath.cos(x))
					+ Double.doubleToLongBits(StrictMath.atan(x)) + Double.doubleToLongBits(StrictMath.pow(1.1, x));
		}
		out.line("I jdk strictmath trig hash " + Long.toHexString(hash) + " (the console's Math may differ by an ulp)");
	}

	// ---- networking: stubs on the console ----

	static void networking(final ProbeB.Out out) {
		String result;
		try {
			final SocketChannel channel = SocketChannel.open();
			channel.close();
			result = "opened";
		}
		catch (final IOException e) {
			result = name(e) + ": " + e.getMessage();
		}
		out.line("I jdk SocketChannel.open: " + result);
	}

	static InputStream unused() {
		return System.in; // compiles against the console runtime (Warsmash's tools read it)
	}
}
