// warsmash-nx: audio for SwitchGDX on the Switch 2 (no SDL_mixer there): 32 voices for sounds and
// one music stream, as SDL_mixer had. A playing sound is named by a voice id (generation << 8 |
// channel), so an id kept after its sound ended (or after the channel was reused) does nothing,
// and the Java side (backend/java SwitchSound) needs no bookkeeping of its own. Output: libounce's
// audout, 48 kHz stereo Int16, fed by a mixer thread (the sink Torchlight runs on this console).
//
// Decoders: RIFF/WAVE 16-bit PCM (every WAV in Warcraft III 1.31: 9,558 files, mono/stereo,
// 22.05/44.1 kHz) and MP3 through mpg123 (9,698 files). Everything else is refused with a message.
#pragma once

#include <cstddef>
#include <cstdint>

namespace nxaudio {

constexpr int kChannels = 32;

struct Sample;
struct Music;

/// Opens audout and starts the mixer thread. Returns false (and the rest becomes silent no-ops)
/// when audout cannot be opened.
bool init();
void shutdown();

/// Decodes a whole WAV or MP3 into memory. On failure returns null and sets *error.
Sample *load(const uint8_t *data, size_t size, const char **error);
/// Stops every channel playing it, then frees it.
void release(Sample *sample);
/// Seconds of audio at the sample's own rate.
float duration(const Sample *sample);

/// Plays on the first free channel. Returns the voice id, or -1 when none is free or audio is off.
int64_t play(Sample *sample, bool loop, float volume, float pitch, float pan);
void stop(int64_t voice);
void pause(int64_t voice);
void resume(int64_t voice);
void setLooping(int64_t voice, bool loop);
void setVolume(int64_t voice, float volume); // 0..1
void setPan(int64_t voice, float pan);       // -1 left .. 1 right, SDL_mixer's law (the far side fades)
void setPitch(int64_t voice, float pitch);   // playback rate factor, 1 = as recorded
/// Every voice playing this sample.
void stopAll(Sample *sample);
void pauseAll(Sample *sample);
void resumeAll(Sample *sample);

/// Keeps a copy of the bytes and decodes while playing (MP3), or decodes at once (WAV).
Music *musicLoad(const uint8_t *data, size_t size, const char **error);
void musicRelease(Music *music);
void musicPlay(Music *music, bool loop);
void musicPause();
void musicResume();
void musicStop();
void musicSetVolume(float volume);
void musicSetLooping(bool loop);
void musicSetPosition(float seconds);
/// 0: this music is the current one and playing or paused; 1: it reached its end (reported once);
/// 2: stopped, or another music took its place.
int musicPoll(Music *music);

} // namespace nxaudio
