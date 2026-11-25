package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.shadowhunter;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * warsmash-nx: the ward spells, a unit summoned at the target point for a time (Data A of them; Unit ID
 * per level; Duration): Serpent Ward (AOwd: the Shadow Hunter's, Rokhan's Arsw), Healing Ward (Ahwd) and
 * Sentry Ward (Aeye). What the ward does comes from its own unit type (attack, healing aura, vision).
 */
public class CAbilityWard extends CAbilityPointTargetSpellBase {
	private final int baseOrderId;
	private War3ID summonUnitId;
	private int summonCount;
	private War3ID buffId;

	public CAbilityWard(final int handleId, final War3ID alias, final int baseOrderId) {
		super(handleId, alias);
		this.baseOrderId = baseOrderId;
	}

	@Override
	public int getBaseOrderId() {
		return this.baseOrderId;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		final String unitId = worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + level, 0);
		this.summonUnitId = unitId.length() == 4 ? War3ID.fromString(unitId) : War3ID.NONE;
		this.summonCount = Math.max(1, worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0));
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final AbilityPointTarget point = target.visit(AbilityTargetVisitor.POINT);
		if ((point == null) || War3ID.NONE.equals(this.summonUnitId)) {
			return false;
		}
		for (int i = 0; i < this.summonCount; i++) {
			final CUnit ward = simulation.createUnitSimple(this.summonUnitId, caster.getPlayerIndex(), point.x,
					point.y, caster.getFacing());
			if (ward == null) {
				continue;
			}
			ward.addClassification(CUnitClassification.SUMMONED);
			caster.fireSummonEvents(simulation, ward);
			ward.add(simulation, new CBuffTimedLife(simulation.getHandleIdAllocator().createId(), this.buffId,
					getDuration(), false));
			simulation.createTemporarySpellEffectOnUnit(ward, getAlias(), CEffectType.SPECIAL);
		}
		return false;
	}
}
