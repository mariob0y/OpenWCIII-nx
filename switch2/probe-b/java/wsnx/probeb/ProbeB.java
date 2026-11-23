package wsnx.probeb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Probe B: does Java converted to C++ by Clearwing VM run on the Switch 2?
 *
 * The same program runs on the PC's JVM (the reference) and on the console. Lines starting with
 * "R " are results that must be identical on both; "T " lines are timings to compare; "I " lines
 * are information. Each test is announced with a synced "step:" line first, so if the console
 * crashes the log says where. Written for Java 8 (Clearwing's runtime is Java 7 + Java 8
 * language features), plus the JDK additions in JdkTests; build-java.sh compiles it.
 */
public final class ProbeB {
	static Out out;

	public static void main(final String[] args) {
		out = Out.create();
		out.line("==== probe B run on " + out.platform() + " ====");
		out.line("I keys: R = must match the PC JVM, T = timing, I = info");
		final String environment = out.environment();
		if (environment != null) {
			out.line("I " + environment);
		}
		if (out.offerSkip()) {
			out.line("I + pressed at start: tests skipped");
			out.mark("probe B done");
			out.finish();
			return;
		}

		final String[] names = { "basics", "floatmath", "exceptions", "collections", "threads", "reflection", "gc", "bench",
				"fileio", "jdk", "w2" }; // file I/O and the Warsmash JDK additions last: the likeliest to crash (our natives)
		for (final String name : names) {
			if (out.poll()) {
				out.line("I + pressed: remaining tests skipped");
				break;
			}
			out.mark("step: " + name);
			final long start = System.nanoTime();
			try {
				run(name);
				out.line("T " + name + " total " + ms(start) + " ms");
			}
			catch (final Throwable t) {
				out.line("R " + name + " FAILED: " + t);
			}
		}
		out.mark("probe B done");
		out.finish();
	}

	static void run(final String name) throws Exception {
		if ("basics".equals(name)) {
			basics();
		}
		else if ("floatmath".equals(name)) {
			floatMath();
		}
		else if ("exceptions".equals(name)) {
			exceptions();
		}
		else if ("collections".equals(name)) {
			collections();
		}
		else if ("threads".equals(name)) {
			threads();
		}
		else if ("reflection".equals(name)) {
			reflection();
		}
		else if ("fileio".equals(name)) {
			fileIo();
		}
		else if ("gc".equals(name)) {
			gc();
		}
		else if ("bench".equals(name)) {
			bench();
		}
		else if ("jdk".equals(name)) {
			JdkTests.run(out);
		}
		else if ("w2".equals(name)) {
			W2Tests.run(out);
		}
	}

	static long ms(final long startNanos) {
		return (System.nanoTime() - startNanos) / 1000000L;
	}

	// ---- basics: strings and number formatting ------------------------------------------------

	static void basics() {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			sb.append(i).append(',');
		}
		out.line("R basics stringbuilder length " + sb.length() + " hash " + sb.toString().hashCode());
		out.line("R basics concat " + ("a" + 1 + 'b' + 2L + 3.5f + true + null));
		out.line("R basics parse " + Integer.parseInt("-12345") + " " + Long.parseLong("9876543210") + " "
				+ Float.parseFloat("3.25") + " " + Double.parseDouble("-1.5e-3") + " " + Integer.toHexString(-1));
		out.line("R basics floattostring " + 0.1f + " " + 1.0e10f + " " + 1.0f / 3 + " " + 1e-5 + " " + 100.0 + " "
				+ Float.MIN_VALUE + " " + Double.MAX_VALUE);
		out.line("R basics format [" + String.format("%d|%5d|%-4s|%.3f|%x|%s", 42, 7, "ab", Math.PI, 255, "z") + "]");
		final String s = "  Hello, Warsmash  ";
		out.line("R basics string ops [" + s.trim().toUpperCase() + "][" + s.indexOf("Wars") + "]["
				+ s.replace('a', '4') + "][" + s.split(",").length + "][" + "abc".compareTo("abd") + "]["
				+ String.valueOf(new char[] { 'x', 'y' }) + "]");
		out.line("R basics unicode " + "é中".length() + " " + (int) "é中".charAt(1));
	}

	// ---- floatmath: Java's float rules versus C++ ----------------------------------------------

	// Non-final so javac cannot fold the conversions at compile time.
	static float[] specials = { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 3.0e9f, -3.0e9f, 3.99f,
			-3.99f, -0.5f, 300.7f, 70000.2f };
	static double[] specialsD = { Double.NaN, Double.POSITIVE_INFINITY, -1e30, 1e19, -0.99 };
	static int[] ints = { Integer.MIN_VALUE, -1, 0, 5, 7 };
	static long[] longs = { Long.MIN_VALUE, -1L, 0L };

	static void floatMath() {
		final StringBuilder casts = new StringBuilder();
		for (final float f : specials) {
			casts.append((int) f).append('/').append((long) f).append('/').append((short) f).append('/')
					.append((byte) f).append('/').append((int) (char) f).append(' ');
		}
		for (final double d : specialsD) {
			casts.append((int) d).append('/').append((long) d).append(' ');
		}
		out.line("R floatmath casts " + casts);

		final StringBuilder div = new StringBuilder();
		div.append(ints[0] / ints[1]).append(' ').append(ints[0] % ints[1]).append(' ');
		div.append(longs[0] / longs[1]).append(' ').append(longs[0] % longs[1]).append(' ');
		div.append(-7 / ints[4] * ints[3]).append(' ').append(-7 % ints[3]).append(' ');
		try {
			div.append(ints[3] / ints[2]);
		}
		catch (final RuntimeException e) { // ArithmeticException on the JVM
			div.append("int/0 throws ").append(e.getClass().getSimpleName());
		}
		div.append(' ');
		try {
			div.append(longs[1] % longs[2]);
		}
		catch (final RuntimeException e) {
			div.append("long%0 throws ").append(e.getClass().getSimpleName());
		}
		div.append(' ').append(specials[5] / ints[2]).append(' ').append(0.0f / ints[2]);
		div.append(' ').append(ints[0] >> 33).append(' ').append(ints[1] >>> 28).append(' ').append(longs[1] << 65);
		out.line("R floatmath division " + div);

		// A simulation-like float loop. Java evaluates every float op in float, with no fused
		// multiply-add; a C++ compiler may fuse a*b+c, which changes low bits.
		float x = 1.0f, y = 0.5f, vx = 0.01f, vy = -0.02f;
		long hash = 17;
		for (int i = 0; i < 200000; i++) {
			final float ax = (-x * 0.001f) + (vy * 0.0003f);
			final float ay = (-y * 0.001f) - (vx * 0.0003f);
			vx = (vx * 0.9999f) + ax;
			vy = (vy * 0.9999f) + ay;
			x += vx * 0.016f;
			y += vy * 0.016f;
			hash = (hash * 31) + Float.floatToRawIntBits(x) + (Float.floatToRawIntBits(y) * 7L);
		}
		out.line("R floatmath simloop " + Long.toHexString(hash) + " x " + x + " y " + y);

		double dsum = 0;
		long dhash = 0;
		for (int i = 1; i < 2000; i++) {
			final double a = i * 0.01;
			final double v = (Math.sqrt(a) + Math.floor(a * 3.3) + Math.abs(-a) + Math.round(a * 7.5)) / 3;
			dsum += v;
			dhash = (dhash * 31) + Double.doubleToLongBits(v);
		}
		out.line("R floatmath sqrt-floor-round " + Long.toHexString(dhash) + " sum " + dsum);

		long trig = 0;
		for (int i = 0; i < 2000; i++) {
			final double a = (i - 1000) * 0.0137;
			final double sv = Math.sin(a), cv = Math.cos(a), tv = Math.atan2(a, 1.7), ev = Math.exp(a * 0.01),
					lv = Math.log(1 + Math.abs(a)), pv = Math.pow(1.0 + Math.abs(a), 1.3);
			trig = (trig * 31) + Double.doubleToLongBits(sv) + Double.doubleToLongBits(cv)
					+ Double.doubleToLongBits(tv) + Double.doubleToLongBits(ev) + Double.doubleToLongBits(lv)
					+ Double.doubleToLongBits(pv);
		}
		// Math.sin and friends may legally differ from the JVM in the last bit (Clearwing has no
		// StrictMath); sqrt must be exact (IEEE correctly rounded).
		out.line("I floatmath trig hash " + Long.toHexString(trig) + " (Math.* may differ by an ulp)");
		out.line("I floatmath sin(1) " + Double.doubleToLongBits(Math.sin(1.0)) + " cos(1) "
				+ Double.doubleToLongBits(Math.cos(1.0)));
		out.line("R floatmath sqrt(2) " + Double.doubleToLongBits(Math.sqrt(2.0)));
		out.line("R floatmath bits " + Float.floatToIntBits(Float.NaN) + " " + Float.floatToRawIntBits(-0.0f) + " "
				+ Float.intBitsToFloat(0x3f800001) + " " + Double.longBitsToDouble(0x7ff0000000000000L) + " "
				+ Float.compare(0.0f, -0.0f) + " " + (0.0f == -0.0f) + " " + Float.isNaN(0.0f / 0.0f) + " "
				+ Math.min(-0.0f, 0.0f) + " " + Math.max(Float.NaN, 1f));
		final Random random = new Random(12345L);
		long rhash = 0;
		for (int i = 0; i < 1000; i++) {
			rhash = (rhash * 31) + random.nextInt(1000) + random.nextLong() + Float.floatToRawIntBits(random.nextFloat())
					+ Double.doubleToLongBits(random.nextDouble()) + (random.nextBoolean() ? 1 : 0);
		}
		out.line("R floatmath java.util.Random(12345) " + Long.toHexString(rhash));
	}

	// ---- exceptions ---------------------------------------------------------------------------

	static final class ProbeException extends Exception {
		final int code;

		ProbeException(final int code) {
			super("probe " + code);
			this.code = code;
		}
	}

	static int thrower(final int i) throws ProbeException {
		if ((i % 3) == 0) {
			throw new ProbeException(i);
		}
		return i;
	}

	static Object nothing() {
		return null;
	}

	static void exceptions() {
		long sum = 0;
		int caught = 0;
		final long start = System.nanoTime();
		for (int i = 0; i < 100000; i++) {
			try {
				sum += thrower(i);
			}
			catch (final ProbeException e) {
				caught++;
				sum += e.code;
			}
			finally {
				sum++;
			}
		}
		out.line("R exceptions loop caught " + caught + " sum " + sum);
		out.line("T exceptions 100k try/throw/finally " + ms(start) + " ms");

		final StringBuilder order = new StringBuilder();
		try {
			try {
				order.append('a');
				throw new IllegalStateException("inner");
			}
			finally {
				order.append('b');
			}
		}
		catch (final RuntimeException e) {
			order.append('c').append(e.getMessage());
		}
		out.line("R exceptions finally order " + order);

		final StringBuilder kinds = new StringBuilder();
		final Object[] probes = new Object[6];
		for (int k = 0; k < probes.length; k++) {
			try {
				switch (k) {
				case 0:
					kinds.append(nothing().toString());
					break;
				case 1:
					kinds.append(ints[ints[4]]);
					break;
				case 2:
					kinds.append((String) (Object) Integer.valueOf(1));
					break;
				case 3:
					kinds.append(ints[3] / ints[2]);
					break;
				case 4: // Clearwing 3.1.2 aborted here; patched to throw (patches/clearwing-3.1.2-runtime)
					kinds.append(new int[ints[1]].length);
					break;
				default:
					final String text = (String) nothing();
					kinds.append(text.length());
					break;
				}
				kinds.append(k).append(":none ");
			}
			catch (final Throwable t) {
				kinds.append(k).append(':').append(t.getClass().getSimpleName()).append(' ');
			}
		}
		out.line("R exceptions kinds " + kinds);

		try {
			throw new ProbeException(7);
		}
		catch (final ProbeException e) {
			final StackTraceElement[] trace = e.getStackTrace();
			out.line("I exceptions stack trace depth " + (trace == null ? -1 : trace.length)
					+ (trace != null && trace.length > 0 ? " top " + trace[0] : ""));
		}
	}

	// ---- collections ------------------------------------------------------------------------------

	static void collections() {
		final Random random = new Random(7L);
		long start = System.nanoTime();
		final HashMap<Integer, String> map = new HashMap<Integer, String>();
		for (int i = 0; i < 200000; i++) {
			map.put(random.nextInt(1000000), "v" + i);
		}
		int hits = 0;
		for (int i = 0; i < 200000; i++) {
			if (map.containsKey(i * 5)) {
				hits++;
			}
		}
		for (int i = 0; i < 50000; i++) {
			map.remove(i * 3);
		}
		long valueHash = 0;
		for (final Map.Entry<Integer, String> e : map.entrySet()) {
			valueHash += e.getKey() ^ e.getValue().hashCode();
		}
		out.line("R collections hashmap size " + map.size() + " hits " + hits + " sum " + valueHash);
		out.line("T collections hashmap " + ms(start) + " ms");

		start = System.nanoTime();
		final ArrayList<Integer> list = new ArrayList<Integer>();
		for (int i = 0; i < 200000; i++) {
			list.add(random.nextInt());
		}
		Collections.sort(list, new Comparator<Integer>() {
			@Override
			public int compare(final Integer a, final Integer b) {
				return Integer.compare(b, a);
			}
		});
		boolean sorted = true;
		for (int i = 1; i < list.size(); i++) {
			sorted &= list.get(i - 1) >= list.get(i);
		}
		final List<Integer> small = new ArrayList<Integer>(list.subList(0, 20));
		Collections.shuffle(small, new Random(3L));
		out.line("R collections sort " + sorted + " first " + list.get(0) + " shuffled " + small.get(0) + ","
				+ small.get(1));
		out.line("T collections sort 200k " + ms(start) + " ms");

		final HashSet<String> set = new HashSet<String>();
		final LinkedList<Integer> linked = new LinkedList<Integer>();
		final ArrayDeque<Integer> deque = new ArrayDeque<Integer>();
		for (int i = 0; i < 10000; i++) {
			set.add("k" + (i % 777));
			linked.addFirst(i);
			deque.addLast(i);
			if ((i % 3) == 0) {
				linked.removeLast();
				deque.pollFirst();
			}
		}
		final int lambdaSum = sumWith(list.subList(0, 100), x -> x / 1000);
		out.line("R collections set " + set.size() + " linked " + linked.size() + "/" + linked.getFirst()
				+ " deque " + deque.size() + "/" + deque.peekFirst() + " lambda " + lambdaSum);
	}

	interface IntOp {
		int apply(int x);
	}

	static int sumWith(final List<Integer> values, final IntOp op) {
		int sum = 0;
		for (final Integer v : values) {
			sum += op.apply(v);
		}
		return sum;
	}

	// ---- threads ----------------------------------------------------------------------------------

	static int counter;

	static void threads() throws InterruptedException {
		counter = 0;
		final Object lock = new Object();
		long start = System.nanoTime();
		final Thread[] workers = new Thread[4];
		final String[] places = new String[workers.length];
		for (int t = 0; t < workers.length; t++) {
			final int index = t;
			workers[t] = new Thread(new Runnable() {
				@Override
				public void run() {
					places[index] = out.where();
					for (int i = 0; i < 250000; i++) {
						synchronized (lock) {
							counter++;
						}
					}
				}
			});
			workers[t].start();
		}
		for (final Thread w : workers) {
			w.join();
		}
		out.line("R threads synchronized counter " + counter);
		final StringBuilder placement = new StringBuilder("I threads placement: main " + out.where() + "; workers");
		for (final String place : places) {
			placement.append(" [").append(place).append(']');
		}
		out.line(placement.toString());
		out.line("T threads 4x250k synchronized " + ms(start) + " ms");

		// Round 7: one thread, no contention (most of Warsmash's and LibGDX's locking), and re-entry.
		final Object solo = new Object();
		int soloCount = 0;
		start = System.nanoTime();
		for (int i = 0; i < 10000000; i++) {
			synchronized (solo) {
				soloCount++;
			}
		}
		out.line("R threads uncontended synchronized " + soloCount);
		out.line("T threads 10M uncontended synchronized " + ms(start) + " ms");
		start = System.nanoTime();
		for (int i = 0; i < 2000000; i++) {
			synchronized (solo) {
				synchronized (solo) {
					soloCount++;
				}
			}
		}
		out.line("R threads re-entrant synchronized " + soloCount);
		out.line("T threads 2M re-entrant synchronized " + ms(start) + " ms");

		// wait inside a re-entered monitor must release it completely, or the notifier never enters
		// (Clearwing released one level: both threads hung).
		final Object nest = new Object();
		final boolean[] nestState = { false, false };
		final Thread nested = new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized (nest) {
					synchronized (nest) {
						nestState[0] = true;
						nest.notifyAll();
						while (!nestState[1]) {
							try {
								nest.wait();
							}
							catch (final InterruptedException e) {
								return;
							}
						}
					}
				}
			}
		});
		out.progress("nested wait (a hang here = wait kept the monitor)");
		nested.start();
		synchronized (nest) {
			while (!nestState[0]) {
				nest.wait();
			}
			nestState[1] = true;
			nest.notifyAll();
		}
		nested.join();
		out.line("R threads nested wait released the monitor");

		// notify without owning the monitor must throw.
		String unowned = "no exception";
		try {
			solo.notify();
		}
		catch (final IllegalMonitorStateException e) {
			unowned = "IllegalMonitorStateException";
		}
		out.line("R threads notify unowned: " + unowned);

		// wait/notify ping-pong
		final int[] turn = { 0 };
		start = System.nanoTime();
		final Thread pong = new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized (turn) {
					for (int i = 0; i < 5000; i++) {
						while (turn[0] != 1) {
							try {
								turn.wait();
							}
							catch (final InterruptedException e) {
								return;
							}
						}
						turn[0] = 0;
						turn.notifyAll();
					}
				}
			}
		});
		pong.start();
		synchronized (turn) {
			for (int i = 0; i < 5000; i++) {
				turn[0] = 1;
				turn.notifyAll();
				while (turn[0] != 0) {
					turn.wait();
				}
			}
		}
		pong.join();
		out.line("R threads ping-pong 5000 done");
		out.line("T threads ping-pong 5000 " + ms(start) + " ms");

		// Two threads allocating at once: the collector has to stop both at safe points.
		start = System.nanoTime();
		final long[] sums = new long[2];
		final Thread[] allocators = new Thread[2];
		for (int t = 0; t < 2; t++) {
			final int index = t;
			allocators[t] = new Thread(new Runnable() {
				@Override
				public void run() {
					long sum = 0;
					for (int i = 0; i < 2000000; i++) {
						final int[] a = new int[8];
						a[i & 7] = i;
						sum += a[i & 7] & 1;
					}
					sums[index] = sum;
				}
			});
			allocators[t].start();
		}
		for (final Thread a : allocators) {
			a.join();
		}
		out.line("R threads parallel allocation " + sums[0] + " " + sums[1]);
		out.line("T threads 2x2M allocations " + ms(start) + " ms");
	}

	// ---- reflection -------------------------------------------------------------------------------

	public static final class Reflected {
		public int number = 5;
		public String label = "start";

		public Reflected() {
		}

		public int add(final int a, final int b) {
			return a + b + this.number;
		}

		public String describe(final String prefix, final float scale) {
			return prefix + ":" + this.label + ":" + (this.number * scale);
		}
	}

	enum Kind {
		PEON, GRUNT, TAUREN
	}

	static void reflection() throws Exception {
		final Class<?> c = Class.forName("wsnx.probeb.ProbeB$Reflected");
		final Constructor<?> ctor = c.getConstructor();
		final Object o = ctor.newInstance();
		final Field number = c.getField("number");
		number.setInt(o, 40);
		final Field label = c.getField("label");
		label.set(o, "set");
		final Method add = c.getMethod("add", int.class, int.class);
		final Object sum = add.invoke(o, 1, 2);
		final Method describe = c.getMethod("describe", String.class, float.class);
		final Object text = describe.invoke(o, "p", 0.5f);
		out.line("R reflection " + c.getSimpleName() + " " + number.getInt(o) + " " + sum + " " + text + " fields "
				+ c.getFields().length + " " + (o instanceof Reflected));
		out.line("R reflection enum " + Kind.valueOf("GRUNT").ordinal() + " " + Kind.values().length + " "
				+ Kind.TAUREN.name() + " " + Kind.class.isEnum());
	}

	// ---- fileio -----------------------------------------------------------------------------------

	static void fileIo() throws IOException {
		final File dir = new File(out.dir());
		final File file = new File(dir, "io-test.bin");
		final byte[] data = new byte[1 << 20];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) ((i * 31) ^ (i >>> 8));
		}
		long start = System.nanoTime();
		final FileOutputStream fos = new FileOutputStream(file);
		try {
			fos.write(data);
		}
		finally {
			fos.close();
		}
		out.line("T fileio write 1 MB " + ms(start) + " ms");

		start = System.nanoTime();
		final byte[] back = new byte[data.length];
		final FileInputStream fis = new FileInputStream(file);
		int got = 0;
		try {
			int n;
			while ((got < back.length) && ((n = fis.read(back, got, back.length - got)) > 0)) {
				got += n;
			}
		}
		finally {
			fis.close();
		}
		out.line("R fileio stream read " + got + " equal " + java.util.Arrays.equals(data, back) + " length "
				+ file.length() + " exists " + file.exists());
		out.line("T fileio read 1 MB " + ms(start) + " ms");

		final RandomAccessFile raf = new RandomAccessFile(file, "r");
		try {
			raf.seek(500000);
			final byte[] chunk = new byte[16];
			raf.readFully(chunk);
			raf.seek(4);
			final int asInt = raf.readInt();
			final FileChannel channel = raf.getChannel();
			final ByteBuffer heap = ByteBuffer.allocate(64);
			final int heapRead = channel.read(heap, 123456);
			final ByteBuffer direct = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
			final int directRead = channel.read(direct, 777777);
			direct.flip();
			out.line("R fileio random " + (chunk[0] & 255) + "," + (chunk[15] & 255) + " int " + asInt + " channel "
					+ heapRead + "/" + (heap.get(10) & 255) + " direct " + directRead + "/" + direct.getInt(4)
					+ " size " + channel.size());
		}
		finally {
			raf.close();
		}
		final String[] listing = dir.list();
		boolean listed = false;
		if (listing != null) {
			for (final String name : listing) {
				listed |= "io-test.bin".equals(name);
			}
		}
		out.line("R fileio listed " + listed + " deleted " + file.delete() + " gone " + !file.exists());
	}

	// ---- gc: allocation churn, with and without a large live heap ----------------------------------

	static Object[] keep;

	static void churn(final String label, final int allocations) {
		final Object[] ring = new Object[100000];
		long maxSlice = 0;
		long slices = 0;
		long sliceStart = System.nanoTime();
		final long start = sliceStart;
		long check = 0;
		for (int i = 0; i < allocations; i++) {
			final float[] f = new float[4];
			f[i & 3] = i;
			ring[i % ring.length] = f;
			check += (long) f[i & 3] & 1;
			if ((i % 100000) == 99999) {
				final long now = System.nanoTime();
				maxSlice = Math.max(maxSlice, now - sliceStart);
				sliceStart = now;
				slices++;
				if ((slices % 10) == 0) {
					out.progress(label + " " + (slices / 10) + "M of " + allocations / 1000000 + "M");
				}
			}
		}
		out.line("R gc " + label + " check " + check);
		out.line("T gc " + label + ": " + allocations / 1000000 + "M allocations in " + ms(start)
				+ " ms; worst 100k-slice " + (maxSlice / 1000000.0) + " ms over " + slices + " slices");
	}

	static void gc() throws InterruptedException {
		memory("start");
		churn("churn, small heap", 20000000);
		for (final int megabytes : new int[] { 256, 1024 }) {
			final int arrays = megabytes * 16; // 64 KB each
			keep = new Object[arrays];
			final long start = System.nanoTime();
			for (int i = 0; i < arrays; i++) {
				final int[] block = new int[16384];
				block[i & 16383] = i;
				keep[i] = block;
				if ((i & 1023) == 1023) {
					out.progress("fill " + (i + 1) / 16 + " of " + megabytes + " MB");
				}
			}
			out.line("T gc fill " + megabytes + " MB live in " + ms(start) + " ms");
			memory(megabytes + " MB live");
			churn("churn with " + megabytes + " MB live", 5000000);
			long check = 0;
			for (int i = 0; i < arrays; i++) {
				check += ((int[]) keep[i])[i & 16383];
			}
			out.line("R gc live " + megabytes + " MB intact " + (check == (((long) arrays * (arrays - 1)) / 2)));
			keep = null;
			final long gcStart = System.nanoTime();
			System.gc();
			out.line("T gc System.gc() after dropping " + megabytes + " MB: " + ms(gcStart) + " ms");
			memory("after drop");
		}
		spinnerChurn();
	}

	static volatile boolean spinStop;
	static volatile long spinResult;

	/**
	 * A thread in a loop without calls or allocations while this one allocates. Clearwing stopped
	 * threads for a collection only at method entry and exit, so here the collection waited for
	 * the spinner and the spinner for this thread: a deadlock (round 4 makes every jump a safepoint).
	 */
	static void spinnerChurn() throws InterruptedException {
		spinStop = false;
		final Thread spinner = new Thread(new Runnable() {
			@Override
			public void run() {
				long x = 1;
				long spins = 0;
				while (!spinStop) {
					x = x * 6364136223846793005L + 1442695040888963407L;
					spins++;
				}
				spinResult = (x == 0 ? 1 : 0) + (spins > 0 ? 1 : 0);
			}
		});
		spinner.start();
		churn("churn beside a spinning thread", 2000000);
		spinStop = true;
		spinner.join();
		out.line("R gc spinner stopped " + spinResult);
	}

	static void memory(final String when) {
		try {
			final Runtime r = Runtime.getRuntime();
			out.line("I gc memory (" + when + "): total " + (r.totalMemory() >> 20) + " MB, free "
					+ (r.freeMemory() >> 20) + " MB, max " + (r.maxMemory() >> 20) + " MB");
		}
		catch (final Throwable t) {
			out.line("I gc memory (" + when + "): unavailable " + t);
		}
	}

	// ---- bench: the same work timed on both ---------------------------------------------------------

	interface Shape {
		float area();
	}

	static final class Square implements Shape {
		final float s;

		Square(final float s) {
			this.s = s;
		}

		@Override
		public float area() {
			return this.s * this.s;
		}
	}

	static final class Circle implements Shape {
		final float r;

		Circle(final float r) {
			this.r = r;
		}

		@Override
		public float area() {
			return 3.14159f * this.r * this.r;
		}
	}

	static final class Tri implements Shape {
		final float b, h;

		Tri(final float b, final float h) {
			this.b = b;
			this.h = h;
		}

		@Override
		public float area() {
			return 0.5f * this.b * this.h;
		}
	}

	static void bench() {
		long start = System.nanoTime();
		float x = 0.5f, y = 0.25f;
		for (int i = 0; i < 30000000; i++) {
			final float nx = (x * 0.9999f) + (y * 0.0001f);
			y = (y * 0.9998f) - (x * 0.0002f) + 0.00001f;
			x = nx;
		}
		out.line("R bench floatloop " + Float.floatToRawIntBits(x) + " " + Float.floatToRawIntBits(y));
		out.line("T bench float loop 30M " + ms(start) + " ms");

		start = System.nanoTime();
		final boolean[] composite = new boolean[5000001];
		int primes = 0;
		for (int i = 2; i < composite.length; i++) {
			if (!composite[i]) {
				primes++;
				for (long j = (long) i * i; j < composite.length; j += i) {
					composite[(int) j] = true;
				}
			}
		}
		out.line("R bench sieve 5M primes " + primes);
		out.line("T bench sieve 5M " + ms(start) + " ms");

		start = System.nanoTime();
		final Shape[] shapes = { new Square(1.5f), new Circle(0.7f), new Tri(2f, 3f) };
		float total = 0;
		for (int i = 0; i < 50000000; i++) {
			total += shapes[i % 3].area();
		}
		out.line("R bench interface calls " + Float.floatToRawIntBits(total));
		out.line("T bench interface calls 50M " + ms(start) + " ms");

		start = System.nanoTime();
		final HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
		long acc = 0;
		for (int i = 0; i < 1000000; i++) {
			map.put(i & 65535, i);
			acc += map.get((i * 7) & 65535) == null ? 0 : 1;
		}
		out.line("R bench hashmap " + acc);
		out.line("T bench hashmap 1M put+get " + ms(start) + " ms");

		start = System.nanoTime();
		final int[] src = new int[1 << 20];
		final int[] dst = new int[1 << 20];
		for (int r = 0; r < 200; r++) {
			src[r] = r;
			System.arraycopy(src, 0, dst, 0, src.length);
		}
		out.line("R bench arraycopy " + dst[199]);
		out.line("T bench arraycopy 200x4MB " + ms(start) + " ms");

		start = System.nanoTime();
		String s = "";
		int length = 0;
		for (int i = 0; i < 20000; i++) {
			s = "u" + i + ":" + (i * 0.5f);
			length += s.length();
		}
		out.line("R bench string building " + length);
		out.line("T bench string building 20k " + ms(start) + " ms");
	}

	// ---- output: native console and log on the Switch, a file on the PC ------------------------------

	abstract static class Out {
		abstract String platform();

		abstract String dir();

		abstract void line(String text, boolean sync);

		abstract boolean poll();

		abstract void finish();

		abstract String where();

		void line(final String text) {
			line(text, false);
		}

		/** How far a long test has got: the console's watchdog shows it, the PC ignores it. */
		void progress(final String text) {
		}

		/** The console's crash-handler environment line; null on the PC. */
		String environment() {
			return null;
		}

		/** The console waits two seconds for + (straight to the self-tests); the PC never skips. */
		boolean offerSkip() {
			return false;
		}

		void mark(final String text) {
			line(text, true);
		}

		static Out create() {
			try {
				return new ConsoleOut();
			}
			catch (final Throwable e) { // UnsatisfiedLinkError on the JVM (Clearwing has no such class)
				return new JvmOut();
			}
		}
	}

	static final class ConsoleOut extends Out {
		static final String DIR = "sdmc:/switch/warsmash-probe-b";

		ConsoleOut() {
			Native.init(DIR, DIR + "/probe-b.log", DIR + "/crash.txt", DIR + "/watch.log");
		}

		@Override
		void progress(final String text) {
			Native.progress(text);
		}

		@Override
		String environment() {
			return Native.crashEnvironment();
		}

		@Override
		boolean offerSkip() {
			Native.line("press + within 2 s to skip the tests (straight to the crash self-tests)", false);
			return Native.waitForPlusFor(2000);
		}

		@Override
		String platform() {
			return "Switch 2 (Clearwing)";
		}

		@Override
		String dir() {
			return DIR;
		}

		@Override
		void line(final String text, final boolean sync) {
			Native.line(text, sync);
		}

		@Override
		boolean poll() {
			return Native.poll();
		}

		@Override
		String where() {
			return Native.where();
		}

		@Override
		void finish() {
			Native.line("press + to exit", true);
			Native.line("or test the crash report (ends the program, writes crash.txt):", false);
			Native.line("  Y: a CPU fault    X: an uncaught C++ exception", false);
			Native.waitForExitOrSelfTest();
			Native.finish();
		}
	}

	static final class JvmOut extends Out {
		final String dir = System.getProperty("probeb.dir", "pc-out");
		FileWriter writer;

		JvmOut() {
			new File(this.dir).mkdirs();
			try {
				this.writer = new FileWriter(new File(this.dir, "probe-b-pc.log"), true);
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		String platform() {
			return "PC JVM " + System.getProperty("java.version");
		}

		@Override
		String dir() {
			return this.dir;
		}

		@Override
		void line(final String text, final boolean sync) {
			System.out.println(text);
			try {
				this.writer.write(text + "\n");
				this.writer.flush();
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		boolean poll() {
			return false;
		}

		@Override
		String where() {
			return "jvm";
		}

		@Override
		void finish() {
			try {
				this.writer.close();
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
