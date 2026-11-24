package com.etheller.warsmash.viewer5;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.Vector4;

import com.etheller.warsmash.util.RenderMathUtils;

public class GridCell {
	public final float left;
	public final float right;
	public final float bottom;
	public final float top;
	public int plane;
	final List<ModelInstance> instances;
	public final boolean visible;
	/**
	 * warsmash-nx: the heights the bounding spheres of the instances that entered this cell have
	 * reached (Grid.moved). They only grow, so the box (left..right, bottom..top, zMin..zMax)
	 * always holds every sphere ModelInstance.isVisible tests.
	 */
	float zMin = Float.POSITIVE_INFINITY;
	float zMax = Float.NEGATIVE_INFINITY;

	public GridCell(final float left, final float right, final float bottom, final float top) {
		this.left = left;
		this.right = right;
		this.bottom = bottom;
		this.top = top;
		this.plane = -1;
		this.instances = new ArrayList<ModelInstance>();
		this.visible = false;
	}

	public void add(final ModelInstance instance) {
		this.instances.add(instance);
	}

	public void remove(final ModelInstance instance) {
		this.instances.remove(instance);
	}

	public void clear() {
		this.instances.clear();
	}

	void includeHeights(final float low, final float high) {
		if (low < this.zMin) {
			this.zMin = low;
		}
		if (high > this.zMax) {
			this.zMax = high;
		}
	}

	/**
	 * warsmash-nx: false only when the cell's whole box is behind one frustum plane, so no instance
	 * in it can pass ModelInstance.isVisible. The old test (RenderMathUtils.testCell) checked the
	 * cell's corners at height 0 only, which is wrong for anything above the ground; that is why
	 * WorldScene had switched it off and tested every instance of the map every frame.
	 */
	public boolean isVisible(final Camera camera) {
		if (!(this.zMin <= this.zMax)) {
			return true; // no heights yet: do not guess
		}
		final Vector4[] planes = camera.planes;
		final int first = this.plane == -1 ? 0 : this.plane;
		for (int i = 0; i < 6; i++) {
			final int index = (first + i) % 6;
			final Vector4 p = planes[index];
			// the box corner furthest along the plane's normal
			final float d = (p.x * (p.x > 0 ? this.right : this.left)) + (p.y * (p.y > 0 ? this.top : this.bottom))
					+ (p.z * (p.z > 0 ? this.zMax : this.zMin)) + p.w;
			if (d < 0) {
				this.plane = index;
				return false;
			}
		}
		this.plane = -1;
		return true;
	}
}
