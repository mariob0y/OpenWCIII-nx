package com.etheller.warsmash.util.image;

/**
 * Decodes the top mipmap of a DDS file: DXT1/DXT3/DXT5 (also as DX10 BC1-BC3)
 * and uncompressed 16/24/32-bit RGB(A) with channel masks. WarCraft III up to
 * 1.31 ships no DDS; custom maps and later versions may.
 */
public final class DdsDecoder {
	private static final int MAGIC = 0x20534444; // "DDS "
	private static final int DDPF_ALPHAPIXELS = 0x1;
	private static final int DDPF_FOURCC = 0x4;
	private static final int DDPF_RGB = 0x40;
	private static final int DDPF_LUMINANCE = 0x20000;

	public static boolean isDds(final byte[] data) {
		return (data.length >= 128) && (BlpDecoder.le32(data, 0) == MAGIC);
	}

	public static PixelImage decode(final byte[] data) {
		if (!isDds(data)) {
			throw new IllegalArgumentException("Not a DDS file");
		}
		final int height = BlpDecoder.le32(data, 12);
		final int width = BlpDecoder.le32(data, 16);
		if ((width <= 0) || (height <= 0) || (width > 65535) || (height > 65535)) {
			throw new IllegalArgumentException("Bad DDS size " + width + "x" + height);
		}
		final int pfFlags = BlpDecoder.le32(data, 80);
		final int fourCC = BlpDecoder.le32(data, 84);
		int offset = 128;
		final int[] argb = new int[width * height];
		if ((pfFlags & DDPF_FOURCC) != 0) {
			int format;
			final String code = fourCCString(fourCC);
			if ("DX10".equals(code)) {
				final int dxgi = BlpDecoder.le32(data, 128);
				offset += 20;
				switch (dxgi) {
				case 70: case 71: case 72: format = DxtDecoder.DXT1; break; // BC1
				case 73: case 74: case 75: format = DxtDecoder.DXT3; break; // BC2
				case 76: case 77: case 78: format = DxtDecoder.DXT5; break; // BC3
				case 27: case 28: case 29: // R8G8B8A8
					decodeMasked(data, offset, width, height, 32, 0xFF, 0xFF00, 0xFF0000, 0xFF000000, argb);
					return new PixelImage(width, height, argb, false);
				case 87: case 88: case 90: case 91: // B8G8R8A8 / B8G8R8X8
					decodeMasked(data, offset, width, height, 32, 0xFF0000, 0xFF00, 0xFF,
							(dxgi == 87) || (dxgi == 90) ? 0xFF000000 : 0, argb);
					return new PixelImage(width, height, argb, false);
				default:
					throw new UnsupportedOperationException("DDS DXGI format " + dxgi + " is not supported");
				}
			}
			else if ("DXT1".equals(code)) {
				format = DxtDecoder.DXT1;
			}
			else if ("DXT2".equals(code) || "DXT3".equals(code)) {
				format = DxtDecoder.DXT3;
			}
			else if ("DXT4".equals(code) || "DXT5".equals(code)) {
				format = DxtDecoder.DXT5;
			}
			else {
				throw new UnsupportedOperationException("DDS format " + code + " is not supported");
			}
			DxtDecoder.decode(data, offset, width, height, format, true, argb);
			return new PixelImage(width, height, argb, false);
		}
		if ((pfFlags & (DDPF_RGB | DDPF_LUMINANCE)) != 0) {
			final int bits = BlpDecoder.le32(data, 88);
			final int rMask = BlpDecoder.le32(data, 92);
			final int gMask = (pfFlags & DDPF_LUMINANCE) != 0 ? rMask : BlpDecoder.le32(data, 96);
			final int bMask = (pfFlags & DDPF_LUMINANCE) != 0 ? rMask : BlpDecoder.le32(data, 100);
			final int aMask = (pfFlags & DDPF_ALPHAPIXELS) != 0 ? BlpDecoder.le32(data, 104) : 0;
			decodeMasked(data, offset, width, height, bits, rMask, gMask, bMask, aMask, argb);
			return new PixelImage(width, height, argb, false);
		}
		throw new UnsupportedOperationException("DDS pixel format flags 0x" + Integer.toHexString(pfFlags));
	}

	private static void decodeMasked(final byte[] data, final int offset, final int width, final int height,
			final int bits, final int rMask, final int gMask, final int bMask, final int aMask, final int[] out) {
		if ((bits != 8) && (bits != 16) && (bits != 24) && (bits != 32)) {
			throw new UnsupportedOperationException("DDS with " + bits + " bits per pixel");
		}
		final int bytes = bits / 8;
		final int pitch = width * bytes;
		if ((long) offset + ((long) pitch * height) > data.length) {
			throw new IllegalArgumentException("DDS pixel data is truncated");
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int p = offset + (y * pitch) + (x * bytes);
				int v = 0;
				for (int i = 0; i < bytes; i++) {
					v |= (data[p + i] & 0xFF) << (8 * i);
				}
				final int a = aMask == 0 ? 255 : channel(v, aMask);
				out[(y * width) + x] = (a << 24) | (channel(v, rMask) << 16) | (channel(v, gMask) << 8)
						| channel(v, bMask);
			}
		}
	}

	/**
	 * Extracts the masked channel and scales it to 0..255 as floor(v * 255 / max),
	 * which is what the old net.nikr.dds reader did (5-bit 26 becomes 213).
	 */
	private static int channel(final int value, final int mask) {
		if (mask == 0) {
			return 0;
		}
		final int shift = Integer.numberOfTrailingZeros(mask);
		final long max = (mask & 0xFFFFFFFFL) >>> shift;
		final long v = ((value & mask) & 0xFFFFFFFFL) >>> shift;
		return (int) ((v * 255) / max);
	}

	private static String fourCCString(final int fourCC) {
		final char[] c = new char[4];
		for (int i = 0; i < 4; i++) {
			c[i] = (char) ((fourCC >> (8 * i)) & 0xFF);
		}
		return new String(c);
	}

	private DdsDecoder() {
	}
}
