package org.apache.commons.compress.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

/**
 * warsmash-nx: stand-in for commons-compress 1.20's class of the same name (Warsmash reads maps from
 * memory through it). Same behaviour: read returns -1 at the end, write grows the array by doubling,
 * positions limited to Integer.MAX_VALUE.
 */
public class SeekableInMemoryByteChannel implements SeekableByteChannel {
	private static final int NAIVE_RESIZE_LIMIT = Integer.MAX_VALUE >> 1;

	private byte[] data;
	private boolean closed;
	private int position, size;

	public SeekableInMemoryByteChannel(final byte[] data) {
		this.data = data;
		size = data.length;
	}

	public SeekableInMemoryByteChannel() {
		this(new byte[0]);
	}

	public SeekableInMemoryByteChannel(final int size) {
		this(new byte[size]);
	}

	@Override
	public long position() {
		return position;
	}

	@Override
	public SeekableByteChannel position(final long newPosition) throws IOException {
		ensureOpen();
		if ((newPosition < 0L) || (newPosition > Integer.MAX_VALUE)) {
			throw new IllegalArgumentException("Position has to be in range 0.. " + Integer.MAX_VALUE);
		}
		position = (int) newPosition;
		return this;
	}

	@Override
	public long size() {
		return size;
	}

	@Override
	public SeekableByteChannel truncate(final long newSize) {
		if ((newSize < 0L) || (newSize > Integer.MAX_VALUE)) {
			throw new IllegalArgumentException("Size has to be in range 0.. " + Integer.MAX_VALUE);
		}
		if (size > newSize) {
			size = (int) newSize;
		}
		if (position > size) {
			position = size;
		}
		return this;
	}

	@Override
	public int read(final ByteBuffer buf) throws IOException {
		ensureOpen();
		int wanted = buf.remaining();
		final int possible = size - position;
		if (possible <= 0) {
			return -1;
		}
		if (wanted > possible) {
			wanted = possible;
		}
		buf.put(data, position, wanted);
		position += wanted;
		return wanted;
	}

	@Override
	public void close() {
		closed = true;
	}

	@Override
	public boolean isOpen() {
		return !closed;
	}

	@Override
	public int write(final ByteBuffer b) throws IOException {
		ensureOpen();
		int wanted = b.remaining();
		final int possibleWithoutResize = size - position;
		if (wanted > possibleWithoutResize) {
			final int newSize = position + wanted;
			if (newSize < 0) {
				resize(Integer.MAX_VALUE);
				wanted = Integer.MAX_VALUE - position;
			}
			else {
				resize(newSize);
			}
		}
		b.get(data, position, wanted);
		position += wanted;
		if (size < position) {
			size = position;
		}
		return wanted;
	}

	public byte[] array() {
		return data;
	}

	private void resize(final int newLength) {
		int len = data.length;
		if (len <= 0) {
			len = 1;
		}
		if (newLength < NAIVE_RESIZE_LIMIT) {
			while (len < newLength) {
				len <<= 1;
			}
		}
		else {
			len = newLength;
		}
		data = Arrays.copyOf(data, len);
	}

	private void ensureOpen() throws ClosedChannelException {
		if (!isOpen()) {
			throw new ClosedChannelException();
		}
	}
}
