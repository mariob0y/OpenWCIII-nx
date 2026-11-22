package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

public interface CSound {
	/** warsmash-nx: StopSound. */
	default void stop() {
	}

	void start();

	float getPredictedDuration();

	float getRemainingTimeToPlayOnTheDesyncLocalComputer();
}
