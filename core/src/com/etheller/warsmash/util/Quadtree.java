package com.etheller.warsmash.util;

import java.util.IdentityHashMap;
import java.util.function.Consumer;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class Quadtree<T> {
	private static final int MAX_DEPTH = 9; // 2^9 = 512, and 512 is the biggest map size...
	private static final int SPLIT_THRESHOLD = 6;

	private final Rectangle bounds;
	private final IdentityHashMap<T, Node<T>> registered;
	private Quadtree<T> northeast;
	private Quadtree<T> northwest;
	private Quadtree<T> southwest;
	private Quadtree<T> southeast;
	private final Array<Node<T>> nodes = new Array<>();
	private Array<Node<T>> outsideNodes;
	private boolean leaf = true;
	private final NodeAdder nodeAdder = new NodeAdder();
	private final UniqueNodeAdder uniqueNodeAdder = new UniqueNodeAdder();

	public Quadtree(final Rectangle bounds) {
		this(bounds, true);
	}

	private Quadtree(final Rectangle bounds, final boolean root) {
		this.bounds = bounds;
		this.registered = root ? new IdentityHashMap<>() : null;
	}

	public void add(final T object, final Rectangle bounds) {
		final Node<T> replacement = new Node<>(object, new Rectangle(bounds));
		final Node<T> previous = this.registered.put(object, replacement);
		if (previous != null) removeFromIndex(previous);
		addAtRoot(replacement);
	}

	public void remove(final T object, final Rectangle bounds) {
		final Node<T> node = this.registered.remove(object);
		if (node != null) removeFromIndex(node);
	}

	public void translate(final T object, final Rectangle prevBoundsToUpdate, final float xShift, final float yShift) {
		final Node<T> node = this.registered.get(object);
		prevBoundsToUpdate.x += xShift;
		prevBoundsToUpdate.y += yShift;
		if (node != null) {
			removeFromIndex(node);
			node.bounds.set(prevBoundsToUpdate);
			addAtRoot(node);
		}
	}

	public boolean intersect(final Rectangle bounds, final QuadtreeIntersector<T> intersector) {
		if (this.outsideNodes != null) {
			for (int index = 0; index < this.outsideNodes.size; index++) {
				final Node<T> node = this.outsideNodes.get(index);
				if (node.bounds.overlaps(bounds) && intersector.onIntersect(node.object)) return true;
			}
		}
		return intersectRect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, intersector);
	}

	private boolean intersectRect(final float minX, final float minY, final float maxX, final float maxY,
			final QuadtreeIntersector<T> intersector) {
		if (this.leaf) {
			final Object[] items = this.nodes.items;
			final int size = this.nodes.size;
			for (int i = 0; i < size; i++) {
				@SuppressWarnings("unchecked")
				final Node<T> node = (Node<T>) items[i];
				if (node != null) {
					final Rectangle nb = node.bounds;
					if ((nb.x < maxX) && ((nb.x + nb.width) > minX) && (nb.y < maxY) && ((nb.y + nb.height) > minY)) {
						if (intersector.onIntersect(node.object)) {
							return true;
						}
					}
				}
			}
			return false;
		}
		else {
			final Quadtree<T> ne = this.northeast;
			final Rectangle neb = ne.bounds;
			if ((neb.x < maxX) && ((neb.x + neb.width) > minX) && (neb.y < maxY) && ((neb.y + neb.height) > minY)) {
				if (ne.intersectRect(minX, minY, maxX, maxY, intersector)) {
					return true;
				}
			}
			final Quadtree<T> nw = this.northwest;
			final Rectangle nwb = nw.bounds;
			if ((nwb.x < maxX) && ((nwb.x + nwb.width) > minX) && (nwb.y < maxY) && ((nwb.y + nwb.height) > minY)) {
				if (nw.intersectRect(minX, minY, maxX, maxY, intersector)) {
					return true;
				}
			}
			final Quadtree<T> sw = this.southwest;
			final Rectangle swb = sw.bounds;
			if ((swb.x < maxX) && ((swb.x + swb.width) > minX) && (swb.y < maxY) && ((swb.y + swb.height) > minY)) {
				if (sw.intersectRect(minX, minY, maxX, maxY, intersector)) {
					return true;
				}
			}
			final Quadtree<T> se = this.southeast;
			final Rectangle seb = se.bounds;
			if ((seb.x < maxX) && ((seb.x + seb.width) > minX) && (seb.y < maxY) && ((seb.y + seb.height) > minY)) {
				if (se.intersectRect(minX, minY, maxX, maxY, intersector)) {
					return true;
				}
			}
			return false;
		}
	}

	public boolean intersect(final float x, final float y, final QuadtreeIntersector<T> intersector) {
		if (this.outsideNodes != null) {
			for (int index = 0; index < this.outsideNodes.size; index++) {
				final Node<T> node = this.outsideNodes.get(index);
				if (node.bounds.contains(x, y) && intersector.onIntersect(node.object)) return true;
			}
		}
		if (this.leaf) {
			final Object[] items = this.nodes.items;
			final int size = this.nodes.size;
			for (int i = 0; i < size; i++) {
				@SuppressWarnings("unchecked")
				final Node<T> node = (Node<T>) items[i];
				if (node != null) {
					final Rectangle nb = node.bounds;
					if ((nb.x <= x) && ((nb.x + nb.width) >= x) && (nb.y <= y) && ((nb.y + nb.height) >= y)) {
						if (intersector.onIntersect(node.object)) {
							return true;
						}
					}
				}
			}
			return false;
		}
		else {
			final Quadtree<T> ne = this.northeast;
			final Rectangle neb = ne.bounds;
			if ((neb.x <= x) && ((neb.x + neb.width) >= x) && (neb.y <= y) && ((neb.y + neb.height) >= y)) {
				if (ne.intersect(x, y, intersector)) {
					return true;
				}
			}
			final Quadtree<T> nw = this.northwest;
			final Rectangle nwb = nw.bounds;
			if ((nwb.x <= x) && ((nwb.x + nwb.width) >= x) && (nwb.y <= y) && ((nwb.y + nwb.height) >= y)) {
				if (nw.intersect(x, y, intersector)) {
					return true;
				}
			}
			final Quadtree<T> sw = this.southwest;
			final Rectangle swb = sw.bounds;
			if ((swb.x <= x) && ((swb.x + swb.width) >= x) && (swb.y <= y) && ((swb.y + swb.height) >= y)) {
				if (sw.intersect(x, y, intersector)) {
					return true;
				}
			}
			final Quadtree<T> se = this.southeast;
			final Rectangle seb = se.bounds;
			if ((seb.x <= x) && ((seb.x + seb.width) >= x) && (seb.y <= y) && ((seb.y + seb.height) >= y)) {
				if (se.intersect(x, y, intersector)) {
					return true;
				}
			}
			return false;
		}
	}

	private void addAtRoot(final Node<T> node) {
		// JASS may place objects beyond the playable rectangle, where no child can contain them.
		if (canIndex(node.bounds)) {
			add(node, 0);
		}
		else {
			if (this.outsideNodes == null) this.outsideNodes = new Array<>();
			this.outsideNodes.add(node);
		}
	}

	private void removeFromIndex(final Node<T> node) {
		if (canIndex(node.bounds)) {
			removeInside(node);
		}
		else if (this.outsideNodes != null) {
			this.outsideNodes.removeValue(node, true);
		}
	}

	private boolean canIndex(final Rectangle rectangle) {
		return rectangle.width > 0 && rectangle.height > 0 && this.bounds.contains(rectangle);
	}

	private void add(final Node<T> node, final int depth) {
		if (node == null) {
			return;
		}
		if (this.leaf) {
			if ((this.nodes.size >= SPLIT_THRESHOLD) && (depth < MAX_DEPTH)) {
				split(depth);
				// then dont return and add as a nonleaf
			}
			else {
				this.nodes.add(node);
				return;
			}
		}
		boolean overlapsAny = false;
		if (this.northeast.bounds.overlaps(node.bounds)) {
			this.northeast.add(node, depth + 1);
			overlapsAny = true;
		}
		if (this.northwest.bounds.overlaps(node.bounds)) {
			this.northwest.add(node, depth + 1);
			overlapsAny = true;
		}
		if (this.southwest.bounds.overlaps(node.bounds)) {
			this.southwest.add(node, depth + 1);
			overlapsAny = true;
		}
		if (this.southeast.bounds.overlaps(node.bounds)) {
			this.southeast.add(node, depth + 1);
			overlapsAny = true;
		}
		if (!overlapsAny) {
			// warsmash-nx: safety fallback if float boundary misses overlaps()
			final float cx = Math.max(this.bounds.x,
					Math.min(this.bounds.x + this.bounds.width, node.bounds.x + (node.bounds.width / 2)));
			final float cy = Math.max(this.bounds.y,
					Math.min(this.bounds.y + this.bounds.height, node.bounds.y + (node.bounds.height / 2)));
			final boolean east = cx >= (this.bounds.x + (this.bounds.width / 2));
			final boolean north = cy >= (this.bounds.y + (this.bounds.height / 2));
			(north ? (east ? this.northeast : this.northwest) : (east ? this.southeast : this.southwest)).add(node,
					depth + 1);
		}
	}

	private void split(final int depth) {
		final int splitDepth = depth + 1;
		final float halfWidth = this.bounds.width / 2;
		final float x = this.bounds.x;
		final float xMidpoint = x + halfWidth;
		final float halfHeight = this.bounds.height / 2;
		final float y = this.bounds.y;
		final float yMidpoint = y + halfHeight;
		this.northeast = new Quadtree<>(new Rectangle(xMidpoint, yMidpoint, halfWidth, halfHeight), false);
		this.northwest = new Quadtree<>(new Rectangle(x, yMidpoint, halfWidth, halfHeight), false);
		this.southwest = new Quadtree<>(new Rectangle(x, y, halfWidth, halfHeight), false);
		this.southeast = new Quadtree<>(new Rectangle(xMidpoint, y, halfWidth, halfHeight), false);
		this.leaf = false;
		this.nodes.forEach(this.nodeAdder.reset(splitDepth));
		this.nodes.clear();
	}

	private void removeInside(final Node<T> node) {
		if (this.leaf) {
			this.nodes.removeValue(node, true);
		}
		else {
			boolean overlapsAny = false;
			if (this.northeast.bounds.overlaps(node.bounds)) {
				this.northeast.removeInside(node);
				overlapsAny = true;
			}
			if (this.northwest.bounds.overlaps(node.bounds)) {
				this.northwest.removeInside(node);
				overlapsAny = true;
			}
			if (this.southwest.bounds.overlaps(node.bounds)) {
				this.southwest.removeInside(node);
				overlapsAny = true;
			}
			if (this.southeast.bounds.overlaps(node.bounds)) {
				this.southeast.removeInside(node);
				overlapsAny = true;
			}
			if (!overlapsAny) {
				final float cx = Math.max(this.bounds.x,
						Math.min(this.bounds.x + this.bounds.width, node.bounds.x + (node.bounds.width / 2)));
				final float cy = Math.max(this.bounds.y,
						Math.min(this.bounds.y + this.bounds.height, node.bounds.y + (node.bounds.height / 2)));
				final boolean east = cx >= (this.bounds.x + (this.bounds.width / 2));
				final boolean north = cy >= (this.bounds.y + (this.bounds.height / 2));
				(north ? (east ? this.northeast : this.northwest) : (east ? this.southeast : this.southwest))
						.removeInside(node);
			}
			mergeIfNecessary();
		}
	}

	private void mergeIfNecessary() {
		if (this.northeast.leaf && this.northwest.leaf && this.southwest.leaf && this.southeast.leaf) {
			final int children = this.northeast.nodes.size + this.northwest.nodes.size + this.southwest.nodes.size
					+ this.southeast.nodes.size; // might include duplicates
			if (children <= SPLIT_THRESHOLD) {
				this.leaf = true;
				addAllUnique(this.northeast.nodes);
				addAllUnique(this.northwest.nodes);
				addAllUnique(this.southwest.nodes);
				addAllUnique(this.southeast.nodes);
				this.northeast = this.northwest = this.southwest = this.southeast = null;
			}
		}
	}

	private void addAllUnique(final Array<Node<T>> nodes) {
		nodes.forEach(this.uniqueNodeAdder);
	}

	private static final class Node<T> {
		final T object;
		final Rectangle bounds;

		public Node(final T object, final Rectangle bounds) {
			this.object = object;
			this.bounds = bounds;
		}
	}

	private final class NodeAdder implements Consumer<Node<T>> {
		private int splitDepth;

		private NodeAdder reset(final int splitDepth) {
			this.splitDepth = splitDepth;
			return this;
		}

		@Override
		public void accept(final Node<T> node) {
			if (node != null) {
				add(node, this.splitDepth);
			}
		}
	}

	private final class UniqueNodeAdder implements Consumer<Node<T>> {

		private UniqueNodeAdder reset() {
			return this;
		}

		@Override
		public void accept(final Node<T> node) {
			if (node == null) {
				return;
			}
			for (int i = 0; i < Quadtree.this.nodes.size; i++) {
				if (Quadtree.this.nodes.get(i) == node) {
					return;
				}
			}
			Quadtree.this.nodes.add(node);
		}
	}
}
