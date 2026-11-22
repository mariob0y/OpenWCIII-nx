package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CBlendMode;

/**
 * warsmash-nx: the cinematic filter (SetCineFilter* / DisplayCineFilter), which Warsmash did not have:
 * a full-screen texture over the world, its colour going from the start colour to the end colour over
 * the duration. Blizzard.j fades every cinematic in and out with it (CinematicFadeBJ, black mask); without
 * it cuts were abrupt and "fade to black then move things" showed the moving. UV animation and texmap
 * flags are not modelled (the campaign uses the full texture).
 *
 * One game runs at a time, so the state is static: the natives write it, MeleeUI.render draws it.
 */
public final class NxCineFilter {
	private NxCineFilter() {
	}

	private static String texturePath;
	private static CBlendMode blendMode = CBlendMode.BLEND;
	private static final int[] startColor = { 255, 255, 255, 255 };
	private static final int[] endColor = { 255, 255, 255, 255 };
	private static float duration;
	private static boolean displayed;
	private static long displayStartNanos;
	private static final Map<String, Texture> TEXTURES = new HashMap<>();

	public static void setTexture(final String path) {
		texturePath = path;
	}

	public static void setBlendMode(final CBlendMode mode) {
		blendMode = mode == null ? CBlendMode.BLEND : mode;
	}

	public static void setStartColor(final int r, final int g, final int b, final int a) {
		set(startColor, r, g, b, a);
	}

	public static void setEndColor(final int r, final int g, final int b, final int a) {
		set(endColor, r, g, b, a);
	}

	private static void set(final int[] c, final int r, final int g, final int b, final int a) {
		c[0] = r;
		c[1] = g;
		c[2] = b;
		c[3] = a;
	}

	public static void setDuration(final float seconds) {
		duration = seconds;
	}

	public static void display(final boolean flag) {
		displayed = flag;
		displayStartNanos = System.nanoTime();
	}

	public static boolean isDisplayed() {
		return displayed;
	}

	/** A new game: no filter left over from the last one. */
	public static void reset() {
		displayed = false;
		texturePath = null;
	}

	static void render(final SpriteBatch batch, final GameUI rootFrame, final float width, final float height) {
		if (!displayed || (texturePath == null)) {
			return;
		}
		Texture texture = TEXTURES.get(texturePath);
		if ((texture == null) && !TEXTURES.containsKey(texturePath)) {
			try {
				texture = rootFrame.loadTexture(texturePath);
			}
			catch (final RuntimeException e) {
				System.err.println("warsmash-nx: cine filter texture " + texturePath + ": " + e);
			}
			TEXTURES.put(texturePath, texture);
		}
		if (texture == null) {
			return;
		}
		final float elapsed = (System.nanoTime() - displayStartNanos) / 1e9f;
		final float t = duration <= 0 ? 1 : Math.min(1, elapsed / duration);
		final float r = lerp(startColor[0], endColor[0], t) / 255f;
		final float g = lerp(startColor[1], endColor[1], t) / 255f;
		final float b = lerp(startColor[2], endColor[2], t) / 255f;
		final float a = lerp(startColor[3], endColor[3], t) / 255f;
		if (a <= 0) {
			return;
		}
		final int srcFunc = batch.getBlendSrcFunc();
		final int dstFunc = batch.getBlendDstFunc();
		switch (blendMode) {
		case ADDITIVE:
			batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
			break;
		case MODULATE:
		case MODULATE_2X:
			batch.setBlendFunction(GL20.GL_ZERO, GL20.GL_SRC_COLOR);
			break;
		default:
			batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
			break;
		}
		final com.badlogic.gdx.graphics.Color previous = batch.getColor().cpy();
		batch.setColor(r, g, b, a);
		batch.draw(texture, 0, 0, width, height);
		batch.setColor(previous);
		batch.setBlendFunction(srcFunc, dstFunc);
	}

	private static float lerp(final int from, final int to, final float t) {
		return from + ((to - from) * t);
	}
}
