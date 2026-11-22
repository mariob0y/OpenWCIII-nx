package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffStun;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.CAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.CAbilityTypeDefinitionSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPostDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * warsmash-nx: spells the Founding of Durotar campaign needs that Warsmash never had. Each is driven by
 * the ability's own SLK/map data (Data A..F per level, durations, area, buffs), so the map-edited
 * versions (Rokhan's Hex, creep variants...) work as they are.
 * <ul>
 * <li>unit target: Hex and Polymorph (a random critter form from Data B..E by movement type: its model,
 * portrait, sounds and speed; no attacks, no spells; see NxHex), Ensnare, Bloodlust, Unholy Frenzy, Cripple, Lightning
 * Shield, Purge, Drunken Haze, Spirit Link, Ancestral Spirit, Devour, Unstable Concoction;</li>
 * <li>no target: Berserk, Locust Swarm (no visible locusts: each bite shows the spell's effect art);</li>
 * <li>point: Dispel Magic / Disenchant, Stasis Trap;</li>
 * <li>passive attack effects: Venom Spears, Slow Poison, Liquid Fire, Pillage.</li>
 * </ul>
 * Registered from CAbilityData.registerCodes (a body-only change there).
 */
public final class NxSpells {
	private NxSpells() {
	}

	static int ticks(final float seconds) {
		return Math.max(1, (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME));
	}

	static float data(final GameObject go, final String field, final int level) {
		return go.getFieldAsFloat(field + level, 0);
	}

	static boolean enemies(final CSimulation game, final CUnit a, final CUnit b) {
		return !b.isUnitAlly(game.getPlayer(a.getPlayerIndex()));
	}

	static void spellDamage(final CSimulation game, final CUnit source, final CUnit target, final float amount,
			final CDamageType type) {
		if (amount > 0) {
			target.damage(game, source, false, true, CAttackType.SPELLS, type, CWeaponSoundTypeJass.WHOKNOWS.name(),
					amount);
		}
	}

	private static void put(final Map<War3ID, CAbilityTypeDefinition> map, final String code,
			final CAbilityTypeDefinitionSpellBase.AbilityConstructor constructor) {
		map.put(War3ID.fromString(code), new CAbilityTypeDefinitionSpellBase(constructor));
	}

	public static void register(final Map<War3ID, CAbilityTypeDefinition> map) {
		put(map, "AOhx", (h, a) -> new TargetSpell(h, a, Recipe.HEX, OrderIds.hex));
		put(map, "Aply", (h, a) -> new TargetSpell(h, a, Recipe.HEX, OrderIds.polymorph));
		put(map, "Aens", (h, a) -> new TargetSpell(h, a, Recipe.ENSNARE, OrderIds.ensnare));
		put(map, "Ablo", (h, a) -> new TargetSpell(h, a, Recipe.BLOODLUST, OrderIds.bloodlust));
		put(map, "Auhf", (h, a) -> new TargetSpell(h, a, Recipe.UNHOLY_FRENZY, OrderIds.unholyfrenzy));
		put(map, "Acri", (h, a) -> new TargetSpell(h, a, Recipe.CRIPPLE, OrderIds.cripple));
		put(map, "Alsh", (h, a) -> new TargetSpell(h, a, Recipe.LIGHTNING_SHIELD, OrderIds.lightningshield));
		put(map, "Aprg", (h, a) -> new TargetSpell(h, a, Recipe.PURGE, OrderIds.purge));
		put(map, "ANdh", (h, a) -> new TargetSpell(h, a, Recipe.DRUNKEN_HAZE, OrderIds.drunkenhaze));
		put(map, "Aspl", (h, a) -> new TargetSpell(h, a, Recipe.SPIRIT_LINK, OrderIds.spiritlink));
		put(map, "Aast", (h, a) -> new TargetSpell(h, a, Recipe.ANCESTRAL_SPIRIT, OrderIds.ancestralspirittarget));
		put(map, "Adev", (h, a) -> new TargetSpell(h, a, Recipe.DEVOUR, OrderIds.devour));
		put(map, "Auco", (h, a) -> new TargetSpell(h, a, Recipe.UNSTABLE_CONCOCTION, OrderIds.unstableconcoction));
		put(map, "Absk", (h, a) -> new Berserk(h, a));
		put(map, "AUls", (h, a) -> new LocustSwarm(h, a));
		put(map, "Adis", (h, a) -> new AreaDispel(h, a));
		put(map, "Asta", (h, a) -> new StasisTrap(h, a));
		put(map, "Aven", (h, a) -> new AttackEffect(h, a, Recipe.POISON));
		put(map, "Aspo", (h, a) -> new AttackEffect(h, a, Recipe.POISON));
		put(map, "Aliq", (h, a) -> new AttackEffect(h, a, Recipe.LIQUID_FIRE));
		put(map, "Asal", (h, a) -> new AttackEffect(h, a, Recipe.PILLAGE));
		NxSpells2.register(map);
	}

	enum Recipe {
		HEX, ENSNARE, BLOODLUST, UNHOLY_FRENZY, CRIPPLE, LIGHTNING_SHIELD, PURGE, DRUNKEN_HAZE, SPIRIT_LINK,
		ANCESTRAL_SPIRIT, DEVOUR, UNSTABLE_CONCOCTION, POISON, LIQUID_FIRE, PILLAGE
	}

	// ------------------------------------------------------------------------------------------------
	// unit-target spells
	// ------------------------------------------------------------------------------------------------

	public static final class TargetSpell extends CAbilityTargetSpellBase {
		private final Recipe recipe;
		private final int orderId;
		private float a, b, c, d, e, f, area;
		private War3ID buff0, buff1;

		public TargetSpell(final int handleId, final War3ID alias, final Recipe recipe, final int orderId) {
			super(handleId, alias);
			this.recipe = recipe;
			this.orderId = orderId;
		}

		@Override
		public int getBaseOrderId() {
			return this.orderId;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = data(go, AbilityFields.DATA_A, level);
			this.b = data(go, AbilityFields.DATA_B, level);
			this.c = data(go, AbilityFields.DATA_C, level);
			this.d = data(go, AbilityFields.DATA_D, level);
			this.e = data(go, AbilityFields.DATA_E, level);
			this.f = data(go, AbilityFields.DATA_F, level);
			this.area = data(go, AbilityFields.AREA_OF_EFFECT, level);
			this.buff0 = AbstractCAbilityTypeDefinition.getBuffId(go, level, 0);
			this.buff1 = AbstractCAbilityTypeDefinition.getBuffId(go, level, 1);
			if (this.buff0 == null) {
				this.buff0 = getAlias();
			}
			if (this.buff1 == null) {
				this.buff1 = this.buff0;
			}
			if (this.recipe == Recipe.HEX) {
				// Data B..E: the critter forms for ground, air, amphibious and water units
				this.morphForms = new String[] { go.getFieldAsString(AbilityFields.DATA_B + level, 0),
						go.getFieldAsString(AbilityFields.DATA_C + level, 0),
						go.getFieldAsString(AbilityFields.DATA_D + level, 0),
						go.getFieldAsString(AbilityFields.DATA_E + level, 0) };
			}
		}

		private String[] morphForms;

		private NxBuff buff(final CSimulation game, final War3ID id, final CUnit target) {
			return new NxBuff(game.getHandleIdAllocator().createId(), id, getDurationForTarget(target));
		}

		@Override
		protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
				final CWidget target, final com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver<CWidget> receiver) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if (this.recipe == Recipe.DEVOUR) {
				if ((t == null) || t.isHero() || t.isBuilding() || (t.getUnitType().getLevel() > this.a)
						|| (this.devoured != null)) {
					receiver.targetCheckFailed(
							com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
					return;
				}
			}
			if ((this.recipe == Recipe.ANCESTRAL_SPIRIT) && ((t == null) || !t.isDead() || t.isHero())) {
				receiver.targetCheckFailed(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
				return;
			}
			receiver.targetOk(target);
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if (t == null) {
				return false;
			}
			if ((this.recipe != Recipe.ANCESTRAL_SPIRIT) && t.isDead()) {
				return false;
			}
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			switch (this.recipe) {
			case HEX:
				// Data A: max creep level (Polymorph)
				if (!t.isHero() && (this.a > 0) && (t.getUnitType().getLevel() > this.a)) {
					return false;
				}
				NxHex.apply(game, t, buff(game, this.buff0, t), this.morphForms);
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				break;
			case ENSNARE: {
				final boolean air = t.getFlyHeight() > 0;
				NxBuff.apply(game, t, buff(game, air ? this.buff0 : this.buff1, t).state(StateModBuffType.SNARED));
				break;
			}
			case BLOODLUST:
				NxBuff.apply(game, t, buff(game, this.buff0, t).stat(NonStackingStatBuffType.ATKSPD, this.a)
						.stat(NonStackingStatBuffType.MVSPDPCT, this.b));
				break;
			case UNHOLY_FRENZY:
				NxBuff.apply(game, t, buff(game, this.buff0, t).stat(NonStackingStatBuffType.ATKSPD, this.a)
						.damageOverTime(caster, this.b, CDamageType.UNIVERSAL));
				break;
			case CRIPPLE:
				NxBuff.apply(game, t,
						buff(game, this.buff0, t).stat(NonStackingStatBuffType.MVSPDPCT, -this.a)
								.stat(NonStackingStatBuffType.ATKSPD, -this.b)
								.stat(NonStackingStatBuffType.MELEEATKPCT, -this.c)
								.stat(NonStackingStatBuffType.RNGDATKPCT, -this.c));
				break;
			case LIGHTNING_SHIELD:
				NxBuff.apply(game, t, buff(game, this.buff0, t).auraDamage(caster, this.a,
						this.area > 0 ? this.area : 160));
				break;
			case PURGE:
				purge(game, caster, t);
				break;
			case DRUNKEN_HAZE: {
				final float radius = this.area > 0 ? this.area : 200;
				game.getWorldCollision().enumUnitsInRange(t.getX(), t.getY(), radius, (u) -> {
					if (!u.isDead() && !u.isBuilding() && enemies(game, caster, u)
							&& u.canBeTargetedBy(game, caster, getTargetsAllowed())) {
						NxBuff.apply(game, u,
								buff(game, this.buff0, u).miss(this.b)
										.stat(NonStackingStatBuffType.MVSPDPCT, -this.c)
										.stat(NonStackingStatBuffType.ATKSPD, -this.d));
					}
					return false;
				});
				break;
			}
			case SPIRIT_LINK:
				spiritLink(game, caster, t);
				break;
			case ANCESTRAL_SPIRIT:
				if (t.isDead()) {
					t.resurrect(game);
					t.setLife(game, Math.max(1, t.getMaximumLife() * (this.a > 0 ? this.a : 1)));
					if (t.getMaximumMana() > 0) {
						t.setMana(t.getMaximumMana() * this.b);
					}
					game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				}
				break;
			case DEVOUR:
				devour(game, caster, t);
				break;
			case UNSTABLE_CONCOCTION: {
				// Data A/B: full damage radius/amount, Data C/D: partial damage radius/amount
				final float fullRadius = Math.max(this.a, 50);
				game.getWorldCollision().enumUnitsInRange(t.getX(), t.getY(), Math.max(this.c, fullRadius), (u) -> {
					if (!u.isDead() && enemies(game, caster, u) && u.canBeTargetedBy(game, caster, getTargetsAllowed())) {
						final float dist = (float) StrictMath.hypot(u.getX() - t.getX(), u.getY() - t.getY());
						spellDamage(game, caster, u, (u == t) || (dist <= fullRadius) ? this.b : this.d,
								CDamageType.FIRE);
					}
					return false;
				});
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				caster.kill(game);
				break;
			}
			default:
				break;
			}
			return false;
		}

		private void purge(final CSimulation game, final CUnit caster, final CUnit t) {
			NxBuff.dispel(game, t);
			game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
			if (!enemies(game, caster, t)) {
				return;
			}
			if (t.getClassifications().contains(CUnitClassification.SUMMONED)) {
				spellDamage(game, caster, t, this.c, CDamageType.MAGIC);
			}
			if ((this.f > 0) && (t.getMana() > 0)) {
				t.setMana(Math.max(0, t.getMana() - this.f));
			}
			if (t.isDead()) {
				return;
			}
			// WC3 slows fully and lets the unit recover over the duration; here: a heavy slow for the duration
			NxBuff slow = buff(game, this.buff0, t).stat(NonStackingStatBuffType.MVSPDPCT, -0.75f);
			final float pause = t.isHero() ? this.e : this.d;
			if (pause > 0) {
				slow = slow.state(StateModBuffType.STUN);
			}
			NxBuff.apply(game, t, slow);
		}

		// ---- Spirit Link: Data A of the damage a linked unit takes is shared out among the others

		private void spiritLink(final CSimulation game, final CUnit caster, final CUnit t) {
			final int max = Math.max(1, (int) this.b);
			final List<CUnit> members = new ArrayList<>();
			members.add(t);
			final float radius = this.area > 0 ? this.area : 500;
			game.getWorldCollision().enumUnitsInRange(t.getX(), t.getY(), radius, (u) -> {
				if ((members.size() < max) && (u != t) && !u.isDead() && !u.isBuilding() && !enemies(game, caster, u)
						&& u.canBeTargetedBy(game, caster, getTargetsAllowed())) {
					members.add(u);
				}
				return false;
			});
			final List<CUnit> linked = new ArrayList<>(members);
			for (final CUnit member : members) {
				final NxBuff buff = buff(game, this.buff0, member);
				final CUnitAttackDamageTakenModificationListener share = (simulation, attacker, target, isAttack,
						isRanged, attackType, damageType, previous) -> {
					if (damageType == CDamageType.SPIRIT_LINK) {
						return previous;
					}
					final List<CUnit> others = new ArrayList<>();
					for (final CUnit o : linked) {
						if ((o != target) && !o.isDead()) {
							others.add(o);
						}
					}
					if (others.isEmpty()) {
						return previous;
					}
					final float total = (previous.getBaseDamage() + previous.getBonusDamage())
							* previous.getDamageMultiplier();
					final float shared = total * this.a;
					previous.setDamageMultiplier(previous.getDamageMultiplier() * (1 - this.a));
					for (final CUnit o : others) {
						o.damage(simulation, attacker, false, true, CAttackType.SPELLS, CDamageType.SPIRIT_LINK,
								CWeaponSoundTypeJass.WHOKNOWS.name(), shared / others.size());
					}
					return previous;
				};
				member.addDamageTakenModificationListener(share);
				NxBuff.apply(game, member, buff.onRemove((g, u) -> {
					u.removeDamageTakenModificationListener(share);
					linked.remove(u);
				}));
			}
		}

		// ---- Devour: the target is swallowed (hidden) and digested; the kodo dying lets it out

		private CUnit devoured;

		private void devour(final CSimulation game, final CUnit caster, final CUnit t) {
			this.devoured = t;
			t.setHidden(true);
			t.setPaused(true);
			t.setInvulnerable(true);
			game.getWorldCollision().removeUnit(t);
			final float perSecond = Math.max(10, t.getMaximumLife() / 30f);
			game.registerEffect(new CEffect() {
				private int next = game.getGameTurnTick() + ticks(1);

				@Override
				public boolean update(final CSimulation g) {
					if (t.isDead()) {
						TargetSpell.this.devoured = null;
						return true;
					}
					if (caster.isDead() || (g.getUnit(caster.getHandleId()) == null)) {
						release(g, caster, t);
						return true;
					}
					if (g.getGameTurnTick() >= this.next) {
						this.next = g.getGameTurnTick() + ticks(1);
						t.setInvulnerable(false);
						if (t.getLife() <= perSecond) {
							t.kill(g);
							TargetSpell.this.devoured = null;
							return true;
						}
						t.setLife(g, t.getLife() - perSecond);
						t.setInvulnerable(true);
					}
					return false;
				}
			});
		}

		private void release(final CSimulation game, final CUnit caster, final CUnit t) {
			this.devoured = null;
			t.setInvulnerable(false);
			t.setPaused(false);
			t.setHidden(false);
			t.setPointAndCheckUnstuck(caster.getX(), caster.getY(), game);
			game.getWorldCollision().addUnit(t);
		}

		@Override
		public void onDeath(final CSimulation game, final CUnit unit) {
			if (this.devoured != null) {
				release(game, unit, this.devoured);
			}
			super.onDeath(game, unit);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// no-target spells
	// ------------------------------------------------------------------------------------------------

	/** Berserk (Absk): Data A movement speed, Data B attack speed, Data C extra damage taken. */
	public static final class Berserk extends CAbilityNoTargetSpellBase {
		private float a, b, c;
		private War3ID buffId;

		public Berserk(final int handleId, final War3ID alias) {
			super(handleId, alias);
		}

		@Override
		public int getBaseOrderId() {
			return OrderIds.berserk;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = data(go, AbilityFields.DATA_A, level);
			this.b = data(go, AbilityFields.DATA_B, level);
			this.c = data(go, AbilityFields.DATA_C, level);
			this.buffId = AbstractCAbilityTypeDefinition.getBuffId(go, level);
			if (this.buffId == null) {
				this.buffId = getAlias();
			}
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			NxBuff.apply(game, caster,
					new NxBuff(game.getHandleIdAllocator().createId(), this.buffId, getDurationForTarget(caster))
							.stat(NonStackingStatBuffType.MVSPDPCT, this.a).stat(NonStackingStatBuffType.ATKSPD, this.b)
							.damageTaken(1 + this.c));
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			return false;
		}
	}

	/**
	 * Locust Swarm (AUls): Data A locusts, released one per Data B seconds, at most Data C on one target;
	 * each bites enemies around the caster (within Area) with the locust unit's damage and, after Data E
	 * damage, flies back to give the caster Data D of it as life.
	 */
	public static final class LocustSwarm extends CAbilityNoTargetSpellBase {
		private int count, perTarget;
		private float interval, returnFactor, returnThreshold, area;
		private War3ID locustType;

		public LocustSwarm(final int handleId, final War3ID alias) {
			super(handleId, alias);
		}

		@Override
		public int getBaseOrderId() {
			return OrderIds.locustswarm;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.count = Math.max(1, (int) data(go, AbilityFields.DATA_A, level));
			this.interval = Math.max(0.05f, data(go, AbilityFields.DATA_B, level));
			this.perTarget = Math.max(1, (int) data(go, AbilityFields.DATA_C, level));
			this.returnFactor = data(go, AbilityFields.DATA_D, level);
			this.returnThreshold = Math.max(1, data(go, AbilityFields.DATA_E, level));
			this.area = data(go, AbilityFields.AREA_OF_EFFECT, level);
			if (this.area <= 0) {
				this.area = 800;
			}
			final String unitId = go.getFieldAsString(AbilityFields.UNIT_ID + level, 0);
			this.locustType = (unitId != null) && (unitId.length() == 4) ? War3ID.fromString(unitId) : null;
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			float bite = 10;
			if (this.locustType != null) {
				final com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType type = game.getUnitData()
						.getUnitType(this.locustType);
				if ((type != null) && !type.getAttacks().isEmpty()) {
					final CUnitAttack attack = type.getAttacks().get(0);
					bite = Math.max(1, attack.getDamageBase() + ((attack.getDamageDice() * (attack.getDamageSidesPerDie() + 1)) / 2f));
				}
			}
			final float biteDamage = bite;
			final int end = game.getGameTurnTick() + ticks(getDuration() > 0 ? getDuration() : 30);
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			game.registerEffect(new CEffect() {
				private final int[] nextBite = new int[LocustSwarm.this.count];
				private final float[] carried = new float[LocustSwarm.this.count];
				private final CUnit[] on = new CUnit[LocustSwarm.this.count];
				private final Map<CUnit, Integer> load = new HashMap<>();

				{
					for (int i = 0; i < this.nextBite.length; i++) {
						this.nextBite[i] = game.getGameTurnTick() + ticks(LocustSwarm.this.interval * (i + 1));
					}
				}

				@Override
				public boolean update(final CSimulation g) {
					if (caster.isDead() || (g.getGameTurnTick() >= end)) {
						return true;
					}
					final int now = g.getGameTurnTick();
					for (int i = 0; i < this.nextBite.length; i++) {
						if (now < this.nextBite[i]) {
							continue;
						}
						CUnit t = this.on[i];
						if ((t == null) || t.isDead() || (dist(t, caster) > LocustSwarm.this.area)) {
							if (t != null) {
								this.load.merge(t, -1, Integer::sum);
							}
							t = pick(g);
							this.on[i] = t;
							if (t == null) {
								this.nextBite[i] = now + ticks(0.5f);
								continue;
							}
							this.load.merge(t, 1, Integer::sum);
						}
						spellDamage(g, caster, t, biteDamage, CDamageType.NORMAL);
						if ((g.getSeededRandom().nextInt(4)) == 0) {
							g.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
						}
						this.carried[i] += biteDamage;
						if (this.carried[i] >= LocustSwarm.this.returnThreshold) {
							caster.setLife(g, Math.min(caster.getMaximumLife(),
									caster.getLife() + (this.carried[i] * LocustSwarm.this.returnFactor)));
							this.carried[i] = 0;
							this.load.merge(t, -1, Integer::sum);
							this.on[i] = null;
							this.nextBite[i] = now + ticks(1.5f);
						}
						else {
							this.nextBite[i] = now + ticks(1.0f);
						}
					}
					return false;
				}

				private CUnit pick(final CSimulation g) {
					final List<CUnit> options = new ArrayList<>();
					g.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), LocustSwarm.this.area, (u) -> {
						if (!u.isDead() && !u.isHidden() && enemies(g, caster, u)
								&& u.canBeTargetedBy(g, caster, getTargetsAllowed())) {
							final Integer n = this.load.get(u);
							if ((n == null) || (n < LocustSwarm.this.perTarget)) {
								options.add(u);
							}
						}
						return false;
					});
					return options.isEmpty() ? null : options.get(g.getSeededRandom().nextInt(options.size()));
				}
			});
			return false;
		}
	}

	static float dist(final CUnit a, final CUnit b) {
		return (float) StrictMath.hypot(a.getX() - b.getX(), a.getY() - b.getY());
	}

	// ------------------------------------------------------------------------------------------------
	// point spells
	// ------------------------------------------------------------------------------------------------

	/**
	 * Dispel Magic / Disenchant (Adis code): removes the buffs of every unit in the area; enemy summons take
	 * Data B damage; Data A mana loss.
	 */
	public static final class AreaDispel extends CAbilityPointTargetSpellBase {
		private float manaLoss, summonDamage, area;

		public AreaDispel(final int handleId, final War3ID alias) {
			super(handleId, alias);
		}

		@Override
		public int getBaseOrderId() {
			return "Adcn".equals(getAlias().toString()) ? OrderIds.disenchant : OrderIds.dispel;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.manaLoss = data(go, AbilityFields.DATA_A, level);
			this.summonDamage = data(go, AbilityFields.DATA_B, level);
			this.area = data(go, AbilityFields.AREA_OF_EFFECT, level);
			if (this.area <= 0) {
				this.area = 200;
			}
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final AbilityPointTarget p = target.visit(AbilityTargetVisitor.POINT);
			final float x = p != null ? p.x : caster.getX();
			final float y = p != null ? p.y : caster.getY();
			game.getWorldCollision().enumUnitsInRange(x, y, this.area, (u) -> {
				if (u.isDead()) {
					return false;
				}
				NxBuff.dispel(game, u);
				if (enemies(game, caster, u)) {
					if (u.getClassifications().contains(CUnitClassification.SUMMONED)) {
						spellDamage(game, caster, u, this.summonDamage, CDamageType.MAGIC);
					}
					if ((this.manaLoss > 0) && (u.getMana() > 0)) {
						u.setMana(Math.max(0, u.getMana() - this.manaLoss));
					}
				}
				return false;
			});
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			return false;
		}
	}

	/**
	 * Stasis Trap (Asta): a trap unit (Unit ID, lives Duration seconds) that arms after Data A seconds; an
	 * enemy within Data B sets it off, stunning the enemies within Data C for Data D seconds (heroes: the
	 * hero duration when shorter).
	 */
	public static final class StasisTrap extends CAbilityPointTargetSpellBase {
		private float armDelay, detect, radius, stun;
		private War3ID trapType, buffId;

		public StasisTrap(final int handleId, final War3ID alias) {
			super(handleId, alias);
		}

		@Override
		public int getBaseOrderId() {
			return OrderIds.stasistrap;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.armDelay = data(go, AbilityFields.DATA_A, level);
			this.detect = data(go, AbilityFields.DATA_B, level);
			this.radius = data(go, AbilityFields.DATA_C, level);
			this.stun = data(go, AbilityFields.DATA_D, level);
			final String unitId = go.getFieldAsString(AbilityFields.UNIT_ID + level, 0);
			this.trapType = (unitId != null) && (unitId.length() == 4) ? War3ID.fromString(unitId) : null;
			this.buffId = AbstractCAbilityTypeDefinition.getBuffId(go, level);
			if (this.buffId == null) {
				this.buffId = getAlias();
			}
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final AbilityPointTarget p = target.visit(AbilityTargetVisitor.POINT);
			if ((p == null) || (this.trapType == null)) {
				return false;
			}
			final CUnit trap = game.createUnitSimple(this.trapType, caster.getPlayerIndex(), p.x, p.y,
					caster.getFacing());
			if (trap == null) {
				return false;
			}
			trap.addClassification(CUnitClassification.SUMMONED);
			trap.add(game, new CBuffTimedLife(game.getHandleIdAllocator().createId(), this.buffId,
					getDuration() > 0 ? getDuration() : 150, false));
			final int armed = game.getGameTurnTick() + ticks(this.armDelay);
			game.registerEffect(new CEffect() {
				@Override
				public boolean update(final CSimulation g) {
					if (trap.isDead() || (g.getUnit(trap.getHandleId()) == null)) {
						return true;
					}
					if (g.getGameTurnTick() < armed) {
						return false;
					}
					final boolean[] sprung = { false };
					g.getWorldCollision().enumUnitsInRange(trap.getX(), trap.getY(), StasisTrap.this.detect, (u) -> {
						if (!u.isDead() && !u.isBuilding() && enemies(g, trap, u) && (u.getFlyHeight() <= 0)) {
							sprung[0] = true;
							return true;
						}
						return false;
					});
					if (!sprung[0]) {
						return false;
					}
					g.getWorldCollision().enumUnitsInRange(trap.getX(), trap.getY(), StasisTrap.this.radius, (u) -> {
						if (!u.isDead() && !u.isBuilding() && enemies(g, trap, u)) {
							float seconds = StasisTrap.this.stun;
							if (u.isHero() && (getHeroDuration() > 0)) {
								seconds = Math.min(seconds, getHeroDuration());
							}
							u.add(g, new CBuffStun(g.getHandleIdAllocator().createId(), StasisTrap.this.buffId, seconds));
						}
						return false;
					});
					g.createTemporarySpellEffectOnUnit(trap, getAlias(), CEffectType.SPECIAL);
					trap.kill(g);
					return true;
				}
			});
			return false;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// passive attack effects
	// ------------------------------------------------------------------------------------------------

	/**
	 * Attack effects: Venom Spears / Slow Poison (Data A damage per second, Data B movement and Data C
	 * attack speed factor, for Duration), Liquid Fire (on buildings: Data A damage per second, Data B/C
	 * movement/attack speed reduction), Pillage (on enemy buildings: Data A of the damage's share of the
	 * building's cost as gold and lumber).
	 */
	public static final class AttackEffect extends CAbilityPassiveSpellBase {
		private final Recipe recipe;
		private float a, b, c;
		private War3ID buffId;
		private CUnit owner;
		private final CUnitAttackPostDamageListener listener = this::onHit;

		public AttackEffect(final int handleId, final War3ID alias, final Recipe recipe) {
			super(handleId, alias, alias);
			this.recipe = recipe;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = data(go, AbilityFields.DATA_A, level);
			this.b = data(go, AbilityFields.DATA_B, level);
			this.c = data(go, AbilityFields.DATA_C, level);
			this.buffId = AbstractCAbilityTypeDefinition.getBuffId(go, level);
			if (this.buffId == null) {
				this.buffId = getAlias();
			}
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			this.owner = unit;
			unit.addPostDamageListener(this.listener);
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			unit.removePostDamageListener(this.listener);
			super.onRemove(game, unit);
		}

		private void onHit(final CSimulation game, final CUnit attacker, final AbilityTarget target,
				final float damage) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || t.isDead() || !enemies(game, attacker, t)) {
				return;
			}
			switch (this.recipe) {
			case POISON:
				if (!t.isBuilding()) {
					final float seconds = getDurationForTarget(t);
					NxBuff.apply(game, t,
							new NxBuff(game.getHandleIdAllocator().createId(), this.buffId, seconds > 0 ? seconds : 5)
									.damageOverTime(attacker, this.a, CDamageType.POISON)
									.stat(NonStackingStatBuffType.MVSPDPCT, -this.b)
									.stat(NonStackingStatBuffType.ATKSPD, -this.c));
				}
				break;
			case LIQUID_FIRE:
				if (t.isBuilding()) {
					final float seconds = getDuration() > 0 ? getDuration() : 5;
					NxBuff.apply(game, t, new NxBuff(game.getHandleIdAllocator().createId(), this.buffId, seconds)
							.damageOverTime(attacker, this.a, CDamageType.FIRE)
							.stat(NonStackingStatBuffType.ATKSPD, -this.c));
				}
				break;
			case PILLAGE:
				if (t.isBuilding() && (t.getMaximumLife() > 0) && (damage > 0)) {
					final float share = (damage / t.getMaximumLife()) * this.a;
					final CPlayer player = game.getPlayer(attacker.getPlayerIndex());
					final int gold = (int) (t.getUnitType().getGoldCost() * share);
					final int lumber = (int) (t.getUnitType().getLumberCost() * share);
					if (gold > 0) {
						player.setGold(player.getGold() + gold);
					}
					if (lumber > 0) {
						player.setLumber(player.getLumber() + lumber);
					}
				}
				break;
			default:
				break;
			}
		}
	}
}
