package com.etheller.warsmash.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.image.PixelImage;

/**
 * warsmash-nx: a texture read from the game data the first time it is drawn or measured, not when it
 * is made. The game interface makes a texture for every ability, buff and unit icon there is
 * (AbilityDataUI), thousands, of which a mission shows a few dozen; decoding them all took 35 s of
 * each mission start on the console (OrcX01, 2026-11-22).
 *
 * Until then it has no GL texture at all: making one per icon (a 1x1 placeholder each) still cost
 * thousands of texture creations per mission start, each a Vulkan image under zink. Read with
 * ImageUtils.getAnyExtensionImageFixRGB and filled like ImageUtils.getTexture, so the pixels are the
 * same as an eagerly made texture's. A file that cannot be decoded becomes a 1x1 transparent texture
 * (eagerly, GameUI.loadTexture returned null).
 */
public final class LazyTexture extends Texture {
	private final DataSource dataSource;
	private String path;

	/**
	 * Stands in for the image until the first use: uploads nothing (Custom data whose upload does
	 * nothing), so constructing on GL handle 0 creates no GL texture. Texture.load still sets the
	 * filter and wrap state of whatever is bound to handle 0, which nothing samples.
	 */
	private static final TextureData PLACEHOLDER = new TextureData() {
		@Override
		public TextureDataType getType() {
			return TextureDataType.Custom;
		}

		@Override
		public boolean isPrepared() {
			return true;
		}

		@Override
		public void prepare() {
		}

		@Override
		public Pixmap consumePixmap() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean disposePixmap() {
			return false;
		}

		@Override
		public void consumeCustomData(final int target) {
		}

		@Override
		public int getWidth() {
			return 1;
		}

		@Override
		public int getHeight() {
			return 1;
		}

		@Override
		public Format getFormat() {
			return Format.RGBA8888;
		}

		@Override
		public boolean useMipMaps() {
			return false;
		}

		@Override
		public boolean isManaged() {
			return false;
		}
	};

	public LazyTexture(final DataSource dataSource, final String path) {
		// during this, path is still null: the bind() that load() makes does not come back to ensureLoaded
		super(GL20.GL_TEXTURE_2D, 0, PLACEHOLDER);
		this.dataSource = dataSource;
		this.path = path;
	}

	private void ensureLoaded() {
		final String toLoad = this.path;
		if (toLoad == null) {
			return;
		}
		this.path = null; // load() binds, i.e. comes back here
		this.glHandle = Gdx.gl.glGenTexture();
		boolean loaded = false;
		try {
			final ImageUtils.AnyExtensionImage imageInfo = ImageUtils.getAnyExtensionImageFixRGB(this.dataSource,
					toLoad, "texture");
			final PixelImage image = imageInfo.getImageData();
			if (image != null) {
				load(new PixmapTextureData(ImageUtils.getPixmap(image, imageInfo.isNeedsSRGBFix()), null, false,
						true));
				setFilter(TextureFilter.Linear, TextureFilter.Linear);
				loaded = true;
			}
		}
		catch (final Exception e) {
			System.err.println("LazyTexture: " + toLoad + ": " + e);
		}
		if (!loaded) {
			// transparent, as the placeholder of an eagerly made texture was (handle 0 would sample black)
			load(new PixmapTextureData(new Pixmap(1, 1, Format.RGBA8888), null, false, true));
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
