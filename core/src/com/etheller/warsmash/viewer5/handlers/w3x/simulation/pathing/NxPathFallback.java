package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;

/**
 * warsmash-nx: the node closest to the goal that a pathfinding job reached, so that a search that fails
 * (goal unreachable, or the iteration cap on a long trip) still walks the unit as close as it can, as
 * Warcraft III does, instead of leaving it standing. Kept per job (one job per player processor runs at
 * a time, over several frames) outside CPathfindingProcessor so that its generated header is unchanged.
 */
final class NxPathFallback {
	private static final Map<Object, Object> BEST = new IdentityHashMap<>();
	private static final Map<Object, float[]> BEST_H = new IdentityHashMap<>();
	private static final Map<Object, BitSet> CLOSED = new IdentityHashMap<>();
	private static final Map<Object, BitSet[]> STATIC = new IdentityHashMap<>();

	/**
	 * Static (terrain/building) pathability of a search-graph point, computed once per job: the search
	 * asks it for the same points up to three times per neighbour.
	 */
	static boolean staticPathable(final Object job, final int index, final PathingGrid grid, final float x,
			final float y, final PathingGrid.MovementType movementType, final float collisionSize) {
		BitSet[] memo = STATIC.get(job);
		if (memo == null) {
			memo = new BitSet[] { new BitSet(), new BitSet() };
			STATIC.put(job, memo);
		}
		if (memo[0].get(index)) {
			return memo[1].get(index);
		}
		final boolean pathable = grid.isPathable(x, y, movementType, collisionSize);
		memo[0].set(index);
		if (pathable) {
			memo[1].set(index);
		}
		return pathable;
	}

	/** The job's expanded cells (a node is expanded once: no linear PriorityQueue.contains scans). */
	static BitSet closed(final Object job) {
		BitSet closed = CLOSED.get(job);
		if (closed == null) {
			closed = new BitSet();
			CLOSED.put(job, closed);
		}
		return closed;
	}

	private NxPathFallback() {
	}

	static void offer(final Object job, final Object node, final float h) {
		final float[] best = BEST_H.get(job);
		if (best == null) {
			BEST_H.put(job, new float[] { h });
			BEST.put(job, node);
		}
		else if (h < best[0]) {
			best[0] = h;
			BEST.put(job, node);
		}
	}

	/** The best node of a finished job if it gets meaningfully closer than the start; forgets the job. */
	static Object take(final Object job, final float startH) {
		final Object node = BEST.remove(job);
		final float[] best = BEST_H.remove(job);
		CLOSED.remove(job);
		STATIC.remove(job);
		if ((node == null) || (best == null) || (best[0] > (startH - 96))) {
			return null;
		}
		return node;
	}

	static void forget(final Object job) {
		BEST.remove(job);
		BEST_H.remove(job);
		CLOSED.remove(job);
		STATIC.remove(job);
	}
}
