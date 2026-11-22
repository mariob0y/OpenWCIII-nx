package com.etheller.warsmash.desktop.util;

import java.awt.image.BufferedImage;

import com.etheller.warsmash.util.image.PixelImage;

/** Bridges core's AWT-free {@link PixelImage} to Swing, for the desktop-only editors. */
public final class AwtImages {
	/** A TYPE_INT_ARGB copy whose getRGB returns what the PixelImage's getRGB does. */
	public static BufferedImage toBufferedImage(final PixelImage image) {
		if (image == null) {
			return null;
		}
		final BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		out.setRGB(0, 0, image.getWidth(), image.getHeight(), image.getRGB(), 0, image.getWidth());
		return out;
	}

	/** The other way: an sRGB PixelImage holding the BufferedImage's getRGB values. */
	public static PixelImage toPixelImage(final BufferedImage image) {
		if (image == null) {
			return null;
		}
		final int w = image.getWidth();
		final int h = image.getHeight();
		return new PixelImage(w, h, image.getRGB(0, 0, w, h, null, 0, w), false);
	}

	private AwtImages() {
	}
}
