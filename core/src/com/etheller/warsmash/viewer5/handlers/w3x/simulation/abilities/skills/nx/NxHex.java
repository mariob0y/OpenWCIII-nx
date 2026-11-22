package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nx;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * warsmash-nx: Hex and Polymorph turn the target into a critter, as in WC3: the unit shows the critter's
 * model, portrait and sounds, moves at the critter's speed, and can neither attack nor cast. Its unit
 * type stays the same for the game and the triggers (GetUnitTypeId, hit points, owner...), as in WC3;
 * only the render side gets the critter's art (SimulationRenderController.unitUpdatedType with the
 * critter's type id). The original art comes back when the buff ends, is dispelled, or the unit dies.
 */
public final class NxHex {
	private NxHex() {
	}

	/**
	 * @param forms the ability's Data B..E: comma-separated critter ids for ground, air, amphibious and
	 *              water units ("-" or empty for none)
	 */
	public static void apply(final CSimulation game, final CUnit target, final NxBuff buff, final String[] forms) {
		final War3ID form = pickForm(game, target, forms);
		NxBuff withForm = buff.state(StateModBuffType.DISABLE_ATTACK).state(StateModBuffType.DISABLE_SPELLS);
		final CUnitType critter = form == null ? null : game.getUnitData().getUnitType(form);
		if (critter != null) {
			// the critter's own movement speed, whatever the unit had
			final float delta = critter.getSpeed() - target.getUnitType().getSpeed();
			withForm = withForm.stat(NonStackingStatBuffType.MVSPD, delta).morph(form);
		}
		else {
			withForm = withForm.stat(NonStackingStatBuffType.MVSPDPCT, -0.4f);
		}
		NxBuff.apply(game, target, withForm);
		target.order(game, OrderIds.stop, null);
	}

	private static War3ID pickForm(final CSimulation game, final CUnit target, final String[] forms) {
		if (forms == null) {
			return null;
		}
		final MovementType movement = target.getUnitType().getMovementType();
		int slot = 0;
		if (movement != null) {
			switch (movement) {
			case FLY:
				slot = 1;
				break;
			case AMPHIBIOUS:
				slot = 2;
				break;
			case FLOAT:
				slot = 3;
				break;
			default:
				slot = 0;
				break;
			}
		}
		War3ID picked = pickFrom(game, forms[slot]);
		if ((picked == null) && (slot != 0)) {
			picked = pickFrom(game, forms[0]);
		}
		return picked;
	}

	private static War3ID pickFrom(final CSimulation game, final String list) {
		if ((list == null) || list.isEmpty() || "-".equals(list) || "_".equals(list)) {
			return null;
		}
		final List<War3ID> options = new ArrayList<>();
		for (final String part : list.split(",")) {
			final String id = part.trim();
			if ((id.length() == 4) && (game.getUnitData().getUnitType(War3ID.fromString(id)) != null)) {
				options.add(War3ID.fromString(id));
			}
		}
		if (options.isEmpty()) {
			return null;
		}
		return options.get(game.getSeededRandom().nextInt(options.size()));
	}

	/** Shows the unit with the art of its newest remaining critter form, or its own. */
	static void refreshArt(final CSimulation game, final CUnit unit, final NxBuff leaving) {
		War3ID form = null;
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof NxBuff) && (ability != leaving) && (((NxBuff) ability).getMorph() != null)) {
				form = ((NxBuff) ability).getMorph();
			}
		}
		System.err.println("NXHEX " + unit.getTypeId() + " shown as " + (form != null ? form : "itself"));
		game.unitUpdatedType(unit, form != null ? form : unit.getTypeId());
	}
}
