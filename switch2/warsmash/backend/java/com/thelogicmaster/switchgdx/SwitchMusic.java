package com.thelogicmaster.switchgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * warsmash-nx: replaces SwitchGDX's SwitchMusic. One music plays at a time (backend/native
 * nx_audio.cpp streams the MP3 from a copy of its bytes). Completion is polled each frame
 * (SwitchAudio.update): upstream's finished callback was never delivered, so OnCompletionListener
 * never fired, and Warsmash's music player waits for it to start the next track. Loaded from
 * file.readBytes() (Warsmash's DataSourceFileHandle has no real file to open by path).
 */
public class SwitchMusic implements Music {
	/*JNI
	#include "nx_audio.h"
	static const char *nxMusicError = "";
	*/

	private long handle;
	private OnCompletionListener listener;
	private boolean paused;
	private boolean playing;
	private boolean looping;
	private float volume = 1;
	private float position;
	private boolean disposed;

	public SwitchMusic (FileHandle file) {
		final byte[] data = file.readBytes();
		handle = create0(data, data.length);
		if (handle == 0)
			throw new GdxRuntimeException("Couldn't load music " + file.path() + ": " + lastError0());
	}

	boolean update (float delta) {
		if (disposed)
			return true;
		if (!playing)
			return false;
		final int state = poll0(handle);
		if (state == 1) {
			// Reached its end (a looping music never does: the mixer loops it).
			playing = false;
			paused = false;
			position = 0;
			if (listener != null)
				Gdx.app.postRunnable(() -> listener.onCompletion(this));
		}
		else if (state == 2) {
			playing = false; // another music took its place
			paused = false;
		}
		else if (!paused) {
			position += delta;
		}
		return false;
	}

	@Override
	public void play () {
		if (disposed)
			return;
		if (playing && paused) {
			resume0();
		}
		else if (!playing) {
			start0(handle, looping);
			position = 0;
		}
		setVolume0(volume);
		paused = false;
		playing = true;
	}

	@Override
	public void pause () {
		if (!playing || paused)
			return;
		paused = true;
		pause0();
	}

	@Override
	public void stop () {
		if (!playing)
			return;
		playing = false;
		paused = false;
		position = 0;
		if (poll0(handle) == 0)
			stop0();
	}

	@Override
	public boolean isPlaying () {
		return playing && !paused;
	}

	@Override
	public void setLooping (boolean isLooping) {
		looping = isLooping;
		if (playing && poll0(handle) == 0)
			setLooping0(isLooping);
	}

	@Override
	public boolean isLooping () {
		return looping;
	}

	@Override
	public void setVolume (float volume) {
		this.volume = volume;
		if (playing && poll0(handle) == 0)
			setVolume0(volume);
	}

	@Override
	public float getVolume () {
		return volume;
	}

	@Override
	public void setPan (float pan, float volume) {
		setVolume(volume); // the music stream has no pan (as upstream)
	}

	@Override
	public void setPosition (float position) {
		this.position = position;
		if (playing && poll0(handle) == 0)
			setPosition0(position);
	}

	@Override
	public float getPosition () {
		return position;
	}

	@Override
	public void dispose () {
		if (disposed)
			return;
		stop();
		disposed = true;
		release0(handle);
		handle = 0;
	}

	@Override
	public void setOnCompletionListener (OnCompletionListener listener) {
		this.listener = listener;
	}

	private static native long create0 (byte[] data, int length); /*
		const char *error = nullptr;
		auto music = nxaudio::musicLoad((const uint8_t *) data, (size_t) length, &error); // data: the array's bytes
		nxMusicError = error ? error : "";
		return (jlong) (intptr_t) music;
	*/

	private static native String lastError0 (); /*
		return (jobject) stringFromNative(ctx, nxMusicError);
	*/

	private static native void release0 (long handle); /*
		nxaudio::musicRelease((nxaudio::Music *) (intptr_t) handle);
	*/

	private static native void start0 (long handle, boolean looping); /*
		nxaudio::musicPlay((nxaudio::Music *) (intptr_t) handle, looping);
	*/

	private static native int poll0 (long handle); /*
		return nxaudio::musicPoll((nxaudio::Music *) (intptr_t) handle);
	*/

	private static native void setLooping0 (boolean looping); /*
		nxaudio::musicSetLooping(looping);
	*/

	private static native void resume0 (); /*
		nxaudio::musicResume();
	*/

	private static native void pause0 (); /*
		nxaudio::musicPause();
	*/

	private static native void stop0 (); /*
		nxaudio::musicStop();
	*/

	private static native void setVolume0 (float volume); /*
		nxaudio::musicSetVolume(volume);
	*/

	private static native void setPosition0 (float position); /*
		nxaudio::musicSetPosition(position);
	*/
}
