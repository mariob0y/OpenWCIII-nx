package com.etheller.warsmash.util;

/**
 * warsmash-nx: a game pad's camera stick, written once a frame by the platform's input (the Switch 2
 * backend's SwitchInput: the left stick) and read by the game camera. Each axis is -1..1 past the
 * stick's dead zone, x to the right and y up (forward); 0 on platforms without a pad.
 */
public final class PadSticks {
	public static volatile float cameraX;
	public static volatile float cameraY;

	private PadSticks() {
	}
}
