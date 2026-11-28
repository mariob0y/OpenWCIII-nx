package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.File;

/**
 * warsmash-nx console build: no-op stand-in for MovieAudioPlayback (the desktop version spawns an
 * external ffmpeg process via ProcessBuilder, which does not exist on the console).
 */
final class MovieAudioPlayback {
	MovieAudioPlayback(final String decoder, final File file) {
	}

	void start() {
	}

	void stop() {
	}

	float getSubmittedSeconds() {
		return 0;
	}
}
