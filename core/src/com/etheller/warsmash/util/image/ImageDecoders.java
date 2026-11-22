package com.etheller.warsmash.util.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Stands in for javax.imageio.ImageIO.read in core: picks BLP or DDS by
 * content and decodes it without AWT.
 */
public final class ImageDecoders {
	/**
	 * Same contract as ImageIO.read(InputStream): throws IllegalArgumentException
	 * for a null stream, returns null for a format it does not know, and does not
	 * close the stream.
	 */
	public static PixelImage read(final InputStream stream) throws IOException {
		if (stream == null) {
			throw new IllegalArgumentException("input == null!");
		}
		return decode(readAll(stream));
	}

	/** Decodes BLP or DDS bytes; null if they are neither. */
	public static PixelImage decode(final byte[] data) {
		if (BlpDecoder.isBlp(data)) {
			return BlpDecoder.decode(data);
		}
		if (DdsDecoder.isDds(data)) {
			return DdsDecoder.decode(data);
		}
		return null;
	}

	public static byte[] readAll(final InputStream stream) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(stream.available(), 16384));
		final byte[] buffer = new byte[16384];
		int n;
		while ((n = stream.read(buffer)) != -1) {
			out.write(buffer, 0, n);
		}
		return out.toByteArray();
	}

	private ImageDecoders() {
	}
}
