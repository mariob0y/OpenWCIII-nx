package com.etheller.warsmash.parsers.jass;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;

/**
 * warsmash-nx: what Warcraft III's engine does for every computer-controlled unit, script or no script
 * (Warsmash had none of it):
 * <ul>
 * <li>spells: computer and creep units cast their spells in combat (offensive spells at enemies, heals on
 * hurt allies, buffs on allies in combat, area spells where enemies bunch up, summons and self-buffs when
 * enemies are near) and have their autocast abilities switched on;</li>
 * <li>creep camps: Neutral Hostile units remember where they were first seen and give up a chase beyond
 * MaxGuardDistance (1000), walking back home; idle creeps away from home return too. RemoveGuardPosition
 * forgets a unit's home (WC3 does the same for creeps).</li>
 * </ul>
 * Ticked from NxAI.tick (no new engine hook), every half second.
 */
public final class NxUnitAI {
	private static final float THINK_SECONDS = 0.5f;
	private static final float MAX_GUARD_DISTANCE = 1000f;
	private static final float GUARD_RETURN_DISTANCE = 250f;
	private static final float COMBAT_RANGE = 800f;

	private static CSimulation sim;
	private static War3MapViewer viewer;
	private static int nextThink;
	private static int thinks;
	private static final Map<CUnit, float[]> HOMES = new HashMap<>();
	private static final Set<CUnit> SEEN = new HashSet<>();
	private static final Set<CUnit> NO_HOME = new HashSet<>();
	private static final Map<CUnit, Integer> NEXT_CAST = new HashMap<>();
	private static final Map<Long, Integer> RECAST = new HashMap<>();
	private static final Map<Long, Spell> SPELLS = new HashMap<>();
	private static final boolean DISABLED = System.getenv("NX_NO_UNIT_AI") != null;
	private static final boolean TRACE = System.getenv("NX_AI_TRACE") != null;
	private static int casts;

	private NxUnitAI() {
	}

	static void reset(final CSimulation simulation, final War3MapViewer mapViewer) {
		sim = simulation;
		viewer = mapViewer;
		nextThink = 0;
		thinks = 0;
		HOMES.clear();
		SEEN.clear();
		NO_HOME.clear();
		NEXT_CAST.clear();
		RECAST.clear();
		SPELLS.clear();
	}

	/** RemoveGuardPosition on a creep: it no longer walks back to where it started. */
	static void forgetHome(final CUnit u) {
		HOMES.remove(u);
		NO_HOME.add(u);
	}

	private static int ticks(final float seconds) {
		return Math.max(1, (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME));
	}

	private static int now() {
		return sim.getGameTurnTick();
	}

	static boolean isCreepPlayer(final int p) {
		return p == (WarsmashConstants.MAX_PLAYERS - 4);
	}

	/** Units whose owner the engine plays: computer slots and Neutral Hostile. */
	static boolean isComputer(final int p) {
		if ((p < 0) || (p >= (WarsmashConstants.MAX_PLAYERS - 3))) {
			return false;
		}
		if (isCreepPlayer(p)) {
			return true;
		}
		final CPlayer player = sim.getPlayer(p);
		if (player == null) {
			return false;
		}
		final CMapControl control = player.getController();
		return (control == CMapControl.COMPUTER) || (control == CMapControl.CREEP);
	}

	static void tick(final CSimulation simulation) {
		if (DISABLED || (simulation != sim) || (now() < nextThink)) {
			return;
		}
		nextThink = now() + ticks(THINK_SECONDS);
		thinks++;
		if ((thinks % 120) == 0) {
			cleanUp();
		}
		final List<CUnit> units = new ArrayList<>(sim.getUnits());
		for (final CUnit u : units) {
			if (u.isDead() || u.isHidden() || u.isBuilding() || u.isConstructing() || u.isPaused()) {
				continue;
			}
			final int owner = u.getPlayerIndex();
			if (!isComputer(owner)) {
				continue;
			}
			try {
				if (SEEN.add(u)) {
					firstSight(u, owner);
				}
				if (isCreepPlayer(owner) && leash(u)) {
					continue;
				}
				if (((thinks + u.getHandleId()) & 1) == 0) {
					cast(u, owner);
				}
			}
			catch (final Throwable t) {
				// one unit's odd ability must not stop the others (logged once per kind)
				final String key = t.getClass().getName();
				if (LOGGED.add(key)) {
					System.err.println("NXAI unit " + u.getTypeId() + ": " + t);
					t.printStackTrace();
				}
			}
		}
		if (TRACE && ((thinks % 60) == 0)) {
			System.err.println("NXAI units: " + casts + " casts in 30 s, " + HOMES.size() + " creep homes");
			casts = 0;
		}
	}

	private static final Set<String> LOGGED = new HashSet<>();

	private static void cleanUp() {
		final List<CUnit> gone = new ArrayList<>();
		for (final CUnit u : SEEN) {
			if (u.isDead() && (u.getHeroData() == null)) {
				gone.add(u);
			}
		}
		for (final CUnit u : gone) {
			SEEN.remove(u);
			HOMES.remove(u);
			NO_HOME.remove(u);
			NEXT_CAST.remove(u);
		}
		final int t = now();
		com.etheller.warsmash.util.JavaCompat.removeIf(RECAST.values(), expiry -> expiry < t);
	}

	private static void firstSight(final CUnit u, final int owner) {
		if (isCreepPlayer(owner) && !NO_HOME.contains(u)) {
			HOMES.put(u, new float[] { u.getX(), u.getY() });
		}
		for (final CAbility ability : u.getAbilities()) {
			if ((ability instanceof CAutocastAbility) && !ability.isDisabled()) {
				final CAutocastAbility autocast = (CAutocastAbility) ability;
				if (!autocast.isAutoCastOn() && wantsAutocast(ability)) {
					autocast.setAutoCastOn(u, true);
				}
			}
		}
	}

	/** Every autocast but the worker ones (repair, which pulls peons off the gold). */
	private static boolean wantsAutocast(final CAbility ability) {
		final String code = code(ability);
		return !("Arep".equals(code) || "Aren".equals(code) || "Arst".equals(code) || "Ahrp".equals(code));
	}

	// ------------------------------------------------------------------------------------------------
	// creep camps
	// ------------------------------------------------------------------------------------------------

	/** True when the unit was sent home this tick. */
	private static boolean leash(final CUnit u) {
		final float[] home = HOMES.get(u);
		if ((home == null) || u.isMovementDisabled()) {
			return false;
		}
		final COrder order = u.getCurrentOrder();
		if (order != null) {
			// an order (the map's, or our own walk home): leave it be
			return false;
		}
		final float d = NxAI.dist(u.getX(), u.getY(), home[0], home[1]);
		final boolean idle = NxAI.idle(u);
		if ((d > MAX_GUARD_DISTANCE) || (idle && (d > GUARD_RETURN_DISTANCE))) {
			return NxAI.pointOrder(u, OrderIds.move, home[0], home[1]);
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------
	// spells
	// ------------------------------------------------------------------------------------------------

	enum Kind {
		/** a unit-target (or point) spell at an enemy */
		ENEMY,
		/** disables (hex, sleep, polymorph...): heroes first, never twice on one target */
		DISABLE,
		/** a point spell where enemies bunch up */
		ENEMY_AREA,
		/** heals: the most hurt ally in range */
		HEAL,
		/** buffs: an ally in combat that does not have it yet */
		BUFF,
		/** no-target spell hitting the enemies around the caster */
		SELF_AREA,
		/** summons, self-buffs, transformations for combat: when enemies are near */
		COMBAT,
		/** self-buff when hurt (Divine Shield...) */
		HURT,
		/** a point near enemies (wards, traps, infernals) */
		PLACE_ENEMY,
		/** a point near hurt allies (Healing Ward) */
		PLACE_HEAL,
		/** area dispels: only with enemy summons around */
		DISPEL,
		SKIP
	}

	static final Map<String, Kind> KINDS = new HashMap<>();

	private static void kinds(final Kind kind, final String... codes) {
		for (final String c : codes) {
			KINDS.put(c, kind);
		}
	}

	static {
		kinds(Kind.ENEMY, "AHtb", "ANsb", "ACtb", "AEmb", "AUdc", "ACdc", "ANfd", "Acri", "ACcr", "Aslo", "ACsw",
				"Acrs", "ACcs", "Afae", "ACff", "AEsh", "Aprg", "ACpu", "ANfl", "ACfl", "AOcl", "ACcl", "AHdr", "ACdr",
				"ANba", "ANfb", "ACfb", "ANpa", "ACpa", "ANso", "ANab", "AHbn", "ACbn", "AUfn", "ACfn", "ANdh",
				"Acmg", "ANsi", "AOsh", "ACsh", "AUcs", "ACca", "ANbf", "ACbf", "ANcs", "Advm", "Adev", "ACdv",
				"Aspo", "AUim", "ACim");
		kinds(Kind.DISABLE, "AOhx", "ANhx", "AChx", "Aply", "ACpy", "AEer", "ACen", "Aens", "ACes", "Aweb", "ACwb",
				"AUsl", "ACsl", "Acyc", "ACcy", "ANch", "ACch");
		kinds(Kind.ENEMY_AREA, "AHbz", "ACbz", "ANrf", "ACrf", "AHfs", "ACfs", "AOeq", "ANst", "ANmo", "ACmo",
				"Aclf", "ACcw", "ANrc", "ANvc", "ANcr", "ACcv", "ANto");
		kinds(Kind.HEAL, "AHhb", "AOhw", "Ahea", "Anhe", "ACnh", "Arej", "ACrj", "ANhs");
		kinds(Kind.BUFF, "Ablo", "ACbl", "Ainf", "ACif", "AUfa", "AUfu", "ACfa", "ACfu", "Auhf", "ACuf", "Alsh",
				"ACls", "Aams", "ACam", "Aspl", "Adsm");
		kinds(Kind.SELF_AREA, "AHtc", "ACtc", "AOws", "ACws", "ANht", "ACht", "AEfk", "ACfk", "AOww", "Atau",
				"ANbr", "ACbr", "AEsf", "ANdb", "Aroa", "ACro");
		kinds(Kind.COMBAT, "AHav", "AOmi", "Absk", "ANef", "AHwe", "ACwe", "AOsf", "ACsf", "ANsg", "ANsq", "ANsw",
				"Arai", "ACrd", "AHpx", "AUan", "ACad", "AOls", "AUls", "AEme", "AUav", "ANrg", "ANsy", "AEtq", "AOvd",
				"Aspd", "ANwm");
		kinds(Kind.HURT, "AHds", "ACds", "Apsh", "ACwk", "AOwk");
		kinds(Kind.PLACE_ENEMY, "AOwd", "ACwd", "Asta", "AUin", "ACin", "AEfn", "ACfr", "ANfy", "Afla");
		kinds(Kind.PLACE_HEAL, "Ahwd", "AChw");
		kinds(Kind.DISPEL, "Adis", "ACds2", "Aadm", "ACdm");
		// never on their own: travel, scouting, economy, transport, morphing, items
		kinds(Kind.SKIP, "AHmt", "AEbl", "AOfs", "ANwk", "Asds", "Adri", "Aloa", "Adro", "Aloc", "Amil", "Abun",
				"Astd", "Aroo", "Abtl", "Arbr", "Asid", "Asud", "Aeye", "Ahwd2", "Aetl", "Aeat", "Ambt", "Ahrp", "Arep",
				"Arst", "Aren2", "AHre", "AUau", "Asac", "Aspb", "Afir", "Ainv", "Aret", "Atol", "Acoa", "Acoh",
				"Asal", "Acha", "Abrf", "Arav", "Asb1", "Asb2", "Asb3", "Aetf", "Amel", "Adec", "Aave", "Asph",
				"Ablp", "Absp", "AHab", "AHbh", "AHad", "AHer", "AOcr", "AOre", "AEev", "AUts", "AEah", "AEar",
				"AUau", "AIrc", "ANrn", "ANsk");
	}

	static final class Spell {
		String code;
		Kind kind;
		int orderId;
		float range;
		float area;
		float duration;
		boolean enemies;
		boolean friends;
		boolean noTarget;
	}

	private static String code(final CAbility ability) {
		final GameObject go = gameObject(ability.getAlias());
		if (go == null) {
			return ability.getAlias().toString();
		}
		final String code = go.readSLKTag(AbilityFields.CODE);
		return (code == null) || code.isEmpty() ? ability.getAlias().toString() : code;
	}

	private static GameObject gameObject(final War3ID alias) {
		final ObjectData abilities = viewer.getAllObjectData().getAbilities();
		return abilities == null ? null : abilities.get(alias);
	}

	private static Spell spell(final CUnit u, final CAbility ability) {
		final GenericSingleIconActiveAbility active = (GenericSingleIconActiveAbility) ability;
		final int level = Math.max(1, active.getLevel());
		final long key = (((long) ability.getAlias().getValue()) << 8) | level;
		Spell s = SPELLS.get(key);
		if (s != null) {
			return s;
		}
		s = new Spell();
		s.orderId = active.getBaseOrderId();
		s.code = code(ability);
		final GameObject go = gameObject(ability.getAlias());
		EnumSet<CTargetType> targets = EnumSet.noneOf(CTargetType.class);
		if (go != null) {
			s.range = go.getFieldAsFloat(AbilityFields.CAST_RANGE + level, 0);
			s.area = go.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
			s.duration = Math.max(go.getFieldAsFloat(AbilityFields.DURATION + level, 0),
					go.getFieldAsFloat(AbilityFields.HERO_DURATION + level, 0));
			try {
				targets = CTargetType.parseTargetTypeSet(go.getFieldAsList(AbilityFields.TARGETS_ALLOWED + level));
			}
			catch (final RuntimeException e) {
				// unparsable field: no target info
			}
		}
		s.enemies = targets.contains(CTargetType.ENEMIES) || targets.contains(CTargetType.NEUTRAL);
		s.friends = targets.contains(CTargetType.FRIEND) || targets.contains(CTargetType.ALLIES)
				|| targets.contains(CTargetType.PLAYERUNITS) || targets.contains(CTargetType.SELF);
		if (!s.enemies && !s.friends) {
			// the SLK default ("ground,air,..." with no side) means anyone
			s.enemies = true;
			s.friends = true;
		}
		final BooleanAbilityTargetCheckReceiver<Void> receiver = BooleanAbilityTargetCheckReceiver.<Void>getInstance();
		ability.checkCanTargetNoTarget(sim, u, s.orderId, receiver.reset());
		s.noTarget = receiver.isTargetable();
		final Kind known = KINDS.get(s.code);
		if (known != null) {
			s.kind = known;
		}
		else if (s.orderId == 0) {
			s.kind = Kind.SKIP;
		}
		else if (s.noTarget) {
			// unknown no-target spells: only clearly offensive area ones
			s.kind = (s.area > 0) && targets.contains(CTargetType.ENEMIES) ? Kind.SELF_AREA : Kind.SKIP;
		}
		else if (s.enemies && !targets.contains(CTargetType.FRIEND)) {
			s.kind = s.area > 0 ? Kind.ENEMY_AREA : Kind.ENEMY;
		}
		else if (s.friends && !s.enemies) {
			s.kind = Kind.BUFF;
		}
		else {
			s.kind = Kind.SKIP;
		}
		if (TRACE) {
			System.err.println("NXAI spell " + ability.getAlias() + " (" + s.code + ") level " + level + ": " + s.kind
					+ " order " + s.orderId + " range " + s.range + " area " + s.area + " dur " + s.duration
					+ (s.noTarget ? " no-target" : ""));
		}
		SPELLS.put(key, s);
		return s;
	}

	private static boolean isSpell(final CAbility ability) {
		return (ability instanceof GenericSingleIconActiveAbility)
				&& (ability.getAbilityCategory() == CAbilityCategory.SPELL) && !ability.isDisabled()
				&& (ability.getItem() == null);
	}

	private static void cast(final CUnit u, final int owner) {
		final Integer next = NEXT_CAST.get(u);
		if ((next != null) && (now() < next)) {
			return;
		}
		List<CAbility> spells = null;
		for (final CAbility ability : u.getAbilities()) {
			if (isSpell(ability)) {
				if ((ability instanceof CAutocastAbility) && ((CAutocastAbility) ability).isAutoCastOn()) {
					continue; // the engine casts it
				}
				if (spells == null) {
					spells = new ArrayList<>(4);
				}
				spells.add(ability);
			}
		}
		if (spells == null) {
			return;
		}
		final COrder order = u.getCurrentOrder();
		if (order != null) {
			for (final CAbility ability : spells) {
				if (((GenericSingleIconActiveAbility) ability).getBaseOrderId() == order.getOrderId()) {
					return; // casting already
				}
			}
		}
		final float reach = Math.max(COMBAT_RANGE, u.getAcquisitionRange() + 200);
		final CUnit nearest = nearestEnemy(u, owner, u.getX(), u.getY(), reach);
		final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE;
		// start at a rotating spell so one spell that always fails does not hide the others
		final int n = spells.size();
		final int first = (thinks / 2) % n;
		for (int k = 0; k < n; k++) {
			final CAbility ability = spells.get((first + k) % n);
			final Spell s = spell(u, ability);
			if (s.kind == Kind.SKIP) {
				continue;
			}
			if ((nearest == null) && (s.kind != Kind.HEAL) && (s.kind != Kind.PLACE_HEAL)) {
				continue;
			}
			ability.checkCanUse(sim, u, s.orderId, activation);
			if (!activation.isOk()) {
				continue;
			}
			if (tryCast(u, owner, ability, s, nearest)) {
				casts++;
				NEXT_CAST.put(u, now() + ticks(2.5f));
				if (TRACE) {
					System.err.println("NXAI cast " + u.getTypeId() + " p" + owner + " " + s.code + " (" + s.kind + ")");
				}
				return;
			}
		}
	}

	private static float castReach(final Spell s) {
		return Math.min(1200, Math.max(s.range, 250) + 200);
	}

	private static boolean tryCast(final CUnit u, final int owner, final CAbility ability, final Spell s,
			final CUnit nearest) {
		final float x = u.getX();
		final float y = u.getY();
		switch (s.kind) {
		case ENEMY:
		case DISABLE: {
			final CUnit t = pickEnemy(u, owner, ability, s, castReach(s));
			if (t == null) {
				return false;
			}
			if (castAt(u, ability, s, t)) {
				remember(ability, t, s);
				return true;
			}
			return false;
		}
		case ENEMY_AREA: {
			final float area = s.area > 0 ? s.area : 250;
			final CUnit t = pickCluster(u, owner, castReach(s), area);
			return (t != null) && castAt(u, ability, s, t);
		}
		case HEAL: {
			final CUnit t = pickHurtAlly(u, owner, castReach(s), 0.7f);
			if (t == null) {
				return false;
			}
			if (s.noTarget) {
				return castNoTarget(u, ability, s);
			}
			return castAt(u, ability, s, t);
		}
		case BUFF: {
			if (s.noTarget) {
				return castNoTarget(u, ability, s);
			}
			final CUnit t = pickBuffTarget(u, owner, ability, s, castReach(s));
			if ((t != null) && castAt(u, ability, s, t)) {
				remember(ability, t, s);
				return true;
			}
			return false;
		}
		case SELF_AREA: {
			final float area = s.area > 0 ? s.area : 300;
			if (countEnemies(u, owner, x, y, area) < 1) {
				return false;
			}
			return s.noTarget ? castNoTarget(u, ability, s) : castAt(u, ability, s, nearest);
		}
		case COMBAT: {
			if (NxAI.dist(x, y, nearest.getX(), nearest.getY()) > 700) {
				return false;
			}
			if (s.noTarget) {
				return castNoTarget(u, ability, s);
			}
			return castPoint(u, ability, s, (x + nearest.getX()) / 2, (y + nearest.getY()) / 2)
					|| castUnit(u, ability, s, nearest);
		}
		case HURT: {
			if ((u.getLife() > (u.getMaximumLife() * 0.35f))
					|| (NxAI.dist(x, y, nearest.getX(), nearest.getY()) > 700)) {
				return false;
			}
			return s.noTarget ? castNoTarget(u, ability, s) : castUnit(u, ability, s, u);
		}
		case PLACE_ENEMY: {
			if (NxAI.dist(x, y, nearest.getX(), nearest.getY()) > castReach(s)) {
				return false;
			}
			final float px = x + ((nearest.getX() - x) * 0.6f);
			final float py = y + ((nearest.getY() - y) * 0.6f);
			return castPoint(u, ability, s, px, py);
		}
		case PLACE_HEAL: {
			final CUnit t = pickHurtAlly(u, owner, castReach(s), 0.6f);
			return (t != null) && castPoint(u, ability, s, t.getX(), t.getY());
		}
		case DISPEL: {
			final CUnit t = pickSummonedEnemy(u, owner, castReach(s));
			return (t != null) && castAt(u, ability, s, t);
		}
		default:
			return false;
		}
	}

	// ---- targets

	private static boolean isEnemyOf(final int owner, final CUnit t) {
		final int o = t.getPlayerIndex();
		return !t.isDead() && !t.isHidden() && (o != owner) && !t.isInvulnerable()
				&& !sim.getPlayer(owner).hasAlliance(o, CAllianceType.PASSIVE);
	}

	private static boolean isAllyOf(final int owner, final CUnit t) {
		final int o = t.getPlayerIndex();
		return !t.isDead() && !t.isHidden() && !t.isBuilding()
				&& ((o == owner) || sim.getPlayer(owner).hasAlliance(o, CAllianceType.PASSIVE));
	}

	private static CUnit nearestEnemy(final CUnit self, final int owner, final float x, final float y,
			final float radius) {
		final CUnit[] best = new CUnit[1];
		final float[] bestDistance = { Float.MAX_VALUE };
		sim.getWorldCollision().enumUnitsInRange(x, y, radius, (t) -> {
			if (isEnemyOf(owner, t) && !t.isBuilding()) {
				final float d = NxAI.dist(t.getX(), t.getY(), x, y);
				if (d < bestDistance[0]) {
					best[0] = t;
					bestDistance[0] = d;
				}
			}
			return false;
		});
		return best[0];
	}

	private static int countEnemies(final CUnit self, final int owner, final float x, final float y,
			final float radius) {
		final int[] n = { 0 };
		sim.getWorldCollision().enumUnitsInRange(x, y, radius, (t) -> {
			if (isEnemyOf(owner, t) && !t.isBuilding()) {
				n[0]++;
			}
			return false;
		});
		return n[0];
	}

	private static long recastKey(final CAbility ability, final CUnit t) {
		return (((long) ability.getAlias().getValue()) << 32) ^ (t.getHandleId() & 0xffffffffL);
	}

	private static void remember(final CAbility ability, final CUnit t, final Spell s) {
		if ((s.kind == Kind.DISABLE) || (s.kind == Kind.BUFF) || (s.duration > 0)) {
			final float seconds = s.duration > 0 ? s.duration : 10;
			RECAST.put(recastKey(ability, t), now() + ticks(seconds));
		}
	}

	private static boolean recentlyHit(final CAbility ability, final CUnit t) {
		final Integer expiry = RECAST.get(recastKey(ability, t));
		return (expiry != null) && (now() < expiry);
	}

	private static CUnit pickEnemy(final CUnit self, final int owner, final CAbility ability, final Spell s,
			final float radius) {
		final CUnit[] best = new CUnit[1];
		final float[] bestScore = { -Float.MAX_VALUE };
		final float x = self.getX();
		final float y = self.getY();
		sim.getWorldCollision().enumUnitsInRange(x, y, radius, (t) -> {
			if (!isEnemyOf(owner, t) || t.isBuilding() || recentlyHit(ability, t)) {
				return false;
			}
			float score = -NxAI.dist(t.getX(), t.getY(), x, y);
			if (t.isHero()) {
				score += 2000;
			}
			if ("Aprg".equals(s.code) || "ACpu".equals(s.code) || "Acmg".equals(s.code) || "Advm".equals(s.code)) {
				if (!t.getClassifications().contains(CUnitClassification.SUMMONED)) {
					return false;
				}
			}
			if ((s.kind == Kind.DISABLE) && !t.isHero()) {
				score += t.getMaximumLife() * 0.5f;
			}
			if (score > bestScore[0]) {
				best[0] = t;
				bestScore[0] = score;
			}
			return false;
		});
		return best[0];
	}

	/** An enemy with at least one other enemy within the area around it (or an enemy hero). */
	private static CUnit pickCluster(final CUnit self, final int owner, final float radius, final float area) {
		final List<CUnit> enemies = new ArrayList<>();
		final float x = self.getX();
		final float y = self.getY();
		sim.getWorldCollision().enumUnitsInRange(x, y, radius, (t) -> {
			if (isEnemyOf(owner, t) && !t.isBuilding()) {
				enemies.add(t);
			}
			return false;
		});
		CUnit best = null;
		int bestCount = 0;
		for (final CUnit e : enemies) {
			int count = 0;
			for (final CUnit o : enemies) {
				if (NxAI.dist(e.getX(), e.getY(), o.getX(), o.getY()) <= area) {
					count += o.isHero() ? 2 : 1;
				}
			}
			if (count > bestCount) {
				best = e;
				bestCount = count;
			}
		}
		return bestCount >= 2 ? best : null;
	}

	private static CUnit pickHurtAlly(final CUnit self, final int owner, final float radius, final float below) {
		final CUnit[] best = new CUnit[1];
		final float[] bestRatio = { below };
		sim.getWorldCollision().enumUnitsInRange(self.getX(), self.getY(), radius, (t) -> {
			if (isAllyOf(owner, t) && (t.getMaximumLife() > 0)) {
				float ratio = t.getLife() / t.getMaximumLife();
				if (t.isHero()) {
					ratio -= 0.1f;
				}
				if (ratio < bestRatio[0]) {
					best[0] = t;
					bestRatio[0] = ratio;
				}
			}
			return false;
		});
		return best[0];
	}

	/** An ally (melee ones first) with an enemy close, not given this buff recently. */
	private static CUnit pickBuffTarget(final CUnit self, final int owner, final CAbility ability, final Spell s,
			final float radius) {
		final CUnit[] best = new CUnit[1];
		final float[] bestScore = { -Float.MAX_VALUE };
		sim.getWorldCollision().enumUnitsInRange(self.getX(), self.getY(), radius, (t) -> {
			if (!isAllyOf(owner, t) || recentlyHit(ability, t)
					|| t.getClassifications().contains(CUnitClassification.PEON)
					|| t.getClassifications().contains(CUnitClassification.WARD)) {
				return false;
			}
			if (("Aspl".equals(s.code)) && (t == self)) {
				return false;
			}
			final CUnit foe = nearestEnemy(t, owner, t.getX(), t.getY(), 500);
			if (foe == null) {
				return false;
			}
			float score = t.getMaximumLife();
			if (t.isHero()) {
				score += 1000;
			}
			if (score > bestScore[0]) {
				best[0] = t;
				bestScore[0] = score;
			}
			return false;
		});
		return best[0];
	}

	private static CUnit pickSummonedEnemy(final CUnit self, final int owner, final float radius) {
		final CUnit[] best = new CUnit[1];
		sim.getWorldCollision().enumUnitsInRange(self.getX(), self.getY(), radius, (t) -> {
			if ((best[0] == null) && isEnemyOf(owner, t)
					&& t.getClassifications().contains(CUnitClassification.SUMMONED)) {
				best[0] = t;
			}
			return false;
		});
		return best[0];
	}

	// ---- orders through one given ability

	/** A unit target if the spell takes one, else a point at the unit, else no target. */
	private static boolean castAt(final CUnit u, final CAbility ability, final Spell s, final CUnit t) {
		if (s.noTarget) {
			return castNoTarget(u, ability, s);
		}
		return castUnit(u, ability, s, t) || castPoint(u, ability, s, t.getX(), t.getY());
	}

	private static boolean castUnit(final CUnit u, final CAbility ability, final Spell s, final CUnit t) {
		final CWidgetAbilityTargetCheckReceiver receiver = CWidgetAbilityTargetCheckReceiver.INSTANCE;
		ability.checkCanTarget(sim, u, s.orderId, t, receiver.reset());
		if (receiver.getTarget() == null) {
			return false;
		}
		sim.getDefaultPlayerUnitOrderExecutor(u.getPlayerIndex()).issueTargetOrder(u.getHandleId(),
				ability.getHandleId(), s.orderId, receiver.getTarget().getHandleId(), false);
		return true;
	}

	private static boolean castPoint(final CUnit u, final CAbility ability, final Spell s, final float x,
			final float y) {
		final PointAbilityTargetCheckReceiver receiver = PointAbilityTargetCheckReceiver.INSTANCE;
		ability.checkCanTarget(sim, u, s.orderId, new AbilityPointTarget(x, y), receiver.reset());
		final AbilityPointTarget target = receiver.getTarget();
		if (target == null) {
			return false;
		}
		sim.getDefaultPlayerUnitOrderExecutor(u.getPlayerIndex()).issuePointOrder(u.getHandleId(),
				ability.getHandleId(), s.orderId, target.x, target.y, false);
		return true;
	}

	private static boolean castNoTarget(final CUnit u, final CAbility ability, final Spell s) {
		final BooleanAbilityTargetCheckReceiver<Void> receiver = BooleanAbilityTargetCheckReceiver.<Void>getInstance();
		ability.checkCanTargetNoTarget(sim, u, s.orderId, receiver.reset());
		if (!receiver.isTargetable()) {
			return false;
		}
		sim.getDefaultPlayerUnitOrderExecutor(u.getPlayerIndex()).issueImmediateOrder(u.getHandleId(),
				ability.getHandleId(), s.orderId, false);
		return true;
	}

	static String describe() {
		return String.format(Locale.US, "%d creep homes, %d units seen", HOMES.size(), SEEN.size());
	}
}
