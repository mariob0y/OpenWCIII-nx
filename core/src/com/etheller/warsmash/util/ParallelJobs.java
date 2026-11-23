package com.etheller.warsmash.util;

/**
 * A small fork-join pool: the game thread hands out the items of one job, takes items itself while
 * the workers take theirs, and returns when every item is done. One job at a time, started only
 * from the game thread; a job started from inside a job (or with no workers) runs inline.
 *
 * Written for the Switch 2 port (Clearwing VM, where Runtime.availableProcessors() is 1 and
 * java.util.concurrent's atomics are synchronized methods anyway), so it uses only monitors:
 * idle workers block in wait() (a blocked thread does not hold up the garbage collector) and
 * never spin, because on Horizon a spinning thread starves same-core threads of lower priority.
 *
 * Items are claimed in small chunks under the pool's lock, so the lock is taken once per chunk,
 * not once per item. Each worker has an index (1..workers; the game thread is 0) that a job can
 * use to pick its own scratch memory.
 */
public final class ParallelJobs {
	/** One parallel loop body: run item {@code index} on the thread with index {@code worker}. */
	public interface Job {
		void run(int index, int worker);
	}

	/** Places a worker thread (core, priority) as it starts; the console installs one. */
	public interface ThreadPlacer {
		void place(int worker);
	}

	private static final int CHUNK = 4;

	private static final Object lock = new Object();
	private static Thread[] threads = new Thread[0];
	private static int workers = -1; // -1: not started yet
	private static int requested = -1; // -1: automatic
	private static ThreadPlacer placer;

	// The job being run; guarded by lock.
	private static Job job;
	private static int count;
	private static int next;
	private static int generation;
	private static int checkedIn; // workers done with the current generation
	private static Throwable failure;
	private static boolean running;

	private ParallelJobs() {
	}

	/**
	 * Sets the number of worker threads (0 = everything on the game thread, -1 = automatic) and how
	 * they place themselves. Takes effect at the first job; later calls are ignored.
	 */
	public static void configure(final int workerCount, final ThreadPlacer threadPlacer) {
		synchronized (lock) {
			if (workers < 0) {
				requested = workerCount;
				placer = threadPlacer;
			}
		}
	}

	/** The number of worker threads besides the game thread (starts them on first use). */
	public static int workerCount() {
		synchronized (lock) {
			if (workers < 0) {
				start();
			}
			return workers;
		}
	}

	/** Scratch slots a job needs: one per worker plus the game thread's. */
	public static int threadSlots() {
		return workerCount() + 1;
	}

	private static void start() {
		int n = requested;
		if (n < 0) {
			n = Math.min(3, Runtime.getRuntime().availableProcessors() - 1);
		}
		n = Math.max(0, Math.min(7, n));
		threads = new Thread[n];
		for (int i = 0; i < n; i++) {
			final int worker = i + 1;
			final Thread thread = new Thread(() -> workerLoop(worker), "warsmash-worker-" + worker);
			thread.setDaemon(true);
			threads[i] = thread;
		}
		workers = n;
		for (final Thread thread : threads) {
			thread.start();
		}
		System.out.println("ParallelJobs: " + n + " worker thread(s)");
	}

	/**
	 * Runs {@code job.run(i, worker)} for every i in [0, n), spread over the workers and the calling
	 * thread, and returns when all are done. The first exception or error of any item is rethrown
	 * here after the others have finished.
	 */
	public static void parallelFor(final int n, final Job body) {
		if (n <= 0) {
			return;
		}
		final boolean inline;
		synchronized (lock) {
			if (workers < 0) {
				start();
			}
			inline = (workers == 0) || running || (n == 1);
			if (!inline) {
				running = true;
				job = body;
				count = n;
				next = 0;
				checkedIn = 0;
				failure = null;
				generation++;
				lock.notifyAll();
			}
		}
		if (inline) {
			for (int i = 0; i < n; i++) {
				body.run(i, 0);
			}
			return;
		}

		Throwable mine = null;
		try {
			runChunks(body, 0);
		}
		catch (final Throwable t) {
			mine = t;
		}

		final Throwable theirs;
		synchronized (lock) {
			while (checkedIn < workers) {
				try {
					lock.wait();
				}
				catch (final InterruptedException e) {
					// the game thread is never interrupted on purpose; keep waiting for the workers
				}
			}
			theirs = failure;
			job = null;
			running = false;
		}
		rethrow(mine != null ? mine : theirs);
	}

	private static void rethrow(final Throwable t) {
		if (t == null) {
			return;
		}
		if (t instanceof RuntimeException) {
			throw (RuntimeException) t;
		}
		if (t instanceof Error) {
			throw (Error) t;
		}
		throw new RuntimeException(t);
	}

	/** Claims and runs chunks of the current job until none are left. */
	private static void runChunks(final Job body, final int worker) {
		while (true) {
			final int start;
			final int end;
			synchronized (lock) {
				if ((next >= count) || (failure != null)) {
					return;
				}
				start = next;
				end = Math.min(count, start + CHUNK);
				next = end;
			}
			for (int i = start; i < end; i++) {
				body.run(i, worker);
			}
		}
	}

	private static void workerLoop(final int worker) {
		final ThreadPlacer threadPlacer;
		synchronized (lock) {
			threadPlacer = placer;
		}
		if (threadPlacer != null) {
			threadPlacer.place(worker);
		}
		int seen = 0;
		while (true) {
			final Job body;
			synchronized (lock) {
				while (generation == seen) {
					try {
						lock.wait();
					}
					catch (final InterruptedException e) {
						// nothing interrupts the workers; wait on
					}
				}
				seen = generation;
				body = job;
			}
			try {
				if (body != null) {
					runChunks(body, worker);
				}
			}
			catch (final Throwable t) {
				synchronized (lock) {
					if (failure == null) {
						failure = t;
					}
				}
			}
			synchronized (lock) {
				checkedIn++;
				if (checkedIn >= workers) {
					lock.notifyAll();
				}
			}
		}
	}
}
