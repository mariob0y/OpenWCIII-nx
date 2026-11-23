import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.etheller.warsmash.util.image.BlpDecoder;
import com.etheller.warsmash.util.image.DdsDecoder;
import com.etheller.warsmash.util.image.PixelImage;
import com.etheller.warsmash.util.image.TgaDecoder;
import com.etheller.warsmash.viewer5.handlers.tga.TgaFile;

/**
 * Checks the AWT-free decoders against the old AWT/ImageIO path, pixel by
 * pixel, over every BLP and TGA under a folder, plus random DDS files.
 *
 * For a BLP it compares what core actually consumed: getRGB (what was uploaded
 * to OpenGL) and forceBufferedImagesRGB(...).getRGB (ground and cliff
 * textures).
 *
 * Usage: ImageCheck <wc3data folder> [ddsCount]
 */
public class ImageCheck {
	static final AtomicInteger files = new AtomicInteger();
	static final AtomicInteger exact = new AtomicInteger();
	static final AtomicInteger oldUnsupported = new AtomicInteger();
	static final ConcurrentLinkedQueue<String> problems = new ConcurrentLinkedQueue<>();

	public static void main(final String[] args) throws Exception {
		ImageIO.setUseCache(false);
		final List<Path> paths;
		try (Stream<Path> walk = Files.walk(new File(args[0]).toPath())) {
			paths = walk.filter(p -> {
				final String n = p.getFileName().toString().toLowerCase();
				return n.endsWith(".blp") || n.endsWith(".tga");
			}).collect(Collectors.toList());
		}
		final long start = System.currentTimeMillis();
		paths.parallelStream().forEach(ImageCheck::check);
		final int ddsCount = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
		checkRandomDds(ddsCount);
		checkRandomBlp(ddsCount);
		System.out.println("checked " + files.get() + " images (" + paths.size() + " files + " + ddsCount
				+ " random DDS + " + ddsCount + " random BLP) in " + ((System.currentTimeMillis() - start) / 1000)
				+ " s");
		System.out.println("bit-exact: " + exact.get() + ", problems: " + problems.size()
				+ ", old reader unsupported (decoded by the new one only): " + oldUnsupported.get());
		problems.stream().sorted().limit(100000).forEach(s -> System.out.println("  " + s));
		System.exit(problems.isEmpty() ? 0 : 1);
	}

	static void check(final Path path) {
		final String name = path.toString();
		try {
			final byte[] bytes = Files.readAllBytes(path);
			if (name.toLowerCase().endsWith(".tga")) {
				final BufferedImage old = TgaFile.readTGA(name, new ByteArrayInputStream(bytes));
				final PixelImage neu = TgaDecoder.decode(name, bytes);
				compare(name, "tga", rgb(old), old.getWidth(), old.getHeight(), neu.getRGB(), neu);
				return;
			}
			BufferedImage old;
			String oldError = null;
			try {
				old = ImageIO.read(new ByteArrayInputStream(bytes));
			}
			catch (final Exception e) {
				old = null;
				oldError = e.toString();
			}
			PixelImage neu;
			String newError = null;
			try {
				neu = BlpDecoder.decode(bytes);
			}
			catch (final Exception e) {
				neu = null;
				newError = e.toString();
			}
			if ((old == null) || (neu == null)) {
				if ((old == null) != (neu == null)) {
					problems.add(name + ": old " + (old == null ? "failed " + oldError : "ok") + ", new "
							+ (neu == null ? "failed " + newError : "ok"));
				}
				else {
					files.incrementAndGet();
					exact.incrementAndGet(); // both refuse it: same behaviour
				}
				return;
			}
			if (!compare(name, "getRGB", rgb(old), old.getWidth(), old.getHeight(), neu.getRGB(), neu)) {
				return;
			}
			final BufferedImage oldForced = forceBufferedImagesRGB(old);
			final PixelImage newForced = neu.forceSRGB();
			files.decrementAndGet(); // the forced pass is part of the same image
			exact.decrementAndGet();
			compare(name, "forceSRGB", rgb(oldForced), old.getWidth(), old.getHeight(), newForced.getRGB(),
					newForced);
		}
		catch (final Exception e) {
			problems.add(name + ": harness error " + e);
		}
	}

	static boolean compare(final String name, final String what, final int[] old, final int ow, final int oh,
			final int[] neu, final PixelImage n) {
		files.incrementAndGet();
		if ((ow != n.getWidth()) || (oh != n.getHeight())) {
			problems.add(name + " [" + what + "]: size old " + ow + "x" + oh + " new " + n.getWidth() + "x"
					+ n.getHeight());
			return false;
		}
		int differing = 0;
		int maxDiff = 0;
		int first = -1;
		for (int i = 0; i < old.length; i++) {
			if (old[i] != neu[i]) {
				if (first < 0) {
					first = i;
				}
				differing++;
				for (int s = 0; s < 32; s += 8) {
					maxDiff = Math.max(maxDiff, Math.abs(((old[i] >>> s) & 255) - ((neu[i] >>> s) & 255)));
				}
			}
		}
		if (differing == 0) {
			exact.incrementAndGet();
			return true;
		}
		problems.add(name + " [" + what + "]: " + differing + "/" + old.length + " pixels differ, max " + maxDiff
				+ "; first at (" + (first % ow) + "," + (first / ow) + ") old " + Integer.toHexString(old[first])
				+ " new " + Integer.toHexString(neu[first]));
		return false;
	}

	static int[] rgb(final BufferedImage image) {
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	/** Copied unchanged from the old core ImageUtils.forceBufferedImagesRGB. */
	static BufferedImage forceBufferedImagesRGB(final BufferedImage in) {
		final ColorSpace inCS = in.getColorModel().getColorSpace();
		final ColorSpace sRGBCS = ColorSpace.getInstance(ColorSpace.CS_sRGB);
		if (inCS == sRGBCS) {
			return in;
		}
		if (inCS.getNumComponents() != sRGBCS.getNumComponents()) {
			throw new IllegalArgumentException("Input color space has different number of components from sRGB.");
		}
		final ColorModel lRGBModel = new ComponentColorModel(inCS, true, false, Transparency.TRANSLUCENT,
				DataBuffer.TYPE_BYTE);
		final ColorModel sRGBModel = new ComponentColorModel(sRGBCS, true, false, Transparency.TRANSLUCENT,
				DataBuffer.TYPE_BYTE);
		final BufferedImage lRGB = new BufferedImage(lRGBModel,
				lRGBModel.createCompatibleWritableRaster(in.getWidth(), in.getHeight()), false, null);
		for (int i = 0; i < in.getWidth(); i++) {
			for (int j = 0; j < in.getHeight(); j++) {
				lRGB.setRGB(i, j, in.getRGB(i, j));
			}
		}
		return new BufferedImage(sRGBModel, lRGB.getRaster(), false, null);
	}

	/** Random DXT1/3/5 and uncompressed DDS files, decoded by net.nikr.dds and by DdsDecoder. */
	static void checkRandomDds(final int count) {
		final Random random = new Random(12345);
		final String[] kinds = { "DXT1", "DXT3", "DXT5", "RGBA32", "RGB24", "RGB16" };
		final List<Integer> seeds = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			seeds.add(random.nextInt());
		}
		seeds.parallelStream().forEach(seed -> {
			final Random r = new Random(seed);
			final String kind = kinds[Math.floorMod(seed, kinds.length)];
			final int w = 1 + r.nextInt(70);
			final int h = 1 + r.nextInt(70);
			final byte[] dds = makeDds(kind, w, h, r);
			final String name = "random " + kind + " " + w + "x" + h + " seed " + seed;
			try {
				BufferedImage old = null;
				String oldError = null;
				try {
					old = ImageIO.read(new ByteArrayInputStream(dds));
				}
				catch (final Exception e) {
					oldError = e.toString();
				}
				if (old == null) {
					problems.add(name + ": old reader gave " + (oldError == null ? "null" : oldError));
					return;
				}
				final PixelImage neu = DdsDecoder.decode(dds);
				compare(name, "dds", rgb(old), old.getWidth(), old.getHeight(), neu.getRGB(), neu);
			}
			catch (final Exception e) {
				problems.add(name + ": " + e);
			}
		});
	}

	static byte[] makeDds(final String kind, final int w, final int h, final Random r) {
		final boolean dxt = kind.startsWith("DXT");
		final int bpp = kind.equals("RGBA32") ? 4 : kind.equals("RGB24") ? 3 : 2;
		final int dataSize = dxt ? ((w + 3) / 4) * ((h + 3) / 4) * (kind.equals("DXT1") ? 8 : 16) : w * h * bpp;
		final byte[] f = new byte[128 + dataSize];
		put(f, 0, 0x20534444);
		put(f, 4, 124);
		put(f, 8, 0x1 | 0x2 | 0x4 | 0x1000 | (dxt ? 0x80000 : 0x8));
		put(f, 12, h);
		put(f, 16, w);
		put(f, 20, dxt ? dataSize : w * bpp);
		put(f, 76, 32);
		if (dxt) {
			put(f, 80, 0x4);
			f[84] = 'D';
			f[85] = 'X';
			f[86] = 'T';
			f[87] = (byte) kind.charAt(3);
		}
		else if (bpp == 4) {
			put(f, 80, 0x40 | 0x1);
			put(f, 88, 32);
			put(f, 92, 0x00FF0000);
			put(f, 96, 0x0000FF00);
			put(f, 100, 0x000000FF);
			put(f, 104, 0xFF000000);
		}
		else if (bpp == 3) {
			put(f, 80, 0x40);
			put(f, 88, 24);
			put(f, 92, 0x00FF0000);
			put(f, 96, 0x0000FF00);
			put(f, 100, 0x000000FF);
		}
		else {
			put(f, 80, 0x40);
			put(f, 88, 16);
			put(f, 92, 0xF800);
			put(f, 96, 0x07E0);
			put(f, 100, 0x001F);
		}
		put(f, 108, 0x1000);
		final byte[] payload = new byte[dataSize];
		r.nextBytes(payload);
		if (kind.equals("DXT3") || kind.equals("DXT5")) {
			forceFourColorBlocks(payload, 16, 8);
		}
		System.arraycopy(payload, 0, f, 128, dataSize);
		return f;
	}

	/**
	 * DXT3/DXT5 colour blocks always use four colours (per the spec and GPUs), but
	 * net.nikr.dds applies DXT1's three-colour rule when c0 <= c1. Our decoder
	 * deliberately follows the spec, so random blocks are made c0 > c1, where
	 * both agree, and the rest of the format is still compared exactly.
	 */
	static void forceFourColorBlocks(final byte[] payload, final int blockSize, final int colorOffset) {
		for (int p = colorOffset; (p + 4) <= payload.length; p += blockSize) {
			int c0 = (payload[p] & 0xFF) | ((payload[p + 1] & 0xFF) << 8);
			int c1 = (payload[p + 2] & 0xFF) | ((payload[p + 3] & 0xFF) << 8);
			if (c0 < c1) {
				final int t = c0;
				c0 = c1;
				c1 = t;
			}
			if (c0 == c1) {
				if (c0 == 0xFFFF) {
					c1--;
				}
				else {
					c0++;
				}
			}
			payload[p] = (byte) c0;
			payload[p + 1] = (byte) (c0 >> 8);
			payload[p + 2] = (byte) c1;
			payload[p + 3] = (byte) (c1 >> 8);
		}
	}

	/** Random BLP1 palette files (all alpha depths) and BLP2 files, decoded by blp-iio-plugin and by BlpDecoder. */
	static void checkRandomBlp(final int count) {
		final Random random = new Random(777);
		final String[] kinds = { "BLP1-PAL-A0", "BLP1-PAL-A1", "BLP1-PAL-A4", "BLP1-PAL-A8", "BLP2-PAL-A0",
				"BLP2-PAL-A1", "BLP2-PAL-A4", "BLP2-PAL-A8", "BLP2-DXT1-A0", "BLP2-DXT1-A1", "BLP2-DXT3-A4",
				"BLP2-DXT5-A8", "BLP2-BGRA-A0", "BLP2-BGRA-A8" };
		final List<Integer> seeds = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			seeds.add(random.nextInt());
		}
		seeds.parallelStream().forEach(seed -> {
			final Random r = new Random(seed);
			final String kind = kinds[Math.floorMod(seed, kinds.length)];
			final int w = 1 << r.nextInt(8);
			final int h = 1 << r.nextInt(8);
			final byte[] blp = makeBlp(kind, w, h, r);
			final String name = "random " + kind + " " + w + "x" + h + " seed " + seed;
			try {
				BufferedImage old = null;
				String oldError = null;
				try {
					old = ImageIO.read(new ByteArrayInputStream(blp));
				}
				catch (final Exception e) {
					oldError = e.toString();
				}
				if (old == null) {
					if ((oldError != null) && oldError.contains("Unsupported content type")) {
						// blp-iio-plugin never read BLP2 DXT/BGRA: nothing to compare. Just
						// check the new decoder handles it.
						final PixelImage neu = BlpDecoder.decode(blp);
						if ((neu.getWidth() != w) || (neu.getHeight() != h)) {
							problems.add(name + ": new decoder gave " + neu.getWidth() + "x" + neu.getHeight());
						}
						oldUnsupported.incrementAndGet();
						return;
					}
					problems.add(name + ": old reader gave " + (oldError == null ? "null" : oldError));
					return;
				}
				final PixelImage neu = BlpDecoder.decode(blp);
				compare(name, "blp", rgb(old), old.getWidth(), old.getHeight(), neu.getRGB(), neu);
			}
			catch (final Exception e) {
				problems.add(name + ": " + e);
			}
		});
	}

	static byte[] makeBlp(final String kind, final int w, final int h, final Random r) {
		final String[] parts = kind.split("-");
		final boolean blp2 = parts[0].equals("BLP2");
		final String enc = parts[1];
		final int alpha = Integer.parseInt(parts[2].substring(1));
		final int n = w * h;
		final int dataSize;
		switch (enc) {
		case "PAL":
			dataSize = n + (((n * alpha) + 7) / 8);
			break;
		case "BGRA":
			dataSize = n * 4;
			break;
		default:
			dataSize = ((w + 3) / 4) * ((h + 3) / 4) * (enc.equals("DXT1") ? 8 : 16);
			break;
		}
		final int headerEnd = blp2 ? 148 : 156;
		final int dataStart = headerEnd + 1024;
		final byte[] f = new byte[dataStart + dataSize];
		r.nextBytes(f); // random palette; the header is written over it below
		java.util.Arrays.fill(f, 0, headerEnd, (byte) 0);
		if (blp2) {
			put(f, 0, 0x32504C42);
			put(f, 4, 1);
			f[8] = (byte) (enc.equals("PAL") ? 1 : enc.equals("BGRA") ? 3 : 2);
			f[9] = (byte) alpha;
			f[10] = (byte) (enc.equals("DXT3") ? 1 : enc.equals("DXT5") ? 7 : 0);
			f[11] = 0;
			put(f, 12, w);
			put(f, 16, h);
			put(f, 20, dataStart);
			put(f, 84, dataSize);
		}
		else {
			put(f, 0, 0x31504C42);
			put(f, 4, 1);
			put(f, 8, alpha);
			put(f, 12, w);
			put(f, 16, h);
			put(f, 20, 4);
			put(f, 24, 0);
			put(f, 28, dataStart);
			put(f, 92, dataSize);
		}
		if (enc.equals("DXT3") || enc.equals("DXT5")) {
			final byte[] payload = java.util.Arrays.copyOfRange(f, dataStart, f.length);
			forceFourColorBlocks(payload, 16, 8);
			System.arraycopy(payload, 0, f, dataStart, payload.length);
		}
		return f;
	}

	static void put(final byte[] f, final int at, final int v) {
		f[at] = (byte) v;
		f[at + 1] = (byte) (v >> 8);
		f[at + 2] = (byte) (v >> 16);
		f[at + 3] = (byte) (v >> 24);
	}
}
