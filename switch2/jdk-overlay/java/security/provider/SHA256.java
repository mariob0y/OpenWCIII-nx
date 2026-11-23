package java.security.provider;

import java.util.Arrays;

/**
 * warsmash-nx: SHA-256 (FIPS 180-4) for Clearwing, whose MessageDigest only has MD5. OpenWCIII hashes
 * profile names (gamecache folders) and mission checkpoints with SHA-256. Built like Clearwing's MD5 on
 * its DigestBase.
 */
public final class SHA256 extends DigestBase {
	private static final int[] K = { 0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
			0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe,
			0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa,
			0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
			0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb,
			0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624,
			0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
			0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb,
			0xbef9a3f7, 0xc67178f2 };

	private int[] state = new int[8];
	private int[] w = new int[64];

	public SHA256() {
		super("SHA-256", 32, 64);
		resetHashes();
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		final SHA256 copy = (SHA256) super.clone();
		copy.state = copy.state.clone();
		copy.w = new int[64];
		return copy;
	}

	@Override
	void implReset() {
		resetHashes();
		Arrays.fill(this.w, 0);
	}

	private void resetHashes() {
		this.state[0] = 0x6a09e667;
		this.state[1] = 0xbb67ae85;
		this.state[2] = 0x3c6ef372;
		this.state[3] = 0xa54ff53a;
		this.state[4] = 0x510e527f;
		this.state[5] = 0x9b05688c;
		this.state[6] = 0x1f83d9ab;
		this.state[7] = 0x5be0cd19;
	}

	@Override
	void implDigest(final byte[] out, final int ofs) {
		final long bitsProcessed = this.bytesProcessed << 3;
		final int index = (int) this.bytesProcessed & 63;
		final int padLen = index < 56 ? 56 - index : 120 - index;
		engineUpdate(padding, 0, padLen);
		ByteArrayAccess.i2bBig4((int) (bitsProcessed >>> 32), this.buffer, 56);
		ByteArrayAccess.i2bBig4((int) bitsProcessed, this.buffer, 60);
		implCompress(this.buffer, 0);
		ByteArrayAccess.i2bBig(this.state, 0, out, ofs, 32);
	}

	@Override
	void implCompress(final byte[] buf, final int ofs) {
		final int[] w = this.w;
		for (int t = 0; t < 16; t++) {
			final int p = ofs + (t * 4);
			w[t] = ((buf[p] & 0xFF) << 24) | ((buf[p + 1] & 0xFF) << 16) | ((buf[p + 2] & 0xFF) << 8)
					| (buf[p + 3] & 0xFF);
		}
		for (int t = 16; t < 64; t++) {
			final int a = w[t - 15];
			final int b = w[t - 2];
			final int s0 = Integer.rotateRight(a, 7) ^ Integer.rotateRight(a, 18) ^ (a >>> 3);
			final int s1 = Integer.rotateRight(b, 17) ^ Integer.rotateRight(b, 19) ^ (b >>> 10);
			w[t] = w[t - 16] + s0 + w[t - 7] + s1;
		}
		int a = this.state[0];
		int b = this.state[1];
		int c = this.state[2];
		int d = this.state[3];
		int e = this.state[4];
		int f = this.state[5];
		int g = this.state[6];
		int h = this.state[7];
		for (int t = 0; t < 64; t++) {
			final int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
			final int ch = (e & f) ^ (~e & g);
			final int t1 = h + s1 + ch + K[t] + w[t];
			final int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
			final int maj = (a & b) ^ (a & c) ^ (b & c);
			final int t2 = s0 + maj;
			h = g;
			g = f;
			f = e;
			e = d + t1;
			d = c;
			c = b;
			b = a;
			a = t1 + t2;
		}
		this.state[0] += a;
		this.state[1] += b;
		this.state[2] += c;
		this.state[3] += d;
		this.state[4] += e;
		this.state[5] += f;
		this.state[6] += g;
		this.state[7] += h;
	}
}
