package com.etheller.warsmash.util;

/**
 * warsmash-nx: where long loads say what they are doing. The Switch 2 launcher listens, to draw a
 * loading screen while the game is still inside create() (minutes on the console, with no frame
 * shown otherwise) and to log each step with its time. Without a listener (desktop) it does nothing.
 */
public final class LoadingStatus {
	public interface Listener {
		/**
		 * @param step   what is being done, e.g. "Reading game files"; a new step is logged
		 * @param detail progress within the step (may be empty); shown, never logged
		 */
		void status(String step, String detail);

		/** The load is over and normal frames follow: the loading screen must stop drawing. */
		void done(String what);
	}

	private static Listener listener;

	private LoadingStatus() {
	}

	public static void setListener(final Listener listener) {
		LoadingStatus.listener = listener;
	}

	public static void report(final String step, final String detail) {
		final Listener current = listener;
		if (current != null) {
			current.status(step, detail);
		}
	}

	public static void report(final String step) {
		report(step, "");
	}

	/**
	 * The current step is over and no other follows yet (e.g. the in-game interface layouts, read
	 * after the map's done): logged with its time, and no longer counted as running.
	 */
	public static void finished() {
		report("", "");
	}

	public static void done(final String what) {
		final Listener current = listener;
		if (current != null) {
			current.done(what);
		}
	}
}
