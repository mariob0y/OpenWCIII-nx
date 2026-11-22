package com.etheller.warsmash.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.image.ImageDecoders;
import com.etheller.warsmash.util.image.PixelImage;
import com.etheller.warsmash.viewer5.handlers.tga.TgaFile;

/**
 * Texture loading helpers. Used AWT/ImageIO until the Switch 2 port; now
 * decodes through {@link ImageDecoders}, with identical pixel results (checked
 * against the old path by warsmash-nx/tools/imagecheck).
 */
public final class ImageUtils {
	private static final int BYTES_PER_PIXEL = 4;
	public static final String DEFAULT_ICON_PATH = "ReplaceableTextures\\CommandButtons\\BTNTemp.blp";

	public static Texture getAnyExtensionTexture(final DataSource dataSource, final String path) {
		PixelImage image;
		try {
			final AnyExtensionImage imageInfo = getAnyExtensionImageFixRGB(dataSource, path, "texture");
			image = imageInfo.getImageData();
			if (image != null) {
				return ImageUtils.getTexture(image, imageInfo.isNeedsSRGBFix());
			}
		}
		catch (final IOException e) {
			return null;
		}
		return null;
	}

	public static AnyExtensionImage getAnyExtensionImageFixRGB(final DataSource dataSource, final String path,
			final String errorType) throws IOException {
		if (path.toLowerCase().endsWith(".blp")) {
			try (InputStream stream = dataSource.getResourceAsStream(path)) {
				if (stream == null) {
					final String tgaPath = path.substring(0, path.length() - 4) + ".tga";
					try (final InputStream tgaStream = dataSource.getResourceAsStream(tgaPath)) {
						if (tgaStream != null) {
							final PixelImage tgaData = TgaFile.readTGA(tgaPath, tgaStream);
							return new AnyExtensionImage(false, tgaData);
						}
						else {
							final String ddsPath = path.substring(0, path.length() - 4) + ".dds";
							try (final InputStream ddsStream = dataSource.getResourceAsStream(ddsPath)) {
								if (ddsStream != null) {
									final PixelImage image = ImageDecoders.read(ddsStream);
									return new AnyExtensionImage(false, image);
								}
								else {
									throw new IllegalStateException("Missing " + errorType + ": " + path);
								}
							}
						}
					}
				}
				else {
					final PixelImage image = ImageDecoders.read(stream);
					return new AnyExtensionImage(true, image);
				}
			}
		}
		else {
			throw new IllegalStateException("Missing " + errorType + ": " + path);
		}
	}

	public static final class AnyExtensionImage {
		private final boolean needsSRGBFix;
		private final PixelImage imageData;

		public AnyExtensionImage(final boolean needsSRGBFix, final PixelImage imageData) {
			this.needsSRGBFix = needsSRGBFix;
			this.imageData = imageData;
		}

		public PixelImage getImageData() {
			return this.imageData;
		}

		public PixelImage getRGBCorrectImageData() {
			return this.needsSRGBFix ? this.imageData.forceSRGB() : this.imageData;
		}

		public boolean isNeedsSRGBFix() {
			return this.needsSRGBFix;
		}
	}

	public static Texture getTexture(final PixelImage image, final boolean sRGBFix) {
		final Pixmap pixmap = sRGBFix ? new Pixmap(image.getWidth(), image.getHeight(), Format.RGBA8888) {
			@Override
			public int getGLInternalFormat() {
				return GL30.GL_SRGB8_ALPHA8;
			}
		} : new Pixmap(image.getWidth(), image.getHeight(), Format.RGBA8888);
		fillPixmap(pixmap, image);
		final Texture texture = new Texture(pixmap);
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		return texture;
	}

	public static Texture getTextureNoColorCorrection(final PixelImage image) {
		final Pixmap pixmap = new Pixmap(image.getWidth(), image.getHeight(), Format.RGBA8888);
		fillPixmap(pixmap, image);
		final Texture texture = new Texture(pixmap);
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		return texture;
	}

	private static void fillPixmap(final Pixmap pixmap, final PixelImage image) {
		final int[] pixels = image.getRGB();
		final int width = image.getWidth();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < width; x++) {
				final int pixel = pixels[(y * width) + x];
				pixmap.drawPixel(x, y, (pixel << 8) | (pixel >>> 24));
			}
		}
	}

	/** The image as getRGB sees it, packed R, G, B, A per pixel in a direct buffer for glTexImage. */
	public static Buffer getTextureBuffer(final PixelImage image) {
		final ByteBuffer buffer = toRgbaBuffer(image);
		buffer.flip();
		return buffer;
	}

	/** Same bytes as {@link #getTextureBuffer}, left unflipped (position at the end). */
	public static ByteBuffer toRgbaBuffer(final PixelImage image) {
		final int[] pixels = image.getRGB();
		final ByteBuffer buffer = ByteBuffer.allocateDirect(pixels.length * BYTES_PER_PIXEL)
				.order(ByteOrder.nativeOrder());
		for (final int pixel : pixels) {
			buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red component
			buffer.put((byte) ((pixel >> 8) & 0xFF)); // Green component
			buffer.put((byte) (pixel & 0xFF)); // Blue component
			buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha component
		}
		return buffer;
	}

	private ImageUtils() {
	}
}
