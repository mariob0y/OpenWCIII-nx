package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.OptionsSettingsStore;

/**
 * Visual occlusion only: never changes pathing, fog, selection, or authored vertex colors.
 *
 * warsmash-nx: only trees that are occluded or still fading back are touched every frame (active),
 * and a sample tests only the trees the scene drew this frame. Before, every frame walked every
 * widget of the map (3900 on OrcX01) to fade trees that were fully opaque, and each sample tested
 * every tree for visibility again.
 */
public final class TreeOcclusion {
	private final List<RenderUnit> visibleUnits = new ArrayList<>();
	private final ArrayList<RenderDestructable> active = new ArrayList<>();
	private final BoundingBox bounds = new BoundingBox();
	private final Vector3 target = new Vector3();
	private final Vector3 hit = new Vector3();
	private final Ray ray = new Ray();
	private float elapsed = 1;

	public static boolean blocks(final Vector3 camera, final Vector3 target, final BoundingBox bounds,
			final Ray ray, final Vector3 hit) {
		ray.set(camera, hit.set(target).sub(camera).nor());
		return Intersector.intersectRayBounds(ray, bounds, hit)
				&& camera.dst2(hit) < camera.dst2(target) - 1;
	}

	public static float fade(final float current, final boolean occluded, final float dt) {
		final float target = occluded ? 0.35f : 1;
		final float step = Math.max(0, dt) * 4;
		return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
	}

	public void update(final War3MapViewer viewer, final float dt) {
		final boolean enabled = viewer.isOcclusionEnabled() && OptionsSettingsStore.get().isOcclusion();
		this.elapsed += Math.max(0, dt);
		final boolean sample = this.elapsed >= 1f / 15f;
		if (sample) {
			this.elapsed = 0;
			this.visibleUnits.clear();
			if (enabled) {
				for (final RenderUnit unit : viewer.units) {
					if (!unit.instance.hidden() && !unit.getSimulationUnit().isDead()
							&& unit.getSimulationUnit().isVisible(viewer.simulation, viewer.getLocalPlayerIndex())
							&& unit.instance.isVisible(viewer.worldScene.camera)) this.visibleUnits.add(unit);
				}
			}
		}
		final ArrayList<RenderDestructable> active = this.active;
		if (sample) {
			for (int i = 0, l = active.size(); i < l; i++) {
				active.get(i).getInstance().occluded = false;
			}
			if (enabled && !this.visibleUnits.isEmpty()) {
				final Scene scene = viewer.worldScene;
				sampleDrawnTrees(viewer, scene.instances, scene.currentInstance);
				sampleDrawnTrees(viewer, scene.batchedInstances, scene.currentBatchedInstance);
			}
		}
		for (int i = active.size() - 1; i >= 0; i--) {
			final RenderDestructable tree = active.get(i);
			final MdxComplexInstance instance = tree.getInstance();
			if (!enabled || tree.getSimulationDestructable().isDead()) instance.occluded = false;
			instance.occlusionAlpha = fade(instance.occlusionAlpha, instance.occluded, dt);
			if (!instance.occluded && (instance.occlusionAlpha >= 1)) {
				// opaque again: off the list (swap with the last)
				final int last = active.size() - 1;
				active.set(i, active.get(last));
				active.remove(last);
			}
		}
	}

	private void sampleDrawnTrees(final War3MapViewer viewer, final List<ModelInstance> drawn, final int count) {
		for (int i = 0, l = Math.min(count, drawn.size()); i < l; i++) {
			final Object owner = drawn.get(i).owner;
			if (owner instanceof RenderDestructable) {
				sampleTree(viewer, (RenderDestructable) owner);
			}
		}
	}

	private void sampleTree(final War3MapViewer viewer, final RenderDestructable tree) {
		final MdxComplexInstance instance = tree.getInstance();
		if (instance.occluded || tree.getSimulationDestructable().isDead()) {
			return; // already tested in this sample (an instance can be drawn once only, but be safe)
		}
		final float height = tree.getSimulationDestructable().getOccluderHeight();
		if (height > 0 && !instance.hidden() && instance.model.bounds.getBoundingBox() != null) {
			this.bounds.set(instance.model.bounds.getBoundingBox());
			this.bounds.max.z = Math.max(this.bounds.min.z, height);
			this.bounds.set(this.bounds.min, this.bounds.max).mul(instance.worldMatrix);
			for (final RenderUnit unit : this.visibleUnits) {
				this.target.set(unit.getX(), unit.getY(), unit.getZ()
						+ Math.max(16, unit.instance.model.bounds.z * Math.abs(unit.instance.localScale.z)));
				if (blocks(viewer.worldScene.camera.location, this.target, this.bounds, this.ray, this.hit)) {
					instance.occluded = true;
					// opaque trees are on the list only if a frame without time (paused) kept them at 1
					if ((instance.occlusionAlpha >= 1) && !this.active.contains(tree)) {
						this.active.add(tree);
					}
					break;
				}
			}
		}
	}
}
