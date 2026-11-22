package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import com.etheller.interpreter.ast.util.CHandle;

public enum CPlayerColor implements CHandle {
	RED,
	BLUE,
	CYAN,
	PURPLE,
	YELLOW,
	ORANGE,
	GREEN,
	PINK,
	LIGHT_GRAY,
	LIGHT_BLUE,
	AQUA,
	BROWN,
	MAROON, // 1.32
	NAVY,
	TURQUOISE,
	VIOLET,
	WHEAT,
	PEACH,
	MINT,
	LAVENDER,
	COAL,
	SNOW,
	EMERALD,
	PEANUT,
	// warsmash-nx: the four neutral slots (24-27). common.j names no constant for them, but WC3 gives
	// their players these colours (GetPlayerColor, ConvertPlayerColor) and ships TeamColor24-27; without
	// them GetPlayerColor(neutral passive) was null and a transmission from a neutral unit (OrcX01's
	// Mogrin) threw in MeleeUI.setCinematicScene.
	NEUTRAL_24,
	NEUTRAL_25,
	NEUTRAL_26,
	NEUTRAL_27;

	public static CPlayerColor[] VALUES = values();

	public static CPlayerColor getColorByIndex(final int index) {
		if ((index >= 0) && (index < VALUES.length)) {
			return VALUES[index];
		}
		return null;
	}

	@Override
	public int getHandleId() {
		return ordinal();
	}
}
