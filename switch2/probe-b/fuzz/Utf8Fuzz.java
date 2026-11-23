import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

/**
 * warsmash-nx cpp1c check (PC only, not part of probe B): Clearwing's String.cpp decodeUtf8Java and
 * cpp1b's encodeUtf8, transliterated line for line, against JDK 17's String(byte[], UTF_8) and
 * String.getBytes(UTF_8). Also counts where the JDK's own CharsetDecoder (InputStreamReader) differs
 * from its String constructor: on the console both go through decodeUtf8Java.
 *
 *   java probe-b/fuzz/Utf8Fuzz.java [cases]
 */
public class Utf8Fuzz {
	static final int[] INTERESTING = { 0x00, 0x41, 0x7F, 0x80, 0x8F, 0x90, 0x9F, 0xA0, 0xBF, 0xC0, 0xC1, 0xC2, 0xDF, 0xE0,
			0xE1, 0xEC, 0xED, 0xEE, 0xEF, 0xF0, 0xF1, 0xF3, 0xF4, 0xF5, 0xF7, 0xF8, 0xFB, 0xFC, 0xFE, 0xFF };

	// ---- String.cpp decodeUtf8Java, transliterated (int arithmetic as in the C++) ----
	static int decodeUtf8Java(final byte[] src, final int sl, final char[] dst) {
		final int REPL = 0xFFFD;
		int sp = 0;
		final int[] dp = { 0 };
		while (sp < sl) {
			final int b1 = src[sp++];
			if (b1 >= 0) {
				put(dst, dp, b1);
			}
			else if (((b1 >> 5) == -2) && ((b1 & 0x1E) != 0)) {
				if (sp < sl) {
					final int b2 = src[sp++];
					if (notCont(b2)) {
						put(dst, dp, REPL);
						sp--;
					}
					else {
						put(dst, dp, ((b1 & 0x1F) << 6) | (b2 & 0x3F));
					}
					continue;
				}
				put(dst, dp, REPL);
				break;
			}
			else if ((b1 >> 4) == -2) {
				if ((sp + 1) < sl) {
					final int b2 = src[sp++];
					final int b3 = src[sp++];
					final boolean bad2 = ((b1 == -0x20) && ((b2 & 0xE0) == 0x80)) || notCont(b2);
					if (bad2 || notCont(b3)) {
						put(dst, dp, REPL);
						sp -= 3;
						sp += bad2 ? 1 : 2;
					}
					else {
						final int c = ((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
						put(dst, dp, (c >= 0xD800) && (c <= 0xDFFF) ? REPL : c);
					}
					continue;
				}
				if ((sp < sl) && (((b1 == -0x20) && ((src[sp] & 0xE0) == 0x80)) || notCont(src[sp]))) {
					put(dst, dp, REPL);
					continue;
				}
				put(dst, dp, REPL);
				break;
			}
			else if ((b1 >> 3) == -2) {
				if ((sp + 2) < sl) {
					final int b2 = src[sp++];
					final int b3 = src[sp++];
					final int b4 = src[sp++];
					final int uc = ((b1 & 0x07) << 18) | ((b2 & 0x3F) << 12) | ((b3 & 0x3F) << 6) | (b4 & 0x3F);
					if (notCont(b2) || notCont(b3) || notCont(b4) || (uc < 0x10000) || (uc > 0x10FFFF)) {
						put(dst, dp, REPL);
						sp -= 4;
						final int u1 = b1 & 0xFF, u2 = b2 & 0xFF;
						if ((u1 > 0xF4) || ((u1 == 0xF0) && ((u2 < 0x90) || (u2 > 0xBF))) || ((u1 == 0xF4) && ((u2 & 0xF0) != 0x80))
								|| notCont(u2)) {
							sp += 1;
						}
						else if (notCont(b3)) {
							sp += 2;
						}
						else {
							sp += 3;
						}
					}
					else {
						put(dst, dp, 0xD800 + ((uc - 0x10000) >> 10));
						put(dst, dp, 0xDC00 + ((uc - 0x10000) & 0x3FF));
					}
					continue;
				}
				final int u1 = b1 & 0xFF;
				if ((u1 > 0xF4) || ((sp < sl) && (((u1 == 0xF0) && (((src[sp] & 0xFF) < 0x90) || ((src[sp] & 0xFF) > 0xBF)))
						|| ((u1 == 0xF4) && ((src[sp] & 0xF0) != 0x80)) || notCont(src[sp])))) {
					put(dst, dp, REPL);
					continue;
				}
				sp++;
				put(dst, dp, REPL);
				if ((sp < sl) && notCont(src[sp])) {
					continue;
				}
				break;
			}
			else {
				put(dst, dp, REPL);
			}
		}
		return dp[0];
	}

	static boolean notCont(final int b) {
		return (b & 0xC0) != 0x80;
	}

	static void put(final char[] dst, final int[] dp, final int c) {
		if (dst != null) {
			dst[dp[0]] = (char) c;
		}
		dp[0]++;
	}

	// ---- String.cpp encodeUtf8 (cpp1b), transliterated ----
	static byte[] encodeUtf8(final char[] s) {
		final byte[] out = new byte[s.length * 4];
		int count = 0;
		for (int i = 0; i < s.length; i++) {
			int c = s[i];
			if ((c >= 0xD800) && (c <= 0xDBFF) && ((i + 1) < s.length) && (s[i + 1] >= 0xDC00) && (s[i + 1] <= 0xDFFF)) {
				c = 0x10000 + ((c - 0xD800) << 10) + (s[++i] - 0xDC00);
			}
			else if ((c >= 0xD800) && (c <= 0xDFFF)) {
				c = '?';
			}
			if (c < 0x80) {
				out[count++] = (byte) c;
			}
			else if (c < 0x800) {
				out[count++] = (byte) (0xC0 | (c >> 6));
				out[count++] = (byte) (0x80 | (c & 0x3F));
			}
			else if (c < 0x10000) {
				out[count++] = (byte) (0xE0 | (c >> 12));
				out[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				out[count++] = (byte) (0x80 | (c & 0x3F));
			}
			else {
				out[count++] = (byte) (0xF0 | (c >> 18));
				out[count++] = (byte) (0x80 | ((c >> 12) & 0x3F));
				out[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				out[count++] = (byte) (0x80 | (c & 0x3F));
			}
		}
		return Arrays.copyOf(out, count);
	}

	static String hex(final byte[] b) {
		final StringBuilder s = new StringBuilder();
		for (final byte x : b) {
			s.append(String.format("%02X ", x & 0xFF));
		}
		return s.toString().trim();
	}

	static String readerDecode(final byte[] b) throws Exception {
		final Reader r = new InputStreamReader(new ByteArrayInputStream(b), StandardCharsets.UTF_8);
		final StringBuilder s = new StringBuilder();
		final char[] buf = new char[64];
		int n;
		while ((n = r.read(buf)) > 0) {
			s.append(buf, 0, n);
		}
		return s.toString();
	}

	public static void main(final String[] args) throws Exception {
		final long cases = args.length > 0 ? Long.parseLong(args[0]) : 5_000_000L;
		final Random random = new Random(20261121);
		long decodeBad = 0, readerDiff = 0, encodeBad = 0;
		String readerExample = null;
		for (long n = 0; n < cases; n++) {
			final byte[] b = new byte[random.nextInt(13)];
			for (int i = 0; i < b.length; i++) {
				b[i] = (byte) (random.nextInt(4) == 0 ? random.nextInt(256) : INTERESTING[random.nextInt(INTERESTING.length)]);
			}
			final int count = decodeUtf8Java(b, b.length, null);
			final char[] ours = new char[count];
			decodeUtf8Java(b, b.length, ours);
			final String jdk = new String(b, StandardCharsets.UTF_8);
			if (!jdk.equals(new String(ours))) {
				if (decodeBad++ < 10) {
					System.out.println("decode differs: " + hex(b));
				}
			}
			if ((n % 10) == 0) {
				final String reader = readerDecode(b);
				if (!reader.equals(jdk)) {
					readerDiff++;
					if (readerExample == null) {
						readerExample = hex(b) + " -> String " + jdk.length() + " chars, reader " + reader.length();
					}
				}
			}
			final char[] chars = new char[random.nextInt(9)];
			for (int i = 0; i < chars.length; i++) {
				final int k = random.nextInt(6);
				chars[i] = (char) (k == 0 ? 0xD800 + random.nextInt(0x400) : k == 1 ? 0xDC00 + random.nextInt(0x400)
						: k == 2 ? random.nextInt(0x80) : random.nextInt(0x10000));
			}
			if (!Arrays.equals(encodeUtf8(chars), new String(chars).getBytes(StandardCharsets.UTF_8))) {
				if (encodeBad++ < 10) {
					System.out.println("encode differs for chars " + Arrays.toString(chars));
				}
			}
		}
		System.out.println(cases + " byte strings: decode mismatches " + decodeBad + "; " + cases
				+ " char strings: encode mismatches " + encodeBad + "; JDK reader vs JDK String differ in " + readerDiff
				+ " of " + ((cases + 9) / 10) + (readerExample == null ? "" : " (e.g. " + readerExample + ")"));
	}
}
