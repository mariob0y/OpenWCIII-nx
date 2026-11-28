package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.Element;

/**
 * warsmash-nx console build: no-op stand-in for CMIDISound (Clearwing has no javax.sound.midi).
 * The PC build keeps the full MIDI sequencer/DLS implementation.
 */
public final class CMIDISound implements CSound {
	public CMIDISound(final DataSource assets, final String label, final Element row,
			final BooleanSupplier playbackAllowed) {
	}

	public CMIDISound(final DataSource assets, final String label, final Element row,
			final BooleanSupplier playbackAllowed, final DoubleSupplier ambientVolumeScale) {
	}

	@Override
	public void start() {
	}

	public boolean updatePlayback() {
		return false;
	}

	@Override
	public void stop() {
	}

	@Override
	public void setVolume(final int volume) {
	}

	@Override
	public void setPitch(final float pitch) {
	}

	@Override
	public void setPosition(final float x, final float y, final float z) {
	}

	@Override
	public void setPlayPosition(final int milliseconds) {
	}

	@Override
	public boolean isPlaying() {
		return false;
	}

	@Override
	public float getPredictedDuration() {
		return 0;
	}

	@Override
	public float getRemainingTimeToPlayOnTheDesyncLocalComputer() {
		return 0;
	}
}
