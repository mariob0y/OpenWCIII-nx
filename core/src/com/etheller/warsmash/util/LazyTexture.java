package com.etheller.warsmash.util;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.image.PixelImage;

/**
 * warsmash-nx: a texture read from the game data the first time it is drawn or measured, not when it
 * is made. The game interface makes a texture for every ability, buff and unit icon there is
 * (AbilityDataUI), thousands, of which a mission shows a few dozen; decoding them all took 35 s of
 * each mission start on the console (OrcX01, 2026-11-22).
 *
 * Until then it is a 1x1 transparent texture. Read with ImageUtils.getAnyExtensionImageFixRGB and
 * filled like ImageUtils.getTexture, so the pixels are the same as an eagerly made texture's. A file
 * that cannot be decoded stays transparent (eagerly, GameUI.loadTexture returned null).
 */
public final class LazyTexture extends Texture {
	private final DataSource dataSource;
	private String path;

	public LazyTexture(final DataSource dataSource, final String path) {
		super(new PixmapTextureData(new Pixmap(1, 1, Format.RGBA8888), null, false, true));
		this.dataSource = dataSource;
		this.path = path;
	}

	private void ensureLoaded() {
		final String toLoad = this.path;
		if (toLoad == null) {
			return;
		}
		this.path = null; // load() binds, i.e. comes back here
		try {
			final ImageUtils.AnyExtensionImage imageInfo = ImageUtils.getAnyExtensionImageFixRGB(this.dataSource,
					toLoad, "texture");
			final PixelImage image = imageInfo.getImageData();
			if (image != null) {
				load(new PixmapTextureData(ImageUtils.getPixmap(image, imageInfo.isNeedsSRGBFix()), null, false,
						true));
				setFilter(TextureFilter.Linear, TextureFilter.Linear);
			}
		}
		catch (final Exception e) {
			System.err.println("LazyTexture: " + toLoad + ": " + e);
		}
	}

	@Override
	public void bind() {
		ensureLoaded();
		super.bind();
	}

	@Override
	public void bind(final int unit) {
		ensureLoaded();
		super.bind(unit);
	}

	@Override
	public int getTextureObjectHandle() {
		ensureLoaded();
		return super.getTextureObjectHandle();
	}

	@Override
	public int getWidth() {
		ensureLoaded();
		return super.getWidth();
	}

	@Override
	public int getHeight() {
		ensureLoaded();
		return super.getHeight();
	}
}
