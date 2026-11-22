package com.etheller.warsmash.util;

/**
 * warsmash-nx: breadcrumbs for the first frames after loading. On the Switch 2 (kaiHEN) a CPU fault
 * goes straight to the system's crash screen and leaves nothing behind, so the console launcher
 * turns these on and the last line in warsmash.log says where a silent crash happened. Off (and
 * silent) unless {@link #enable} is called, as on the PC.
 */
public final class NxCrumbs {
	private static int framesLeft = 0;
	private static int frame = 0;

	private NxCrumbs() {
	}

	/** Logs crumbs for the next {@code frames} frames. */
	public static void enable(final int frames) {
		framesLeft = frames;
	}

	public static boolean on() {
		return framesLeft > 0;
	}

	/** Fine-grained crumbs (per model, per group): only the first few frames, they are many. */
	public static boolean detail() {
		return (framesLeft > 0) && (frame < DETAIL_FRAMES);
	}

	private static final int DETAIL_FRAMES = 4;

	public static void crumb(final String what) {
		if (framesLeft > 0) {
			System.out.println("crumb r" + frame + " t=" + ((System.nanoTime() / 1000000L) % 1000000L) + " ms " + what);
		}
	}

	/** The end of one rendered frame. */
	public static void endFrame() {
		if (framesLeft > 0) {
			framesLeft--;
			frame++;
		}
	}
}
