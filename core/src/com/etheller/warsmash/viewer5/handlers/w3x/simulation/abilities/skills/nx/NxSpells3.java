package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.CAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.CAbilityTypeDefinitionSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPostDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementEffectPriority;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementStacking;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * warsmash-nx, third batch of Founding of Durotar spells: what enemies and creeps use (OpenWCIII has none of
 * them), driven by each ability's own data like {@link NxSpells}:
 * <ul>
 * <li>Locust: the dummy units map scripts create (unselectable, untargetable, invulnerable, no collision,
 * skipped by area searches; see CUnit.isNxLocust);</li>
 * <li>researched passives (the "Requires" upgrade must be done): Frag Shards and Flak Cannon (extra splash
 * Data C/D/E within Area/Data A/Data B; Frag Shards hits medium and unarmored ground units, Flak Cannon air
 * units), Gyrocopter Bombs (switches on the ground weapon), Rocket Attack/Barrage (switches on the air
 * weapon; every cooldown Data A to up to Data C air enemies within Area);</li>
 * <li>Hardened Skin, Cleaving Attack, Searing Arrows, Black Arrow, Permanent Immolation, Phase Shift;</li>
 * <li>on death: Spawn Spider/Hydra (Data A units of Data B), Death Damage (sapper), Phoenix (egg that
 * hatches);</li>
 * <li>target spells: Siphon Mana (channeled), Frost Nova, Forked Lightning, Entangling Roots, Anti-magic
 * Shield, Parasite, Frost Armor, Spell Steal;</li>
 * <li>point spells: Monsoon, Rain of Fire (channeled), Devour Magic, Rain of Chaos, Tornado (+ the tornado's
 * own damage, spin and wander);</li>
 * <li>no-target spells: Big Bad Voodoo (channeled), Howl of Terror, Taunt;</li>
 * <li>creep Devour and creep Animate Dead reuse {@link NxSpells} and {@link NxItems}; Detect (arcane tower)
 * reuses Far Sight's reveal.</li>
 * </ul>
 * Registered from NxSpells.register.
 */
public final class NxSpells3 {
	private static final War3ID TIMED_LIFE = War3ID.fromString("BTLF");

	private NxSpells3() {
	}

	static void register(final Map<War3ID, CAbilityTypeDefinition> map) {
		put(map, "Aloc", (h, a) -> new Locust(h, a));
		// handled elsewhere: the gryphon's line weapon is in its unit data (Storm Hammers), the kodo's Devour
		// holds the eaten unit itself (Cargo Hold), Lightning Attack only changes the missile art
		put(map, "Asth", (h, a) -> new Marker(h, a));
		put(map, "Advc", (h, a) -> new Marker(h, a));
		put(map, "Alit", (h, a) -> new Marker(h, a));
		put(map, "Afsh", (h, a) -> new Upgraded(h, a, Upgraded.FRAG));
		put(map, "Aflk", (h, a) -> new Upgraded(h, a, Upgraded.FLAK));
		put(map, "Agyb", (h, a) -> new Upgraded(h, a, Upgraded.GYRO_BOMBS));
		put(map, "Aroc", (h, a) -> new Upgraded(h, a, Upgraded.ROCKETS));
		put(map, "Assk", (h, a) -> new HardenedSkin(h, a));
		put(map, "ANca", (h, a) -> new Passive(h, a, Passive.CLEAVE));
		put(map, "AHfa", (h, a) -> new Passive(h, a, Passive.SEARING));
		put(map, "ANba", (h, a) -> new Passive(h, a, Passive.BLACK_ARROW));
		put(map, "ANpi", (h, a) -> new Passive(h, a, Passive.IMMOLATION));
		put(map, "Apsh", (h, a) -> new Passive(h, a, Passive.PHASE_SHIFT));
		put(map, "Atdg", (h, a) -> new Passive(h, a, Passive.TORNADO_DAMAGE));
		put(map, "Atsp", (h, a) -> new Passive(h, a, Passive.TORNADO_SPIN));
		put(map, "Atwa", (h, a) -> new Passive(h, a, Passive.TORNADO_WANDER));
		put(map, "Aspd", (h, a) -> new OnDeath(h, a, OnDeath.SPAWN));
		put(map, "Adda", (h, a) -> new OnDeath(h, a, OnDeath.SAPPER));
		put(map, "Aphx", (h, a) -> new OnDeath(h, a, OnDeath.PHOENIX));
		put(map, "AHdr", (h, a) -> new Target(h, a, Target.SIPHON, OrderIds.drain));
		put(map, "AUfn", (h, a) -> new Target(h, a, Target.FROST_NOVA, OrderIds.frostnova));
		put(map, "ANfl", (h, a) -> new Target(h, a, Target.FORKED, OrderIds.forkedlightning));
		put(map, "AEer", (h, a) -> new Target(h, a, Target.ROOTS, OrderIds.entanglingroots));
		put(map, "Aams", (h, a) -> new Target(h, a, Target.ANTIMAGIC, OrderIds.antimagicshell));
		put(map, "ANpa", (h, a) -> new Target(h, a, Target.PARASITE, OrderIds.parasite));
		put(map, "AUfu", (h, a) -> new Target(h, a, Target.FROST_ARMOR, OrderIds.frostarmor));
		put(map, "Asps", (h, a) -> new Target(h, a, Target.SPELL_STEAL, OrderIds.spellsteal));
		put(map, "ANmo", (h, a) -> new Point(h, a, Point.MONSOON, OrderIds.monsoon));
		put(map, "ANrf", (h, a) -> new Point(h, a, Point.RAIN_OF_FIRE, OrderIds.rainoffire));
		put(map, "Advm", (h, a) -> new Point(h, a, Point.DEVOUR_MAGIC, OrderIds.devourmagic));
		put(map, "ANrc", (h, a) -> new Point(h, a, Point.RAIN_OF_CHAOS, OrderIds.rainofchaos));
		put(map, "ANto", (h, a) -> new Point(h, a, Point.TORNADO, OrderIds.tornado));
		put(map, "AOvd", (h, a) -> new NoTarget(h, a, NoTarget.VOODOO, OrderIds.voodoo));
		put(map, "ANht", (h, a) -> new NoTarget(h, a, NoTarget.HOWL, OrderIds.howlofterror));
		put(map, "Atau", (h, a) -> new NoTarget(h, a, NoTarget.TAUNT, OrderIds.taunt));
		put(map, "ACdv", (h, a) -> new NxSpells.TargetSpell(h, a, NxSpells.Recipe.DEVOUR, OrderIds.creepdevour));
		put(map, "ACad", (h, a) -> new NxItems.AnimateDead(h, a, OrderIds.animatedead));
		put(map, "AIta", (h, a) -> new NxSpells2.Reveal(h, a, OrderIds.detectaoe, true));
	}

	private static void put(final Map<War3ID, CAbilityTypeDefinition> map, final String code,
			final CAbilityTypeDefinitionSpellBase.AbilityConstructor constructor) {
		map.putIfAbsent(War3ID.fromString(code), new CAbilityTypeDefinitionSpellBase(constructor));
	}

	// ------------------------------------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------------------------------------

	private static boolean air(final CUnit u) {
		return u.getUnitType().getMovementType() == MovementType.FLY;
	}

	private static float distance(final CUnit u, final float x, final float y) {
		final float dx = u.getX() - x;
		final float dy = u.getY() - y;
		return (float) StrictMath.sqrt((dx * dx) + (dy * dy));
	}

	private static float seconds(final float value, final float fallback) {
		return value > 0 ? value : fallback;
	}

	/** Damage that counts as a weapon's (armor applies as for a normal attack), not as a spell. */
	private static void attackDamage(final CSimulation game, final CUnit source, final CUnit target,
			final float amount) {
		if (amount > 0) {
			target.damage(game, source, false, true, CAttackType.NORMAL, CDamageType.NORMAL,
					CWeaponSoundTypeJass.WHOKNOWS.name(), amount);
		}
	}

	private static NxBuff buff(final CSimulation game, final War3ID id, final float seconds) {
		return new NxBuff(game.getHandleIdAllocator().createId(), id, seconds);
	}

	private static War3ID buffId(final GameObject go, final int level, final int index, final War3ID fallback) {
		final War3ID id = AbstractCAbilityTypeDefinition.getBuffId(go, level, index);
		return id != null ? id : fallback;
	}

	private static War3ID unitId(final GameObject go, final String field, final int level) {
		final String text = go.getFieldAsString(field + level, 0);
		if ((text == null) || (text.length() < 4)) {
			return null;
		}
		return War3ID.fromString(text.substring(0, 4));
	}

	/** A summoned unit for a player: timed life when seconds > 0. */
	private static CUnit summon(final CSimulation game, final War3ID type, final int playerIndex, final float x,
			final float y, final float facing, final float seconds) {
		if (type == null) {
			return null;
		}
		final CUnit u = game.createUnit(type, playerIndex, x, y, facing);
		if (u == null) {
			return null;
		}
		u.setPointAndCheckUnstuck(x, y, game);
		u.addClassification(CUnitClassification.SUMMONED);
		if (seconds > 0) {
			u.add(game, new CBuffTimedLife(game.getHandleIdAllocator().createId(), TIMED_LIFE, seconds, false));
		}
		game.createTemporarySpellEffectOnUnit(u, null, CEffectType.TARGET);
		return u;
	}

	/** Runs the action if the unit dies within the given seconds. */
	private static void onDeathWithin(final CSimulation game, final CUnit unit, final float seconds,
			final Runnable action) {
		final int end = game.getGameTurnTick() + NxSpells.ticks(seconds);
		game.registerEffect(new CEffect() {
			@Override
			public boolean update(final CSimulation g) {
				if (unit.isDead()) {
					action.run();
					return true;
				}
				return g.getGameTurnTick() > end;
			}
		});
	}

	// ------------------------------------------------------------------------------------------------
	// Locust and markers
	// ------------------------------------------------------------------------------------------------

	/** Locust: the map scripts' dummy units (cinematic props, effect casters). */
	public static final class Locust extends CAbilityPassiveSpellBase {
		public Locust(final int handleId, final War3ID alias) {
			super(handleId, alias, alias);
		}

		@Override
		public void populateData(final GameObject go, final int level) {
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			unit.setNxLocust(true);
			unit.setInvulnerable(true);
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			unit.setNxLocust(false);
			unit.setInvulnerable(false);
			super.onRemove(game, unit);
		}
	}

	/** An ability whose effect lives elsewhere (see register). */
	public static final class Marker extends CAbilityPassiveSpellBase {
		public Marker(final int handleId, final War3ID alias) {
			super(handleId, alias, alias);
		}

		@Override
		public void populateData(final GameObject go, final int level) {
		}
	}

	// ------------------------------------------------------------------------------------------------
	// researched passives
	// ------------------------------------------------------------------------------------------------

	public static final class Upgraded extends CAbilityPassiveSpellBase {
		static final int FRAG = 0, FLAK = 1, GYRO_BOMBS = 2, ROCKETS = 3;
		private final int kind;
		private float a, b, c, d, e, cooldown;
		private War3ID requires;
		private boolean weaponOn;
		private int nextVolley;
		private final CUnitAttackPostDamageListener listener = this::onHit;

		public Upgraded(final int handleId, final War3ID alias, final int kind) {
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
			this.cooldown = NxSpells.data(go, AbilityFields.COOLDOWN, level);
			final String req = go.getFieldAsString(AbilityFields.REQUIREMENTS, 0);
			this.requires = (req != null) && (req.length() >= 4) ? War3ID.fromString(req.substring(0, 4)) : null;
		}

		private boolean researched(final CSimulation game, final CUnit unit) {
			return (this.requires == null) || (game.getPlayer(unit.getPlayerIndex()).getTechtreeUnlocked(this.requires) > 0);
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			if ((this.kind == FRAG) || (this.kind == FLAK)) {
				unit.addPostDamageListener(this.listener);
			}
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			unit.removePostDamageListener(this.listener);
			if (this.weaponOn) {
				unit.nxEnableExtraAttack(game, 2, false);
				this.weaponOn = false;
			}
			super.onRemove(game, unit);
		}

		@Override
		public void onTick(final CSimulation game, final CUnit unit) {
			super.onTick(game, unit);
			if (((this.kind != GYRO_BOMBS) && (this.kind != ROCKETS)) || unit.isDead()) {
				return;
			}
			if (!this.weaponOn) {
				if (researched(game, unit)) {
					this.weaponOn = true;
					unit.nxEnableExtraAttack(game, 2, true);
				}
				return;
			}
			if ((this.kind != ROCKETS) || (game.getGameTurnTick() < this.nextVolley) || unit.isHidden()) {
				return;
			}
			// Barrage: rockets at up to Data C air enemies within Area, Data A each
			final List<CUnit> targets = new ArrayList<>();
			final float radius = seconds(getAreaOfEffect(), 500);
			game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), radius, (u) -> {
				if ((targets.size() < Math.max(1, (int) this.c)) && !u.isDead() && air(u)
						&& NxSpells.enemies(game, unit, u) && u.isVisible(game, unit.getPlayerIndex())) {
					targets.add(u);
				}
				return false;
			});
			if (targets.isEmpty()) {
				return;
			}
			this.nextVolley = game.getGameTurnTick() + NxSpells.ticks(seconds(this.cooldown, 2));
			for (final CUnit t : targets) {
				attackDamage(game, unit, t, this.a);
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
			}
		}

		private void onHit(final CSimulation game, final CUnit attacker, final AbilityTarget target,
				final float damage) {
			if (!researched(game, attacker)) {
				return;
			}
			final CUnit hit = target.visit(AbilityTargetVisitor.UNIT);
			final boolean flak = this.kind == FLAK;
			if (flak ? ((hit == null) || !air(hit)) : ((hit != null) && air(hit))) {
				return;
			}
			final float x = target.getX();
			final float y = target.getY();
			final float full = seconds(getAreaOfEffect(), 50);
			final float medium = Math.max(full, this.a);
			final float small = Math.max(medium, this.b);
			game.getWorldCollision().enumUnitsInRange(x, y, small, (u) -> {
				if (u.isDead() || u.isBuilding() || (air(u) != flak) || !NxSpells.enemies(game, attacker, u)) {
					return false;
				}
				if (!flak) {
					final CDefenseType armor = u.getUnitType().getDefenseType();
					if ((armor != CDefenseType.MEDIUM) && (armor != CDefenseType.NONE)) {
						return false;
					}
				}
				final float dist = distance(u, x, y);
				attackDamage(game, attacker, u, dist <= full ? this.c : (dist <= medium ? this.d : this.e));
				return false;
			});
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Hardened Skin
	// ------------------------------------------------------------------------------------------------

	/** Hardened Skin: Data A % of attacks lose Data C damage, but not below Data B. */
	public static final class HardenedSkin extends CAbilityPassiveSpellBase {
		private float chance, minimum, reduction;
		private final CUnitAttackDamageTakenModificationListener listener = (simulation, attacker, target, isAttack,
				isRanged, attackType, damageType, previous) -> {
			if (isAttack && ((simulation.getSeededRandom().nextFloat() * 100) < this.chance)) {
				final float base = previous.getBaseDamage();
				if (base > this.minimum) {
					previous.setBaseDamage(Math.max(this.minimum, base - this.reduction));
				}
			}
			return previous;
		};

		public HardenedSkin(final int handleId, final War3ID alias) {
			super(handleId, alias, alias);
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.chance = NxSpells.data(go, AbilityFields.DATA_A, level);
			this.minimum = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.reduction = NxSpells.data(go, AbilityFields.DATA_C, level);
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			unit.addDamageTakenModificationListener(this.listener);
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			unit.removeDamageTakenModificationListener(this.listener);
			super.onRemove(game, unit);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// other passives
	// ------------------------------------------------------------------------------------------------

	public static final class Passive extends CAbilityPassiveSpellBase {
		static final int CLEAVE = 0, SEARING = 1, BLACK_ARROW = 2, IMMOLATION = 3, PHASE_SHIFT = 4,
				TORNADO_DAMAGE = 5, TORNADO_SPIN = 6, TORNADO_WANDER = 7;
		private final int kind;
		private float a, b, c, manaCost, cooldown, range;
		private War3ID buff0, summonType;
		private int nextTick, readyTick;
		private float homeX, homeY;
		/** Black Arrow: units already marked (arrows still in flight must not raise a second minion). */
		private final java.util.Set<CUnit> marked = new java.util.HashSet<>();
		private final CUnitAttackPostDamageListener hitListener = this::onHit;
		private final CUnitAttackDamageTakenListener takenListener = this::onDamaged;

		public Passive(final int handleId, final War3ID alias, final int kind) {
			super(handleId, alias, alias);
			this.kind = kind;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = NxSpells.data(go, AbilityFields.DATA_A, level);
			this.b = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.c = NxSpells.data(go, AbilityFields.DATA_C, level);
			this.manaCost = NxSpells.data(go, AbilityFields.MANA_COST, level);
			this.cooldown = NxSpells.data(go, AbilityFields.COOLDOWN, level);
			this.range = NxSpells.data(go, AbilityFields.CAST_RANGE, level);
			this.buff0 = buffId(go, level, 0, getAlias());
			this.summonType = unitId(go, AbilityFields.UNIT_ID, level);
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			switch (this.kind) {
			case CLEAVE:
			case SEARING:
			case BLACK_ARROW:
				unit.addPostDamageListener(this.hitListener);
				break;
			case PHASE_SHIFT:
				unit.addDamageTakenListener(this.takenListener);
				break;
			default:
				break;
			}
			this.homeX = unit.getX();
			this.homeY = unit.getY();
			this.nextTick = game.getGameTurnTick() + NxSpells.ticks(1);
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			unit.removePostDamageListener(this.hitListener);
			unit.removeDamageTakenListener(this.takenListener);
			super.onRemove(game, unit);
		}

		private void onHit(final CSimulation game, final CUnit attacker, final AbilityTarget target,
				final float damage) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || !NxSpells.enemies(game, attacker, t)) {
				return;
			}
			switch (this.kind) {
			case CLEAVE: {
				// Data A of the damage to the other ground enemies within Area of the target
				final float radius = seconds(getAreaOfEffect(), 150);
				final float share = damage * this.a;
				game.getWorldCollision().enumUnitsInRange(t.getX(), t.getY(), radius, (u) -> {
					if ((u != t) && !u.isDead() && !air(u) && NxSpells.enemies(game, attacker, u)) {
						attackDamage(game, attacker, u, share);
					}
					return false;
				});
				break;
			}
			case SEARING:
			case BLACK_ARROW: {
				// the arrows cost mana per shot; without it the attack is a plain one
				if (attacker.getMana() < this.manaCost) {
					return;
				}
				if (this.kind == BLACK_ARROW) {
					// a late arrow landing on a unit that died earlier raises nothing; the killing arrow does
					final boolean diedEarlier = t.isDead() && (t.getDeathTurnTick() != game.getGameTurnTick());
					if (t.isHero() || t.isBuilding() || diedEarlier || !this.marked.add(t)) {
						return;
					}
					// a unit killed within the duration rises as Data B dark minions for Data C seconds
					final int count = Math.max(1, (int) this.b);
					final float life = this.c;
					final int owner = attacker.getPlayerIndex();
					final float watch = seconds(getDuration(), 2);
					onDeathWithin(game, t, watch, () -> {
						for (int i = 0; i < count; i++) {
							summon(game, this.summonType, owner, t.getX(), t.getY(), t.getFacing(), life);
						}
					});
					// a survivor can be marked again once this arrow's watch is over; a corpse is forgotten a few
					// seconds later, when no arrow shot at it can still land
					game.registerEffect(new CEffect() {
						private final int end = game.getGameTurnTick() + NxSpells.ticks(watch) + 1;
						private final int forget = this.end + NxSpells.ticks(5);

						@Override
						public boolean update(final CSimulation g) {
							final int tick = g.getGameTurnTick();
							if ((tick > this.forget) || (!t.isDead() && (tick > this.end))) {
								Passive.this.marked.remove(t);
								return true;
							}
							return false;
						}
					});
				}
				attacker.setMana(attacker.getMana() - this.manaCost);
				if (!t.isDead()) {
					NxSpells.spellDamage(game, attacker, t, this.a,
							this.kind == SEARING ? CDamageType.FIRE : CDamageType.MAGIC);
				}
				break;
			}
			default:
				break;
			}
		}

		private void onDamaged(final CSimulation game, final CUnit attacker, final CUnit target, final boolean isAttack,
				final boolean isRanged, final CDamageType weaponType, final float damage, final float bonusDamage,
				final float trueDamage) {
			// Phase Shift: when hit, the unit shifts out of reality for Duration (mana cost, cooldown)
			if ((this.kind != PHASE_SHIFT) || target.isDead() || (game.getGameTurnTick() < this.readyTick)
					|| (target.getMana() < this.manaCost)) {
				return;
			}
			target.setMana(target.getMana() - this.manaCost);
			this.readyTick = game.getGameTurnTick() + NxSpells.ticks(seconds(this.cooldown, 6));
			NxBuff.apply(game, target, buff(game, this.buff0, seconds(getDuration(), 1.5f))
					.state(StateModBuffType.INVULNERABLE));
			game.createTemporarySpellEffectOnUnit(target, getAlias(), CEffectType.CASTER);
		}

		@Override
		public void onTick(final CSimulation game, final CUnit unit) {
			super.onTick(game, unit);
			if ((this.kind < IMMOLATION) || (this.kind == PHASE_SHIFT) || unit.isDead() || unit.isHidden()
					|| (game.getGameTurnTick() < this.nextTick)) {
				return;
			}
			switch (this.kind) {
			case IMMOLATION:
				this.nextTick = game.getGameTurnTick() + NxSpells.ticks(1);
				game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), seconds(getAreaOfEffect(), 160),
						(u) -> {
							if ((u != unit) && !u.isDead() && !u.isBuilding() && NxSpells.enemies(game, unit, u)
									&& u.canBeTargetedBy(game, unit, getTargetsAllowed())) {
								NxSpells.spellDamage(game, unit, u, this.a, CDamageType.FIRE);
							}
							return false;
						});
				break;
			case TORNADO_DAMAGE:
				// buildings near the tornado take Data A per second
				this.nextTick = game.getGameTurnTick() + NxSpells.ticks(1);
				game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), seconds(getAreaOfEffect(), 650),
						(u) -> {
							if (u.isBuilding() && !u.isDead() && NxSpells.enemies(game, unit, u)) {
								NxSpells.spellDamage(game, unit, u, this.a, CDamageType.NORMAL);
							}
							return false;
						});
				break;
			case TORNADO_SPIN:
				// ground enemies close to it are thrown about: stunned while they stay near, Data A per second
				this.nextTick = game.getGameTurnTick() + NxSpells.ticks(1);
				game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), seconds(getAreaOfEffect(), 275),
						(u) -> {
							if ((u != unit) && !u.isDead() && !u.isBuilding() && !air(u)
									&& NxSpells.enemies(game, unit, u)) {
								NxBuff.apply(game, u, buff(game, this.buff0, 1.1f).state(StateModBuffType.STUN));
								NxSpells.spellDamage(game, unit, u, this.a, CDamageType.NORMAL);
							}
							return false;
						});
				break;
			case TORNADO_WANDER: {
				// a new spot within Range of where it appeared every Duration seconds
				this.nextTick = game.getGameTurnTick() + NxSpells.ticks(seconds(getDuration(), 2));
				final float angle = game.getSeededRandom().nextFloat() * (float) (StrictMath.PI * 2);
				final float dist = game.getSeededRandom().nextFloat() * seconds(this.range, 600);
				unit.order(game, OrderIds.move, new AbilityPointTarget(
						this.homeX + ((float) StrictMath.cos(angle) * dist),
						this.homeY + ((float) StrictMath.sin(angle) * dist)));
				break;
			}
			default:
				break;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// on death
	// ------------------------------------------------------------------------------------------------

	public static final class OnDeath extends CAbilityPassiveSpellBase {
		static final int SPAWN = 0, SAPPER = 1, PHOENIX = 2;
		private final int kind;
		private float a, b, c, d;
		private War3ID spawnType, eggType;
		private final CUnitDeathReplacementEffect effect = this::onDeath;

		public OnDeath(final int handleId, final War3ID alias, final int kind) {
			super(handleId, alias, alias);
			this.kind = kind;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.a = NxSpells.data(go, AbilityFields.DATA_A, level);
			this.b = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.c = NxSpells.data(go, AbilityFields.DATA_C, level);
			this.d = NxSpells.data(go, AbilityFields.DATA_D, level);
			// Spawn: Data B is the unit; Phoenix: Data A is the bird, UnitID the egg
			this.spawnType = unitId(go, this.kind == PHOENIX ? AbilityFields.DATA_A : AbilityFields.DATA_B, level);
			this.eggType = unitId(go, AbilityFields.UNIT_ID, level);
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			unit.addDeathReplacementEffect(CUnitDeathReplacementEffectPriority.GENERALONDEATHACTIONS, this.effect);
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			unit.removeDeathReplacementEffect(CUnitDeathReplacementEffectPriority.GENERALONDEATHACTIONS, this.effect);
			super.onRemove(game, unit);
		}

		private CUnitDeathReplacementStacking onDeath(final CSimulation game, final CUnit unit, final CUnit killer,
				final com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementResult result) {
			final float x = unit.getX();
			final float y = unit.getY();
			switch (this.kind) {
			case SPAWN: {
				final int count = Math.max(1, (int) this.a);
				for (int i = 0; i < count; i++) {
					final float angle = (float) ((StrictMath.PI * 2 * i) / count);
					final CUnit u = game.createUnit(this.spawnType, unit.getPlayerIndex(),
							x + ((float) StrictMath.cos(angle) * 40), y + ((float) StrictMath.sin(angle) * 40),
							unit.getFacing());
					if (u != null) {
						u.setPointAndCheckUnstuck(u.getX(), u.getY(), game);
					}
				}
				break;
			}
			case SAPPER: {
				// Data B within Data A, Data D within Data C
				final float full = seconds(this.a, 100);
				final float partial = Math.max(full, this.c);
				game.getWorldCollision().enumUnitsInRange(x, y, partial, (u) -> {
					if ((u != unit) && !u.isDead() && !air(u) && NxSpells.enemies(game, unit, u)) {
						attackDamage(game, unit, u, distance(u, x, y) <= full ? this.b : this.d);
					}
					return false;
				});
				game.spawnTemporarySpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.EFFECT, 0);
				break;
			}
			case PHOENIX: {
				// the egg hatches into a new phoenix unless it is destroyed first
				final CUnit egg = game.createUnit(this.eggType, unit.getPlayerIndex(), x, y, unit.getFacing());
				if (egg == null) {
					break;
				}
				final War3ID bird = this.spawnType != null ? this.spawnType : unit.getTypeId();
				final int hatch = game.getGameTurnTick() + NxSpells.ticks(seconds(this.b, 7));
				game.registerEffect(new CEffect() {
					@Override
					public boolean update(final CSimulation g) {
						if (egg.isDead()) {
							return true;
						}
						if (g.getGameTurnTick() < hatch) {
							return false;
						}
						final CUnit phoenix = g.createUnit(bird, egg.getPlayerIndex(), egg.getX(), egg.getY(),
								egg.getFacing());
						g.removeUnit(egg);
						if (phoenix != null) {
							phoenix.addClassification(CUnitClassification.SUMMONED);
							g.createTemporarySpellEffectOnUnit(phoenix, getAlias(), CEffectType.TARGET);
						}
						return true;
					}
				});
				break;
			}
			default:
				break;
			}
			return new CUnitDeathReplacementStacking(true, true);
		}
	}

	// ------------------------------------------------------------------------------------------------
	// unit-target spells
	// ------------------------------------------------------------------------------------------------

	public static final class Target extends CAbilityTargetSpellBase {
		static final int SIPHON = 0, FROST_NOVA = 1, FORKED = 2, ROOTS = 3, ANTIMAGIC = 4, PARASITE = 5,
				FROST_ARMOR = 6, SPELL_STEAL = 7;
		private final int kind;
		private final int orderId;
		private float a, b, c, d, area, minionSeconds;
		private War3ID buff0, buff1, summonType;
		private int endTick, nextTick;

		public Target(final int handleId, final War3ID alias, final int kind, final int orderId) {
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
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			this.buff0 = buffId(go, level, 0, getAlias());
			this.buff1 = buffId(go, level, 1, this.buff0);
			this.summonType = unitId(go, AbilityFields.UNIT_ID, level);
			// Parasite keeps its minion's lifetime in the casting time field
			this.minionSeconds = NxSpells.data(go, AbilityFields.CASTING_TIME, level);
		}

		@Override
		public float getCastingTime() {
			return this.kind == PARASITE ? 0 : super.getCastingTime();
		}

		private NxBuff buffFor(final CSimulation game, final War3ID id, final CUnit t) {
			return buff(game, id, seconds(getDurationForTarget(t), 1));
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || t.isDead()) {
				return false;
			}
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			switch (this.kind) {
			case SIPHON:
				this.endTick = game.getGameTurnTick() + NxSpells.ticks(seconds(getDuration(), 6));
				this.nextTick = game.getGameTurnTick();
				return true;
			case FROST_NOVA: {
				// Data B to the target, Data A to every enemy within Area (the target too), all slowed
				NxSpells.spellDamage(game, caster, t, this.b, CDamageType.COLD);
				final float radius = seconds(this.area, 200);
				game.getWorldCollision().enumUnitsInRange(t.getX(), t.getY(), radius, (u) -> {
					if (!u.isDead() && !u.isBuilding() && NxSpells.enemies(game, caster, u)
							&& u.canBeTargetedBy(game, caster, getTargetsAllowed())) {
						NxSpells.spellDamage(game, caster, u, this.a, CDamageType.COLD);
						NxBuff.apply(game, u, buffFor(game, this.buff0, u).stat(NonStackingStatBuffType.MVSPDPCT, -0.5f)
								.stat(NonStackingStatBuffType.ATKSPD, -0.25f));
					}
					return false;
				});
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				break;
			}
			case FORKED:
				forkedLightning(game, caster, t);
				break;
			case ROOTS:
				NxBuff.apply(game, t, buffFor(game, this.buff0, t).state(StateModBuffType.SNARED)
						.state(StateModBuffType.DISABLE_ATTACK).damageOverTime(caster, this.a, CDamageType.MAGIC));
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				break;
			case ANTIMAGIC:
				NxBuff.dispel(game, t);
				NxBuff.apply(game, t, buffFor(game, this.buff0, t).state(StateModBuffType.MAGIC_IMMUNE));
				break;
			case PARASITE: {
				// Data A per second; if it dies meanwhile, a minion (UnitID) rises for the caster
				final float duration = seconds(getDurationForTarget(t), 30);
				NxBuff.apply(game, t,
						buff(game, this.buff0, duration).damageOverTime(caster, this.a, CDamageType.MAGIC));
				final int owner = caster.getPlayerIndex();
				onDeathWithin(game, t, duration, () -> summon(game, this.summonType, owner, t.getX(), t.getY(),
						t.getFacing(), seconds(this.minionSeconds, 60)));
				break;
			}
			case FROST_ARMOR:
				// Data A seconds of Data B armor
				NxBuff.apply(game, t, buff(game, this.buff0, seconds(this.a, 45)).stat(NonStackingStatBuffType.DEF,
						this.b));
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
				break;
			case SPELL_STEAL:
				spellSteal(game, caster, t);
				break;
			default:
				break;
			}
			return false;
		}

		@Override
		public boolean doChannelTick(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			if (this.kind != SIPHON) {
				return false;
			}
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			final int tick = game.getGameTurnTick();
			if ((t == null) || t.isDead() || (tick >= this.endTick)
					|| (NxSpells.dist(caster, t) > seconds(this.area, 800))) {
				return false;
			}
			if (tick >= this.nextTick) {
				// Siphon Mana: Data B mana per second from the target to the caster
				this.nextTick = tick + NxSpells.ticks(1);
				final float drained = Math.min(t.getMana(), this.b);
				if (drained > 0) {
					t.setMana(t.getMana() - drained);
					caster.setMana(Math.min(caster.getMaximumMana(), caster.getMana() + drained));
				}
				game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
			}
			return true;
		}

		/** Forked Lightning: Data A to the target and up to Data B units in all in a cone towards it. */
		private void forkedLightning(final CSimulation game, final CUnit caster, final CUnit t) {
			final float length = seconds(this.c, 800);
			final float halfWidth = seconds(this.d, 300) / 2;
			final double heading = StrictMath.atan2(t.getY() - caster.getY(), t.getX() - caster.getX());
			final List<CUnit> hit = new ArrayList<>();
			game.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), length, (u) -> {
				if ((u == t) || u.isDead() || u.isBuilding() || !NxSpells.enemies(game, caster, u)
						|| !u.canBeTargetedBy(game, caster, getTargetsAllowed())) {
					return false;
				}
				final double dx = u.getX() - caster.getX();
				final double dy = u.getY() - caster.getY();
				final double along = (dx * StrictMath.cos(heading)) + (dy * StrictMath.sin(heading));
				final double across = Math.abs((-dx * StrictMath.sin(heading)) + (dy * StrictMath.cos(heading)));
				if ((along > 0) && (across <= (halfWidth * (along / length)) + 50)) {
					hit.add(u);
				}
				return false;
			});
			hit.sort(Comparator.comparingDouble((u) -> NxSpells.dist(caster, u)));
			final int max = Math.max(1, (int) this.b);
			final List<CUnit> struck = new ArrayList<>();
			struck.add(t);
			for (final CUnit u : hit) {
				if (struck.size() >= max) {
					break;
				}
				struck.add(u);
			}
			for (final CUnit u : struck) {
				NxSpells.spellDamage(game, caster, u, this.a, CDamageType.LIGHTNING);
				game.createTemporarySpellEffectOnUnit(u, getAlias(), CEffectType.TARGET);
			}
		}

		/**
		 * Spell Steal: a blessing taken from an enemy goes to an ally of the caster near it; a curse taken from
		 * an ally goes to an enemy near it.
		 */
		private void spellSteal(final CSimulation game, final CUnit caster, final CUnit t) {
			final boolean fromEnemy = NxSpells.enemies(game, caster, t);
			NxBuff stolen = null;
			for (final CAbility ability : new ArrayList<>(t.getAbilities())) {
				if ((ability instanceof NxBuff) && (fromEnemy ? ((NxBuff) ability).isHelpful()
						: ((NxBuff) ability).isHarmful())) {
					stolen = (NxBuff) ability;
					break;
				}
			}
			if (stolen == null) {
				return;
			}
			final float radius = seconds(this.area, 700);
			final CUnit[] receiver = new CUnit[1];
			final float[] best = { Float.MAX_VALUE };
			game.getWorldCollision().enumUnitsInRange(t.getX(), t.getY(), radius, (u) -> {
				if ((u == t) || u.isDead() || u.isBuilding()) {
					return false;
				}
				final boolean enemy = NxSpells.enemies(game, caster, u);
				if (enemy != !fromEnemy) {
					return false;
				}
				final float dist = NxSpells.dist(t, u);
				if (dist < best[0]) {
					best[0] = dist;
					receiver[0] = u;
				}
				return false;
			});
			t.remove(game, stolen);
			game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
			if (receiver[0] != null) {
				NxBuff.apply(game, receiver[0], stolen);
				game.createTemporarySpellEffectOnUnit(receiver[0], getAlias(), CEffectType.SPECIAL);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// point-target spells
	// ------------------------------------------------------------------------------------------------

	public static final class Point extends CAbilityPointTargetSpellBase {
		static final int MONSOON = 0, RAIN_OF_FIRE = 1, DEVOUR_MAGIC = 2, RAIN_OF_CHAOS = 3, TORNADO = 4;
		private static final War3ID INFERNAL = War3ID.fromString("ninf");
		private final int kind;
		private final int orderId;
		private float a, b, c, d, e, area;
		private War3ID buff0, summonType;
		private int wavesLeft, nextTick;

		public Point(final int handleId, final War3ID alias, final int kind, final int orderId) {
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
			this.buff0 = buffId(go, level, 0, getAlias());
			this.summonType = unitId(go, AbilityFields.UNIT_ID, level);
		}

		@Override
		public float getUIAreaOfEffect() {
			return this.area;
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final float x = target.getX();
			final float y = target.getY();
			final float radius = seconds(this.area, 200);
			switch (this.kind) {
			case MONSOON: {
				// Data A to every enemy in the area every Data B seconds (buildings times Data C), for Duration
				final int end = game.getGameTurnTick() + NxSpells.ticks(seconds(getDuration(), 12));
				game.spawnTemporarySpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.EFFECT, 0);
				game.registerEffect(new CEffect() {
					private int next = game.getGameTurnTick();

					@Override
					public boolean update(final CSimulation g) {
						final int tick = g.getGameTurnTick();
						if (tick > end) {
							return true;
						}
						if (tick >= this.next) {
							this.next = tick + NxSpells.ticks(seconds(Point.this.b, 1.5f));
							g.getWorldCollision().enumUnitsInRange(x, y, radius, (u) -> {
								if (!u.isDead() && NxSpells.enemies(g, caster, u)) {
									NxSpells.spellDamage(g, caster, u,
											u.isBuilding() ? Point.this.a * seconds(Point.this.c, 1) : Point.this.a,
											CDamageType.LIGHTNING);
									g.createTemporarySpellEffectOnUnit(u, getAlias(), CEffectType.TARGET);
								}
								return false;
							});
						}
						return false;
					}
				});
				return false;
			}
			case RAIN_OF_FIRE:
				// channeled: Data A waves, one a second, of Data B (buildings times Data D); Data E burn per second
				this.wavesLeft = Math.max(1, (int) this.a);
				this.nextTick = game.getGameTurnTick();
				return true;
			case DEVOUR_MAGIC: {
				// dispels the area; Data A life and Data B mana to the caster per buff, Data E to summons
				final int[] removed = { 0 };
				game.getWorldCollision().enumUnitsInRange(x, y, radius, (u) -> {
					if (u.isDead()) {
						return false;
					}
					removed[0] += NxBuff.dispel(game, u);
					if (NxSpells.enemies(game, caster, u) && u.getClassifications().contains(CUnitClassification.SUMMONED)) {
						NxSpells.spellDamage(game, caster, u, this.e, CDamageType.MAGIC);
					}
					return false;
				});
				if (removed[0] > 0) {
					caster.heal(game, this.a * removed[0]);
					caster.setMana(Math.min(caster.getMaximumMana(), caster.getMana() + (this.b * removed[0])));
				}
				game.spawnTemporarySpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.EFFECT, 0);
				return false;
			}
			case RAIN_OF_CHAOS: {
				// Data B infernals fall in the area: 50 damage and a 1 s stun where each lands
				final int count = Math.max(1, (int) this.b);
				for (int i = 0; i < count; i++) {
					final float angle = game.getSeededRandom().nextFloat() * (float) (StrictMath.PI * 2);
					final float dist = game.getSeededRandom().nextFloat() * (radius / 2);
					final float ix = x + ((float) StrictMath.cos(angle) * dist);
					final float iy = y + ((float) StrictMath.sin(angle) * dist);
					game.getWorldCollision().enumUnitsInRange(ix, iy, 250, (u) -> {
						if (!u.isDead() && NxSpells.enemies(game, caster, u)) {
							NxSpells.spellDamage(game, caster, u, 50, CDamageType.FIRE);
							NxBuff.apply(game, u, buff(game, getAlias(), 1).state(StateModBuffType.STUN));
						}
						return false;
					});
					summon(game, INFERNAL, caster.getPlayerIndex(), ix, iy, caster.getFacing(), 0);
				}
				return false;
			}
			case TORNADO:
				summon(game, this.summonType, caster.getPlayerIndex(), x, y, caster.getFacing(),
						seconds(getDuration(), 40));
				return false;
			default:
				return false;
			}
		}

		@Override
		public boolean doChannelTick(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			if (this.kind != RAIN_OF_FIRE) {
				return false;
			}
			final int tick = game.getGameTurnTick();
			if (tick < this.nextTick) {
				return true;
			}
			if (this.wavesLeft <= 0) {
				return false;
			}
			this.wavesLeft--;
			this.nextTick = tick + NxSpells.ticks(1);
			final float x = target.getX();
			final float y = target.getY();
			game.spawnTemporarySpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.EFFECT, 0);
			game.getWorldCollision().enumUnitsInRange(x, y, seconds(this.area, 200), (u) -> {
				if (!u.isDead() && NxSpells.enemies(game, caster, u)) {
					NxSpells.spellDamage(game, caster, u, u.isBuilding() ? this.b * seconds(this.d, 1) : this.b,
							CDamageType.FIRE);
					if ((this.e > 0) && !u.isDead() && !u.isBuilding()) {
						NxBuff.apply(game, u, buff(game, this.buff0, seconds(getDuration(), 3))
								.damageOverTime(caster, this.e, CDamageType.FIRE));
					}
				}
				return false;
			});
			return true;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// no-target spells
	// ------------------------------------------------------------------------------------------------

	public static final class NoTarget extends CAbilityNoTargetSpellBase {
		static final int VOODOO = 0, HOWL = 1, TAUNT = 2;
		private final int kind;
		private final int orderId;
		private float a, area;
		private War3ID buff0;
		private int endTick, nextTick;

		public NoTarget(final int handleId, final War3ID alias, final int kind, final int orderId) {
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
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			this.buff0 = buffId(go, level, 0, getAlias());
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final float radius = seconds(this.area, 500);
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			switch (this.kind) {
			case VOODOO:
				// channeled: friendly non-hero units within Area are invulnerable while it lasts
				this.endTick = game.getGameTurnTick() + NxSpells.ticks(seconds(getDuration(), 30));
				this.nextTick = game.getGameTurnTick();
				return true;
			case HOWL:
				// enemies within Area deal Data A less damage for Duration
				game.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), radius, (u) -> {
					if (!u.isDead() && !u.isBuilding() && NxSpells.enemies(game, caster, u)
							&& u.canBeTargetedBy(game, caster, getTargetsAllowed())) {
						NxBuff.apply(game, u, buff(game, this.buff0, seconds(getDurationForTarget(u), 15))
								.stat(NonStackingStatBuffType.MELEEATKPCT, -this.a)
								.stat(NonStackingStatBuffType.RNGDATKPCT, -this.a));
					}
					return false;
				});
				return false;
			case TAUNT:
				// enemies within Area turn on the caster
				game.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), radius, (u) -> {
					if (!u.isDead() && !u.isBuilding() && NxSpells.enemies(game, caster, u)) {
						u.order(game, OrderIds.attack, caster);
					}
					return false;
				});
				return false;
			default:
				return false;
			}
		}

		@Override
		public boolean doChannelTick(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			if (this.kind != VOODOO) {
				return false;
			}
			final int tick = game.getGameTurnTick();
			if (tick >= this.endTick) {
				return false;
			}
			if (tick >= this.nextTick) {
				this.nextTick = tick + NxSpells.ticks(0.5f);
				game.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), seconds(this.area, 800),
						(u) -> {
							if ((u != caster) && !u.isDead() && !u.isHero() && !u.isBuilding()
									&& !NxSpells.enemies(game, caster, u)) {
								NxBuff.apply(game, u, buff(game, this.buff0, 1).state(StateModBuffType.INVULNERABLE));
							}
							return false;
						});
			}
			return true;
		}
	}
}
