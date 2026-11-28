package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/** Extra attack damage granted by the named aura on a researched unit. */
public final class CUpgradeEffectAuraDamage implements CUpgradeEffect {
	// Java 8 form of a record (the console build cannot transpile records).
	private final War3ID ability;
	private final float base;
	private final float increment;

	public CUpgradeEffectAuraDamage(final War3ID ability, final float base, final float increment) {
		this.ability = ability;
		this.base = base;
		this.increment = increment;
	}

	public War3ID ability() {
		return this.ability;
	}

	public float base() {
		return this.base;
	}

	public float increment() {
		return this.increment;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof CUpgradeEffectAuraDamage)) {
			return false;
		}
		final CUpgradeEffectAuraDamage other = (CUpgradeEffectAuraDamage) obj;
		return java.util.Objects.equals(this.ability, other.ability)
				&& Float.compare(this.base, other.base) == 0
				&& Float.compare(this.increment, other.increment) == 0;
	}

	@Override
	public int hashCode() {
		int result = 0;
		result = (31 * result) + java.util.Objects.hashCode(this.ability);
		result = (31 * result) + Float.hashCode(this.base);
		result = (31 * result) + Float.hashCode(this.increment);
		return result;
	}

	@Override
	public String toString() {
		return "CUpgradeEffectAuraDamage[" + "ability=" + this.ability + ", " + "base=" + this.base + ", " + "increment=" + this.increment + "]";
	}

	public float getBonus(final int level) {
		return Util.levelValue(this.base, this.increment, level - 1);
	}

	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
	}
}
