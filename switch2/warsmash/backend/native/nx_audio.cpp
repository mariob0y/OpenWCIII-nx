// warsmash-nx: see nx_audio.h. Mixing is done in 1024-frame blocks (one 0x1000 page of stereo
// Int16 for audout, 21 ms at 48 kHz; four in flight, as Torchlight's platform_audio.cpp) under one
// mutex that the API calls also take briefly. Each voice resamples its source linearly at
// sourceRate * pitch / outputRate.
#include "nx_audio.h"

#include <switch.h>

#include <mpg123.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <vector>

// warsmash-nx: breadcrumbs into the log (nx_switchgdx.cpp), so a silent crash says what audio did last.
extern "C" void nx_event(const char *what);

namespace nxaudio {

struct Sample {
    std::vector<int16_t> pcm; // interleaved
    int channels = 1;
    int rate = 22050;
    size_t frames() const { return pcm.size() / channels; }
};

namespace {

constexpr size_t kBlockFrames = 1024;
constexpr size_t kBlocks = 4;
constexpr size_t kBlockBytes = kBlockFrames * 2 * sizeof(int16_t);

struct Voice {
    Sample *sample = nullptr;
    double position = 0; // in source frames
    float volume = 1, left = 1, right = 1, pitch = 1;
    bool loop = false, paused = false;
    uint32_t generation = 0;
};

struct Stream {
    // An MP3 decoded while it plays, or a WAV decoded up front (then `sample` is set).
    std::vector<uint8_t> bytes;
    size_t readPos = 0;
    mpg123_handle *mh = nullptr;
    Sample *sample = nullptr;
    int channels = 2, rate = 44100;
    std::vector<int16_t> buffer; // decoded, not yet mixed
    size_t bufferPos = 0;        // in samples (interleaved)
    double frac = 0;             // resampling phase between the last two frames
    int16_t last[2] = {0, 0}, next[2] = {0, 0};
    bool primed = false;
};

struct State {
    std::mutex lock;
    Voice voices[kChannels];
    uint32_t generation = 0;
    Music *music = nullptr;
    bool musicPlaying = false, musicPaused = false, musicLoop = false, musicEnded = false;
    float musicVolume = 1;
    // device
    bool open = false, running = false;
    int rate = 48000;
    AudioOutBuffer buffers[kBlocks]{};
    void *storage = nullptr;
    size_t next = 0, queued = 0;
    Thread thread{};
};

State &state() {
    static State s;
    return s;
}

// ---- decoding ----

uint32_t le32(const uint8_t *p) { return p[0] | p[1] << 8 | p[2] << 16 | (uint32_t) p[3] << 24; }
uint16_t le16(const uint8_t *p) { return (uint16_t) (p[0] | p[1] << 8); }

bool isWav(const uint8_t *data, size_t size) {
    return size >= 12 && !memcmp(data, "RIFF", 4) && !memcmp(data + 8, "WAVE", 4);
}

Sample *decodeWav(const uint8_t *data, size_t size, const char **error) {
    int channels = 0, rate = 0, bits = 0, tag = 0;
    const uint8_t *pcm = nullptr;
    size_t pcmSize = 0;
    for (size_t i = 12; i + 8 <= size;) {
        uint32_t chunk = le32(data + i + 4);
        const uint8_t *body = data + i + 8;
        size_t avail = std::min<size_t>(chunk, size - (i + 8));
        if (!memcmp(data + i, "fmt ", 4) && avail >= 16) {
            tag = le16(body);
            channels = le16(body + 2);
            rate = (int) le32(body + 4);
            bits = le16(body + 14);
        } else if (!memcmp(data + i, "data", 4)) {
            pcm = body;
            pcmSize = avail;
        }
        i += 8 + (size_t) chunk + (chunk & 1);
    }
    if (tag != 1 || bits != 16 || channels < 1 || channels > 2 || rate <= 0 || !pcm) {
        *error = "WAV: only 16-bit PCM, mono or stereo";
        return nullptr;
    }
    auto s = new Sample;
    s->channels = channels;
    s->rate = rate;
    s->pcm.resize(pcmSize / 2);
    for (size_t k = 0; k < s->pcm.size(); k++)
        s->pcm[k] = (int16_t) le16(pcm + 2 * k);
    s->pcm.resize(s->pcm.size() / channels * channels);
    return s;
}

// mpg123 reads from memory through these (the handle is a Stream).
mpg123_ssize_t memRead(void *handle, void *out, size_t count) {
    auto s = (Stream *) handle;
    size_t n = std::min(count, s->bytes.size() - s->readPos);
    memcpy(out, s->bytes.data() + s->readPos, n);
    s->readPos += n;
    return (mpg123_ssize_t) n;
}

off_t memSeek(void *handle, off_t offset, int whence) {
    auto s = (Stream *) handle;
    off_t base = whence == SEEK_SET ? 0 : whence == SEEK_CUR ? (off_t) s->readPos : (off_t) s->bytes.size();
    off_t target = base + offset;
    if (target < 0 || target > (off_t) s->bytes.size())
        return -1;
    s->readPos = (size_t) target;
    return target;
}

bool mpgInit() {
    static bool done = false;
    if (!done) {
        mpg123_init(); // a no-op since 1.27, needed before
        done = true;
    }
    return true;
}

/// Opens an MP3 held in s->bytes; on success s->mh is ready and s->rate/channels are set.
bool mpgOpen(Stream *s, const char **error) {
    mpgInit();
    int err = 0;
    s->mh = mpg123_new(nullptr, &err);
    if (!s->mh) {
        *error = "mpg123_new failed";
        return false;
    }
    mpg123_param(s->mh, MPG123_FLAGS, MPG123_QUIET, 0);
    mpg123_format_none(s->mh);
    const long *rates;
    size_t count;
    mpg123_rates(&rates, &count);
    for (size_t i = 0; i < count; i++)
        mpg123_format(s->mh, rates[i], MPG123_MONO | MPG123_STEREO, MPG123_ENC_SIGNED_16);
    long rate;
    int channels, encoding;
    if (mpg123_replace_reader_handle(s->mh, memRead, memSeek, nullptr) != MPG123_OK ||
        mpg123_open_handle(s->mh, s) != MPG123_OK ||
        mpg123_getformat(s->mh, &rate, &channels, &encoding) != MPG123_OK) {
        *error = "MP3: not decodable";
        mpg123_delete(s->mh);
        s->mh = nullptr;
        return false;
    }
    s->rate = (int) rate;
    s->channels = channels;
    return true;
}

Sample *decodeMp3(const uint8_t *data, size_t size, const char **error) {
    Stream s;
    s.bytes.assign(data, data + size);
    if (!mpgOpen(&s, error))
        return nullptr;
    auto out = new Sample;
    out->rate = s.rate;
    out->channels = s.channels;
    unsigned char chunk[16384];
    for (;;) {
        size_t done = 0;
        int rc = mpg123_read(s.mh, chunk, sizeof(chunk), &done);
        out->pcm.insert(out->pcm.end(), (int16_t *) chunk, (int16_t *) (chunk + done / 2 * 2));
        if (rc == MPG123_DONE || (rc != MPG123_OK && rc != MPG123_NEW_FORMAT))
            break;
    }
    mpg123_close(s.mh);
    mpg123_delete(s.mh);
    out->pcm.resize(out->pcm.size() / out->channels * out->channels);
    if (out->pcm.empty()) {
        delete out;
        *error = "MP3: no audio decoded";
        return nullptr;
    }
    return out;
}

// ---- mixing ----

void setPanGains(Voice &v, float pan) {
    // SDL_mixer's Mix_SetPanning as SwitchGDX used it: the near side stays full, the far side fades.
    pan = std::clamp(pan, -1.0f, 1.0f);
    v.left = pan <= 0 ? 1.0f : 1.0f - pan;
    v.right = pan <= 0 ? 1.0f + pan : 1.0f;
}

/// Mixes one voice into acc; returns false when it reached its end (not looping).
bool mixVoice(Voice &v, int32_t *acc, size_t frames, int outRate) {
    const Sample &s = *v.sample;
    const size_t n = s.frames();
    if (n == 0)
        return false;
    const double step = (double) s.rate * v.pitch / outRate;
    const float gl = v.volume * v.left, gr = v.volume * v.right;
    const int16_t *p = s.pcm.data();
    for (size_t f = 0; f < frames; f++) {
        if (v.position >= n) {
            if (!v.loop)
                return false;
            v.position = std::fmod(v.position, (double) n);
        }
        size_t i = (size_t) v.position;
        float t = (float) (v.position - i);
        size_t j = i + 1 < n ? i + 1 : (v.loop ? 0 : i);
        float l, r;
        if (s.channels == 1) {
            l = r = p[i] + (p[j] - p[i]) * t;
        } else {
            l = p[2 * i] + (p[2 * j] - p[2 * i]) * t;
            r = p[2 * i + 1] + (p[2 * j + 1] - p[2 * i + 1]) * t;
        }
        acc[2 * f] += (int32_t) (l * gl);
        acc[2 * f + 1] += (int32_t) (r * gr);
        v.position += step;
    }
    return true;
}

/// Next source frame of a streamed MP3; false at its end.
bool streamFrame(Stream &s, int16_t out[2], bool loop) {
    for (int attempt = 0; attempt < 3; attempt++) {
        if (s.bufferPos + s.channels <= s.buffer.size()) {
            out[0] = s.buffer[s.bufferPos];
            out[1] = s.buffer[s.bufferPos + (s.channels == 2 ? 1 : 0)];
            s.bufferPos += s.channels;
            return true;
        }
        s.buffer.resize(8192);
        size_t done = 0;
        int rc = mpg123_read(s.mh, (unsigned char *) s.buffer.data(), s.buffer.size() * 2, &done);
        s.buffer.resize(done / 2 / s.channels * s.channels);
        s.bufferPos = 0;
        // MPG123_NEW_FORMAT can come with no samples: not the end.
        if (s.buffer.empty() && rc != MPG123_OK && rc != MPG123_NEW_FORMAT) {
            if (!loop)
                return false;
            mpg123_seek(s.mh, 0, SEEK_SET);
        }
    }
    return false;
}

/// Mixes the music into acc; false when it ended (not looping).
bool mixMusic(State &st, int32_t *acc, size_t frames) {
    Music *m = st.music;
    auto &s = *(Stream *) m;
    const float g = st.musicVolume;
    if (s.sample) {
        Voice v;
        v.sample = s.sample;
        v.position = s.frac;
        v.loop = st.musicLoop;
        v.volume = g;
        bool more = mixVoice(v, acc, frames, st.rate);
        s.frac = v.position;
        return more;
    }
    const double step = (double) s.rate / st.rate;
    if (!s.primed) {
        if (!streamFrame(s, s.last, st.musicLoop) || !streamFrame(s, s.next, st.musicLoop))
            return false;
        s.primed = true;
        nx_event("audio: mixer decoded the music's first frames");
    }
    for (size_t f = 0; f < frames; f++) {
        float t = (float) s.frac;
        acc[2 * f] += (int32_t) ((s.last[0] + (s.next[0] - s.last[0]) * t) * g);
        acc[2 * f + 1] += (int32_t) ((s.last[1] + (s.next[1] - s.last[1]) * t) * g);
        s.frac += step;
        while (s.frac >= 1) {
            s.frac -= 1;
            s.last[0] = s.next[0];
            s.last[1] = s.next[1];
            if (!streamFrame(s, s.next, st.musicLoop))
                return false;
        }
    }
    return true;
}

bool submit(State &st, const int16_t *block) {
    if (st.queued >= kBlocks) {
        AudioOutBuffer *released = nullptr;
        u32 count = 0;
        // A second, not forever: a service that stops answering must not hold the shutdown.
        if (R_FAILED(audoutWaitPlayFinish(&released, &count, 1000000000ULL)) || count == 0)
            return st.running;
        st.queued -= std::min<size_t>(count, st.queued);
    }
    AudioOutBuffer &b = st.buffers[st.next];
    memcpy(b.buffer, block, kBlockBytes);
    b.data_size = kBlockBytes;
    if (R_FAILED(audoutAppendAudioOutBuffer(&b)))
        return false;
    st.next = (st.next + 1) % kBlocks;
    st.queued++;
    return true;
}

void mixerThread(void *) {
    State &st = state();
    static int32_t acc[kBlockFrames * 2];
    static int16_t block[kBlockFrames * 2];
    while (st.running) {
        memset(acc, 0, sizeof(acc));
        {
            std::lock_guard<std::mutex> guard(st.lock);
            for (int c = 0; c < kChannels; c++) {
                Voice &v = st.voices[c];
                if (!v.sample || v.paused)
                    continue;
                if (!mixVoice(v, acc, kBlockFrames, st.rate))
                    v.sample = nullptr;
            }
            if (st.music && st.musicPlaying && !st.musicPaused && !mixMusic(st, acc, kBlockFrames)) {
                st.musicPlaying = false;
                st.musicEnded = true;
            }
        }
        for (size_t k = 0; k < kBlockFrames * 2; k++)
            block[k] = (int16_t) std::clamp<int32_t>(acc[k], -32768, 32767);
        if (!submit(st, block))
            svcSleepThread(5000000); // the device refused: do not spin
    }
}

} // namespace

// ---- API ----

bool init() {
    State &st = state();
    if (st.open)
        return true;
    if (R_FAILED(audoutInitialize())) {
        printf("audio: audoutInitialize failed, no sound\n");
        return false;
    }
    if (R_FAILED(audoutStartAudioOut())) {
        printf("audio: audoutStartAudioOut failed, no sound\n");
        audoutExit();
        return false;
    }
    st.storage = aligned_alloc(0x1000, kBlockBytes * kBlocks);
    if (!st.storage) {
        audoutStopAudioOut();
        audoutExit();
        return false;
    }
    memset(st.storage, 0, kBlockBytes * kBlocks);
    for (size_t i = 0; i < kBlocks; i++) {
        AudioOutBuffer &b = st.buffers[i];
        b.next = nullptr;
        b.buffer = (uint8_t *) st.storage + i * kBlockBytes;
        b.buffer_size = kBlockBytes;
        b.data_size = kBlockBytes;
        b.data_offset = 0;
    }
    st.rate = (int) audoutGetSampleRate();
    st.open = true;
    st.running = true;
    // One priority step above the calling (game) thread, on another core: libnx's default would
    // put it below the game on the game's core, where a long synchronous load starves it
    // (Torchlight, 2026-10).
    s32 priority = 0x2C;
    svcGetThreadPriority(&priority, CUR_THREAD_HANDLE);
    const int core = svcGetCurrentProcessorNumber() == 2 ? 0 : 2;
    if (R_FAILED(threadCreate(&st.thread, mixerThread, nullptr, nullptr, 0x10000, priority - 1, core)) ||
        R_FAILED(threadStart(&st.thread))) {
        printf("audio: no mixer thread, no sound\n");
        st.running = false;
        audoutStopAudioOut();
        audoutExit();
        free(st.storage);
        st.storage = nullptr;
        st.open = false;
        return false;
    }
    printf("audio: audout %d Hz, %u channels; mixer at priority 0x%x on core %d\n", st.rate,
           (unsigned) audoutGetChannelCount(), (unsigned) (priority - 1), core);
    return true;
}

void shutdown() {
    State &st = state();
    if (!st.open)
        return;
    st.running = false;
    audoutStopAudioOut(); // releases the queued buffers and wakes a waiting mixer
    threadWaitForExit(&st.thread);
    threadClose(&st.thread);
    audoutExit();
    free(st.storage);
    st.storage = nullptr;
    st.open = false;
}

Sample *load(const uint8_t *data, size_t size, const char **error) {
    *error = nullptr;
    if (!data || size < 4) {
        *error = "empty audio file";
        return nullptr;
    }
    return isWav(data, size) ? decodeWav(data, size, error) : decodeMp3(data, size, error);
}

void release(Sample *sample) {
    if (!sample)
        return;
    State &st = state();
    {
        std::lock_guard<std::mutex> guard(st.lock);
        for (auto &v : st.voices)
            if (v.sample == sample)
                v.sample = nullptr;
    }
    delete sample;
}

float duration(const Sample *sample) {
    return sample && sample->rate ? (float) sample->frames() / sample->rate : 0;
}

int64_t play(Sample *sample, bool loop, float volume, float pitch, float pan) {
    State &st = state();
    if (!st.open || !sample)
        return -1;
    std::lock_guard<std::mutex> guard(st.lock);
    int channel = -1;
    for (int c = 0; c < kChannels && channel < 0; c++)
        if (!st.voices[c].sample)
            channel = c;
    if (channel < 0)
        return -1;
    Voice &v = st.voices[channel];
    v = Voice{};
    v.sample = sample;
    v.loop = loop;
    v.volume = std::clamp(volume, 0.0f, 1.0f);
    v.pitch = std::clamp(pitch, 0.05f, 8.0f);
    setPanGains(v, pan);
    v.generation = ++st.generation & 0xFFFFFF;
    return (int64_t) v.generation << 8 | channel;
}

/// Runs f on the voice the id names, if it still plays that sound.
template <typename F> static void withVoice(int64_t voice, F f) {
    if (voice < 0)
        return;
    const int channel = (int) (voice & 0xFF);
    if (channel >= kChannels)
        return;
    State &st = state();
    std::lock_guard<std::mutex> guard(st.lock);
    Voice &v = st.voices[channel];
    if (v.sample && v.generation == (uint32_t) (voice >> 8))
        f(v);
}

template <typename F> static void withSample(Sample *sample, F f) {
    State &st = state();
    std::lock_guard<std::mutex> guard(st.lock);
    for (auto &v : st.voices)
        if (sample && v.sample == sample)
            f(v);
}

void stop(int64_t voice) { withVoice(voice, [](Voice &v) { v.sample = nullptr; }); }
void pause(int64_t voice) { withVoice(voice, [](Voice &v) { v.paused = true; }); }
void resume(int64_t voice) { withVoice(voice, [](Voice &v) { v.paused = false; }); }
void setLooping(int64_t voice, bool loop) { withVoice(voice, [=](Voice &v) { v.loop = loop; }); }
void setVolume(int64_t voice, float volume) {
    withVoice(voice, [=](Voice &v) { v.volume = std::clamp(volume, 0.0f, 1.0f); });
}
void setPan(int64_t voice, float pan) { withVoice(voice, [=](Voice &v) { setPanGains(v, pan); }); }
void setPitch(int64_t voice, float pitch) {
    withVoice(voice, [=](Voice &v) { v.pitch = std::clamp(pitch, 0.05f, 8.0f); });
}
void stopAll(Sample *sample) { withSample(sample, [](Voice &v) { v.sample = nullptr; }); }
void pauseAll(Sample *sample) { withSample(sample, [](Voice &v) { v.paused = true; }); }
void resumeAll(Sample *sample) { withSample(sample, [](Voice &v) { v.paused = false; }); }

Music *musicLoad(const uint8_t *data, size_t size, const char **error) {
    *error = nullptr;
    {
        char line[96];
        snprintf(line, sizeof line, "audio: opening music (%s, %u bytes)", isWav(data, size) ? "wav" : "mp3",
                (unsigned) size);
        nx_event(line);
    }
    auto s = new Stream;
    if (isWav(data, size)) {
        s->sample = decodeWav(data, size, error);
        if (!s->sample) {
            delete s;
            return nullptr;
        }
        return (Music *) s;
    }
    s->bytes.assign(data, data + size);
    if (!mpgOpen(s, error)) {
        delete s;
        return nullptr;
    }
    return (Music *) s;
}

void musicRelease(Music *music) {
    if (!music)
        return;
    State &st = state();
    {
        std::lock_guard<std::mutex> guard(st.lock);
        if (st.music == music) {
            st.music = nullptr;
            st.musicPlaying = false;
        }
    }
    auto s = (Stream *) music;
    if (s->mh) {
        mpg123_close(s->mh);
        mpg123_delete(s->mh);
    }
    delete s->sample;
    delete s;
}

static void rewind(Stream &s) {
    s.frac = 0;
    s.primed = false;
    s.buffer.clear();
    s.bufferPos = 0;
    if (s.mh)
        mpg123_seek(s.mh, 0, SEEK_SET);
}

void musicPlay(Music *music, bool loop) {
    nx_event(music ? "audio: music play" : "audio: music stop");
    State &st = state();
    std::lock_guard<std::mutex> guard(st.lock);
    st.music = music;
    if (music)
        rewind(*(Stream *) music);
    st.musicLoop = loop;
    st.musicPlaying = music != nullptr;
    st.musicPaused = false;
    st.musicEnded = false;
}

int musicPoll(Music *music) {
    State &st = state();
    std::lock_guard<std::mutex> guard(st.lock);
    if (!music || st.music != music)
        return 2;
    if (st.musicPlaying)
        return 0;
    if (st.musicEnded) {
        st.musicEnded = false;
        return 1;
    }
    return 2;
}

void musicPause() {
    std::lock_guard<std::mutex> guard(state().lock);
    state().musicPaused = true;
}

void musicResume() {
    std::lock_guard<std::mutex> guard(state().lock);
    state().musicPaused = false;
}

void musicStop() {
    std::lock_guard<std::mutex> guard(state().lock);
    state().musicPlaying = false;
}

void musicSetVolume(float volume) {
    std::lock_guard<std::mutex> guard(state().lock);
    state().musicVolume = std::clamp(volume, 0.0f, 1.0f);
}

void musicSetLooping(bool loop) {
    std::lock_guard<std::mutex> guard(state().lock);
    state().musicLoop = loop;
}

void musicSetPosition(float seconds) {
    State &st = state();
    std::lock_guard<std::mutex> guard(st.lock);
    if (!st.music)
        return;
    auto &s = *(Stream *) st.music;
    s.primed = false;
    s.buffer.clear();
    s.bufferPos = 0;
    if (s.sample) {
        s.frac = std::max(0.0f, seconds) * s.sample->rate;
    } else if (s.mh) {
        s.frac = 0;
        mpg123_seek(s.mh, (off_t) (std::max(0.0f, seconds) * s.rate), SEEK_SET);
    }
}

} // namespace nxaudio
