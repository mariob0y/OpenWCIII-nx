package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision;

import java.nio.ByteBuffer;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CFogMaskSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

public class CPlayerFogOfWar {
	public static final int PATHING_RATIO = 4;
	public static final int GRID_STEP = PATHING_RATIO * 32;
	private final int width;
	private final int height;
	private final ByteBuffer fogOfWarBuffer;
	private final ByteBuffer detectionBuffer;

	public CPlayerFogOfWar(final PathingGrid pathingGrid) {
		this.width = (pathingGrid.getWidth() / PATHING_RATIO) + 1;
		this.height = (pathingGrid.getHeight() / PATHING_RATIO) + 1;
		final int fogOfWarBufferLen = this.width * this.height;
		this.fogOfWarBuffer = ByteBuffer.allocateDirect(fogOfWarBufferLen);
		this.detectionBuffer = ByteBuffer.allocateDirect(fogOfWarBufferLen);
		this.fogOfWarBuffer.clear();
		while (this.fogOfWarBuffer.hasRemaining()) {
			this.fogOfWarBuffer.put((byte) -1);
		}
		this.fogOfWarBuffer.clear();
		this.detectionBuffer.clear();
		while (this.detectionBuffer.hasRemaining()) {
			this.detectionBuffer.put((byte) 0);
		}
		this.detectionBuffer.clear();
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public ByteBuffer getFogOfWarBuffer() {
		return this.fogOfWarBuffer;
	}

	private byte getState(CFogMaskSettings fogMaskSettings, final PathingGrid pathingGrid, final float x,
			final float y) {
		final int indexX = pathingGrid.getFogOfWarIndexX(x);
		final int indexY = pathingGrid.getFogOfWarIndexY(y);
		return getState(fogMaskSettings, indexX, indexY);
	}

	private byte getState(CFogMaskSettings fogMaskSettings, final int indexX, final int indexY) {
		final int index = (indexY * getWidth()) + indexX;
		if ((index >= 0) && (index < this.fogOfWarBuffer.capacity())) {
			return fogMaskSettings.getFogStateFromSettings(this.fogOfWarBuffer.get(index));
		}
		return 0;
	}

	private byte getDetectionState(final int indexX, final int indexY) {
		final int index = (indexY * getWidth()) + indexX;
		if ((index >= 0) && (index < this.detectionBuffer.capacity())) {
			return this.detectionBuffer.get(index);
		}
		return 0;
	}

	public CFogState getFogState(CFogMaskSettings fogMaskSettings, final PathingGrid pathingGrid, final float x,
			final float y) {
		return CFogState.getByMask(getState(fogMaskSettings, pathingGrid, x, y));
	}

	public CFogState getFogState(CFogMaskSettings fogMaskSettings, final int indexX, final int indexY) {
		return CFogState.getByMask(getState(fogMaskSettings, indexX, indexY));
	}

	public boolean isVisible(CFogMaskSettings fogMaskSettings, final PathingGrid pathingGrid, final float x,
			final float y) {
		return getState(fogMaskSettings, pathingGrid, x, y) == 0;
	}

	public boolean isVisible(CFogMaskSettings fogMaskSettings, final int indexX, final int indexY) {
		return getState(fogMaskSettings, indexX, indexY) == 0;
	}

	public boolean isDetecting(CFogMaskSettings fogMaskSettings, PathingGrid pathingGrid, float x, float y,
			byte invisLevels) {
		final int indexX = pathingGrid.getFogOfWarIndexX(x);
		final int indexY = pathingGrid.getFogOfWarIndexY(y);
		return isVisible(fogMaskSettings, indexX, indexY)
				&& ((getDetectionState(indexX, indexY) & invisLevels) == invisLevels);
	}

	public boolean isDetecting(CFogMaskSettings fogMaskSettings, final int indexX, final int indexY, byte invisLevels) {
		return isVisible(fogMaskSettings, indexX, indexY)
				&& ((getDetectionState(indexX, indexY) & invisLevels) == invisLevels);
	}

	private void setState(final PathingGrid pathingGrid, final float x, final float y, final byte fogOfWarState) {
		final int indexX = pathingGrid.getFogOfWarIndexX(x);
		final int indexY = pathingGrid.getFogOfWarIndexY(y);
		setState(indexX, indexY, fogOfWarState);
	}

	private void setState(final int indexX, final int indexY, final byte fogOfWarState) {
		final int writeIndex = (indexY * getWidth()) + indexX;
		if ((writeIndex >= 0) && (writeIndex < this.fogOfWarBuffer.capacity())) {
			this.fogOfWarBuffer.put(writeIndex, fogOfWarState);
		}
	}

	public void setVisible(final PathingGrid pathingGrid, final float x, final float y, final CFogState fogOfWarState) {
		setState(pathingGrid, x, y, fogOfWarState.getMask());
	}

	public void setVisible(final int indexX, final int indexY, final CFogState fogOfWarState) {
		setState(indexX, indexY, fogOfWarState.getMask());
	}

	public void setFogStateRect(PathingGrid pathingGrid, Rectangle rectangle, CFogState fogOfWarState) {
		final int xMin = pathingGrid.getFogOfWarIndexX((float) Math.floor(rectangle.x));
		final int yMin = pathingGrid.getFogOfWarIndexY((float) Math.floor(rectangle.y));
		final int xMax = pathingGrid.getFogOfWarIndexX((float) Math.ceil(rectangle.x + rectangle.width));
		final int yMax = pathingGrid.getFogOfWarIndexY((float) Math.ceil(rectangle.y + rectangle.height));
		for (int i = xMin; i <= xMax; i += 1) {
			for (int j = yMin; j <= yMax; j += 1) {
				setVisible(i, j, fogOfWarState);
			}
		}
	}

	public void setDetecting(final PathingGrid pathingGrid, final float x, final float y, final CFogState fogOfWarState,
			byte detectLevels) {
		final int indexX = pathingGrid.getFogOfWarIndexX(x);
		final int indexY = pathingGrid.getFogOfWarIndexY(y);
		setDetecting(indexX, indexY, fogOfWarState, detectLevels);
	}

	public void setDetecting(final int indexX, final int indexY, final CFogState fogOfWarState, byte detectLevels) {
		final int writeIndex = (indexY * getWidth()) + indexX;
		if ((writeIndex >= 0) && (writeIndex < this.fogOfWarBuffer.capacity())) {
			this.fogOfWarBuffer.put(writeIndex, fogOfWarState.getMask());
			this.detectionBuffer.put(writeIndex, (byte) (this.detectionBuffer.get(writeIndex) | detectLevels));
		}
	}

	public void checkCardinalVision(final CSimulation game, final PathingGrid pathingGrid, final boolean flying,
			final int dx, final int dy, final int dxp, final int dyp, final float dxf, final float dyf,
			final float dxfp, final float dyfp, final int myZ) {
		if ((flying || game.isTerrainWater(dxf, dyf) || (myZ > game.getTerrainHeight(dxf, dyf))
				|| (!game.isTerrainRomp(dxf, dyf) && (myZ == game.getTerrainHeight(dxf, dyf))))
				&& (flying || !pathingGrid.isBlockVision(dxfp, dyfp)) && this.isVisible(game, dxp, dyp)) {
			this.setVisible(dx, dy, CFogState.VISIBLE);
		}
	}

	public void checkCardinalVision(final CSimulation game, final PathingGrid pathingGrid, final byte detections,
			final boolean flying, final int dx, final int dy, final int dxp, final int dyp, final float dxf,
			final float dyf, final float dxfp, final float dyfp, final int myZ) {
		if ((flying || game.isTerrainWater(dxf, dyf) || (myZ > game.getTerrainHeight(dxf, dyf))
				|| (!game.isTerrainRomp(dxf, dyf) && (myZ == game.getTerrainHeight(dxf, dyf))))
				&& (flying || !pathingGrid.isBlockVision(dxfp, dyfp)) && this.isVisible(game, dxp, dyp)) {
			this.setDetecting(dx, dy, CFogState.VISIBLE, detections);
		}
	}

	public void checkDiagonalVision(final CSimulation game, final PathingGrid pathingGrid, final boolean flying,
			final int x, final int y, final int dx, final int dy, final int dxp, final int dyp, final float dxf,
			final float dyf, final float dxfp, final float dyfp, final int myZ) {
		if ((flying || game.isTerrainWater(dxf, dyf) || (myZ > game.getTerrainHeight(dxf, dyf))
				|| (!game.isTerrainRomp(dxf, dyf) && (myZ == game.getTerrainHeight(dxf, dyf))))
				&& (flying || !pathingGrid.isBlockVision(dxfp, dyfp)) && this.isVisible(game, dxp, dyp)
				&& ((x == y)
						|| ((x > y) && this.isVisible(game, dxp, dy)
								&& (flying || !pathingGrid.isBlockVision(dxfp, dyf)))
						|| ((x < y) && this.isVisible(game, dx, dyp)
								&& (flying || !pathingGrid.isBlockVision(dxf, dyfp))))) {
			this.setVisible(dx, dy, CFogState.VISIBLE);
		}
	}

	public void checkDiagonalVision(final CSimulation game, final PathingGrid pathingGrid, final byte detections,
			final boolean flying, final int x, final int y, final int dx, final int dy, final int dxp, final int dyp,
			final float dxf, final float dyf, final float dxfp, final float dyfp, final int myZ) {
		if ((flying || game.isTerrainWater(dxf, dyf) || (myZ > game.getTerrainHeight(dxf, dyf))
				|| (!game.isTerrainRomp(dxf, dyf) && (myZ == game.getTerrainHeight(dxf, dyf))))
				&& (flying || !pathingGrid.isBlockVision(dxfp, dyfp)) && this.isVisible(game, dxp, dyp)
				&& ((x == y)
						|| ((x > y) && this.isVisible(game, dxp, dy)
								&& (flying || !pathingGrid.isBlockVision(dxfp, dyf)))
						|| ((x < y) && this.isVisible(game, dx, dyp)
								&& (flying || !pathingGrid.isBlockVision(dxf, dyfp))))) {
			this.setDetecting(dx, dy, CFogState.VISIBLE, detections);
		}
	}

	/** warsmash-nx: scratch for convertVisibleToFogged (the simulation runs on one thread). */
	private static byte[] scratchCells = new byte[0];
	private static byte[] scratchZeros = new byte[0];

	/**
	 * warsmash-nx: copies the grid out, changes it in an array and copies it back, instead of three
	 * checked ByteBuffer calls per cell; it runs for every player (28) every second, and on the console
	 * it was ~200 ms a run (OrcX01 profile, 2026-11-22). Same result. Through duplicates, so the
	 * buffers' own position and limit are left alone.
	 */
	public void convertVisibleToFogged() {
		final int count = this.fogOfWarBuffer.capacity();
		if (scratchCells.length < count) {
			scratchCells = new byte[count];
			scratchZeros = new byte[count];
		}
		final byte[] cells = scratchCells;
		final ByteBuffer fog = this.fogOfWarBuffer.duplicate();
		fog.clear();
		fog.get(cells, 0, count);
		for (int i = 0; i < count; i++) {
			if (cells[i] == 0) {
				cells[i] = (byte) 127;
			}
		}
		fog.clear();
		fog.put(cells, 0, count);
		final ByteBuffer detection = this.detectionBuffer.duplicate();
		detection.clear();
		detection.put(scratchZeros, 0, count);
	}

	public void setFogStateRadius(PathingGrid pathingGrid, float myX, float myY, float radius, CFogState state) {
		final float radSq = radius * radius;
		setVisible(pathingGrid.getFogOfWarIndexX(myX), pathingGrid.getFogOfWarIndexY(myY), state);

		for (int y = 0; y <= (int) Math.floor(radius); y += 128) {
			for (int x = 0; x <= (int) Math.floor(radius); x += 128) {
				final float distance = (x * x) + (y * y);
				if (distance <= radSq) {
					setVisible(pathingGrid.getFogOfWarIndexX(myX - x), pathingGrid.getFogOfWarIndexY(myY - y), state);
					setVisible(pathingGrid.getFogOfWarIndexX(myX - x), pathingGrid.getFogOfWarIndexY(myY + y), state);
					setVisible(pathingGrid.getFogOfWarIndexX(myX + x), pathingGrid.getFogOfWarIndexY(myY - y), state);
					setVisible(pathingGrid.getFogOfWarIndexX(myX + x), pathingGrid.getFogOfWarIndexY(myY + y), state);
				}
			}
		}
	}
}
