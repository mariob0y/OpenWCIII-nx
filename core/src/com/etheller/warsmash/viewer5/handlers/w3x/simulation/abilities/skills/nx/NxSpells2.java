package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nx;

import java.util.Map;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementEffectPriority;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementStacking;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.CAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.CAbilityTypeDefinitionSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPostDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CTimedCircleFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * warsmash-nx, second batch of Founding of Durotar spells (OpenWCIII has none of them), driven by the
 * abilities' own data like {@link NxSpells}:
 * <ul>
 * <li>Far Sight (Thrall) and Flare (mortar team): reveal Area around the point for Duration;</li>
 * <li>Earthquake (Thrall, channeled): after Data A seconds, Data B damage per second to buildings and Data C
 * slow to units within Area, for Duration;</li>
 * <li>Cyclone (harpy queen, sea witch...): the target is lifted out of the fight (stunned, invulnerable) for
 * Duration (Hero Duration on heroes);</li>
 * <li>Pulverize (tauren, creep giants): Data A % of attacks deal Data B to enemies within Data C, half within
 * Data D, around the attacker;</li>
 * <li>Feedback (spell breaker, arcane tower): each attack burns up to Data A mana (heroes Data C) and deals
 * it times Data B (heroes Data D) as extra damage; Data E extra damage to summoned units;</li>
 * <li>Burning Oil (demolisher): the attacked ground burns for Duration: Data A per Data B seconds within
 * half the Area, Data C per Data D seconds within the Area, buildings times Data E;</li>
 * <li>Wander (critters): an idle unit walks to a random nearby spot every few seconds;</li>
 * <li>Spiked Barricades (orc buildings): once the Rosp upgrade is researched, melee attackers take 5 plus
 * 20 % (level 2: 50 %) of the damage they deal (upgradedata.slk rspi/rspp);</li>
 * <li>Abolish Magic (creep casters, autocast): removes the target's buffs; summoned enemies take Data B;</li>
 * <li>Mirror Image (Samuro): Data A images of the hero (same level, no spells, Data B of its damage, Data C
 * times the damage taken) for Duration, the hero hidden among them; an image that dies or expires just
 * vanishes (no corpse, no bounty, no revive). Images are units of the hero's type: there is no illusion
 * flag in the engine, so triggers counting units of that type see them.</li>
 * </ul>
 * Registered from NxSpells.register.
 */
public final class NxSpells2 {
	private NxSpells2() {
	}

	static void register(final Map<War3ID, CAbilityTypeDefinition> map) {
		put(map, "AOfs", (h, a) -> new Reveal(h, a, OrderIds.farsight, false));
		put(map, "Afla", (h, a) -> new Reveal(h, a, OrderIds.flare, true));
		put(map, "AOeq", (h, a) -> new Earthquake(h, a));
		put(map, "Acyc", (h, a) -> new Cyclone(h, a));
		put(map, "Awar", (h, a) -> new OnAttack(h, a, OnAttack.PULVERIZE));
		put(map, "Afbk", (h, a) -> new OnAttack(h, a, OnAttack.FEEDBACK));
		put(map, "Abof", (h, a) -> new OnAttack(h, a, OnAttack.BURNING_OIL));
		put(map, "Awan", (h, a) -> new Wander(h, a));
		put(map, "Aspi", (h, a) -> new SpikedBarricades(h, a));
		put(map, "Aadm", (h, a) -> new AbolishMagic(h, a));
		put(map, "AOmi", (h, a) -> new MirrorImage(h, a));
	}

	private static void put(final Map<War3ID, CAbilityTypeDefinition> map, final String code,
			final CAbilityTypeDefinitionSpellBase.AbilityConstructor constructor) {
		map.put(War3ID.fromString(code), new CAbilityTypeDefinitionSpellBase(constructor));
	}

	private static boolean air(final CUnit u) {
		return u.getUnitType().getMovementType() == MovementType.FLY;
	}

	private static float distance(final CUnit u, final float x, final float y) {
		final float dx = u.getX() - x;
		final float dy = u.getY() - y;
		return (float) StrictMath.sqrt((dx * dx) + (dy * dy));
	}

	/** Far Sight / Flare: the caster's player sees Area around the point for Duration. */
	public static final class Reveal extends CAbilityPointTargetSpellBase {
		private final int orderId;
		private final boolean flare;
		private float area, delay;

		public Reveal(final int handleId, final War3ID alias, final int orderId, final boolean flare) {
			super(handleId, alias);
			this.orderId = orderId;
			this.flare = flare;
		}

		@Override
		public int getBaseOrderId() {
			return this.orderId;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			if (this.area <= 0) {
				this.area = 900;
			}
			this.delay = this.flare ? NxSpells.data(go, AbilityFields.DATA_B, level) : 0;
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
			final float seconds = getDuration() > 0 ? getDuration() : 8;
			game.getPlayer(caster.getPlayerIndex()).addFogModifer(game,
					new CTimedCircleFogModifier(CFogState.VISIBLE, this.area, x, y, seconds + this.delay), false);
			game.spawnTemporarySpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.EFFECT, 0);
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			return false;
		}
	}

	/** Earthquake: channeled; buildings in the area take damage, units in it are slowed. */
	public static final class Earthquake extends CAbilityPointTargetSpellBase {
		private float delay, buildingDamage, slow, area;
		private War3ID buffId;
		private int startTick, endTick, nextTick;
		private SimulationRenderComponentModel art;

		public Earthquake(final int handleId, final War3ID alias) {
			super(handleId, alias);
		}

		@Override
		public int getBaseOrderId() {
			return OrderIds.earthquake;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.delay = NxSpells.data(go, AbilityFields.DATA_A, level);
			this.buildingDamage = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.slow = NxSpells.data(go, AbilityFields.DATA_C, level);
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			if (this.area <= 0) {
				this.area = 250;
			}
			this.buffId = AbstractCAbilityTypeDefinition.getBuffId(go, level, 0);
			if (this.buffId == null) {
				this.buffId = getAlias();
			}
		}

		@Override
		public float getUIAreaOfEffect() {
			return this.area;
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final float seconds = caster.isHero() && (getHeroDuration() > 0) ? getHeroDuration() : getDuration();
			this.startTick = game.getGameTurnTick() + NxSpells.ticks(this.delay);
			this.endTick = this.startTick + NxSpells.ticks(seconds > 0 ? seconds : 20);
			this.nextTick = this.startTick;
			removeArt();
			this.art = game.spawnSpellEffectOnPoint(target.getX(), target.getY(), 0, getAlias(), CEffectType.EFFECT, 0);
			return true;
		}

		@Override
		public boolean doChannelTick(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final int tick = game.getGameTurnTick();
			if (tick >= this.nextTick) {
				this.nextTick = tick + NxSpells.ticks(1);
				final float x = target.getX();
				final float y = target.getY();
				game.getWorldCollision().enumUnitsInRange(x, y, this.area, (u) -> {
					if (u.isDead() || air(u) || !NxSpells.enemies(game, caster, u)) {
						return false;
					}
					if (u.isBuilding()) {
						NxSpells.spellDamage(game, caster, u, this.buildingDamage, CDamageType.NORMAL);
					}
					else {
						NxBuff.apply(game, u, new NxBuff(game.getHandleIdAllocator().createId(), this.buffId, 1.2f)
								.stat(NonStackingStatBuffType.MVSPDPCT, -this.slow));
					}
					return false;
				});
			}
			if (tick >= this.endTick) {
				removeArt();
				return false;
			}
			return true;
		}

		@Override
		public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
				final boolean interrupted) {
			removeArt();
		}

		private void removeArt() {
			if (this.art != null) {
				this.art.remove();
				this.art = null;
			}
		}
	}

	/** Cyclone: the target is lifted out of the fight for the duration. */
	public static final class Cyclone extends CAbilityTargetSpellBase {
		private War3ID buffId;

		public Cyclone(final int handleId, final War3ID alias) {
			super(handleId, alias);
		}

		@Override
		public int getBaseOrderId() {
			return OrderIds.cyclone;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.buffId = AbstractCAbilityTypeDefinition.getBuffId(go, level, 0);
			if (this.buffId == null) {
				this.buffId = getAlias();
			}
		}

		@Override
		protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
				final CWidget target,
				final com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver<CWidget> receiver) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || t.isBuilding()) {
				receiver.targetCheckFailed(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
				return;
			}
			receiver.targetOk(target);
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || t.isDead()) {
				return false;
			}
			final float seconds = getDurationForTarget(t);
			NxBuff.apply(game, t, new NxBuff(game.getHandleIdAllocator().createId(), this.buffId, seconds > 0 ? seconds : 6)
					.state(StateModBuffType.STUN).state(StateModBuffType.INVULNERABLE));
			game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
			return false;
		}
	}

	/** Pulverize, Feedback and Burning Oil: something happens when the unit's attack hits. */
	public static final class OnAttack extends CAbilityPassiveSpellBase {
		static final int PULVERIZE = 0;
		static final int FEEDBACK = 1;
		static final int BURNING_OIL = 2;
		private final int kind;
		private float a, b, c, d, e, area, duration;
		private final CUnitAttackPostDamageListener listener = this::onHit;

		public OnAttack(final int handleId, final War3ID alias, final int kind) {
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
			this.area = NxSpells.data(go, AbilityFields.AREA_OF_EFFECT, level);
			this.duration = NxSpells.data(go, AbilityFields.DURATION, level);
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			unit.addPostDamageListener(this.listener);
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			unit.removePostDamageListener(this.listener);
			super.onRemove(game, unit);
		}

		private void onHit(final CSimulation game, final CUnit attacker, final AbilityTarget target,
				final float damage) {
			switch (this.kind) {
			case PULVERIZE:
				pulverize(game, attacker);
				break;
			case FEEDBACK:
				feedback(game, attacker, target.visit(AbilityTargetVisitor.UNIT));
				break;
			default:
				burningOil(game, attacker, target.getX(), target.getY());
				break;
			}
		}

		private void pulverize(final CSimulation game, final CUnit attacker) {
			if ((game.getSeededRandom().nextFloat() * 100) >= this.a) {
				return;
			}
			final float x = attacker.getX();
			final float y = attacker.getY();
			final float full = this.c > 0 ? this.c : 250;
			final float half = Math.max(full, this.d);
			game.getWorldCollision().enumUnitsInRange(x, y, half, (u) -> {
				if ((u != attacker) && !u.isDead() && !air(u) && NxSpells.enemies(game, attacker, u)) {
					NxSpells.spellDamage(game, attacker, u, distance(u, x, y) <= full ? this.b : this.b / 2,
							CDamageType.NORMAL);
				}
				return false;
			});
			game.createTemporarySpellEffectOnUnit(attacker, getAlias(), CEffectType.CASTER);
		}

		private void feedback(final CSimulation game, final CUnit attacker, final CUnit t) {
			if ((t == null) || t.isDead() || !NxSpells.enemies(game, attacker, t)) {
				return;
			}
			if (t.getClassifications().contains(CUnitClassification.SUMMONED) && (this.e > 0)) {
				NxSpells.spellDamage(game, attacker, t, this.e, CDamageType.MAGIC);
			}
			if (t.getMana() <= 0) {
				return;
			}
			final float max = t.isHero() ? this.c : this.a;
			final float ratio = t.isHero() ? this.d : this.b;
			final float burned = Math.min(t.getMana(), max);
			if (burned <= 0) {
				return;
			}
			t.setMana(t.getMana() - burned);
			NxSpells.spellDamage(game, attacker, t, burned * ratio, CDamageType.MAGIC);
			game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
		}

		private void burningOil(final CSimulation game, final CUnit attacker, final float x, final float y) {
			final float radius = this.area > 0 ? this.area : 150;
			final float fullEvery = this.b > 0 ? this.b : 0.5f;
			final float halfEvery = this.d > 0 ? this.d : 1;
			final float buildings = this.e > 0 ? this.e : 1;
			final int end = game.getGameTurnTick() + NxSpells.ticks(this.duration > 0 ? this.duration : 2.5f);
			game.spawnTemporarySpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.EFFECT, 0);
			game.registerEffect(new CEffect() {
				private int nextFull = game.getGameTurnTick();
				private int nextHalf = game.getGameTurnTick();

				@Override
				public boolean update(final CSimulation g) {
					final int tick = g.getGameTurnTick();
					if (tick > end) {
						return true;
					}
					final boolean doFull = tick >= this.nextFull;
					final boolean doHalf = tick >= this.nextHalf;
					if (doFull) {
						this.nextFull = tick + NxSpells.ticks(fullEvery);
					}
					if (doHalf) {
						this.nextHalf = tick + NxSpells.ticks(halfEvery);
					}
					if (!doFull && !doHalf) {
						return false;
					}
					g.getWorldCollision().enumUnitsInRange(x, y, radius, (u) -> {
						if (u.isDead() || air(u) || !NxSpells.enemies(g, attacker, u)) {
							return false;
						}
						final boolean inner = distance(u, x, y) <= (radius / 2);
						float amount = 0;
						if (inner && doFull) {
							amount = OnAttack.this.a;
						}
						else if (!inner && doHalf) {
							amount = OnAttack.this.c;
						}
						if (u.isBuilding()) {
							amount *= buildings;
						}
						NxSpells.spellDamage(g, attacker, u, amount, CDamageType.FIRE);
						return false;
					});
					return false;
				}
			});
		}
	}

	/** Wander: an idle critter walks somewhere nearby every 3 to 8 seconds. */
	public static final class Wander extends CAbilityPassiveSpellBase {
		private int nextTick = -1;

		public Wander(final int handleId, final War3ID alias) {
			super(handleId, alias, alias);
		}

		@Override
		public void populateData(final GameObject go, final int level) {
		}

		@Override
		public void onTick(final CSimulation game, final CUnit unit) {
			super.onTick(game, unit);
			final int tick = game.getGameTurnTick();
			if (this.nextTick < 0) {
				this.nextTick = tick + NxSpells.ticks(3 + (game.getSeededRandom().nextFloat() * 5));
				return;
			}
			if (tick < this.nextTick) {
				return;
			}
			this.nextTick = tick + NxSpells.ticks(3 + (game.getSeededRandom().nextFloat() * 5));
			if (unit.isDead() || unit.isPaused() || unit.isHidden() || unit.isMovementDisabled()
					|| ((unit.getCurrentBehavior() != null)
							&& (unit.getCurrentBehavior().getHighlightOrderId() != OrderIds.stop))) {
				return;
			}
			final float angle = game.getSeededRandom().nextFloat() * (float) (StrictMath.PI * 2);
			final float dist = 100 + (game.getSeededRandom().nextFloat() * 300);
			final float x = unit.getX() + ((float) StrictMath.cos(angle) * dist);
			final float y = unit.getY() + ((float) StrictMath.sin(angle) * dist);
			unit.order(game, OrderIds.move, new AbilityPointTarget(x, y));
		}
	}

	/** Spiked Barricades: melee attackers of a building take damage once Rosp is researched. */
	public static final class SpikedBarricades extends CAbilityPassiveSpellBase {
		private static final War3ID UPGRADE = War3ID.fromString("Rosp");
		private final CUnitAttackDamageTakenListener listener = this::onDamaged;

		public SpikedBarricades(final int handleId, final War3ID alias) {
			super(handleId, alias, alias);
		}

		@Override
		public void populateData(final GameObject go, final int level) {
		}

		@Override
		public void onAdd(final CSimulation game, final CUnit unit) {
			super.onAdd(game, unit);
			unit.addDamageTakenListener(this.listener);
		}

		@Override
		public void onRemove(final CSimulation game, final CUnit unit) {
			unit.removeDamageTakenListener(this.listener);
			super.onRemove(game, unit);
		}

		private void onDamaged(final CSimulation game, final CUnit attacker, final CUnit target, final boolean isAttack,
				final boolean isRanged, final CDamageType weaponType, final float damage, final float bonusDamage,
				final float trueDamage) {
			if (!isAttack || isRanged || (attacker == null) || (attacker == target) || attacker.isDead()
					|| attacker.isBuilding() || !NxSpells.enemies(game, target, attacker)) {
				return;
			}
			final int level = game.getPlayer(target.getPlayerIndex()).getTechtreeUnlocked(UPGRADE);
			if (level <= 0) {
				return;
			}
			NxSpells.spellDamage(game, target, attacker, 5 + (damage * (0.2f + (0.3f * (level - 1)))),
					CDamageType.NORMAL);
		}
	}

	/** Abolish Magic: dispels the target; summoned enemies take Data B. */
	public static final class AbolishMagic extends CAbilityTargetSpellBase {
		private float summonDamage;

		public AbolishMagic(final int handleId, final War3ID alias) {
			super(handleId, alias);
		}

		@Override
		public int getBaseOrderId() {
			return OrderIds.autodispel;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.summonDamage = NxSpells.data(go, AbilityFields.DATA_B, level);
		}

		@Override
		protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
				final CWidget target,
				final com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver<CWidget> receiver) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || t.isBuilding()) {
				receiver.targetCheckFailed(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
				return;
			}
			receiver.targetOk(target);
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			final CUnit t = target.visit(AbilityTargetVisitor.UNIT);
			if ((t == null) || t.isDead()) {
				return false;
			}
			NxBuff.dispel(game, t);
			if (NxSpells.enemies(game, caster, t) && t.getClassifications().contains(CUnitClassification.SUMMONED)) {
				NxSpells.spellDamage(game, caster, t, this.summonDamage, CDamageType.MAGIC);
			}
			game.createTemporarySpellEffectOnUnit(t, getAlias(), CEffectType.TARGET);
			return false;
		}
	}

	/** Mirror Image: copies of the hero, which vanish when they die or expire. */
	public static final class MirrorImage extends CAbilityNoTargetSpellBase {
		private int count;
		private float dealt, taken;
		private War3ID buffId;

		public MirrorImage(final int handleId, final War3ID alias) {
			super(handleId, alias);
		}

		@Override
		public int getBaseOrderId() {
			return OrderIds.mirrorimage;
		}

		@Override
		public void populateData(final GameObject go, final int level) {
			this.count = Math.max(1, (int) NxSpells.data(go, AbilityFields.DATA_A, level));
			this.dealt = NxSpells.data(go, AbilityFields.DATA_B, level);
			this.taken = NxSpells.data(go, AbilityFields.DATA_C, level);
			this.buffId = AbstractCAbilityTypeDefinition.getBuffId(go, level, 0);
			if (this.buffId == null) {
				this.buffId = getAlias();
			}
		}

		@Override
		public boolean doEffect(final CSimulation game, final CUnit caster, final AbilityTarget target) {
			NxBuff.dispel(game, caster);
			final float seconds = getDuration() > 0 ? getDuration() : 60;
			final int slots = this.count + 1;
			final int casterSlot = game.getSeededRandom().nextInt(slots);
			final float start = game.getSeededRandom().nextFloat() * (float) (StrictMath.PI * 2);
			final float cx = caster.getX();
			final float cy = caster.getY();
			final CAbilityHero heroData = caster.getHeroData();
			for (int i = 0; i < slots; i++) {
				final float angle = start + ((float) (StrictMath.PI * 2 * i) / slots);
				final float x = cx + ((float) StrictMath.cos(angle) * 100);
				final float y = cy + ((float) StrictMath.sin(angle) * 100);
				if (i == casterSlot) {
					caster.setPointAndCheckUnstuck(x, y, game);
					continue;
				}
				final CUnit image = game.createUnit(caster.getTypeId(), caster.getPlayerIndex(), x, y,
						caster.getFacing());
				if (image == null) {
					continue;
				}
				if ((heroData != null) && (image.getHeroData() != null)) {
					image.getHeroData().setHeroLevel(game, image, heroData.getHeroLevel(), false);
				}
				image.setLife(game, image.getMaximumLife() * (caster.getLife() / Math.max(1f, caster.getMaximumLife())));
				image.setMana(0);
				image.setNxIllusion(true);
				image.addDeathReplacementEffect(CUnitDeathReplacementEffectPriority.PHOENIXREVIVE,
						(sim, unit, killer, result) -> {
							// an image vanishes instead of dying
							result.setReviving(true);
							sim.createTemporarySpellEffectOnUnit(unit, getAlias(), CEffectType.SPECIAL);
							sim.removeUnit(unit);
							final CUnitDeathReplacementStacking stop = new CUnitDeathReplacementStacking();
							stop.setAllowStacking(false);
							stop.setAllowSamePriorityStacking(false);
							return stop;
						});
				NxBuff.apply(game, image, new NxBuff(game.getHandleIdAllocator().createId(), this.buffId, seconds)
						.state(StateModBuffType.DISABLE_SPELLS)
						.stat(NonStackingStatBuffType.MELEEATKPCT, this.dealt - 1)
						.stat(NonStackingStatBuffType.RNGDATKPCT, this.dealt - 1).damageTaken(this.taken));
				image.add(game, new CBuffTimedLife(game.getHandleIdAllocator().createId(), this.buffId, seconds, false));
				game.createTemporarySpellEffectOnUnit(image, getAlias(), CEffectType.TARGET);
			}
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			return false;
		}
	}
}
