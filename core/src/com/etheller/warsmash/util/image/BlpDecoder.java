package com.etheller.warsmash.util.image;

/**
 * Decodes the first mipmap of a BLP1 or BLP2 texture into a {@link PixelImage}
 * tagged linear, matching what the blp-iio-plugin ImageIO reader returned.
 *
 * BLP1 (every texture in WarCraft III up to 1.31): JPEG with four channels
 * stored B, G, R, A, or a 256-colour BGRA palette with 0, 1, 4 or 8 bits of
 * separate alpha. BLP2 (custom maps): palette, DXT1/3/5 or raw BGRA.
 */
public final class BlpDecoder {
	private static final int MAGIC_BLP1 = 0x31504C42; // "BLP1" little-endian
	private static final int MAGIC_BLP2 = 0x32504C42; // "BLP2"

	public static boolean isBlp(final byte[] data) {
		if (data.length < 4) {
			return false;
		}
		final int magic = le32(data, 0);
		return (magic == MAGIC_BLP1) || (magic == MAGIC_BLP2);
	}

	public static PixelImage decode(final byte[] data) {
		final int magic = le32(data, 0);
		if (magic == MAGIC_BLP1) {
			return decodeBlp1(data);
		}
		if (magic == MAGIC_BLP2) {
			return decodeBlp2(data);
		}
		throw new IllegalArgumentException("Not a BLP1/BLP2 file");
	}

	private static PixelImage decodeBlp1(final byte[] data) {
		final int compression = le32(data, 4);
		final int alphaBits = le32(data, 8);
		final int width = le32(data, 12);
		final int height = le32(data, 16);
		final int offset = le32(data, 28);
		final int size = le32(data, 92);
		checkSize(width, height);
		if (compression == 0) {
			final int headerSize = le32(data, 156);
			return decodeJpeg(data, 160, headerSize, offset, size, width, height, alphaBits);
		}
		if (compression == 1) {
			return decodePalette(data, 156, offset, size, width, height, alphaBits);
		}
		throw new UnsupportedOperationException("Unknown BLP1 compression " + compression);
	}

	private static PixelImage decodeBlp2(final byte[] data) {
		final int type = le32(data, 4);
		final int encoding = data[8] & 0xFF;
		final int alphaDepth = data[9] & 0xFF;
		final int alphaEncoding = data[10] & 0xFF;
		final int width = le32(data, 12);
		final int height = le32(data, 16);
		final int offset = le32(data, 20);
		final int size = le32(data, 84);
		checkSize(width, height);
		if (type == 0) {
			// JPEG content: the shared header follows the palette-sized gap.
			final int headerSize = le32(data, 148);
			return decodeJpeg(data, 152, headerSize, offset, size, width, height, alphaDepth);
		}
		checkRange(data, offset, size);
		switch (encoding) {
		case 1:
			return decodePalette(data, 148, offset, size, width, height, alphaDepth);
		case 2: {
			final int[] argb = new int[width * height];
			final int format = alphaDepth == 0 ? DxtDecoder.DXT1
					: (alphaEncoding == 0 ? DxtDecoder.DXT1 : (alphaEncoding == 1 ? DxtDecoder.DXT3 : DxtDecoder.DXT5));
			DxtDecoder.decode(data, offset, width, height, format, alphaDepth != 0, argb);
			return new PixelImage(width, height, argb, true);
		}
		case 3: {
			final int[] argb = new int[width * height];
			for (int i = 0; i < argb.length; i++) {
				final int p = offset + (i * 4);
				final int a = alphaDepth == 0 ? 0xFF : data[p + 3] & 0xFF;
				argb[i] = (a << 24) | ((data[p + 2] & 0xFF) << 16) | ((data[p + 1] & 0xFF) << 8) | (data[p] & 0xFF);
			}
			return new PixelImage(width, height, argb, true);
		}
		default:
			throw new UnsupportedOperationException("Unknown BLP2 encoding " + encoding);
		}
	}

	private static PixelImage decodeJpeg(final byte[] data, final int headerStart, final int headerSize,
			final int offset, final int size, final int width, final int height, final int alphaBits) {
		checkRange(data, headerStart, headerSize);
		checkRange(data, offset, size);
		// The JPEG stream is the shared header followed by this mipmap's data.
		final byte[] jpeg = new byte[headerSize + size];
		System.arraycopy(data, headerStart, jpeg, 0, headerSize);
		System.arraycopy(data, offset, jpeg, headerSize, size);
		final JpegDecoder.Result result = JpegDecoder.decode(jpeg, 0, jpeg.length);
		final byte[][] planes = result.planes;
		if (planes.length < 3) {
			throw new UnsupportedOperationException("BLP JPEG with " + planes.length + " channels");
		}
		final int[] argb = new int[width * height];
		final int w = Math.min(width, result.width);
		final int h = Math.min(height, result.height);
		// The fourth JPEG channel is alpha even when the header claims 0 alpha
		// bits: the blp-iio-plugin read it that way, and 534 WarCraft III 1.31
		// textures (shadows among them) carry real alpha there.
		final boolean hasAlpha = planes.length > 3;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				final int s = (y * result.width) + x;
				final int a = hasAlpha ? planes[3][s] & 0xFF : 0xFF;
				argb[(y * width) + x] = (a << 24) | ((planes[2][s] & 0xFF) << 16) | ((planes[1][s] & 0xFF) << 8)
						| (planes[0][s] & 0xFF);
			}
		}
		return new PixelImage(width, height, argb, true);
	}

	private static PixelImage decodePalette(final byte[] data, final int paletteStart, final int offset,
			final int size, final int width, final int height, final int alphaBits) {
		checkRange(data, paletteStart, 1024);
		checkRange(data, offset, size);
		final int pixels = width * height;
		final int[] palette = new int[256];
		for (int i = 0; i < 256; i++) {
			palette[i] = le32(data, paletteStart + (i * 4)) & 0xFFFFFF;
		}
		final int[] argb = new int[pixels];
		final int alphaStart = offset + pixels;
		final int limit = offset + size;
		for (int i = 0; i < pixels; i++) {
			final int ip = offset + i;
			final int index = ip < limit ? data[ip] & 0xFF : 0;
			final int a;
			switch (alphaBits) {
			case 0:
				a = 0xFF;
				break;
			case 1: {
				final int p = alphaStart + (i >> 3);
				a = (p < limit) && (((data[p] >> (i & 7)) & 1) != 0) ? 0xFF : 0;
				break;
			}
			case 4: {
				final int p = alphaStart + (i >> 1);
				final int nibble = p < limit ? (data[p] >> ((i & 1) << 2)) & 0xF : 0;
				a = nibble * 17;
				break;
			}
			case 8: {
				final int p = alphaStart + i;
				a = p < limit ? data[p] & 0xFF : 0;
				break;
			}
			default:
				throw new UnsupportedOperationException("BLP palette alpha of " + alphaBits + " bits");
			}
			argb[i] = (a << 24) | palette[index];
		}
		return new PixelImage(width, height, argb, true);
	}

	private static void checkSize(final int width, final int height) {
		if ((width <= 0) || (height <= 0) || (width > 65535) || (height > 65535)) {
			throw new IllegalArgumentException("Bad BLP size " + width + "x" + height);
		}
	}

	private static void checkRange(final byte[] data, final int offset, final int size) {
		if ((offset < 0) || (size < 0) || ((long) offset + size > data.length)) {
			throw new IllegalArgumentException(
					"BLP section " + offset + "+" + size + " outside a " + data.length + "-byte file");
		}
	}

	static int le32(final byte[] data, final int at) {
		return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8) | ((data[at + 2] & 0xFF) << 16)
				| ((data[at + 3] & 0xFF) << 24);
	}

	private BlpDecoder() {
	}
}
