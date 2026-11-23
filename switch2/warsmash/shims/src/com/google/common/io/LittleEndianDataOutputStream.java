package com.google.common.io;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * warsmash-nx: stand-in for Guava's class of the same name (see LittleEndianDataInputStream).
 * Little-endian numbers; boolean, byte, bytes and UTF as DataOutputStream.
 */
public final class LittleEndianDataOutputStream extends FilterOutputStream implements DataOutput {
	public LittleEndianDataOutputStream(final OutputStream out) {
		super(new DataOutputStream(java.util.Objects.requireNonNull(out)));
	}

	@Override
	public void write(final byte[] b, final int off, final int len) throws IOException {
		out.write(b, off, len);
	}

	@Override
	public void writeBoolean(final boolean v) throws IOException {
		((DataOutputStream) out).writeBoolean(v);
	}

	@Override
	public void writeByte(final int v) throws IOException {
		((DataOutputStream) out).writeByte(v);
	}

	@Override
	public void writeBytes(final String s) throws IOException {
		((DataOutputStream) out).writeBytes(s);
	}

	@Override
	public void writeChar(final int v) throws IOException {
		writeShort(v);
	}

	@Override
	public void writeChars(final String s) throws IOException {
		for (int i = 0; i < s.length(); i++) {
			writeChar(s.charAt(i));
		}
	}

	@Override
	public void writeDouble(final double v) throws IOException {
		writeLong(Double.doubleToLongBits(v));
	}

	@Override
	public void writeFloat(final float v) throws IOException {
		writeInt(Float.floatToIntBits(v));
	}

	@Override
	public void writeInt(final int v) throws IOException {
		out.write(0xFF & v);
		out.write(0xFF & (v >> 8));
		out.write(0xFF & (v >> 16));
		out.write(0xFF & (v >> 24));
	}

	@Override
	public void writeLong(final long v) throws IOException {
		writeInt((int) v);
		writeInt((int) (v >>> 32));
	}

	@Override
	public void writeShort(final int v) throws IOException {
		out.write(0xFF & v);
		out.write(0xFF & (v >> 8));
	}

	@Override
	public void writeUTF(final String str) throws IOException {
		((DataOutputStream) out).writeUTF(str);
	}

	@Override
	public void close() throws IOException {
		out.close();
	}
}
