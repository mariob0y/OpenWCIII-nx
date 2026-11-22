package com.etheller.warsmash.util.image;

/** Decodes DXT1/DXT3/DXT5 (BC1/BC2/BC3) block-compressed pixels into 0xAARRGGBB. */
public final class DxtDecoder {
	public static final int DXT1 = 1;
	public static final int DXT3 = 3;
	public static final int DXT5 = 5;

	public static int blockBytes(final int format) {
		return format == DXT1 ? 8 : 16;
	}

	/**
	 * @param dxt1Alpha for DXT1, whether the "transparent black" colour 3 is
	 *                  transparent (true) or opaque black (false)
	 */
	public static void decode(final byte[] data, final int offset, final int width, final int height,
			final int format, final boolean dxt1Alpha, final int[] out) {
		final int blocksWide = (width + 3) / 4;
		final int blocksHigh = (height + 3) / 4;
		final int blockSize = blockBytes(format);
		final int needed = blocksWide * blocksHigh * blockSize;
		if ((offset < 0) || ((long) offset + needed > data.length)) {
			throw new IllegalArgumentException("DXT data needs " + needed + " bytes at " + offset + ", file has "
					+ data.length);
		}
		final int[] colors = new int[4];
		final int[] alphas = new int[16];
		int p = offset;
		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				int colorAt = p;
				if (format == DXT3) {
					for (int i = 0; i < 16; i++) {
						final int nibble = (data[p + (i >> 1)] >> ((i & 1) << 2)) & 0xF;
						alphas[i] = nibble * 17;
					}
					colorAt = p + 8;
				}
				else if (format == DXT5) {
					decodeDxt5Alpha(data, p, alphas);
					colorAt = p + 8;
				}
				final boolean opaqueOnly = format != DXT1;
				decodeColors(data, colorAt, colors, opaqueOnly, dxt1Alpha);
				final int indices = BlpDecoder.le32(data, colorAt + 4);
				for (int i = 0; i < 16; i++) {
					final int x = (bx * 4) + (i & 3);
					final int y = (by * 4) + (i >> 2);
					if ((x >= width) || (y >= height)) {
						continue;
					}
					int argb = colors[(indices >>> (i * 2)) & 3];
					if (format != DXT1) {
						argb = (argb & 0xFFFFFF) | (alphas[i] << 24);
					}
					out[(y * width) + x] = argb;
				}
				p += blockSize;
			}
		}
	}

	private static void decodeColors(final byte[] data, final int at, final int[] colors, final boolean fourColor,
			final boolean dxt1Alpha) {
		final int c0 = (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8);
		final int c1 = (data[at + 2] & 0xFF) | ((data[at + 3] & 0xFF) << 8);
		final int r0 = expand5(c0 >> 11);
		final int g0 = expand6((c0 >> 5) & 0x3F);
		final int b0 = expand5(c0 & 0x1F);
		final int r1 = expand5(c1 >> 11);
		final int g1 = expand6((c1 >> 5) & 0x3F);
		final int b1 = expand5(c1 & 0x1F);
		colors[0] = argb(255, r0, g0, b0);
		colors[1] = argb(255, r1, g1, b1);
		if (fourColor || (c0 > c1)) {
			colors[2] = argb(255, ((2 * r0) + r1) / 3, ((2 * g0) + g1) / 3, ((2 * b0) + b1) / 3);
			colors[3] = argb(255, (r0 + (2 * r1)) / 3, (g0 + (2 * g1)) / 3, (b0 + (2 * b1)) / 3);
		}
		else {
			colors[2] = argb(255, (r0 + r1) / 2, (g0 + g1) / 2, (b0 + b1) / 2);
			colors[3] = dxt1Alpha ? 0 : argb(255, 0, 0, 0);
		}
	}

	private static void decodeDxt5Alpha(final byte[] data, final int at, final int[] alphas) {
		final int a0 = data[at] & 0xFF;
		final int a1 = data[at + 1] & 0xFF;
		final int[] table = new int[8];
		table[0] = a0;
		table[1] = a1;
		if (a0 > a1) {
			for (int i = 1; i < 7; i++) {
				table[i + 1] = (((7 - i) * a0) + (i * a1)) / 7;
			}
		}
		else {
			for (int i = 1; i < 5; i++) {
				table[i + 1] = (((5 - i) * a0) + (i * a1)) / 5;
			}
			table[6] = 0;
			table[7] = 255;
		}
		long bits = 0;
		for (int i = 0; i < 6; i++) {
			bits |= (long) (data[at + 2 + i] & 0xFF) << (8 * i);
		}
		for (int i = 0; i < 16; i++) {
			alphas[i] = table[(int) ((bits >>> (3 * i)) & 7)];
		}
	}

	private static int expand5(final int v) {
		return (v << 3) | (v >> 2);
	}

	private static int expand6(final int v) {
		return (v << 2) | (v >> 4);
	}

	private static int argb(final int a, final int r, final int g, final int b) {
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private DxtDecoder() {
	}
}
