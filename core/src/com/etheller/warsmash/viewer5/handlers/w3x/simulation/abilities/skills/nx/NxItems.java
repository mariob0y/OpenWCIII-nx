package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityUnitOrPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.CAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.CAbilityTypeDefinitionSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackEffectListenerStacking;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPostDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListenerPriority;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CRectFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * warsmash-nx: the item abilities the Founding of Durotar maps use that Warsmash never had (audit:
 * switch2/scripts/campaign-audit/item_audit.py). Each reads its own SLK/map data (Data A..F, area,
 * duration, buffs, unit ids), so the map-edited item variants work as they are.
 * <ul>
 * <li>instant (no order, no interruption; the inventory uses the charge): healing/mana/restoration
 * scrolls and runes, speed and protection scrolls, potions (restoration, invulnerability, invisibility,
 * anti-magic, vampirism), Glyph of Omniscience, Rune of Rebirth, Tome of Retraining;</li>
 * <li>cast like spells (the charge goes when the spell takes effect): Town Portal, Ancestral Staff and
 * Scepter of the Sea summons, Finger of Death, Wand of Negation, Wand of Illusion, Control Magic,
 * Scroll of the Unholy Legion, Mechanical Critter, Wand of Mana Stealing, Ivory Tower, Wand of
 * Neutralization, Amulet of Recall, Spider Silk Broach, Staff of Sanctuary, Horn of the Clouds;</li>
 * <li>passive while held: Ring of Regeneration, orbs (bonus damage, a melee hero's second weapon so it
 * hits air, fire splash / frost slow / a chance to cast slow or purge / poison), Cloak of Flames, the
 * Battle Standard marker.</li>
 * </ul>
 */
public final class NxItems {
	private NxItems() {
	}

	private static void put(final Map<War3ID, CAbilityTypeDefinition> map, final String code,
			final CAbilityTypeDefinitionSpellBase.AbilityConstructor constructor) {
		map.put(War3ID.fromString(code), new CAbilityTypeDefinitionSpellBase(constructor));
	}

	static void register(final Map<War3ID, CAbilityTypeDefinition> map) {
		put(map, "AIha", (h, a) -> new Instant(h, a, Instant.HEAL_AOE, OrderIds.itemhealaoe));
		put(map, "AImr", (h, a) -> new Instant(h, a, Instant.MANA_AOE, OrderIds.itemmanarestoreaoe));
		put(map, "AIra", (h, a) -> new Instant(h, a, Instant.RESTORE_AOE, OrderIds.itemrestoreaoe));
		put(map, "AIre", (h, a) -> new Instant(h, a, Instant.RESTORE, OrderIds.itemrestore));
		put(map, "AIsa", (h, a) -> new Instant(h, a, Instant.SPEED_AOE, OrderIds.itemspeed));
		put(map, "AIda", (h, a) -> new Instant(h, a, Instant.DEFENSE_AOE, OrderIds.itemdefenseaoe));
		put(map, "AIvu", (h, a) -> new Instant(h, a, Instant.INVULNERABLE, OrderIds.iteminvulnerabilitypotion));
		put(map, "AIvi", (h, a) -> new Instant(h, a, Instant.INVISIBLE, OrderIds.iteminvisibility));
		put(map, "Aami", (h, a) -> new Instant(h, a, Instant.ANTI_MAGIC, OrderIds.antimagicshell));
		put(map, "AIpv", (h, a) -> new Instant(h, a, Instant.VAMPIRISM, OrderIds.itempotionvampirism));
		put(map, "AIrv", (h, a) -> new Instant(h, a, Instant.REVEAL_MAP, OrderIds.itemrevealmap));
		put(map, "AIrb", (h, a) -> new Instant(h, a, Instant.REBIRTH, OrderIds.itemruneofrebirth));
		put(map, "Aret", (h, a) -> new Instant(h, a, Instant.RETRAIN, OrderIds.itemretrain));

		put(map, "AItp", (h, a) -> new Teleport(h, a, true));
		put(map, "AIrt", (h, a) -> new Teleport(h, a, false));
		put(map, "AIsh", (h, a) -> new Summon(h, a, AbilityFields.DATA_B, OrderIds.itemfigurinesummon));
		put(map, "ANwm", (h, a) -> new Summon(h, a, AbilityFields.DATA_A, OrderIds.wateryminion));
		put(map, "Amec", (h, a) -> new Summon(h, a, AbilityFields.DATA_A, OrderIds.mechanicalcritter));
		put(map, "AIan", (h, a) -> new AnimateDead(h, a));
		put(map, "ANfd", (h, a) -> new UnitTarget(h, a, UnitTarget.FINGER_OF_DEATH, OrderIds.fingerofdeath));
		put(map, "AIil", (h, a) -> new UnitTarget(h, a, UnitTarget.ILLUSION, OrderIds.itemwandillusion));
		put(map, "Acmg", (h, a) -> new UnitTarget(h, a, UnitTarget.CONTROL_MAGIC, OrderIds.controlmagic));
		put(map, "Aste", (h, a) -> new UnitTarget(h, a, UnitTarget.MANA_STEAL, OrderIds.itemmanarestore));
		put(map, "AIdc", (h, a) -> new UnitTarget(h, a, UnitTarget.CHAIN_DISPEL, OrderIds.itemchaindispel));
		put(map, "AIwb", (h, a) -> new UnitTarget(h, a, UnitTarget.WEB, OrderIds.itemweb));
		put(map, "ANsa", (h, a) -> new UnitTarget(h, a, UnitTarget.SANCTUARY, OrderIds.sanctuary));
		put(map, "AIdi", (h, a) -> new NxSpells.AreaDispel(h, a));
		put(map, "AIbl", (h, a) -> new PointTarget(h, a, PointTarget.TINY_BUILDING, OrderIds.itemtinystructures));
		put(map, "Aclf", (h, a) -> new PointTarget(h, a, PointTarget.CLOUD_OF_FOG, OrderIds.cloudoffog));

		put(map, "Arel", (h, a) -> new Held(h, a, Held.REGEN));
		put(map, "AIfl", (h, a) -> new Held(h, a, Held.MARKER));
		put(map, "AIcf", (h, a) -> new Held(h, a, Held.IMMOLATION));
		put(map, "AIfb", (h, a) -> new Held(h, a, Held.ORB_FIRE));
		put(map, "AIhu", (h, a) -> new Held(h, a, Held.ORB_PLAIN));
		put(map, "AIpb", (h, a) -> new Held(h, a, Held.ORB_PLAIN));
		put(map, "AIob", (h, a) -> new Held(h, a, Held.ORB_FROST));
		put(map, "AIsb", (h, a) -> new Held(h, a, Held.ORB_SPELL));
		put(map, "Apo2", (h, a) -> new NxSpells.AttackEffect(h, a, NxSpells.Recipe.POISON));
	}

	// ------------------------------------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------------------------------------

	private static War3ID buffOrAlias(final GameObject go, final int level, final int index, final War3ID alias) {
		final War3ID buff = AbstractCAbilityTypeDefinition.getBuffId(go, level, index);
		return buff != null ? buff : alias;
	}

	private static List<War3ID> unitIds(final GameObject go, final int level) {
		final List<War3ID> ids = new ArrayList<>();
		for (final String s : go.getFieldAsString(AbilityFields.UNIT_ID + level, 0).split(",")) {
			final String id = s.trim();
			if (id.length() == 4) {
				ids.add(War3ID.fromString(id));
			}
		}
		return ids;
	}

	private static NxBuff buff(final CSimulation game, final War3ID id, final float seconds) {
		return new NxBuff(game.getHandleIdAllocator().createId(), id, seconds);
	}

	private static void restore(final CUnit u, final CSimulation game, final float life, final float mana) {
		if (life > 0) {
			u.heal(game, life);
		}
		if ((mana > 0) && (u.getMaximumMana() > 0)) {
			u.setMana(Math.min(u.getMaximumMana(), u.getMana() + mana));
		}
	}

	private static float distance(final CUnit u, final float x, final float y) {
		return (float) StrictMath.hypot(u.getX() - x, u.getY() - y);
	}

	/**
	 * Dead units (not heroes, not buildings) within the radius, nearest first. The engine's corpse lookup
	 * only holds corpses that have decayed to bones, so fresh ones are found by scanning the units.
	 */
	private static List<CUnit> deadUnitsNear(final CSimulation game, final float x, final float y,
			final float radius) {
		final List<CUnit> dead = new ArrayList<>();
		for (final CUnit u : game.getUnits()) {
			if (u.isDead() && !u.isHero() && !u.isBuilding() && !u.isHidden() && (distance(u, x, y) <= radius)) {
				dead.add(u);
			}
		}
		dead.sort(Comparator.comparingDouble((u) -> distance(u, x, y)));
		return dead;
	}

	/** The player's town hall nearest to (x, y), else any of its buildings, else null. */
	private static CUnit nearestOwnBuilding(final CSimulation game, final int playerIndex, final float x,
			final float y) {
		CUnit best = null;
		boolean bestIsHall = false;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit u : game.getUnits()) {
			if ((u.getPlayerIndex() != playerIndex) || u.isDead() || !u.isBuilding() || u.isHidden()) {
				continue;
			}
			final boolean hall = u.getClassifications().contains(CUnitClassification.TOWNHALL);
			final float d = distance(u, x, y);
			if ((best == null) || (hall && !bestIsHall) || ((hall == bestIsHall) && (d < bestDistance))) {
				best = u;
				bestIsHall = hall;
				bestDistance = d;
			}
		}
		return best;
	}

	/** Moves units around (x, y), spread on rings so they do not stack. */
	private static void placeAround(final CSimulation game, final List<CUnit> units, final float x, final float y,
			final float startRadius) {
		int i = 0;
		for (final CUnit u : units) {
			final int ring = 1 + (i / 8);
			final float angle = (float) ((StrictMath.PI * 2 * (i % 8)) / 8) + (ring * 0.4f);
			final float r = startRadius + ((ring - 1) * 90);
			u.setPointAndCheckUnstuck(x + ((float) StrictMath.cos(angle) * r), y + ((float) StrictMath.sin(angle) * r),
					game);
			game.createTemporarySpellEffectOnUnit(u, null, CEffectType.TARGET);
			i++;
		}
	}

	/**
	 * An illusion of a unit for a player: a real unit of the same type that deals the given share of its
	 * damage, takes the given multiple, has no spells and vanishes instead of dying.
	 */
	private static CUnit createIllusion(final CSimulation game, final CUnit source, final int playerIndex,
			final War3ID buffId, final War3ID alias, final float seconds, final float dealt, final float taken) {
		final float angle = game.getSeededRandom().nextFloat() * (float) (StrictMath.PI * 2);
		final CUnit image = game.createUnit(source.getTypeId(), playerIndex,
				source.getX() + ((float) StrictMath.cos(angle) * 100), source.getY() + ((float) StrictMath.sin(angle) * 100),
				source.getFacing());
		if (image == null) {
			return null;
		}
		final CAbilityHero heroData = source.getHeroData();
		if ((heroData != null) && (image.getHeroData() != null)) {
			image.getHeroData().setHeroLevel(game, image, heroData.getHeroLevel(), false);
		}
		image.setLife(game, image.getMaximumLife() * (source.getLife() / Math.max(1f, source.getMaximumLife())));
		image.setMana(0);
		image.setNxIllusion(true);
		image.addClassification(CUnitClassification.SUMMONED);
		image.addDeathReplacementEffect(
				com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementEffectPriority.PHOENIXREVIVE,
				(sim, unit, killer, result) -> {
					result.setReviving(true);
					sim.createTemporarySpellEffectOnUnit(unit, alias, CEffectType.SPECIAL);
					sim.removeUnit(unit);
					final com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementStacking stop = new com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementStacking();
					stop.setAllowStacking(false);
					stop.setAllowSamePriorityStacking(false);
					return stop;
				});
		NxBuff.apply(game, image, buff(game, buffId, seconds).state(StateModBuffType.DISABLE_SPELLS)
				.stat(NonStackingStatBuffType.MELEEATKPCT, dealt - 1).stat(NonStackingStatBuffType.RNGDATKPCT, dealt - 1)
				.damageTaken(taken));
		image.add(game, new CBuffTimedLife(game.getHandleIdAllocator().createId(), buffId, seconds, false));
		game.createTemporarySpellEffectOnUnit(image, alias, CEffectType.TARGET);
		return image;
	}

	// ------------------------------------------------------------------------------------------------
	// instant items: done in checkBeforeQueue (no order, the current order goes on); the inventory then
	// uses the charge. Power-ups (runes) come through the same path when picked up.
	// ------------------------------------------------------------------------------------------------

	public static final class Instant extends CAbilityNoTargetSpellBase {
		static final int HEAL_AOE = 0, MANA_AOE = 1, RESTORE_AOE = 2, RESTORE = 3, SPEED_AOE = 4, DEFENSE_AOE = 5,
				INVULNERABLE = 6, INVISIBLE = 7, ANTI_MAGIC = 8, VAMPIRISM = 9, REVEAL_MAP = 10, REBIRTH = 11,
				RETRAIN = 12;
		private final int kind;
		private final int orderId;
		private float a, b, c, area;
		private War3ID buffId;

		public Instant(final int handleId, final War3ID alias, final int kind, final int orderId) {
			super(handleId, alias);
			this.kind = kind;
			this.orderId = orderId;
		}

		@Override
		public int getBaseOrderId() {
			return this.orderId;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = NxSpells.data(go, AbilityFields.DATA_A, level);
			this.b = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.c = NxSpells.data(go, AbilityFields.DATA_C, level);
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			this.buffId = buffOrAlias(go, level, 0, getAlias());
		}

		@Override
		public float getUIAreaOfEffect() {
			return this.area;
		}

		private boolean isPowerUp() {
			final CItem item = getItem();
			return (item != null) && item.getItemType().isUseAutomaticallyWhenAcquired();
		}

		@Override
		public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int orderId,
				final AbilityTarget target) {
			if ((orderId != getBaseOrderId()) || (target != null)) {
				return super.checkBeforeQueue(game, caster, orderId, target);
			}
			if (!isPowerUp() && (getCooldownRemaining(game, caster) > 0)) {
				// not ready: no effect and no charge used (beginNoTarget does nothing)
				return true;
			}
			doEffect(game, caster, null);
			caster.beginCooldown(game, getCooldownId(), getCooldown());
			game.unitSoundEffectEvent(caster, getAlias());
			return false;
		}

		@Override
		public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int orderId) {
			return caster.pollNextOrderBehavior(game);
		}

		private void forAlliesInArea(final CSimulation game, final CUnit caster, final UnitAction action) {
			final float radius = this.area;
			if (radius <= 0) {
				action.run(caster);
				return;
			}
			final List<CUnit> units = new ArrayList<>();
			game.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), radius, (u) -> {
				if (!u.isDead() && !u.isHidden() && ((u == caster) || u.canBeTargetedBy(game, caster, getTargetsAllowed()))) {
					units.add(u);
				}
				return false;
			});
			if (!units.contains(caster)) {
				units.add(caster);
			}
			for (final CUnit u : units) {
				action.run(u);
			}
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			switch (this.kind) {
			case HEAL_AOE:
				forAlliesInArea(game, caster, (u) -> {
					restore(u, game, this.a, 0);
					game.createTemporarySpellEffectOnUnit(u, getAlias(), CEffectType.TARGET);
				});
				break;
			case MANA_AOE:
				forAlliesInArea(game, caster, (u) -> {
					restore(u, game, 0, this.a);
					game.createTemporarySpellEffectOnUnit(u, getAlias(), CEffectType.TARGET);
				});
				break;
			case RESTORE_AOE:
				forAlliesInArea(game, caster, (u) -> {
					restore(u, game, this.a, this.b);
					game.createTemporarySpellEffectOnUnit(u, getAlias(), CEffectType.TARGET);
				});
				break;
			case RESTORE:
				restore(caster, game, this.a, this.b);
				break;
			case SPEED_AOE: {
				// Data A: speed increase, at most up to the game's maximum unit speed (the engine has no cap)
				final float max = game.getGameplayConstants().getMaxUnitSpeed();
				forAlliesInArea(game, caster, (u) -> {
					float pct = this.a;
					if ((max > 0) && (u.getSpeed() > 0)) {
						pct = Math.min(pct, Math.max(0, (max / u.getSpeed()) - 1));
					}
					NxBuff.apply(game, u, buff(game, this.buffId, getDurationForTarget(u))
							.stat(NonStackingStatBuffType.MVSPDPCT, pct));
				});
				break;
			}
			case DEFENSE_AOE:
				// Data A: armor; the Arcane Scroll variant also restores Data B life and Data C mana
				forAlliesInArea(game, caster, (u) -> {
					NxBuff.apply(game, u,
							buff(game, this.buffId, getDurationForTarget(u)).stat(NonStackingStatBuffType.DEF, this.a));
					restore(u, game, this.b, this.c);
				});
				break;
			case INVULNERABLE:
				NxBuff.apply(game, caster, buff(game, this.buffId, getDurationForTarget(caster))
						.state(StateModBuffType.INVULNERABLE));
				break;
			case INVISIBLE:
				invisibility(game, caster);
				break;
			case ANTI_MAGIC:
				NxBuff.dispel(game, caster);
				NxBuff.apply(game, caster, buff(game, this.buffId, getDurationForTarget(caster))
						.state(StateModBuffType.MAGIC_IMMUNE));
				break;
			case VAMPIRISM:
				// Data A: damage bonus, Data B: share of the damage dealt returned as life
				NxBuff.apply(game, caster,
						buff(game, this.buffId, getDurationForTarget(caster))
								.stat(NonStackingStatBuffType.MELEEATK, this.a).stat(NonStackingStatBuffType.RNGDATK, this.a)
								.stat(NonStackingStatBuffType.HPSTEAL, this.b));
				break;
			case REVEAL_MAP:
				revealMap(game, caster);
				break;
			case REBIRTH:
				rebirth(game, caster);
				break;
			case RETRAIN:
				retrain(game, caster);
				break;
			default:
				break;
			}
			return false;
		}

		/** Invisible for the duration; attacking ends it. */
		private void invisibility(final CSimulation game, final CUnit caster) {
			final NxBuff invisible = buff(game, this.buffId, getDurationForTarget(caster))
					.state(StateModBuffType.INVISIBLE);
			final boolean[] ending = new boolean[1];
			final CUnitAttackPreDamageListener reveal = (simulation, attacker, t, weaponType, attackType, damageType,
					damageResult) -> {
				if (!ending[0]) {
					ending[0] = true;
					// removed on the next tick, outside the attack's listener loop
					simulation.registerEffect((g) -> {
						if (g.getUnit(caster.getHandleId()) != null) {
							caster.remove(g, invisible);
						}
						return true;
					});
				}
				return new CUnitAttackEffectListenerStacking();
			};
			caster.addPreDamageListener(CUnitAttackPreDamageListenerPriority.ACCURACY, reveal);
			NxBuff.apply(game, caster, invisible.onRemove((g, u) -> u
					.removePreDamageListener(CUnitAttackPreDamageListenerPriority.ACCURACY, reveal)));
		}

		private void revealMap(final CSimulation game, final CUnit caster) {
			final PathingGrid grid = game.getPathingGrid();
			final float minX = grid.getWorldX(0);
			final float minY = grid.getWorldY(0);
			final float maxX = grid.getWorldX(grid.getWidth() - 1);
			final float maxY = grid.getWorldY(grid.getHeight() - 1);
			final CPlayer player = game.getPlayer(caster.getPlayerIndex());
			final CRectFogModifier modifier = new CRectFogModifier(CFogState.VISIBLE,
					new Rectangle(minX, minY, maxX - minX, maxY - minY));
			player.addFogModifer(game, modifier, false);
			final int end = game.getGameTurnTick() + NxSpells.ticks(getDuration() > 0 ? getDuration() : 5);
			game.registerEffect((g) -> {
				if (g.getGameTurnTick() >= end) {
					player.removeFogModifer(g, modifier);
					return true;
				}
				return false;
			});
		}

		/**
		 * Rune of Rebirth: a copy of the creep that dropped it joins the player. The rune lies where its
		 * creep died, so the nearest creep corpse is taken to be that creep.
		 */
		private void rebirth(final CSimulation game, final CUnit caster) {
			CUnit dead = null;
			for (final CUnit u : deadUnitsNear(game, caster.getX(), caster.getY(), 600)) {
				if (!caster.isUnitAlly(game.getPlayer(u.getPlayerIndex()))) {
					dead = u;
					break;
				}
			}
			if (dead == null) {
				return;
			}
			final CUnit reborn = game.createUnit(dead.getTypeId(), caster.getPlayerIndex(), caster.getX(),
					caster.getY(), caster.getFacing());
			if (reborn != null) {
				reborn.setPointAndCheckUnstuck(caster.getX(), caster.getY(), game);
				game.createTemporarySpellEffectOnUnit(reborn, getAlias(), CEffectType.TARGET);
			}
		}

		/** Tome of Retraining: every learned hero skill is unlearned and its points given back. */
		private void retrain(final CSimulation game, final CUnit caster) {
			final CAbilityHero hero = caster.getHeroData();
			if (hero == null) {
				return;
			}
			int refunded = 0;
			for (final War3ID skill : hero.getSkillsAvailable()) {
				for (final CAbility ability : new ArrayList<>(caster.getAbilities())) {
					if ((ability instanceof CLevelingAbility) && skill.equals(ability.getAlias())
							&& (ability.getItem() == null)) {
						refunded += ((CLevelingAbility) ability).getLevel();
						caster.remove(game, ability);
					}
				}
			}
			hero.setSkillPoints(hero.getSkillPoints() + refunded);
		}
	}

	private interface UnitAction {
		void run(CUnit unit);
	}

	// ------------------------------------------------------------------------------------------------
	// Town Portal (AItp) and Amulet of Recall (AIrt)
	// ------------------------------------------------------------------------------------------------

	/**
	 * Town Portal: after the casting time (channelled), the caster and up to Data A of the player's units
	 * around it go to the player's town hall nearest the target (or the targeted building). Recall: up to
	 * Data A of the player's units around the target come to the caster.
	 */
	public static final class Teleport extends CAbilityUnitOrPointTargetSpellBase {
		private final boolean townPortal;
		private int maxUnits;
		private float area, castTime;
		private int endTick;

		public Teleport(final int handleId, final War3ID alias, final boolean townPortal) {
			super(handleId, alias);
			this.townPortal = townPortal;
		}

		@Override
		public int getBaseOrderId() {
			return this.townPortal ? OrderIds.itemtownportal : OrderIds.itemrecall;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.maxUnits = (int) NxSpells.data(go, AbilityFields.DATA_A, level);
			if (this.maxUnits <= 0) {
				this.maxUnits = 12;
			}
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			if (this.area <= 0) {
				this.area = 700;
			}
			this.castTime = this.townPortal ? Math.max(0, getCastingTime()) : 0;
		}

		@Override
		protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
				final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if (this.townPortal && ((t == null) || !t.isBuilding() || (t.getPlayerIndex() != unit.getPlayerIndex()))) {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
				return;
			}
			super.innerCheckCanTarget(game, unit, orderId, target, receiver);
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			if (!this.townPortal) {
				recall(game, caster, target);
				return false;
			}
			if (destination(game, caster, target) == null) {
				game.getCommandErrorListener().showInterfaceError(caster.getPlayerIndex(),
						CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
				return false;
			}
			this.endTick = game.getGameTurnTick() + NxSpells.ticks(this.castTime);
			return this.castTime > 0 ? true : portal(game, caster, target);
		}

		@Override
		public boolean doChannelTick(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			return game.getGameTurnTick() < this.endTick;
		}

		@Override
		public void doChannelEnd(final CSimulation game, final CUnit caster, final AbilityTarget target,
				final boolean interrupted) {
			if (!interrupted) {
				portal(game, caster, target);
			}
		}

		private CUnit destination(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t != null) && t.isBuilding() && !t.isDead() && (t.getPlayerIndex() == caster.getPlayerIndex())) {
				return t;
			}
			return nearestOwnBuilding(game, caster.getPlayerIndex(), target.getX(), target.getY());
		}

		private boolean portal(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final CUnit building = destination(game, caster, target);
			if (building == null) {
				return false;
			}
			final List<CUnit> group = ownUnitsNear(game, caster, caster.getX(), caster.getY());
			group.add(0, caster);
			game.spawnTemporarySpellEffectOnPoint(caster.getX(), caster.getY(), 0, getAlias(), CEffectType.SPECIAL, 0);
			placeAround(game, group, building.getX(), building.getY(), 250);
			return false;
		}

		private void recall(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final List<CUnit> group = ownUnitsNear(game, caster, target.getX(), target.getY());
			placeAround(game, group, caster.getX(), caster.getY(), 120);
		}

		/** The player's movable units (not the caster) within the area, nearest first, at most maxUnits. */
		private List<CUnit> ownUnitsNear(final CSimulation game, final CUnit caster, final float x, final float y) {
			final List<CUnit> found = new ArrayList<>();
			game.getWorldCollision().enumUnitsInRange(x, y, this.area, (u) -> {
				if ((u != caster) && (u.getPlayerIndex() == caster.getPlayerIndex()) && !u.isDead() && !u.isBuilding()
						&& !u.isHidden() && !u.isMovementDisabled()) {
					found.add(u);
				}
				return false;
			});
			found.sort(Comparator.comparingDouble((u) -> distance(u, x, y)));
			final int limit = this.townPortal ? this.maxUnits - 1 : this.maxUnits;
			return new ArrayList<>(found.subList(0, Math.max(0, Math.min(limit, found.size()))));
		}
	}

	// ------------------------------------------------------------------------------------------------
	// summons: Ancestral Staff (AIsh), Watery Minion (ANwm, Scepter of the Sea), Mechanical Critter (Amec)
	// ------------------------------------------------------------------------------------------------

	public static final class Summon extends CAbilityNoTargetSpellBase {
		private final String countField;
		private final int orderId;
		private List<War3ID> unitTypes;
		private int count;
		private float area;
		private War3ID buffId;

		public Summon(final int handleId, final War3ID alias, final String countField, final int orderId) {
			super(handleId, alias);
			this.countField = countField;
			this.orderId = orderId;
		}

		@Override
		public int getBaseOrderId() {
			return this.orderId;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.unitTypes = unitIds(go, level);
			this.count = Math.max(1, (int) NxSpells.data(go, this.countField, level));
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			this.buffId = buffOrAlias(go, level, 0, getAlias());
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			if (this.unitTypes.isEmpty()) {
				System.err.println("NxItems: " + getAlias() + " has no unit to summon");
				return false;
			}
			final War3ID type = this.unitTypes.get(0);
			final float facing = caster.getFacing();
			final float rad = (float) StrictMath.toRadians(facing);
			final float distance = this.area > 0 ? this.area : 150;
			final float x = caster.getX() + ((float) StrictMath.cos(rad) * distance);
			final float y = caster.getY() + ((float) StrictMath.sin(rad) * distance);
			for (int i = 0; i < this.count; i++) {
				final CUnit summoned = game.createUnitSimple(type, caster.getPlayerIndex(), x, y, facing);
				if (summoned == null) {
					continue;
				}
				summoned.setPointAndCheckUnstuck(x, y, game);
				summoned.addClassification(CUnitClassification.SUMMONED);
				if (getDuration() > 0) {
					summoned.add(game,
							new CBuffTimedLife(game.getHandleIdAllocator().createId(), this.buffId, getDuration(), true));
				}
				game.createTemporarySpellEffectOnUnit(summoned, getAlias(), CEffectType.TARGET);
			}
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			return false;
		}
	}

	/** Scroll of the Unholy Legion: Data A corpses around the caster rise as its timed, invulnerable units. */
	public static final class AnimateDead extends CAbilityNoTargetSpellBase {
		private int corpses;
		private boolean invulnerable;
		private float area;
		private War3ID buffId;

		private final int orderId;

		public AnimateDead(final int handleId, final War3ID alias) {
			this(handleId, alias, OrderIds.itemanimatedead);
		}

		/** Also the creep spell (ACad), with its own order. */
		public AnimateDead(final int handleId, final War3ID alias, final int orderId) {
			super(handleId, alias);
			this.orderId = orderId;
		}

		@Override
		public int getBaseOrderId() {
			return this.orderId;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.corpses = Math.max(1, (int) NxSpells.data(go, AbilityFields.DATA_A, level));
			this.invulnerable = NxSpells.data(go, AbilityFields.DATA_B, level) != 0;
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			if (this.area <= 0) {
				this.area = 900;
			}
			this.buffId = buffOrAlias(go, level, 0, getAlias());
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final List<CUnit> raised = new ArrayList<>();
			for (final CUnit u : deadUnitsNear(game, caster.getX(), caster.getY(), this.area)) {
				if ((raised.size() < this.corpses) && u.isRaisable()) {
					raised.add(u);
				}
			}
			final float seconds = getDuration() > 0 ? getDuration() : 40;
			for (final CUnit u : raised) {
				u.resurrect(game);
				if (u.getPlayerIndex() != caster.getPlayerIndex()) {
					u.setPlayerIndex(game, caster.getPlayerIndex(), true);
				}
				u.addClassification(CUnitClassification.SUMMONED);
				if (this.invulnerable) {
					NxBuff.apply(game, u, buff(game, this.buffId, seconds).state(StateModBuffType.INVULNERABLE));
				}
				u.add(game, new CBuffTimedLife(game.getHandleIdAllocator().createId(), this.buffId, seconds, true));
				game.createTemporarySpellEffectOnUnit(u, getAlias(), CEffectType.TARGET);
			}
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			return false;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// unit-target items
	// ------------------------------------------------------------------------------------------------

	public static final class UnitTarget extends CAbilityTargetSpellBase {
		static final int FINGER_OF_DEATH = 0, ILLUSION = 1, CONTROL_MAGIC = 2, MANA_STEAL = 3, CHAIN_DISPEL = 4,
				WEB = 5, SANCTUARY = 6;
		private final int kind;
		private final int orderId;
		private float a, b, c, d, e, area;
		private War3ID buff0, buff1;

		public UnitTarget(final int handleId, final War3ID alias, final int kind, final int orderId) {
			super(handleId, alias);
			this.kind = kind;
			this.orderId = orderId;
		}

		@Override
		public int getBaseOrderId() {
			return this.orderId;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = NxSpells.data(go, AbilityFields.DATA_A, level);
			this.b = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.c = NxSpells.data(go, AbilityFields.DATA_C, level);
			this.d = NxSpells.data(go, AbilityFields.DATA_D, level);
			this.e = NxSpells.data(go, AbilityFields.DATA_E, level);
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			this.buff0 = buffOrAlias(go, level, 0, getAlias());
			this.buff1 = buffOrAlias(go, level, 1, this.buff0);
		}

		@Override
		protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
				final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			boolean ok = t != null;
			if (ok) {
				switch (this.kind) {
				case CONTROL_MAGIC:
					ok = t.getClassifications().contains(CUnitClassification.SUMMONED) && !t.isHero();
					break;
				case MANA_STEAL:
					ok = t.getMaximumMana() > 0;
					break;
				case WEB:
					ok = t.getFlyHeight() > 0;
					break;
				case SANCTUARY:
					ok = !t.isBuilding() && (t.getPlayerIndex() == unit.getPlayerIndex());
					break;
				default:
					break;
				}
			}
			if (ok) {
				receiver.targetOk(target);
			}
			else {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
			}
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || t.isDead()) {
				return false;
			}
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			switch (this.kind) {
			case FINGER_OF_DEATH:
				// Data C: damage
				NxSpells.spellDamage(game, caster, t, this.c, CDamageType.MAGIC);
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				break;
			case ILLUSION:
				// Data A: damage dealt (share), Data B: damage taken multiplier
				createIllusion(game, t, caster.getPlayerIndex(), this.buff0, getAlias(),
						getDuration() > 0 ? getDuration() : 60, this.a, this.b > 0 ? this.b : 2);
				break;
			case CONTROL_MAGIC:
				controlMagic(game, caster, t);
				break;
			case MANA_STEAL: {
				final float stolen = Math.min(t.getMana(), this.a);
				if (stolen > 0) {
					t.setMana(t.getMana() - stolen);
					restore(caster, game, 0, stolen);
				}
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				break;
			}
			case CHAIN_DISPEL:
				chainDispel(game, caster, t);
				break;
			case WEB:
				NxBuff.apply(game, t, buff(game, this.buff1, getDurationForTarget(t)).state(StateModBuffType.SNARED));
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				break;
			case SANCTUARY:
				sanctuary(game, caster, t);
				break;
			default:
				break;
			}
			return false;
		}

		/** Control Magic: a summoned unit changes sides if the caster pays Data B mana per hit point. */
		private void controlMagic(final CSimulation game, final CUnit caster, final CUnit t) {
			final float cost = this.b * t.getLife();
			if (caster.getMana() < cost) {
				game.getCommandErrorListener().showInterfaceError(caster.getPlayerIndex(),
						CommandStringErrorKeys.NOT_ENOUGH_MANA);
				return;
			}
			caster.setMana(caster.getMana() - cost);
			t.setPlayerIndex(game, caster.getPlayerIndex(), true);
			game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
		}

		/**
		 * Wand of Neutralization: dispels the target, then jumps to the nearest unit not yet hit within the
		 * area, up to Data C units; summoned enemies take Data B damage, Data A mana is drained.
		 */
		private void chainDispel(final CSimulation game, final CUnit caster, final CUnit first) {
			final int max = Math.max(1, (int) this.c);
			final float jump = this.area > 0 ? this.area : 400;
			final List<CUnit> hit = new ArrayList<>();
			CUnit current = first;
			while ((current != null) && (hit.size() < max)) {
				hit.add(current);
				NxBuff.dispel(game, current);
				if (NxSpells.enemies(game, caster, current)) {
					if (current.getClassifications().contains(CUnitClassification.SUMMONED)) {
						NxSpells.spellDamage(game, caster, current, this.b, CDamageType.MAGIC);
					}
					if (this.a > 0) {
						current.setMana(Math.max(0, current.getMana() - this.a));
					}
				}
				game.createTemporarySpellEffectOnUnit(current, getAlias(), CEffectType.TARGET);
				final CUnit from = current;
				final CUnit[] next = new CUnit[1];
				game.getWorldCollision().enumUnitsInRange(from.getX(), from.getY(), jump, (u) -> {
					if (!hit.contains(u) && !u.isDead() && !u.isBuilding() && !u.isHidden()
							&& u.canBeTargetedBy(game, caster, getTargetsAllowed())
							&& ((next[0] == null) || (distance(u, from.getX(), from.getY()) < distance(next[0],
									from.getX(), from.getY())))) {
						next[0] = u;
					}
					return false;
				});
				current = next[0];
			}
		}

		/**
		 * Staff of Sanctuary: the target goes to its player's town hall, stunned and healing Data E life per
		 * second (after Data B / Data C seconds for heroes / units) until it is at full life.
		 */
		private void sanctuary(final CSimulation game, final CUnit caster, final CUnit t) {
			final CUnit hall = nearestOwnBuilding(game, t.getPlayerIndex(), t.getX(), t.getY());
			if (hall == null) {
				return;
			}
			final List<CUnit> one = new ArrayList<>();
			one.add(t);
			placeAround(game, one, hall.getX(), hall.getY(), 200);
			final float perSecond = this.e > 0 ? this.e : 15;
			final float delay = t.isHero() ? this.b : this.c;
			final float seconds = Math.min(120, delay + ((t.getMaximumLife() - t.getLife()) / perSecond));
			NxBuff.apply(game, t, buff(game, this.buff0, Math.max(1, seconds)).state(StateModBuffType.STUN)
					.stat(NonStackingStatBuffType.HPGEN, perSecond));
			game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// point-target items
	// ------------------------------------------------------------------------------------------------

	public static final class PointTarget extends CAbilityPointTargetSpellBase {
		static final int TINY_BUILDING = 0, CLOUD_OF_FOG = 1;
		private final int kind;
		private final int orderId;
		private float a, b, area;
		private List<War3ID> unitTypes;
		private War3ID buffId;

		public PointTarget(final int handleId, final War3ID alias, final int kind, final int orderId) {
			super(handleId, alias);
			this.kind = kind;
			this.orderId = orderId;
		}

		@Override
		public int getBaseOrderId() {
			return this.orderId;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = NxSpells.data(go, AbilityFields.DATA_A, level);
			this.b = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			this.unitTypes = unitIds(go, level);
			this.buffId = buffOrAlias(go, level, 0, getAlias());
		}

		@Override
		public float getUIAreaOfEffect() {
			return this.area;
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final AbilityPointTarget p = target.visit(AbilityTargetVisitor.POINT);
			final float x = p != null ? p.x : target.getX();
			final float y = p != null ? p.y : target.getY();
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			if (this.kind == TINY_BUILDING) {
				// UnitID: one building per race (human, orc, undead, night elf)
				if (this.unitTypes.isEmpty()) {
					return false;
				}
				final int race = game.getPlayer(caster.getPlayerIndex()).getRace().ordinal();
				final War3ID type = this.unitTypes.get(Math.max(0, Math.min(this.unitTypes.size() - 1, race)));
				final CUnit building = game.createUnit(type, caster.getPlayerIndex(), x, y, 270);
				if (building != null) {
					game.createTemporarySpellEffectOnUnit(building, getAlias(), CEffectType.TARGET);
				}
				return false;
			}
			// Cloud of Fog: enemies in the cloud cannot attack (Data B: their chance to miss when it allows it);
			// targets "structure" alone (the item, the dragonhawk) means enemy buildings only
			final float radius = this.area > 0 ? this.area : 300;
			final boolean buildingsOnly = getTargetsAllowed().contains(CTargetType.STRUCTURE)
					&& !getTargetsAllowed().contains(CTargetType.GROUND) && !getTargetsAllowed().contains(CTargetType.AIR);
			game.spawnTemporarySpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.EFFECT, 0);
			game.getWorldCollision().enumUnitsInRange(x, y, radius, (u) -> {
				if (!u.isDead() && NxSpells.enemies(game, caster, u)
						&& (buildingsOnly ? u.isBuilding() : u.canBeTargetedBy(game, caster, getTargetsAllowed()))) {
					NxBuff fog = buff(game, this.buffId, getDurationForTarget(u));
					fog = this.a > 0 ? fog.state(StateModBuffType.DISABLE_ATTACK) : fog.miss(this.b / 100f);
					NxBuff.apply(game, u, fog);
				}
				return false;
			});
			return false;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// passive while held
	// ------------------------------------------------------------------------------------------------

	public static final class Held extends CAbilityPassiveSpellBase {
		static final int REGEN = 0, MARKER = 1, IMMOLATION = 2, ORB_PLAIN = 3, ORB_FIRE = 4, ORB_FROST = 5,
				ORB_SPELL = 6;
		private final int kind;
		private float a, b, c, d, e;
		private War3ID buffId;
		private War3ID effectAbility;
		private String missileArt;
		private int missileSpeed;
		private final List<NonStackingStatBuff> stats = new ArrayList<>();
		private final CUnitAttackPostDamageListener listener = this::onHit;
		private CAbilitySpellBase effectSpell;
		private int nextSecondTick;

		public Held(final int handleId, final War3ID alias, final int kind) {
			super(handleId, alias, alias);
			this.kind = kind;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = NxSpells.data(go, AbilityFields.DATA_A, level);
			this.b = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.c = NxSpells.data(go, AbilityFields.DATA_C, level);
			this.d = NxSpells.data(go, AbilityFields.DATA_D, level);
			this.e = NxSpells.data(go, AbilityFields.DATA_E, level);
			this.buffId = buffOrAlias(go, level, 0, getAlias());
			final List<War3ID> ids = unitIds(go, level);
			this.effectAbility = ids.isEmpty() ? null : ids.get(0);
			this.missileArt = go.getFieldAsString("Missileart", 0);
			this.missileSpeed = go.getFieldAsInteger(AbilityFields.PROJECTILE_SPEED, 0);
		}

		/**
		 * The weapon an orb switches on is a spare one only orbs use (a melee hero's second weapon); in
		 * Warcraft III it shoots the orb's missile. Its unit data often has no missile art or speed (a speed
		 * of 0 never arrives), so the orb supplies them.
		 */
		private void armSpareWeapon(final CUnit unit) {
			final int index = (int) this.e;
			final List<CUnitAttack> attacks = unit.getUnitSpecificAttacks();
			if ((index < 1) || (attacks == null) || (index > attacks.size())
					|| ((unit.getUnitType().getAttacksEnabled() & index) != 0)) {
				return;
			}
			final CUnitAttack attack = attacks.get(index - 1);
			if (attack instanceof CUnitAttackMissile) {
				final CUnitAttackMissile missile = (CUnitAttackMissile) attack;
				if ((this.missileArt != null) && !this.missileArt.isEmpty()) {
					missile.setProjectileArt(this.missileArt);
				}
				if (this.missileSpeed > 0) {
					missile.setProjectileSpeed(this.missileSpeed);
				}
				else if (missile.getProjectileSpeed() <= 0) {
					missile.setProjectileSpeed(900);
				}
				missile.setProjectileHomingEnabled(true);
			}
		}

		private boolean isOrb() {
			return this.kind >= ORB_PLAIN;
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			// each held item counts on its own (two Rings of Regeneration add up)
			final String key = "nxitem" + getHandleId();
			if (this.kind == REGEN) {
				this.stats.add(new NonStackingStatBuff(NonStackingStatBuffType.HPGEN, key, this.a));
			}
			else if (isOrb() && (this.a != 0)) {
				this.stats.add(new NonStackingStatBuff(NonStackingStatBuffType.MELEEATK, key, this.a));
				this.stats.add(new NonStackingStatBuff(NonStackingStatBuffType.RNGDATK, key, this.a));
			}
			for (final NonStackingStatBuff stat : this.stats) {
				unit.addNonStackingStatBuff(stat);
			}
			if (isOrb()) {
				unit.addPostDamageListener(this.listener);
				armSpareWeapon(unit);
				unit.nxEnableExtraAttack(game, (int) this.e, true);
			}
			this.nextSecondTick = game.getGameTurnTick() + NxSpells.ticks(1);
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			for (final NonStackingStatBuff stat : this.stats) {
				unit.removeNonStackingStatBuff(stat);
			}
			this.stats.clear();
			if (isOrb()) {
				unit.removePostDamageListener(this.listener);
				unit.nxEnableExtraAttack(game, (int) this.e, false);
			}
			super.onRemove(game, unit);
		}

		@Override
		public void onTick(final CSimulation game, final CUnit unit) {
			if ((this.kind != IMMOLATION) || unit.isDead() || (game.getGameTurnTick() < this.nextSecondTick)) {
				return;
			}
			this.nextSecondTick = game.getGameTurnTick() + NxSpells.ticks(1);
			// Cloak of Flames: Data A damage per second to enemies in the area
			final float radius = getAreaOfEffect() > 0 ? getAreaOfEffect() : 160;
			game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), radius, (u) -> {
				if ((u != unit) && !u.isDead() && !u.isBuilding() && NxSpells.enemies(game, unit, u)
						&& u.canBeTargetedBy(game, unit, getTargetsAllowed())) {
					NxSpells.spellDamage(game, unit, u, this.a, CDamageType.FIRE);
				}
				return false;
			});
		}

		private void onHit(final CSimulation game, final CUnit attacker, final AbilityTarget target,
				final float damage) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || t.isDead() || !NxSpells.enemies(game, attacker, t)) {
				return;
			}
			switch (this.kind) {
			case ORB_FIRE: {
				// the bonus fire damage splashes on the enemies around the target
				final float radius = getAreaOfEffect() > 0 ? getAreaOfEffect() : 150;
				game.getWorldCollision().enumUnitsInRange(t.getX(), t.getY(), radius, (u) -> {
					if ((u != t) && !u.isDead() && NxSpells.enemies(game, attacker, u)) {
						NxSpells.spellDamage(game, attacker, u, this.a, CDamageType.FIRE);
					}
					return false;
				});
				break;
			}
			case ORB_FROST:
				if (!t.isBuilding()) {
					final float seconds = getDurationForTarget(t);
					NxBuff.apply(game, t, buff(game, this.buffId, seconds > 0 ? seconds : 2)
							.stat(NonStackingStatBuffType.MVSPDPCT, -0.5f).stat(NonStackingStatBuffType.ATKSPD, -0.25f));
				}
				break;
			case ORB_SPELL:
				orbSpell(game, attacker, t);
				break;
			default:
				break;
			}
		}

		/** Data B / C / D: chance (%) against units / heroes / summons to cast the effect ability. */
		private void orbSpell(final CSimulation game, final CUnit attacker, final CUnit t) {
			final float chance = t.isHero() ? this.c
					: (t.getClassifications().contains(CUnitClassification.SUMMONED) ? this.d : this.b);
			if ((t.isBuilding()) || ((game.getSeededRandom().nextFloat() * 100) >= chance)) {
				return;
			}
			if ((this.effectSpell == null) && (this.effectAbility != null)) {
				final CAbilityType<?> type = game.getAbilityData().getAbilityType(this.effectAbility);
				final CAbility created = type != null ? type.createAbility(game.getHandleIdAllocator().createId())
						: null;
				if (created instanceof CAbilitySpellBase) {
					this.effectSpell = (CAbilitySpellBase) created;
				}
			}
			if (this.effectSpell != null) {
				this.effectSpell.doEffect(game, attacker, t);
				return;
			}
			// the effect ability is not a Java spell (Slow is built from JSON): Orb of Slow's slow
			final float seconds = t.isHero() ? 10 : 30;
			NxBuff.apply(game, t, buff(game, War3ID.fromString("Bslo"), seconds)
					.stat(NonStackingStatBuffType.MVSPDPCT, -0.6f).stat(NonStackingStatBuffType.ATKSPD, -0.25f));
			game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
		}
	}
}
