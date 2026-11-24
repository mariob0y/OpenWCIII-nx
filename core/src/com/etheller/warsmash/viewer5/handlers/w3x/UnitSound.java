package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.viewer5.AudioBufferSource;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioPanner;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.OptionsSettingsStore;

public final class UnitSound {
	private static final UnitSound SILENT = new UnitSound(0, 0, 0, 0, 0, 0, false);

	/** Decoded sounds; null until first played (see sound()). Same indices as soundPaths. */
	private final List<Sound> sounds = new ArrayList<>();
	private final List<String> soundPaths = new ArrayList<>();
	private DataSource dataSource;
	private final float volume;
	private final float pitch;
	private final float pitchVariance;
	private final float minDistance;
	private final float maxDistance;
	private final float distanceCutoff;
	private final boolean looping;

	private Sound lastPlayedSound;

	public static UnitSound create(final DataSource dataSource, final DataTable unitAckSounds, final String soundName,
			final String soundType) {
		final Element row = unitAckSounds.get(soundName + soundType);
		if (row == null) {
			return SILENT;
		}
		final String fileNames = row.getField("FileNames");
		String directoryBase = row.getField("DirectoryBase");
		if ((directoryBase.length() > 1) && !directoryBase.endsWith("\\")) {
			directoryBase += "\\";
		}
		final float volume = row.getFieldFloatValue("Volume") / 127f;
		final float pitch = row.getFieldFloatValue("Pitch");
		float pitchVariance = row.getFieldFloatValue("PitchVariance");
		if (pitchVariance == 1.0f) {
			pitchVariance = 0.0f;
		}
		final float minDistance = row.getFieldFloatValue("MinDistance");
		final float maxDistance = row.getFieldFloatValue("MaxDistance");
		final float distanceCutoff = row.getFieldFloatValue("DistanceCutoff");
		final String[] flags = row.getField("Flags").split(",");
		boolean looping = false;
		for (final String flag : flags) {
			if ("LOOPING".equals(flag)) {
				looping = true;
			}
		}
		final UnitSound sound = new UnitSound(volume, pitch, pitchVariance, minDistance, maxDistance, distanceCutoff,
				looping);
		// warsmash-nx: only find the files here; each is decoded the first time it plays (sound()). A new
		// unit type used to decode its whole soundset (15-25 files) in one frame: 135-500 ms hitches.
		sound.dataSource = dataSource;
		for (final String fileName : fileNames.split(",")) {
			final String loadPath = resolveSoundPath(dataSource, directoryBase + fileName);
			if (loadPath != null) {
				sound.soundPaths.add(loadPath);
				sound.sounds.add(null);
			}
		}
		return sound;
	}

	/** The file a sound path loads from (extension replaced by .wav or .mp3), or null if there is none. */
	private static String resolveSoundPath(final DataSource dataSource, String filePath) {
		final int lastDotIndex = filePath.lastIndexOf('.');
		if (lastDotIndex != -1) {
			filePath = filePath.substring(0, lastDotIndex);
		}
		if (dataSource.has(filePath + ".wav") || dataSource.has(filePath + ".flac")) {
			return filePath + ".wav";
		}
		else if (dataSource.has(filePath + ".mp3")) {
			return filePath + ".mp3";
		}
		return null;
	}

	private static Sound loadSound(final DataSource dataSource, final String loadPath) {
		try {
			return Gdx.audio.newSound(new DataSourceFileHandle(dataSource, loadPath));
		}
		catch (final Exception exc) {
			exc.printStackTrace();
			return null;
		}
	}

	public static Sound createSound(final DataSource dataSource, final String filePath) {
		final String loadPath = resolveSoundPath(dataSource, filePath);
		return loadPath == null ? null : loadSound(dataSource, loadPath);
	}

	/** The decoded sound at an index, decoding it on first use; null if it does not decode. */
	private Sound sound(final int index) {
		Sound sound = this.sounds.get(index);
		if ((sound == null) && (this.soundPaths.get(index) != null)) {
			sound = loadSound(this.dataSource, this.soundPaths.get(index));
			if (sound == null) {
				this.soundPaths.set(index, null); // do not try again
			}
			this.sounds.set(index, sound);
		}
		return sound;
	}

	public UnitSound(final float volume, final float pitch, final float pitchVariation, final float minDistance,
			final float maxDistance, final float distanceCutoff, final boolean looping) {
		this.volume = volume;
		this.pitch = pitch;
		this.pitchVariance = pitchVariation;
		this.minDistance = minDistance;
		this.maxDistance = maxDistance;
		this.distanceCutoff = distanceCutoff;
		this.looping = looping;
	}

	public boolean playUnitResponse(final AudioContext audioContext, final RenderUnit unit) {
		return playUnitResponse(audioContext, unit, (int) (Math.random() * this.sounds.size()));
	}

	public boolean playUnitResponse(final AudioContext audioContext, final RenderUnit unit, final int index) {
		if (!OptionsSettingsStore.get().isUnitSounds()) return false;
		final long millisTime = TimeUtils.millis();
		if (millisTime < unit.lastUnitResponseEndTimeMillis) {
			return false;
		}
		if (play(audioContext, unit.location[0], unit.location[1], unit.location[2], index) != -1) {
			final float duration = Extensions.audio.getDuration(this.lastPlayedSound);
			unit.lastUnitResponseEndTimeMillis = millisTime + (long) (1000 * duration);
			return true;
		}
		return false;
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z,
			final boolean loopOverride) {
		return play(audioContext, x, y, z, (int) (Math.random() * this.sounds.size()), loopOverride);
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z) {
		return play(audioContext, x, y, z, (int) (Math.random() * this.sounds.size()));
	}

	/** Restarts a screen-owned sound without leaving an earlier instance playing. */
	public long playExclusive(final AudioContext audioContext, final float x, final float y, final float z) {
		stop();
		return play(audioContext, x, y, z);
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z, final int index) {
		return play(audioContext, x, y, z, index, null);
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z, final int index,
			final Boolean loopOverride) {
		if (this.sounds.isEmpty() || index < 0 || index >= this.sounds.size()) {
			return -1;
		}

		if (audioContext == null) {
			return -1;
		}
		final Sound buffer = sound(index);
		if (buffer == null) {
			return -1;
		}
		final AudioPanner panner = audioContext.createPanner();
		final AudioBufferSource source = audioContext.createBufferSource();

		// Panner settings
		panner.setPosition(x, y, z);
		panner.setDistances(this.distanceCutoff, this.minDistance);
		panner.connect(audioContext.destination);

		// Source.
		source.buffer = buffer;
		source.connect(panner);

		// Make a sound.
		long soundId = -1;
		if (loopOverride == null) {
			soundId = source.start(0, this.volume,
					(this.pitch + ((float) Math.random() * this.pitchVariance * 2)) - this.pitchVariance, this.looping);
		}
		else {
			soundId = source.start(0, this.volume,
					(this.pitch + ((float) Math.random() * this.pitchVariance * 2)) - this.pitchVariance, loopOverride);
		}
		this.lastPlayedSound = source.buffer;
		return soundId;
	}

	/** Creates a script-owned voice without borrowing this label's global last-played state. */
	public com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename createPlayback(
			final AudioContext context, final boolean loop, final boolean spatial, final boolean stopOutOfRange,
			final int fadeIn, final int fadeOut) {
		if (this.sounds.isEmpty()) return null;
		final Sound buffer = sound((int) (Math.random() * this.sounds.size()));
		if (buffer == null) return null;
		final com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename playback = new com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename(
				buffer, context, loop, stopOutOfRange, fadeIn, fadeOut, "");
		playback.setNormalizedVolume(this.volume);
		playback.setPitch(this.pitch + ((float) Math.random() * 2 - 1) * this.pitchVariance);
		playback.setMinDistance(this.minDistance);
		playback.setDistanceCutoff(this.distanceCutoff);
		playback.setSpatial(spatial);
		return playback;
	}

	public int getSoundCount() {
		return this.sounds.size();
	}

	public Sound getLastPlayedSound() {
		return this.lastPlayedSound;
	}

	public void stop() {
		for (final Sound sound : this.sounds) {
			if (sound != null) {
				sound.stop();
			}
		}
	}

	public void stop(long soundId) {
		// This may misbehave if called for a list longer than 1, due to the random
		// index used when starting?
		// Not sure if IDs are unique per source
		for (final Sound sound : this.sounds) {
			if (sound != null) {
				sound.stop(soundId);
			}
		}
	}
}
