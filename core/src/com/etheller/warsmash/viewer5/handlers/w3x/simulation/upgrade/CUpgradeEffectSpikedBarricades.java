package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/** Flat retaliation damage read using the struck building's current owner. */
public final class CUpgradeEffectSpikedBarricades implements CUpgradeEffect {
	// Java 8 form of a record (the console build cannot transpile records).
	private final float base;
	private final float increment;

	public CUpgradeEffectSpikedBarricades(final float base, final float increment) {
		this.base = base;
		this.increment = increment;
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
		if (!(obj instanceof CUpgradeEffectSpikedBarricades)) {
			return false;
		}
		final CUpgradeEffectSpikedBarricades other = (CUpgradeEffectSpikedBarricades) obj;
		return Float.compare(this.base, other.base) == 0
				&& Float.compare(this.increment, other.increment) == 0;
	}

	@Override
	public int hashCode() {
		int result = 0;
		result = (31 * result) + Float.hashCode(this.base);
		result = (31 * result) + Float.hashCode(this.increment);
		return result;
	}

	@Override
	public String toString() {
		return "CUpgradeEffectSpikedBarricades[" + "base=" + this.base + ", " + "increment=" + this.increment + "]";
	}

	public float getDamage(final int level) {
		return Util.levelValue(this.base, this.increment, level - 1);
	}

	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
	}
}
