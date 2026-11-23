package com.etheller.warsmash.viewer5;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.JavaCompat;
import com.etheller.warsmash.util.ParallelJobs;

/**
 * A PC check of WorldScene's parallel pose pass (run with -Dwarsmash.verifyPoses=true): every
 * frame, each deferred pose is evaluated on the workers as usual, then put back to its state
 * before the pass and evaluated again on the game thread alone, and the two results are compared
 * bit for bit. Any state shared between instances (a static temporary left behind) shows up as a
 * mismatch. Slow (copies every pose twice a frame); never on in a normal run.
 */
final class PoseVerifier {
	static final boolean ENABLED = JavaCompat.getBoolean("warsmash.verifyPoses");

	private static long frames;
	private static long poses;
	private static long mismatches;
	private static long lastReport;

	private PoseVerifier() {
	}

	/** Runs the pass as WorldScene would, then the serial re-run and the comparison. */
	static void runVerified(final List<ModelInstance> deferred, final ParallelJobs.Job job) {
		final int n = deferred.size();
		final List<int[]> before = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			before.add(deferred.get(i).poseState(null, false));
		}

		ParallelJobs.parallelFor(n, job);

		for (int i = 0; i < n; i++) {
			final ModelInstance instance = deferred.get(i);
			final int[] start = before.get(i);
			if (start == null) {
				continue;
			}
			final int[] parallel = instance.poseState(null, false);
			instance.poseState(start, true);
			instance.computeDeferredPose(PoseScratch.MAIN);
			final int[] serial = instance.poseState(null, false);
			poses++;
			if (!java.util.Arrays.equals(parallel, serial)) {
				mismatches++;
				if (mismatches <= 20) {
					int first = 0;
					while ((first < parallel.length) && (first < serial.length) && (parallel[first] == serial[first])) {
						first++;
					}
					System.err.println("PoseVerifier: MISMATCH frame " + frames + " instance " + i + "/" + n + " ("
							+ instance.model + "), first differing word " + first + " of " + serial.length);
				}
			}
		}
		frames++;
		if ((frames - lastReport) >= 300) {
			lastReport = frames;
			System.out.println("PoseVerifier: " + frames + " frames, " + poses + " poses compared, " + mismatches
					+ " mismatches, " + ParallelJobs.workerCount() + " workers");
		}
	}
}
