package com.etheller.warsmash.viewer5;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Temporaries for evaluating a model's pose (keyframe values, node transforms). They used to be
 * static fields of MdxComplexInstance and SkeletalNode, shared by every instance; poses are now
 * evaluated on several threads at once (WorldScene + ParallelJobs), so each thread has its own.
 * {@link #MAIN} is the game thread's, used by every caller outside the parallel pass.
 */
public final class PoseScratch {
	public static final PoseScratch MAIN = new PoseScratch();

	private static PoseScratch[] perThread = { MAIN };

	// MdxComplexInstance: keyframe values.
	public final float[] visibility = new float[1];
	public final float[] translation = new float[3];
	public final float[] rotation = new float[4];
	public final float[] scale = new float[3];
	public final float[] color = new float[3];
	public final float[] alpha = new float[1];
	public final long[] textureId = new long[1];

	// SkeletalNode.recalculateTransformation.
	public final Vector3 cameraRay = new Vector3();
	public final Vector3 billboardAxis = new Vector3();
	public final Quaternion rotationA = new Quaternion();
	public final Quaternion rotationB = new Quaternion();
	public final Vector3 scaling = new Vector3();
	public final Vector3 blendLocation = new Vector3();
	public final Vector3 blend = new Vector3();
	public final Vector3 blendScale = new Vector3();

	// transform(): libGDX's Quaternion.transform(Vector3) uses two static Quaternions.
	private final Quaternion transformA = new Quaternion();
	private final Quaternion transformB = new Quaternion();

	/**
	 * The scratch of thread slot {@code slot} (0 = the game thread, 1.. = ParallelJobs workers).
	 * Call {@link #ensureSlots} on the game thread before handing slots to workers.
	 */
	public static PoseScratch forSlot(final int slot) {
		return perThread[slot];
	}

	/** Makes sure slots 0..count-1 exist. Game thread only, outside a parallel pass. */
	public static void ensureSlots(final int count) {
		if (perThread.length < count) {
			final PoseScratch[] grown = new PoseScratch[count];
			System.arraycopy(perThread, 0, grown, 0, perThread.length);
			for (int i = perThread.length; i < count; i++) {
				grown[i] = new PoseScratch();
			}
			perThread = grown;
		}
	}

	/**
	 * Rotates {@code v} by {@code q}, exactly as libGDX's {@code q.transform(v)} does (the same
	 * operations in the same order), but with this thread's temporaries instead of its statics.
	 */
	public Vector3 transform(final Quaternion q, final Vector3 v) {
		this.transformB.set(q);
		this.transformB.conjugate();
		this.transformB.mulLeft(this.transformA.set(v.x, v.y, v.z, 0)).mulLeft(q);

		v.x = this.transformB.x;
		v.y = this.transformB.y;
		v.z = this.transformB.z;
		return v;
	}
}
