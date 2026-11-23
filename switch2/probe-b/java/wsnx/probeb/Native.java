package wsnx.probeb;

/**
 * The console side of probe B, as inline C++ that Clearwing compiles in (jnigen style comments).
 * On the PC's JVM these natives do not exist; {@link Out} falls back to a plain log file.
 *
 * Safety: the only writes are the log (appended), watch.log and crash.txt in the probe's own
 * folder, all through newlib, i.e. through the guard's wrapped fsFsOpenFile/CreateFile. No
 * services beyond what the runtime already has (hid for the pad).
 *
 * The watchdog (round 3, 2026-11-20): a native thread, outside the Java VM so a stopped or
 * corrupted collector cannot stop it, appends one line a second to watch.log: the last mark, the
 * last progress note, the heap and what the collector is doing. If the program hangs the lines
 * go on and say where; if it dies they stop, and crash.txt (or its absence) says how.
 */
final class Native {
	/*JNI
	#include <switch.h>
	#include <cstdio>
	#include <cstring>
	#include <fcntl.h>
	#include <unistd.h>
	#include <sys/stat.h>
	#include <stdexcept>

	extern "C" void ws_switch2_crash_paths(const char* crash_file, const char* log_file);
	extern "C" int ws_switch2_crash_environment(char* out, size_t size);

	static FILE* g_log = nullptr;
	static PadState g_pad;
	static u64 g_last_sync = 0;
	static bool g_console = false;

	// Watchdog state. Written by the Java threads, read by the watchdog; a torn read only garbles
	// one diagnostic line.
	static Thread g_watch;
	static bool g_watch_started = false;
	static volatile bool g_watch_run = false;
	static int g_watch_fd = -1;
	static volatile u64 g_lines = 0;
	static char g_mark[96];
	static char g_progress[96];

	static void watchMain(void*) {
		u64 seconds = 0;
		char text[1024];
		while (g_watch_run) {
			svcSleepThread(1000000000LL);
			if (!g_watch_run)
				break;
			seconds++;
			const ClearwingGcStats& s = clearwingGcStats;
			static const char* const kPhase[] = {"idle", "STOPPING", "marking", "sweeping"};
			static const char* const kCollector[] = {"waiting", "finalizing", "freeing"};
			const int phase = s.phase >= 0 && s.phase <= 3 ? s.phase : 0;
			const int collector = s.collectorPhase >= 0 && s.collectorPhase <= 2 ? s.collectorPhase : 0;
			const int used = snprintf(text, sizeof text,
				"[%llu ms] +%llus lines %llu | %s | %s | heap %lld MB, %lld live | gc #%lld %s%s%s, last %lld dead, "
				"stop %lld us (max %lld), mark+sweep %lld us (max %lld) [prep %lld, roots %lld, stacks+young scan %lld, "
				"deep %lld, sweep %lld us; %lld young] | collector %s %lld/%lld, freed %lld | "
				"threads finalized %lld (%d problems)\n",
				(unsigned long long) (armTicksToNs(armGetSystemTick()) / 1000000ULL), (unsigned long long) seconds,
				(unsigned long long) g_lines, g_mark, g_progress, (long long) (getHeapUsage() >> 20),
				(long long) s.liveObjects, (long long) s.collections, kPhase[phase],
				phase == 1 ? (s.blockingIsCollector ? " for the collector" : " for a thread") : "",
				phase == 1 ? (s.blockingThreads > 1 ? " (+more)" : "") : "", (long long) s.lastCollected,
				(long long) s.lastStopUs, (long long) s.maxStopUs, (long long) s.lastMarkSweepUs,
				(long long) s.maxMarkSweepUs, (long long) s.lastPrepUs, (long long) s.lastRootsUs,
				(long long) s.lastStacksUs, (long long) s.lastDeepUs, (long long) s.lastSweepUs, (long long) s.lastYoung,
				kCollector[collector], (long long) s.collectorDone,
				(long long) s.collectorBatch, (long long) s.freedTotal, (long long) s.threadsFinalized,
				s.threadFinalizeProblems);
			if (used > 0 && g_watch_fd >= 0) {
				(void) write(g_watch_fd, text, (size_t) (used < (int) sizeof text ? used : (int) sizeof text - 1));
				fsync(g_watch_fd);
			}
		}
	}
	*/

	private Native() {
	}

	/** Console, pad, crash handler paths, the (appended) log and the watchdog. */
	static native void init(String dir, String logPath, String crashPath, String watchPath); /*
		consoleInit(nullptr);
		g_console = true;
		padConfigureInput(8, HidNpadStyleSet_NpadStandard);
		padInitializeAny(&g_pad);
		mkdir("sdmc:/switch", 0777);
		mkdir(dir, 0777);
		ws_switch2_crash_paths(crashPath, logPath);
		g_log = fopen(logPath, "a");
		g_watch_fd = open(watchPath, O_WRONLY | O_CREAT | O_APPEND, 0644);
		if (g_watch_fd >= 0) {
			const char* header = "==== probe B watchdog: one line a second ====\n";
			(void) write(g_watch_fd, header, strlen(header));
			// Above the main thread's priority (0x2C) so a busy core cannot starve it; it sleeps
			// all but a moment a second. The process's default core.
			g_watch_run = true;
			if (R_SUCCEEDED(threadCreate(&g_watch, watchMain, nullptr, nullptr, 0x8000, 0x2B, -2))
					&& R_SUCCEEDED(threadStart(&g_watch)))
				g_watch_started = true;
			else
				g_watch_run = false;
		}
	*/

	/** One line to the screen and the log; sync=true commits it to the card at once (a mark). */
	static native void line(String text, boolean sync); /*
		const double t = armTicksToNs(armGetSystemTick()) / 1e9;
		g_lines = g_lines + 1;
		if (sync)
			snprintf(g_mark, sizeof g_mark, "%s", text);
		if (g_log) {
			fprintf(g_log, "[%8.3f] %s\n", t, text);
			fflush(g_log);
			const u64 now = armGetSystemTick();
			if (sync || armTicksToNs(now - g_last_sync) >= 1000000000ULL) {
				fsync(fileno(g_log));
				g_last_sync = now;
			}
		}
		if (g_console) {
			printf("%s\n", text);
			consoleUpdate(nullptr);
		}
	*/

	/** Whether a CPU fault can reach the crash handler (debug state, loader). */
	static native String crashEnvironment(); /*
		char text[256];
		if (ws_switch2_crash_environment(text, sizeof text) <= 0)
			snprintf(text, sizeof text, "crash handler: environment unavailable");
		return (jobject) stringFromNative(ctx, text);
	*/

	/** A note for the watchdog only (no log line): how far a long test has got. */
	static native void progress(String text); /*
		snprintf(g_progress, sizeof g_progress, "%s", text);
	*/

	/** True if + is pressed within the given time. */
	static native boolean waitForPlusFor(int millis); /*
		const u64 start = armGetSystemTick();
		while (appletMainLoop() && armTicksToNs(armGetSystemTick() - start) < (u64) millis * 1000000ULL) {
			padUpdate(&g_pad);
			if (padGetButtonsDown(&g_pad) & HidNpadButton_Plus)
				return true;
			consoleUpdate(nullptr);
			svcSleepThread(16000000LL);
		}
		return false;
	*/

	/** Keeps the system responsive between tests; true if + was pressed. */
	static native boolean poll(); /*
		if (!appletMainLoop())
			return true;
		padUpdate(&g_pad);
		return (padGetButtonsDown(&g_pad) & HidNpadButton_Plus) != 0;
	*/

	/**
	 * After the tests: + exits; Y and X end the program on purpose to show that crash.txt is
	 * written: Y by a CPU fault (the exception handler), X by an uncaught C++ exception
	 * (std::terminate, then abort). Neither has been seen to work on a Switch 2 yet.
	 */
	static native void waitForExitOrSelfTest(); /*
		while (appletMainLoop()) {
			padUpdate(&g_pad);
			const u64 down = padGetButtonsDown(&g_pad);
			if (down & HidNpadButton_Plus)
				break;
			if (down & HidNpadButton_Y) {
				printf("self-test: CPU fault (write to address 8)\n");
				consoleUpdate(nullptr);
				if (g_log) { fprintf(g_log, "self-test: CPU fault (write to address 8)\n"); fflush(g_log); fsync(fileno(g_log)); }
				*(volatile int*) 8 = 1;
			}
			if (down & HidNpadButton_X) {
				printf("self-test: uncaught C++ exception\n");
				consoleUpdate(nullptr);
				if (g_log) { fprintf(g_log, "self-test: uncaught C++ exception\n"); fflush(g_log); fsync(fileno(g_log)); }
				throw std::runtime_error("probe B self-test: an uncaught C++ exception");
			}
			consoleUpdate(nullptr);
		}
	*/

	/** Which core and priority the calling thread runs on (the runtime places its threads). */
	static native String where(); /*
		s32 priority = -1;
		svcGetThreadPriority(&priority, CUR_THREAD_HANDLE);
		char text[48];
		snprintf(text, sizeof text, "core %d prio 0x%x", (int) svcGetCurrentProcessorNumber(), (unsigned) priority);
		return (jobject) stringFromNative(ctx, text);
	*/

	static native void finish(); /*
		if (g_watch_started) {
			g_watch_run = false;
			threadWaitForExit(&g_watch);
			threadClose(&g_watch);
			g_watch_started = false;
		}
		if (g_watch_fd >= 0) {
			fsync(g_watch_fd);
			close(g_watch_fd);
			g_watch_fd = -1;
		}
		if (g_log) {
			fsync(fileno(g_log));
			fclose(g_log);
			g_log = nullptr;
		}
		if (g_console) {
			consoleExit(nullptr);
			g_console = false;
		}
	*/
}
