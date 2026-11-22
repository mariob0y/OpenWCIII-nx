package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.shadowhunter;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;

/**
 * warsmash-nx: Healing Wave (AOhw: the Shadow Hunter's, and Rokhan's ANhw in the Frozen Throne orc
 * campaign). Chain Lightning's mechanics (CAbilityChainLightning) with healing: Data A = healing on the
 * first target, Data B = number of targets, Data C = reduction per bounce; it bounces to the most wounded
 * allowed unit in range that it has not healed yet.
 */
public class CAbilityHealingWave extends CAbilityTargetSpellBase {
	private static final float SECONDS_BETWEEN_JUMPS = 0.25f;
	private static final float BOLT_LIFETIME_SECONDS = 2.00f;
	private War3ID lightningIdPrimary;
	private War3ID lightningIdSecondary;
	private float healPerTarget;
	private float healReductionPerTarget;
	private int numberOfTargetsHit;
	private float areaOfEffect;

	public CAbilityHealingWave(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.healingwave;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.lightningIdPrimary = AbstractCAbilityTypeDefinition.getLightningId(worldEditorAbility, level, 0);
		this.lightningIdSecondary = AbstractCAbilityTypeDefinition.getLightningId(worldEditorAbility, level, 1);
		if (this.lightningIdSecondary == null) {
			this.lightningIdSecondary = this.lightningIdPrimary;
		}
		this.healPerTarget = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.numberOfTargetsHit = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_B + level, 0);
		this.healReductionPerTarget = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		if (this.areaOfEffect <= 0) {
			this.areaOfEffect = 500;
		}
	}

	static void heal(final CSimulation simulation, final CUnit unit, final float amount) {
		unit.setLife(simulation, Math.min(unit.getMaximumLife(), unit.getLife() + amount));
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			final SimulationRenderComponentLightning lightning = simulation.createLightning(caster,
					this.lightningIdPrimary, targetUnit);
			simulation.createTemporarySpellEffectOnUnit(targetUnit, getAlias(), CEffectType.TARGET);
			heal(simulation, targetUnit, this.healPerTarget);
			final float multiplier = 1.0f - this.healReductionPerTarget;
			final Set<CUnit> previousTargets = new HashSet<>();
			previousTargets.add(targetUnit);
			simulation.registerEffect(new CEffectHealingWaveBolt(simulation.getGameTurnTick(),
					this.healPerTarget * multiplier, multiplier, caster, targetUnit, lightning, getAlias(),
					this.lightningIdSecondary, getTargetsAllowed(), this.areaOfEffect, this.numberOfTargetsHit - 1,
					previousTargets));
		}
		return false;
	}

	private static int ticks(final float seconds) {
		return (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	private static final class CEffectHealingWaveBolt implements CEffect {
		private final int boltLifetimeEndTick;
		private int jumpDelayEndTick;
		private final float remainingHeal;
		private final float multiplier;
		private final CUnit caster;
		private final CUnit targetUnit;
		private final SimulationRenderComponentLightning boltFx;
		private final War3ID abilityId;
		private final War3ID jumpLightningId;
		private final EnumSet<CTargetType> targetsAllowed;
		private final float jumpRadius;
		private final int remainingJumps;
		private final Set<CUnit> previousTargets;

		CEffectHealingWaveBolt(final int startTick, final float remainingHeal, final float multiplier,
				final CUnit caster, final CUnit targetUnit, final SimulationRenderComponentLightning boltFx,
				final War3ID abilityId, final War3ID jumpLightningId, final EnumSet<CTargetType> targetsAllowed,
				final float jumpRadius, final int remainingJumps, final Set<CUnit> previousTargets) {
			this.boltLifetimeEndTick = startTick + ticks(BOLT_LIFETIME_SECONDS);
			this.jumpDelayEndTick = startTick + ticks(SECONDS_BETWEEN_JUMPS);
			this.remainingHeal = remainingHeal;
			this.multiplier = multiplier;
			this.caster = caster;
			this.targetUnit = targetUnit;
			this.boltFx = boltFx;
			this.abilityId = abilityId;
			this.jumpLightningId = jumpLightningId;
			this.targetsAllowed = targetsAllowed;
			this.jumpRadius = jumpRadius;
			this.remainingJumps = remainingJumps;
			this.previousTargets = previousTargets;
		}

		@Override
		public boolean update(final CSimulation game) {
			final int gameTurnTick = game.getGameTurnTick();
			if ((gameTurnTick >= this.jumpDelayEndTick) && (this.remainingJumps > 0)) {
				final CUnit[] best = { null };
				game.getWorldCollision().enumUnitsInRange(this.targetUnit.getX(), this.targetUnit.getY(),
						this.jumpRadius, (enumUnit) -> {
							if (!enumUnit.isDead() && !this.previousTargets.contains(enumUnit)
									&& (enumUnit.getLife() < enumUnit.getMaximumLife())
									&& enumUnit.canBeTargetedBy(game, this.caster, this.targetsAllowed)) {
								if ((best[0] == null) || ((enumUnit.getLife() / enumUnit.getMaximumLife()) < (best[0]
										.getLife() / best[0].getMaximumLife()))) {
									best[0] = enumUnit;
								}
							}
							return false;
						});
				final CUnit next = best[0];
				if (next != null) {
					final SimulationRenderComponentLightning lightning = game.createLightning(this.targetUnit,
							this.jumpLightningId, next);
					game.createTemporarySpellEffectOnUnit(next, this.abilityId, CEffectType.TARGET);
					heal(game, next, this.remainingHeal);
					this.previousTargets.add(next);
					game.registerEffect(new CEffectHealingWaveBolt(gameTurnTick, this.remainingHeal * this.multiplier,
							this.multiplier, this.caster, next, lightning, this.abilityId, this.jumpLightningId,
							this.targetsAllowed, this.jumpRadius, this.remainingJumps - 1, this.previousTargets));
				}
				this.jumpDelayEndTick = Integer.MAX_VALUE;
			}
			final boolean done = gameTurnTick >= this.boltLifetimeEndTick;
			if (done && (this.boltFx != null)) {
				this.boltFx.remove();
			}
			return done;
		}
	}
}
