package com.etheller.warsmash.parsers.jass;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.parsers.jass.triggers.LocationJass;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.GetAbilityByRawcodeVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellItems;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.NxCineFilter;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;

/**
 * warsmash-nx: the natives a Warcraft III campaign needs that upstream Warsmash declares but does not
 * implement (it printed "no native implementation" and returned null for each). Without them a campaign
 * cannot carry its hero from one map to the next (game cache), cannot move to the next map (ChangeLevel),
 * and its conditions read wrong values (GetHeroLevel 0, IsLocationVisibleToPlayer false, ...).
 *
 * Registered last in Jass2.CommonEnvironment, so it only adds natives; it never replaces one that exists
 * (see {@link #define}).
 *
 * Saved games are not supported (that would mean serialising the whole simulation): SaveGame does
 * nothing and SaveGameExists answers false, so a campaign that goes back to a map it already visited
 * (the Frozen Throne orc campaign does) loads that map afresh; what the map keeps across visits through
 * the game cache (the hero, its items, quest flags) still carries over.
 */
public final class NxCampaignNatives {
	private NxCampaignNatives() {
	}

	// ------------------------------------------------------------------------------------------------
	// Level changes: the natives only record the request; WarsmashGdxMapScreen.render acts on it at the
	// start of the next frame (switching screens in the middle of a simulation step would be unsafe).
	// ------------------------------------------------------------------------------------------------

	/** Map to load next ("" = back to the menu), or null: nothing requested. */
	private static volatile String pendingLevel;
	/** The map being played, for RestartGame. Set by MenuUI when it starts a map. */
	public static volatile String currentMap;

	public static void requestLevel(final String level) {
		System.err.println("warsmash-nx: level change requested: '" + level + "' (from '" + currentMap + "')");
		pendingLevel = level;
	}

	/** The pending request (and clears it): a map path, "" for the menu, null if none. */
	public static String takePendingLevel() {
		final String level = pendingLevel;
		pendingLevel = null;
		return level;
	}

	// ------------------------------------------------------------------------------------------------
	// PC test hook (inert unless the NX_DEBUG environment variable is set; never set on the console):
	//   NX_DEBUG="OrcX01@30:udg_TransitionTarget=2,udg_TransitionTargetArea=1,gg_trg_Transition;OrcX01_02@20:heroes,quit"
	// = when map OrcX01 has run 30 game seconds, set those integer globals and run that trigger; on
	// OrcX01_02 at 20 s print every hero (type, level, xp, skills, items), then quit. Each step runs once.
	// ------------------------------------------------------------------------------------------------

	private static final java.util.Set<String> DEBUG_DONE = new java.util.HashSet<>();
	/** "shot" step: the game window's own frame to save at the end of this frame (WarsmashGdxMapScreen). */
	private static String pendingShot;

	/** Saves the game's own frame to core/assets/nxshots/<name>.png at the end of this frame (PC tests). */
	public static void requestShot(final String name) {
		pendingShot = "nxshots/" + name + ".png";
	}

	public static String takePendingShot() {
		final String shot = pendingShot;
		pendingShot = null;
		return shot;
	}

	private static float menuSeconds;
	private static boolean menuStarted;

	/**
	 * The same steps on the main menu, under the name "Menu" and timed from the menu's first frame:
	 * NX_DEBUG="Menu@4:key:DPAD_DOWN,shot;Menu@5:click". Only the input steps, "shot" and "quit".
	 */
	public static void debugMenuTick(final float deltaTime) {
		final String spec = System.getenv("NX_DEBUG");
		if (spec == null) {
			return;
		}
		// The first frame's delta is the whole load before it (~20 s on the PC): the clock starts there.
		if (menuStarted) {
			menuSeconds += deltaTime;
		}
		menuStarted = true;
		for (final String step : spec.split(";")) {
			final int at = step.indexOf('@');
			final int colon = step.indexOf(':');
			if ((at < 0) || (colon < at) || DEBUG_DONE.contains(step) || !step.startsWith("Menu@")
					|| (menuSeconds < Float.parseFloat(step.substring(at + 1, colon)))) {
				continue;
			}
			DEBUG_DONE.add(step);
			for (final String action : step.substring(colon + 1).split(",")) {
				System.err.println("NXDEBUG Menu t=" + menuSeconds + ": " + action);
				if ("shot".equals(action)) {
					pendingShot = "nxshots/Menu_" + String.format(java.util.Locale.ROOT, "%05.1f", menuSeconds) + ".png";
				}
				else if ("quit".equals(action)) {
					com.badlogic.gdx.Gdx.app.exit();
				}
				else {
					inputAction(action);
				}
			}
		}
	}

	/**
	 * Input steps for PC tests of the console's pad: key:NAME presses and releases a pad key
	 * (BUTTON_START, BUTTON_A, BUTTON_B, DPAD_UP/DOWN/LEFT/RIGHT); lstick:X/Y sets the camera stick
	 * (-1..1, y forward; lstick:0/0 releases it); cursor:X/Y moves the cursor (pixels, y down); click
	 * presses and releases the left button where the cursor is, rclick the right one.
	 */
	private static boolean inputAction(final String action) {
		final com.badlogic.gdx.InputProcessor input = com.badlogic.gdx.Gdx.input.getInputProcessor();
		if (action.startsWith("key:")) {
			final int key = padKey(action.substring(4));
			if ((key >= 0) && (input != null)) {
				input.keyDown(key);
				input.keyUp(key);
			}
			System.err.println("NXDEBUG cursor now " + com.badlogic.gdx.Gdx.input.getX() + ","
					+ com.badlogic.gdx.Gdx.input.getY());
			return true;
		}
		if (action.startsWith("lstick:")) {
			final String[] xy = action.substring(7).split("/");
			com.etheller.warsmash.util.PadSticks.cameraX = Float.parseFloat(xy[0]);
			com.etheller.warsmash.util.PadSticks.cameraY = Float.parseFloat(xy[1]);
			return true;
		}
		if (action.startsWith("cursor:")) {
			final String[] xy = action.substring(7).split("/");
			final int x = Integer.parseInt(xy[0]);
			final int y = Integer.parseInt(xy[1]);
			com.badlogic.gdx.Gdx.input.setCursorPosition(x, y);
			if (input != null) {
				input.mouseMoved(x, y);
			}
			return true;
		}
		if ("click".equals(action) || "rclick".equals(action)) {
			final int x = com.badlogic.gdx.Gdx.input.getX();
			final int y = com.badlogic.gdx.Gdx.input.getY();
			final int button = "click".equals(action) ? com.badlogic.gdx.Input.Buttons.LEFT
					: com.badlogic.gdx.Input.Buttons.RIGHT;
			if (input != null) {
				input.touchDown(x, y, 0, button);
				input.touchUp(x, y, 0, button);
			}
			return true;
		}
		return false;
	}

	private static int padKey(final String name) {
		switch (name) {
		case "BUTTON_START":
			return com.badlogic.gdx.Input.Keys.BUTTON_START;
		case "BUTTON_A":
			return com.badlogic.gdx.Input.Keys.BUTTON_A;
		case "BUTTON_B":
			return com.badlogic.gdx.Input.Keys.BUTTON_B;
		case "DPAD_UP":
			return com.badlogic.gdx.Input.Keys.DPAD_UP;
		case "DPAD_DOWN":
			return com.badlogic.gdx.Input.Keys.DPAD_DOWN;
		case "DPAD_LEFT":
			return com.badlogic.gdx.Input.Keys.DPAD_LEFT;
		case "DPAD_RIGHT":
			return com.badlogic.gdx.Input.Keys.DPAD_RIGHT;
		case "ESCAPE":
			return com.badlogic.gdx.Input.Keys.ESCAPE;
		default:
			System.err.println("NXDEBUG unknown key " + name);
			return -1;
		}
	}

	public static void debugTick(final CSimulation sim) {
		final String spec = System.getenv("NX_DEBUG");
		if ((spec == null) || (currentMap == null) || (sim.getGlobalScope() == null)) {
			return;
		}
		String map = currentMap.replace('/', '\\');
		map = map.substring(map.lastIndexOf('\\') + 1);
		if (map.toLowerCase(Locale.US).endsWith(".w3x") || map.toLowerCase(Locale.US).endsWith(".w3m")) {
			map = map.substring(0, map.length() - 4);
		}
		final float seconds = sim.getGameTurnTick() * com.etheller.warsmash.util.WarsmashConstants.SIMULATION_STEP_TIME;
		for (final String step : spec.split(";")) {
			final int at = step.indexOf('@');
			final int colon = step.indexOf(':');
			if ((at < 0) || (colon < at) || DEBUG_DONE.contains(step) || !step.substring(0, at).equalsIgnoreCase(map)
					|| (seconds < Float.parseFloat(step.substring(at + 1, colon)))) {
				continue;
			}
			DEBUG_DONE.add(step);
			final GlobalScope globals = sim.getGlobalScope();
			for (final String action : step.substring(colon + 1).split(",")) {
				System.err.println("NXDEBUG " + map + " t=" + seconds + ": " + action);
				if ("shot".equals(action)) {
					pendingShot = "nxshots/" + map + "_" + ((seconds == (int) seconds) ? String.valueOf((int) seconds) : String.format(java.util.Locale.ROOT, "%05.2f", seconds)) + ".png";
				}
				else if ("quit".equals(action)) {
					com.badlogic.gdx.Gdx.app.exit();
				}
				else if ("heroes".equals(action)) {
					for (final CUnit unit : sim.getUnits()) {
						if (unit.getHeroData() != null) {
							final StoredUnit u = storeUnit(unit);
							final StringBuilder b = new StringBuilder("NXDEBUG hero " + unit.getTypeId() + " player "
									+ unit.getPlayerIndex() + " level " + unit.getHeroData().getHeroLevel() + " xp " + u.xp
									+ " sp " + u.skillPoints + " dead " + unit.isDead() + " skills");
							for (final int[] s : u.skills) {
								b.append(' ').append(new War3ID(s[0])).append('=').append(s[1]);
							}
							b.append(" items");
							for (final int[] i : u.items) {
								b.append(' ').append(i[0]).append(':').append(new War3ID(i[1])).append('x').append(i[2]);
							}
							System.err.println(b);
						}
					}
				}
				else if (action.startsWith("script:")) {
					// script:<file in a data source>@<function>: loads a JASS file into the running map and
					// runs that function (Warsmash's LoadScriptFile body, which no loaded script declares)
					final String[] pf = action.substring(7).split("@", 2);
					final JassFunction loader = mapProgram.getJassNativeManager().getUnboundNative("LoadScriptFile");
					final List<JassValue> args = new ArrayList<>();
					args.add(new StringJassValue(pf[0]));
					args.add(new StringJassValue(pf.length > 1 ? pf[1] : "main"));
					loader.call(args, globals, com.etheller.interpreter.ast.scope.TriggerExecutionScope.EMPTY);
				}
				else if (inputAction(action)) {
					// a pad key, the camera stick, the cursor or a click (inputAction)
					System.err.println("NXDEBUG   game paused " + sim.isGamePaused());
				}
				else if (action.contains("=")) {
					final String[] kv = action.split("=", 2);
					globals.setGlobal(kv[0], IntegerJassValue.of(Integer.parseInt(kv[1])));
				}
				else {
					final com.etheller.interpreter.ast.scope.trigger.Trigger trigger = globals.getGlobal(action)
							.visit(ObjectJassValueVisitor.<com.etheller.interpreter.ast.scope.trigger.Trigger>getInstance());
					trigger.executeNow(globals, new CommonTriggerExecutionScope(trigger,
							com.etheller.interpreter.ast.scope.TriggerExecutionScope.EMPTY));
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Game cache
	// ------------------------------------------------------------------------------------------------

	/** What StoreUnit keeps of a unit, and RestoreUnit rebuilds. */
	static final class StoredUnit {
		int typeId;
		int xp;
		int skillPoints;
		int strength;
		int agility;
		int intelligence;
		float lifeRatio = 1;
		float manaRatio = 1;
		final List<int[]> skills = new ArrayList<>(); // {abilityId, level}
		final List<int[]> items = new ArrayList<>(); // {slot, itemTypeId, charges}
	}

	private static final byte T_INT = 'i', T_REAL = 'r', T_BOOL = 'b', T_STRING = 's', T_UNIT = 'u';

	public static final class GameCache {
		final String name;
		/** mission key -> (type char + key) -> value; keys lower case (WC3 game cache keys ignore case). */
		final Map<String, Map<String, Object>> missions = new LinkedHashMap<>();

		GameCache(final String name) {
			this.name = name;
		}

		Map<String, Object> mission(final String missionKey, final boolean create) {
			final String m = norm(missionKey);
			Map<String, Object> map = this.missions.get(m);
			if ((map == null) && create) {
				map = new LinkedHashMap<>();
				this.missions.put(m, map);
			}
			return map;
		}

		void put(final String missionKey, final byte type, final String key, final Object value) {
			mission(missionKey, true).put((char) type + norm(key), value);
		}

		Object get(final String missionKey, final byte type, final String key) {
			final Map<String, Object> map = mission(missionKey, false);
			return map == null ? null : map.get((char) type + norm(key));
		}

		void remove(final String missionKey, final byte type, final String key) {
			final Map<String, Object> map = mission(missionKey, false);
			if (map != null) {
				map.remove((char) type + norm(key));
			}
		}
	}

	private static final Map<String, GameCache> CACHES = new HashMap<>();
	private static final String CACHE_DIR = "Save/GameCache";

	private static String norm(final String key) {
		return key == null ? "" : key.toLowerCase(Locale.US);
	}

	private static File cacheFile(final String name) {
		final StringBuilder safe = new StringBuilder();
		for (final char c : norm(name).toCharArray()) {
			safe.append(Character.isLetterOrDigit(c) || (c == '.') || (c == '-') || (c == '_') ? c : '_');
		}
		return new File(CACHE_DIR, safe + ".nxgc");
	}

	static GameCache initGameCache(final String name) {
		final String key = norm(name);
		GameCache cache = CACHES.get(key);
		if (cache == null) {
			cache = new GameCache(name);
			final File file = cacheFile(name);
			if (file.isFile()) {
				try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
					readCache(in, cache);
					System.err.println("warsmash-nx: game cache '" + name + "' loaded from " + file);
				}
				catch (final IOException | RuntimeException e) {
					System.err.println("warsmash-nx: game cache '" + name + "' unreadable, starting empty: " + e);
					cache.missions.clear();
				}
			}
			CACHES.put(key, cache);
		}
		return cache;
	}

	static boolean saveGameCache(final GameCache cache) {
		final File file = cacheFile(cache.name);
		// one level at a time, and no rename: on the console Clearwing's File.mkdirs creates the deepest
		// directory first (fails for a new tree) and File.renameTo always returns false
		final File parent = file.getParentFile();
		if (parent.getParentFile() != null) {
			parent.getParentFile().mkdir();
		}
		parent.mkdir();
		try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
			writeCache(out, cache);
		}
		catch (final IOException e) {
			System.err.println("warsmash-nx: could not save game cache '" + cache.name + "': " + e);
			return false;
		}
		System.err.println("warsmash-nx: game cache '" + cache.name + "' saved to " + file);
		return true;
	}

	private static final int CACHE_MAGIC = 0x4E584743; // NXGC

	private static void writeCache(final DataOutputStream out, final GameCache cache) throws IOException {
		out.writeInt(CACHE_MAGIC);
		out.writeInt(1);
		out.writeInt(cache.missions.size());
		for (final Map.Entry<String, Map<String, Object>> mission : cache.missions.entrySet()) {
			out.writeUTF(mission.getKey());
			out.writeInt(mission.getValue().size());
			for (final Map.Entry<String, Object> entry : mission.getValue().entrySet()) {
				out.writeUTF(entry.getKey());
				final Object v = entry.getValue();
				switch ((byte) entry.getKey().charAt(0)) {
				case T_INT:
					out.writeInt((Integer) v);
					break;
				case T_REAL:
					out.writeFloat((Float) v);
					break;
				case T_BOOL:
					out.writeBoolean((Boolean) v);
					break;
				case T_STRING:
					out.writeUTF((String) v);
					break;
				case T_UNIT: {
					final StoredUnit u = (StoredUnit) v;
					out.writeInt(u.typeId);
					out.writeInt(u.xp);
					out.writeInt(u.skillPoints);
					out.writeInt(u.strength);
					out.writeInt(u.agility);
					out.writeInt(u.intelligence);
					out.writeFloat(u.lifeRatio);
					out.writeFloat(u.manaRatio);
					out.writeInt(u.skills.size());
					for (final int[] s : u.skills) {
						out.writeInt(s[0]);
						out.writeInt(s[1]);
					}
					out.writeInt(u.items.size());
					for (final int[] i : u.items) {
						out.writeInt(i[0]);
						out.writeInt(i[1]);
						out.writeInt(i[2]);
					}
					break;
				}
				default:
					throw new IOException("bad entry " + entry.getKey());
				}
			}
		}
	}

	private static void readCache(final DataInputStream in, final GameCache cache) throws IOException {
		if ((in.readInt() != CACHE_MAGIC) || (in.readInt() != 1)) {
			throw new IOException("not a warsmash-nx game cache");
		}
		final int missionCount = in.readInt();
		for (int m = 0; m < missionCount; m++) {
			final Map<String, Object> mission = new LinkedHashMap<>();
			cache.missions.put(in.readUTF(), mission);
			final int count = in.readInt();
			for (int e = 0; e < count; e++) {
				final String key = in.readUTF();
				final Object v;
				switch ((byte) key.charAt(0)) {
				case T_INT:
					v = in.readInt();
					break;
				case T_REAL:
					v = in.readFloat();
					break;
				case T_BOOL:
					v = in.readBoolean();
					break;
				case T_STRING:
					v = in.readUTF();
					break;
				case T_UNIT: {
					final StoredUnit u = new StoredUnit();
					u.typeId = in.readInt();
					u.xp = in.readInt();
					u.skillPoints = in.readInt();
					u.strength = in.readInt();
					u.agility = in.readInt();
					u.intelligence = in.readInt();
					u.lifeRatio = in.readFloat();
					u.manaRatio = in.readFloat();
					for (int n = in.readInt(); n > 0; n--) {
						u.skills.add(new int[] { in.readInt(), in.readInt() });
					}
					for (int n = in.readInt(); n > 0; n--) {
						u.items.add(new int[] { in.readInt(), in.readInt(), in.readInt() });
					}
					v = u;
					break;
				}
				default:
					throw new IOException("bad entry " + key);
				}
				mission.put(key, v);
			}
		}
	}

	static StoredUnit storeUnit(final CUnit unit) {
		final StoredUnit u = new StoredUnit();
		u.typeId = unit.getTypeId().getValue();
		u.lifeRatio = unit.getMaximumLife() > 0 ? unit.getLife() / unit.getMaximumLife() : 1;
		u.manaRatio = unit.getMaximumMana() > 0 ? unit.getMana() / unit.getMaximumMana() : 1;
		final CAbilityHero hero = unit.getHeroData();
		if (hero != null) {
			u.xp = hero.getXp();
			u.skillPoints = hero.getSkillPoints();
			u.strength = hero.getStrength().getBase();
			u.agility = hero.getAgility().getBase();
			u.intelligence = hero.getIntelligence().getBase();
			for (final War3ID skill : hero.getSkillsAvailable()) {
				final CLevelingAbility ability = unit.getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(skill));
				if ((ability != null) && (ability.getLevel() > 0)) {
					u.skills.add(new int[] { skill.getValue(), ability.getLevel() });
				}
			}
		}
		final CAbilityInventory inventory = unit.getInventoryData();
		if (inventory != null) {
			for (int slot = 0; slot < inventory.getItemCapacity(); slot++) {
				final CItem item = inventory.getItemInSlot(slot);
				if (item != null) {
					u.items.add(new int[] { slot, item.getTypeId().getValue(), item.getCharges() });
				}
			}
		}
		return u;
	}

	static CUnit restoreUnit(final CSimulation simulation, final StoredUnit u, final int playerIndex, final float x,
			final float y, final float facing) {
		final CUnit unit = simulation.createUnitSimple(new War3ID(u.typeId), playerIndex, x, y, facing);
		if (unit == null) {
			return null;
		}
		final CAbilityHero hero = unit.getHeroData();
		if (hero != null) {
			hero.nxSetXpExact(simulation, unit, u.xp);
			for (final int[] skill : u.skills) {
				final War3ID id = new War3ID(skill[0]);
				final CAbilityType<?> type = simulation.getAbilityData().getAbilityType(id);
				if (type == null) {
					System.err.println("warsmash-nx: RestoreUnit: hero skill " + id + " is not implemented, skipped");
					continue;
				}
				CLevelingAbility ability = unit.getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(id));
				if (ability == null) {
					final CAbility created = type.createAbility(simulation.getHandleIdAllocator().createId());
					unit.add(simulation, created);
					ability = unit.getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(id));
				}
				if ((ability != null) && (ability.getLevel() != skill[1])) {
					type.setLevel(simulation, unit, ability, skill[1]);
				}
			}
			hero.setSkillPoints(u.skillPoints);
			hero.setStrengthBase(simulation, unit, u.strength);
			hero.setAgilityBase(simulation, unit, u.agility);
			hero.setIntelligenceBase(simulation, unit, u.intelligence);
		}
		final CAbilityInventory inventory = unit.getInventoryData();
		if (inventory != null) {
			for (final int[] i : u.items) {
				final CItem item = simulation.createItem(new War3ID(i[1]), x, y);
				if (item != null) {
					item.setCharges(i[2]);
					inventory.giveItem(simulation, unit, item, i[0], false);
				}
			}
		}
		unit.setLife(simulation, Math.max(1, unit.getMaximumLife() * u.lifeRatio));
		unit.setMana(unit.getMaximumMana() * u.manaRatio);
		System.err.println("warsmash-nx: RestoreUnit " + new War3ID(u.typeId) + " for player " + playerIndex + ": "
				+ u.skills.size() + " skills, " + u.items.size() + " items, xp " + u.xp);
		return unit;
	}

	// ------------------------------------------------------------------------------------------------
	// Quests (data only: Warsmash has no quest log; what scripts read back is kept)
	// ------------------------------------------------------------------------------------------------

	static final class NxQuest {
		String title = "", description = "", iconPath = "";
		boolean required, completed, discovered, failed, enabled = true;
		final List<NxQuestItem> items = new ArrayList<>();
	}

	static final class NxQuestItem {
		String description = "";
		boolean completed;
	}

	// ------------------------------------------------------------------------------------------------

	private static final class Args {
		final List<JassValue> list;
		final com.etheller.interpreter.ast.scope.TriggerExecutionScope rawScope;

		Args(final List<JassValue> list, final com.etheller.interpreter.ast.scope.TriggerExecutionScope rawScope) {
			this.list = list;
			this.rawScope = rawScope;
		}

		<T> T obj(final int i) {
			final JassValue v = this.list.get(i);
			return v == null ? null : v.visit(ObjectJassValueVisitor.<T>getInstance());
		}

		int i(final int i) {
			return this.list.get(i).visit(IntegerJassValueVisitor.getInstance());
		}

		float f(final int i) {
			return this.list.get(i).visit(RealJassValueVisitor.getInstance()).floatValue();
		}

		boolean b(final int i) {
			return this.list.get(i).visit(BooleanJassValueVisitor.getInstance());
		}

		String s(final int i) {
			final JassValue v = this.list.get(i);
			return v == null ? null : v.visit(StringJassValueVisitor.getInstance());
		}
	}

	private interface Native {
		JassValue call(Args a, CommonTriggerExecutionScope scope);
	}

	private static JassProgram program;
	/** The map's program (register also runs for every AI player's program; see NxAI). */
	private static JassProgram mapProgram;
	private static List<String> defined;

	/**
	 * The order natives Warsmash never had, forwarded to the ones it has: by order string -> ById (via
	 * OrderIdUtils, like IssuePointOrderLoc), the Instant variants drop their extra widget, the Neutral
	 * variants (a neutral unit, e.g. a shop, ordered for a player) drop the player.
	 */
	private static void defineOrderForwards() {
		forward("IssuePointOrder", "IssuePointOrderById", args -> {
			args.set(1, IntegerJassValue.of(orderId(args.get(1))));
			return args;
		});
		forward("IssuePointOrderByIdLoc", "IssuePointOrderById", args -> {
			final AbilityPointTarget loc = args.get(2).visit(ObjectJassValueVisitor.getInstance());
			if (loc == null) {
				return null;
			}
			args.set(2, RealJassValue.of(loc.x));
			args.add(RealJassValue.of(loc.y));
			return args;
		});
		forward("IssueInstantPointOrder", "IssuePointOrderById", args -> {
			args.set(1, IntegerJassValue.of(orderId(args.get(1))));
			return new ArrayList<>(args.subList(0, 4));
		});
		forward("IssueInstantPointOrderById", "IssuePointOrderById", args -> new ArrayList<>(args.subList(0, 4)));
		forward("IssueInstantTargetOrder", "IssueTargetOrderById", args -> {
			args.set(1, IntegerJassValue.of(orderId(args.get(1))));
			return new ArrayList<>(args.subList(0, 3));
		});
		forward("IssueInstantTargetOrderById", "IssueTargetOrderById", args -> new ArrayList<>(args.subList(0, 3)));
		forward("IssueNeutralImmediateOrder", "IssueImmediateOrderById", args -> {
			args.set(2, IntegerJassValue.of(orderId(args.get(2))));
			return new ArrayList<>(args.subList(1, 3));
		});
		forward("IssueNeutralImmediateOrderById", "IssueImmediateOrderById",
				args -> new ArrayList<>(args.subList(1, 3)));
		forward("IssueNeutralPointOrder", "IssuePointOrderById", args -> {
			args.set(2, IntegerJassValue.of(orderId(args.get(2))));
			return new ArrayList<>(args.subList(1, 5));
		});
		forward("IssueNeutralPointOrderById", "IssuePointOrderById", args -> new ArrayList<>(args.subList(1, 5)));
		forward("IssueNeutralTargetOrder", "IssueTargetOrderById", args -> {
			args.set(2, IntegerJassValue.of(orderId(args.get(2))));
			return new ArrayList<>(args.subList(1, 4));
		});
		forward("IssueNeutralTargetOrderById", "IssueTargetOrderById", args -> new ArrayList<>(args.subList(1, 4)));
	}

	private static int orderId(final JassValue orderString) {
		final String s = orderString == null ? null : orderString.visit(StringJassValueVisitor.getInstance());
		return s == null ? 0
				: com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIdUtils.getOrderId(s);
	}

	private interface ArgsRewrite {
		List<JassValue> rewrite(List<JassValue> args);
	}

	/** Registers name as target with rewritten arguments (false when the rewrite gives null). */
	private static void forward(final String name, final String target, final ArgsRewrite rewrite) {
		if (program.getJassNativeManager().hasNative(name)) {
			return;
		}
		final JassFunction targetFunction = program.getJassNativeManager().getUnboundNative(target);
		if (targetFunction == null) {
			System.err.println("warsmash-nx: cannot forward " + name + ": no " + target);
			return;
		}
		program.getJassNativeManager().createNative(name, (arguments, globalScope, triggerScope) -> {
			final List<JassValue> rewritten = rewrite.rewrite(new ArrayList<>(arguments));
			if (rewritten == null) {
				return BooleanJassValue.FALSE;
			}
			return targetFunction.call(rewritten, globalScope, triggerScope);
		});
		defined.add(name);
	}

	/** Registers a native unless Warsmash already implements it. */
	private static void define(final String name, final Native body) {
		if (program.getJassNativeManager().hasNative(name)) {
			return;
		}
		final JassFunction function = (arguments, globalScope, triggerScope) -> body.call(new Args(arguments, triggerScope),
				triggerScope instanceof CommonTriggerExecutionScope ? (CommonTriggerExecutionScope) triggerScope
						: null);
		program.getJassNativeManager().createNative(name, function);
		defined.add(name);
	}

	private static JassValue bool(final boolean b) {
		return BooleanJassValue.of(b);
	}

	public static void register(final JassProgram jassProgram, final War3MapViewer viewer, final WarsmashUI ui) {
		program = jassProgram;
		if (!NxAI.isLoadingAI()) {
			mapProgram = jassProgram;
		}
		defined = new ArrayList<>();
		final GlobalScope globals = jassProgram.getGlobalScope();
		final HandleJassType unitType = globals.getHandleType("unit");
		final HandleJassType itemType = globals.getHandleType("item");
		final HandleJassType locationType = globals.getHandleType("location");
		final HandleJassType gamecacheType = globals.getHandleType("gamecache");
		final HandleJassType questType = globals.getHandleType("quest");
		final HandleJassType questitemType = globals.getHandleType("questitem");
		final HandleJassType gamedifficultyType = globals.getHandleType("gamedifficulty");
		final CSimulation sim = viewer.simulation;

		// ---- game cache
		define("InitGameCache", (a, s) -> new HandleJassValue(gamecacheType, initGameCache(a.s(0))));
		define("SaveGameCache", (a, s) -> {
			final GameCache c = a.obj(0);
			return bool((c != null) && saveGameCache(c));
		});
		// the caches in memory are never older than the files (one process, every save goes through here),
		// so keep them: reloading would only lose data if a save had failed
		define("ReloadGameCachesFromDisk", (a, s) -> bool(true));
		define("StoreInteger", (a, s) -> {
			final GameCache c = a.obj(0);
			if (c != null) {
				c.put(a.s(1), T_INT, a.s(2), a.i(3));
			}
			return null;
		});
		define("StoreReal", (a, s) -> {
			final GameCache c = a.obj(0);
			if (c != null) {
				c.put(a.s(1), T_REAL, a.s(2), a.f(3));
			}
			return null;
		});
		define("StoreBoolean", (a, s) -> {
			final GameCache c = a.obj(0);
			if (c != null) {
				c.put(a.s(1), T_BOOL, a.s(2), a.b(3));
			}
			return null;
		});
		define("StoreString", (a, s) -> {
			final GameCache c = a.obj(0);
			final String v = a.s(3);
			if (c == null) {
				return bool(false);
			}
			c.put(a.s(1), T_STRING, a.s(2), v == null ? "" : v);
			return bool(true);
		});
		define("StoreUnit", (a, s) -> {
			final GameCache c = a.obj(0);
			final CUnit unit = a.obj(3);
			if ((c == null) || (unit == null)) {
				return bool(false);
			}
			c.put(a.s(1), T_UNIT, a.s(2), storeUnit(unit));
			return bool(true);
		});
		define("GetStoredInteger", (a, s) -> {
			final GameCache c = a.obj(0);
			final Object v = c == null ? null : c.get(a.s(1), T_INT, a.s(2));
			return IntegerJassValue.of(v == null ? 0 : (Integer) v);
		});
		define("GetStoredReal", (a, s) -> {
			final GameCache c = a.obj(0);
			final Object v = c == null ? null : c.get(a.s(1), T_REAL, a.s(2));
			return RealJassValue.of(v == null ? 0 : (Float) v);
		});
		define("GetStoredBoolean", (a, s) -> {
			final GameCache c = a.obj(0);
			final Object v = c == null ? null : c.get(a.s(1), T_BOOL, a.s(2));
			return bool((v != null) && (Boolean) v);
		});
		define("GetStoredString", (a, s) -> {
			final GameCache c = a.obj(0);
			final Object v = c == null ? null : c.get(a.s(1), T_STRING, a.s(2));
			return new StringJassValue(v == null ? "" : (String) v);
		});
		define("RestoreUnit", (a, s) -> {
			final GameCache c = a.obj(0);
			final Object v = c == null ? null : c.get(a.s(1), T_UNIT, a.s(2));
			final CPlayerJass player = a.obj(3);
			if ((v == null) || (player == null)) {
				return unitType.getNullValue();
			}
			return new HandleJassValue(unitType,
					restoreUnit(sim, (StoredUnit) v, player.getId(), a.f(4), a.f(5), a.f(6)));
		});
		final byte[] types = { T_INT, T_REAL, T_BOOL, T_UNIT, T_STRING };
		final String[] typeNames = { "Integer", "Real", "Boolean", "Unit", "String" };
		for (int t = 0; t < types.length; t++) {
			final byte type = types[t];
			define("HaveStored" + typeNames[t], (a, s) -> {
				final GameCache c = a.obj(0);
				return bool((c != null) && (c.get(a.s(1), type, a.s(2)) != null));
			});
			define("FlushStored" + typeNames[t], (a, s) -> {
				final GameCache c = a.obj(0);
				if (c != null) {
					c.remove(a.s(1), type, a.s(2));
				}
				return null;
			});
			define("SyncStored" + typeNames[t], (a, s) -> null); // single player: nothing to sync
		}
		define("FlushGameCache", (a, s) -> {
			final GameCache c = a.obj(0);
			if (c != null) {
				c.missions.clear();
			}
			return null;
		});
		define("FlushStoredMission", (a, s) -> {
			final GameCache c = a.obj(0);
			if (c != null) {
				c.missions.remove(norm(a.s(1)));
			}
			return null;
		});

		// ---- levels, saved games, campaign progress
		define("ChangeLevel", (a, s) -> {
			requestLevel(a.s(0));
			return null;
		});
		define("EndGame", (a, s) -> {
			requestLevel("");
			return null;
		});
		define("RestartGame", (a, s) -> {
			requestLevel(currentMap == null ? "" : currentMap);
			return null;
		});
		define("LoadGame", (a, s) -> {
			// never reached through SaveGameExists (always false); end the game rather than hang
			System.err.println("warsmash-nx: LoadGame('" + a.s(0) + "') unsupported, back to the menu");
			requestLevel("");
			return null;
		});
		define("SaveGame", (a, s) -> null);
		define("SaveGameExists", (a, s) -> bool(false));
		define("CopySaveGame", (a, s) -> bool(false));
		define("RenameSaveDirectory", (a, s) -> bool(false));
		define("RemoveSaveDirectory", (a, s) -> bool(true));
		define("SetMissionAvailable", (a, s) -> null);
		define("SetCampaignAvailable", (a, s) -> null);
		define("SetOpCinematicAvailable", (a, s) -> null);
		define("SetEdCinematicAvailable", (a, s) -> null);
		define("SetTutorialCleared", (a, s) -> null);
		define("GetDefaultDifficulty", (a, s) -> new HandleJassValue(gamedifficultyType, CMapDifficulty.NORMAL));

		// ---- heroes
		define("GetHeroLevel", (a, s) -> {
			final CUnit u = a.obj(0);
			return IntegerJassValue.of((u == null) || (u.getHeroData() == null) ? 0 : u.getHeroData().getHeroLevel());
		});
		define("GetHeroXP", (a, s) -> {
			final CUnit u = a.obj(0);
			return IntegerJassValue.of((u == null) || (u.getHeroData() == null) ? 0 : u.getHeroData().getXp());
		});
		define("GetHeroSkillPoints", (a, s) -> {
			final CUnit u = a.obj(0);
			return IntegerJassValue
					.of((u == null) || (u.getHeroData() == null) ? 0 : u.getHeroData().getSkillPoints());
		});
		define("UnitModifySkillPoints", (a, s) -> {
			final CUnit u = a.obj(0);
			if ((u == null) || (u.getHeroData() == null)) {
				return bool(false);
			}
			final CAbilityHero hero = u.getHeroData();
			hero.setSkillPoints(Math.max(0, hero.getSkillPoints() + a.i(1)));
			return bool(true);
		});
		define("GetHeroStr", (a, s) -> {
			final CUnit u = a.obj(0);
			if ((u == null) || (u.getHeroData() == null)) {
				return IntegerJassValue.ZERO;
			}
			return IntegerJassValue.of(a.b(1) ? u.getHeroData().getStrength().getCurrent()
					: u.getHeroData().getStrength().getCurrentBase());
		});
		define("GetHeroAgi", (a, s) -> {
			final CUnit u = a.obj(0);
			if ((u == null) || (u.getHeroData() == null)) {
				return IntegerJassValue.ZERO;
			}
			return IntegerJassValue.of(a.b(1) ? u.getHeroData().getAgility().getCurrent()
					: u.getHeroData().getAgility().getCurrentBase());
		});
		define("GetHeroInt", (a, s) -> {
			final CUnit u = a.obj(0);
			if ((u == null) || (u.getHeroData() == null)) {
				return IntegerJassValue.ZERO;
			}
			return IntegerJassValue.of(a.b(1) ? u.getHeroData().getIntelligence().getCurrent()
					: u.getHeroData().getIntelligence().getCurrentBase());
		});
		define("GetHeroProperName", (a, s) -> {
			final CUnit u = a.obj(0);
			return new StringJassValue((u == null) || (u.getHeroData() == null) ? "" : u.getHeroData().getProperName());
		});
		define("UnitStripHeroLevel", (a, s) -> {
			final CUnit u = a.obj(0);
			if ((u == null) || (u.getHeroData() == null)) {
				return bool(false);
			}
			final int level = Math.max(1, u.getHeroData().getHeroLevel() - a.i(1));
			u.getHeroData().setHeroLevel(sim, u, level, false);
			return bool(true);
		});
		define("SuspendHeroXP", (a, s) -> {
			final CUnit u = a.obj(0);
			if ((u != null) && (u.getHeroData() != null)) {
				u.getHeroData().setXpSuspended(a.b(1));
			}
			return null;
		});
		define("IsSuspendedXP", (a, s) -> {
			final CUnit u = a.obj(0);
			return bool((u != null) && (u.getHeroData() != null) && u.getHeroData().isXpSuspended());
		});
		define("ReviveHero", (a, s) -> {
			final CUnit u = a.obj(0);
			return bool((u != null) && u.nxReviveHero(sim, a.f(1), a.f(2)));
		});
		define("ReviveHeroLoc", (a, s) -> {
			final CUnit u = a.obj(0);
			final AbilityPointTarget loc = a.obj(1);
			return bool((u != null) && (loc != null) && u.nxReviveHero(sim, loc.x, loc.y));
		});
		define("IsHeroUnitId", (a, s) -> {
			final int id = a.i(0);
			return bool(Character.isUpperCase((char) ((id >>> 24) & 0xFF)));
		});

		// ---- event responses (the trigger scope already carries them)
		define("GetLevelingUnit", (a, s) -> new HandleJassValue(unitType, s == null ? null : s.getLevelingUnit()));
		define("GetLearningUnit", (a, s) -> new HandleJassValue(unitType, s == null ? null : s.getLearningUnit()));
		define("GetLearnedSkill", (a, s) -> IntegerJassValue.of(s == null ? 0 : s.getLearnedSkill()));
		define("GetLearnedSkillLevel", (a, s) -> IntegerJassValue.of(s == null ? 0 : s.getLearnedSkillLevel()));
		define("GetRescuer", (a, s) -> new HandleJassValue(unitType, s == null ? null : s.getRescuer()));
		define("GetSummoningUnit", (a, s) -> new HandleJassValue(unitType, s == null ? null : s.getSummoningUnit()));
		define("GetSummonedUnit", (a, s) -> new HandleJassValue(unitType, s == null ? null : s.getSummonedUnit()));
		define("GetSellingUnit", (a, s) -> new HandleJassValue(unitType, s == null ? null : s.getSellingUnit()));
		define("GetSoldUnit", (a, s) -> new HandleJassValue(unitType, s == null ? null : s.getSoldUnit()));
		define("GetBuyingUnit", (a, s) -> new HandleJassValue(unitType, s == null ? null : s.getBuyingUnit()));
		define("GetSoldItem", (a, s) -> new HandleJassValue(itemType, null));

		// ---- units
		define("IsUnitPaused", (a, s) -> {
			final CUnit u = a.obj(0);
			return bool((u != null) && u.isPaused());
		});
		define("IsUnitLoaded", (a, s) -> bool(false));
		define("GetWidgetLife", (a, s) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget w = a.obj(0);
			return RealJassValue.of(w == null ? 0 : w.getLife());
		});
		define("SetWidgetLife", (a, s) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget w = a.obj(0);
			if (w != null) {
				w.setLife(sim, a.f(1));
			}
			return null;
		});
		define("UnitRemoveBuffs", (a, s) -> {
			removeBuffs(sim, a.obj(0));
			return null;
		});
		define("UnitRemoveBuffsEx", (a, s) -> {
			removeBuffs(sim, a.obj(0));
			return null;
		});
		define("UnitResetCooldown", (a, s) -> {
			final CUnit u = a.obj(0);
			if (u != null) {
				u.nxResetCooldowns();
			}
			return null;
		});
		define("UnitApplyTimedLife", (a, s) -> {
			final CUnit u = a.obj(0);
			if (u != null) {
				u.add(sim, new CBuffTimedLife(sim.getHandleIdAllocator().createId(), new War3ID(a.i(1)), a.f(2), false));
			}
			return null;
		});
		define("UnitWakeUp", (a, s) -> null);
		define("UnitSuspendDecay", (a, s) -> null);
		define("UnitShareVision", (a, s) -> null);
		define("UnitAddIndicator", (a, s) -> null);
		define("AddIndicator", (a, s) -> null);
		define("PingMinimap", (a, s) -> null);
		define("PingMinimapEx", (a, s) -> null);

		// ---- selection (local player: single player campaign)
		define("SelectUnit", (a, s) -> {
			ui.nxSelectUnit(a.obj(0), a.b(1));
			return null;
		});
		define("ClearSelection", (a, s) -> {
			ui.nxClearSelection();
			return null;
		});
		define("IsUnitSelected", (a, s) -> bool(ui.nxIsUnitSelected(a.obj(0))));
		define("SyncSelections", (a, s) -> null);
		define("GroupEnumUnitsSelected", (a, s) -> {
			final List<CUnit> group = a.obj(0);
			final TriggerBooleanExpression filter = a.obj(2);
			if (group != null) {
				for (final CUnit unit : ui.nxSelectedUnits()) {
					if ((filter == null) || filter.evaluate(mapProgram.getGlobalScope(),
							CommonTriggerExecutionScope.filterScope(a.rawScope, unit))) {
						group.add(unit);
					}
				}
			}
			return null;
		});

		// ---- shops
		define("AddItemToStock", (a, s) -> {
			addToStock(sim, a.obj(0), a.i(1));
			return null;
		});
		define("AddUnitToStock", (a, s) -> null);
		define("RemoveItemFromStock", (a, s) -> {
			final CUnit u = a.obj(0);
			if (u != null) {
				for (final CAbility ability : u.getAbilities()) {
					if (ability instanceof CAbilitySellItems) {
						((CAbilitySellItems) ability).getItemsSold().remove(new War3ID(a.i(1)));
					}
				}
			}
			return null;
		});
		define("RemoveUnitFromStock", (a, s) -> null);
		define("AddItemToAllStock", (a, s) -> {
			for (final CUnit u : new ArrayList<>(sim.getUnits())) {
				for (final CAbility ability : u.getAbilities()) {
					if (ability instanceof CAbilitySellItems) {
						addToStock(sim, u, a.i(0));
						break;
					}
				}
			}
			return null;
		});
		define("AddUnitToAllStock", (a, s) -> null);
		define("SetItemTypeSlots", (a, s) -> null);
		define("SetUnitTypeSlots", (a, s) -> null);

		// ---- quests (data only)
		define("CreateQuest", (a, s) -> new HandleJassValue(questType, new NxQuest()));
		define("DestroyQuest", (a, s) -> null);
		define("QuestSetTitle", (a, s) -> {
			final NxQuest q = a.obj(0);
			if (q != null) {
				q.title = a.s(1);
			}
			return null;
		});
		define("QuestSetDescription", (a, s) -> {
			final NxQuest q = a.obj(0);
			if (q != null) {
				q.description = a.s(1);
			}
			return null;
		});
		define("QuestSetIconPath", (a, s) -> {
			final NxQuest q = a.obj(0);
			if (q != null) {
				q.iconPath = a.s(1);
			}
			return null;
		});
		define("QuestSetRequired", (a, s) -> {
			final NxQuest q = a.obj(0);
			if (q != null) {
				q.required = a.b(1);
			}
			return null;
		});
		define("QuestSetCompleted", (a, s) -> {
			final NxQuest q = a.obj(0);
			if (q != null) {
				q.completed = a.b(1);
			}
			return null;
		});
		define("QuestSetDiscovered", (a, s) -> {
			final NxQuest q = a.obj(0);
			if (q != null) {
				q.discovered = a.b(1);
			}
			return null;
		});
		define("QuestSetFailed", (a, s) -> {
			final NxQuest q = a.obj(0);
			if (q != null) {
				q.failed = a.b(1);
			}
			return null;
		});
		define("QuestSetEnabled", (a, s) -> {
			final NxQuest q = a.obj(0);
			if (q != null) {
				q.enabled = a.b(1);
			}
			return null;
		});
		define("IsQuestRequired", (a, s) -> {
			final NxQuest q = a.obj(0);
			return bool((q != null) && q.required);
		});
		define("IsQuestCompleted", (a, s) -> {
			final NxQuest q = a.obj(0);
			return bool((q != null) && q.completed);
		});
		define("IsQuestDiscovered", (a, s) -> {
			final NxQuest q = a.obj(0);
			return bool((q != null) && q.discovered);
		});
		define("IsQuestFailed", (a, s) -> {
			final NxQuest q = a.obj(0);
			return bool((q != null) && q.failed);
		});
		define("IsQuestEnabled", (a, s) -> {
			final NxQuest q = a.obj(0);
			return bool((q != null) && q.enabled);
		});
		define("QuestCreateItem", (a, s) -> {
			final NxQuest q = a.obj(0);
			final NxQuestItem item = new NxQuestItem();
			if (q != null) {
				q.items.add(item);
			}
			return new HandleJassValue(questitemType, item);
		});
		define("QuestItemSetDescription", (a, s) -> {
			final NxQuestItem q = a.obj(0);
			if (q != null) {
				q.description = a.s(1);
			}
			return null;
		});
		define("QuestItemSetCompleted", (a, s) -> {
			final NxQuestItem q = a.obj(0);
			if (q != null) {
				q.completed = a.b(1);
			}
			return null;
		});
		define("IsQuestItemCompleted", (a, s) -> {
			final NxQuestItem q = a.obj(0);
			return bool((q != null) && q.completed);
		});
		define("FlashQuestDialogButton", (a, s) -> null);
		define("ForceQuestDialogUpdate", (a, s) -> null);

		// ---- sound, camera, visibility, misc
		define("StopSound", (a, s) -> {
			final CSound sound = a.obj(0);
			if (sound != null) {
				sound.stop();
			}
			return null;
		});
		define("GetCameraTargetPositionLoc", (a, s) -> new HandleJassValue(locationType,
				new LocationJass(ui.getCameraManager().target.x, ui.getCameraManager().target.y,
						sim.getHandleIdAllocator().createId())));
		define("GetCameraEyePositionLoc", (a, s) -> new HandleJassValue(locationType,
				new LocationJass(ui.getCameraManager().camera.location.x, ui.getCameraManager().camera.location.y,
						sim.getHandleIdAllocator().createId())));
		define("IsLocationVisibleToPlayer", (a, s) -> {
			final AbilityPointTarget loc = a.obj(0);
			final CPlayerJass player = a.obj(1);
			if ((loc == null) || (player == null)) {
				return bool(false);
			}
			final CPlayer p = sim.getPlayer(player.getId());
			return bool(p.getFogOfWar().isVisible(sim, sim.getPathingGrid(), loc.x, loc.y));
		});
		// IsVisibleToPlayer / IsFoggedToPlayer / IsMaskedToPlayer (x, y, player): neither engine had them
		for (final String[] fog : new String[][] { { "IsVisibleToPlayer", "VISIBLE" }, { "IsFoggedToPlayer", "FOGGED" },
				{ "IsMaskedToPlayer", "MASKED" } }) {
			final com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState wanted = com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState
					.valueOf(fog[1]);
			define(fog[0], (a, s) -> {
				final CPlayerJass player = a.obj(2);
				if (player == null) {
					return bool(false);
				}
				final CPlayer p = sim.getPlayer(player.getId());
				return bool(p.getFogOfWar().getFogState(sim, sim.getPathingGrid(), a.f(0), a.f(1)) == wanted);
			});
		}
		define("PauseGame", (a, s) -> null);
		define("SetPlayerHandicap", (a, s) -> {
			final CPlayerJass player = a.obj(0);
			if (player != null) {
				sim.getPlayer(player.getId()).setHandicap(a.f(1));
			}
			return null;
		});
		// ---- cinematic filter (fades)
		define("SetCineFilterTexture", (a, s) -> {
			NxCineFilter.setTexture(a.s(0));
			return null;
		});
		define("SetCineFilterBlendMode", (a, s) -> {
			NxCineFilter.setBlendMode(a.obj(0));
			return null;
		});
		define("SetCineFilterTexMapFlags", (a, s) -> null);
		define("SetCineFilterStartUV", (a, s) -> null);
		define("SetCineFilterEndUV", (a, s) -> null);
		define("SetCineFilterStartColor", (a, s) -> {
			NxCineFilter.setStartColor(a.i(0), a.i(1), a.i(2), a.i(3));
			return null;
		});
		define("SetCineFilterEndColor", (a, s) -> {
			NxCineFilter.setEndColor(a.i(0), a.i(1), a.i(2), a.i(3));
			return null;
		});
		define("SetCineFilterDuration", (a, s) -> {
			NxCineFilter.setDuration(a.f(0));
			return null;
		});
		define("DisplayCineFilter", (a, s) -> {
			NxCineFilter.display(a.b(0));
			return null;
		});
		define("IsCineFilterDisplayed", (a, s) -> bool(NxCineFilter.isDisplayed()));
		define("EnableUserUI", (a, s) -> null); // UI clicks during fades; cinematic mode handles control

		// ---- sound and presentation details with no gameplay effect
		define("SetSoundPosition", (a, s) -> {
			final CSound sound = a.obj(0);
			if (sound instanceof com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename) {
				((com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename) sound)
						.setPosition(a.f(1), a.f(2), a.f(3));
			}
			return null;
		});
		for (final String name : new String[] { "RegisterStackedSound", "UnregisterStackedSound", "VolumeGroupSetVolume",
				"VolumeGroupReset", "SetDoodadAnimationRect", "SetSkyModel", "EnableOcclusion", "EnableWorldFogBoundary",
				"SetAllyColorFilterState", "SetRandomSeed", "PauseCompAI", "DisableRestartMission", "SetCampaignMenuRace",
				"SetCampaignMenuRaceEx", "ClearMapMusic", "CameraSetSmoothingFactor", "SetCameraQuickPosition",
				"SetAltMinimapIcon", "UnitSetUsesAltIcon", "DisplayLoadDialog", "AttachSoundToUnit",
				"CameraSetSourceNoise", "CameraSetTargetNoise", "SetTimeOfDayScale", "MultiboardSuppressDisplay" }) {
			define(name, (a, s) -> null);
		}

		// events and natives the campaign maps use that Warsmash lacked (separate class: small rebuilds)
		NxEventNatives.register(jassProgram, viewer, NxEventNatives.trigStrings(viewer));

		// for the PC test scripts (tests/jass, NX_DEBUG "script:" steps), which declare it
		defineOrderForwards();

		define("NxLog", (a, s) -> {
			System.err.println(a.s(0));
			return null;
		});

		System.err.println("warsmash-nx: campaign natives added: " + defined.size());
	}

	private static void removeBuffs(final CSimulation sim, final CUnit unit) {
		if (unit == null) {
			return;
		}
		for (final CAbility ability : new ArrayList<>(unit.getAbilities())) {
			if ((ability instanceof CBuff) && !((CBuff) ability).isTimedLifeBar()) {
				unit.remove(sim, ability);
			}
		}
	}

	private static void addToStock(final CSimulation sim, final CUnit unit, final int itemId) {
		if (unit == null) {
			return;
		}
		final War3ID id = new War3ID(itemId);
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CAbilitySellItems) {
				final List<War3ID> sold = ((CAbilitySellItems) ability).getItemsSold();
				if (!sold.contains(id)) {
					sold.add(id);
				}
				return;
			}
		}
		final List<War3ID> sold = new ArrayList<>();
		sold.add(id);
		unit.add(sim, new CAbilitySellItems(sim.getHandleIdAllocator().createId(), sold));
	}
}
