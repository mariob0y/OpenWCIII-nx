package java.util.zip;

import java.nio.ByteBuffer;

/**
 * warsmash-nx: missing from Clearwing 3.1.2. CRC-32C (Castagnoli, reflected polynomial 0x82F63B78),
 * initial value and final complement as the JDK's class: getValue() of "123456789" is 0xE3069283.
 */
public final class CRC32C implements Checksum {
	private static final int[] TABLE = new int[256];

	static {
		for (int n = 0; n < 256; n++) {
			int c = n;
			for (int k = 0; k < 8; k++) {
				c = (c & 1) != 0 ? (c >>> 1) ^ 0x82F63B78 : c >>> 1;
			}
			TABLE[n] = c;
		}
	}

	private int crc = 0xFFFFFFFF;

	public CRC32C() {
	}

	@Override
	public void update(final int b) {
		this.crc = (this.crc >>> 8) ^ TABLE[(this.crc ^ b) & 0xFF];
	}

	@Override
	public void update(final byte[] b, final int off, final int len) {
		if (b == null) {
			throw new NullPointerException();
		}
		if ((off < 0) || (len < 0) || (off > (b.length - len))) {
			throw new ArrayIndexOutOfBoundsException();
		}
		int c = this.crc;
		for (int i = off; i < (off + len); i++) {
			c = (c >>> 8) ^ TABLE[(c ^ b[i]) & 0xFF];
		}
		this.crc = c;
	}

	public void update(final byte[] b) {
		update(b, 0, b.length);
	}

	public void update(final ByteBuffer buffer) {
		int c = this.crc;
		while (buffer.hasRemaining()) {
			c = (c >>> 8) ^ TABLE[(c ^ buffer.get()) & 0xFF];
		}
		this.crc = c;
	}

	@Override
	public long getValue() {
		return (~this.crc) & 0xFFFFFFFFL;
	}

	@Override
	public void reset() {
		this.crc = 0xFFFFFFFF;
	}
}
