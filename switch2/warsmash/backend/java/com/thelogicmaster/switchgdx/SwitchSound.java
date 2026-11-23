package com.thelogicmaster.switchgdx;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * warsmash-nx: replaces SwitchGDX's SwitchSound (later transpiler inputs win). A sound is decoded
 * once into memory by backend/native/nx_audio.cpp; each play() gets a voice id from the mixer
 * (generation << 8 | channel), so ids are the mixer's and need no bookkeeping here: an id whose
 * sound already ended, or whose channel was reused, is ignored. Upstream kept a map of channels
 * that was never cleaned (its finished events were never delivered) and could stop the wrong
 * sound. Loaded from file.readBytes(): Warsmash's sounds come from its data sources
 * (DataSourceFileHandle), which have no real file to open by path.
 */
public class SwitchSound implements Sound {
	/*JNI
	#include "nx_audio.h"
	static const char *nxSoundError = "";
	*/

	private long handle;
	private boolean disposed;

	public SwitchSound (FileHandle file) {
		this(file.readBytes(), file.path());
	}

	public SwitchSound (byte[] data) {
		this(data, "(bytes)");
	}

	private SwitchSound (byte[] data, String name) {
		handle = create0(data, data.length);
		if (handle == 0)
			throw new GdxRuntimeException("Couldn't load sound " + name + ": " + lastError0());
	}

	boolean update () {
		return disposed;
	}

	/** Seconds, at the recording's own rate (Warsmash's AudioExtension.getDuration). */
	public float duration () {
		return disposed ? 0 : duration0(handle);
	}

	@Override
	public long play () {
		return play(1);
	}

	@Override
	public long play (float volume) {
		return play(volume, 1, 0);
	}

	@Override
	public long play (float volume, float pitch, float pan) {
		return disposed ? -1 : play0(handle, false, volume, pitch, pan);
	}

	@Override
	public long loop () {
		return loop(1);
	}

	@Override
	public long loop (float volume) {
		return loop(volume, 1, 0);
	}

	@Override
	public long loop (float volume, float pitch, float pan) {
		return disposed ? -1 : play0(handle, true, volume, pitch, pan);
	}

	@Override
	public void stop () {
		if (!disposed)
			stopAll0(handle);
	}

	@Override
	public void pause () {
		if (!disposed)
			pauseAll0(handle);
	}

	@Override
	public void resume () {
		if (!disposed)
			resumeAll0(handle);
	}

	@Override
	public void dispose () {
		if (disposed)
			return;
		disposed = true;
		release0(handle);
		handle = 0;
	}

	@Override
	public void stop (long soundId) {
		stop0(soundId);
	}

	@Override
	public void pause (long soundId) {
		pause0(soundId);
	}

	@Override
	public void resume (long soundId) {
		resume0(soundId);
	}

	@Override
	public void setLooping (long soundId, boolean looping) {
		setLooping0(soundId, looping);
	}

	@Override
	public void setPitch (long soundId, float pitch) {
		setPitch0(soundId, pitch);
	}

	@Override
	public void setVolume (long soundId, float volume) {
		setVolume0(soundId, volume);
	}

	@Override
	public void setPan (long soundId, float pan, float volume) {
		setPan0(soundId, pan);
		setVolume0(soundId, volume);
	}

	private static native long create0 (byte[] data, int length); /*
		const char *error = nullptr;
		auto sample = nxaudio::load((const uint8_t *) data, (size_t) length, &error); // data: the array's bytes (the transpiler converts it)
		nxSoundError = error ? error : "";
		return (jlong) (intptr_t) sample;
	*/

	private static native String lastError0 (); /*
		return (jobject) stringFromNative(ctx, nxSoundError);
	*/

	private static native float duration0 (long handle); /*
		return nxaudio::duration((nxaudio::Sample *) (intptr_t) handle);
	*/

	private static native void release0 (long handle); /*
		nxaudio::release((nxaudio::Sample *) (intptr_t) handle);
	*/

	private static native long play0 (long handle, boolean looping, float volume, float pitch, float pan); /*
		return nxaudio::play((nxaudio::Sample *) (intptr_t) handle, looping, volume, pitch, pan);
	*/

	private static native void stopAll0 (long handle); /*
		nxaudio::stopAll((nxaudio::Sample *) (intptr_t) handle);
	*/

	private static native void pauseAll0 (long handle); /*
		nxaudio::pauseAll((nxaudio::Sample *) (intptr_t) handle);
	*/

	private static native void resumeAll0 (long handle); /*
		nxaudio::resumeAll((nxaudio::Sample *) (intptr_t) handle);
	*/

	private static native void stop0 (long voice); /*
		nxaudio::stop(voice);
	*/

	private static native void pause0 (long voice); /*
		nxaudio::pause(voice);
	*/

	private static native void resume0 (long voice); /*
		nxaudio::resume(voice);
	*/

	private static native void setLooping0 (long voice, boolean looping); /*
		nxaudio::setLooping(voice, looping);
	*/

	private static native void setPitch0 (long voice, float pitch); /*
		nxaudio::setPitch(voice, pitch);
	*/

	private static native void setVolume0 (long voice, float volume); /*
		nxaudio::setVolume(voice, volume);
	*/

	private static native void setPan0 (long voice, float pan); /*
		nxaudio::setPan(voice, pan);
	*/
}
