package com.etheller.warsmash.parsers.jass;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityGoldMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityQueue;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityReviveHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerSleepAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;

/**
 * warsmash-nx: Warcraft III's computer-player AI, for campaign maps.
 * <p>
 * StartCampaignAI(player, "x.ai") gives that player its own JASS program (common.j + common.ai + the
 * script, as Warcraft III does: separate globals and threads per AI player), run on the simulation's
 * clock beside the map's. The script drives the AI through the natives common.ai declares; those are
 * implemented here on top of the simulation:
 * <ul>
 * <li>production: SetProduce trains at a building whose queue offers the unit, or has a peon build a
 * structure at the first legal spot around the town; SetUpgrade researches;</li>
 * <li>two captains (attack, defense) with a home: AddAssault/AddDefenders fill them with free units
 * (not peons, not guard-posted, not already used), AttackMoveXY / SuicidePlayer send the attack captain,
 * idle defenders answer enemies near home, idle units are re-ordered once a second;</li>
 * <li>guard posts: the player's units at AI start hold their spots (ReturnGuardPosts walks them back,
 * FillGuardPosts replaces the dead up to SetReplacementCount);</li>
 * <li>SuicideUnit(Ex): single units sent at a player for good;</li>
 * <li>harvesting (HarvestGold/HarvestWood), hero skills (SetHeroLevels, or the first learnable),
 * CommandAI's command queue, the unit/upgrade queries.</li>
 * </ul>
 * Expansion, zeppelins, creeping and the melee-only target finding are stubs (common.ai's own fallbacks
 * keep the scripts going); every native common.ai declares that is not implemented returns its type's
 * zero. StartMeleeAI is off unless NX_MELEE_AI is set: skirmish needs much more (base building).
 * Kept out of the engine classes on purpose: a change here recompiles one translation unit on the
 * console build.
 */
public final class NxAI {
	private static final float THINK_SECONDS = 1.0f;
	private static final float HOME_RADIUS = 450f;
	private static final float THREAT_RADIUS = 1400f;
	private static final float COMBAT_RADIUS = 650f;

	private static War3MapViewer viewer;
	private static WarsmashUI ui;
	private static DataSource dataSource;
	private static Viewport uiViewport;
	private static Scene uiScene;
	private static CSimulation sim;
	private static final List<Brain> BRAINS = new ArrayList<>();
	@SuppressWarnings("unchecked")
	private static ArrayDeque<int[]>[] commands = new ArrayDeque[0];
	private static Brain loading;

	private NxAI() {
	}

	/** True while an AI player's program is being built (its CommonEnvironment is not the map's). */
	public static boolean isLoadingAI() {
		return loading != null;
	}

	private static void log(final String s) {
		System.err.println("NXAI " + s);
	}

	/** The scripts' own Trace output (common.ai's DisplayText calls), only with NX_AI_TRACE set. */
	private static final boolean TRACE = System.getenv("NX_AI_TRACE") != null;

	private static void trace(final int player, final String s) {
		if (TRACE) {
			log("player " + player + ": " + s.replace("\\n", "").replace("\n", "").trim());
		}
	}

	private static int ticks(final float seconds) {
		return Math.max(1, (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME));
	}

	/**
	 * Called at the end of every CommonEnvironment construction: the map's (start of a map: forget the AI
	 * players of the last one, add the map-side AI natives) or an AI player's (add common.ai's natives).
	 */
	public static void onEnvironment(final JassProgram program, final DataSource source, final Viewport viewport,
			final Scene scene, final War3MapViewer mapViewer, final WarsmashUI meleeUI) {
		if (loading != null) {
			loading.program = program;
			registerAINatives(program, loading);
			return;
		}
		BRAINS.clear();
		viewer = mapViewer;
		ui = meleeUI;
		dataSource = source;
		uiViewport = viewport;
		uiScene = scene;
		sim = mapViewer.simulation;
		commands = newQueues();
		NxUnitAI.reset(sim, mapViewer);
		registerMapNatives(program);
	}

	@SuppressWarnings("unchecked")
	private static ArrayDeque<int[]>[] newQueues() {
		final ArrayDeque<int[]>[] q = new ArrayDeque[WarsmashConstants.MAX_PLAYERS];
		for (int i = 0; i < q.length; i++) {
			q[i] = new ArrayDeque<>();
		}
		return q;
	}

	/** Every simulation step, after the map's threads: the AI players' threads, then their upkeep. */
	public static void tick(final CSimulation simulation) {
		if (simulation != sim) {
			return;
		}
		NxUnitAI.tick(simulation);
		if (BRAINS.isEmpty()) {
			return;
		}
		for (int k = 0; k < BRAINS.size(); k++) {
			final Brain b = BRAINS.get(k);
			if (b.dead || b.paused) {
				continue;
			}
			try {
				b.globals.runThreads();
				final int tick = sim.getGameTurnTick();
				if (tick >= b.nextThink) {
					b.nextThink = tick + ticks(THINK_SECONDS);
					b.think();
				}
			}
			catch (final Throwable t) {
				log("player " + b.player + " (" + b.script + ") stopped: " + t);
				t.printStackTrace();
				b.dead = true;
			}
		}
	}

	static void start(final int playerIndex, final String script) {
		if ((sim == null) || (script == null)) {
			return;
		}
		if (System.getenv("NX_NO_AI") != null) {
			log("NX_NO_AI: not starting " + script + " for player " + playerIndex);
			return;
		}
		for (final Brain b : BRAINS) {
			if ((b.player == playerIndex) && !b.dead) {
				log("player " + playerIndex + " already runs " + b.script + ", ignoring " + script);
				return;
			}
		}
		final Brain b = new Brain(playerIndex, script);
		final String path = ((script.indexOf('\\') >= 0) || (script.indexOf('/') >= 0)) ? script
				: "Scripts\\" + script;
		final long t0 = System.currentTimeMillis();
		loading = b;
		try {
			Jass2.loadCommon(new OctalFixingSource(dataSource), uiViewport, uiScene, viewer, ui, "Scripts\\common.j",
					"Scripts\\common.ai", path);
		}
		catch (final Throwable t) {
			log("player " + playerIndex + ": loading " + path + " failed: " + t);
			t.printStackTrace();
			return;
		}
		finally {
			loading = null;
		}
		if (b.program == null) {
			log("player " + playerIndex + ": no program for " + path);
			return;
		}
		b.globals = b.program.getGlobalScope();
		try {
			final JassThread main = b.globals.createThread("main", Collections.<JassValue>emptyList(),
					TriggerExecutionScope.EMPTY);
			b.globals.queueThread(main);
		}
		catch (final Throwable t) {
			log("player " + playerIndex + ": " + path + " has no main: " + t);
			return;
		}
		b.initGuardPosts();
		b.defaultHomes();
		BRAINS.add(b);
		log("player " + playerIndex + " runs " + path + " (loaded in " + (System.currentTimeMillis() - t0) + " ms, "
				+ b.posts.size() + " guard posts)");
	}

	/**
	 * JASS integer literals with a leading 0 are octal (o02_06x10.ai: PlayerEx(03)); Warsmash's generated
	 * lexer only knows decimal and hex, so the AI scripts are read through this, which rewrites them to
	 * decimal outside strings, rawcodes and comments.
	 */
	static final class OctalFixingSource implements DataSource {
		private final DataSource inner;

		OctalFixingSource(final DataSource inner) {
			this.inner = inner;
		}

		@Override
		public InputStream getResourceAsStream(final String filepath) throws IOException {
			final InputStream in = this.inner.getResourceAsStream(filepath);
			if ((in == null) || !filepath.toLowerCase(Locale.US).endsWith(".ai")) {
				return in;
			}
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			int n;
			try {
				while ((n = in.read(buffer)) > 0) {
					bytes.write(buffer, 0, n);
				}
			}
			finally {
				in.close();
			}
			final String fixed = fixOctal(new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1));
			return new ByteArrayInputStream(fixed.getBytes(StandardCharsets.ISO_8859_1));
		}

		@Override
		public File getFile(final String filepath) throws IOException {
			return this.inner.getFile(filepath);
		}

		@Override
		public File getDirectory(final String filepath) throws IOException {
			return this.inner.getDirectory(filepath);
		}

		@Override
		public ByteBuffer read(final String path) throws IOException {
			return this.inner.read(path);
		}

		@Override
		public boolean has(final String filepath) {
			return this.inner.has(filepath);
		}

		@Override
		public Collection<String> getListfile() {
			return this.inner.getListfile();
		}

		@Override
		public void close() throws IOException {
			// the map's data source: not ours to close
		}
	}

	static String fixOctal(final String src) {
		final StringBuilder out = new StringBuilder(src.length());
		final int len = src.length();
		int i = 0;
		while (i < len) {
			final char c = src.charAt(i);
			if ((c == '/') && ((i + 1) < len) && (src.charAt(i + 1) == '/')) {
				final int end = src.indexOf('\n', i);
				final int stop = end < 0 ? len : end;
				out.append(src, i, stop);
				i = stop;
			}
			else if ((c == '"') || (c == '\'')) {
				int j = i + 1;
				while ((j < len) && (src.charAt(j) != c) && (src.charAt(j) != '\n')) {
					j += src.charAt(j) == '\\' ? 2 : 1;
				}
				j = Math.min(len, j + 1);
				out.append(src, i, j);
				i = j;
			}
			else if (Character.isLetterOrDigit(c) || (c == '_') || (c == '.') || (c == '$')) {
				int j = i;
				while ((j < len) && (Character.isLetterOrDigit(src.charAt(j)) || (src.charAt(j) == '_')
						|| (src.charAt(j) == '.') || (src.charAt(j) == '$'))) {
					j++;
				}
				final String word = src.substring(i, j);
				if ((word.length() > 1) && (word.charAt(0) == '0') && word.matches("0[0-7]+")) {
					out.append(Integer.parseInt(word.substring(1), 8));
				}
				else {
					out.append(word);
				}
				i = j;
			}
			else {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	private static Brain brainOf(final int playerIndex) {
		for (final Brain b : BRAINS) {
			if ((b.player == playerIndex) && !b.dead) {
				return b;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------
	// argument helpers
	// ------------------------------------------------------------------------------------------------

	private interface Body {
		JassValue call(List<JassValue> a, GlobalScope g);
	}

	private static void nat(final JassProgram program, final String name, final Body body) {
		program.getJassNativeManager().createNative(name, (arguments, globalScope, triggerScope) -> body
				.call(arguments, globalScope));
	}

	private static int i(final List<JassValue> a, final int k) {
		return a.get(k).visit(IntegerJassValueVisitor.getInstance());
	}

	private static float f(final List<JassValue> a, final int k) {
		return a.get(k).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	private static boolean b(final List<JassValue> a, final int k) {
		return a.get(k).visit(BooleanJassValueVisitor.getInstance());
	}

	private static String s(final List<JassValue> a, final int k) {
		final JassValue v = a.get(k);
		return v == null ? null : v.visit(StringJassValueVisitor.getInstance());
	}

	private static <T> T o(final List<JassValue> a, final int k) {
		final JassValue v = a.get(k);
		return v == null ? null : v.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static int playerIndexOf(final Object player) {
		return player instanceof CPlayer ? ((CPlayer) player).getId() : -1;
	}

	private static JassValue bool(final boolean v) {
		return BooleanJassValue.of(v);
	}

	private static JassValue integer(final int v) {
		return IntegerJassValue.of(v);
	}

	// ------------------------------------------------------------------------------------------------
	// map-side natives
	// ------------------------------------------------------------------------------------------------

	private static void registerMapNatives(final JassProgram program) {
		nat(program, "StartCampaignAI", (a, g) -> {
			start(playerIndexOf(o(a, 0)), s(a, 1));
			return null;
		});
		nat(program, "StartMeleeAI", (a, g) -> {
			if (System.getenv("NX_MELEE_AI") != null) {
				start(playerIndexOf(o(a, 0)), s(a, 1));
			}
			else {
				log("StartMeleeAI(" + playerIndexOf(o(a, 0)) + ", " + s(a, 1) + ") ignored (melee AI not supported)");
			}
			return null;
		});
		nat(program, "CommandAI", (a, g) -> {
			final int p = playerIndexOf(o(a, 0));
			if ((p >= 0) && (p < commands.length)) {
				commands[p].addLast(new int[] { i(a, 1), i(a, 2) });
			}
			return null;
		});
		nat(program, "PauseCompAI", (a, g) -> {
			final Brain b = brainOf(playerIndexOf(o(a, 0)));
			if (b != null) {
				b.paused = b(a, 1);
			}
			return null;
		});
		nat(program, "RemoveGuardPosition", (a, g) -> {
			final CUnit u = o(a, 0);
			if (u != null) {
				NxUnitAI.forgetHome(u);
				final Brain b = brainOf(u.getPlayerIndex());
				if (b != null) {
					b.removePost(u, false);
				}
			}
			return null;
		});
		nat(program, "RecycleGuardPosition", (a, g) -> {
			final CUnit u = o(a, 0);
			if (u != null) {
				final Brain b = brainOf(u.getPlayerIndex());
				if (b != null) {
					b.removePost(u, true);
				}
			}
			return null;
		});
		nat(program, "RemoveAllGuardPositions", (a, g) -> {
			final Brain b = brainOf(playerIndexOf(o(a, 0)));
			if (b != null) {
				b.posts.clear();
			}
			return null;
		});
	}

	// ------------------------------------------------------------------------------------------------
	// common.ai natives
	// ------------------------------------------------------------------------------------------------

	private static void registerAINatives(final JassProgram program, final Brain ai) {
		final GlobalScope globals = program.getGlobalScope();
		final HandleJassType unitType = globals.getHandleType("unit");

		// ---- threads
		nat(program, "Sleep", (a, g) -> {
			final float seconds = f(a, 0);
			final JassThread thread = g.getCurrentThread();
			if (thread != null) {
				thread.setSleeping(true);
				final CTimerSleepAction timer = new CTimerSleepAction(thread);
				timer.setRepeats(false);
				timer.setTimeoutTime(Math.max(seconds, WarsmashConstants.SIMULATION_STEP_TIME));
				timer.start(sim);
			}
			return null;
		});
		nat(program, "StartThread", (a, g) -> {
			final CodeJassValue code = a.get(0).visit(CodeJassValueVisitor.getInstance());
			if (code != null) {
				g.queueThread(g.createThread(code));
			}
			return null;
		});

		// ---- identity and debug output
		nat(program, "GetAiPlayer", (a, g) -> integer(ai.player));
		nat(program, "DoAiScriptDebug", (a, g) -> bool(false));
		nat(program, "DebugS", (a, g) -> null);
		nat(program, "DebugFI", (a, g) -> null);
		nat(program, "DebugUnitID", (a, g) -> null);
		nat(program, "DisplayText", (a, g) -> {
			trace(ai.player, s(a, 1));
			return null;
		});
		nat(program, "DisplayTextI", (a, g) -> {
			trace(ai.player, s(a, 1).replace("%d", Integer.toString(i(a, 2))));
			return null;
		});
		nat(program, "DisplayTextII", (a, g) -> null);
		nat(program, "DisplayTextIII", (a, g) -> null);
		nat(program, "MeleeDifficulty", (a, g) -> integer(1));

		// ---- commands from the map (CommandAI)
		nat(program, "CommandsWaiting", (a, g) -> integer(ai.queue().size()));
		nat(program, "GetLastCommand", (a, g) -> {
			final int[] c = ai.queue().peekFirst();
			return integer(c == null ? 0 : c[0]);
		});
		nat(program, "GetLastData", (a, g) -> {
			final int[] c = ai.queue().peekFirst();
			return integer(c == null ? 0 : c[1]);
		});
		nat(program, "PopLastCommand", (a, g) -> {
			ai.queue().pollFirst();
			return null;
		});

		// ---- queries
		nat(program, "GetUnitCount", (a, g) -> integer(ai.count(ai.player, i(a, 0), false)));
		nat(program, "GetUnitCountDone", (a, g) -> integer(ai.count(ai.player, i(a, 0), true)));
		nat(program, "GetTownUnitCount", (a, g) -> integer(ai.count(ai.player, i(a, 0), b(a, 2))));
		nat(program, "GetPlayerUnitTypeCount",
				(a, g) -> integer(ai.count(playerIndexOf(o(a, 0)), i(a, 1), false)));
		nat(program, "GetUnitGoldCost", (a, g) -> {
			final CUnitType t = unitType(i(a, 0));
			return integer(t == null ? 0 : t.getGoldCost());
		});
		nat(program, "GetUnitWoodCost", (a, g) -> {
			final CUnitType t = unitType(i(a, 0));
			return integer(t == null ? 0 : t.getLumberCost());
		});
		nat(program, "GetUnitBuildTime", (a, g) -> {
			final CUnitType t = unitType(i(a, 0));
			return integer(t == null ? 0 : t.getBuildTime());
		});
		nat(program, "GetUpgradeLevel", (a, g) -> integer(ai.upgradeLevel(i(a, 0))));
		nat(program, "GetUpgradeGoldCost", (a, g) -> {
			final CUpgradeType t = sim.getUpgradeData().getType(new War3ID(i(a, 0)));
			return integer(t == null ? 0 : t.getGoldCost(ai.upgradeLevel(i(a, 0))));
		});
		nat(program, "GetUpgradeWoodCost", (a, g) -> {
			final CUpgradeType t = sim.getUpgradeData().getType(new War3ID(i(a, 0)));
			return integer(t == null ? 0 : t.getLumberCost(ai.upgradeLevel(i(a, 0))));
		});
		nat(program, "GetHeroId", (a, g) -> integer(ai.currentHeroId));
		nat(program, "GetHeroLevelAI", (a, g) -> integer(ai.currentHeroLevel));
		nat(program, "UnitAlive", (a, g) -> {
			final CUnit u = o(a, 0);
			return bool((u != null) && !u.isDead());
		});
		nat(program, "UnitInvis", (a, g) -> bool(false));
		nat(program, "IgnoredUnits", (a, g) -> integer(0));
		nat(program, "TownThreatened", (a, g) -> bool(ai.nearestEnemy(ai.defense.homeX, ai.defense.homeY,
				THREAT_RADIUS) != null));
		nat(program, "TownHasHall", (a, g) -> bool((i(a, 0) == 0) && (ai.townCenter() != null)));
		nat(program, "TownHasMine", (a, g) -> bool((i(a, 0) == 0) && (ai.mineNear(ai.townCenter()) != null)));
		nat(program, "TownWithMine", (a, g) -> integer(0));
		nat(program, "GetMinesOwned", (a, g) -> integer(ai.mineNear(ai.townCenter()) == null ? 0 : 1));
		nat(program, "GetGoldOwned", (a, g) -> integer(0));
		nat(program, "GetEnemyPower", (a, g) -> integer(0));
		nat(program, "CreepsOnMap", (a, g) -> bool(false));
		nat(program, "IsTowered", (a, g) -> bool(false));
		nat(program, "GetBuilding", (a, g) -> {
			final CUnit u = ai.findTarget(playerIndexOf(o(a, 0)), ai.defense.homeX, ai.defense.homeY, false);
			return u == null ? unitType.getNullValue() : new HandleJassValue(unitType, u);
		});
		nat(program, "GetEnemyBase", (a, g) -> {
			final CUnit u = ai.nearestEnemyStructure();
			return u == null ? unitType.getNullValue() : new HandleJassValue(unitType, u);
		});
		nat(program, "WaitGetEnemyBase", (a, g) -> bool(true));
		nat(program, "StartGetEnemyBase", (a, g) -> null);

		// ---- production
		nat(program, "SetProduce", (a, g) -> bool(ai.produce(i(a, 0), i(a, 1))));
		nat(program, "SetUpgrade", (a, g) -> bool(ai.research(i(a, 0))));
		nat(program, "SetHeroLevels", (a, g) -> {
			ai.heroLevels = a.get(0).visit(CodeJassValueVisitor.getInstance());
			return null;
		});
		nat(program, "SetReplacementCount", (a, g) -> {
			ai.replacements = i(a, 0);
			return null;
		});
		nat(program, "MergeUnits", (a, g) -> bool(false));
		nat(program, "ConvertUnits", (a, g) -> bool(false));

		// ---- harvesting
		nat(program, "ClearHarvestAI", (a, g) -> {
			ai.wantGold = 0;
			ai.wantWood = 0;
			return null;
		});
		nat(program, "HarvestGold", (a, g) -> {
			ai.wantGold += Math.max(0, i(a, 1));
			return null;
		});
		nat(program, "HarvestWood", (a, g) -> {
			ai.wantWood += Math.max(0, i(a, 1));
			return null;
		});
		nat(program, "StopGathering", (a, g) -> {
			ai.wantGold = 0;
			ai.wantWood = 0;
			return null;
		});

		// ---- guard posts
		nat(program, "AddGuardPost", (a, g) -> {
			ai.posts.add(new Post(i(a, 0), f(a, 1), f(a, 2), null));
			return null;
		});
		nat(program, "FillGuardPosts", (a, g) -> {
			ai.fillGuardPosts();
			return null;
		});
		nat(program, "ReturnGuardPosts", (a, g) -> {
			ai.returnGuardPosts();
			return null;
		});

		// ---- captains
		nat(program, "CreateCaptains", (a, g) -> {
			ai.defaultHomes();
			return null;
		});
		nat(program, "SetCaptainHome", (a, g) -> {
			final int which = i(a, 0);
			if ((which & 1) != 0) {
				ai.attack.setHome(f(a, 1), f(a, 2));
			}
			if ((which & 2) != 0) {
				ai.defense.setHome(f(a, 1), f(a, 2));
			}
			return null;
		});
		nat(program, "ResetCaptainLocs", (a, g) -> {
			ai.attack.homeSet = false;
			ai.defense.homeSet = false;
			ai.defaultHomes();
			return null;
		});
		nat(program, "InitAssault", (a, g) -> {
			ai.initAssault();
			return null;
		});
		nat(program, "AddAssault", (a, g) -> bool(ai.addTo(ai.attack, i(a, 0), i(a, 1))));
		nat(program, "AddDefenders", (a, g) -> bool(ai.addTo(ai.defense, i(a, 0), i(a, 1))));
		nat(program, "AttackMoveXY", (a, g) -> {
			ai.attackMoveXY(i(a, 0), i(a, 1));
			return null;
		});
		nat(program, "CaptainAttack", (a, g) -> {
			ai.attackMoveXY(f(a, 0), f(a, 1));
			return null;
		});
		nat(program, "AttackMoveKill", (a, g) -> {
			final CUnit target = o(a, 0);
			if (target != null) {
				ai.attackMoveXY(target.getX(), target.getY());
			}
			return null;
		});
		nat(program, "SuicidePlayer", (a, g) -> bool(ai.suicidePlayer(playerIndexOf(o(a, 0)), b(a, 1), false)));
		nat(program, "SuicidePlayerUnits",
				(a, g) -> bool(ai.suicidePlayer(playerIndexOf(o(a, 0)), b(a, 1), true)));
		nat(program, "CaptainVsPlayer", (a, g) -> {
			ai.suicidePlayer(playerIndexOf(o(a, 0)), false, false);
			return null;
		});
		nat(program, "CaptainVsUnits", (a, g) -> {
			ai.suicidePlayer(playerIndexOf(o(a, 0)), false, true);
			return null;
		});
		nat(program, "CaptainGoHome", (a, g) -> {
			ai.attack.clearTargets();
			for (final CUnit u : ai.attack.units) {
				ai.attackMove(u, ai.attack.homeX, ai.attack.homeY);
			}
			return null;
		});
		nat(program, "ClearCaptainTargets", (a, g) -> {
			ai.attack.clearTargets();
			return null;
		});
		nat(program, "CaptainInCombat", (a, g) -> bool(ai.inCombat(b(a, 0) ? ai.attack : ai.defense)));
		nat(program, "CaptainIsHome", (a, g) -> {
			final float[] c = ai.attack.center();
			return bool((c != null) && (dist(c[0], c[1], ai.attack.homeX, ai.attack.homeY) < 700));
		});
		nat(program, "CaptainIsFull", (a, g) -> bool(ai.attack.isFull()));
		nat(program, "CaptainIsEmpty", (a, g) -> {
			ai.attack.prune(ai.player);
			return bool(ai.attack.units.isEmpty());
		});
		nat(program, "CaptainGroupSize", (a, g) -> {
			ai.attack.prune(ai.player);
			return integer(ai.attack.units.size());
		});
		nat(program, "CaptainReadiness", (a, g) -> integer(ai.attack.readiness(false)));
		nat(program, "CaptainReadinessHP", (a, g) -> integer(ai.attack.readiness(false)));
		nat(program, "CaptainReadinessMa", (a, g) -> integer(ai.attack.readiness(true)));
		nat(program, "CaptainRetreating", (a, g) -> bool(false));
		nat(program, "CaptainAtGoal", (a, g) -> {
			final float[] c = ai.attack.center();
			return bool(ai.attack.hasGoal && (c != null)
					&& (dist(c[0], c[1], ai.attack.goalX, ai.attack.goalY) < 500));
		});

		// ---- single-unit suicide attacks
		nat(program, "SuicideUnit", (a, g) -> {
			ai.suicideUnits(i(a, 0), i(a, 1), -1);
			return null;
		});
		nat(program, "SuicideUnitEx", (a, g) -> {
			ai.suicideUnits(i(a, 0), i(a, 1), i(a, 2));
			return null;
		});

		// every other native common.ai declares (expansion, zeppelins, creeps, flags...): its type's zero
		stubRemaining(program, globals);
	}

	private static final Pattern NATIVE = Pattern
			.compile("^\\s*(?:constant\\s+)?native\\s+(\\w+)\\s+takes\\s+.*?\\s+returns\\s+(\\w+)");

	private static void stubRemaining(final JassProgram program, final GlobalScope globals) {
		int stubs = 0;
		try (InputStream in = dataSource.getResourceAsStream("Scripts\\common.ai")) {
			if (in == null) {
				return;
			}
			final BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
			String line;
			while ((line = reader.readLine()) != null) {
				final Matcher m = NATIVE.matcher(line);
				if (!m.find() || program.getJassNativeManager().hasNative(m.group(1))) {
					continue;
				}
				final String type = m.group(2).toLowerCase(Locale.US);
				final JassValue zero;
				if ("nothing".equals(type)) {
					zero = null;
				}
				else if ("integer".equals(type)) {
					zero = IntegerJassValue.ZERO;
				}
				else if ("boolean".equals(type)) {
					zero = BooleanJassValue.FALSE;
				}
				else if ("real".equals(type)) {
					zero = new RealJassValue(0);
				}
				else {
					final HandleJassType handleType = globals.getHandleType(type);
					zero = handleType == null ? null : handleType.getNullValue();
				}
				nat(program, m.group(1), (a, g) -> zero);
				stubs++;
			}
		}
		catch (final Exception e) {
			log("reading common.ai natives: " + e);
		}
		if (stubs > 0) {
			log(stubs + " common.ai natives stubbed");
		}
	}

	// ------------------------------------------------------------------------------------------------
	// helpers over the simulation
	// ------------------------------------------------------------------------------------------------

	private static CUnitType unitType(final int id) {
		return sim.getUnitData().getUnitType(new War3ID(id));
	}

	static float dist(final float x1, final float y1, final float x2, final float y2) {
		final float dx = x1 - x2;
		final float dy = y1 - y2;
		return (float) StrictMath.sqrt((dx * dx) + (dy * dy));
	}

	static boolean idle(final CUnit u) {
		final CBehavior behavior = u.getCurrentBehavior();
		return (behavior == null) || (behavior.getHighlightOrderId() == OrderIds.stop);
	}

	static boolean usable(final CUnit u) {
		return !u.isDead() && !u.isHidden() && !u.isConstructing() && !u.isBuilding();
	}

	/** Whether one of u's attacks can hit t. */
	static boolean canAttack(final CUnit u, final CUnit t) {
		final List<CUnitAttack> attacks = u.getCurrentAttacks();
		if ((attacks == null) || attacks.isEmpty()) {
			return false;
		}
		for (final CUnitAttack attack : attacks) {
			if (t.canBeTargetedBy(sim, u, attack.getTargetsAllowed())) {
				return true;
			}
		}
		return false;
	}

	static boolean isPeon(final CUnit u) {
		return u.getClassifications().contains(CUnitClassification.PEON);
	}

	/** An order through the unit's abilities, as IssuePointOrderById does. */
	static boolean pointOrder(final CUnit u, final int orderId, final float x, final float y) {
		final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE;
		AbilityPointTarget target = new AbilityPointTarget(x, y);
		int abilityHandleId = 0;
		for (final CAbility ability : u.getAbilities()) {
			ability.checkCanUse(sim, u, orderId, activation);
			if (activation.isOk()) {
				final PointAbilityTargetCheckReceiver receiver = PointAbilityTargetCheckReceiver.INSTANCE;
				ability.checkCanTarget(sim, u, orderId, target, receiver.reset());
				if (receiver.getTarget() != null) {
					target = receiver.getTarget();
					abilityHandleId = ability.getHandleId();
					break;
				}
			}
		}
		if (abilityHandleId == 0) {
			return false;
		}
		sim.getDefaultPlayerUnitOrderExecutor(u.getPlayerIndex()).issuePointOrder(u.getHandleId(), abilityHandleId,
				orderId, target.x, target.y, false);
		return true;
	}

	/** A widget-target order through the unit's abilities, as IssueTargetOrderById does. */
	static boolean targetOrder(final CUnit u, final int orderId, final CWidget widget) {
		final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE;
		for (final CAbility ability : u.getAbilities()) {
			ability.checkCanUse(sim, u, orderId, activation);
			if (activation.isOk()) {
				final CWidgetAbilityTargetCheckReceiver receiver = CWidgetAbilityTargetCheckReceiver.INSTANCE;
				ability.checkCanTarget(sim, u, orderId, widget, receiver.reset());
				if (receiver.getTarget() != null) {
					sim.getDefaultPlayerUnitOrderExecutor(u.getPlayerIndex()).issueTargetOrder(u.getHandleId(),
							ability.getHandleId(), orderId, receiver.getTarget().getHandleId(), false);
					return true;
				}
			}
		}
		return false;
	}

	/** A no-target order with one given ability (training/research with the building's queue). */
	static boolean immediateOrder(final CUnit u, final CAbility ability, final int orderId) {
		final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE;
		ability.checkCanUse(sim, u, orderId, activation);
		if (!activation.isOk()) {
			return false;
		}
		final BooleanAbilityTargetCheckReceiver<Void> receiver = BooleanAbilityTargetCheckReceiver.<Void>getInstance();
		ability.checkCanTargetNoTarget(sim, u, orderId, receiver.reset());
		if (!receiver.isTargetable()) {
			return false;
		}
		final CPlayerUnitOrderExecutor executor = sim.getDefaultPlayerUnitOrderExecutor(u.getPlayerIndex());
		executor.issueImmediateOrder(u.getHandleId(), ability.getHandleId(), orderId, false);
		return true;
	}

	static int queueLength(final CUnit u) {
		int n = 0;
		for (final War3ID id : u.getBuildQueue()) {
			if (id != null) {
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------------------------------------------
	// state
	// ------------------------------------------------------------------------------------------------

	static final class Post {
		final int typeId;
		final float x;
		final float y;
		CUnit unit;

		Post(final int typeId, final float x, final float y, final CUnit unit) {
			this.typeId = typeId;
			this.x = x;
			this.y = y;
			this.unit = unit;
		}
	}

	static final class Captain {
		final List<CUnit> units = new ArrayList<>();
		final Map<Integer, Integer> desire = new HashMap<>();
		float homeX;
		float homeY;
		boolean homeSet;
		float goalX;
		float goalY;
		boolean hasGoal;
		int targetPlayer = -1;
		boolean unitsFirst;

		void setHome(final float x, final float y) {
			this.homeX = x;
			this.homeY = y;
			this.homeSet = true;
		}

		void clearTargets() {
			this.hasGoal = false;
			this.targetPlayer = -1;
		}

		boolean launched() {
			return this.hasGoal || (this.targetPlayer >= 0);
		}

		void prune(final int player) {
			for (int k = this.units.size() - 1; k >= 0; k--) {
				final CUnit u = this.units.get(k);
				if (u.isDead() || (u.getPlayerIndex() != player)) {
					this.units.remove(k);
				}
			}
		}

		int countOf(final int typeId) {
			int n = 0;
			for (final CUnit u : this.units) {
				if (u.getTypeId().getValue() == typeId) {
					n++;
				}
			}
			return n;
		}

		boolean isFull() {
			for (final Map.Entry<Integer, Integer> e : this.desire.entrySet()) {
				if (countOf(e.getKey()) < e.getValue()) {
					return false;
				}
			}
			return true;
		}

		int readiness(final boolean mana) {
			float sum = 0;
			int n = 0;
			for (final CUnit u : this.units) {
				if (u.isDead()) {
					continue;
				}
				if (mana) {
					if (u.getMaximumMana() > 0) {
						sum += u.getMana() / u.getMaximumMana();
						n++;
					}
				}
				else if (u.getMaximumLife() > 0) {
					sum += u.getLife() / u.getMaximumLife();
					n++;
				}
			}
			return n == 0 ? 100 : (int) ((sum * 100) / n);
		}

		float[] center() {
			float x = 0;
			float y = 0;
			int n = 0;
			for (final CUnit u : this.units) {
				if (!u.isDead()) {
					x += u.getX();
					y += u.getY();
					n++;
				}
			}
			return n == 0 ? null : new float[] { x / n, y / n };
		}
	}

	static final class Brain {
		final int player;
		final String script;
		JassProgram program;
		GlobalScope globals;
		boolean dead;
		boolean paused;
		int nextThink;
		int thinks;

		final Captain attack = new Captain();
		final Captain defense = new Captain();
		final List<Post> posts = new ArrayList<>();
		final Map<CUnit, Integer> suicide = new HashMap<>();
		final Map<Integer, int[]> pendingBuild = new HashMap<>();
		int replacements;
		int wantGold;
		int wantWood;
		CodeJassValue heroLevels;
		int currentHeroId;
		int currentHeroLevel;

		Brain(final int player, final String script) {
			this.player = player;
			this.script = script;
		}

		ArrayDeque<int[]> queue() {
			return ((this.player >= 0) && (this.player < commands.length)) ? commands[this.player]
					: new ArrayDeque<int[]>();
		}

		CPlayer me() {
			return sim.getPlayer(this.player);
		}

		boolean isEnemy(final CUnit u) {
			final int owner = u.getPlayerIndex();
			return !u.isDead() && !u.isHidden() && (owner != this.player) && !u.isInvulnerable()
					&& !me().hasAlliance(owner, CAllianceType.PASSIVE);
		}

		boolean isFree(final CUnit u) {
			if ((u.getPlayerIndex() != this.player) || !usable(u) || this.attack.units.contains(u)
					|| this.defense.units.contains(u) || this.suicide.containsKey(u)) {
				return false;
			}
			for (final Post p : this.posts) {
				if (p.unit == u) {
					return false;
				}
			}
			return true;
		}

		// ---- counts

		/** Units of a type (and, unless onlyDone, those queued or ordered built), guards included. */
		int count(final int owner, final int typeId, final boolean onlyDone) {
			int n = 0;
			for (final CUnit u : sim.getUnits()) {
				if (u.getPlayerIndex() != owner) {
					continue;
				}
				if (u.getTypeId().getValue() == typeId) {
					if (onlyDone) {
						if (!u.isDead() && !u.isConstructing()) {
							n++;
						}
					}
					else if (!u.isDead() || (u.getHeroData() != null)) {
						// a dead hero still exists (Warcraft III revives, never retrains, a hero)
						n++;
					}
				}
				if (!onlyDone && !u.isDead()) {
					final War3ID[] queue = u.getBuildQueue();
					final CUnit.QueueItemType[] types = u.getBuildQueueTypes();
					for (int k = 0; k < queue.length; k++) {
						if ((queue[k] != null) && (types[k] == CUnit.QueueItemType.UNIT)
								&& (queue[k].getValue() == typeId)) {
							n++;
						}
					}
				}
			}
			if (!onlyDone && (owner == this.player)) {
				final int[] pending = this.pendingBuild.get(typeId);
				if (pending != null) {
					if ((sim.getGameTurnTick() < pending[0]) && (n <= pending[1])) {
						n++;
					}
					else {
						this.pendingBuild.remove(typeId);
					}
				}
			}
			return n;
		}

		int upgradeLevel(final int id) {
			return me().getTechtreeUnlocked(new War3ID(id));
		}

		// ---- production

		boolean produce(final int qty, final int typeId) {
			final CUnitType type = unitType(typeId);
			if ((type == null) || (qty <= 0)) {
				return false;
			}
			if (type.isBuilding()) {
				return construct(typeId);
			}
			final War3ID id = new War3ID(typeId);
			int issued = 0;
			int trainers = 0;
			for (final CUnit u : new ArrayList<>(sim.getUnits())) {
				if (issued >= qty) {
					break;
				}
				if ((u.getPlayerIndex() != this.player) || u.isDead() || u.isConstructing() || !u.isBuilding()) {
					continue;
				}
				final CAbilityQueue queueAbility = u.getFirstAbilityOfType(CAbilityQueue.class);
				if ((queueAbility == null) || !queueAbility.getUnitsTrained().contains(id)) {
					continue;
				}
				trainers++;
				int queued = queueLength(u);
				while ((issued < qty) && (queued < 2)) {
					if (!immediateOrder(u, queueAbility, typeId) || (queueLength(u) == queued)) {
						break;
					}
					issued++;
					queued++;
				}
			}
			trace(this.player, "SetProduce " + qty + " " + id + ": " + issued + " queued at " + trainers
					+ " trainer(s), gold " + me().getGold() + ", food " + me().getFoodUsed() + "/" + me().getFoodCap());
			return issued > 0;
		}

		boolean construct(final int typeId) {
			final int[] pending = this.pendingBuild.get(typeId);
			if ((pending != null) && (sim.getGameTurnTick() < pending[0])) {
				return true;
			}
			CUnit builder = null;
			AbstractCAbilityBuild build = null;
			final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE;
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() != this.player) || !usable(u) || !isPeon(u)
						|| this.attack.units.contains(u) || this.defense.units.contains(u)) {
					continue;
				}
				for (final CAbility ability : u.getAbilities()) {
					if (ability instanceof AbstractCAbilityBuild) {
						ability.checkCanUse(sim, u, typeId, activation);
						if (activation.isOk()) {
							if ((builder == null) || (idle(u) && !idle(builder))) {
								builder = u;
								build = (AbstractCAbilityBuild) ability;
							}
						}
					}
				}
			}
			if (builder == null) {
				return false;
			}
			final CUnit hall = townCenter();
			final float cx = hall != null ? hall.getX() : this.defense.homeX;
			final float cy = hall != null ? hall.getY() : this.defense.homeY;
			final float start = sim.getSeededRandom().nextFloat() * 6.2831855f;
			for (int ring = 1; ring <= 14; ring++) {
				final float radius = 256 + (ring * 128);
				final int steps = 8 + (ring * 3);
				for (int k = 0; k < steps; k++) {
					final double angle = start + ((k * 6.283185307179586) / steps);
					final float x = cx + (float) (StrictMath.cos(angle) * radius);
					final float y = cy + (float) (StrictMath.sin(angle) * radius);
					final PointAbilityTargetCheckReceiver receiver = PointAbilityTargetCheckReceiver.INSTANCE;
					build.checkCanTarget(sim, builder, typeId, new AbilityPointTarget(x, y), receiver.reset());
					final AbilityPointTarget spot = receiver.getTarget();
					if (spot != null) {
						sim.getDefaultPlayerUnitOrderExecutor(this.player).issuePointOrder(builder.getHandleId(),
								build.getHandleId(), typeId, spot.x, spot.y, false);
						this.pendingBuild.put(typeId,
								new int[] { sim.getGameTurnTick() + ticks(30), count(this.player, typeId, false) });
						return true;
					}
				}
			}
			return false;
		}

		boolean research(final int upgradeId) {
			final War3ID id = new War3ID(upgradeId);
			for (final CUnit u : new ArrayList<>(sim.getUnits())) {
				if ((u.getPlayerIndex() != this.player) || u.isDead() || u.isConstructing() || !u.isBuilding()) {
					continue;
				}
				final CAbilityQueue queueAbility = u.getFirstAbilityOfType(CAbilityQueue.class);
				if ((queueAbility == null) || !queueAbility.getResearchesAvailable().contains(id)) {
					continue;
				}
				final War3ID[] queue = u.getBuildQueue();
				for (int k = 0; k < queue.length; k++) {
					if ((queue[k] != null) && (queue[k].getValue() == upgradeId)) {
						return true;
					}
				}
				if (immediateOrder(u, queueAbility, upgradeId)) {
					return true;
				}
			}
			return false;
		}

		// ---- places

		CUnit townCenter() {
			CUnit best = null;
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() == this.player) && !u.isDead() && u.isBuilding()
						&& u.getClassifications().contains(CUnitClassification.TOWNHALL)) {
					if ((best == null) || (dist(u.getX(), u.getY(), this.defense.homeX,
							this.defense.homeY) < dist(best.getX(), best.getY(), this.defense.homeX, this.defense.homeY))) {
						best = u;
					}
				}
			}
			return best;
		}

		CUnit mineNear(final CUnit hall) {
			if (hall == null) {
				return null;
			}
			CUnit best = null;
			float bestDistance = 2000;
			for (final CUnit u : sim.getUnits()) {
				if (!u.isDead() && (u.getFirstAbilityOfType(CAbilityGoldMine.class) != null)) {
					final float d = dist(u.getX(), u.getY(), hall.getX(), hall.getY());
					if (d < bestDistance) {
						best = u;
						bestDistance = d;
					}
				}
			}
			return best;
		}

		void defaultHomes() {
			float x;
			float y;
			final CUnit hall = townCenter();
			if (hall != null) {
				x = hall.getX();
				y = hall.getY();
			}
			else {
				// the middle of the player's buildings, else of its units, else its start location
				float sx = 0;
				float sy = 0;
				int n = 0;
				float ux = 0;
				float uy = 0;
				int un = 0;
				for (final CUnit u : sim.getUnits()) {
					if ((u.getPlayerIndex() == this.player) && !u.isDead()) {
						if (u.isBuilding()) {
							sx += u.getX();
							sy += u.getY();
							n++;
						}
						else {
							ux += u.getX();
							uy += u.getY();
							un++;
						}
					}
				}
				if (n > 0) {
					x = sx / n;
					y = sy / n;
				}
				else if (un > 0) {
					x = ux / un;
					y = uy / un;
				}
				else {
					final float[] startLocation = me().getStartLocation();
					x = startLocation == null ? 0 : startLocation[0];
					y = startLocation == null ? 0 : startLocation[1];
				}
			}
			if (!this.attack.homeSet) {
				this.attack.homeX = x;
				this.attack.homeY = y;
			}
			if (!this.defense.homeSet) {
				this.defense.homeX = x;
				this.defense.homeY = y;
			}
		}

		CUnit nearestEnemy(final float x, final float y, final float radius) {
			final CUnit[] best = new CUnit[1];
			final float[] bestDistance = { Float.MAX_VALUE };
			sim.getWorldCollision().enumUnitsInRange(x, y, radius, (u) -> {
				if (isEnemy(u)) {
					final float d = dist(u.getX(), u.getY(), x, y);
					if (d < bestDistance[0]) {
						best[0] = u;
						bestDistance[0] = d;
					}
				}
				return false;
			});
			return best[0];
		}

		CUnit nearestEnemyStructure() {
			CUnit best = null;
			float bestDistance = Float.MAX_VALUE;
			for (final CUnit u : sim.getUnits()) {
				if (u.isBuilding() && isEnemy(u)) {
					final float d = dist(u.getX(), u.getY(), this.defense.homeX, this.defense.homeY);
					if (d < bestDistance) {
						best = u;
						bestDistance = d;
					}
				}
			}
			return best;
		}

		/** The nearest structure of a player (or unit, if unitsFirst or it has no structures). */
		CUnit findTarget(final int owner, final float x, final float y, final boolean unitsFirst) {
			return findTarget(owner, x, y, unitsFirst, null);
		}

		/** As above, but only what the attacker can hit (a footman cannot hit a gryphon). */
		CUnit findTarget(final int owner, final float x, final float y, final boolean unitsFirst,
				final CUnit attacker) {
			if (owner < 0) {
				return null;
			}
			CUnit building = null;
			CUnit unit = null;
			float buildingDistance = Float.MAX_VALUE;
			float unitDistance = Float.MAX_VALUE;
			CUnit shielded = null;
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() != owner) || u.isDead() || u.isHidden()) {
					continue;
				}
				if (u.isInvulnerable()) {
					// scripted bases are often invulnerable: only a last resort (to walk to)
					shielded = u;
					continue;
				}
				if ((attacker != null) && !canAttack(attacker, u)) {
					continue;
				}
				final float d = dist(u.getX(), u.getY(), x, y);
				if (u.isBuilding()) {
					if (d < buildingDistance) {
						building = u;
						buildingDistance = d;
					}
				}
				else if (d < unitDistance) {
					unit = u;
					unitDistance = d;
				}
			}
			if (unitsFirst) {
				return unit != null ? unit : building != null ? building : shielded;
			}
			return building != null ? building : unit != null ? unit : shielded;
		}

		// ---- orders

		/** Ticks before which the AI leaves a unit alone (no re-ordering a unit that just got an order). */
		final Map<CUnit, Integer> nextOrder = new HashMap<>();

		boolean ready(final CUnit u) {
			final Integer t = this.nextOrder.get(u);
			return (t == null) || (sim.getGameTurnTick() >= t);
		}

		int orders;

		void rest(final CUnit u, final float seconds) {
			this.nextOrder.put(u, sim.getGameTurnTick() + ticks(seconds));
			this.orders++;
		}

		void attackMove(final CUnit u, final float x, final float y) {
			rest(u, 4);
			if (!pointOrder(u, OrderIds.attack, x, y)) {
				pointOrder(u, OrderIds.move, x, y);
			}
		}

		/**
		 * Goes for a target: an attack order on it when near (a building's centre is no place to walk to),
		 * attack-move towards it when far; next to a target that cannot be attacked, wait.
		 */
		void strike(final CUnit u, final CUnit target) {
			final float d = dist(u.getX(), u.getY(), target.getX(), target.getY());
			if ((d < 1200) && !target.isInvulnerable() && targetOrder(u, OrderIds.attack, target)) {
				rest(u, 4);
				return;
			}
			if (d < 700) {
				// cannot hit it (invulnerable, out of reach): anything else hostile around?
				final CUnit other = nearestAttackable(u, 1000);
				if ((other != null) && targetOrder(u, OrderIds.attack, other)) {
					rest(u, 4);
					return;
				}
				rest(u, 10);
				return;
			}
			attackMove(u, target.getX(), target.getY());
		}

		CUnit nearestAttackable(final CUnit from, final float radius) {
			final CUnit[] best = new CUnit[1];
			final float[] bestDistance = { Float.MAX_VALUE };
			final float x = from.getX();
			final float y = from.getY();
			sim.getWorldCollision().enumUnitsInRange(x, y, radius, (u) -> {
				if (isEnemy(u) && canAttack(from, u)) {
					final float d = dist(u.getX(), u.getY(), x, y);
					if (d < bestDistance[0]) {
						best[0] = u;
						bestDistance[0] = d;
					}
				}
				return false;
			});
			return best[0];
		}

		void initAssault() {
			this.attack.desire.clear();
			this.attack.prune(this.player);
			if (!this.attack.launched()) {
				this.attack.units.clear();
			}
			this.attack.clearTargets();
		}

		boolean addTo(final Captain captain, final int qty, final int typeId) {
			captain.prune(this.player);
			if (captain == this.attack) {
				final Integer had = captain.desire.get(typeId);
				captain.desire.put(typeId, (had == null ? 0 : had) + qty);
			}
			int have = captain.countOf(typeId);
			if (have < qty) {
				for (final CUnit u : sim.getUnits()) {
					if (have >= qty) {
						break;
					}
					if ((u.getTypeId().getValue() == typeId) && isFree(u) && (!isPeon(u) || (captain == this.attack))) {
						captain.units.add(u);
						have++;
					}
				}
			}
			if ((have < qty) && (captain == this.attack)) {
				// a campaign AI's waves are mostly its base's own units (o02_06x11: 750 gold, no gold mine,
				// 13-unit waves): guards leave their posts for the attack; the post is refilled later
				// (FillGuardPosts, up to SetReplacementCount)
				for (final Post p : this.posts) {
					if (have >= qty) {
						break;
					}
					final CUnit u = p.unit;
					if ((u != null) && (u.getTypeId().getValue() == typeId) && !u.isDead()
							&& (u.getPlayerIndex() == this.player) && (u.getHeroData() == null) && !isPeon(u)
							&& !this.defense.units.contains(u) && !this.suicide.containsKey(u)
							&& !captain.units.contains(u)) {
						p.unit = null;
						captain.units.add(u);
						have++;
					}
				}
			}
			return have >= qty;
		}

		void attackMoveXY(final float x, final float y) {
			this.attack.prune(this.player);
			this.attack.targetPlayer = -1;
			this.attack.hasGoal = true;
			this.attack.goalX = x;
			this.attack.goalY = y;
			for (final CUnit u : this.attack.units) {
				attackMove(u, x, y);
			}
		}

		boolean suicidePlayer(final int owner, final boolean checkFull, final boolean unitsFirst) {
			this.attack.prune(this.player);
			if ((checkFull && !this.attack.isFull()) || this.attack.units.isEmpty()) {
				return false;
			}
			final float[] c = this.attack.center();
			final CUnit target = findTarget(owner, c[0], c[1], unitsFirst);
			if (target == null) {
				return false;
			}
			this.attack.hasGoal = false;
			this.attack.targetPlayer = owner;
			this.attack.unitsFirst = unitsFirst;
			for (final CUnit u : this.attack.units) {
				attackMove(u, target.getX(), target.getY());
			}
			log("player " + this.player + " attacks player " + owner + " with " + this.attack.units.size()
					+ " units");
			return true;
		}

		void suicideUnits(final int qty, final int typeId, final int owner) {
			int sent = 0;
			for (final CUnit u : sim.getUnits()) {
				if (sent >= qty) {
					break;
				}
				if ((u.getTypeId().getValue() != typeId) || !isFree(u) || isPeon(u)) {
					continue;
				}
				final int target = owner >= 0 ? owner : enemyPlayerNear(u);
				if (target < 0) {
					return;
				}
				CUnit t = findTarget(target, u.getX(), u.getY(), false, u);
				if (t == null) {
					t = findTarget(target, u.getX(), u.getY(), false);
				}
				if (t == null) {
					return;
				}
				this.suicide.put(u, target);
				strike(u, t);
				sent++;
			}
		}

		int enemyPlayerNear(final CUnit from) {
			final CUnit e = nearestEnemyStructure();
			return e == null ? -1 : e.getPlayerIndex();
		}

		boolean inCombat(final Captain captain) {
			captain.prune(this.player);
			int checked = 0;
			for (final CUnit u : captain.units) {
				if (nearestEnemy(u.getX(), u.getY(), COMBAT_RADIUS) != null) {
					return true;
				}
				if (++checked >= 16) {
					break;
				}
			}
			return false;
		}

		// ---- guard posts

		void initGuardPosts() {
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() == this.player) && usable(u) && !isPeon(u)) {
					this.posts.add(new Post(u.getTypeId().getValue(), u.getX(), u.getY(), u));
				}
			}
		}

		void removePost(final CUnit u, final boolean keepSpot) {
			for (int k = this.posts.size() - 1; k >= 0; k--) {
				final Post p = this.posts.get(k);
				if (p.unit == u) {
					if (keepSpot) {
						p.unit = null;
					}
					else {
						this.posts.remove(k);
					}
				}
			}
		}

		void fillGuardPosts() {
			for (final Post p : this.posts) {
				if ((p.unit != null) && !p.unit.isDead() && (p.unit.getPlayerIndex() == this.player)) {
					continue;
				}
				p.unit = null;
				if (this.replacements <= 0) {
					continue;
				}
				CUnit free = null;
				for (final CUnit u : sim.getUnits()) {
					if ((u.getTypeId().getValue() == p.typeId) && isFree(u)) {
						free = u;
						break;
					}
				}
				if (free != null) {
					p.unit = free;
					this.replacements--;
					attackMove(free, p.x, p.y);
				}
				else if (count(this.player, p.typeId, false) <= count(this.player, p.typeId, true)) {
					// nothing on the way: train one (fails quietly if it cannot be afforded)
					produce(1, p.typeId);
				}
			}
		}

		void returnGuardPosts() {
			for (final Post p : this.posts) {
				final CUnit u = p.unit;
				if ((u == null) || u.isDead() || !idle(u) || !ready(u)) {
					continue;
				}
				if ((dist(u.getX(), u.getY(), p.x, p.y) > 300) && (nearestEnemy(u.getX(), u.getY(), 800) == null)) {
					attackMove(u, p.x, p.y);
				}
			}
		}

		// ---- the once-a-second upkeep

		void think() {
			this.thinks++;
			if (this.nextOrder.size() > 256) {
				final List<CUnit> gone = new ArrayList<>();
				for (final CUnit u : this.nextOrder.keySet()) {
					if (u.isDead()) {
						gone.add(u);
					}
				}
				for (final CUnit u : gone) {
					this.nextOrder.remove(u);
				}
			}
			this.attack.prune(this.player);
			this.defense.prune(this.player);
			thinkDefense();
			thinkAttack();
			thinkSuicide();
			if ((this.thinks % 3) == 0) {
				thinkHarvest();
			}
			thinkHeroes();
			if ((this.thinks % 2) == 0) {
				thinkRevive();
			}
			if ((this.thinks % 3) == 1) {
				leashGuards();
			}
			if ((this.thinks % 30) == 0) {
				int own = 0;
				for (final CUnit u : sim.getUnits()) {
					if ((u.getPlayerIndex() == this.player) && !u.isDead() && !u.isBuilding()) {
						own++;
					}
				}
				log("status player " + this.player + " t=" + (int) (sim.getGameTurnTick()
						* WarsmashConstants.SIMULATION_STEP_TIME) + "s units " + own + " attack " + this.attack.units.size()
						+ (this.attack.targetPlayer >= 0 ? " (vs player " + this.attack.targetPlayer + ")"
								: this.attack.hasGoal ? " (to goal)" : "")
						+ " defense " + this.defense.units.size() + " suicide " + this.suicide.size() + " orders/30s "
						+ this.orders + " gold " + me().getGold() + " commands " + queue().size());
				this.orders = 0;
			}
		}

		void thinkDefense() {
			if (this.defense.units.isEmpty()) {
				return;
			}
			final CUnit threat = nearestEnemy(this.defense.homeX, this.defense.homeY, THREAT_RADIUS);
			for (final CUnit u : this.defense.units) {
				if (!idle(u) || !ready(u)) {
					continue;
				}
				if (threat != null) {
					attackMove(u, threat.getX(), threat.getY());
				}
				else if (dist(u.getX(), u.getY(), this.defense.homeX, this.defense.homeY) > HOME_RADIUS) {
					attackMove(u, this.defense.homeX, this.defense.homeY);
				}
			}
		}

		void thinkAttack() {
			final Captain a = this.attack;
			for (final CUnit u : a.units) {
				if (!idle(u) || !ready(u)) {
					continue;
				}
				if (a.targetPlayer >= 0) {
					CUnit t = findTarget(a.targetPlayer, u.getX(), u.getY(), a.unitsFirst, u);
					if (t == null) {
						t = findTarget(a.targetPlayer, u.getX(), u.getY(), a.unitsFirst);
					}
					if (t != null) {
						strike(u, t);
					}
				}
				else if (a.hasGoal) {
					if (dist(u.getX(), u.getY(), a.goalX, a.goalY) > 250) {
						attackMove(u, a.goalX, a.goalY);
					}
					else {
						// arrived: WC3's captain fights whatever is around the goal
						final CUnit other = nearestAttackable(u, 1200);
						if (other != null) {
							strike(u, other);
						}
					}
				}
				else if (dist(u.getX(), u.getY(), a.homeX, a.homeY) > HOME_RADIUS) {
					attackMove(u, a.homeX, a.homeY);
				}
			}
		}

		void thinkSuicide() {
			if (this.suicide.isEmpty()) {
				return;
			}
			for (final Map.Entry<CUnit, Integer> e : new ArrayList<>(this.suicide.entrySet())) {
				final CUnit u = e.getKey();
				if (u.isDead() || (u.getPlayerIndex() != this.player)) {
					this.suicide.remove(u);
					continue;
				}
				if (idle(u) && ready(u)) {
					CUnit t = findTarget(e.getValue(), u.getX(), u.getY(), false, u);
					if (t == null) {
						t = findTarget(e.getValue(), u.getX(), u.getY(), false);
					}
					if (t == null) {
						this.suicide.remove(u);
					}
					else {
						strike(u, t);
					}
				}
			}
		}

		void thinkHarvest() {
			if ((this.wantGold <= 0) && (this.wantWood <= 0)) {
				return;
			}
			final List<CUnit> gold = new ArrayList<>();
			final List<CUnit> wood = new ArrayList<>();
			final List<CUnit> idlePeons = new ArrayList<>();
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() != this.player) || !usable(u) || !isPeon(u) || !isFree(u)) {
					continue;
				}
				final CBehavior behavior = u.getCurrentBehavior();
				final int highlight = behavior == null ? OrderIds.stop : behavior.getHighlightOrderId();
				if ((highlight == OrderIds.harvest) || (highlight == OrderIds.returnresources)) {
					final COrder order = u.getCurrentOrder();
					final AbilityTarget target = order == null ? null : order.getTarget(sim);
					if (target instanceof CDestructable) {
						wood.add(u);
					}
					else {
						gold.add(u);
					}
				}
				else if (idle(u)) {
					idlePeons.add(u);
				}
			}
			final CUnit hall = townCenter();
			if (hall == null) {
				return;
			}
			final CUnit mine = mineNear(hall);
			while ((gold.size() < this.wantGold) && (mine != null)) {
				CUnit peon = null;
				if (!idlePeons.isEmpty()) {
					peon = idlePeons.remove(idlePeons.size() - 1);
				}
				else if (wood.size() > this.wantWood) {
					peon = wood.remove(wood.size() - 1);
				}
				if (peon == null) {
					break;
				}
				if (targetOrder(peon, OrderIds.harvest, mine)) {
					gold.add(peon);
				}
			}
			while ((wood.size() < this.wantWood) && !idlePeons.isEmpty()) {
				final CUnit peon = idlePeons.remove(idlePeons.size() - 1);
				if (harvestTree(peon, hall)) {
					wood.add(peon);
				}
				else {
					break;
				}
			}
		}

		boolean harvestTree(final CUnit peon, final CUnit hall) {
			final List<CDestructable> trees = new ArrayList<>();
			final Rectangle area = new Rectangle(hall.getX() - 1500, hall.getY() - 1500, 3000, 3000);
			sim.getWorldCollision().enumDestructablesInRect(area, (d) -> {
				if (!d.isDead()) {
					trees.add(d);
				}
				return false;
			});
			final float px = peon.getX();
			final float py = peon.getY();
			Collections.sort(trees, (t1, t2) -> Float.compare(dist(t1.getX(), t1.getY(), px, py),
					dist(t2.getX(), t2.getY(), px, py)));
			for (int k = 0; (k < trees.size()) && (k < 12); k++) {
				if (targetOrder(peon, OrderIds.harvest, trees.get(k))) {
					return true;
				}
			}
			return false;
		}

		/** Dead heroes: revived at an altar as soon as the gold is there (WC3's AI does this itself). */
		void thinkRevive() {
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() != this.player) || !u.isDead()) {
					continue;
				}
				final CAbilityHero hero = u.getHeroData();
				if ((hero == null) || !hero.isAwaitingRevive() || hero.isReviving()) {
					continue;
				}
				for (final CUnit altar : sim.getUnits()) {
					if ((altar.getPlayerIndex() != this.player) || altar.isDead() || altar.isConstructing()
							|| !altar.isBuilding() || (queueLength(altar) > 0)) {
						continue;
					}
					final CAbilityReviveHero revive = altar.getFirstAbilityOfType(CAbilityReviveHero.class);
					if ((revive != null) && immediateOrder(altar, revive, u.getHandleId())) {
						log("player " + this.player + " revives " + u.getTypeId() + " at " + altar.getTypeId());
						return;
					}
				}
			}
		}

		/**
		 * Guards give up a chase beyond MaxGuardDistance and walk back; idle guards away from their post
		 * with no enemy around return to it (the WC3 AI does both on its own, not only on ReturnGuardPosts).
		 */
		void leashGuards() {
			for (final Post p : this.posts) {
				final CUnit u = p.unit;
				if ((u == null) || u.isDead() || (u.getPlayerIndex() != this.player) || u.isMovementDisabled()
						|| (u.getCurrentOrder() != null) || !ready(u)) {
					continue;
				}
				final float d = dist(u.getX(), u.getY(), p.x, p.y);
				if (d > 1000) {
					rest(u, 4);
					pointOrder(u, OrderIds.move, p.x, p.y);
				}
				else if ((d > 300) && idle(u) && (nearestEnemy(u.getX(), u.getY(), 800) == null)) {
					attackMove(u, p.x, p.y);
				}
			}
		}

		void thinkHeroes() {
			for (final CUnit u : sim.getUnits()) {
				if ((u.getPlayerIndex() != this.player) || u.isDead()) {
					continue;
				}
				final CAbilityHero hero = u.getHeroData();
				if ((hero == null) || (hero.getSkillPoints() <= 0)) {
					continue;
				}
				for (int attempt = 0; (attempt < 6) && (hero.getSkillPoints() > 0); attempt++) {
					final int before = hero.getSkillPoints();
					if (this.heroLevels != null) {
						this.currentHeroId = u.getTypeId().getValue();
						this.currentHeroLevel = hero.getHeroLevel();
						final JassThread thread = this.globals.createThreadCapturingReturnValue(this.heroLevels,
								TriggerExecutionScope.EMPTY);
						final JassValue skill = this.globals.runThreadUntilCompletionAndReadReturnValue(thread,
								"SetHeroLevels", IntegerJassValue.ZERO);
						final int skillId = skill == null ? 0 : skill.visit(IntegerJassValueVisitor.getInstance());
						if (skillId != 0) {
							hero.selectHeroSkill(sim, u, new War3ID(skillId));
						}
					}
					if (hero.getSkillPoints() == before) {
						// no (usable) answer from the script: the first skill that can still be learned
						for (final War3ID skillId : u.getUnitType().getHeroAbilityList()) {
							hero.selectHeroSkill(sim, u, skillId);
							if (hero.getSkillPoints() < before) {
								break;
							}
						}
					}
					if (hero.getSkillPoints() == before) {
						break;
					}
				}
			}
		}
	}
}
