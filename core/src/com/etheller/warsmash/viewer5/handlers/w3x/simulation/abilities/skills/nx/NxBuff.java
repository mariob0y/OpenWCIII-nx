package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nx;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABGenericTimedBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedInstantTransformationBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedTransformationBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackEffectListenerStacking;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListenerPriority;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * warsmash-nx: one configurable timed buff for the campaign spells (NxSpells): stat bonuses (green
 * numbers, through the engine's non-stacking stat buffs), state changes (no attack, no spells, snared...),
 * damage over time from a source, a chance to miss on the unit's attacks, a damage-taken factor, and an
 * aura of damage around the unit. A new cast of the same buff (alias) replaces the old one.
 */
public class NxBuff extends CBuffTimed {
	public interface Hook {
		void run(CSimulation game, CUnit unit);
	}

	private final List<NonStackingStatBuff> stats = new ArrayList<>();
	private final List<StateModBuff> states = new ArrayList<>();
	private CUnit source;
	private float damagePerSecond;
	private CDamageType damageType = CDamageType.MAGIC;
	private float auraDamagePerSecond;
	private float auraRadius;
	private float missChance;
	private float damageTakenFactor = 1f;
	private Hook onRemoveHook;
	private Hook onTickHook;
	private War3ID morph;
	private CUnit unit;
	private int nextSecondTick;
	private final String key;

	private final CUnitAttackPreDamageListener missListener = (simulation, attacker, target, weaponType, attackType,
			damageType, damageResult) -> {
		if ((this.missChance > 0) && (simulation.getSeededRandom().nextFloat() < this.missChance)) {
			damageResult.setMiss(true);
		}
		return new CUnitAttackEffectListenerStacking();
	};

	private final CUnitAttackDamageTakenModificationListener takenListener = (simulation, attacker, target, isAttack,
			isRanged, attackType, damageType, previous) -> {
		previous.setDamageMultiplier(previous.getDamageMultiplier() * this.damageTakenFactor);
		return previous;
	};

	public NxBuff(final int handleId, final War3ID buffId, final float duration) {
		super(handleId, buffId, buffId, Math.max(duration, WarsmashConstants.SIMULATION_STEP_TIME));
		this.key = "nx" + buffId;
	}

	public NxBuff stat(final NonStackingStatBuffType type, final float value) {
		if (value != 0) {
			this.stats.add(new NonStackingStatBuff(type, this.key, value));
		}
		return this;
	}

	public NxBuff state(final StateModBuffType type) {
		this.states.add(new StateModBuff(type, 1));
		return this;
	}

	public NxBuff damageOverTime(final CUnit damageSource, final float perSecond, final CDamageType type) {
		this.source = damageSource;
		this.damagePerSecond = perSecond;
		this.damageType = type;
		return this;
	}

	public NxBuff auraDamage(final CUnit damageSource, final float perSecond, final float radius) {
		this.source = damageSource;
		this.auraDamagePerSecond = perSecond;
		this.auraRadius = radius;
		return this;
	}

	public NxBuff miss(final float chance) {
		this.missChance = chance;
		return this;
	}

	public NxBuff damageTaken(final float factor) {
		this.damageTakenFactor = factor;
		return this;
	}

	/** Hex/Polymorph: show the unit with this unit type's art while the buff lasts (NxHex). */
	public NxBuff morph(final War3ID form) {
		this.morph = form;
		return this;
	}

	public War3ID getMorph() {
		return this.morph;
	}

	public NxBuff onRemove(final Hook hook) {
		this.onRemoveHook = hook;
		return this;
	}

	public NxBuff onEachSecond(final Hook hook) {
		this.onTickHook = hook;
		return this;
	}

	/** A curse: damage over time, misses, more damage taken, a lowered stat, a disabling state or a morph. */
	public boolean isHarmful() {
		if ((this.damagePerSecond > 0) || (this.missChance > 0) || (this.damageTakenFactor > 1f)
				|| (this.morph != null)) {
			return true;
		}
		for (final NonStackingStatBuff stat : this.stats) {
			if (stat.getValue() < 0) {
				return true;
			}
		}
		for (final StateModBuff state : this.states) {
			switch (state.getBuffType()) {
			case INVULNERABLE:
			case MAGIC_IMMUNE:
			case RESISTANT:
			case INVISIBLE:
			case DETECTOR:
				break;
			default:
				return true;
			}
		}
		return false;
	}

	/** A blessing: something raised or protected, and nothing harmful (Spell Steal moves these). */
	public boolean isHelpful() {
		return !isHarmful() && (!this.stats.isEmpty() || !this.states.isEmpty() || (this.auraDamagePerSecond > 0)
				|| (this.damageTakenFactor < 1f));
	}

	/** Puts the buff on a unit, replacing the same buff from an earlier cast. */
	public static void apply(final CSimulation game, final CUnit target, final NxBuff buff) {
		for (final CAbility ability : new ArrayList<>(target.getAbilities())) {
			if ((ability instanceof NxBuff) && ability.getAlias().equals(buff.getAlias())) {
				target.remove(game, (NxBuff) ability);
			}
		}
		target.add(game, buff);
	}

	/** True for buffs a dispel removes: timed spell buffs, not timed life (summons) or auras. */
	public static boolean isDispellable(final CAbility ability) {
		if ((ability instanceof CBuffTimedLife) || (ability instanceof ABTimedTransformationBuff)
				|| (ability instanceof ABTimedInstantTransformationBuff)) {
			return false;
		}
		return (ability instanceof CBuffTimed) || (ability instanceof ABGenericTimedBuff);
	}

	/** Removes the dispellable buffs of a unit; returns how many. */
	public static int dispel(final CSimulation game, final CUnit target) {
		int n = 0;
		for (final CAbility ability : new ArrayList<>(target.getAbilities())) {
			if (isDispellable(ability)) {
				target.remove(game, (CBuff) ability);
				n++;
			}
		}
		return n;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit target) {
		this.unit = target;
		for (final NonStackingStatBuff stat : this.stats) {
			target.addNonStackingStatBuff(stat);
		}
		for (final StateModBuff state : this.states) {
			target.addStateModBuff(state);
			target.computeUnitState(game, state.getBuffType());
		}
		if (this.missChance > 0) {
			target.addPreDamageListener(CUnitAttackPreDamageListenerPriority.ACCURACY, this.missListener);
		}
		if (this.damageTakenFactor != 1f) {
			target.addDamageTakenModificationListener(this.takenListener);
		}
		this.nextSecondTick = game.getGameTurnTick() + secondTicks();
		if (this.morph != null) {
			System.err.println("NXHEX " + target.getTypeId() + " shown as " + this.morph);
			game.unitUpdatedType(target, this.morph);
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit target) {
		for (final NonStackingStatBuff stat : this.stats) {
			target.removeNonStackingStatBuff(stat);
		}
		for (final StateModBuff state : this.states) {
			target.removeStateModBuff(state);
			target.computeUnitState(game, state.getBuffType());
		}
		if (this.missChance > 0) {
			target.removePreDamageListener(CUnitAttackPreDamageListenerPriority.ACCURACY, this.missListener);
		}
		if (this.damageTakenFactor != 1f) {
			target.removeDamageTakenModificationListener(this.takenListener);
		}
		if (this.onRemoveHook != null) {
			this.onRemoveHook.run(game, target);
		}
		if (this.morph != null) {
			NxHex.refreshArt(game, target, this);
		}
	}

	private static int secondTicks() {
		return (int) StrictMath.ceil(1.0f / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit target) {
		if (game.getGameTurnTick() >= this.nextSecondTick) {
			this.nextSecondTick = game.getGameTurnTick() + secondTicks();
			final CUnit damageSource = this.source != null ? this.source : target;
			if (this.damagePerSecond > 0) {
				target.damage(game, damageSource, false, true, CAttackType.SPELLS, this.damageType,
						CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerSecond);
			}
			if (this.auraDamagePerSecond > 0) {
				final float x = target.getX();
				final float y = target.getY();
				game.getWorldCollision().enumUnitsInRange(x, y, this.auraRadius, (other) -> {
					if ((other != target) && !other.isDead() && !other.isBuilding()) {
						other.damage(game, damageSource, false, true, CAttackType.SPELLS, CDamageType.LIGHTNING,
								CWeaponSoundTypeJass.WHOKNOWS.name(), this.auraDamagePerSecond);
					}
					return false;
				});
			}
			if (this.onTickHook != null) {
				this.onTickHook.run(game, target);
			}
		}
		if (!target.isDead()) {
			super.onTick(game, target);
		}
	}

	public CUnit getUnit() {
		return this.unit;
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}
}
