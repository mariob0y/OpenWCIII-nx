package java.lang;

/**
 * warsmash-nx: Clearwing 3.1.2 has no StrictMath; Warsmash's simulation and JASS natives use it
 * (~150 calls). The JDK's StrictMath promises fdlibm's exact results so that every machine computes
 * the same bits (lockstep multiplayer, replays). This port is single player, so it delegates to
 * Math (the C library), which can differ from fdlibm in the last bit for sin/cos/tan/atan/pow etc.
 * The exactly-rounded operations (sqrt, ceil, floor, rint, abs, min, max, round, toRadians,
 * toDegrees) are the same either way.
 */
public final class StrictMath {
	public static final double E = Math.E;
	public static final double PI = Math.PI;

	private StrictMath() {
	}

	public static double sin(final double a) {
		return Math.sin(a);
	}

	public static double cos(final double a) {
		return Math.cos(a);
	}

	public static double tan(final double a) {
		return Math.tan(a);
	}

	public static double asin(final double a) {
		return Math.asin(a);
	}

	public static double acos(final double a) {
		return Math.acos(a);
	}

	public static double atan(final double a) {
		return Math.atan(a);
	}

	public static double atan2(final double y, final double x) {
		return Math.atan2(y, x);
	}

	public static double toRadians(final double angdeg) {
		return Math.toRadians(angdeg);
	}

	public static double toDegrees(final double angrad) {
		return Math.toDegrees(angrad);
	}

	public static double exp(final double a) {
		return Math.exp(a);
	}

	public static double log(final double a) {
		return Math.log(a);
	}

	public static double log10(final double a) {
		return Math.log10(a);
	}

	public static double sqrt(final double a) {
		return Math.sqrt(a);
	}

	public static double cbrt(final double a) {
		return Math.cbrt(a);
	}

	public static double IEEEremainder(final double f1, final double f2) {
		return Math.IEEEremainder(f1, f2);
	}

	public static double ceil(final double a) {
		return Math.ceil(a);
	}

	public static double floor(final double a) {
		return Math.floor(a);
	}

	public static double rint(final double a) {
		return Math.rint(a);
	}

	public static double pow(final double a, final double b) {
		return Math.pow(a, b);
	}

	public static int round(final float a) {
		return Math.round(a);
	}

	public static long round(final double a) {
		return Math.round(a);
	}

	public static double random() {
		return Math.random();
	}

	public static int abs(final int a) {
		return Math.abs(a);
	}

	public static long abs(final long a) {
		return Math.abs(a);
	}

	public static float abs(final float a) {
		return Math.abs(a);
	}

	public static double abs(final double a) {
		return Math.abs(a);
	}

	public static int max(final int a, final int b) {
		return Math.max(a, b);
	}

	public static long max(final long a, final long b) {
		return Math.max(a, b);
	}

	public static float max(final float a, final float b) {
		return Math.max(a, b);
	}

	public static double max(final double a, final double b) {
		return Math.max(a, b);
	}

	public static int min(final int a, final int b) {
		return Math.min(a, b);
	}

	public static long min(final long a, final long b) {
		return Math.min(a, b);
	}

	public static float min(final float a, final float b) {
		return Math.min(a, b);
	}

	public static double min(final double a, final double b) {
		return Math.min(a, b);
	}

	public static double signum(final double d) {
		return Math.signum(d);
	}

	public static float signum(final float f) {
		return Math.signum(f);
	}

	public static double sinh(final double x) {
		return Math.sinh(x);
	}

	public static double cosh(final double x) {
		return Math.cosh(x);
	}

	public static double tanh(final double x) {
		return Math.tanh(x);
	}

	public static double hypot(final double x, final double y) {
		return Math.hypot(x, y);
	}

	public static double expm1(final double x) {
		return Math.expm1(x);
	}

	public static double log1p(final double x) {
		return Math.log1p(x);
	}
}
