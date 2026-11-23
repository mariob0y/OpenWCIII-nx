package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.etheller.warsmash.util.ParallelJobs;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * warsmash-nx: the units' part of the once-a-second fog of war update (CSimulation.updateFogOfWar).
 * The same cells with the same tests as CUnit.updateFogOfWar and
 * CPlayerFogOfWar.checkCardinalVision/checkDiagonalVision, so the same result byte for byte, but:
 * <ul>
 * <li>every float coordinate the tests use is turned into its terrain and pathing cell once per row
 * and column, not three to five times per tested cell through the render controller;</li>
 * <li>the terrain's cliff level, ramp and water flags are read from a table sampled once, the
 * players' grids are copied into arrays for the pass and back, and the vision blockers are read
 * straight from the pathing grid's array: no calls per cell;</li>
 * <li>the units run on the worker threads in waves. A unit only reads and writes its own player's
 * grid, and only inside the square of cells around it; where squares overlap, the order matters (a
 * cell one unit made visible lets the next one see past it). So, going through the units in the
 * simulation's order, a unit's wave is one more than the last wave that touched any block of 4x4
 * cells under its square: units of one wave never overlap, and each runs after every earlier unit it
 * overlaps. The grid is one flat array, so a square over the map's left or right edge goes on in the
 * next or previous row; such a square counts as the whole width and one more row on each side.
 * A base's buildings all overlap, so there are dozens of waves; whether handing them out beats one
 * thread is measured at the start (runWaves).</li>
 * </ul>
 * Unit state (sight, position, detectors) is read on the game thread first; the jobs only read the
 * pathing grid and touch their own player's grid arrays. On the console the old pass took ~200 ms of
 * one frame every second (OrcX01 profile, 2026-11-23).
 * <p>
 * PC check: NX_FOG_VERIFY=1 also runs the old pass on the same state and compares every byte of
 * every player's grids (FOGVERIFY lines).
 */
public final class UnitVision {
	private static final boolean VERIFY = System.getenv("NX_FOG_VERIFY") != null;
	private static final int STEP = CPlayerFogOfWar.GRID_STEP;
	private static final int BLOCK = 4;

	// Terrain cliff level, ramp and water per terrain cell, as Terrain.getCorner gives them.
	private float offsetX;
	private float offsetY;
	private int columns;
	private int rows;
	private int[] cliffLevel;
	private byte[] terrainFlags; // 1 ramp, 2 water
	private int tableState; // 0 not built, 1 usable, -1 unusable (the old pass runs)

	private final SeeingUnits seeing = new SeeingUnits();
	private Scratch[] scratch = new Scratch[0];

	// Waves of this pass: wave w's units are byWave[waveStart[w]] .. byWave[waveStart[w] + waveLength[w] - 1].
	private int waveCount;
	private int currentWave;
	private int[] wave = new int[0];
	private int[] byWave = new int[0];
	private int[] waveStart = new int[0];
	private int[] waveLength = new int[0];
	private int[][] blockWaves = new int[0][]; // per player: the next free wave of each block
	private boolean[] playerTouched = new boolean[0];

	// Per pass, read on the game thread.
	private CSimulation game;
	private PathingGrid pathingGrid;
	private boolean maskedVisible;
	private boolean foggedVisible;
	private short[] overlay; // PathingGrid.isCellBlockVision's array
	private int overlayWidth;
	private int blockVisionFlag;

	// Per player, for the pass: its grids copied out of the buffers, worked on, copied back.
	private byte[][] fogCells = new byte[0][];
	private byte[][] detectionCells = new byte[0][];
	private boolean[] playerSeeing = new boolean[0];
	private CPlayerFogOfWar[] playerFog = new CPlayerFogOfWar[0];

	/** Waves this small run on the game thread: handing them out would cost more than it saves. */
	private static final int SMALL_WAVE = 6;

	private final ParallelJobs.Job job = (index, worker) -> runUnit(
			this.byWave[this.waveStart[this.currentWave] + index], this.scratch[worker]);

	/**
	 * Runs the units' vision for every player. False if it cannot (the terrain table did not line up
	 * with the terrain), and then the caller runs the old pass.
	 */
	public boolean run(final CSimulation game, final List<CUnit> units) {
		if (this.tableState == 0) {
			buildTable(game);
		}
		if (this.tableState < 0) {
			return false;
		}
		this.game = game;
		this.pathingGrid = game.getPathingGrid();
		this.maskedVisible = game.getFogStateFromSettings(CFogState.MASKED.getMask()) == 0;
		this.foggedVisible = game.getFogStateFromSettings(CFogState.FOGGED.getMask()) == 0;
		this.overlay = this.pathingGrid.getDynamicPathingOverlay();
		this.overlayWidth = this.pathingGrid.getWidth();
		this.blockVisionFlag = PathingGrid.PathingFlags.BLOCKVISION;
		collect(game, units);
		waves();
		ensureScratch();
		if (!VERIFY) {
			runWaves();
		}
		else {
			verify(game, units);
		}
		this.game = null;
		return true;
	}

	/** Copies the seeing players' grids out of their buffers (game thread). */
	private void copyOut() {
		for (int p = 0; p < this.playerSeeing.length; p++) {
			if (this.playerSeeing[p]) {
				final CPlayerFogOfWar fog = this.playerFog[p];
				this.fogCells[p] = copyInto(fog.fogBuffer(), this.fogCells[p]);
				this.detectionCells[p] = copyInto(fog.detectionBuffer(), this.detectionCells[p]);
			}
		}
	}

	/** Copies the seeing players' grids back into their buffers (game thread). */
	private void copyBack() {
		for (int p = 0; p < this.playerSeeing.length; p++) {
			if (this.playerSeeing[p]) {
				final CPlayerFogOfWar fog = this.playerFog[p];
				restore(fog.fogBuffer(), this.fogCells[p]);
				restore(fog.detectionBuffer(), this.detectionCells[p]);
			}
		}
	}

	private static byte[] copyInto(final ByteBuffer buffer, byte[] bytes) {
		if ((bytes == null) || (bytes.length != buffer.capacity())) {
			bytes = new byte[buffer.capacity()];
		}
		final ByteBuffer view = buffer.duplicate();
		view.clear();
		view.get(bytes);
		return bytes;
	}

	private void ensureScratch() {
		final int slots = ParallelJobs.threadSlots();
		if (this.scratch.length < slots) {
			final Scratch[] grown = new Scratch[slots];
			for (int i = 0; i < slots; i++) {
				grown[i] = i < this.scratch.length ? this.scratch[i] : new Scratch();
			}
			this.scratch = grown;
		}
	}

	// Waves on the workers, or every unit on the game thread: whichever is faster on this machine.
	// The first TRIALS passes alternate and are timed, then the faster one stays (logged). The result
	// is the same either way.
	private static final int TRIALS = 16;
	private int trials;
	private long parallelNanos;
	private long serialNanos;
	private boolean parallel = true;

	private void runWaves() {
		final boolean timed = this.trials < TRIALS;
		final boolean useWorkers = timed ? ((this.trials & 1) == 0) : this.parallel;
		final long start = timed ? System.nanoTime() : 0;
		runWaves(useWorkers);
		if (timed) {
			final long nanos = System.nanoTime() - start;
			if (this.trials >= 2) { // the first two warm up
				if (useWorkers) {
					this.parallelNanos += nanos;
				}
				else {
					this.serialNanos += nanos;
				}
			}
			if (++this.trials == TRIALS) {
				this.parallel = this.parallelNanos < this.serialNanos;
				final int runs = (TRIALS - 2) / 2;
				System.out.println("UnitVision: " + (this.parallel ? "worker threads" : "game thread") + " (avg "
						+ (this.parallelNanos / runs / 1000) + " us on the workers, " + (this.serialNanos / runs / 1000)
						+ " us on the game thread)");
			}
		}
	}

	private void runWaves(final boolean useWorkers) {
		copyOut();
		try {
			for (int w = 0; w < this.waveCount; w++) {
				final int length = this.waveLength[w];
				if (!useWorkers || (length <= SMALL_WAVE)) {
					for (int k = this.waveStart[w], end = k + length; k < end; k++) {
						runUnit(this.byWave[k], this.scratch[0]);
					}
				}
				else {
					this.currentWave = w;
					ParallelJobs.parallelFor(length, this.job);
				}
			}
		}
		finally {
			copyBack();
		}
	}

	/** The units that see, in the simulation's order, with what the old pass reads of them. */
	private void collect(final CSimulation game, final List<CUnit> units) {
		final SeeingUnits seeing = this.seeing;
		seeing.count = 0;
		seeing.detectorCount = 0;
		Arrays.fill(this.playerSeeing, false);
		final boolean day = game.isDay();
		for (int i = 0, n = units.size(); i < n; i++) {
			final CUnit unit = units.get(i);
			if (unit.isDead() || unit.isHidden()) {
				continue;
			}
			final float sightRadius = unit.getSightRadius(game, day);
			if (!(sightRadius > 0)) {
				continue;
			}
			final int playerIndex = unit.getPlayerIndex();
			final CPlayer player = game.getPlayer(playerIndex);
			if (player == null) {
				continue;
			}
			final float x = unit.getX();
			final float y = unit.getY();
			final boolean flying = unit.getMovementType() == MovementType.FLY;
			if (playerIndex >= this.playerSeeing.length) {
				final int size = playerIndex + 1;
				this.playerSeeing = Arrays.copyOf(this.playerSeeing, size);
				this.playerFog = Arrays.copyOf(this.playerFog, size);
				this.fogCells = Arrays.copyOf(this.fogCells, size);
				this.detectionCells = Arrays.copyOf(this.detectionCells, size);
			}
			this.playerSeeing[playerIndex] = true;
			this.playerFog[playerIndex] = player.getFogOfWar();
			seeing.add(player.getFogOfWar(), playerIndex, x, y, sightRadius, flying,
					flying ? Integer.MAX_VALUE : game.getTerrainHeight(x, y), unit.getDetectorLevels());
		}
	}

	/** Puts each unit in its wave (see the class comment). */
	private void waves() {
		final SeeingUnits seeing = this.seeing;
		final int n = seeing.count;
		if (this.wave.length < n) {
			final int size = Math.max(n, this.wave.length * 2);
			this.wave = new int[size];
			this.byWave = new int[size];
			this.waveStart = new int[size];
			this.waveLength = new int[size];
		}
		for (int p = 0; p < this.playerTouched.length; p++) {
			if (this.playerTouched[p]) {
				Arrays.fill(this.blockWaves[p], 0);
				this.playerTouched[p] = false;
			}
		}
		final PathingGrid pathing = this.pathingGrid;
		this.waveCount = 0;
		for (int i = 0; i < n; i++) {
			final CPlayerFogOfWar fog = seeing.fog[i];
			final int fogWidth = fog.getWidth();
			final int fogHeight = fog.getHeight();
			final int blocksX = ((fogWidth - 1) / BLOCK) + 1;
			final int blocksY = ((fogHeight - 1) / BLOCK) + 1;
			final int[] blocks = blocksFor(seeing.player[i], blocksX * blocksY);

			final int myXi = pathing.getFogOfWarIndexX(seeing.x[i]);
			final int myYi = pathing.getFogOfWarIndexY(seeing.y[i]);
			final int spanX = pathing.getFogOfWarIndexX(seeing.x[i] + seeing.radius[i]) - myXi;
			final int spanY = pathing.getFogOfWarIndexY(seeing.y[i] + seeing.radius[i]) - myYi;
			final int span = Math.max(Math.max(spanX, spanY), 0);
			int minX = myXi - span;
			int maxX = myXi + span;
			int minY = myYi - span;
			int maxY = myYi + span;
			if ((minX < 0) || (maxX >= fogWidth)) {
				minX = 0;
				maxX = fogWidth - 1;
				minY -= 1;
				maxY += 1;
			}
			minY = Math.max(minY, 0);
			maxY = Math.min(maxY, fogHeight - 1);
			if (minY > maxY) {
				// nothing of it on the grid: take the whole grid rather than reason about it
				minY = 0;
				maxY = fogHeight - 1;
			}
			final int bx0 = minX / BLOCK;
			final int bx1 = maxX / BLOCK;
			final int by0 = minY / BLOCK;
			final int by1 = maxY / BLOCK;
			int w = 0;
			for (int by = by0; by <= by1; by++) {
				for (int bx = bx0; bx <= bx1; bx++) {
					w = Math.max(w, blocks[(by * blocksX) + bx]);
				}
			}
			for (int by = by0; by <= by1; by++) {
				for (int bx = bx0; bx <= bx1; bx++) {
					blocks[(by * blocksX) + bx] = w + 1;
				}
			}
			this.wave[i] = w;
			while (this.waveCount <= w) {
				this.waveLength[this.waveCount++] = 0;
			}
			this.waveLength[w]++;
		}
		int at = 0;
		for (int w = 0; w < this.waveCount; w++) {
			this.waveStart[w] = at;
			at += this.waveLength[w];
			this.waveLength[w] = 0;
		}
		for (int i = 0; i < n; i++) {
			final int w = this.wave[i];
			this.byWave[this.waveStart[w] + this.waveLength[w]++] = i;
		}
	}

	private int[] blocksFor(final int player, final int size) {
		if (player >= this.blockWaves.length) {
			this.blockWaves = Arrays.copyOf(this.blockWaves, player + 1);
			this.playerTouched = Arrays.copyOf(this.playerTouched, player + 1);
		}
		int[] blocks = this.blockWaves[player];
		if ((blocks == null) || (blocks.length != size)) {
			blocks = new int[size];
			this.blockWaves[player] = blocks;
		}
		this.playerTouched[player] = true;
		return blocks;
	}

	/** CUnit.updateFogOfWar from the point it has its fog grid. */
	private void runUnit(final int u, final Scratch s) {
		final SeeingUnits units = this.seeing;
		final CPlayerFogOfWar fog = units.fog[u];
		final byte[] fogBuffer = this.fogCells[units.player[u]];
		final byte[] detectionBuffer = this.detectionCells[units.player[u]];
		final int fogWidth = fog.getWidth();
		final int capacity = fogBuffer.length;
		final PathingGrid pathing = this.pathingGrid;
		final float myX = units.x[u];
		final float myY = units.y[u];
		final float sightRadius = units.radius[u];
		final int myZ = units.z[u];
		final boolean flying = units.flying[u];
		final int detectorStart = units.detectorStart[u];
		final int detectorEnd = detectorStart + units.detectorLength[u];
		final int[] detectors = units.detectors;
		final float radSq = (sightRadius * sightRadius) / (STEP * STEP);

		byte detections = 0;
		for (int d = detectorStart; d < detectorEnd; d++) {
			if ((detectors[d] >> 8) > 0) {
				detections |= (byte) detectors[d];
			}
		}
		final int myXi = pathing.getFogOfWarIndexX(myX);
		final int myYi = pathing.getFogOfWarIndexY(myY);
		setDetecting(fogBuffer, detectionBuffer, capacity, (myYi * fogWidth) + myXi, detections);

		final int spanX = pathing.getFogOfWarIndexX(myX + sightRadius) - myXi;
		final int spanY = pathing.getFogOfWarIndexY(myY + sightRadius) - myYi;
		final int span = Math.max(Math.max(spanX, spanY), 0);
		s.prepare(span + 1, myX, myY, this.offsetX, this.offsetY, pathing);
		final int[] tcXM = s.tcXM, tcXP = s.tcXP, tcYM = s.tcYM, tcYP = s.tcYP;
		final int[] pcXM = s.pcXM, pcXP = s.pcXP, pcXMP = s.pcXMP, pcXPM = s.pcXPM;
		final int[] prYM = s.prYM, prYP = s.prYP, prYMP = s.prYMP, prYPM = s.prYPM;

		for (int a = 1; a <= Math.max(spanY, spanX); a++) {
			detections = 0;
			for (int d = detectorStart; d < detectorEnd; d++) {
				if ((detectors[d] >> 8) > a) {
					detections |= (byte) detectors[d];
				}
			}
			if ((a * a) <= radSq) {
				// toward -y, +y, -x, +x, as CUnit.updateFogOfWar calls checkCardinalVision
				if (seesCell(flying, myZ, tcXM[0], tcYM[a])
						&& (flying || !blocked(pcXM[0], prYM[a - 1]))
						&& visible(fogBuffer, capacity, ((((myYi - a) + 1) * fogWidth) + myXi))) {
					setDetecting(fogBuffer, detectionBuffer, capacity, ((myYi - a) * fogWidth) + myXi, detections);
				}
				if (seesCell(flying, myZ, tcXM[0], tcYP[a])
						&& (flying || !blocked(pcXM[0], prYP[a - 1]))
						&& visible(fogBuffer, capacity, ((((myYi + a) - 1) * fogWidth) + myXi))) {
					setDetecting(fogBuffer, detectionBuffer, capacity, ((myYi + a) * fogWidth) + myXi, detections);
				}
				if (seesCell(flying, myZ, tcXM[a], tcYM[0])
						&& (flying || !blocked(pcXM[a - 1], prYM[0]))
						&& visible(fogBuffer, capacity, (myYi * fogWidth) + ((myXi - a) + 1))) {
					setDetecting(fogBuffer, detectionBuffer, capacity, (myYi * fogWidth) + (myXi - a), detections);
				}
				if (seesCell(flying, myZ, tcXP[a], tcYM[0])
						&& (flying || !blocked(pcXP[a - 1], prYM[0]))
						&& visible(fogBuffer, capacity, (myYi * fogWidth) + ((myXi + a) - 1))) {
					setDetecting(fogBuffer, detectionBuffer, capacity, (myYi * fogWidth) + (myXi + a), detections);
				}
			}
		}

		for (int y = 1; y <= spanY; y++) {
			for (int x = 1; x <= spanX; x++) {
				final float distance = (x * x) + (y * y);
				detections = 0;
				for (int d = detectorStart; d < detectorEnd; d++) {
					if (((detectors[d] >> 8) * (detectors[d] >> 8)) > distance) {
						detections |= (byte) detectors[d];
					}
				}
				if (distance <= radSq) {
					// (-x,-y), (-x,+y), (+x,-y), (+x,+y), as CUnit.updateFogOfWar calls checkDiagonalVision
					diagonal(fogBuffer, detectionBuffer, capacity, fogWidth, pathing, detections, flying, myZ, x, y,
							myXi - x, myYi - y, 1, 1, tcXM[x], tcYM[y], pcXM[x], prYM[y], pcXMP[x], prYMP[y]);
					diagonal(fogBuffer, detectionBuffer, capacity, fogWidth, pathing, detections, flying, myZ, x, y,
							myXi - x, myYi + y, 1, -1, tcXM[x], tcYP[y], pcXM[x], prYP[y], pcXMP[x], prYPM[y]);
					diagonal(fogBuffer, detectionBuffer, capacity, fogWidth, pathing, detections, flying, myZ, x, y,
							myXi + x, myYi - y, -1, 1, tcXP[x], tcYM[y], pcXP[x], prYM[y], pcXPM[x], prYMP[y]);
					diagonal(fogBuffer, detectionBuffer, capacity, fogWidth, pathing, detections, flying, myZ, x, y,
							myXi + x, myYi + y, -1, -1, tcXP[x], tcYP[y], pcXP[x], prYP[y], pcXPM[x], prYPM[y]);
				}
			}
		}
	}

	/**
	 * CPlayerFogOfWar.checkDiagonalVision: cell (dx, dy), its parent toward the unit (dx + px, dy + py);
	 * tcX / tcY the terrain cell of the cell's coordinates; pcX / prY the pathing column / row of the
	 * cell's coordinates (dxf, dyf), pcXParent / prYParent of the parent's (dxfp, dyfp).
	 */
	private void diagonal(final byte[] fogBuffer, final byte[] detectionBuffer, final int capacity,
			final int fogWidth, final PathingGrid pathing, final byte detections, final boolean flying, final int myZ,
			final int x, final int y, final int dx, final int dy, final int px, final int py, final int tcX,
			final int tcY, final int pcX, final int prY, final int pcXParent, final int prYParent) {
		final int dxp = dx + px;
		final int dyp = dy + py;
		if (seesCell(flying, myZ, tcX, tcY) && (flying || !blocked(pcXParent, prYParent))
				&& visible(fogBuffer, capacity, (dyp * fogWidth) + dxp)
				&& ((x == y)
						|| ((x > y) && visible(fogBuffer, capacity, (dy * fogWidth) + dxp)
								&& (flying || !blocked(pcXParent, prY)))
						|| ((x < y) && visible(fogBuffer, capacity, (dyp * fogWidth) + dx)
								&& (flying || !blocked(pcX, prYParent))))) {
			setDetecting(fogBuffer, detectionBuffer, capacity, (dy * fogWidth) + dx, detections);
		}
	}

	/** PathingGrid.isCellBlockVision. */
	private boolean blocked(final int cellX, final int cellY) {
		final int index = (cellY * this.overlayWidth) + cellX;
		if ((index < 0) || (index >= this.overlay.length)) {
			return false;
		}
		return (this.overlay[index] & this.blockVisionFlag) != 0;
	}

	/** The terrain test of checkCardinalVision/checkDiagonalVision for a terrain cell. */
	private boolean seesCell(final boolean flying, final int myZ, final int column, final int row) {
		if (flying) {
			return true;
		}
		if ((column < 0) || (column >= this.columns) || (row < 0) || (row >= this.rows)) {
			// no corner there: not water, cliff level 999, not a ramp
			return myZ >= 999;
		}
		final int index = (row * this.columns) + column;
		final int flags = this.terrainFlags[index];
		final int cliff = this.cliffLevel[index];
		return ((flags & 2) != 0) || (myZ > cliff) || (((flags & 1) == 0) && (myZ == cliff));
	}

	/** CPlayerFogOfWar.isVisible(game, x, y): outside the buffer counts as visible (getState gives 0). */
	private boolean visible(final byte[] fogBuffer, final int capacity, final int index) {
		if ((index < 0) || (index >= capacity)) {
			return true;
		}
		final byte raw = fogBuffer[index];
		if (raw == 0) {
			return true;
		}
		if (raw == CFogState.FOGGED.getMask()) {
			return this.foggedVisible;
		}
		if (raw == CFogState.MASKED.getMask()) {
			return this.maskedVisible;
		}
		return this.game.getFogStateFromSettings(raw) == 0;
	}

	private static void setDetecting(final byte[] fogBuffer, final byte[] detectionBuffer,
			final int capacity, final int index, final byte detections) {
		if ((index >= 0) && (index < capacity)) {
			fogBuffer[index] = 0; // CFogState.VISIBLE
			detectionBuffer[index] |= detections;
		}
	}

	/**
	 * Samples the terrain through the same calls the old pass makes, at each terrain cell's middle.
	 * Usable only if the cells line up: the last column and row answer, the next ones do not.
	 */
	private void buildTable(final CSimulation game) {
		final PathingGrid pathing = game.getPathingGrid();
		this.offsetX = pathing.getCenterOffsetX();
		this.offsetY = pathing.getCenterOffsetY();
		this.columns = pathing.getWidth() / CPlayerFogOfWar.PATHING_RATIO;
		this.rows = pathing.getHeight() / CPlayerFogOfWar.PATHING_RATIO;
		this.tableState = -1;
		if ((this.columns <= 0) || (this.rows <= 0) || (sampleCliff(game, this.columns - 1, this.rows - 1) == 999)
				|| (sampleCliff(game, this.columns, 0) != 999) || (sampleCliff(game, 0, this.rows) != 999)) {
			System.err.println("UnitVision: terrain cells do not line up, the old fog pass runs");
			return;
		}
		this.cliffLevel = new int[this.columns * this.rows];
		this.terrainFlags = new byte[this.columns * this.rows];
		for (int row = 0; row < this.rows; row++) {
			for (int column = 0; column < this.columns; column++) {
				final float x = middle(this.offsetX, column);
				final float y = middle(this.offsetY, row);
				final int index = (row * this.columns) + column;
				this.cliffLevel[index] = game.getTerrainHeight(x, y);
				this.terrainFlags[index] = (byte) ((game.isTerrainRomp(x, y) ? 1 : 0)
						| (game.isTerrainWater(x, y) ? 2 : 0));
			}
		}
		this.tableState = 1;
	}

	private int sampleCliff(final CSimulation game, final int column, final int row) {
		return game.getTerrainHeight(middle(this.offsetX, column), middle(this.offsetY, row));
	}

	private static float middle(final float offset, final int cell) {
		return offset + (cell * 128) + 64;
	}

	/** Terrain.getCorner's cell for a coordinate. */
	static int terrainCell(final float v, final float offset) {
		return (int) ((v - offset) / 128.0f);
	}

	private void verify(final CSimulation game, final List<CUnit> units) {
		final List<CPlayer> players = new ArrayList<>();
		for (int i = 0; game.getPlayer(i) != null; i++) {
			players.add(game.getPlayer(i));
		}
		final byte[][] before = snapshot(players);
		final long t0 = System.nanoTime();
		runWaves();
		final long t1 = System.nanoTime();
		final byte[][] fast = snapshot(players);
		restore(players, before);
		// the same new pass on this thread alone
		final long s0 = System.nanoTime();
		copyOut();
		for (int k = 0; k < this.seeing.count; k++) {
			runUnit(this.byWave[k], this.scratch[0]);
		}
		copyBack();
		final long s1 = System.nanoTime();
		int largest = 0;
		for (int w = 0; w < this.waveCount; w++) {
			largest = Math.max(largest, this.waveLength[w]);
		}
		restore(players, before);
		final long t2 = System.nanoTime();
		for (int i = 0, n = units.size(); i < n; i++) {
			units.get(i).updateFogOfWar(game);
		}
		final long t3 = System.nanoTime();
		final byte[][] old = snapshot(players);
		int differing = 0;
		int changed = 0;
		for (int i = 0; i < old.length; i++) {
			for (int c = 0; c < old[i].length; c++) {
				if (old[i][c] != fast[i][c]) {
					differing++;
				}
				if (((i & 1) == 0) && (old[i][c] != before[i][c])) {
					changed++;
				}
			}
		}
		System.err.println("FOGVERIFY " + (differing == 0 ? "same" : "DIFFERENT") + " differing " + differing
				+ " cells set " + changed + " units " + this.seeing.count + " waves " + this.waveCount + " largest "
				+ largest + " new " + ((t1 - t0) / 1000) + " us, one thread " + ((s1 - s0) / 1000) + " us, old "
				+ ((t3 - t2) / 1000) + " us");
	}

	/** Every player's fog and detection bytes (fog at 2i, detection at 2i + 1). */
	private static byte[][] snapshot(final List<CPlayer> players) {
		final byte[][] copy = new byte[players.size() * 2][];
		for (int i = 0; i < players.size(); i++) {
			copy[i * 2] = copy(players.get(i).getFogOfWar().fogBuffer());
			copy[(i * 2) + 1] = copy(players.get(i).getFogOfWar().detectionBuffer());
		}
		return copy;
	}

	private static void restore(final List<CPlayer> players, final byte[][] bytes) {
		for (int i = 0; i < players.size(); i++) {
			restore(players.get(i).getFogOfWar().fogBuffer(), bytes[i * 2]);
			restore(players.get(i).getFogOfWar().detectionBuffer(), bytes[(i * 2) + 1]);
		}
	}

	private static byte[] copy(final ByteBuffer buffer) {
		final byte[] bytes = new byte[buffer.capacity()];
		final ByteBuffer view = buffer.duplicate();
		view.clear();
		view.get(bytes);
		return bytes;
	}

	private static void restore(final ByteBuffer buffer, final byte[] bytes) {
		final ByteBuffer view = buffer.duplicate();
		view.clear();
		view.put(bytes);
	}

	/** The units that see in this pass, in the simulation's order. */
	private static final class SeeingUnits {
		int count;
		CPlayerFogOfWar[] fog = new CPlayerFogOfWar[64];
		int[] player = new int[64];
		float[] x = new float[64];
		float[] y = new float[64];
		float[] radius = new float[64];
		int[] z = new int[64];
		boolean[] flying = new boolean[64];
		int[] detectorStart = new int[64];
		int[] detectorLength = new int[64];
		int[] detectors = new int[16];
		int detectorCount;

		void add(final CPlayerFogOfWar playerFog, final int playerIndex, final float ux, final float uy,
				final float sightRadius, final boolean fly, final int uz, final List<Integer> detectorLevels) {
			if (this.count == this.x.length) {
				final int n = this.count * 2;
				this.fog = Arrays.copyOf(this.fog, n);
				this.player = Arrays.copyOf(this.player, n);
				this.x = Arrays.copyOf(this.x, n);
				this.y = Arrays.copyOf(this.y, n);
				this.radius = Arrays.copyOf(this.radius, n);
				this.z = Arrays.copyOf(this.z, n);
				this.flying = Arrays.copyOf(this.flying, n);
				this.detectorStart = Arrays.copyOf(this.detectorStart, n);
				this.detectorLength = Arrays.copyOf(this.detectorLength, n);
			}
			final int levels = detectorLevels.size();
			if ((this.detectorCount + levels) > this.detectors.length) {
				this.detectors = Arrays.copyOf(this.detectors,
						Math.max(this.detectors.length * 2, this.detectorCount + levels));
			}
			this.detectorStart[this.count] = this.detectorCount;
			this.detectorLength[this.count] = levels;
			for (int i = 0; i < levels; i++) {
				this.detectors[this.detectorCount++] = detectorLevels.get(i);
			}
			this.fog[this.count] = playerFog;
			this.player[this.count] = playerIndex;
			this.x[this.count] = ux;
			this.y[this.count] = uy;
			this.radius[this.count] = sightRadius;
			this.z[this.count] = uz;
			this.flying[this.count] = fly;
			this.count++;
		}
	}

	/**
	 * One thread's cells for the unit it is on, index k = k grid steps from the unit. tc: terrain
	 * column / row; pc / pr: pathing column / row. XM = myX - k*step, XP = myX + k*step,
	 * XMP = (myX - k*step) + step, XPM = (myX + k*step) - step: the exact float expressions the old
	 * pass hands to the terrain and pathing lookups.
	 */
	private static final class Scratch {
		int[] tcXM = new int[0], tcXP, tcYM, tcYP;
		int[] pcXM, pcXP, pcXMP, pcXPM;
		int[] prYM, prYP, prYMP, prYPM;

		void prepare(final int n, final float myX, final float myY, final float offsetX, final float offsetY,
				final PathingGrid pathing) {
			if (this.tcXM.length < n) {
				final int size = Math.max(n, 32);
				this.tcXM = new int[size];
				this.tcXP = new int[size];
				this.tcYM = new int[size];
				this.tcYP = new int[size];
				this.pcXM = new int[size];
				this.pcXP = new int[size];
				this.pcXMP = new int[size];
				this.pcXPM = new int[size];
				this.prYM = new int[size];
				this.prYP = new int[size];
				this.prYMP = new int[size];
				this.prYPM = new int[size];
			}
			for (int k = 0; k < n; k++) {
				final int d = k * STEP;
				final float xm = myX - d;
				final float xp = myX + d;
				final float ym = myY - d;
				final float yp = myY + d;
				this.tcXM[k] = terrainCell(xm, offsetX);
				this.tcXP[k] = terrainCell(xp, offsetX);
				this.tcYM[k] = terrainCell(ym, offsetY);
				this.tcYP[k] = terrainCell(yp, offsetY);
				this.pcXM[k] = pathing.getCellX(xm);
				this.pcXP[k] = pathing.getCellX(xp);
				this.pcXMP[k] = pathing.getCellX(xm + STEP);
				this.pcXPM[k] = pathing.getCellX(xp - STEP);
				this.prYM[k] = pathing.getCellY(ym);
				this.prYP[k] = pathing.getCellY(yp);
				this.prYMP[k] = pathing.getCellY(ym + STEP);
				this.prYPM[k] = pathing.getCellY(yp - STEP);
			}
		}
	}
}
