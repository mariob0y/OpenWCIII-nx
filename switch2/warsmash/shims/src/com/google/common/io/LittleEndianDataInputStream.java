package com.google.common.io;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * warsmash-nx: stand-in for Guava's class of the same name (Warsmash uses nothing else from Guava),
 * so the console build does not carry Guava. Same behaviour: little-endian numbers, EOFException at
 * the end, readUTF as DataInputStream, readLine unsupported.
 */
public final class LittleEndianDataInputStream extends FilterInputStream implements DataInput {
	public LittleEndianDataInputStream(final InputStream in) {
		super(java.util.Objects.requireNonNull(in));
	}

	@Override
	public String readLine() {
		throw new UnsupportedOperationException("readLine is not supported");
	}

	@Override
	public void readFully(final byte[] b) throws IOException {
		readFully(b, 0, b.length);
	}

	@Override
	public void readFully(final byte[] b, final int off, final int len) throws IOException {
		int done = 0;
		while (done < len) {
			final int n = in.read(b, off + done, len - done);
			if (n < 0) {
				throw new EOFException(
						"reached end of stream after reading " + done + " bytes; " + len + " bytes expected");
			}
			done += n;
		}
	}

	@Override
	public int skipBytes(final int n) throws IOException {
		return (int) in.skip(n);
	}

	@Override
	public int readUnsignedByte() throws IOException {
		final int b1 = in.read();
		if (b1 < 0) {
			throw new EOFException();
		}
		return b1;
	}

	@Override
	public int readUnsignedShort() throws IOException {
		final int b1 = readUnsignedByte();
		final int b2 = readUnsignedByte();
		return (b2 << 8) | b1;
	}

	@Override
	public int readInt() throws IOException {
		final int b1 = readUnsignedByte();
		final int b2 = readUnsignedByte();
		final int b3 = readUnsignedByte();
		final int b4 = readUnsignedByte();
		return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
	}

	@Override
	public long readLong() throws IOException {
		final long low = readInt() & 0xFFFFFFFFL;
		final long high = readInt() & 0xFFFFFFFFL;
		return (high << 32) | low;
	}

	@Override
	public float readFloat() throws IOException {
		return Float.intBitsToFloat(readInt());
	}

	@Override
	public double readDouble() throws IOException {
		return Double.longBitsToDouble(readLong());
	}

	@Override
	public String readUTF() throws IOException {
		return new DataInputStream(in).readUTF();
	}

	@Override
	public short readShort() throws IOException {
		return (short) readUnsignedShort();
	}

	@Override
	public char readChar() throws IOException {
		return (char) readUnsignedShort();
	}

	@Override
	public byte readByte() throws IOException {
		return (byte) readUnsignedByte();
	}

	@Override
	public boolean readBoolean() throws IOException {
		return readUnsignedByte() != 0;
	}
}
