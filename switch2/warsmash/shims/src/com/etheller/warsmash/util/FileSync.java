package com.etheller.warsmash.util;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * warsmash-nx console build: stands in for core's FileSync (FileOutputStream.getFD().sync()), which
 * Clearwing lacks. The stream is flushed; the Switch file system commits it on close.
 */
public final class FileSync {
	private FileSync() {
	}

	public static void sync(final FileOutputStream stream) throws IOException {
		stream.flush();
	}
}
