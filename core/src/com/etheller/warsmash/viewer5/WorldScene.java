package com.etheller.warsmash.viewer5;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.ParallelJobs;

/**
 * A scene.
 *
 * Every scene has its own list of model instances, and its own camera and
 * viewport.
 *
 * In addition, in Ghostwolf's original code every scene may have its own
 * AudioContext if enableAudio() is called. If audo is enabled, the
 * AudioContext's listener's location will be updated automatically. Note that
 * due to browser policies, this may be done only after user interaction with
 * the web page.
 *
 * In "Warsmash", we are starting from an attempt to replicate Ghostwolf, but
 * audio is always on in LibGDX generally. So we will probably simplify or skip
 * over those behaviors other than a boolean on/off toggle for audio.
 */
public class WorldScene extends Scene {

	public Grid grid;
	private final InstanceProcessor instanceProcessor = new InstanceProcessor();

	/**
	 * The parallel pose pass. While the instances are walked, each one that can (an
	 * MdxComplexInstance attached to nothing) advances its animation clock and leaves its pose
	 * here; the poses are then evaluated on ParallelJobs' workers and the game thread together,
	 * and finished (node objects, attached children, bone textures) on the game thread in walk
	 * order. Instances attached to a host whose pose is pending wait in postponedInstances and are
	 * walked after it, so they read the host's new bones, as they did when the host updated first.
	 * Turn off with PARALLEL_POSES = false or zero workers.
	 */
	public static boolean PARALLEL_POSES = true;

	/**
	 * warsmash-nx: skip the grid cells whose whole box is off screen (GridCell.isVisible) instead of
	 * testing every instance of the map every frame. PC check: NX_CULL_VERIFY=1 tests the instances
	 * of the skipped cells too and logs any that would have been drawn.
	 */
	public static boolean GRID_CULLING = true;

	/**
	 * warsmash-nx: walked every frame, whatever the culling says: the sky dome. It follows the camera,
	 * so its grid cell (the ground under the camera) is often just off screen; a culled sky was not
	 * updated and its light left the scene for that frame, and units blinked darker in cinematics.
	 */
	public ModelInstance unculledInstance;
	private static final boolean CULL_VERIFY = System.getenv("NX_CULL_VERIFY") != null;
	private final ArrayList<ModelInstance> cellScratch = new ArrayList<>();
	private final ArrayList<ModelInstance> cullSuspects = new ArrayList<>();
	private int cullVerifyFrames;
	private long cullVerifyCulledCells;
	private long cullVerifyCells;
	private long cullVerifyMisses;
	private final ArrayList<ModelInstance> deferredPoses = new ArrayList<>();
	private final ArrayList<ModelInstance> postponedInstances = new ArrayList<>();
	private boolean collectingPoses;
	private final ParallelJobs.Job poseJob = new ParallelJobs.Job() {
		@Override
		public void run(final int index, final int worker) {
			WorldScene.this.deferredPoses.get(index).computeDeferredPose(PoseScratch.forSlot(worker));
		}
	};

	public WorldScene(final ModelViewer viewer, final SceneLightManager lightManager) {
		super(viewer, lightManager);
		this.grid = new Grid(-100000, -100000, 200000, 200000, 200000, 200000);
	}

	@Override
	public void instanceMoved(final ModelInstance instance, final float x, final float y) {
		this.grid.moved(instance, x, y);
	}

	@Override
	protected void innerRemove(final ModelInstance instance) {
		this.grid.remove(instance);
	}

	@Override
	public void clear() {
		// First remove references to this scene stored in the instances.
		for (final GridCell cell : this.grid.cells) {
			for (final ModelInstance instance : cell.instances) {
				instance.scene = null;
			}
		}

		// Then remove references to the instances.
		this.grid.clear();
	}

	@Override
	protected void innerUpdate(final float dt, final int frame) {
		this.visibleCells = 0;
		this.visibleInstances = 0;

		// Update and collect all of the visible instances.
		instanceProcessor.reset(dt, frame);
		this.collectingPoses = PARALLEL_POSES && (ParallelJobs.workerCount() > 0);
		try {
			final GridCell[] cells = this.grid.cells;
			final ArrayList<ModelInstance> scratch = this.cellScratch;
			for (int c = 0; c < cells.length; c++) {
				final GridCell cell = cells[c];
				final List<ModelInstance> cellInstances = cell.instances;
				if (cellInstances.isEmpty()) {
					continue;
				}
				if (!GRID_CULLING || cell.isVisible(this.camera)) {
					this.visibleCells += 1;

					// a copy: an instance that moves while it updates may change this cell's list
					scratch.clear();
					for (int i = 0, l = cellInstances.size(); i < l; i++) {
						scratch.add(cellInstances.get(i));
					}
					for (int i = 0, l = scratch.size(); i < l; i++) {
						instanceProcessor.call(scratch.get(i));
					}
					scratch.clear();
				}
				else if (CULL_VERIFY) {
					this.cullVerifyCulledCells++;
					this.cullSuspects.addAll(cellInstances);
				}
				if (CULL_VERIFY) {
					this.cullVerifyCells++;
				}
			}
			final ModelInstance unculled = this.unculledInstance;
			if ((unculled != null) && (unculled.scene == this)) {
				instanceProcessor.accept(unculled);
			}
		}
		finally {
			this.collectingPoses = false;
		}
		if (CULL_VERIFY) {
			verifyCulling(frame);
		}

		final ArrayList<ModelInstance> deferred = this.deferredPoses;
		if (!deferred.isEmpty()) {
			try {
				PoseScratch.ensureSlots(ParallelJobs.threadSlots());
				if (PoseVerifier.ENABLED) {
					PoseVerifier.runVerified(deferred, this.poseJob);
				}
				else {
					ParallelJobs.parallelFor(deferred.size(), this.poseJob);
				}
				for (int i = 0, l = deferred.size(); i < l; i++) {
					deferred.get(i).finishDeferredPose();
				}
			}
			finally {
				deferred.clear();
			}
		}

		final ArrayList<ModelInstance> postponed = this.postponedInstances;
		if (!postponed.isEmpty()) {
			try {
				// Not collecting any more, so none of these is postponed again.
				for (int i = 0; i < postponed.size(); i++) {
					instanceProcessor.call(postponed.get(i));
				}
			}
			finally {
				postponed.clear();
			}
		}
	}

	/**
	 * NX_CULL_VERIFY: an instance of a skipped cell that no other cell walked, and that isVisible
	 * would have let through, was wrongly culled.
	 */
	private void verifyCulling(final int frame) {
		final ArrayList<ModelInstance> suspects = this.cullSuspects;
		for (int i = 0, l = suspects.size(); i < l; i++) {
			final ModelInstance instance = suspects.get(i);
			if (instance.rendered && (instance.cullFrame < frame) && instance.isVisible(this.camera)) {
				this.cullVerifyMisses++;
				if (this.cullVerifyMisses <= 20) {
					System.out.println("NX_CULL_VERIFY: wrongly culled " + instance.model + " at "
							+ instance.worldLocation + " r " + instance.model.bounds.r);
				}
			}
		}
		suspects.clear();
		if ((++this.cullVerifyFrames % 300) == 0) {
			System.out.println("NX_CULL_VERIFY: " + this.cullVerifyFrames + " frames, cells skipped "
					+ this.cullVerifyCulledCells + " of " + this.cullVerifyCells + ", wrongly culled "
					+ this.cullVerifyMisses);
		}
	}

	/** Whether instances may leave their poses for the parallel pass right now (game thread). */
	public boolean isCollectingPoses() {
		return this.collectingPoses;
	}

	/** Takes an instance's pose for the parallel pass of this frame (game thread). */
	public void deferPose(final ModelInstance instance) {
		instance.poseDeferredFrame = this.viewer.frame;
		this.deferredPoses.add(instance);
	}

	/** The instance at the top of a node's parent chain, if the top is an instance. */
	private static ModelInstance hostOf(final GenericNode node) {
		GenericNode root = node;
		while (root.parent != null) {
			root = root.parent;
		}
		return root instanceof ModelInstance ? (ModelInstance) root : null;
	}

	private final class InstanceProcessor implements ModelInstanceCallback {
		private float dt;
		private int frame;

		public InstanceProcessor reset(final float dt, final int frame) {
			this.dt = dt;
			this.frame = frame;
			return this;
		}

		@Override
		public void call(final ModelInstance instance) {
//			final ModelInstance instance = cell.instances.get(i);
			if (WorldScene.this.collectingPoses && (instance.parent != null)) {
				final ModelInstance host = hostOf(instance.parent);
				if ((host != null) && (host.poseDeferredFrame == this.frame)) {
					// its host's bones are not evaluated yet: walk it after the pass
					WorldScene.this.postponedInstances.add(instance);
					return;
				}
			}
			// the cheap tests first: an instance over several cells is met once per cell
			if (instance.rendered && (instance.cullFrame < frame) && instance.isVisible(camera)) {
				accept(instance);
			}
		}

		/** Updates and collects an instance without the view test (call() makes that test first). */
		public void accept(final ModelInstance instance) {
			if (instance.rendered && (instance.cullFrame < frame)) {
				instance.cullFrame = frame;

				if (instance.updateFrame < frame) {
					instance.update(dt, WorldScene.this);
					if (!instance.rendered) {
						// it became hidden while it updated
						return;
					}
				}

				if (instance.isBatched()) {
					if (currentBatchedInstance < batchedInstances.size()) {
						batchedInstances.set(currentBatchedInstance++, instance);
					}
					else {
						batchedInstances.add(instance);
						currentBatchedInstance++;
					}
				}
				else {
					if (currentInstance < instances.size()) {
						instances.set(currentInstance++, instance);
					}
					else {
						instances.add(instance);
						currentInstance++;
					}
				}

				visibleInstances += 1;
				for (int i = 0, l = instance.childrenInstances.size(); i < l; i++) {
					call(instance.childrenInstances.get(i));
				}
			}
		}
	}
}
