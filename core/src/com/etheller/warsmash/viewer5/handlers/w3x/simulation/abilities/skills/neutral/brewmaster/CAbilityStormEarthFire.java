package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.brewmaster;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * warsmash-nx: Storm, Earth and Fire (ANef: the Pandaren Brewmaster's, Chen's Acef in the Frozen Throne
 * orc campaign). The caster splits into the units of Data A (npn1,npn2,npn3) for the duration: it is
 * hidden, paused and invulnerable meanwhile, and comes back where the last surviving elemental stood when
 * the time is up or all of them have died.
 */
public class CAbilityStormEarthFire extends CAbilityNoTargetSpellBase {
	private final List<War3ID> summonUnitIds = new ArrayList<>();
	private War3ID buffId;
	private float areaOfEffect;

	public CAbilityStormEarthFire(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.elementalfury;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.summonUnitIds.clear();
		final List<String> ids = worldEditorAbility.getFieldAsList(AbilityFields.DATA_A + level);
		if (ids != null) {
			for (final String id : ids) {
				for (final String part : id.split(",")) {
					if (part.trim().length() == 4) {
						this.summonUnitIds.add(War3ID.fromString(part.trim()));
					}
				}
			}
		}
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		if (this.areaOfEffect <= 0) {
			this.areaOfEffect = 128;
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		if (this.summonUnitIds.isEmpty()) {
			return false;
		}
		final List<CUnit> elementals = new ArrayList<>();
		final float facing = caster.getFacing();
		for (int i = 0; i < this.summonUnitIds.size(); i++) {
			final double angle = StrictMath.toRadians(facing + ((360.0 * i) / this.summonUnitIds.size()));
			final float x = caster.getX() + (float) (StrictMath.cos(angle) * this.areaOfEffect);
			final float y = caster.getY() + (float) (StrictMath.sin(angle) * this.areaOfEffect);
			final CUnit unit = simulation.createUnitSimple(this.summonUnitIds.get(i), caster.getPlayerIndex(), x, y,
					facing);
			if (unit == null) {
				continue;
			}
			unit.addClassification(CUnitClassification.SUMMONED);
			caster.fireSummonEvents(simulation, unit);
			unit.add(simulation, new CBuffTimedLife(simulation.getHandleIdAllocator().createId(), this.buffId,
					getDuration(), false));
			simulation.createTemporarySpellEffectOnUnit(unit, getAlias(), CEffectType.SPECIAL);
			elementals.add(unit);
		}
		if (elementals.isEmpty()) {
			return false;
		}
		caster.setInvulnerable(true);
		caster.setPaused(true);
		caster.setHidden(true);
		simulation.registerEffect(new CEffectSplit(caster, elementals));
		return false;
	}

	/** Brings the caster back once every elemental is gone. */
	private static final class CEffectSplit implements CEffect {
		private final CUnit caster;
		private final List<CUnit> elementals;
		private float lastX;
		private float lastY;

		CEffectSplit(final CUnit caster, final List<CUnit> elementals) {
			this.caster = caster;
			this.elementals = elementals;
			this.lastX = caster.getX();
			this.lastY = caster.getY();
		}

		@Override
		public boolean update(final CSimulation game) {
			boolean anyAlive = false;
			for (final CUnit unit : this.elementals) {
				if (!unit.isDead()) {
					anyAlive = true;
					this.lastX = unit.getX();
					this.lastY = unit.getY();
				}
			}
			if (anyAlive) {
				return false;
			}
			this.caster.setHidden(false);
			this.caster.setPointAndCheckUnstuck(this.lastX, this.lastY, game);
			this.caster.setPaused(false);
			this.caster.setInvulnerable(false);
			game.unitRepositioned(this.caster);
			return true;
		}
	}
}
