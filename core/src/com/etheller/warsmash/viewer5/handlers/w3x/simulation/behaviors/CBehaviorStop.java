package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

public class CBehaviorStop implements CBehavior {

	private final CUnit unit;

	public CBehaviorStop(final CUnit unit) {
		this.unit = unit;
	}

	@Override
	public int getHighlightOrderId() {
		return OrderIds.stop;
	}

	@Override
	public CBehavior update(final CSimulation game) {
		// warsmash-nx: an idle unit looks for a target every 4th tick (0.2 s), staggered by handle so
		// the scans spread over the ticks; each is a quadtree search of its acquisition range, and
		// every idle unit on the map did one 20 times a second (~10% of the console's game thread,
		// OrcX01 profile 2026-11-22). Its queued orders are still read every tick.
		if ((((game.getGameTurnTick() + this.unit.getHandleId()) & 3) == 0)
				&& this.unit.autoAcquireTargets(game, false)) {
			return this.unit.getCurrentBehavior();
		}
		return this.unit.pollNextOrderBehavior(game);
	}

	@Override
	public void begin(final CSimulation game) {
		if (!this.unit.isConstructingOrUpgrading()) {
			this.unit.getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f,
					true);
		}
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {

	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public <T> T visit(final CBehaviorVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.IDLE;
	}

}
