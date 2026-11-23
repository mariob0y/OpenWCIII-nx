package wsnx.warsmash;

import com.etheller.warsmash.util.ParallelJobs;

/**
 * The console's CPU cores for ParallelJobs (backend/native/nx_threads.cpp does the work): how many
 * cores are free for worker threads, and placing each worker on its core as it starts. The GL
 * driver's threads get a core of their own there (the threadCreate wrap), so workers never sit
 * on the game thread's core nor on the driver's.
 */
final class NxCores {
	private NxCores() {
	}

	/*JNI
	extern "C" int nxWorkerCoreCount(void);
	extern "C" void nxPlaceWorker(int worker);
	extern "C" void nxLogCoreLayout(void);
	*/

	/**
	 * Starts ParallelJobs with {@code requested} workers, or one per free core when negative
	 * (warsmash.ini [Emulator] Workers, absent = automatic, 0 = everything on the game thread).
	 */
	static void install(final int requested) {
		logCoreLayout();
		final int workers = requested >= 0 ? requested : workerCoreCount();
		ParallelJobs.configure(workers, new ParallelJobs.ThreadPlacer() {
			@Override
			public void place(final int worker) {
				placeWorker(worker);
			}
		});
		System.out.println("threads: " + workers + " worker(s) for parallel jobs"
				+ (requested >= 0 ? " (warsmash.ini Workers=" + requested + ")" : " (one per free core)"));
	}

	private static native int workerCoreCount(); /*
		return nxWorkerCoreCount();
	*/

	private static native void placeWorker(int worker); /*
		nxPlaceWorker(worker);
	*/

	private static native void logCoreLayout(); /*
		nxLogCoreLayout();
	*/
}
