package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.ScrollBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;

/**
 * warsmash-nx: menu navigation with a game pad's D-pad. The cursor jumps from the button under it to
 * the nearest button in the pressed direction; with no button under it, to the nearest button at
 * all. Pressing the pad's click (A on the console) then clicks it as the mouse would, so menus keep
 * a single cursor and their hover highlight.
 */
public final class PadNavigation {
	public static final int NONE = -1;
	public static final int UP = 0;
	public static final int DOWN = 1;
	public static final int LEFT = 2;
	public static final int RIGHT = 3;

	private static final Vector2 POINT = new Vector2();

	private PadNavigation() {
	}

	/** The navigation direction of a D-pad key, or {@link #NONE}. */
	public static int direction(final int keycode) {
		switch (keycode) {
		case Input.Keys.DPAD_UP:
			return UP;
		case Input.Keys.DPAD_DOWN:
			return DOWN;
		case Input.Keys.DPAD_LEFT:
			return LEFT;
		case Input.Keys.DPAD_RIGHT:
			return RIGHT;
		default:
			return NONE;
		}
	}

	/**
	 * Moves the cursor (Gdx.input.setCursorPosition) to the next button under {@code root} in that
	 * direction. Returns the new cursor position (screen pixels, y down) in {@code out}, for the
	 * caller's mouseMoved, or false when no button lies that way.
	 */
	public static boolean move(final UIFrame root, final Viewport uiViewport, final int direction, final int[] out) {
		if ((root == null) || (direction == NONE)) {
			return false;
		}
		final List<AbstractRenderableFrame> buttons = new ArrayList<>();
		collect(root, buttons);
		if (buttons.isEmpty()) {
			return false;
		}
		POINT.set(Gdx.input.getX(), Gdx.input.getY());
		uiViewport.unproject(POINT);
		final float cursorX = POINT.x;
		final float cursorY = POINT.y;

		AbstractRenderableFrame current = null;
		for (final AbstractRenderableFrame button : buttons) {
			if (button.getRenderBounds().contains(cursorX, cursorY)) {
				current = button;
				break;
			}
		}

		AbstractRenderableFrame best = null;
		float bestScore = Float.MAX_VALUE;
		if (current == null) {
			for (final AbstractRenderableFrame button : buttons) {
				final Rectangle r = button.getRenderBounds();
				final float dx = (r.x + (r.width / 2)) - cursorX;
				final float dy = (r.y + (r.height / 2)) - cursorY;
				final float score = (dx * dx) + (dy * dy);
				if (score < bestScore) {
					bestScore = score;
					best = button;
				}
			}
		}
		else {
			final Rectangle from = current.getRenderBounds();
			final float originX = from.x + (from.width / 2);
			final float originY = from.y + (from.height / 2);
			// UI coordinates have y up
			final float dirX = direction == LEFT ? -1 : direction == RIGHT ? 1 : 0;
			final float dirY = direction == DOWN ? -1 : direction == UP ? 1 : 0;
			final float minStep = Math.max(1f, ((dirX != 0) ? from.width : from.height) * 0.25f);
			for (final AbstractRenderableFrame button : buttons) {
				if (button == current) {
					continue;
				}
				final Rectangle r = button.getRenderBounds();
				final float dx = (r.x + (r.width / 2)) - originX;
				final float dy = (r.y + (r.height / 2)) - originY;
				final float along = (dx * dirX) + (dy * dirY);
				if (along < minStep) {
					continue;
				}
				final float across = Math.abs((dx * dirY) - (dy * dirX));
				final float score = along + (2 * across);
				if (score < bestScore) {
					bestScore = score;
					best = button;
				}
			}
		}
		if (best == null) {
			return false;
		}
		final Rectangle r = best.getRenderBounds();
		POINT.set(r.x + (r.width / 2), r.y + (r.height / 2));
		uiViewport.project(POINT);
		// project() answers with the origin at the bottom left; the cursor has it at the top left
		final int x = Math.round(POINT.x);
		final int y = Gdx.graphics.getHeight() - 1 - Math.round(POINT.y);
		Gdx.input.setCursorPosition(x, y);
		out[0] = x;
		out[1] = y;
		return true;
	}

	private static void collect(final UIFrame frame, final List<AbstractRenderableFrame> buttons) {
		if (!frame.isVisible()) {
			return;
		}
		if ((frame instanceof ClickableFrame) && (frame instanceof AbstractRenderableFrame)
				&& !(frame instanceof ScrollBarFrame) && isEnabled(frame) && frame.isVisibleOnScreen()) {
			final Rectangle r = ((AbstractRenderableFrame) frame).getRenderBounds();
			if ((r.width > 0) && (r.height > 0)) {
				buttons.add((AbstractRenderableFrame) frame);
			}
		}
		if (frame instanceof AbstractUIFrame) {
			final ListIterator<UIFrame> children = ((AbstractUIFrame) frame).getChildIterator();
			while (children.hasPrevious()) {
				collect(children.previous(), buttons);
			}
		}
	}

	private static boolean isEnabled(final UIFrame frame) {
		if (frame instanceof GlueButtonFrame) {
			return ((GlueButtonFrame) frame).isEnabled();
		}
		if (frame instanceof SimpleButtonFrame) {
			return ((SimpleButtonFrame) frame).isEnabled();
		}
		return true;
	}
}
