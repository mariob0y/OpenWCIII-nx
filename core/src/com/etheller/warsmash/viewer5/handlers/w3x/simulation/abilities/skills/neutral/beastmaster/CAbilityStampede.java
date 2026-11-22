package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.beastmaster;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityCollisionProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * warsmash-nx: Stampede (ANst: the Beastmaster's, Rexxar's Arsp in the Frozen Throne orc campaign). A
 * channel of up to Duration seconds: Data A beasts per second appear at random points of the target area
 * and run across it in the caster's casting direction; a beast that touches (Data B radius) an enemy
 * explodes for Data C damage in Data D radius. Data E (damage delay) is not modelled: the blast is
 * immediate.
 */
public class CAbilityStampede extends CAbilityPointTargetSpellBase {
	private float beastsPerSecond;
	private float collisionRadius;
	private float damage;
	private float damageRadius;
	private float areaOfEffect;
	private float missileSpeed;

	private int channelEndTick;
	private int nextBeastTick;

	public CAbilityStampede(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.stampede;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.beastsPerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		if (this.beastsPerSecond <= 0) {
			this.beastsPerSecond = 2;
		}
		this.collisionRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		if (this.collisionRadius <= 0) {
			this.collisionRadius = 55;
		}
		this.damage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.damageRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		if (this.areaOfEffect <= 0) {
			this.areaOfEffect = 1000;
		}
		this.missileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
		if (this.missileSpeed <= 0) {
			this.missileSpeed = 500;
		}
	}

	private static int ticks(final float seconds) {
		return (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final float duration = getDuration() > 0 ? getDuration() : 30;
		this.channelEndTick = simulation.getGameTurnTick() + ticks(duration);
		this.nextBeastTick = simulation.getGameTurnTick();
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if (tick >= this.channelEndTick) {
			return false;
		}
		if (tick >= this.nextBeastTick) {
			this.nextBeastTick = tick + Math.max(1, ticks(1.0f / this.beastsPerSecond));
			launchBeast(simulation, caster, target);
		}
		return true;
	}

	private void launchBeast(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final float tx = target.getX();
		final float ty = target.getY();
		double direction = StrictMath.atan2(ty - caster.getY(), tx - caster.getX());
		if ((Math.abs(ty - caster.getY()) < 1) && (Math.abs(tx - caster.getX()) < 1)) {
			direction = StrictMath.toRadians(caster.getFacing());
		}
		final float dirX = (float) StrictMath.cos(direction);
		final float dirY = (float) StrictMath.sin(direction);
		final float radius = this.areaOfEffect / 2;
		final Random random = simulation.getSeededRandom();
		// a random line across the area, perpendicular offset from the centre line, running from the near edge
		final float side = ((random.nextFloat() * 2) - 1) * radius;
		final float back = random.nextFloat() * radius * 0.5f;
		final float startX = (tx - (dirX * (radius + back))) - (dirY * side);
		final float startY = (ty - (dirY * (radius + back))) + (dirX * side);
		final float endX = startX + (dirX * (radius * 2));
		final float endY = startY + (dirY * (radius * 2));
		simulation.createCollisionProjectile(caster, getAlias(), startX, startY,
				(float) StrictMath.toDegrees(direction), this.missileSpeed, false, new AbilityPointTarget(endX, endY), 1,
				1, this.collisionRadius, this.collisionRadius, 0.05f, new BeastListener(caster), false);
	}

	private final class BeastListener implements CAbilityCollisionProjectileListener {
		private final CUnit caster;

		BeastListener(final CUnit caster) {
			this.caster = caster;
		}

		@Override
		public void onLaunch(final CSimulation game, final CProjectile projectile, final AbilityTarget target) {
		}

		@Override
		public void onPreHits(final CSimulation game, final CProjectile projectile,
				final AbilityPointTarget location) {
		}

		@Override
		public boolean canHitTarget(final CSimulation game, final CWidget target) {
			final CUnit unit = target.visit(AbilityTargetVisitor.UNIT);
			return (unit != null) && !unit.isDead()
					&& unit.canBeTargetedBy(game, this.caster, getTargetsAllowed());
		}

		@Override
		public void onHit(final CSimulation game, final CProjectile projectile, final AbilityTarget target) {
			final float x = projectile.getX();
			final float y = projectile.getY();
			game.spawnSpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.SPECIAL, 0).remove();
			final List<CUnit> victims = new ArrayList<>();
			game.getWorldCollision().enumUnitsInRange(x, y, CAbilityStampede.this.damageRadius, (enumUnit) -> {
				if (!enumUnit.isDead() && enumUnit.canBeTargetedBy(game, this.caster, getTargetsAllowed())) {
					victims.add(enumUnit);
				}
				return false;
			});
			for (final CUnit victim : victims) {
				victim.damage(game, this.caster, false, true, CAttackType.SPELLS, CDamageType.NORMAL,
						CWeaponSoundTypeJass.WHOKNOWS.name(), CAbilityStampede.this.damage);
			}
		}

		@Override
		public void setUnitTargets(final int units) {
		}

		@Override
		public void setDestructableTargets(final int dests) {
		}

		@Override
		public void setCurrentLocation(final AbilityPointTarget loc) {
		}
	}
}
