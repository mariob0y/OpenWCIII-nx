// Warsmash Switch 2 probe A: does the console give us what a Warsmash port needs, before any Java?
//
//   A  Joy-Con mouse readout: libounce's PadPointer (the Joy-Con that last moved), every Nmouse
//      device raw (type, Npad, position, delta, wheel, buttons, flags), and a USB mouse.
//   X  Graphics: a desktop OpenGL 3.3 core context through zink (Warsmash's shaders are
//      "#version 330 core"), the GL features Warsmash uses (usampler2D, sampler2DArray, texelFetch,
//      a depth-texture framebuffer), then cursors that follow the mice, and the frame rate.
//   Y  Memory: what the process has, and how much malloc gives in 256 MB steps (freed at once).
//   +  Exit.
//
// Safety (the same guard as the Torchlight port, cmake/switch2_guard.cmake, is linked here):
// the only writes are the log in sdmc:/switch/warsmash-probe/. No threads, no clocks, no
// settings. The Joy-Con mouse is read only: the probe never remaps its buttons
// (hidSetNmouseButtonConfig*, refused by scripts/check_switch2_safety.py) and never turns a
// pointer on or off (hidEnableNmousePointer): it reads what the system gives by default.
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GL/glcorearb.h>
#include <switch.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>

extern "C" size_t ws_switch2_guard_summary(char* out, size_t size);              // platform_guard.cpp
extern "C" void ws_switch2_crash_paths(const char* crash_file, const char* log_file);  // platform_crash.cpp

namespace {

constexpr char kDir[] = "sdmc:/switch/warsmash-probe";
// Appended to, run after run: each run starts with a "==== probe A run" header.
constexpr char kLog[] = "sdmc:/switch/warsmash-probe/probe.log";
constexpr char kCrash[] = "sdmc:/switch/warsmash-probe/crash.txt";
// The pointer area the Joy-Con mice are scaled to (the docked and handheld screen are 1080p).
constexpr s32 kPointerW = 1920;
constexpr s32 kPointerH = 1080;

std::FILE* g_log = nullptr;
u64 g_last_sync = 0;

// The log: flushed every line, synced to the card at most once a second (a program closed from
// the HOME menu loses what the card has not committed).
void log_line(const char* text) {
    if (g_log == nullptr) return;
    std::fprintf(g_log, "[%8.3f] %s\n", armTicksToNs(armGetSystemTick()) / 1e9, text);
    std::fflush(g_log);
    const u64 now = armGetSystemTick();
    if (armTicksToNs(now - g_last_sync) >= 1000000000ULL) {
        fsync(fileno(g_log));
        g_last_sync = now;
    }
}

void logf(const char* fmt, ...) {
    char text[512];
    va_list args;
    va_start(args, fmt);
    std::vsnprintf(text, sizeof text, fmt, args);
    va_end(args);
    log_line(text);
}

// A step boundary, synced to the card at once: if the next step crashes, the log still says
// where it was.
void mark(const char* fmt, ...) {
    char text[512];
    va_list args;
    va_start(args, fmt);
    std::vsnprintf(text, sizeof text, fmt, args);
    va_end(args);
    log_line(text);
    if (g_log != nullptr) {
        fsync(fileno(g_log));
        g_last_sync = armGetSystemTick();
    }
}

// On the text console and in the log.
void say(const char* fmt, ...) {
    char text[512];
    va_list args;
    va_start(args, fmt);
    std::vsnprintf(text, sizeof text, fmt, args);
    va_end(args);
    std::printf("%s\n", text);
    consoleUpdate(nullptr);
    log_line(text);
}

void say_guard() {
    static char text[4096];
    if (ws_switch2_guard_summary(text, sizeof text) > 0) say("%s", text);
}

void say_memory(const char* when) {
    u64 total = 0, used = 0;
    svcGetInfo(&total, InfoType_TotalMemorySize, CUR_PROCESS_HANDLE, 0);
    svcGetInfo(&used, InfoType_UsedMemorySize, CUR_PROCESS_HANDLE, 0);
    say("memory (%s): process total %llu MB, used %llu MB", when,
        (unsigned long long)(total >> 20), (unsigned long long)(used >> 20));
}

const char* npad_name(HidNpadIdType id) {
    switch (id) {
        case HidNpadIdType_Handheld: return "handheld";
        case HidNpadIdType_Other: return "other";
        default: break;
    }
    static char name[16];
    std::snprintf(name, sizeof name, "No%u", ((unsigned)id + 1) & 0xff);
    return name;
}

const char* side_name(PadPointerSide side) {
    return side == PadPointerSide_Left ? "left" : side == PadPointerSide_Right ? "right" : "-";
}

void type_set_text(u32 set, char* out, size_t size) {
    std::snprintf(out, size, "%s%s%s%s", (set & HidNmouseType_Merged) ? "merged " : "",
                  (set & HidNmouseType_JoyLeft) ? "joyL " : "",
                  (set & HidNmouseType_JoyRight) ? "joyR " : "",
                  (set & HidNmouseType_StandardMouse) ? "mouse " : "");
}

// --- the input everything below reads ----------------------------------------------------------

struct Input {
    PadState pad;
    PadPointer pointer;
    bool pointer_ok = false;
};

void input_init(Input& in) {
    padConfigureInput(8, HidNpadStyleSet_NpadStandard);
    padInitializeAny(&in.pad);
    hidInitializeMouse();
}

// The Joy-Con mice: registered while a mode that reads them runs, released after.
void pointer_open(Input& in) {
    mark("step: padPointerInitialize");
    const Result rc = padPointerInitialize(&in.pointer, kPointerW, kPointerH);
    in.pointer_ok = R_SUCCEEDED(rc);
    mark("padPointerInitialize(%d, %d): 0x%x", kPointerW, kPointerH, rc);
}

void pointer_close(Input& in) {
    if (in.pointer_ok) {
        mark("step: padPointerExit");
        padPointerExit(&in.pointer);
        mark("padPointerExit returned");
    }
    in.pointer_ok = false;
}

// --- A: Joy-Con mouse readout ------------------------------------------------------------------

struct Seen {
    bool active = false;
    int side = -1;
    u32 buttons = ~0u;
    s32 devices = -1;
    u32 usb_attr = ~0u;
    u32 usb_buttons = ~0u;
    u64 pad_held = ~0ull;
};

void mouse_readout(Input& in) {
    pointer_open(in);
    Seen seen;
    s64 travel_x = 0, travel_y = 0, wheel = 0;
    u64 last_summary = armGetSystemTick();
    std::printf("\x1b[2J");

    while (appletMainLoop()) {
        padUpdate(&in.pad);
        const u64 down = padGetButtonsDown(&in.pad);
        const u64 held = padGetButtons(&in.pad);
        if (down & HidNpadButton_Plus) break;

        std::printf("\x1b[1;1H");
        std::printf("Joy-Con mouse readout            + : back\x1b[K\n\x1b[K\n");

        // libounce's PadPointer: the Joy-Con that last did something.
        PadPointer& p = in.pointer;
        if (in.pointer_ok) {
            padPointerUpdate(&p, &in.pad);
            travel_x += p.delta_x < 0 ? -p.delta_x : p.delta_x;
            travel_y += p.delta_y < 0 ? -p.delta_y : p.delta_y;
            wheel += p.wheel_delta_y;
            std::printf("PadPointer: %s side %-5s x %4d y %4d  d %+4d %+4d  wheel %+3d  buttons 0x%02x\x1b[K\n",
                        p.active ? "ACTIVE" : "idle  ", side_name(p.side), p.x, p.y, p.delta_x, p.delta_y,
                        p.wheel_delta_y, p.buttons_cur);
            std::printf("  travel so far: x %lld  y %lld  wheel %lld\x1b[K\n", (long long)travel_x,
                        (long long)travel_y, (long long)wheel);
        } else {
            std::printf("PadPointer: not initialised (see log)\x1b[K\n\x1b[K\n");
        }

        // Every Nmouse device, raw.
        HidNmouseHandle handles[8];
        s32 count = 0;
        const Result list_rc = hidGetAllNmouseHandles(handles, 8, &count);
        std::printf("\x1b[K\nNmouse devices: %d (rc 0x%x)\x1b[K\n", (int)count, list_rc);
        for (s32 i = 0; i < count && i < 8; ++i) {
            u32 set = 0;
            HidNpadIdType npad = HidNpadIdType_No1;
            bool enabled = false;
            const Result set_rc = hidGetNmouseTypeSet(handles[i], &set);
            const Result npad_rc = hidGetNmouseNpadIdType(handles[i], &npad);
            const Result en_rc = hidIsNmousePointerEnabled(handles[i], &enabled);
            char types[48];
            type_set_text(set, types, sizeof types);
            HidNmouseStateWithDelta s{};
            size_t n = 0;
            hidGetNmouseStatesWithDelta(handles[i], &s, 1, &n);
            std::printf(" [%d] %-12s npad %-8s ptr %s  #%llu\x1b[K\n", (int)i, R_SUCCEEDED(set_rc) ? types : "?",
                        R_SUCCEEDED(npad_rc) ? npad_name(npad) : "?",
                        R_SUCCEEDED(en_rc) ? (enabled ? "on " : "off") : "?  ",
                        (unsigned long long)(n ? s.sampling_number : 0));
            if (n) {
                std::printf("     x %4d y %4d d %+4d %+4d wheel %+3d btn 0x%02x flags 0x%x unk 0x%x %s\x1b[K\n", s.x,
                            s.y, s.delta_x, s.delta_y, s.wheel_delta_y, s.buttons, s.flags, s.unk_x24,
                            (s.flags & 2) ? "tracking" : "NOT tracking");
            } else {
                std::printf("     no samples yet\x1b[K\n");
            }
        }
        for (s32 i = count; i < 4; ++i) std::printf("\x1b[K\n\x1b[K\n");

        HidNmouseHandle merged{}, standard{};
        const Result merged_rc = hidGetMergedNmouseHandle(&merged);
        const Result standard_rc = hidGetStandardMouseNmouseHandle(&standard);
        std::printf("merged pointer: rc 0x%x   standard mouse (Nmouse): rc 0x%x\x1b[K\n", merged_rc, standard_rc);

        // A USB mouse, through the ordinary mouse of the HID shared memory.
        HidMouseState ms{};
        hidGetMouseStates(&ms, 1);
        const bool usb = (ms.attributes & HidMouseAttribute_IsConnected) != 0;
        std::printf("\x1b[K\nUSB mouse: %s x %4d y %4d d %+4d %+4d wheel %+3d btn 0x%02x\x1b[K\n",
                    usb ? "connected" : "none     ", ms.x, ms.y, ms.delta_x, ms.delta_y, ms.wheel_delta_y,
                    ms.buttons);
        std::printf("pad held: 0x%016llx  styles: handheld 0x%x No1 0x%x\x1b[K\n", (unsigned long long)held,
                    hidGetNpadStyleSet(HidNpadIdType_Handheld), hidGetNpadStyleSet(HidNpadIdType_No1));
        std::printf("\x1b[K\nSlide a Joy-Con on a table (sensor down). Try L/ZL, R/ZR, the stick.\x1b[K\n");
        consoleUpdate(nullptr);

        // The log: every change, and a summary once a second.
        if (p.active != seen.active || (int)p.side != seen.side || p.buttons_cur != seen.buttons ||
            count != seen.devices || ms.attributes != seen.usb_attr || ms.buttons != seen.usb_buttons ||
            held != seen.pad_held) {
            logf("change: pointer %s side %s buttons 0x%x | nmouse devices %d | usb attr 0x%x buttons 0x%x | "
                 "pad held 0x%llx",
                 p.active ? "active" : "idle", side_name(p.side), p.buttons_cur, (int)count, ms.attributes,
                 ms.buttons, (unsigned long long)held);
            for (s32 i = 0; i < count && i < 8; ++i) {
                u32 set = 0;
                HidNpadIdType npad = HidNpadIdType_No1;
                hidGetNmouseTypeSet(handles[i], &set);
                hidGetNmouseNpadIdType(handles[i], &npad);
                char types[48];
                type_set_text(set, types, sizeof types);
                logf("  device %d: handle 0x%llx types %s npad %s", (int)i, (unsigned long long)handles[i].handle,
                     types, npad_name(npad));
            }
            seen.active = p.active;
            seen.side = p.side;
            seen.buttons = p.buttons_cur;
            seen.devices = count;
            seen.usb_attr = ms.attributes;
            seen.usb_buttons = ms.buttons;
            seen.pad_held = held;
        }
        const u64 now = armGetSystemTick();
        if (armTicksToNs(now - last_summary) >= 1000000000ULL) {
            logf("second: pointer x %d y %d travel %lld/%lld wheel %lld; merged rc 0x%x standard rc 0x%x", p.x, p.y,
                 (long long)travel_x, (long long)travel_y, (long long)wheel, merged_rc, standard_rc);
            last_summary = now;
        }
    }
    pointer_close(in);
    std::printf("\x1b[2J");
}

// --- X: desktop OpenGL 3.3 core and the cursors -----------------------------------------------

struct Gl {
    PFNGLGETSTRINGPROC GetString;
    PFNGLGETINTEGERVPROC GetIntegerv;
    PFNGLGETERRORPROC GetError;
    PFNGLVIEWPORTPROC Viewport;
    PFNGLCLEARCOLORPROC ClearColor;
    PFNGLCLEARPROC Clear;
    PFNGLCREATESHADERPROC CreateShader;
    PFNGLSHADERSOURCEPROC ShaderSource;
    PFNGLCOMPILESHADERPROC CompileShader;
    PFNGLGETSHADERIVPROC GetShaderiv;
    PFNGLGETSHADERINFOLOGPROC GetShaderInfoLog;
    PFNGLDELETESHADERPROC DeleteShader;
    PFNGLCREATEPROGRAMPROC CreateProgram;
    PFNGLATTACHSHADERPROC AttachShader;
    PFNGLLINKPROGRAMPROC LinkProgram;
    PFNGLGETPROGRAMIVPROC GetProgramiv;
    PFNGLGETPROGRAMINFOLOGPROC GetProgramInfoLog;
    PFNGLDELETEPROGRAMPROC DeleteProgram;
    PFNGLUSEPROGRAMPROC UseProgram;
    PFNGLGETUNIFORMLOCATIONPROC GetUniformLocation;
    PFNGLUNIFORM2FPROC Uniform2f;
    PFNGLUNIFORM4FPROC Uniform4f;
    PFNGLGENVERTEXARRAYSPROC GenVertexArrays;
    PFNGLBINDVERTEXARRAYPROC BindVertexArray;
    PFNGLDELETEVERTEXARRAYSPROC DeleteVertexArrays;
    PFNGLDRAWARRAYSPROC DrawArrays;
    PFNGLENABLEPROC Enable;
    PFNGLDISABLEPROC Disable;
    PFNGLBLENDFUNCPROC BlendFunc;
    PFNGLGENTEXTURESPROC GenTextures;
    PFNGLDELETETEXTURESPROC DeleteTextures;
    PFNGLBINDTEXTUREPROC BindTexture;
    PFNGLTEXIMAGE2DPROC TexImage2D;
    PFNGLTEXIMAGE3DPROC TexImage3D;
    PFNGLTEXPARAMETERIPROC TexParameteri;
    PFNGLGENFRAMEBUFFERSPROC GenFramebuffers;
    PFNGLDELETEFRAMEBUFFERSPROC DeleteFramebuffers;
    PFNGLBINDFRAMEBUFFERPROC BindFramebuffer;
    PFNGLFRAMEBUFFERTEXTURE2DPROC FramebufferTexture2D;
    PFNGLCHECKFRAMEBUFFERSTATUSPROC CheckFramebufferStatus;
    PFNGLDRAWBUFFERPROC DrawBuffer;
    PFNGLREADBUFFERPROC ReadBuffer;
};

bool gl_load(Gl& gl) {
    bool ok = true;
#define WS_LOAD(name)                                                                       \
    do {                                                                                    \
        gl.name = reinterpret_cast<decltype(gl.name)>(eglGetProcAddress("gl" #name));       \
        if (gl.name == nullptr) {                                                           \
            logf("eglGetProcAddress(gl%s): null", #name);                                   \
            ok = false;                                                                     \
        }                                                                                   \
    } while (0)
    WS_LOAD(GetString); WS_LOAD(GetIntegerv); WS_LOAD(GetError); WS_LOAD(Viewport);
    WS_LOAD(ClearColor); WS_LOAD(Clear); WS_LOAD(CreateShader); WS_LOAD(ShaderSource);
    WS_LOAD(CompileShader); WS_LOAD(GetShaderiv); WS_LOAD(GetShaderInfoLog); WS_LOAD(DeleteShader);
    WS_LOAD(CreateProgram); WS_LOAD(AttachShader); WS_LOAD(LinkProgram); WS_LOAD(GetProgramiv);
    WS_LOAD(GetProgramInfoLog); WS_LOAD(DeleteProgram); WS_LOAD(UseProgram); WS_LOAD(GetUniformLocation);
    WS_LOAD(Uniform2f); WS_LOAD(Uniform4f); WS_LOAD(GenVertexArrays); WS_LOAD(BindVertexArray);
    WS_LOAD(DeleteVertexArrays); WS_LOAD(DrawArrays); WS_LOAD(Enable); WS_LOAD(Disable); WS_LOAD(BlendFunc);
    WS_LOAD(GenTextures); WS_LOAD(DeleteTextures); WS_LOAD(BindTexture); WS_LOAD(TexImage2D);
    WS_LOAD(TexImage3D); WS_LOAD(TexParameteri); WS_LOAD(GenFramebuffers); WS_LOAD(DeleteFramebuffers);
    WS_LOAD(BindFramebuffer); WS_LOAD(FramebufferTexture2D); WS_LOAD(CheckFramebufferStatus);
    WS_LOAD(DrawBuffer); WS_LOAD(ReadBuffer);
#undef WS_LOAD
    return ok;
}

GLuint compile(const Gl& gl, GLenum type, const char* source, const char* what, bool& ok) {
    const GLuint shader = gl.CreateShader(type);
    gl.ShaderSource(shader, 1, &source, nullptr);
    gl.CompileShader(shader);
    GLint status = 0;
    gl.GetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (status != GL_TRUE) {
        char info[1024] = {};
        gl.GetShaderInfoLog(shader, sizeof info, nullptr, info);
        logf("shader %s: compile FAILED: %s", what, info);
        ok = false;
    }
    return shader;
}

GLuint link(const Gl& gl, const char* vs, const char* fs, const char* what, bool& ok) {
    const GLuint v = compile(gl, GL_VERTEX_SHADER, vs, what, ok);
    const GLuint f = compile(gl, GL_FRAGMENT_SHADER, fs, what, ok);
    const GLuint program = gl.CreateProgram();
    gl.AttachShader(program, v);
    gl.AttachShader(program, f);
    gl.LinkProgram(program);
    GLint status = 0;
    gl.GetProgramiv(program, GL_LINK_STATUS, &status);
    if (status != GL_TRUE) {
        char info[1024] = {};
        gl.GetProgramInfoLog(program, sizeof info, nullptr, info);
        logf("program %s: link FAILED: %s", what, info);
        ok = false;
    }
    gl.DeleteShader(v);
    gl.DeleteShader(f);
    return program;
}

// The GL features Warsmash's terrain and shadows use (TerrainShaders.java, DynamicShadowManager.java):
// an unsigned-integer texture read with texelFetch, a 2D array texture, a depth-texture framebuffer.
// Results go to the log; the summary comes back as one line.
void warsmash_features(const Gl& gl, char* summary, size_t size) {
    static const char* vs =
        "#version 330 core\n"
        "uniform usampler2D terrain_texture_list;\n"
        "uniform sampler2D height_texture;\n"
        "flat out uvec4 texture_indices;\n"
        "out float height;\n"
        "void main() {\n"
        "  texture_indices = texelFetch(terrain_texture_list, ivec2(gl_VertexID, 0), 0);\n"
        "  height = texelFetch(height_texture, ivec2(1, 1), 0).r;\n"
        "  gl_Position = vec4(0.0, 0.0, height, 1.0);\n"
        "}\n";
    static const char* fs =
        "#version 330 core\n"
        "uniform sampler2DArray cliff_textures;\n"
        "flat in uvec4 texture_indices;\n"
        "in float height;\n"
        "out vec4 color;\n"
        "void main() {\n"
        "  color = texture(cliff_textures, vec3(0.5, 0.5, float(texture_indices.x))) * height;\n"
        "}\n";
    bool shaders = true;
    const GLuint program = link(gl, vs, fs, "warsmash-terrain-like", shaders);
    gl.DeleteProgram(program);

    // A 2D array texture (cliff textures) and an unsigned integer texture (the terrain list).
    GLuint tex[3] = {};
    gl.GenTextures(3, tex);
    gl.BindTexture(GL_TEXTURE_2D_ARRAY, tex[0]);
    gl.TexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, 64, 64, 4, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    const GLenum array_error = gl.GetError();
    gl.BindTexture(GL_TEXTURE_2D, tex[1]);
    gl.TexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16UI, 64, 64, 0, GL_RGBA_INTEGER, GL_UNSIGNED_SHORT, nullptr);
    const GLenum uint_error = gl.GetError();

    // The shadow map: a 1024x1024 DEPTH_COMPONENT16 texture as the only attachment.
    gl.BindTexture(GL_TEXTURE_2D, tex[2]);
    gl.TexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT16, 1024, 1024, 0, GL_DEPTH_COMPONENT, GL_FLOAT, nullptr);
    gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    GLuint fbo = 0;
    gl.GenFramebuffers(1, &fbo);
    gl.BindFramebuffer(GL_FRAMEBUFFER, fbo);
    gl.FramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, tex[2], 0);
    gl.DrawBuffer(GL_NONE);
    gl.ReadBuffer(GL_NONE);
    const GLenum fbo_status = gl.CheckFramebufferStatus(GL_FRAMEBUFFER);
    gl.BindFramebuffer(GL_FRAMEBUFFER, 0);
    gl.DeleteFramebuffers(1, &fbo);
    gl.DeleteTextures(3, tex);
    gl.BindTexture(GL_TEXTURE_2D, 0);
    gl.BindTexture(GL_TEXTURE_2D_ARRAY, 0);

    logf("features: shaders %s, array texture err 0x%x, RGBA16UI err 0x%x, depth framebuffer 0x%x (%s)",
         shaders ? "OK" : "FAILED", array_error, uint_error, fbo_status,
         fbo_status == GL_FRAMEBUFFER_COMPLETE ? "complete" : "INCOMPLETE");
    std::snprintf(summary, size, "330 shaders %s | array tex %s | uint tex %s | depth FBO %s",
                  shaders ? "OK" : "FAIL", array_error == GL_NO_ERROR ? "OK" : "FAIL",
                  uint_error == GL_NO_ERROR ? "OK" : "FAIL", fbo_status == GL_FRAMEBUFFER_COMPLETE ? "OK" : "FAIL");
}

struct Egl {
    EGLDisplay display = EGL_NO_DISPLAY;
};

// The display is initialised once and terminated once, at exit: on the Switch 2 a second
// eglInitialize after eglTerminate fails in the same process (Torchlight, 2026-11-17).
bool egl_display(Egl& egl) {
    if (egl.display != EGL_NO_DISPLAY) return true;
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major = 0, minor = 0;
    if (display == EGL_NO_DISPLAY || eglInitialize(display, &major, &minor) == EGL_FALSE) {
        logf("eglInitialize failed: 0x%x", eglGetError());
        return false;
    }
    logf("EGL %d.%d, vendor %s, APIs %s", major, minor, eglQueryString(display, EGL_VENDOR),
         eglQueryString(display, EGL_CLIENT_APIS));
    egl.display = display;
    return true;
}

void graphics(Egl& egl, Input& in) {
    // Everything is reported after the console comes back; collected here meanwhile.
    char result[256] = "not started";
    char renderer[256] = "";
    char features[256] = "";
    char frames[256] = "";

    consoleExit(nullptr);  // the console and EGL share the default window
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
    do {
        if (!egl_display(egl)) {
            std::snprintf(result, sizeof result, "eglInitialize failed (see log)");
            break;
        }
        if (eglBindAPI(EGL_OPENGL_API) == EGL_FALSE) {
            std::snprintf(result, sizeof result, "eglBindAPI(OPENGL) failed: 0x%x", eglGetError());
            break;
        }
        const EGLint config_attribs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                                         EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 24, EGL_NONE};
        EGLConfig config = nullptr;
        EGLint count = 0;
        if (eglChooseConfig(egl.display, config_attribs, &config, 1, &count) == EGL_FALSE || count == 0) {
            std::snprintf(result, sizeof result, "eglChooseConfig(OPENGL_BIT) failed: 0x%x", eglGetError());
            break;
        }
        surface = eglCreateWindowSurface(egl.display, config, nwindowGetDefault(), nullptr);
        if (surface == EGL_NO_SURFACE) {
            std::snprintf(result, sizeof result, "eglCreateWindowSurface failed: 0x%x", eglGetError());
            break;
        }
        const EGLint core_attribs[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 3,
                                       EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
                                       EGL_NONE};
        context = eglCreateContext(egl.display, config, EGL_NO_CONTEXT, core_attribs);
        if (context == EGL_NO_CONTEXT) {
            logf("GL 3.3 core context failed: 0x%x; trying the default desktop context", eglGetError());
            context = eglCreateContext(egl.display, config, EGL_NO_CONTEXT, nullptr);
        }
        if (context == EGL_NO_CONTEXT || eglMakeCurrent(egl.display, surface, surface, context) == EGL_FALSE) {
            std::snprintf(result, sizeof result, "desktop GL context failed: 0x%x", eglGetError());
            break;
        }
        Gl gl{};
        if (!gl_load(gl)) {
            std::snprintf(result, sizeof result, "GL functions missing (see log)");
            break;
        }
        std::snprintf(renderer, sizeof renderer, "%s | %s | GLSL %s",
                      reinterpret_cast<const char*>(gl.GetString(GL_RENDERER)),
                      reinterpret_cast<const char*>(gl.GetString(GL_VERSION)),
                      reinterpret_cast<const char*>(gl.GetString(GL_SHADING_LANGUAGE_VERSION)));
        logf("GL: %s", renderer);
        warsmash_features(gl, features, sizeof features);

        // The cursors: one quad each, made from gl_VertexID (a core context needs a bound VAO).
        static const char* cursor_vs =
            "#version 330 core\n"
            "uniform vec2 u_pos;\n"
            "uniform vec2 u_size;\n"
            "void main() {\n"
            "  vec2 corner = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1)) * 2.0 - 1.0;\n"
            "  gl_Position = vec4(u_pos + corner * u_size, 0.0, 1.0);\n"
            "}\n";
        static const char* cursor_fs =
            "#version 330 core\n"
            "uniform vec4 u_color;\n"
            "out vec4 color;\n"
            "void main() { color = u_color; }\n";
        bool cursor_ok = true;
        const GLuint program = link(gl, cursor_vs, cursor_fs, "cursor", cursor_ok);
        const GLint u_pos = gl.GetUniformLocation(program, "u_pos");
        const GLint u_size = gl.GetUniformLocation(program, "u_size");
        const GLint u_color = gl.GetUniformLocation(program, "u_color");
        GLuint vao = 0;
        gl.GenVertexArrays(1, &vao);

        EGLint width = 0, height = 0;
        eglQuerySurface(egl.display, surface, EGL_WIDTH, &width);
        eglQuerySurface(egl.display, surface, EGL_HEIGHT, &height);
        logf("surface %dx%d; cursor program %s", width, height, cursor_ok ? "OK" : "FAILED");

        pointer_open(in);
        const u64 start = armGetSystemTick();
        u64 second = start;
        u32 frame_count = 0, total_frames = 0;
        float size = 0.02f;
        while (appletMainLoop()) {
            padUpdate(&in.pad);
            if (padGetButtonsDown(&in.pad) & HidNpadButton_Plus) break;
            if (in.pointer_ok) padPointerUpdate(&in.pointer, &in.pad);
            HidMouseState ms{};
            hidGetMouseStates(&ms, 1);

            const PadPointer& p = in.pointer;
            size += p.wheel_delta_y * 0.002f;
            if (size < 0.005f) size = 0.005f;
            if (size > 0.2f) size = 0.2f;

            gl.Viewport(0, 0, width, height);
            const bool clicking = (p.buttons_cur & HidMouseButton_Left) != 0;
            gl.ClearColor(clicking ? 0.25f : 0.05f, 0.08f, 0.12f, 1.0f);
            gl.Clear(GL_COLOR_BUFFER_BIT);
            gl.UseProgram(program);
            gl.BindVertexArray(vao);
            const float aspect = height > 0 ? float(width) / float(height) : 1.0f;
            auto draw = [&](float px, float py, float s, float r, float g, float b) {
                gl.Uniform2f(u_pos, px / kPointerW * 2.0f - 1.0f, 1.0f - py / kPointerH * 2.0f);
                gl.Uniform2f(u_size, s / aspect, s);
                gl.Uniform4f(u_color, r, g, b, 1.0f);
                gl.DrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            };
            // Every Nmouse device: left Joy-Con blue, right red, others grey.
            HidNmouseHandle handles[8];
            s32 count = 0;
            hidGetAllNmouseHandles(handles, 8, &count);
            for (s32 i = 0; i < count && i < 8; ++i) {
                HidNmouseState s{};
                size_t n = 0;
                u32 set = 0;
                if (R_FAILED(hidGetNmouseStates(handles[i], &s, 1, &n)) || n == 0) continue;
                hidGetNmouseTypeSet(handles[i], &set);
                const bool left = set & HidNmouseType_JoyLeft, right = set & HidNmouseType_JoyRight;
                draw(float(s.x), float(s.y), size * 0.6f, left ? 0.2f : right ? 0.9f : 0.6f, left ? 0.4f : 0.2f,
                     left ? 1.0f : right ? 0.2f : 0.6f);
            }
            // PadPointer: white, yellow while a button is held.
            if (p.active) draw(float(p.x), float(p.y), size * 0.35f, 1.0f, 1.0f, clicking ? 0.0f : 1.0f);
            // USB mouse: green (its coordinates are the shared memory's, scaled to the screen as is).
            if (ms.attributes & HidMouseAttribute_IsConnected)
                draw(float(ms.x) * kPointerW / 1280.0f, float(ms.y) * kPointerH / 720.0f, size * 0.5f, 0.2f, 1.0f,
                     0.3f);
            eglSwapBuffers(egl.display, surface);

            ++frame_count;
            ++total_frames;
            const u64 now = armGetSystemTick();
            if (armTicksToNs(now - second) >= 1000000000ULL) {
                logf("graphics: %u fps; pointer %s x %d y %d buttons 0x%x; nmouse %d; usb 0x%x", frame_count,
                     p.active ? "active" : "idle", p.x, p.y, p.buttons_cur, (int)count, ms.attributes);
                frame_count = 0;
                second = now;
            }
        }
        const double seconds = armTicksToNs(armGetSystemTick() - start) / 1e9;
        std::snprintf(frames, sizeof frames, "%u frames in %.1f s (%.1f fps)", total_frames, seconds,
                      seconds > 0 ? total_frames / seconds : 0.0);
        pointer_close(in);
        mark("step: glDelete*");
        gl.DeleteVertexArrays(1, &vao);
        gl.DeleteProgram(program);
        std::snprintf(result, sizeof result, "desktop GL: OK");
    } while (false);

    // Each step marked: on 2026-11-19 the probe died twice between here and the next say() (no
    // crash.txt then: libounce's abort path was still the system's).
    if (egl.display != EGL_NO_DISPLAY) {
        mark("step: eglMakeCurrent(none)");
        eglMakeCurrent(egl.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        mark("step: eglDestroyContext");
        if (context != EGL_NO_CONTEXT) eglDestroyContext(egl.display, context);
        mark("step: eglDestroySurface");
        if (surface != EGL_NO_SURFACE) eglDestroySurface(egl.display, surface);
    }
    mark("step: consoleInit after EGL");
    consoleInit(nullptr);
    mark("step: console back");
    say("graphics: %s", result);
    if (renderer[0]) say("  %s", renderer);
    if (features[0]) say("  %s", features);
    if (frames[0]) say("  %s", frames);
}

// --- Y: memory ---------------------------------------------------------------------------------

void memory(void) {
    say_memory("before");
    constexpr size_t kStep = size_t(256) << 20;
    constexpr int kMax = 64;  // 16 GB, more than the console has
    void* blocks[kMax] = {};
    int got = 0;
    while (got < kMax && (blocks[got] = std::malloc(kStep)) != nullptr) ++got;
    say("malloc: %d x 256 MB = %d MB before it returned null", got, got * 256);
    say_memory("holding it");
    for (int i = 0; i < got; ++i) std::free(blocks[i]);
    say_memory("after free");
}

void menu() {
    std::printf("\x1b[2J\x1b[1;1H");
    say("Warsmash Switch 2 probe A");
    say("firmware %u.%u.%u", HOSVER_MAJOR(hosversionGet()), HOSVER_MINOR(hosversionGet()),
        HOSVER_MICRO(hosversionGet()));
    say_memory("start");
    say("");
    say("A: Joy-Con mouse readout");
    say("X: desktop OpenGL 3.3 + Warsmash GL features + mouse cursors");
    say("Y: memory: how much malloc gives");
    say("+: exit");
    say("");
}

}  // namespace

int main() {
    consoleInit(nullptr);

    // The only writes: the log (appended) and, after a crash, crash.txt, both in the probe's own
    // folder on the SD card.
    mkdir("sdmc:/switch", 0777);
    mkdir(kDir, 0777);
    ws_switch2_crash_paths(kCrash, kLog);
    g_log = std::fopen(kLog, "a");
    if (g_log != nullptr) {
        std::fprintf(g_log, "\n==== probe A run (build %s %s) ====\n", __DATE__, __TIME__);
        std::fflush(g_log);
    }

    // No shader cache: its default folder, sdmc:/config/ouncevk, is outside the probe's own.
    setenv("MESA_SHADER_CACHE_DISABLE", "true", 1);

    Input in;
    input_init(in);
    Egl egl;
    menu();
    if (g_log == nullptr) say("(SD log could not be opened; continuing)");

    while (appletMainLoop()) {
        padUpdate(&in.pad);
        const u64 down = padGetButtonsDown(&in.pad);
        if (down & HidNpadButton_Plus) break;
        if (down & HidNpadButton_A) {
            mark("mode A: start");
            mouse_readout(in);
            mark("mode A: end");
            menu();
            say_guard();
        } else if (down & HidNpadButton_X) {
            mark("mode X: start");
            say("graphics: starting");
            graphics(egl, in);
            mark("mode X: end");
            say("");
            say_guard();
            say("A / X / Y / +");
        } else if (down & HidNpadButton_Y) {
            mark("mode Y: start");
            memory();
            mark("mode Y: end");
            say("A / X / Y / +");
        }
        consoleUpdate(nullptr);
    }

    say_guard();
    say("probe done");
    if (egl.display != EGL_NO_DISPLAY) eglTerminate(egl.display);
    if (g_log != nullptr) {
        fsync(fileno(g_log));
        std::fclose(g_log);
    }
    consoleExit(nullptr);
    return 0;
}
