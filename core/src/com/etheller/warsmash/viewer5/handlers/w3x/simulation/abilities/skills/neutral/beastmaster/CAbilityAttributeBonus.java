package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.beastmaster;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;

/**
 * warsmash-nx: the learnable Attribute Bonus hero skill (Aamk, Rexxar's 5th skill in the Frozen Throne
 * orc campaign; also the campaign heroes' "Attribute Modifier Skill"): Data A agility, Data B
 * intelligence, Data C strength, as bonuses (green numbers) that grow with the level. The level data is
 * read without the unit at hand, so what is applied is reconciled on the next tick.
 */
public class CAbilityAttributeBonus extends CAbilityPassiveSpellBase {
	private int strength;
	private int agility;
	private int intelligence;
	private int appliedStrength;
	private int appliedAgility;
	private int appliedIntelligence;

	public CAbilityAttributeBonus(final int handleId, final War3ID alias) {
		super(handleId, War3ID.fromString("Aamk"), alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.agility = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.intelligence = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_B + level, 0);
		this.strength = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_C + level, 0);
	}

	private void apply(final CSimulation game, final CUnit unit, final int str, final int agi, final int intel) {
		final CAbilityHero hero = unit.getHeroData();
		if (hero == null) {
			return;
		}
		if (str != this.appliedStrength) {
			hero.addStrengthBonus(game, unit, str - this.appliedStrength);
			this.appliedStrength = str;
		}
		if (agi != this.appliedAgility) {
			hero.addAgilityBonus(game, unit, agi - this.appliedAgility);
			this.appliedAgility = agi;
		}
		if (intel != this.appliedIntelligence) {
			hero.addIntelligenceBonus(game, unit, intel - this.appliedIntelligence);
			this.appliedIntelligence = intel;
		}
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		super.onAdd(game, unit);
		apply(game, unit, this.strength, this.agility, this.intelligence);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		super.onTick(game, unit);
		apply(game, unit, this.strength, this.agility, this.intelligence);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		apply(game, unit, 0, 0, 0);
		super.onRemove(game, unit);
	}
}
