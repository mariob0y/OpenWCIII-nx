package com.etheller.warsmash.util;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Forces a written file to the disk before it replaces the old one (saves). warsmash-nx: in a class of its
 * own because Clearwing's FileOutputStream has no getFD; the Switch 2 build uses a stand-in that only
 * flushes.
 */
public final class FileSync {
	private FileSync() {
	}

	public static void sync(final FileOutputStream stream) throws IOException {
		stream.getFD().sync();
	}
}
