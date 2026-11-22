package com.etheller.warsmash.util.image;

/**
 * A baseline (Huffman, 8-bit) JPEG decoder that returns the raw component
 * planes with no colour conversion, the way ImageIO's JPEG readRaster did for
 * the four-channel JPEGs inside BLP1 textures.
 *
 * The inverse DCT is a line-for-line port of libjpeg 6b's jidctint.c (the
 * "ISLOW" method, libjpeg's default and the one the JDK's bundled libjpeg uses),
 * including its zero-AC shortcuts and its range-limit table, so the output
 * matches the JDK bit for bit.
 *
 * Only images where every component has sampling factors 1x1 are supported:
 * that is every BLP1 in WarCraft III, and it means no upsampling step (whose
 * libjpeg "fancy" variant would also have to be ported exactly).
 */
public final class JpegDecoder {
	private static final int[] ZIGZAG = { 0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40,
			48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28, 35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58,
			59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63,
			// libjpeg's jpeg_natural_order has 16 extra entries so a corrupt run cannot
			// index past the block; they all point at the last coefficient.
			63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 };

	/** The decoded image: one plane per component, width*height bytes each. */
	public static final class Result {
		public final int width;
		public final int height;
		public final byte[][] planes;

		Result(final int width, final int height, final byte[][] planes) {
			this.width = width;
			this.height = height;
			this.planes = planes;
		}
	}

	private static final class Component {
		int id;
		int h;
		int v;
		int quantTable;
		int dcTable;
		int acTable;
		int blocksWide;
		int blocksHigh;
		int dcPred;
		byte[] plane;
	}

	private static final class Huffman {
		/** Fast path: (length << 8) | value for every 9-bit prefix, 0 if longer. */
		final int[] lookup = new int[1 << 9];
		final int[] maxCode = new int[18];
		final int[] valOffset = new int[17];
		final int[] values;

		Huffman(final int[] counts, final int[] values) {
			this.values = values;
			int code = 0;
			int k = 0;
			for (int len = 1; len <= 16; len++) {
				this.valOffset[len] = k - code;
				for (int i = 0; i < counts[len]; i++) {
					if (len <= 9) {
						final int shift = 9 - len;
						final int base = code << shift;
						for (int f = 0; f < (1 << shift); f++) {
							this.lookup[base + f] = (len << 8) | values[k];
						}
					}
					code++;
					k++;
				}
				this.maxCode[len] = counts[len] == 0 ? -1 : code - 1;
				code <<= 1;
			}
			this.maxCode[17] = Integer.MAX_VALUE; // sentinel, as in libjpeg
		}
	}

	private final byte[] data;
	private int pos;
	private final int end;

	private final int[][] quant = new int[4][];
	private final Huffman[] dcTables = new Huffman[4];
	private final Huffman[] acTables = new Huffman[4];
	private Component[] components;
	private int width;
	private int height;
	private int restartInterval;

	// entropy decoder state
	private int bitBuffer;
	private int bitCount;
	private boolean hitMarker;

	private JpegDecoder(final byte[] data, final int offset, final int length) {
		this.data = data;
		this.pos = offset;
		this.end = offset + length;
	}

	public static Result decode(final byte[] data, final int offset, final int length) {
		final JpegDecoder decoder = new JpegDecoder(data, offset, length);
		decoder.run();
		final byte[][] planes = new byte[decoder.components.length][];
		for (int i = 0; i < planes.length; i++) {
			planes[i] = decoder.components[i].plane;
		}
		return new Result(decoder.width, decoder.height, planes);
	}

	private void run() {
		if ((u8() != 0xFF) || (u8() != 0xD8)) {
			throw new IllegalArgumentException("Not a JPEG (no SOI)");
		}
		boolean sawScan = false;
		while (this.pos < this.end) {
			int b = u8();
			if (b != 0xFF) {
				continue; // garbage between markers; libjpeg skips it with a warning
			}
			do {
				b = u8();
			}
			while ((b == 0xFF) && (this.pos < this.end));
			switch (b) {
			case 0xD9: // EOI
				if (!sawScan) {
					throw new IllegalArgumentException("JPEG has no scan");
				}
				return;
			case 0xC0:
			case 0xC1:
				readFrame();
				break;
			case 0xC2:
			case 0xC3:
			case 0xC5:
			case 0xC6:
			case 0xC7:
			case 0xC9:
			case 0xCA:
			case 0xCB:
			case 0xCD:
			case 0xCE:
			case 0xCF:
				throw new UnsupportedOperationException(
						"Only baseline JPEG is supported (SOF" + (b - 0xC0) + ")");
			case 0xC4:
				readHuffmanTables();
				break;
			case 0xDB:
				readQuantTables();
				break;
			case 0xDD:
				u16();
				this.restartInterval = u16();
				break;
			case 0xDA:
				readScan();
				sawScan = true;
				break;
			case 0x01:
			case 0xD0:
			case 0xD1:
			case 0xD2:
			case 0xD3:
			case 0xD4:
			case 0xD5:
			case 0xD6:
			case 0xD7:
				break; // stray markers with no length
			default:
				this.pos += u16() - 2; // APPn, COM, DNL and friends
				break;
			}
		}
		if (!sawScan) {
			throw new IllegalArgumentException("JPEG has no scan");
		}
	}

	private void readQuantTables() {
		final int segmentEnd = (this.pos + u16()) - 2;
		while (this.pos < segmentEnd) {
			final int pq = u8();
			final int[] table = new int[64];
			for (int i = 0; i < 64; i++) {
				table[ZIGZAG[i]] = (pq >> 4) == 0 ? u8() : u16();
			}
			this.quant[pq & 3] = table;
		}
	}

	private void readHuffmanTables() {
		final int segmentEnd = (this.pos + u16()) - 2;
		while (this.pos < segmentEnd) {
			final int tc = u8();
			final int[] counts = new int[17];
			int total = 0;
			for (int i = 1; i <= 16; i++) {
				counts[i] = u8();
				total += counts[i];
			}
			if (total > 256) {
				throw new IllegalArgumentException("Bad Huffman table");
			}
			final int[] values = new int[total];
			for (int i = 0; i < total; i++) {
				values[i] = u8();
			}
			final Huffman table = new Huffman(counts, values);
			if ((tc >> 4) == 0) {
				this.dcTables[tc & 3] = table;
			}
			else {
				this.acTables[tc & 3] = table;
			}
		}
	}

	private void readFrame() {
		u16();
		final int precision = u8();
		if (precision != 8) {
			throw new UnsupportedOperationException("Only 8-bit JPEG is supported, got " + precision);
		}
		this.height = u16();
		this.width = u16();
		final int count = u8();
		if ((this.width <= 0) || (this.height <= 0) || (count <= 0)) {
			throw new IllegalArgumentException("Bad JPEG frame " + this.width + "x" + this.height + "x" + count);
		}
		this.components = new Component[count];
		for (int i = 0; i < count; i++) {
			final Component c = new Component();
			c.id = u8();
			final int hv = u8();
			c.h = hv >> 4;
			c.v = hv & 15;
			c.quantTable = u8() & 3;
			if ((c.h != 1) || (c.v != 1)) {
				throw new UnsupportedOperationException("Only 1x1 JPEG sampling is supported");
			}
			c.blocksWide = (this.width + 7) >> 3;
			c.blocksHigh = (this.height + 7) >> 3;
			c.plane = new byte[this.width * this.height];
			this.components[i] = c;
		}
	}

	private void readScan() {
		if (this.components == null) {
			throw new IllegalArgumentException("JPEG scan before frame");
		}
		u16();
		final int count = u8();
		final Component[] scan = new Component[count];
		for (int i = 0; i < count; i++) {
			final int id = u8();
			final int tables = u8();
			for (final Component c : this.components) {
				if (c.id == id) {
					scan[i] = c;
				}
			}
			if (scan[i] == null) {
				throw new IllegalArgumentException("JPEG scan names unknown component " + id);
			}
			scan[i].dcTable = tables >> 4;
			scan[i].acTable = tables & 15;
		}
		final int ss = u8();
		final int se = u8();
		u8(); // Ah/Al
		if ((ss != 0) || (se != 63)) {
			throw new UnsupportedOperationException("Only sequential JPEG scans are supported");
		}
		for (final Component c : scan) {
			if ((this.dcTables[c.dcTable] == null) || (this.acTables[c.acTable] == null)
					|| (this.quant[c.quantTable] == null)) {
				throw new IllegalArgumentException("JPEG scan uses an undefined table");
			}
			c.dcPred = 0;
		}

		this.bitBuffer = 0;
		this.bitCount = 0;
		this.hitMarker = false;

		final int[] coef = new int[64];
		final int[] workspace = new int[64];
		// Interleaved scans walk MCUs; with 1x1 sampling an MCU is one block per
		// component. A single-component scan walks that component's own blocks.
		final int blocksWide = scan[0].blocksWide;
		final int blocksHigh = scan[0].blocksHigh;
		final int units = blocksWide * blocksHigh;
		int untilRestart = this.restartInterval;
		for (int unit = 0; unit < units; unit++) {
			if ((this.restartInterval != 0) && (untilRestart == 0)) {
				processRestart(scan);
				untilRestart = this.restartInterval;
			}
			final int bx = unit % blocksWide;
			final int by = unit / blocksWide;
			for (final Component c : scan) {
				decodeBlock(c, coef);
				idctIslow(coef, this.quant[c.quantTable], workspace, c.plane, bx << 3, by << 3);
			}
			untilRestart--;
		}
		// Position the parser after the entropy-coded data, at the next marker.
		while (this.pos < (this.end - 1)) {
			if ((this.data[this.pos] & 0xFF) == 0xFF) {
				final int next = this.data[this.pos + 1] & 0xFF;
				if ((next != 0) && ((next < 0xD0) || (next > 0xD7))) {
					break;
				}
			}
			this.pos++;
		}
	}

	private void processRestart(final Component[] scan) {
		// Discard the rest of the current byte and find the RSTn marker.
		this.bitBuffer = 0;
		this.bitCount = 0;
		while (this.pos < (this.end - 1)) {
			if (((this.data[this.pos] & 0xFF) == 0xFF) && ((this.data[this.pos + 1] & 0xF8) == 0xD0)) {
				this.pos += 2;
				break;
			}
			if (((this.data[this.pos] & 0xFF) == 0xFF) && (this.data[this.pos + 1] != 0)
					&& ((this.data[this.pos + 1] & 0xFF) != 0xFF)) {
				break; // some other marker: leave it for the caller
			}
			this.pos++;
		}
		this.hitMarker = false;
		for (final Component c : scan) {
			c.dcPred = 0;
		}
	}

	private void decodeBlock(final Component c, final int[] coef) {
		java.util.Arrays.fill(coef, 0);
		final Huffman dc = this.dcTables[c.dcTable];
		final Huffman ac = this.acTables[c.acTable];
		int s = decodeHuffman(dc);
		int diff = 0;
		if (s != 0) {
			diff = extend(receive(s), s);
		}
		c.dcPred += diff;
		coef[0] = (short) c.dcPred; // JCOEF is 16 bits in libjpeg
		for (int k = 1; k < 64; k++) {
			final int rs = decodeHuffman(ac);
			final int r = rs >> 4;
			s = rs & 15;
			if (s != 0) {
				k += r;
				coef[ZIGZAG[k]] = (short) extend(receive(s), s);
			}
			else {
				if (r != 15) {
					break;
				}
				k += 15;
			}
		}
	}

	private int decodeHuffman(final Huffman table) {
		fill(17);
		final int peek = (this.bitBuffer >>> (this.bitCount - 9)) & 0x1FF;
		final int fast = table.lookup[peek];
		if (fast != 0) {
			this.bitCount -= fast >> 8;
			return fast & 0xFF;
		}
		int code = 0;
		for (int len = 1; len <= 16; len++) {
			code = (code << 1) | ((this.bitBuffer >>> (this.bitCount - len)) & 1);
			if (code <= table.maxCode[len]) {
				this.bitCount -= len;
				final int index = code + table.valOffset[len];
				return (index >= 0) && (index < table.values.length) ? table.values[index] : 0;
			}
		}
		// Corrupt data: libjpeg reads a 17th bit, warns and returns 0.
		this.bitCount -= 17;
		return 0;
	}

	private int receive(final int bits) {
		fill(bits);
		this.bitCount -= bits;
		return (this.bitBuffer >>> this.bitCount) & ((1 << bits) - 1);
	}

	private static int extend(final int value, final int bits) {
		return value < (1 << (bits - 1)) ? (value - (1 << bits)) + 1 : value;
	}

	/** Make sure at least {@code need} bits are buffered; past the data or at a marker, feed zeros like libjpeg. */
	private void fill(final int need) {
		while (this.bitCount < need) {
			int b = 0;
			if (!this.hitMarker && (this.pos < this.end)) {
				b = this.data[this.pos] & 0xFF;
				if (b == 0xFF) {
					final int next = this.pos + 1 < this.end ? this.data[this.pos + 1] & 0xFF : 0xD9;
					if (next == 0) {
						this.pos += 2;
					}
					else {
						this.hitMarker = true;
						b = 0;
					}
				}
				else {
					this.pos++;
				}
			}
			this.bitBuffer = (this.bitBuffer << 8) | b;
			this.bitCount += 8;
		}
		if (this.bitCount > 24) {
			this.bitBuffer &= (1 << this.bitCount) - 1;
		}
	}

	// ---- libjpeg 6b jidctint.c ----

	private static final int CONST_BITS = 13;
	private static final int PASS1_BITS = 2;
	private static final int FIX_0_298631336 = 2446;
	private static final int FIX_0_390180644 = 3196;
	private static final int FIX_0_541196100 = 4433;
	private static final int FIX_0_765366865 = 6270;
	private static final int FIX_0_899976223 = 7373;
	private static final int FIX_1_175875602 = 9633;
	private static final int FIX_1_501321110 = 12299;
	private static final int FIX_1_847759065 = 15137;
	private static final int FIX_1_961570560 = 16069;
	private static final int FIX_2_053119869 = 16819;
	private static final int FIX_2_562915447 = 20995;
	private static final int FIX_3_072711026 = 25172;

	private static int descale(final int x, final int n) {
		return (x + (1 << (n - 1))) >> n;
	}

	/**
	 * libjpeg's IDCT_range_limit(cinfo)[x & RANGE_MASK]: the IDCT output is
	 * centred on 0; in range it becomes x + 128, moderate overflow clamps, and the
	 * table wraps beyond that exactly as libjpeg's does.
	 */
	private static int rangeLimit(final int x) {
		final int v = x & 1023;
		if (v < 128) {
			return v + 128;
		}
		if (v < 512) {
			return 255;
		}
		if (v < 896) {
			return 0;
		}
		return v - 896;
	}

	private void idctIslow(final int[] coef, final int[] q, final int[] ws, final byte[] plane, final int x0,
			final int y0) {
		// Pass 1: columns from input, results into the work array, scaled up by
		// sqrt(8) * 2^PASS1_BITS.
		for (int col = 0; col < 8; col++) {
			if ((coef[col + 8] == 0) && (coef[col + 16] == 0) && (coef[col + 24] == 0) && (coef[col + 32] == 0)
					&& (coef[col + 40] == 0) && (coef[col + 48] == 0) && (coef[col + 56] == 0)) {
				final int dcval = (coef[col] * q[col]) << PASS1_BITS;
				for (int r = 0; r < 8; r++) {
					ws[col + (r * 8)] = dcval;
				}
				continue;
			}
			int z2 = coef[col + 16] * q[col + 16];
			int z3 = coef[col + 48] * q[col + 48];
			int z1 = (z2 + z3) * FIX_0_541196100;
			int tmp2 = z1 + (z3 * -FIX_1_847759065);
			int tmp3 = z1 + (z2 * FIX_0_765366865);

			z2 = coef[col] * q[col];
			z3 = coef[col + 32] * q[col + 32];
			int tmp0 = (z2 + z3) << CONST_BITS;
			int tmp1 = (z2 - z3) << CONST_BITS;

			final int tmp10 = tmp0 + tmp3;
			final int tmp13 = tmp0 - tmp3;
			final int tmp11 = tmp1 + tmp2;
			final int tmp12 = tmp1 - tmp2;

			tmp0 = coef[col + 56] * q[col + 56];
			tmp1 = coef[col + 40] * q[col + 40];
			tmp2 = coef[col + 24] * q[col + 24];
			tmp3 = coef[col + 8] * q[col + 8];

			z1 = tmp0 + tmp3;
			z2 = tmp1 + tmp2;
			z3 = tmp0 + tmp2;
			int z4 = tmp1 + tmp3;
			final int z5 = (z3 + z4) * FIX_1_175875602;

			tmp0 = tmp0 * FIX_0_298631336;
			tmp1 = tmp1 * FIX_2_053119869;
			tmp2 = tmp2 * FIX_3_072711026;
			tmp3 = tmp3 * FIX_1_501321110;
			z1 = z1 * -FIX_0_899976223;
			z2 = z2 * -FIX_2_562915447;
			z3 = z3 * -FIX_1_961570560;
			z4 = z4 * -FIX_0_390180644;

			z3 += z5;
			z4 += z5;

			tmp0 += z1 + z3;
			tmp1 += z2 + z4;
			tmp2 += z2 + z3;
			tmp3 += z1 + z4;

			final int n = CONST_BITS - PASS1_BITS;
			ws[col] = descale(tmp10 + tmp3, n);
			ws[col + 56] = descale(tmp10 - tmp3, n);
			ws[col + 8] = descale(tmp11 + tmp2, n);
			ws[col + 48] = descale(tmp11 - tmp2, n);
			ws[col + 16] = descale(tmp12 + tmp1, n);
			ws[col + 40] = descale(tmp12 - tmp1, n);
			ws[col + 24] = descale(tmp13 + tmp0, n);
			ws[col + 32] = descale(tmp13 - tmp0, n);
		}

		// Pass 2: rows from the work array, output into the plane, cropped to the
		// image.
		final int w = this.width;
		final int h = this.height;
		for (int row = 0; row < 8; row++) {
			final int y = y0 + row;
			final int o = row * 8;
			if (y >= h) {
				break;
			}
			final int rowStart = (y * w) + x0;
			final int cols = Math.min(8, w - x0);
			if ((ws[o + 1] == 0) && (ws[o + 2] == 0) && (ws[o + 3] == 0) && (ws[o + 4] == 0) && (ws[o + 5] == 0)
					&& (ws[o + 6] == 0) && (ws[o + 7] == 0)) {
				final byte outval = (byte) rangeLimit(descale(ws[o], PASS1_BITS + 3));
				for (int c = 0; c < cols; c++) {
					plane[rowStart + c] = outval;
				}
				continue;
			}
			int z2 = ws[o + 2];
			int z3 = ws[o + 6];
			int z1 = (z2 + z3) * FIX_0_541196100;
			int tmp2 = z1 + (z3 * -FIX_1_847759065);
			int tmp3 = z1 + (z2 * FIX_0_765366865);

			int tmp0 = (ws[o] + ws[o + 4]) << CONST_BITS;
			int tmp1 = (ws[o] - ws[o + 4]) << CONST_BITS;

			final int tmp10 = tmp0 + tmp3;
			final int tmp13 = tmp0 - tmp3;
			final int tmp11 = tmp1 + tmp2;
			final int tmp12 = tmp1 - tmp2;

			tmp0 = ws[o + 7];
			tmp1 = ws[o + 5];
			tmp2 = ws[o + 3];
			tmp3 = ws[o + 1];

			z1 = tmp0 + tmp3;
			z2 = tmp1 + tmp2;
			z3 = tmp0 + tmp2;
			int z4 = tmp1 + tmp3;
			final int z5 = (z3 + z4) * FIX_1_175875602;

			tmp0 = tmp0 * FIX_0_298631336;
			tmp1 = tmp1 * FIX_2_053119869;
			tmp2 = tmp2 * FIX_3_072711026;
			tmp3 = tmp3 * FIX_1_501321110;
			z1 = z1 * -FIX_0_899976223;
			z2 = z2 * -FIX_2_562915447;
			z3 = z3 * -FIX_1_961570560;
			z4 = z4 * -FIX_0_390180644;

			z3 += z5;
			z4 += z5;

			tmp0 += z1 + z3;
			tmp1 += z2 + z4;
			tmp2 += z2 + z3;
			tmp3 += z1 + z4;

			final int n = CONST_BITS + PASS1_BITS + 3;
			final int[] out = { descale(tmp10 + tmp3, n), descale(tmp11 + tmp2, n), descale(tmp12 + tmp1, n),
					descale(tmp13 + tmp0, n), descale(tmp13 - tmp0, n), descale(tmp12 - tmp1, n),
					descale(tmp11 - tmp2, n), descale(tmp10 - tmp3, n) };
			for (int c = 0; c < cols; c++) {
				plane[rowStart + c] = (byte) rangeLimit(out[c]);
			}
		}
	}

	private int u8() {
		if (this.pos >= this.end) {
			throw new IllegalArgumentException("JPEG data ends early");
		}
		return this.data[this.pos++] & 0xFF;
	}

	private int u16() {
		return (u8() << 8) | u8();
	}
}
