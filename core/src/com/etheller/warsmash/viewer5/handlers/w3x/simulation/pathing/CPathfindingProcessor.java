package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import com.etheller.warsmash.util.WarsmashConstants;

import com.etheller.warsmash.util.geom.Point2D;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.PriorityQueue;

import java.util.concurrent.atomic.AtomicInteger;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWorldCollision;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;

public class CPathfindingProcessor {
	private static final Rectangle tempRect = new Rectangle();
	private static final byte DIR_MASK = 0x0F;
	private static final byte DIR_NONE = 0x08;
	private static final byte FLAG_CLOSED = 0x10;
	private static final byte FLAG_STATIC_KNOWN = 0x20;
	private static final byte FLAG_STATIC_PASSABLE = 0x40;
	private static final int[] DIR_DX = { -1, 0, 1, 1, 1, 0, -1, -1 };
	private static final int[] DIR_DY = { 1, 1, 1, 0, -1, -1, -1, 0 };
	private static final double SQRT2 = Math.sqrt(2.0);
	private static final double[] DIR_LEN = { SQRT2, 1.0, SQRT2, 1.0, SQRT2, 1.0, SQRT2, 1.0 };

	private final PathingGrid pathingGrid;
	private final CWorldCollision worldCollision;
	private final LinkedList<PathfindingJob> moveQueue = new LinkedList<>();
	// things with modified state per current job:
	private Node[][] nodes;
	private Node[][] cornerNodes;
	private final Node[] goalSet = new Node[4];
	// Retained empty map for checkpoint field compatibility.
	private final Map<Node, Node> searchNodes = new IdentityHashMap<>();
	private int goals = 0;
	private final AtomicInteger sharedJobId;
	private int pathfindJobId = 0;
	private int totalIterations = 0;
	private int totalJobLoops = 0;
	private final int pathingGridCellCount;

	// warsmash-nx: lazy primitive A* scratch buffers (transient so MissionStateFingerprint skips them)
	private transient int[] cellStamp;
	private transient double[] cellG;
	private transient int[] cellCameFrom;
	private transient byte[] cellFlags;
	private transient int searchStamp = 0;
	private transient int[] heapCells;
	private transient double[] heapF;
	private transient int heapSize = 0;
	private transient final int[] goalCellIdx = new int[9];
	private transient final float[] goalWorldX = new float[9];
	private transient final float[] goalWorldY = new float[9];
	private transient int activeGoalCount = 0;
	private transient int bestFallbackCell = -1;
	private transient float bestFallbackH = Float.POSITIVE_INFINITY;
	private transient float startFallbackH = Float.POSITIVE_INFINITY;

	public CPathfindingProcessor(final PathingGrid pathingGrid, final CWorldCollision worldCollision) {
		this(pathingGrid, worldCollision, createNodes(pathingGrid), createCornerNodes(pathingGrid), new AtomicInteger());
	}

	public CPathfindingProcessor(final PathingGrid pathingGrid, final CWorldCollision worldCollision,
			final Node[][] nodes, final Node[][] cornerNodes, final AtomicInteger sharedJobId) {
		this.pathingGrid = pathingGrid;
		this.worldCollision = worldCollision;
		this.nodes = nodes;
		this.cornerNodes = cornerNodes;
		this.sharedJobId = sharedJobId;
		this.pathingGridCellCount = pathingGrid.getWidth() * pathingGrid.getHeight();
	}

	private void ensureSearchBuffers() {
		final int maxCells = (this.pathingGrid.getWidth() + 1) * (this.pathingGrid.getHeight() + 1);
		if ((this.cellStamp == null) || (this.cellStamp.length < maxCells)) {
			this.cellStamp = new int[maxCells];
			this.cellG = new double[maxCells];
			this.cellCameFrom = new int[maxCells];
			this.cellFlags = new byte[maxCells];
		}
		if (this.heapCells == null) {
			this.heapCells = new int[1024];
			this.heapF = new double[1024];
		}
	}

	private int nextSearchStamp() {
		int s = ++this.searchStamp;
		if (s == 0) {
			Arrays.fill(this.cellStamp, 0);
			s = 1;
			this.searchStamp = 1;
		}
		return s;
	}

	private void heapPush(final int cellIdx, final double f) {
		int i = this.heapSize++;
		if (i >= this.heapCells.length) {
			final int newCap = this.heapCells.length * 2;
			this.heapCells = Arrays.copyOf(this.heapCells, newCap);
			this.heapF = Arrays.copyOf(this.heapF, newCap);
		}
		final int[] cells = this.heapCells;
		final double[] fs = this.heapF;
		while (i > 0) {
			final int parent = (i - 1) >>> 1;
			if (f >= fs[parent]) {
				break;
			}
			cells[i] = cells[parent];
			fs[i] = fs[parent];
			i = parent;
		}
		cells[i] = cellIdx;
		fs[i] = f;
	}

	private int heapPop() {
		final int[] cells = this.heapCells;
		final double[] fs = this.heapF;
		final int result = cells[0];
		final int n = --this.heapSize;
		if (n > 0) {
			final int lastCell = cells[n];
			final double lastF = fs[n];
			int i = 0;
			final int half = n >>> 1;
			while (i < half) {
				int child = (i << 1) + 1;
				final int right = child + 1;
				double childF = fs[child];
				if ((right < n) && (fs[right] < childF)) {
					child = right;
					childF = fs[right];
				}
				if (lastF <= childF) {
					break;
				}
				cells[i] = cells[child];
				fs[i] = childF;
				i = child;
			}
			cells[i] = lastCell;
			fs[i] = lastF;
		}
		return result;
	}

	public static Node[][] createNodes(final PathingGrid pathingGrid) {
		final Node[][] nodes = new Node[pathingGrid.getHeight()][pathingGrid.getWidth()];
		for (int i = 0; i < nodes.length; i++) {
			for (int j = 0; j < nodes[i].length; j++) {
				nodes[i][j] = new Node(new Point2D.Float(pathingGrid.getWorldX(j), pathingGrid.getWorldY(i)));
			}
		}
		return nodes;
	}

	public static Node[][] createCornerNodes(final PathingGrid pathingGrid) {
		final Node[][] cornerNodes = new Node[pathingGrid.getHeight() + 1][pathingGrid.getWidth() + 1];
		for (int i = 0; i < cornerNodes.length; i++) {
			for (int j = 0; j < cornerNodes[i].length; j++) {
				cornerNodes[i][j] = new Node(
						new Point2D.Float(pathingGrid.getWorldXFromCorner(j), pathingGrid.getWorldYFromCorner(i)));
			}
		}
		return cornerNodes;
	}

	/**
	 * Finds the path to a point using a naive, slow, and unoptimized algorithm.
	 * Does not have optimizations yet, do this for a bunch of units and it will
	 * probably lag like a walrus. The implementation here was created by reading
	 * the wikipedia article on A* to jog my memory from data structures class back
	 * in college, and is meant only as a first draft to get things working.
	 *
	 * @param collisionSize
	 *
	 *
	 * @param start
	 * @param goal
	 * @param playerIndex
	 * @param queueItem
	 * @return
	 */
	public void findNaiveSlowPath(final CUnit ignoreIntersectionsWithThisUnit,
			final CUnit ignoreIntersectionsWithThisSecondUnit, final float startX, final float startY,
			final Point2D.Float goal, final PathingGrid.MovementType movementType, final float collisionSize,
			final boolean allowSmoothing, final CBehaviorMove queueItem) {
		this.moveQueue.offer(new PathfindingJob(ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit,
				startX, startY, goal, movementType, collisionSize, allowSmoothing, queueItem));
	}

	public void removeFromPathfindingQueue(final CBehaviorMove behaviorMove) {
		final Iterator<PathfindingJob> iterator = this.moveQueue.iterator();
		while (iterator.hasNext()) {
			final PathfindingJob job = iterator.next();
			if (job.queueItem == behaviorMove) {
				iterator.remove();
			}
		}
	}

	private boolean pathableBetween(final CUnit ignoreIntersectionsWithThisUnit,
			final CUnit ignoreIntersectionsWithThisSecondUnit, final float startX, final float startY,
			final PathingGrid.MovementType movementType, final float collisionSize, final float x, final float y) {
		if (!this.pathingGrid.isPathable(x, y, movementType, collisionSize)) {
			return false;
		}
		if (startX != x) {
			if (!this.pathingGrid.isPathable(startX, y, movementType, collisionSize)) {
				return false;
			}
		}
		if (startY != y) {
			if (!this.pathingGrid.isPathable(x, startY, movementType, collisionSize)) {
				return false;
			}
		}
		if (!isPathableDynamically(x, y, ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit,
				movementType)) {
			return false;
		}
		if ((startY != y) && !isPathableDynamically(x, startY, ignoreIntersectionsWithThisUnit,
				ignoreIntersectionsWithThisSecondUnit, movementType)) {
			return false;
		}
		if ((startX != x) && !isPathableDynamically(startX, y, ignoreIntersectionsWithThisUnit,
				ignoreIntersectionsWithThisSecondUnit, movementType)) {
			return false;
		}
		return true;
	}

	private boolean isPathableDynamically(final float x, final float y, final CUnit ignoreIntersectionsWithThisUnit,
			final CUnit ignoreIntersectionsWithThisSecondUnit, final PathingGrid.MovementType movementType) {
		return !this.worldCollision.intersectsAnythingOtherThan(tempRect.setCenter(x, y),
				ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit, movementType, false);
	}

	public static boolean isCollisionSizeBetterSuitedForCorners(final float collisionSize) {
		return (((2 * (int) collisionSize) / 32) % 2) == 1;
	}

	public double f(final Node n) {
		return n.g + h(n);
	}

	public double g(final Node n) {
		return n.g;
	}

	public float h(final Node n) {
		return heuristicWorld(n.point.x, n.point.y);
	}

	private float heuristicWorld(final float x, final float y) {
		float bestDistSq = 0f;
		final int count = this.activeGoalCount;
		final float[] gx = this.goalWorldX;
		final float[] gy = this.goalWorldY;
		for (int i = 0; i < count; i++) {
			final float dx = x - gx[i];
			final float dy = y - gy[i];
			final float distSq = (dx * dx) + (dy * dy);
			if (distSq > bestDistSq) {
				bestDistSq = distSq;
			}
		}
		// warsmash-nx: mildly weighted A* (paths at most 20% longer, far fewer cells searched on long trips)
		return (float) Math.sqrt(bestDistSq) * 1.2f;
	}

	private boolean isGoalCell(final int cellIdx) {
		final int count = this.activeGoalCount;
		final int[] gIdx = this.goalCellIdx;
		for (int i = 0; i < count; i++) {
			if (gIdx[i] == cellIdx) {
				return true;
			}
		}
		return false;
	}

	private boolean checkStaticPathableCached(final int idx, final int stamp, final float x, final float y,
			final MovementType movementType, final float collisionSize) {
		final int[] stamps = this.cellStamp;
		final byte[] flags = this.cellFlags;
		if (stamps[idx] != stamp) {
			stamps[idx] = stamp;
			this.cellG[idx] = Double.POSITIVE_INFINITY;
			this.cellCameFrom[idx] = -1;
			final boolean ok = this.pathingGrid.isPathable(x, y, movementType, collisionSize);
			flags[idx] = (byte) (DIR_NONE | FLAG_STATIC_KNOWN | (ok ? FLAG_STATIC_PASSABLE : 0));
			return ok;
		}
		final byte f = flags[idx];
		if ((f & FLAG_STATIC_KNOWN) != 0) {
			return (f & FLAG_STATIC_PASSABLE) != 0;
		}
		final boolean ok = this.pathingGrid.isPathable(x, y, movementType, collisionSize);
		flags[idx] = (byte) (f | FLAG_STATIC_KNOWN | (ok ? FLAG_STATIC_PASSABLE : 0));
		return ok;
	}

	private Point2D.Float pointForCell(final PathfindingJob job, final int cellIdx, final int stride) {
		final int cy = cellIdx / stride;
		final int cx = cellIdx - (cy * stride);
		return job.searchGraph[cy][cx].point;
	}

	public static final class Node {
		public Direction cameFromDirection;
		private final Point2D.Float point;
		private double f;
		private double g;
		private Node cameFrom;
		private int pathfindJobId;

		private Node(final Point2D.Float point) {
			this.point = point;
		}
	}

	private static enum Direction {
		NORTH_WEST(-1, 1), NORTH(0, 1), NORTH_EAST(1, 1), EAST(1, 0), SOUTH_EAST(1, -1), SOUTH(0, -1),
		SOUTH_WEST(-1, -1), WEST(-1, 0);

		public static final Direction[] VALUES = values();

		private final int xOffset;
		private final int yOffset;
		private final double length;

		private Direction(final int xOffset, final int yOffset) {
			this.xOffset = xOffset;
			this.yOffset = yOffset;
			final double sqrt = Math.sqrt((xOffset * xOffset) + (yOffset * yOffset));
			this.length = sqrt;
		}
	}

	public static interface GridMapping {
		int getX(PathingGrid grid, float worldX);

		int getY(PathingGrid grid, float worldY);

		public static final GridMapping CELLS = new GridMapping() {
			@Override
			public int getX(final PathingGrid grid, final float worldX) {
				return grid.getCellX(worldX);
			}

			@Override
			public int getY(final PathingGrid grid, final float worldY) {
				return grid.getCellY(worldY);
			}

		};

		public static final GridMapping CORNERS = new GridMapping() {
			@Override
			public int getX(final PathingGrid grid, final float worldX) {
				return grid.getCornerX(worldX);
			}

			@Override
			public int getY(final PathingGrid grid, final float worldY) {
				return grid.getCornerY(worldY);
			}

		};
	}

	/**
	 * warsmash-nx: search iterations per simulation tick (was a fixed 1500). The console sets less, so
	 * a burst of path requests is spread over more ticks instead of stalling frames.
	 */
	public static int WORK_BUDGET_PER_TICK = 1500;
	/** NXSTAT temporary PC statistics (env NX_PATHSTAT). */
	static final boolean NXSTAT = System.getenv("NX_PATHSTAT") != null;
	static int nxstatTicks, nxstatJobs, nxstatIters, nxstatBudgetTicks, nxstatPartial, nxstatQueueMax;
	static final java.util.Map<String, Integer> nxstatWho = new java.util.HashMap<>();

	public void update(final CSimulation simulation) {
		if (NXSTAT) {
			nxstatTicks++;
			nxstatQueueMax = Math.max(nxstatQueueMax, this.moveQueue.size());
			if (nxstatTicks % 400 == 0) {
				System.out.println("NXSTAT path: per tick jobs " + (nxstatJobs / 400f) + " iterations " + (nxstatIters / 400f)
						+ " budget-hit ticks " + nxstatBudgetTicks + "/400 partial " + nxstatPartial + " queue max " + nxstatQueueMax
						+ " who " + nxstatWho);
				nxstatJobs = nxstatIters = nxstatBudgetTicks = nxstatPartial = nxstatQueueMax = 0;
				nxstatWho.clear();
			}
		}
		if (this.moveQueue.isEmpty()) {
			return;
		}
		ensureSearchBuffers();
		final int pathW = this.pathingGrid.getWidth();
		final int pathH = this.pathingGrid.getHeight();
		final float offX = this.pathingGrid.getCenterOffsetX();
		final float offY = this.pathingGrid.getCenterOffsetY();
		int workIterations = 0;
		JobsLoop: while (!this.moveQueue.isEmpty()) {
			this.totalJobLoops++;
			final PathfindingJob job = this.moveQueue.peek();
			tempRect.set(0, 0, job.collisionSize * 2, job.collisionSize * 2);
			if (!job.jobStarted) {
				this.pathfindJobId = this.sharedJobId.incrementAndGet();
				this.totalIterations = 0;
				this.totalJobLoops = 0;
				job.jobStarted = true;
				if (NXSTAT) {
					nxstatJobs++;
					final String who = CUnit.maybeMeaningfulName(job.ignoreIntersectionsWithThisUnit);
					nxstatWho.merge(who, 1, Integer::sum);
				}
				if (WarsmashConstants.ENABLE_DEBUG) System.out.println("starting job with smoothing=" + job.allowSmoothing);
				workIterations += 5; // setup of job predicted cost
				job.goalX = job.goal.x;
				job.goalY = job.goal.y;
				job.weightForHittingWalls = 1E9f;
				if (!this.pathingGrid.isPathable(job.goalX, job.goalY, job.movementType, job.collisionSize)
						|| !isPathableDynamically(job.goalX, job.goalY, job.ignoreIntersectionsWithThisUnit,
								job.ignoreIntersectionsWithThisSecondUnit, job.movementType)) {
					job.weightForHittingWalls = 5E2f;
				}
				if (WarsmashConstants.ENABLE_DEBUG) System.out.println("beginning findNaiveSlowPath for  " + job.startX + "," + job.startY + "," + job.goalX
						+ "," + job.goalY);
				if ((job.startX == job.goalX) && (job.startY == job.goalY)) {
					job.queueItem.pathFound(Collections.emptyList(), simulation);
					this.moveQueue.poll();
					continue JobsLoop;
				}
				final boolean useCorners = isCollisionSizeBetterSuitedForCorners(job.collisionSize);
				if (useCorners) {
					job.searchGraph = this.cornerNodes;
					job.gridMapping = GridMapping.CORNERS;
					if (WarsmashConstants.ENABLE_DEBUG) System.out.println("using corners");
				}
				else {
					job.searchGraph = this.nodes;
					job.gridMapping = GridMapping.CELLS;
					if (WarsmashConstants.ENABLE_DEBUG) System.out.println("using cells");
				}
				final int stride = job.searchGraph[0].length;
				final int graphH = job.searchGraph.length;
				final float baseX = useCorners ? offX : (offX + 16f);
				final float baseY = useCorners ? offY : (offY + 16f);

				final int stamp = nextSearchStamp();
				this.heapSize = 0;
				this.bestFallbackCell = -1;
				this.bestFallbackH = Float.POSITIVE_INFINITY;

				final int goalCellY = job.gridMapping.getY(this.pathingGrid, job.goalY);
				final int goalCellX = job.gridMapping.getX(this.pathingGrid, job.goalX);
				final float likelyGoalX = (goalCellX * 32f) + baseX;
				final float likelyGoalY = (goalCellY * 32f) + baseY;
				final float dgx = likelyGoalX - job.goalX;
				final float dgy = likelyGoalY - job.goalY;
				final float bestGoalDistSq = (dgx * dgx) + (dgy * dgy);
				this.activeGoalCount = 0;
				for (int i = goalCellX - 1; i <= (goalCellX + 1); i++) {
					for (int j = goalCellY - 1; j <= (goalCellY + 1); j++) {
						if ((j >= 0) && (j < graphH) && (i >= 0) && (i < stride)) {
							final float px = (i * 32f) + baseX;
							final float py = (j * 32f) + baseY;
							final float dx = px - job.goalX;
							final float dy = py - job.goalY;
							if (((dx * dx) + (dy * dy)) <= bestGoalDistSq + 1e-3f) {
								final int gCount = this.activeGoalCount;
								if (gCount < this.goalCellIdx.length) {
									this.goalCellIdx[gCount] = (j * stride) + i;
									this.goalWorldX[gCount] = px;
									this.goalWorldY[gCount] = py;
									this.activeGoalCount = gCount + 1;
								}
							}
						}
					}
				}
				this.goals = this.activeGoalCount;

				final int startGridY = job.gridMapping.getY(this.pathingGrid, job.startY);
				final int startGridX = job.gridMapping.getX(this.pathingGrid, job.startX);
				final float startCenterX = (startGridX * 32f) + baseX;
				final float startCenterY = (startGridY * 32f) + baseY;
				this.startFallbackH = heuristicWorld(startCenterX, startCenterY);

				if (job.startX > startCenterX) {
					job.startGridMinX = startGridX;
					job.startGridMaxX = startGridX + 1;
				}
				else if (job.startX < startCenterX) {
					job.startGridMinX = startGridX - 1;
					job.startGridMaxX = startGridX;
				}
				else {
					job.startGridMinX = startGridX;
					job.startGridMaxX = startGridX;
				}
				if (job.startY > startCenterY) {
					job.startGridMinY = startGridY;
					job.startGridMaxY = startGridY + 1;
				}
				else if (job.startY < startCenterY) {
					job.startGridMinY = startGridY - 1;
					job.startGridMaxY = startGridY;
				}
				else {
					job.startGridMinY = startGridY;
					job.startGridMaxY = startGridY;
				}
				final int[] stamps = this.cellStamp;
				final double[] gArr = this.cellG;
				final int[] cameArr = this.cellCameFrom;
				final byte[] flagsArr = this.cellFlags;
				for (int cellX = job.startGridMinX; cellX <= job.startGridMaxX; cellX++) {
					for (int cellY = job.startGridMinY; cellY <= job.startGridMaxY; cellY++) {
						if ((cellX >= 0) && (cellX < pathW) && (cellY >= 0) && (cellY < pathH)) {
							final int idx = (cellY * stride) + cellX;
							final float x = (cellX * 32f) + baseX;
							final float y = (cellY * 32f) + baseY;
							final double tentativeScore;
							if (pathableBetween(job.ignoreIntersectionsWithThisUnit,
									job.ignoreIntersectionsWithThisSecondUnit, job.startX, job.startY, job.movementType,
									job.collisionSize, x, y)) {
								final float dx = x - job.startX;
								final float dy = y - job.startY;
								tentativeScore = Math.sqrt((dx * dx) + (dy * dy));
							}
							else {
								tentativeScore = job.weightForHittingWalls;
							}
							stamps[idx] = stamp;
							gArr[idx] = tentativeScore;
							cameArr[idx] = -1;
							flagsArr[idx] = DIR_NONE;
							heapPush(idx, tentativeScore + heuristicWorld(x, y));
						}
					}
				}
			}

			final boolean useCorners = (job.gridMapping == GridMapping.CORNERS);
			final int stride = job.searchGraph[0].length;
			final float baseX = useCorners ? offX : (offX + 16f);
			final float baseY = useCorners ? offY : (offY + 16f);
			final int stamp = this.searchStamp;
			final int[] stamps = this.cellStamp;
			final double[] gArr = this.cellG;
			final int[] cameArr = this.cellCameFrom;
			final byte[] flagsArr = this.cellFlags;
			final float startX = job.startX;
			final float startY = job.startY;
			final CUnit ignoreUnit1 = job.ignoreIntersectionsWithThisUnit;
			final CUnit ignoreUnit2 = job.ignoreIntersectionsWithThisSecondUnit;
			final MovementType movementType = job.movementType;
			final float collisionSize = job.collisionSize;
			final float wallWeight = job.weightForHittingWalls;

			while (this.heapSize > 0) {
				final int currentIdx = heapPop();
				if (stamps[currentIdx] != stamp) {
					continue;
				}
				final byte curFlags = flagsArr[currentIdx];
				if ((curFlags & FLAG_CLOSED) != 0) {
					continue;
				}
				flagsArr[currentIdx] = (byte) (curFlags | FLAG_CLOSED);

				final int curY = currentIdx / stride;
				final int curX = currentIdx - (curY * stride);
				final float curWorldX = (curX * 32f) + baseX;
				final float curWorldY = (curY * 32f) + baseY;
				final double curG = gArr[currentIdx];
				final int curDir = curFlags & DIR_MASK;

				if (curG < 1.0E8) {
					final float curH = heuristicWorld(curWorldX, curWorldY);
					if (curH < this.bestFallbackH) {
						this.bestFallbackH = curH;
						this.bestFallbackCell = currentIdx;
					}
				}

				if (isGoalCell(currentIdx)) {
					final LinkedList<Point2D.Float> totalPath = new LinkedList<>();
					int cur = currentIdx;
					int prev = cameArr[cur];
					if ((prev != -1) && job.allowSmoothing) {
						final Point2D.Float curPt = pointForCell(job, cur, stride);
						final Point2D.Float prevPt = pointForCell(job, prev, stride);
						if (pathableBetween(ignoreUnit1, ignoreUnit2, curPt.x, curPt.y, movementType, collisionSize,
								job.goalX, job.goalY)
								&& pathableBetween(ignoreUnit1, ignoreUnit2, prevPt.x, prevPt.y, movementType,
										collisionSize, curPt.x, curPt.y)
								&& pathableBetween(ignoreUnit1, ignoreUnit2, prevPt.x, prevPt.y, movementType,
										collisionSize, job.goalX, job.goalY)) {
							totalPath.addFirst(job.goal);
							cur = prev;
						}
						else {
							totalPath.addFirst(job.goal);
							totalPath.addFirst(curPt);
						}
					}
					else {
						totalPath.addFirst(job.goal);
						totalPath.addFirst(pointForCell(job, cur, stride));
					}
					int lastDir = flagsArr[cur] & DIR_MASK;
					int lastCell = -1;
					int stepsBackward = 0;
					while (cameArr[cur] != -1) {
						lastCell = cur;
						cur = cameArr[cur];
						final int dir = flagsArr[cur] & DIR_MASK;
						if ((lastDir == DIR_NONE) || (dir != lastDir) || (dir == DIR_NONE)) {
							final Point2D.Float curPt = pointForCell(job, cur, stride);
							boolean addPoint = true;
							if ((dir == DIR_NONE) && (lastCell != -1) && job.allowSmoothing) {
								final Point2D.Float lastPt = pointForCell(job, lastCell, stride);
								if (pathableBetween(ignoreUnit1, ignoreUnit2, startX, startY, movementType,
										collisionSize, curPt.x, curPt.y)
										&& pathableBetween(ignoreUnit1, ignoreUnit2, curPt.x, curPt.y, movementType,
												collisionSize, lastPt.x, lastPt.y)
										&& pathableBetween(ignoreUnit1, ignoreUnit2, startX, startY, movementType,
												collisionSize, lastPt.x, lastPt.y)) {
									addPoint = false;
								}
							}
							if (addPoint) {
								totalPath.addFirst(curPt);
								lastDir = dir;
							}
						}
						if (stepsBackward > this.pathingGridCellCount) {
							new IllegalStateException(
									"PATHING SYSTEM ERROR: The path finding algorithm hit an infinite cycle at or near pt: "
											+ pointForCell(job, cameArr[cur], stride)
											+ ".\nThis means the A* search algorithm heuristic 'admissable' constraint was probably violated.\n\nUnit1:"
											+ CUnit.maybeMeaningfulName(ignoreUnit1)
											+ "\nUnit2:"
											+ CUnit.maybeMeaningfulName(ignoreUnit2))
									.printStackTrace();
							totalPath.clear();
							break;
						}
						stepsBackward++;
					}
					job.queueItem.pathFound(totalPath, simulation);
					this.moveQueue.poll();
					if (WarsmashConstants.ENABLE_DEBUG) System.out.println("Task " + this.pathfindJobId + " took " + this.totalIterations
							+ " iterations and " + this.totalJobLoops + " job loops!");
					continue JobsLoop;
				}

				// warsmash-nx: other units are only obstacles near the walker; far away they will have moved
				final float dsx = curWorldX - startX;
				final float dsy = curWorldY - startY;
				final boolean nearStart = ((dsx * dsx) + (dsy * dsy)) < (640f * 640f);

				for (int d = 0; d < 8; d++) {
					final int dx = DIR_DX[d];
					final int dy = DIR_DY[d];
					final int nx = curX + dx;
					final int ny = curY + dy;
					if ((nx < 0) || (nx >= pathW) || (ny < 0) || (ny >= pathH)) {
						continue;
					}
					final int nIdx = (ny * stride) + nx;
					if ((stamps[nIdx] == stamp) && ((flagsArr[nIdx] & FLAG_CLOSED) != 0)) {
						continue;
					}
					final float nxWorld = (nx * 32f) + baseX;
					final float nyWorld = (ny * 32f) + baseY;
					final double dirLen = DIR_LEN[d];
					final double turnCost = ((curDir != DIR_NONE) && (d != curDir)) ? 0.25 : 0.0;
					double tentativeScore = curG + ((dirLen + turnCost) * 32.0);

					final boolean passable;
					if (nearStart) {
						boolean ok = checkStaticPathableCached(nIdx, stamp, nxWorld, nyWorld, movementType,
								collisionSize);
						if (ok && (dx != 0) && (dy != 0)) {
							ok = checkStaticPathableCached((ny * stride) + curX, stamp, curWorldX, nyWorld,
									movementType, collisionSize)
									&& checkStaticPathableCached((curY * stride) + nx, stamp, nxWorld, curWorldY,
											movementType, collisionSize);
						}
						if (ok) {
							ok = isPathableDynamically(nxWorld, nyWorld, ignoreUnit1, ignoreUnit2, movementType);
							if (ok && (dx != 0) && (dy != 0)) {
								ok = isPathableDynamically(nxWorld, curWorldY, ignoreUnit1, ignoreUnit2, movementType)
										&& isPathableDynamically(curWorldX, nyWorld, ignoreUnit1, ignoreUnit2,
												movementType);
							}
						}
						passable = ok;
					}
					else {
						boolean ok = checkStaticPathableCached(nIdx, stamp, nxWorld, nyWorld, movementType,
								collisionSize);
						if (ok && (dx != 0) && (dy != 0)) {
							ok = checkStaticPathableCached((ny * stride) + curX, stamp, curWorldX, nyWorld,
									movementType, collisionSize)
									&& checkStaticPathableCached((curY * stride) + nx, stamp, nxWorld, curWorldY,
											movementType, collisionSize);
						}
						passable = ok;
					}
					if (!passable) {
						tentativeScore += dirLen * wallWeight;
					}

					if (stamps[nIdx] != stamp) {
						stamps[nIdx] = stamp;
						gArr[nIdx] = tentativeScore;
						cameArr[nIdx] = currentIdx;
						flagsArr[nIdx] = (byte) d;
						heapPush(nIdx, tentativeScore + heuristicWorld(nxWorld, nyWorld));
					}
					else if (tentativeScore < gArr[nIdx]) {
						gArr[nIdx] = tentativeScore;
						cameArr[nIdx] = currentIdx;
						flagsArr[nIdx] = (byte) ((flagsArr[nIdx] & ~DIR_MASK) | d);
						heapPush(nIdx, tentativeScore + heuristicWorld(nxWorld, nyWorld));
					}
				}

				workIterations++;
				if (NXSTAT) nxstatIters++;
				this.totalIterations++;
				if (this.totalIterations > 20000) {
					break;
				}
				if (workIterations >= WORK_BUDGET_PER_TICK) {
					if (NXSTAT) nxstatBudgetTicks++;
					// breaking jobs loop will implicitly exit without calling pathFound() below
					break JobsLoop;
				}
			}
			// warsmash-nx: no path to the goal (unreachable, or the iteration cap on a long trip): walk to the
			// closest point the search reached, as Warcraft III does, instead of not moving at all
			final LinkedList<Point2D.Float> partialPath = new LinkedList<>();
			int closest = ((this.bestFallbackCell != -1) && (this.bestFallbackH <= (this.startFallbackH - 96f)))
					? this.bestFallbackCell
					: -1;
			int partialDir = DIR_NONE;
			int partialSteps = 0;
			while ((closest != -1) && (cameArr[closest] != -1) && (partialSteps++ < this.pathingGridCellCount)) {
				final int dir = flagsArr[closest] & DIR_MASK;
				if (partialPath.isEmpty() || (dir != partialDir)) {
					partialPath.addFirst(pointForCell(job, closest, stride));
					partialDir = dir;
				}
				closest = cameArr[closest];
			}
			if (NXSTAT) nxstatPartial++;
			job.queueItem.pathFound(partialPath, simulation);
			this.moveQueue.poll();
			if (WarsmashConstants.ENABLE_DEBUG) System.out.println("Task " + this.pathfindJobId + " took " + this.totalIterations + " iterations and "
					+ this.totalJobLoops + " job loops! (partial path " + partialPath.size() + ")");
		}
	}

	public static final class PathfindingJob {
		private final CUnit ignoreIntersectionsWithThisUnit;
		private final CUnit ignoreIntersectionsWithThisSecondUnit;
		private final float startX;
		private final float startY;
		private final Point2D.Float goal;
		private final MovementType movementType;
		private final float collisionSize;
		private final boolean allowSmoothing;
		private final CBehaviorMove queueItem;
		private boolean jobStarted;
		public float goalY;
		public float goalX;
		public float weightForHittingWalls;
		Node[][] searchGraph;
		GridMapping gridMapping;
		PriorityQueue<Node> openSet;
		Node start;
		int startGridMinX;
		int startGridMinY;
		int startGridMaxX;
		int startGridMaxY;

		public PathfindingJob(final CUnit ignoreIntersectionsWithThisUnit,
				final CUnit ignoreIntersectionsWithThisSecondUnit, final float startX, final float startY,
				final Point2D.Float goal, final PathingGrid.MovementType movementType, final float collisionSize,
				final boolean allowSmoothing, final CBehaviorMove queueItem) {
			this.ignoreIntersectionsWithThisUnit = ignoreIntersectionsWithThisUnit;
			this.ignoreIntersectionsWithThisSecondUnit = ignoreIntersectionsWithThisSecondUnit;
			this.startX = startX;
			this.startY = startY;
			this.goal = goal;
			this.movementType = movementType;
			this.collisionSize = collisionSize;
			this.allowSmoothing = allowSmoothing;
			this.queueItem = queueItem;
			this.jobStarted = false;
		}
	}
}
