package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

public class CFogModifierJassSingle implements CFogModifierJass {
	private final int playerIndex;
	private final CFogModifier modifier;

	public CFogModifierJassSingle(int playerIndex, CFogModifier modifier) {
		this.playerIndex = playerIndex;
		this.modifier = modifier;
	}

	@Override
	public void setEnabled(boolean flag) {
		this.modifier.setEnabled(flag);
	}

	@Override
	public void destroy(CSimulation game) {
		// warsmash-nx: applied once more first. Maps "wipe visibility" by creating an entire-map visible
		// modifier and destroying it at once (OrcX03b after its intro): in WC3 that leaves the map explored
		// (fogged, not black-masked). Here a modifier only acted on the next vision update, so it never did.
		final CPlayer player = game.getPlayer(this.playerIndex);
		this.modifier.update(game, player, game.getPathingGrid(), player.getFogOfWar());
		player.removeFogModifer(game, this.modifier);
	}

}
