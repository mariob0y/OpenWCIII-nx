package com.etheller.warsmash.parsers.jass;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityNeutralBuilding;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellItems;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;

/**
 * PC test player (inert unless the environment variable NX_PLAY names a goal file; never set on the console).
 * It plays player 0 like a person at the controls: every order goes through the UI's order listener, so the
 * mission's input recording (and the save/load replay built on it) sees ordinary player input. It never sets
 * globals, runs triggers or moves units directly.
 *
 * Goal file: sections "[MapName]" (zone file name without extension); per section:
 *   quests A B C          quest names: logs udg_QuestAProgress and the quest udg_QuestA as they change
 *   hero A B              the unit globals (udg_ optional) that lead: the first alive and visible one
 *   party A B:FLAG        companion heroes ordered along wherever they are (B only while global FLAG is true);
 *                         other non-hero units of player 0 join when within 2500 of the leader
 *   learn AOsf AOs2 ...   hero skill priority (rawcodes); unlisted skills are learned after these
 *   skipcine N            press Escape after N seconds of a cinematic (0 = watch it to the end)
 *   goal COND : ACTION [| ACTION...]  alternatives: the first with something to do (a target) plays;
 *                         the first goal whose COND holds is played; COND = "always" or terms joined by
 *                         "&": NAME OP INT (OP one of < <= == != >= >), NAME (boolean true), !NAME;
 *                         NAME is a global, "udg_" optional, or HEROLEVEL (the leading hero's level), TIME (game seconds)
 * ACTION: goto RECT (attack-move there; the hero leads) | walk RECT | gotoxy X Y | attack UNITVAR |
 *   farm RADIUS (nearest hostile creep) | cheatkill UNITVAR (logged CHEAT) | killgroup GROUPVAR | killplayer IDX [RECT] | killtype RAW... | attackany VAR... | visit RECT... | pickup RAWCODE | buy SHOPVAR RAWCODE | wait | stop
 */
public final class NxPlayBot {
	private static final String FILE = System.getenv("NX_PLAY");
	private static final int TICKS_PER_THINK = 10;

	private static CSimulation sim;
	private static String map;
	private static Section section;
	private static int lastThink = -1000;
	private static final Map<String, String> watched = new HashMap<>();
	private static Goal goal;
	private static int goalSince;
	private static int lastOrder = -1000;
	private static float lastOrderX;
	private static float aimX;
	private static float stallX;
	private static float stallY;
	private static int stallSince;
	private static final List<float[]> route = new ArrayList<>();
	private static float aimY;
	private static float lastOrderY;
	private static String lastOrderKey = "";
	private static boolean stuckReported;
	private static int cineSince = -1;
	private static int lastBeat;
	private static int lastShot;
	private static boolean heroDead;
	private static int heroLevel;
	private static int leaderLevel;
	private static int thinkNow;
	private static int farmTarget = -1;
	private static int farmSince;
	private static float farmLife;
	private static final java.util.Set<Integer> farmGiveUp = new java.util.HashSet<>();
	private static final List<String> notes = new ArrayList<>();
	private static int issued;
	private static int refused;
	private static final java.util.Set<String> visited = new java.util.HashSet<>();

	private NxPlayBot() {
	}

	public static boolean enabled() {
		return FILE != null;
	}

	public static void tick(final CSimulation simulation, final CPlayerUnitOrderListener orders,
			final String currentMap) {
		if ((FILE == null) || (simulation == null) || (currentMap == null) || (simulation.getGlobalScope() == null)) {
			return;
		}
		if ((simulation.getMissionCheckpoint() != null) && simulation.getMissionCheckpoint().isRestoring()) {
			return; // a loaded game is replaying its recorded input: hands off
		}
		final int now = simulation.getGameTurnTick();
		if (simulation != sim) {
			sim = simulation;
			map = mapName(currentMap);
			section = load(map);
			watched.clear();
			goal = null;
			lastThink = now - TICKS_PER_THINK;
			lastBeat = now;
			lastShot = now;
			cineSince = -1;
			heroDead = false;
			heroLevel = -1;
			heroStates.clear();
			final String failure = simulation.getMissionCheckpoint() == null ? null
					: simulation.getMissionCheckpoint().getFailure();
			log("map start" + (section == null ? " (no goals for this map)" : "") + ", tick " + now
					+ (failure != null ? ", CHECKPOINT FAILURE: " + failure : ""));
			NxCampaignNatives.requestShot("play_" + map + "_start_" + now);
		}
		if ((section == null) || ((now - lastThink) < TICKS_PER_THINK)) {
			return;
		}
		lastThink = now;
		try {
			think(orders, now);
		}
		catch (final RuntimeException e) {
			log("ERROR " + e);
			e.printStackTrace();
		}
	}

	private static void think(final CPlayerUnitOrderListener orders, final int now) {
		final GlobalScope g = sim.getGlobalScope();
		thinkNow = now;
		watchQuests(g, now);
		CUnit hero = null;
		for (final String name : section.hero) {
			final CUnit u = unitGlobal(g, name);
			if ((u != null) && !u.isDead() && !u.isHidden()) {
				hero = u;
				break;
			}
			if ((hero == null) && (u != null)) {
				hero = u; // dead or hidden: still the one to report
			}
		}
		watchHeroes();
		leaderLevel = (hero != null) && (hero.getHeroData() != null) ? hero.getHeroData().getHeroLevel() : 0;
		if ((now - lastBeat) >= seconds(30)) {
			lastBeat = now;
			final StringBuilder heroes = new StringBuilder();
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() == 0) && (u.getHeroData() != null) && (u != hero)) {
					heroes.append("; ").append(describe(u)).append(xp(u));
				}
			}
			log("beat: " + describe(hero) + xp(hero)
					+ heroes + "; party " + (hero == null ? 0 : party(hero).size()) + ", orders " + issued + " issued " + refused
					+ " refused, goal "
					+ (goal == null ? "-" : goal.text) + ", gold " + sim.getPlayer(0).getGold());
			issued = 0;
			refused = 0;
		}
		if ((now - lastShot) >= seconds(120)) {
			lastShot = now;
			NxCampaignNatives.requestShot("play_" + map + "_" + (int) (now * WarsmashConstants.SIMULATION_STEP_TIME));
		}
		// cinematics: watch (or press Escape after skipcine seconds); orders wait
		if (bool(g, "bj_cineModeAlreadyIn")) {
			if (cineSince < 0) {
				cineSince = now;
				log("cinematic starts");
			}
			if ((section.skipCine > 0) && ((now - cineSince) == seconds(section.skipCine))) {
				log("cinematic: Escape");
				orders.issueGuiPlayerEvent(JassGameEventsWar3.EVENT_PLAYER_END_CINEMATIC.getEventId());
			}
			if ((now - cineSince) == seconds(300)) {
				log("STUCK? cinematic running for 300 s");
				NxCampaignNatives.requestShot("play_" + map + "_longcine_" + now);
			}
			return;
		}
		if (cineSince >= 0) {
			log("cinematic ends after " + (int) ((now - cineSince) * WarsmashConstants.SIMULATION_STEP_TIME) + " s");
			stallSince = now;
			route.clear();
			cineSince = -1;
		}
		learnSkills(orders);
		fight(orders, now);
		if ((hero == null) || hero.isDead() || hero.isHidden() || hero.isPaused()) {
			return;
		}
		Goal next = null;
		for (final Goal candidate : section.goals) {
			if (candidate.holds(g)) {
				next = candidate;
				break;
			}
		}
		if (next != goal) {
			log("goal: " + (next == null ? "(none)" : next.text));
			goal = next;
			goalSince = now;
			route.clear();
			lastOrderKey = "";
			stuckReported = false;
		}
		if (goal == null) {
			return;
		}
		if (!stuckReported && ((now - goalSince) > seconds(240))) {
			stuckReported = true;
			log("STUCK? same goal for 240 s: " + goal.text + "; " + describe(hero));
			reachability(hero);
			NxCampaignNatives.requestShot("play_" + map + "_stuck_" + now);
		}
		play(g, orders, hero, now);
	}

	// ---- actions

	private static void play(final GlobalScope g, final CPlayerUnitOrderListener orders, final CUnit hero,
			final int now) {
		// re-issue only when idle, or when the hero has not moved for 30 s (a path search can take seconds)
		final float mx = hero.getX() - lastOrderX, my = hero.getY() - lastOrderY;
		final boolean refresh = isIdle(hero)
				|| (((now - lastOrder) >= seconds(30)) && (((mx * mx) + (my * my)) < (100 * 100)));
		if ((now - lastOrder) >= seconds(30)) {
			lastOrderX = hero.getX();
			lastOrderY = hero.getY();
		}
		// regroup: wait while a companion is dead (revival takes seconds), meet one that is far away, so the
		// heroes do not arrive at a fight one by one
		final GlobalScope gs = sim.getGlobalScope();
		for (final String entry : section.party) {
			final String[] nc = entry.split(":");
			if ((nc.length > 1) && !bool(gs, nc[1]) && !bool(gs, "udg_" + nc[1])) {
				continue;
			}
			final CUnit c = unitGlobal(gs, nc[0]);
			if ((c == null) || (c == hero) || c.isHidden()) {
				continue;
			}
			if (c.isDead()) {
				return;
			}
			if ((dist2(c, hero) > (2500 * 2500)) && !fighting(hero)) {
				final String key = "regroup" + c.getHandleId();
				if (refresh || !key.equals(lastOrderKey)) {
					issuePoint(orders, hero, OrderIds.attack, c.getX(), c.getY());
					issuePoint(orders, c, OrderIds.attack, hero.getX(), hero.getY());
					lastOrderKey = key;
					lastOrder = now;
				}
				return;
			}
		}
		// the engine's path search gives up on long detours (20,000-iteration cap): when the leader has not
		// moved for 30 s without fighting, walk a flood-fill route towards the aim point in legs
		final float sdx = hero.getX() - stallX, sdy = hero.getY() - stallY;
		if (((sdx * sdx) + (sdy * sdy)) > (150 * 150)) {
			stallX = hero.getX();
			stallY = hero.getY();
			stallSince = now;
		}
		else if (route.isEmpty() && ((((now - stallSince) > seconds(30)) && !fighting(hero))
				|| ((now - stallSince) > seconds(90)))) {
			stallSince = now;
			reachability(hero, true);
		}
		if (!route.isEmpty()) {
			final float[] next = route.get(0);
			final float rdx = next[0] - hero.getX(), rdy = next[1] - hero.getY();
			if (((rdx * rdx) + (rdy * rdy)) > (1500 * 1500)) {
				// stale (the leader died and revived elsewhere): a new route is made if he stalls again
				route.clear();
			}
			else if (((rdx * rdx) + (rdy * rdy)) < (200 * 200)) {
				route.remove(0);
			}
			else {
				order(orders, hero, OrderIds.attack, next[0], next[1], "route" + route.size(), refresh, now);
				return;
			}
		}
		for (final String[] a : goal.alternatives) {
			if (act(g, orders, hero, a, refresh, now)) {
				return;
			}
		}
	}

	/** Plays one action; false when it has nothing to do (no target left), so the next alternative runs. */
	private static boolean act(final GlobalScope g, final CPlayerUnitOrderListener orders, final CUnit hero,
			final String[] a, final boolean refresh, final int now) {
		switch (a[0]) {
		case "goto":
		case "walk": {
			final Rectangle r = rect(g, a[1]);
			final float x = r.x + (r.width / 2), y = r.y + (r.height / 2);
			order(orders, hero, a[0].equals("goto") ? OrderIds.attack : OrderIds.smart, x, y, a[1], refresh, now);
			return true;
		}
		case "gotoxy":
			order(orders, hero, OrderIds.attack, Float.parseFloat(a[1]), Float.parseFloat(a[2]), a[1] + "," + a[2],
					refresh, now);
			return true;
		case "cheatkill": {
			// CHEAT, logged: kills a unit the bot cannot beat, so the rest of the chapter can be tested
			// (not recorded input: a save made after it will not load back the same)
			final CUnit victim = unitGlobal(g, a[1]);
			if ((victim == null) || victim.isDead()) {
				return false;
			}
			log("CHEAT: killing " + describe(victim) + " (" + a[1] + ")");
			victim.kill(sim);
			return true;
		}
		case "attack": {
			final JassValue v = udg(g, a[1]);
			final Object o = v instanceof HandleJassValue ? ((HandleJassValue) v).getJavaValue() : null;
			if ((o instanceof com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget) && !((com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget) o).isDead()) {
				orderTarget(orders, hero, (com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget) o, refresh, now);
				return true;
			}
			return false;
		}
		case "killgroup": {
			final CUnit target = nearest(hero, groupGlobal(g, a[1]), -1, null);
			if (target != null) {
				orderTarget(orders, hero, target, refresh, now);
				return true;
			}
			return false;
		}
		case "killplayer": {
			final Rectangle r = a.length > 2 ? rect(g, a[2]) : null;
			final CUnit target = nearest(hero, sim.getUnits(), Integer.parseInt(a[1]), r);
			if (target != null) {
				order(orders, hero, OrderIds.attack, target.getX(), target.getY(), "kill" + target.getHandleId(),
						refresh, now);
			}
			else if (!notes.contains("nokill" + map + a[1])) {
				notes.add("nokill" + map + a[1]);
				log("killplayer " + a[1] + ": no target left");
			}
			return target != null;
		}
		case "pickup": {
			final CItem item = nearestItem(hero, War3ID.fromString(a[1]).getValue());
			if (item != null) {
				final String key = "item" + item.getHandleId();
				if (refresh || !key.equals(lastOrderKey)) {
					issueTarget(orders, hero, item, OrderIds.smart);
					lastOrderKey = key;
					lastOrder = now;
				}
				return true;
			}
			return false;
		}
		case "farm": {
			// the nearest hostile creep within RADIUS of the hero (levels before a hard fight); false if none
			final float radius = Float.parseFloat(a[1]);
			final int creeps = ((IntegerJassValue) global(g, "PLAYER_NEUTRAL_AGGRESSIVE")).getValue();
			// flying creeps guard their camps from the air: the melee leader cannot hit them, so camps near them wait
			final List<CUnit> flyers = new ArrayList<>();
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() == creeps) && !u.isDead() && !u.isHidden()
						&& (u.getMovementType() == com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType.FLY)) {
					flyers.add(u);
				}
			}
			CUnit best = null;
			float bestD = radius * radius;
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() == creeps) && !u.isDead() && !u.isHidden() && !u.isInvulnerable()
						&& (u.getMovementType() != com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType.FLY)
						&& flyers.stream().noneMatch(f -> dist2(f, u) < (1500 * 1500))
						&& !farmGiveUp.contains(u.getHandleId())) {
					final float d = dist2(u, hero);
					if (d < bestD) {
						bestD = d;
						best = u;
					}
				}
			}
			if (best != null) {
				// a creep that is never reached (over a cliff, out of the path search's reach) is dropped after 45 s
				if (best.getHandleId() != farmTarget) {
					farmTarget = best.getHandleId();
					farmSince = now;
					farmLife = best.getLife();
				}
				else if (best.getLife() < farmLife) {
					farmSince = now;
					farmLife = best.getLife();
				}
				else if ((now - farmSince) > seconds(45)) {
					log("farm: giving up on " + describe(best));
					farmGiveUp.add(best.getHandleId());
					farmTarget = -1;
					return true;
				}
				order(orders, hero, OrderIds.attack, best.getX(), best.getY(), "farm" + best.getHandleId(), refresh, now);
			}
			return best != null;
		}
		case "killtype": {
			// the nearest living unit of any listed type
			CUnit best = null;
			float bestD = Float.MAX_VALUE;
			for (final CUnit u : sim.getUnits()) {
				if (u.isDead() || u.isHidden()) {
					continue;
				}
				for (int i = 1; i < a.length; i++) {
					if (u.getTypeId().getValue() == War3ID.fromString(a[i]).getValue()) {
						final float d = dist2(u, hero);
						if (d < bestD) {
							bestD = d;
							best = u;
						}
					}
				}
			}
			if (best != null) {
				order(orders, hero, OrderIds.attack, best.getX(), best.getY(), "kill" + best.getHandleId(), refresh, now);
			}
			return best != null;
		}
		case "attackany": {
			// the nearest living one of the listed widget globals (battleships, columns...)
			com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget best = null;
			float bestD = Float.MAX_VALUE;
			for (int i = 1; i < a.length; i++) {
				final JassValue v = udg(g, a[i]);
				final Object o = v instanceof HandleJassValue ? ((HandleJassValue) v).getJavaValue() : null;
				if ((o instanceof com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget) && !((com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget) o).isDead()) {
					final com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget w = (com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget) o;
					final float dx = w.getX() - hero.getX(), dy = w.getY() - hero.getY();
					if (((dx * dx) + (dy * dy)) < bestD) {
						bestD = (dx * dx) + (dy * dy);
						best = w;
					}
				}
			}
			if (best != null) {
				orderTarget(orders, hero, best, refresh, now);
			}
			return best != null;
		}
		case "visit": {
			// walk through the listed rects in order; one counts once the hero has stood in it
			for (int i = 1; i < a.length; i++) {
				final Rectangle r = rect(g, a[i]);
				if (r.contains(hero.getX(), hero.getY()) && visited.add(map + ":" + a[i])) {
					log("visited " + a[i]);
				}
				if (!visited.contains(map + ":" + a[i])) {
					order(orders, hero, OrderIds.attack, r.x + (r.width / 2), r.y + (r.height / 2), a[i], refresh, now);
					return true;
				}
			}
			return true;
		}
		case "buy":
			return buy(g, orders, hero, a[1], War3ID.fromString(a[2]), refresh, now);
		case "stop":
		case "wait":
		default:
			return true;
		}
	}

	private static boolean buy(final GlobalScope g, final CPlayerUnitOrderListener orders, final CUnit hero,
			final String shopName, final War3ID itemId, final boolean refresh, final int now) {
		final CUnit shop = unitGlobal(g, shopName);
		if (shop == null) {
			return false;
		}
		final CAbilityNeutralBuilding neutral = shop.getNeutralBuildingData();
		final CAbilitySellItems sell = shop.getFirstAbilityOfType(CAbilitySellItems.class);
		if ((neutral == null) || (sell == null)) {
			log("buy: " + shopName + " sells nothing");
			return false;
		}
		if (neutral.getSelectedPlayerUnit(0) != hero) {
			order(orders, hero, OrderIds.smart, shop.getX(), shop.getY(), "shop" + shopName, refresh, now);
			return true;
		}
		final int index = sell.getItemsSold().indexOf(itemId);
		if (index < 0) {
			log("buy: " + shopName + " does not sell " + itemId);
			return false;
		}
		if ((now - lastOrder) >= seconds(3)) {
			final int buyOrder = 0 | ((index + 1) << 8);
			final Object itemType = sim.getItemData().getItemType(itemId);
			sell.checkCanUse(sim, shop, buyOrder, BooleanAbilityActivationReceiver.INSTANCE);
			final String[] use = { BooleanAbilityActivationReceiver.INSTANCE.isOk() ? "ok" : "refused" };
			log("buy " + itemId + " at " + shopName + " (gold " + sim.getPlayer(0).getGold() + "; type "
					+ (itemType == null ? "MISSING" : "ok") + ", stock " + sell.getItemStockCurrent(itemId) + ", use "
					+ use[0] + ", shop owner " + shop.getPlayerIndex() + (shop.isPaused() ? " paused" : "")
					+ ", shop order " + (shop.getCurrentBehavior() == null ? "-" : shop.getCurrentBehavior().getClass().getSimpleName())
					+ ", inventory " + (hero.getInventoryData() != null) + ")");
			orders.issueImmediateOrder(shop.getHandleId(), sell.getHandleId(), buyOrder, false);
			lastOrder = now;
		}
		return true;
	}

	/** Point order for the hero, and the same order for the rest of the party near him. */
	private static void order(final CPlayerUnitOrderListener orders, final CUnit hero, final int orderId,
			final float x, final float y, final String key, final boolean refresh, final int now) {
		aimX = x;
		aimY = y;
		if (!refresh && key.equals(lastOrderKey)) {
			return;
		}
		final float dx = hero.getX() - x, dy = hero.getY() - y;
		if (((dx * dx) + (dy * dy)) < (64 * 64) && isIdle(hero) && key.equals(lastOrderKey)) {
			return; // there, waiting for the script
		}
		for (final CUnit unit : party(hero)) {
			issuePoint(orders, unit, orderId, x, y);
		}
		lastOrderKey = key;
		lastOrder = now;
	}

	private static void orderTarget(final CPlayerUnitOrderListener orders, final CUnit hero, final com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget target,
			final boolean refresh, final int now) {
		aimX = target.getX();
		aimY = target.getY();
		final float hx = target.getX() - hero.getX(), hy = target.getY() - hero.getY();
		final String key = "unit" + target.getHandleId() + ((((hx * hx) + (hy * hy)) > (1200 * 1200)) ? "far" : "");
		if (!refresh && key.equals(lastOrderKey)) {
			return;
		}
		// units and items: right-click; destructables (support columns, rocks): attack
		final int orderId = (target instanceof CUnit) || (target instanceof CItem) ? OrderIds.smart : OrderIds.attack;
		final float dx = target.getX() - hero.getX(), dy = target.getY() - hero.getY();
		final boolean far = ((dx * dx) + (dy * dy)) > (1200 * 1200);
		for (final CUnit unit : party(hero)) {
			if (far) {
				// far away (often in the fog): walk there fighting; target it once close
				issuePoint(orders, unit, OrderIds.attack, target.getX(), target.getY());
			}
			else if (!issueTarget(orders, unit, target, orderId)) {
				// refused (out of sight in the fog...): attack-move to where it stands, as a player would
				issuePoint(orders, unit, OrderIds.attack, target.getX(), target.getY());
			}
		}
		lastOrderKey = key;
		lastOrder = now;
	}

	private static void issuePoint(final CPlayerUnitOrderListener orders, final CUnit unit, final int orderId,
			final float x, final float y) {
		final AbilityPointTarget point = new AbilityPointTarget(x, y);
		for (final CAbility ability : unit.getAbilities()) {
			ability.checkCanUse(sim, unit, orderId, BooleanAbilityActivationReceiver.INSTANCE);
			if (!BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
				continue;
			}
			final Receiver<AbilityPointTarget> ok = new Receiver<>();
			ability.checkCanTarget(sim, unit, orderId, point, ok);
			if (ok.target != null) {
				orders.issuePointOrder(unit.getHandleId(), ability.getHandleId(), orderId, x, y, false);
				issued++;
				return;
			}
		}
		refused++;
	}

	private static boolean issueTarget(final CPlayerUnitOrderListener orders, final CUnit unit, final com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget target,
			final int orderId) {
		final int targetHandle = target.getHandleId();
		CAbility use = null;
		for (final CAbility ability : unit.getAbilities()) {
			final Receiver<com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget> ok = new Receiver<>();
			ability.checkCanTarget(sim, unit, orderId, target, ok);
			if (ok.target != null) {
				use = ability; // like the UI: the last ability that accepts the target
			}
		}
		if (use != null) {
			orders.issueTargetOrder(unit.getHandleId(), use.getHandleId(), orderId, targetHandle, false);
			issued++;
			return true;
		}
		return false;
	}

	private static void learnSkills(final CPlayerUnitOrderListener orders) {
		for (final CUnit unit : sim.getUnits()) {
			final CAbilityHero heroData = unit.getHeroData();
			if ((unit.getPlayerIndex() != 0) || (heroData == null) || unit.isDead() || (heroData.getSkillPoints() <= 0)) {
				continue;
			}
			final List<War3ID> order = new ArrayList<>(section.learn);
			for (final War3ID id : heroData.getSkillsAvailable()) {
				if (!order.contains(id)) {
					order.add(id);
				}
			}
			for (final War3ID id : order) {
				if (!heroData.getSkillsAvailable().contains(id)) {
					continue;
				}
				heroData.checkCanUse(sim, unit, id.getValue(), BooleanAbilityActivationReceiver.INSTANCE);
				if (!BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
					continue;
				}
				final Receiver<Void> ok = new Receiver<>();
				heroData.checkCanTargetNoTarget(sim, unit, id.getValue(), ok);
				if (ok.ok) {
					log("learn " + id + " for " + unit.getTypeId() + " (level " + heroData.getHeroLevel() + ")");
					orders.issueImmediateOrder(unit.getHandleId(), heroData.getHandleId(), id.getValue(), false);
					return; // one per think: the next point after this one is applied
				}
			}
		}
	}

	private static final Map<Integer, Integer> lastCast = new HashMap<>();

	/**
	 * Heroes in a fight cast what they can, like a player would: each tries its spells on the nearest enemy (heroes
	 * first), then a hurt ally, then the enemy's position, then with no target. The engine's own order checks
	 * decide what is possible (cooldown, mana, targets allowed).
	 */
	private static void fight(final CPlayerUnitOrderListener orders, final int now) {
		for (final CUnit hero : sim.getUnits()) {
			if ((hero.getPlayerIndex() != 0) || (hero.getHeroData() == null) || hero.isDead() || hero.isHidden()
					|| hero.isPaused()) {
				continue;
			}
			final Integer last = lastCast.get(hero.getHandleId());
			if ((last != null) && ((now - last) < seconds(2))) {
				continue;
			}
			CUnit enemy = null;
			float enemyD = 800 * 800;
			CUnit hurt = null;
			float hurtRatio = 0.6f;
			for (final CUnit u : sim.getUnits()) {
				if (u.isDead() || u.isHidden() || u.isBuilding()) {
					continue;
				}
				final float d = dist2(u, hero);
				if (d > (800 * 800)) {
					continue;
				}
				final boolean ally = (u.getPlayerIndex() == 0) || sim.getPlayer(0).hasAlliance(u.getPlayerIndex(), CAllianceType.PASSIVE);
				if (!ally && !u.isInvulnerable()) {
					final float weighted = u.getHeroData() != null ? d / 4 : d;
					if (weighted < enemyD) {
						enemyD = weighted;
						enemy = u;
					}
				}
				else if ((u.getPlayerIndex() == 0) && ((u.getLife() / Math.max(1, u.getMaximumLife())) < hurtRatio)) {
					hurtRatio = u.getLife() / Math.max(1, u.getMaximumLife());
					hurt = u;
				}
			}
			if (enemy == null) {
				continue;
			}
			for (final CAbility ability : hero.getAbilities()) {
				if (!(ability instanceof com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.SingleOrderAbility)
						|| (ability instanceof com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory)) {
					continue;
				}
				final int orderId = ((com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.SingleOrderAbility) ability).getBaseOrderId();
				ability.checkCanUse(sim, hero, orderId, BooleanAbilityActivationReceiver.INSTANCE);
				if (!BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
					continue;
				}
				final String what;
				final Receiver<com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget> onEnemy = new Receiver<>();
				ability.checkCanTarget(sim, hero, orderId, enemy, onEnemy);
				final Receiver<com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget> onAlly = new Receiver<>();
				if (hurt != null) {
					ability.checkCanTarget(sim, hero, orderId, hurt, onAlly);
				}
				final Receiver<AbilityPointTarget> onPoint = new Receiver<>();
				ability.checkCanTarget(sim, hero, orderId, new AbilityPointTarget(enemy.getX(), enemy.getY()), onPoint);
				final Receiver<Void> noTarget = new Receiver<>();
				ability.checkCanTargetNoTarget(sim, hero, orderId, noTarget);
				if (onEnemy.ok) {
					orders.issueTargetOrder(hero.getHandleId(), ability.getHandleId(), orderId, enemy.getHandleId(), false);
					what = "on " + enemy.getTypeId();
				}
				else if (onAlly.ok) {
					orders.issueTargetOrder(hero.getHandleId(), ability.getHandleId(), orderId, hurt.getHandleId(), false);
					what = "on ally " + hurt.getTypeId();
				}
				else if (onPoint.ok) {
					orders.issuePointOrder(hero.getHandleId(), ability.getHandleId(), orderId, enemy.getX(), enemy.getY(), false);
					what = "at " + enemy.getTypeId();
				}
				else if (noTarget.ok) {
					orders.issueImmediateOrder(hero.getHandleId(), ability.getHandleId(), orderId, false);
					what = "(no target)";
				}
				else {
					continue;
				}
				log("cast " + ability.getAlias() + " by " + hero.getTypeId() + " " + what);
				lastCast.put(hero.getHandleId(), now);
				break;
			}
		}
	}

	// ---- observation

	private static void watchQuests(final GlobalScope g, final int now) {
		for (final String q : section.quests) {
			final StringBuilder state = new StringBuilder();
			final JassValue progress = global(g, "udg_Quest" + q + "Progress");
			if (progress instanceof IntegerJassValue) {
				state.append("progress ").append(((IntegerJassValue) progress).getValue());
			}
			final JassValue quest = global(g, "udg_Quest" + q);
			if ((quest instanceof HandleJassValue) && (((HandleJassValue) quest).getJavaValue() instanceof CQuest)) {
				final CQuest cq = (CQuest) ((HandleJassValue) quest).getJavaValue();
				state.append(cq.isDiscovered() ? " discovered" : "").append(cq.isCompleted() ? " COMPLETED" : "")
						.append(cq.isFailed() ? " FAILED" : "");
				int done = 0;
				for (int i = 0; i < cq.getItems().size(); i++) {
					done += cq.getItems().get(i).isCompleted() ? 1 : 0;
				}
				if (!cq.getItems().isEmpty()) {
					state.append(" items ").append(done).append('/').append(cq.getItems().size());
				}
			}
			final String s = state.toString();
			final String old = watched.put(q, s);
			if ((old != null) && !old.equals(s)) {
				log("QUEST " + q + ": " + old + " -> " + s);
				NxCampaignNatives.requestShot("play_" + map + "_" + q + "_" + now);
			}
			else if (old == null) {
				log("quest " + q + ": " + s);
			}
		}
	}

	private static final Map<Integer, String> heroStates = new HashMap<>();

	/** Every hero of player 0: deaths, revivals, levels. */
	private static void watchHeroes() {
		for (final CUnit u : sim.getUnits()) {
			if ((u.getPlayerIndex() != 0) || (u.getHeroData() == null)) {
				continue;
			}
			final String state = (u.isDead() ? "dead" : "alive") + " level " + u.getHeroData().getHeroLevel();
			final String old = heroStates.put(u.getHandleId(), state);
			if ((old != null) && !old.equals(state)) {
				log("HERO " + u.getTypeId() + ": " + old + " -> " + state + " at " + (int) u.getX() + "," + (int) u.getY());
			}
		}
	}

	private static void watchHero(final CUnit hero) {
		if (hero == null) {
			return;
		}
		if (hero.isDead() != heroDead) {
			heroDead = hero.isDead();
			log(heroDead ? "HERO DIED" : "hero alive again");
		}
		final int level = hero.getHeroData() == null ? 0 : hero.getHeroData().getHeroLevel();
		if (level != heroLevel) {
			if (heroLevel >= 0) {
				log("hero level " + level);
			}
			heroLevel = level;
		}
	}

	/**
	 * Diagnostic for a stuck goal: an uncapped flood fill of the pathing grid from the leader (his movement type
	 * and collision size, terrain and doodads only, no units) and the closest reachable point to the last aim
	 * point. Tells a pathfinder limit (a reachable spot near the target exists) from an unreachable target.
	 */
	private static void reachability(final CUnit hero) {
		reachability(hero, false);
	}

	private static void reachability(final CUnit hero, final boolean makeRoute) {
		final com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid grid = sim.getPathingGrid();
		final com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType mt = hero.getMovementType();
		if (mt == null) {
			return;
		}
		final float coll = hero.getUnitType().getCollisionSize();
		final int w = grid.getWidth(), h = grid.getHeight();
		final boolean[] seen = new boolean[w * h];
		final int[] queue = new int[w * h];
		final int[] parent = makeRoute ? new int[w * h] : null;
		final int sx = grid.getCellX(hero.getX()), sy = grid.getCellY(hero.getY());
		int head = 0, tail = 0, count = 0;
		queue[tail++] = (sy * w) + sx;
		seen[(sy * w) + sx] = true;
		int best = queue[0];
		float bestD = Float.MAX_VALUE;
		while (head < tail) {
			final int c = queue[head++];
			count++;
			final int cx = c % w, cy = c / w;
			final float dx = grid.getWorldX(cx) - aimX, dy = grid.getWorldY(cy) - aimY;
			final float d = (dx * dx) + (dy * dy);
			if (d < bestD) {
				bestD = d;
				best = c;
			}
			for (int k = 0; k < 4; k++) {
				final int nx = cx + ((k == 0) ? 1 : (k == 1) ? -1 : 0), ny = cy + ((k == 2) ? 1 : (k == 3) ? -1 : 0);
				if ((nx < 0) || (ny < 0) || (nx >= w) || (ny >= h) || seen[(ny * w) + nx]) {
					continue;
				}
				seen[(ny * w) + nx] = true;
				if (grid.isCellPathable(nx, ny, mt, coll)) {
					queue[tail++] = (ny * w) + nx;
					if (parent != null) {
						parent[(ny * w) + nx] = c;
					}
				}
			}
		}
		if (parent != null) {
			// legs of ~20 cells from the start towards the closest reachable cell
			final List<float[]> legs = new ArrayList<>();
			int c = best;
			int step = 0;
			final int start = (sy * w) + sx;
			while (c != start) {
				if ((step++ % 20) == 0) {
					legs.add(0, new float[] { grid.getWorldX(c % w), grid.getWorldY(c / w) });
				}
				c = parent[c];
			}
			route.clear();
			route.addAll(legs);
			log("route: " + legs.size() + " legs towards " + (int) aimX + "," + (int) aimY + " (" + step + " cells)");
			return;
		}
		log("reachability: aim " + (int) aimX + "," + (int) aimY + "; " + count + " cells reachable by "
				+ describe(hero) + "; closest reachable " + (int) grid.getWorldX(best % w) + ","
				+ (int) grid.getWorldY(best / w) + " at distance " + (int) Math.sqrt(bestD));
	}

	private static String xp(final CUnit u) {
		if ((u == null) || (u.getHeroData() == null)) {
			return "";
		}
		return " lvl " + u.getHeroData().getHeroLevel() + " xp " + u.getHeroData().getXp()
				+ (u.getHeroData().isXpSuspended() ? " XP-SUSPENDED" : "");
	}

	private static String describe(final CUnit u) {
		if (u == null) {
			return "no hero";
		}
		return u.getTypeId() + " at " + (int) u.getX() + "," + (int) u.getY() + " life " + (int) u.getLife() + "/"
				+ u.getMaximumLife() + (u.isDead() ? " DEAD" : "") + (u.isPaused() ? " paused" : "")
				+ (u.isHidden() ? " hidden" : "") + " order " + (u.getCurrentBehavior() == null ? "-"
						: u.getCurrentBehavior().getClass().getSimpleName());
	}

	/** An enemy is within 600 of the unit (a fight standing still is not a stall). */
	private static boolean fighting(final CUnit hero) {
		for (final CUnit u : sim.getUnits()) {
			if (!u.isDead() && !u.isHidden() && (u.getPlayerIndex() != 0)
					&& !sim.getPlayer(0).hasAlliance(u.getPlayerIndex(), CAllianceType.PASSIVE)
					&& (dist2(u, hero) < (600 * 600))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isIdle(final CUnit u) {
		final Object b = u.getCurrentBehavior();
		return (b == null) || (b == u.getStopBehavior()) || (b == u.getHoldPositionBehavior());
	}

	/** The hero and player 0's other mobile units near him (summons, followers, wyverns). */
	private static List<CUnit> party(final CUnit hero) {
		final List<CUnit> party = new ArrayList<>();
		party.add(hero);
		// listed companions, wherever they are ("party Rokhan Chen:ChenInParty": Chen once udg_ChenInParty)
		final GlobalScope g = sim.getGlobalScope();
		for (final String entry : section.party) {
			final String[] nc = entry.split(":");
			if ((nc.length > 1) && !bool(g, nc[1]) && !bool(g, "udg_" + nc[1])) {
				continue;
			}
			final CUnit u = unitGlobal(g, nc[0]);
			if ((u != null) && (u != hero) && !u.isDead() && !u.isHidden() && !u.isPaused()) {
				party.add(u);
			}
		}
		for (final CUnit u : sim.getUnits()) {
			if ((u != hero) && !party.contains(u) && (u.getHeroData() == null) && (u.getPlayerIndex() == 0) && !u.isDead() && !u.isBuilding() && !u.isHidden()
					&& !u.isPaused() && (u.getMoveBehavior() != null) && (dist2(u, hero) < (2500 * 2500))) {
				party.add(u);
			}
		}
		return party;
	}

	private static CUnit nearest(final CUnit hero, final List<CUnit> units, final int player, final Rectangle r) {
		CUnit best = null;
		float bestD = Float.MAX_VALUE;
		if (units == null) {
			return null;
		}
		for (final CUnit u : units) {
			if (u.isDead() || u.isHidden() || ((player >= 0) && (u.getPlayerIndex() != player))
					|| ((r != null) && !r.contains(u.getX(), u.getY()))) {
				continue;
			}
			if ((player >= 0) && sim.getPlayer(0).hasAlliance(player, CAllianceType.PASSIVE)) {
				continue;
			}
			final float d = dist2(u, hero);
			if (d < bestD) {
				bestD = d;
				best = u;
			}
		}
		return best;
	}

	private static CItem nearestItem(final CUnit hero, final int typeId) {
		CItem best = null;
		float bestD = Float.MAX_VALUE;
		for (final CItem item : sim.getItems()) {
			if (item.isDead() || item.isHidden() || (item.getTypeId().getValue() != typeId)) {
				continue;
			}
			final float dx = item.getX() - hero.getX(), dy = item.getY() - hero.getY();
			final float d = (dx * dx) + (dy * dy);
			if (d < bestD) {
				bestD = d;
				best = item;
			}
		}
		return best;
	}

	private static float dist2(final CUnit a, final CUnit b) {
		final float dx = a.getX() - b.getX(), dy = a.getY() - b.getY();
		return (dx * dx) + (dy * dy);
	}

	// ---- globals

	private static JassValue global(final GlobalScope g, final String name) {
		try {
			return g.getGlobal(name);
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	private static JassValue udg(final GlobalScope g, final String name) {
		JassValue v = global(g, name);
		if ((v == null) && !name.startsWith("udg_") && !name.startsWith("gg_") && !name.startsWith("bj_")) {
			v = global(g, "udg_" + name);
		}
		return v;
	}

	private static boolean bool(final GlobalScope g, final String name) {
		final JassValue v = global(g, name);
		return (v instanceof BooleanJassValue) && ((BooleanJassValue) v).getValue();
	}

	private static CUnit unitGlobal(final GlobalScope g, final String name) {
		final JassValue v = udg(g, name);
		final Object o = v instanceof HandleJassValue ? ((HandleJassValue) v).getJavaValue() : null;
		return o instanceof CUnit ? (CUnit) o : null;
	}

	@SuppressWarnings("unchecked")
	private static List<CUnit> groupGlobal(final GlobalScope g, final String name) {
		final JassValue v = udg(g, name);
		final Object o = v instanceof HandleJassValue ? ((HandleJassValue) v).getJavaValue() : null;
		return o instanceof List ? (List<CUnit>) o : null;
	}

	private static Rectangle rect(final GlobalScope g, final String name) {
		JassValue v = global(g, name);
		if (v == null) {
			v = global(g, "gg_rct_" + name);
		}
		final Object o = v instanceof HandleJassValue ? ((HandleJassValue) v).getJavaValue() : null;
		if (!(o instanceof Rectangle)) {
			throw new IllegalArgumentException("no rect " + name);
		}
		return (Rectangle) o;
	}

	private static int seconds(final float s) {
		return (int) (s / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	private static void log(final String s) {
		final float t = sim == null ? 0 : sim.getGameTurnTick() * WarsmashConstants.SIMULATION_STEP_TIME;
		System.err.println("NXPLAY " + map + " t=" + (int) t + ": " + s);
	}

	private static String mapName(final String path) {
		String m = path.replace('/', '\\');
		m = m.substring(m.lastIndexOf('\\') + 1);
		final String lower = m.toLowerCase(Locale.US);
		if (lower.endsWith(".w3x") || lower.endsWith(".w3m") || lower.endsWith(".w3z")) {
			m = m.substring(0, m.length() - 4);
		}
		return m;
	}

	// ---- goal file

	private static final class Section {
		final List<String> quests = new ArrayList<>();
		final List<String> party = new ArrayList<>();
		final List<War3ID> learn = new ArrayList<>();
		final List<Goal> goals = new ArrayList<>();
		final List<String> hero = new ArrayList<>(java.util.Arrays.asList("udg_Rexxar"));
		int skipCine;
	}

	private static final class Goal {
		final String text;
		final String[][] terms; // {name, op, value}
		final List<String[]> alternatives = new ArrayList<>();

		Goal(final String text, final String cond, final String action) {
			this.text = text;
			if ("always".equals(cond.trim())) {
				this.terms = new String[0][];
			}
			else {
				final String[] parts = cond.split("&");
				this.terms = new String[parts.length][];
				for (int i = 0; i < parts.length; i++) {
					final String p = parts[i].trim();
					final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(!?)(\\w+)\\s*(<=|>=|==|!=|<|>)?\\s*(-?\\d+)?")
							.matcher(p);
					if (!m.matches()) {
						throw new IllegalArgumentException("bad condition: " + p);
					}
					this.terms[i] = new String[] { m.group(2), m.group(3) == null ? (m.group(1).isEmpty() ? "true" : "false")
							: m.group(3), m.group(4) };
				}
			}
			for (final String alt : action.split("\\|")) {
				this.alternatives.add(alt.trim().split("\\s+"));
			}
		}

		boolean holds(final GlobalScope g) {
			for (final String[] t : this.terms) {
				final JassValue v = "HEROLEVEL".equals(t[0]) ? IntegerJassValue.of(leaderLevel)
						: "TIME".equals(t[0]) ? IntegerJassValue.of((int) (thinkNow * WarsmashConstants.SIMULATION_STEP_TIME))
						: udg(g, t[0]);
				if (v == null) {
					return false;
				}
				if ("true".equals(t[1]) || "false".equals(t[1])) {
					final boolean b = v instanceof BooleanJassValue ? ((BooleanJassValue) v).getValue()
							: (v instanceof IntegerJassValue) && (((IntegerJassValue) v).getValue() != 0);
					if (b != "true".equals(t[1])) {
						return false;
					}
					continue;
				}
				if (!(v instanceof IntegerJassValue)) {
					return false;
				}
				final int x = ((IntegerJassValue) v).getValue(), y = Integer.parseInt(t[2]);
				final boolean ok;
				switch (t[1]) {
				case "<": ok = x < y; break;
				case "<=": ok = x <= y; break;
				case "==": ok = x == y; break;
				case "!=": ok = x != y; break;
				case ">=": ok = x >= y; break;
				default: ok = x > y; break;
				}
				if (!ok) {
					return false;
				}
			}
			return true;
		}
	}

	private static Section load(final String mapName) {
		Section current = null;
		Section found = null;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				if (line.startsWith("[")) {
					final String name = line.substring(1, line.indexOf(']'));
					current = name.equalsIgnoreCase(mapName) ? new Section() : null;
					if (current != null) {
						found = current;
					}
					continue;
				}
				if (current == null) {
					continue;
				}
				final String[] w = line.split("\\s+");
				switch (w[0]) {
				case "quests":
					for (int i = 1; i < w.length; i++) {
						current.quests.add(w[i]);
					}
					break;
				case "party":
					for (int i = 1; i < w.length; i++) {
						current.party.add(w[i]);
					}
					break;
				case "learn":
					for (int i = 1; i < w.length; i++) {
						current.learn.add(War3ID.fromString(w[i]));
					}
					break;
				case "hero":
					current.hero.clear();
					for (int i = 1; i < w.length; i++) {
						current.hero.add(w[i]);
					}
					break;
				case "skipcine":
					current.skipCine = Integer.parseInt(w[1]);
					break;
				case "goal": {
					final String rest = line.substring(4).trim();
					final int colon = rest.indexOf(':');
					current.goals.add(new Goal(rest, rest.substring(0, colon), rest.substring(colon + 1)));
					break;
				}
				default:
					System.err.println("NXPLAY: unknown line in " + FILE + ": " + line);
				}
			}
		}
		catch (final Exception e) {
			System.err.println("NXPLAY: cannot read " + FILE + ": " + e);
		}
		return found;
	}

	private static final class Receiver<T> implements AbilityTargetCheckReceiver<T> {
		T target;
		boolean ok;

		@Override
		public void targetOk(final T target) {
			this.target = target;
			this.ok = true;
		}

		@Override
		public void notAnActiveAbility() {
		}

		@Override
		public void orderIdNotAccepted() {
		}

		@Override
		public void targetCheckFailed(final String commandStringErrorKey) {
		}

	}
}
