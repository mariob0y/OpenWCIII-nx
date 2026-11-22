package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

public class CBuffTimedLife extends CBuffTimed {

	private final boolean explode;

	public CBuffTimedLife(final int handleId, final War3ID alias, final float duration, boolean explode) {
		super(handleId, alias, alias, duration);
		this.explode = explode;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		if (this.explode) {
			unit.setExplodesOnDeath(true);
			unit.setExplodesOnDeathBuffId(getAlias());
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.kill(game);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit caster) {
		// warsmash-nx: duration 0 means "no time limit" in Warcraft III (Rexxar's Summon Misha is built
		// on Feral Spirit with Dur 0); it used to expire on the next tick, so Misha died as she appeared
		if (getDurationMax() > 0) {
			super.onTick(game, caster);
		}
	}

	@Override
	public boolean isTimedLifeBar() {
		return getDurationMax() > 0;
	}
}
