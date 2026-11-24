package com.etheller.warsmash.viewer5;

import com.badlogic.gdx.math.Vector3;

public class Grid {
	private final float x;
	private final float y;
	private final int width;
	private final int depth;
	private final int cellWidth;
	private final int cellDepth;
	private final int columns;
	private final int rows;
	final GridCell[] cells;

	public Grid(final float x, final float y, final int width, final int depth, final int cellWidth,
			final int cellDepth) {
		final int columns = width / cellWidth;
		final int rows = depth / cellDepth;

		this.x = x;
		this.y = y;
		this.width = width;
		this.depth = depth;
		this.cellWidth = cellWidth;
		this.cellDepth = cellDepth;
		this.columns = columns;
		this.rows = rows;
		this.cells = new GridCell[rows * columns];

		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				final float left = x + (column * cellWidth);
				final float right = left + cellWidth;
				final float bottom = y + (row * cellDepth);
				final float top = bottom + cellDepth;

				this.cells[(row * columns) + column] = new GridCell(left, right, bottom, top);
			}
		}
	}

	public void add(final ModelInstance instance) {
		final int left = instance.left;
		final int right = instance.right + 1;
		final int bottom = instance.bottom;
		final int top = instance.top + 1;

		if (left != -1) {
			for (int y = bottom; y < top; y++) {
				for (int x = left; x < right; x++) {
					this.cells[(y * this.columns) + x].add(instance);
				}
			}
		}
	}

	public void remove(final ModelInstance instance) {
		final int left = instance.left;
		final int right = instance.right + 1;
		final int bottom = instance.bottom;
		final int top = instance.top + 1;

		if (left != -1) {
			instance.left = -1;

			for (int y = bottom; y < top; y++) {
				for (int x = left; x < right; x++) {
					this.cells[(y * this.columns) + x].remove(instance);
				}
			}
		}
	}

	public void moved(final ModelInstance instance, final float upcomingX, final float upcomingY) {
		if (instance.parent != null) {
			return;
		}
		final Bounds bounds = instance.model.bounds;
		final float x = (upcomingX + bounds.x) - this.x;
		final float y = (upcomingY + bounds.y) - this.y;
		final Vector3 s = instance.worldScale;
		// warsmash-nx: the radius isVisible uses (largest scale), not the x and y scales on their own
		final float reach = bounds.r * Math.max(Math.abs(s.x), Math.max(Math.abs(s.y), Math.abs(s.z)));
		int left = (int) (Math.floor((x - reach) / this.cellWidth));
		int right = (int) (Math.floor((x + reach) / this.cellWidth));
		int bottom = (int) (Math.floor((y - reach) / this.cellDepth));
		int top = (int) (Math.floor((y + reach) / this.cellDepth));

		if ((right < 0) || (left > (this.columns - 1)) || (top < 0) || (bottom > (this.rows - 1))) {
			// The instance is outside of the grid, so remove it.
			this.remove(instance);
		}
		else {
			// Clamp the values so they are in the grid.
			left = Math.max(left, 0);
			right = Math.min(right, this.columns - 1);
			bottom = Math.max(bottom, 0);
			top = Math.min(top, this.rows - 1);

			// If the values actually changed, update the cells.
			if ((left != instance.left) || (right != instance.right) || (bottom != instance.bottom)
					|| (top != instance.top)) {
				/// TODO: This can be optimized by checking if there are shared cells.
				/// That can be done in precisely the same way as done a few lines above, i.e.
				/// simple rectangle intersection.
				this.remove(instance);

				instance.left = left;
				instance.right = right;
				instance.bottom = bottom;
				instance.top = top;

				this.add(instance);
			}

			// warsmash-nx: the cells' boxes hold the sphere at both heights isVisible may use
			// (the world location, or the local one while the instance is dirty)
			final float worldZ = instance.worldLocation.z;
			final float localZ = instance.localLocation.z;
			final float low = (Math.min(worldZ, localZ) + bounds.z) - reach;
			final float high = Math.max(worldZ, localZ) + bounds.z + reach;
			for (int row = bottom; row <= top; row++) {
				for (int column = left; column <= right; column++) {
					this.cells[(row * this.columns) + column].includeHeights(low, high);
				}
			}
		}
	}

	public void clear() {
		for (final GridCell cell : this.cells) {
			cell.clear();
		}
	}
}
