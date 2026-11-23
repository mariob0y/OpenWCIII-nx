package org.apache.commons.compress.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** warsmash-nx: stand-in for commons-compress's IOUtils; Warsmash only calls toByteArray. */
public final class IOUtils {
	private IOUtils() {
	}

	public static byte[] toByteArray(final InputStream input) throws IOException {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final byte[] buffer = new byte[8024];
		int n;
		while ((n = input.read(buffer)) != -1) {
			output.write(buffer, 0, n);
		}
		return output.toByteArray();
	}
}
