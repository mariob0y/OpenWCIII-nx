package wsnx.warsmash;

/**
 * A sampling profiler for the console, where no other profiler reaches: a daemon thread reads the
 * game thread's Java stack (Clearwing keeps it as an array of static FrameInfo records) 200 times a
 * second and every 30 s logs the methods most often on top of it ("self") and anywhere in it
 * ("total"), as a share of the samples. Read without stopping the game thread, like
 * NxLoadingScreen.dumpStack: a slot can be stale, and every pointer is checked with the kernel
 * before it is read. Costs the game thread nothing but the cache misses.
 */
final class NxProfiler {
	private NxProfiler() {
	}

	private static final long SAMPLE_MS = 5;
	private static final long REPORT_NS = 30_000_000_000L;

	static void start(final Thread target) {
		final Thread sampler = new Thread(() -> run(target), "nx-profiler");
		sampler.setDaemon(true);
		sampler.start();
	}

	private static void run(final Thread target) {
		long nextReport = System.nanoTime() + REPORT_NS;
		while (true) {
			try {
				Thread.sleep(SAMPLE_MS);
			}
			catch (final InterruptedException e) {
				return;
			}
			sample(target);
			final long now = System.nanoTime();
			if (now >= nextReport) {
				report();
				nextReport = now + REPORT_NS;
			}
		}
	}

	/*JNI
	#include "java/lang/Thread.h"
	#include <switch.h>
	#include <unordered_map>
	#include <vector>
	#include <algorithm>

	namespace {
	struct NxProfRange {
		uintptr_t start, end;
	};

	struct NxProfState {
		std::unordered_map<const char *, unsigned> self, total;
		unsigned samples = 0;
		unsigned unreadable = 0;
		NxProfRange ranges[16];
		int rangeCount = 0;
	};

	NxProfState nxProf;

	// Inside the program's own code/data mappings (static FrameInfo records and their names).
	// Known mappings are remembered, so the kernel is asked only about a new one.
	bool nxProfReadable(const void *p) {
		const uintptr_t a = (uintptr_t) p;
		if (!a)
			return false;
		for (int i = 0; i < nxProf.rangeCount; i++)
			if (a >= nxProf.ranges[i].start && a < nxProf.ranges[i].end)
				return true;
		MemoryInfo info;
		u32 pageInfo;
		if (R_FAILED(svcQueryMemory(&info, &pageInfo, (u64) a)) || !(info.perm & Perm_R))
			return false;
		const u32 type = info.type & MemState_Type;
		if (type != MemType_CodeStatic && type != MemType_CodeMutable && type != MemType_ModuleCodeStatic
				&& type != MemType_ModuleCodeMutable)
			return false;
		if (nxProf.rangeCount < 16)
			nxProf.ranges[nxProf.rangeCount++] = {(uintptr_t) info.addr, (uintptr_t) (info.addr + info.size)};
		return true;
	}

	void nxProfPrint(const char *title, const std::unordered_map<const char *, unsigned> &counts, size_t top) {
		std::vector<std::pair<const char *, unsigned>> sorted(counts.begin(), counts.end());
		std::sort(sorted.begin(), sorted.end(), [](const auto &x, const auto &y) { return x.second > y.second; });
		printf("profile: %s\n", title);
		for (size_t i = 0; i < sorted.size() && i < top; i++)
			printf("profile: %5.1f%%  %s\n", 100.0 * sorted[i].second / nxProf.samples, sorted[i].first);
	}
	}
	*/

	private static native void sample(Thread thread); /*
		auto target = (jcontext) ((java_lang_Thread *) thread)->F_nativeContext;
		if (!target)
			return;
		int depth = target->stackDepth;
		if (depth <= 0)
			return;
		if (depth > MAX_STACK_DEPTH)
			depth = MAX_STACK_DEPTH;
		const char *seen[96];
		int seenCount = 0;
		bool innermost = true;
		for (int i = depth - 1; i >= 0 && seenCount < 96; i--) {
			const FrameInfo *info = target->frames[i].info;
			const char *method = nxProfReadable(info) ? info->method : nullptr;
			if (!nxProfReadable(method)) {
				if (innermost)
					nxProf.unreadable++;
				innermost = false;
				continue;
			}
			if (innermost)
				nxProf.self[method]++;
			innermost = false;
			bool dup = false;
			for (int j = 0; j < seenCount && !dup; j++)
				dup = seen[j] == method;
			if (!dup) {
				seen[seenCount++] = method;
				nxProf.total[method]++;
			}
		}
		nxProf.samples++;
	*/

	private static native void report(); /*
		if (nxProf.samples == 0)
			return;
		printf("profile: %u samples of the game thread in the last 30 s (%u with an unreadable top frame)\n",
				nxProf.samples, nxProf.unreadable);
		nxProfPrint("self (on top of the stack)", nxProf.self, 30);
		nxProfPrint("total (anywhere in the stack)", nxProf.total, 45);
		fflush(stdout);
		nxProf.self.clear();
		nxProf.total.clear();
		nxProf.samples = 0;
		nxProf.unreadable = 0;
	*/
}
