// Thread placement on the Switch 2 (warsmash-nx, 2026-11-23).
//
// libounce's pthread_create is threadCreate(..., 0x3B, -2): -2 is "the creator's core", so every
// thread the GL driver (Mesa: zink's threaded-context and flush workers) starts lands on the game
// thread's core and takes turns with it. Torchlight measured 30% of its game thread waiting in
// eglSwapBuffers -> tc_flush -> _tc_sync for exactly that, and
// moved such threads to another core with the same wrap as below.
//
// Cores, in the order they are given out (the game thread's core is whatever the system started
// it on, read at run time; the audio mixer picks its own, nx_audio.cpp):
//   driver core  = the first allowed core that is not the game thread's: every default-core thread
//   worker cores = the remaining allowed cores, one ParallelJobs worker each, at the game thread's
//                  priority (above the driver's 0x3B; the mixer, at game - 1, still preempts them)
// More workers than worker cores (warsmash.ini [Emulator] Workers=N) go round-robin over the
// worker cores, then the driver core.
//
// Linked with -Wl,--wrap=threadCreate (warsmash/CMakeLists.txt).

#include <switch.h>

#include <cstdio>

namespace {

struct CoreLayout {
    bool ready = false;
    u64 allowed = 0;
    int gameCore = -1;
    s32 gamePriority = 0x2C;
    int driverCore = -1;
    int workerCores[8] = {};
    int workerCoreCount = 0;
};

CoreLayout g_layout;
Mutex g_layoutLock;

const CoreLayout &layout() {
    mutexLock(&g_layoutLock);
    if (!g_layout.ready) {
        CoreLayout l;
        if (R_FAILED(svcGetInfo(&l.allowed, InfoType_CoreMask, CUR_PROCESS_HANDLE, 0)))
            l.allowed = 0;
        const Handle main = envGetMainThreadHandle();
        s32 core = -1;
        u64 mask = 0;
        if (R_SUCCEEDED(svcGetThreadCoreMask(&core, &mask, main)))
            l.gameCore = core;
        s32 priority = 0x2C;
        if (R_SUCCEEDED(svcGetThreadPriority(&priority, main)))
            l.gamePriority = priority;
        for (int c = 0; c < 64; c++) {
            if (!((l.allowed >> c) & 1) || c == l.gameCore)
                continue;
            if (l.driverCore < 0)
                l.driverCore = c;
            else if (l.workerCoreCount < 8)
                l.workerCores[l.workerCoreCount++] = c;
        }
        l.ready = true;
        g_layout = l;
    }
    mutexUnlock(&g_layoutLock);
    return g_layout;
}

} // namespace

extern "C" Result __real_threadCreate(Thread *t, ThreadFunc entry, void *arg, void *stack_mem, size_t stack_sz,
                                      int prio, int cpuid);

// Every threadCreate outside libounce's own thread.c comes through here (pthread_create's
// included). A thread asking for the default core starts on the driver core instead; a thread
// that names its core (the audio mixer) keeps it. Clearwing's Java threads come through here too
// and then place themselves (clearwingPlaceThread; ParallelJobs workers via nxPlaceWorker).
extern "C" Result __wrap_threadCreate(Thread *t, ThreadFunc entry, void *arg, void *stack_mem, size_t stack_sz,
                                      int prio, int cpuid) {
    if (cpuid == -2) {
        const int core = layout().driverCore;
        if (core >= 0) {
            const Result rc = __real_threadCreate(t, entry, arg, stack_mem, stack_sz, prio, core);
            if (R_SUCCEEDED(rc))
                return rc;
        }
    }
    return __real_threadCreate(t, entry, arg, stack_mem, stack_sz, prio, cpuid);
}

// The number of cores left for ParallelJobs workers (neither the game thread's nor the driver's).
extern "C" int nxWorkerCoreCount(void) {
    return layout().workerCoreCount;
}

// Called by ParallelJobs worker number `worker` (1..) on itself as it starts.
extern "C" void nxPlaceWorker(int worker) {
    const CoreLayout &l = layout();
    int core = -1;
    const int slots = l.workerCoreCount + (l.driverCore >= 0 ? 1 : 0);
    if (worker >= 1 && slots > 0) {
        const int slot = (worker - 1) % slots;
        core = slot < l.workerCoreCount ? l.workerCores[slot] : l.driverCore;
    }
    (void) svcSetThreadPriority(CUR_THREAD_HANDLE, (u32) l.gamePriority);
    if (core >= 0)
        (void) svcSetThreadCoreMask(CUR_THREAD_HANDLE, core, 1u << core);
    printf("threads: worker %d on core %d at priority 0x%x\n", worker, core, (unsigned) l.gamePriority);
}

// One log line: the allowed cores and who goes where.
extern "C" void nxLogCoreLayout(void) {
    const CoreLayout &l = layout();
    char workers[64] = "none";
    int n = 0;
    for (int i = 0; i < l.workerCoreCount && n < (int) sizeof(workers) - 4; i++)
        n += snprintf(workers + n, sizeof(workers) - n, i == 0 ? "%d" : ",%d", l.workerCores[i]);
    printf("threads: allowed cores 0x%llx; game thread on core %d at priority 0x%x; driver threads on core %d; "
           "worker cores %s\n",
           (unsigned long long) l.allowed, l.gameCore, (unsigned) l.gamePriority, l.driverCore, workers);
}
