// Switch 2 only: a crash ends here, not in the system's crash reporter. Without a handler of the
// program's own, a fault goes to the kernel's debug path and the system files a crash report in
// its own storage (and sends it when online). This one appends to `crash.txt` in the program's
// own folder (program counter, link register and fault address as offsets into the .nro, and
// the code addresses found on the stack), copies the same text into the program's log, and ends
// the process. Adapted from Torchlight's src/platform/switch/platform_crash.cpp.
//
// libounce's own aborts land here too: diagAbortWithResult (weak in libounce, where it does
// svcBreak(Panic) and so hands the process to the system's crash reporting) is replaced below.
// The window and framebuffer code call it when a present or dequeue fails; probe A died that way
// twice (2026-11-19) when its text console came back after EGL.
//
// abort() lands here too (`-Wl,--wrap=abort`, cmake/switch2_guard.cmake): newlib's abort ends the
// process without a word, and it is where std::terminate (an uncaught C++ exception, such as
// Clearwing's "Uncaught <class> thrown in <method>"), a failed assert and a malloc that finds its
// heap corrupt all end. A terminate handler installed at start-up records the exception's text
// first. Probe B died that way, without a trace, on 2026-11-20.
//
// The program says where to write with ws_switch2_crash_paths() at start-up; until it does, a
// crash still ends here and exits, it just writes nothing.
#if defined(__SWITCH2__)

#include <switch.h>

#include <fcntl.h>
#include <unistd.h>

#include <cstdio>
#include <cstring>
#include <exception>
#include <typeinfo>

extern "C" {

// The handler runs on this stack; stdio and the SD card need more than libnx's 1 KiB.
alignas(16) u8 __nx_exception_stack[0x10000];
u64 __nx_exception_stack_size = sizeof(__nx_exception_stack);

void __libnx_exception_handler(ThreadExceptionDump* ctx);
void ws_switch2_crash_paths(const char* crash_file, const char* log_file);
int ws_switch2_crash_environment(char* out, size_t size);
void NX_NORETURN diagAbortWithResult(Result res);
void NX_NORETURN __wrap_abort(void);

// libounce's exception entry hands a fault back to the kernel (the system's crash reporting) when
// IsCurrentProcessBeingDebugged is set, unless this is non-zero (weak 0 in libounce init.c). Probe
// B's CPU-fault self-test left no crash.txt (2026-11-20): kaiHEN may hold a debug session on the
// process. Our handler only writes a file and exits, so run it either way.
u32 __nx_exception_ignoredebug = 1;
}

namespace {

// Copied in at start-up, so the handler allocates nothing.
char g_crash_path[256];
char g_log_path[256];
char g_text[4096];
char g_note[512];       // what std::terminate found, for abort's report
bool g_ending = false;  // set while reporting; a second fault or abort just exits

// The program's code mapping: what an address is reported relative to.
MemoryInfo code_region() {
    MemoryInfo info{};
    u32 page = 0;
    (void)svcQueryMemory(&info, &page, reinterpret_cast<u64>(&__libnx_exception_handler));
    return info;
}

long long code_offset(const MemoryInfo& code, u64 address) {
    return address >= code.addr && address < code.addr + code.size ? static_cast<long long>(address - code.addr)
                                                                    : -1;
}

bool readable(u64 address) {
    MemoryInfo info{};
    u32 page = 0;
    if (R_FAILED(svcQueryMemory(&info, &page, address))) return false;
    return (info.perm & Perm_R) != 0 && address + 8 <= info.addr + info.size;
}

// Appends the code addresses found on the stack above sp to g_text; returns the new length.
int stack_scan(const MemoryInfo& code, u64 sp, int used) {
    int found = 0;
    for (u64 at = sp; at < sp + 0x4000 && found < 48 && used > 0 && used < static_cast<int>(sizeof g_text) - 64;
         at += 8) {
        if (!readable(at)) break;
        const long long hit = code_offset(code, *reinterpret_cast<const u64*>(at));
        if (hit < 0) continue;
        used += std::snprintf(g_text + used, sizeof g_text - used, "  sp+0x%llx: +0x%llx\n",
                              static_cast<unsigned long long>(at - sp), hit);
        ++found;
    }
    return used;
}

void append(const char* path, const char* text, size_t size) {
    if (path[0] == '\0') return;
    const int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd < 0) return;
    (void)write(fd, text, size);
    fsync(fd);
    close(fd);
}

void report(int used) {
    if (used > 0) {
        append(g_crash_path, g_text, static_cast<size_t>(used));
        append(g_log_path, g_text, static_cast<size_t>(used));
    }
}

}  // namespace

extern "C" void ws_switch2_crash_paths(const char* crash_file, const char* log_file) {
    std::snprintf(g_crash_path, sizeof g_crash_path, "%s", crash_file ? crash_file : "");
    std::snprintf(g_log_path, sizeof g_log_path, "%s", log_file ? log_file : "");
}

// One line for the program's log at start-up: what decides whether a CPU fault reaches the handler.
// The kernel enters the process at its entry point (nx-hbloader's, which forwards to the NRO's
// crt0), and libounce's entry then checks the debug state. If faults still leave no crash.txt
// while this says "not debugged", the kernel does not deliver user-mode exceptions at all.
extern "C" int ws_switch2_crash_environment(char* out, size_t size) {
    u64 debugged = 0;
    const Result rc = svcGetInfo(&debugged, InfoType_DebuggerAttached, INVALID_HANDLE, 0);
    const char* loader = envGetLoaderInfo();
    int loader_len = 0;
    if (loader != nullptr) {
        const u64 total = envGetLoaderInfoSize();
        while (static_cast<u64>(loader_len) < total && loader_len < 80 && loader[loader_len] != '\0' &&
               loader[loader_len] != '\n' && loader[loader_len] != '\r')
            ++loader_len;
    }
    return std::snprintf(out, size, "crash handler: process %s (svcGetInfo rc 0x%x), ignoredebug %u, loader \"%.*s\"",
                         R_FAILED(rc) ? "debug state unknown" : (debugged ? "BEING DEBUGGED" : "not debugged"), rc,
                         __nx_exception_ignoredebug, loader_len, loader != nullptr ? loader : "");
}

extern "C" void __libnx_exception_handler(ThreadExceptionDump* ctx) {
    if (g_ending) svcExitProcess();  // a fault inside the handler
    g_ending = true;

    const MemoryInfo code = code_region();
    int used = std::snprintf(
        g_text, sizeof g_text,
        "[%8.3f] CRASH: desc 0x%x esr 0x%x\npc   +0x%llx (0x%llx)\nlr   +0x%llx (0x%llx)\n"
        "far  0x%llx\nsp   0x%llx\nstack code addresses:\n",
        armTicksToNs(armGetSystemTick()) / 1e9, ctx->error_desc, ctx->esr, code_offset(code, ctx->pc.x),
        static_cast<unsigned long long>(ctx->pc.x), code_offset(code, ctx->lr.x),
        static_cast<unsigned long long>(ctx->lr.x), static_cast<unsigned long long>(ctx->far.x),
        static_cast<unsigned long long>(ctx->sp.x));
    report(stack_scan(code, ctx->sp.x, used));
    svcExitProcess();
}

// Replaces libounce's weak diagAbortWithResult: report the result code and the caller, then
// exit, instead of svcBreak(Panic).
extern "C" void NX_NORETURN diagAbortWithResult(Result res) {
    if (g_ending) svcExitProcess();
    g_ending = true;

    const MemoryInfo code = code_region();
    const u64 caller = reinterpret_cast<u64>(__builtin_return_address(0));
    const int used = std::snprintf(g_text, sizeof g_text,
                                   "[%8.3f] ABORT: diagAbortWithResult 0x%x (module %u, description %u)\n"
                                   "caller +0x%llx (0x%llx)\nstack code addresses:\n",
                                   armTicksToNs(armGetSystemTick()) / 1e9, res, R_MODULE(res), R_DESCRIPTION(res),
                                   code_offset(code, caller), static_cast<unsigned long long>(caller));
    report(stack_scan(code, reinterpret_cast<u64>(__builtin_frame_address(0)), used));
    svcExitProcess();
}

// Replaces newlib's abort (see the top). Integer formatting only: abort may come from inside a
// corrupt malloc, and printing a double can allocate.
extern "C" void NX_NORETURN __wrap_abort(void) {
    if (g_ending) svcExitProcess();
    g_ending = true;

    const MemoryInfo code = code_region();
    const u64 caller = reinterpret_cast<u64>(__builtin_return_address(0));
    const int used = std::snprintf(g_text, sizeof g_text,
                                   "[%llu ms] ABORT: abort()%s%s\ncaller +0x%llx (0x%llx)\nstack code addresses:\n",
                                   static_cast<unsigned long long>(armTicksToNs(armGetSystemTick()) / 1000000ULL),
                                   g_note[0] ? " after " : "", g_note, code_offset(code, caller),
                                   static_cast<unsigned long long>(caller));
    report(stack_scan(code, reinterpret_cast<u64>(__builtin_frame_address(0)), used));
    svcExitProcess();
}

namespace {

// std::terminate: note what was thrown, then abort (above) writes it with the stack.
[[noreturn]] void on_terminate() {
    std::snprintf(g_note, sizeof g_note, "std::terminate (no exception)");
    if (const std::exception_ptr current = std::current_exception()) {
        try {
            std::rethrow_exception(current);
        } catch (const std::exception& e) {
            std::snprintf(g_note, sizeof g_note, "std::terminate: %s: %s", typeid(e).name(), e.what());
        } catch (...) {
            std::snprintf(g_note, sizeof g_note, "std::terminate: an exception not derived from std::exception");
        }
    }
    __wrap_abort();
}

// Installed before main: this file is always linked (__libnx_exception_handler is required).
struct InstallTerminateHandler {
    InstallTerminateHandler() { std::set_terminate(on_terminate); }
} g_install_terminate_handler;

}  // namespace

#endif  // __SWITCH2__
