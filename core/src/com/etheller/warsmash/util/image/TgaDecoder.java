package com.etheller.warsmash.util.image;

/**
 * Uncompressed 24/32-bit TGA reader, ported from the AWT-based TgaFile (Riven,
 * modified by Oger-Lord) with identical checks and pixel order.
 */
public final class TgaDecoder {
	public static PixelImage decode(final String name, final byte[] file) {
		if (file.length < 18) {
			throw new IllegalStateException("Error " + name);
		}
		final byte[] header = file;
		final int dataStart = 18;
		final int dataLength = file.length - dataStart;

		if ((header[0] | header[1]) != 0) {
			throw new IllegalStateException("Error " + name);
		}
		if (header[2] != 2) {
			throw new IllegalStateException("Error " + name);
		}
		final int w = (header[12] & 0xFF) | ((header[13] & 0xFF) << 8);
		final int h = (header[14] & 0xFF) | ((header[15] & 0xFF) << 8);

		final boolean alpha;
		if (header[16] == 24) {
			alpha = false;
		}
		else if (header[16] == 32) {
			alpha = true;
		}
		else {
			throw new IllegalStateException("Error " + name + " invalid pixel depth: " + header[16]);
		}
		if ((header[17] & 15) != (alpha ? 8 : 0)) {
			throw new IllegalStateException("Error " + name);
		}

		final int[] pixels = new int[w * h];
		if (dataLength < (pixels.length * (alpha ? 4 : 3))) {
			throw new IllegalStateException("Error " + name + " not enaugh pixel data");
		}

		// The pixels are read last-first (a 180 degree turn), then flipped back as
		// the descriptor says, exactly as TgaFile did. A 24-bit TGA was a
		// TYPE_INT_RGB BufferedImage, whose getRGB reports alpha 255.
		if (alpha) {
			for (int i = 0, p = dataStart + ((pixels.length - 1) * 4); i < pixels.length; i++, p -= 4) {
				pixels[i] = (file[p] & 0xFF) | ((file[p + 1] & 0xFF) << 8) | ((file[p + 2] & 0xFF) << 16)
						| ((file[p + 3] & 0xFF) << 24);
			}
		}
		else {
			for (int i = 0, p = dataStart + ((pixels.length - 1) * 3); i < pixels.length; i++, p -= 3) {
				pixels[i] = 0xFF000000 | (file[p] & 0xFF) | ((file[p + 1] & 0xFF) << 8) | ((file[p + 2] & 0xFF) << 16);
			}
		}

		final int order = header[17] >> 4;
		if (order == 1) {
			// already in order
		}
		else if (order == 0) {
			for (int y = 0; y < h; y++) {
				final int w2 = w / 2;
				for (int x = 0; x < w2; x++) {
					swap(pixels, (y * w) + x, (y * w) + (w - 1 - x));
				}
			}
		}
		else if (order == 2) {
			for (int y = 0; y < h; y++) {
				final int w2 = w / 2;
				for (int x = 0; x < w2; x++) {
					swap(pixels, (y * w) + x, ((h - 1 - y) * w) + (w - 1 - x));
				}
			}
		}
		else {
			throw new UnsupportedOperationException("Error " + name + " (" + order + ")");
		}
		return new PixelImage(w, h, pixels, false);
	}

	private static void swap(final int[] pixels, final int a, final int b) {
		final int t = pixels[a];
		pixels[a] = pixels[b];
		pixels[b] = t;
	}

	private TgaDecoder() {
	}
}
