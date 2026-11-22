package com.etheller.warsmash.parsers.jass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.scope.variableevent.CLimitOp;
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
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;

/**
 * warsmash-nx: JASS natives the Founding of Durotar maps call that Warsmash lacked (found by
 * scripts/campaign-audit and the "had no native implementation" log lines):
 * <ul>
 * <li>TriggerRegisterPlayerStateEvent: the maps top the computer players' gold and lumber up through it
 * (orcx02_06, orcx03a, orcx03b), without it the enemy AIs run dry;</li>
 * <li>TriggerRegisterUnitInRange: rescues, voice-overs, the lizard aggro and detector tower events;</li>
 * <li>leaderboards (kept as data; shown as a text line when displayed or relabelled, no panel);</li>
 * <li>ShowDestructable, GetDestructableTypeId, SetDestructableMaxLife, SetUnitPathing, IsUnitIdType,
 * GetUnitDefaultMoveSpeed, DisplayTimedTextFromPlayer, and no-ops for the debug chat commands and weather
 * removal (weather is not rendered).</li>
 * </ul>
 * The two events are polled from one simulation effect (every 0.2 s), like WC3 they fire when the
 * condition starts to hold (state) or a unit comes into range.
 */
final class NxEventNatives {
	private NxEventNatives() {
	}

	private static final float POLL_SECONDS = 0.2f;

	private interface Watcher {
		/** Returns false once the watcher is to be dropped. */
		boolean poll(CSimulation sim);
	}

	private static final List<Watcher> WATCHERS = new ArrayList<>();
	private static CSimulation watchedSim;

	private static JassProgram program;

	private static void define(final String name, final JassFunction function) {
		// Warsmash's two leaderboard getters are "NYI" stubs returning 0: replace them
		if (!program.getJassNativeManager().hasNative(name) || name.startsWith("Leaderboard")) {
			program.getJassNativeManager().createNative(name, function);
		}
	}

	private static void watch(final CSimulation sim, final Watcher watcher) {
		if (watchedSim != sim) {
			WATCHERS.clear();
			watchedSim = sim;
			final int every = Math.max(1, (int) (POLL_SECONDS / WarsmashConstants.SIMULATION_STEP_TIME));
			sim.registerEffect(new CEffect() {
				private int tick;

				@Override
				public boolean update(final CSimulation game) {
					if (watchedSim != game) {
						return true;
					}
					if ((++this.tick % every) != 0) {
						return false;
					}
					for (final Watcher w : new ArrayList<>(WATCHERS)) {
						boolean keep;
						try {
							keep = w.poll(game);
						}
						catch (final Exception e) {
							System.err.println("warsmash-nx: event poll failed: " + e);
							keep = false;
						}
						if (!keep) {
							WATCHERS.remove(w);
						}
					}
					return false;
				}
			});
		}
		WATCHERS.add(watcher);
	}

	static boolean compare(final CLimitOp op, final double value, final double limit) {
		switch (op) {
		case LESS_THAN:
			return value < limit;
		case LESS_THAN_OR_EQUAL:
			return value <= limit;
		case EQUAL:
			return value == limit;
		case GREATER_THAN_OR_EQUAL:
			return value >= limit;
		case GREATER_THAN:
			return value > limit;
		case NOT_EQUAL:
			return value != limit;
		default:
			return false;
		}
	}

	/** A leaderboard: kept as data; see the class comment. */
	static final class Leaderboard {
		String label = "";
		boolean displayed;
		boolean showValues = true;
		final List<Object[]> items = new ArrayList<>(); // {label, value, player}

		int indexOf(final CPlayer p) {
			for (int i = 0; i < this.items.size(); i++) {
				if (this.items.get(i)[2] == p) {
					return i;
				}
			}
			return -1;
		}

		String text() {
			final StringBuilder b = new StringBuilder(this.label);
			for (final Object[] item : this.items) {
				b.append(b.length() > 0 ? "  |  " : "").append(item[0]);
				if (this.showValues && (item[1] != null)) {
					b.append(' ').append(item[1]);
				}
			}
			return b.toString();
		}
	}

	/** TRIGSTR_n -> the map's string n (war3map.wts), loaded on first use. */
	static java.util.function.UnaryOperator<String> trigStrings(final War3MapViewer viewer) {
		final com.etheller.warsmash.units.custom.WTS[] wts = { null };
		return (t) -> {
			if (t == null) {
				return "";
			}
			if (!t.startsWith("TRIGSTR_")) {
				return t;
			}
			try {
				if (wts[0] == null) {
					wts[0] = com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData
							.loadWTS(viewer.mapMpq);
				}
				final String v = wts[0].get(Integer.parseInt(t.substring(8).trim()));
				return v == null ? t : v;
			}
			catch (final Exception e) {
				return t;
			}
		};
	}

	static <T> T obj(final List<JassValue> a, final int i) {
		final JassValue v = a.get(i);
		return v == null ? null : v.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	static int i(final List<JassValue> a, final int i) {
		return a.get(i).visit(IntegerJassValueVisitor.getInstance());
	}

	static float f(final List<JassValue> a, final int i) {
		return a.get(i).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	static boolean b(final List<JassValue> a, final int i) {
		return a.get(i).visit(BooleanJassValueVisitor.getInstance());
	}

	static String s(final List<JassValue> a, final int i) {
		final JassValue v = a.get(i);
		return v == null ? "" : v.visit(StringJassValueVisitor.getInstance());
	}

	static void register(final JassProgram jassProgram, final War3MapViewer viewer,
			final java.util.function.UnaryOperator<String> trigStr) {
		program = jassProgram;
		final CSimulation sim = viewer.simulation;
		final GlobalScope globals = jassProgram.getGlobalScope();
		final HandleJassType eventType = globals.getHandleType("event");
		final HandleJassType playerType = globals.getHandleType("player");
		final HandleJassType leaderboardType = globals.getHandleType("leaderboard");

		// ---- events
		define("TriggerRegisterPlayerStateEvent", (a, globalScope, scope) -> {
			final Trigger trigger = obj(a, 0);
			final CPlayer player = obj(a, 1);
			final CPlayerState state = obj(a, 2);
			final CLimitOp op = obj(a, 3);
			final double limit = f(a, 4);
			final boolean[] removed = { false };
			if ((trigger != null) && (player != null) && (state != null) && (op != null)) {
				watch(sim, new Watcher() {
					private boolean held;

					@Override
					public boolean poll(final CSimulation game) {
						if (removed[0]) {
							return false;
						}
						final boolean holds = compare(op, player.getPlayerState(game, state), limit);
						if (holds && !this.held) {
							System.err.println("warsmash-nx: player state event: player " + player.getId() + " "
									+ state + " " + op + " " + limit);
							final CommonTriggerExecutionScope eventScope = CommonTriggerExecutionScope
									.triggerPlayerScope(JassGameEventsWar3.EVENT_PLAYER_STATE_LIMIT, trigger, player);
							globalScope.queueTrigger(null, TriggerExecutionScope.EMPTY, trigger, eventScope,
									eventScope);
						}
						this.held = holds;
						return true;
					}
				});
			}
			return new HandleJassValue(eventType, new RemovableTriggerEvent(trigger) {
				@Override
				public void remove() {
					removed[0] = true;
				}
			});
		});
		define("TriggerRegisterUnitInRange", (a, globalScope, scope) -> {
			final Trigger trigger = obj(a, 0);
			final CUnit center = obj(a, 1);
			final float range = f(a, 2);
			final TriggerBooleanExpression filter = obj(a, 3);
			final boolean[] removed = { false };
			if ((trigger != null) && (center != null)) {
				watch(sim, new Watcher() {
					private Set<CUnit> inside = new HashSet<>();

					@Override
					public boolean poll(final CSimulation game) {
						if (removed[0]) {
							return false;
						}
						if (game.getUnit(center.getHandleId()) == null) {
							return false; // removed from the game
						}
						final Set<CUnit> now = new HashSet<>();
						if (!center.isDead()) {
							game.getWorldCollision().enumUnitsInRange(center.getX(), center.getY(), range, (u) -> {
								if ((u != center) && !u.isDead() && !u.isHidden()) {
									now.add(u);
								}
								return false;
							});
						}
						for (final CUnit u : now) {
							if (!this.inside.contains(u)) {
								final CommonTriggerExecutionScope eventScope = CommonTriggerExecutionScope
										.unitTriggerScope(JassGameEventsWar3.EVENT_UNIT_TARGET_IN_RANGE, trigger, u);
								globalScope.queueTrigger(filter,
										CommonTriggerExecutionScope.filterScope(TriggerExecutionScope.EMPTY, u), trigger,
										eventScope, eventScope);
							}
						}
						this.inside = now;
						return true;
					}
				});
			}
			return new HandleJassValue(eventType, new RemovableTriggerEvent(trigger) {
				@Override
				public void remove() {
					removed[0] = true;
				}
			});
		});
		// the maps only use chat commands for the designers' debug switches
		define("TriggerRegisterPlayerChatEvent", (a, globalScope, scope) -> {
			final Trigger trigger = obj(a, 0);
			return new HandleJassValue(eventType, new RemovableTriggerEvent(trigger) {
				@Override
				public void remove() {
				}
			});
		});

		// ---- destructables
		define("ShowDestructable", (a, globalScope, scope) -> {
			final CDestructable d = obj(a, 0);
			if (d != null) {
				final RenderDestructable peer = viewer.getRenderPeer(d);
				if ((peer != null) && (peer.instance != null)) {
					if (b(a, 1)) {
						peer.instance.show();
					}
					else {
						peer.instance.hide();
					}
				}
			}
			return null;
		});
		// the simulation's destructable type keeps no id: match the object data by name and life, then by
		// the (cached) type instance itself
		final java.util.Map<Object, Integer> destTypeIds = new java.util.HashMap<>();
		define("GetDestructableTypeId", (a, globalScope, scope) -> {
			final CDestructable d = obj(a, 0);
			if (d == null) {
				return IntegerJassValue.of(0);
			}
			final Integer known = destTypeIds.get(d.getDestType());
			if (known != null) {
				return IntegerJassValue.of(known);
			}
			int id = 0;
			final com.etheller.warsmash.units.ObjectData data = viewer.getAllObjectData().getDestructibles();
			for (final String key : data.keySet()) {
				final com.etheller.warsmash.units.GameObject row = data.get(key);
				if ((row == null) || (key.length() != 4)
						|| !d.getDestType().getName().equals(row.getFieldAsString("Name", 0))
						|| (row.getFieldAsFloat("HP", 0) != d.getDestType().getMaxLife())) {
					continue;
				}
				final War3ID candidate = War3ID.fromString(key);
				if (sim.getDestructableData().getUnitType(candidate) == d.getDestType()) {
					id = candidate.getValue();
					break;
				}
			}
			destTypeIds.put(d.getDestType(), id);
			return IntegerJassValue.of(id);
		});
		// a destructable's maximum life comes from its type here: keep its share of life at the new maximum
		define("SetDestructableMaxLife", (a, globalScope, scope) -> {
			final CDestructable d = obj(a, 0);
			if ((d != null) && !d.isDead() && (d.getMaxLife() > 0)) {
				d.setLife(sim, (d.getLife() / d.getMaxLife()) * f(a, 1));
			}
			return null;
		});
		define("SetDestructableAnimationSpeed", (a, globalScope, scope) -> null);
		define("RemoveWeatherEffect", (a, globalScope, scope) -> null); // AddWeatherEffect is not rendered

		// ---- units
		define("SetUnitPathing", (a, globalScope, scope) -> {
			final CUnit u = obj(a, 0);
			if (u != null) {
				u.setNoCollisionMovementType(!b(a, 1));
			}
			return null;
		});
		define("IsUnitIdType", (a, globalScope, scope) -> {
			final CUnitType type = sim.getUnitData().getUnitType(new War3ID(i(a, 0)));
			final CUnitTypeJass which = obj(a, 1);
			if ((type == null) || (which == null)) {
				return BooleanJassValue.FALSE;
			}
			switch (which) {
			case STRUCTURE:
				return BooleanJassValue.of(type.isBuilding());
			case HERO:
				return BooleanJassValue.of(type.isHero());
			case FLYING:
				return BooleanJassValue.of(type.getTargetedAs().contains(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType.AIR));
			case GROUND:
				return BooleanJassValue.of(type.getTargetedAs().contains(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType.GROUND));
			case PEON:
				return BooleanJassValue.of(type.getClassifications().contains(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification.PEON));
			case MECHANICAL:
				return BooleanJassValue.of(type.getClassifications().contains(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification.MECHANICAL));
			case UNDEAD:
				return BooleanJassValue.of(type.getClassifications().contains(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification.UNDEAD));
			case TOWNHALL:
				return BooleanJassValue.of(type.getClassifications().contains(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification.TOWNHALL));
			case ANCIENT:
				return BooleanJassValue.of(type.getClassifications().contains(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification.ANCIENT));
			case SUMMONED:
				return BooleanJassValue.of(type.getClassifications().contains(
						com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification.SUMMONED));
			default:
				return BooleanJassValue.FALSE;
			}
		});
		define("GetUnitDefaultMoveSpeed", (a, globalScope, scope) -> {
			final CUnit u = obj(a, 0);
			return RealJassValue.of(u == null ? 0 : u.getUnitType().getSpeed());
		});

		// ---- text
		final JassFunction displayTo = program.getJassNativeManager().getUnboundNative("DisplayTimedTextToPlayer");
		final JassFunction localPlayer = program.getJassNativeManager().getUnboundNative("GetLocalPlayer");
		define("DisplayTimedTextFromPlayer", (a, globalScope, scope) -> {
			final CPlayer from = obj(a, 0);
			if ((displayTo == null) || (localPlayer == null)) {
				return null;
			}
			String text = trigStr.apply(s(a, 4));
			if ((from != null) && (text != null)) {
				text = text.replace("%s", from.getName());
			}
			final List<JassValue> args = new ArrayList<>();
			args.add(localPlayer.call(new ArrayList<>(), globalScope, scope));
			args.add(a.get(1));
			args.add(a.get(2));
			args.add(a.get(3));
			args.add(new StringJassValue(text == null ? "" : text));
			return displayTo.call(args, globalScope, scope);
		});

		// ---- leaderboards
		final java.util.function.BiConsumer<Leaderboard, GlobalScope> show = (lb, globalScope) -> {
			if (lb.displayed && (displayTo != null) && (localPlayer != null)) {
				final List<JassValue> args = new ArrayList<>();
				args.add(localPlayer.call(new ArrayList<>(), globalScope, TriggerExecutionScope.EMPTY));
				args.add(RealJassValue.of(0));
				args.add(RealJassValue.of(0));
				args.add(RealJassValue.of(10));
				args.add(new StringJassValue(lb.text()));
				displayTo.call(args, globalScope, TriggerExecutionScope.EMPTY);
			}
		};
		define("CreateLeaderboard", (a, g, s) -> new HandleJassValue(leaderboardType, new Leaderboard()));
		define("DestroyLeaderboard", (a, g, s) -> null);
		define("LeaderboardDisplay", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			if (lb != null) {
				final boolean was = lb.displayed;
				lb.displayed = b(a, 1);
				if (!was) {
					show.accept(lb, g);
				}
			}
			return null;
		});
		define("IsLeaderboardDisplayed", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			return BooleanJassValue.of((lb != null) && lb.displayed);
		});
		define("LeaderboardGetItemCount", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			return IntegerJassValue.of(lb == null ? 0 : lb.items.size());
		});
		define("LeaderboardAddItem", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			if (lb != null) {
				lb.items.add(new Object[] { trigStr.apply(s(a, 1)), i(a, 2), obj(a, 3) });
			}
			return null;
		});
		define("LeaderboardRemoveItem", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			final int index = i(a, 1);
			if ((lb != null) && (index >= 0) && (index < lb.items.size())) {
				lb.items.remove(index);
			}
			return null;
		});
		define("LeaderboardRemovePlayerItem", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			if (lb != null) {
				final int index = lb.indexOf(obj(a, 1));
				if (index >= 0) {
					lb.items.remove(index);
				}
			}
			return null;
		});
		define("LeaderboardClear", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			if (lb != null) {
				lb.items.clear();
			}
			return null;
		});
		define("LeaderboardHasPlayerItem", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			return BooleanJassValue.of((lb != null) && (lb.indexOf(obj(a, 1)) >= 0));
		});
		define("LeaderboardGetPlayerIndex", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			return IntegerJassValue.of(lb == null ? -1 : lb.indexOf(obj(a, 1)));
		});
		define("LeaderboardSetLabel", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			if (lb != null) {
				lb.label = trigStr.apply(s(a, 1));
			}
			return null;
		});
		define("LeaderboardGetLabelText", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			return new StringJassValue(lb == null ? "" : lb.label);
		});
		final java.util.Map<CPlayer, Leaderboard> playerBoards = new java.util.HashMap<>();
		define("PlayerSetLeaderboard", (a, g, s) -> {
			final CPlayer p = obj(a, 0);
			if (p != null) {
				playerBoards.put(p, obj(a, 1));
			}
			return null;
		});
		define("PlayerGetLeaderboard", (a, g, s) -> {
			final CPlayer p = obj(a, 0);
			final Leaderboard lb = p == null ? null : playerBoards.get(p);
			return new HandleJassValue(leaderboardType, lb);
		});
		define("LeaderboardSetItemValue", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			final int index = i(a, 1);
			if ((lb != null) && (index >= 0) && (index < lb.items.size())) {
				lb.items.get(index)[1] = i(a, 2);
			}
			return null;
		});
		define("LeaderboardSetItemLabel", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			final int index = i(a, 1);
			if ((lb != null) && (index >= 0) && (index < lb.items.size())) {
				final String label = trigStr.apply(s(a, 2));
				if (!label.equals(lb.items.get(index)[0])) {
					lb.items.get(index)[0] = label;
					show.accept(lb, g);
				}
			}
			return null;
		});
		for (final String name : new String[] { "LeaderboardSetSizeByItemCount", "LeaderboardSortItemsByValue",
				"LeaderboardSortItemsByPlayer", "LeaderboardSortItemsByLabel", "LeaderboardSetLabelColor",
				"LeaderboardSetValueColor", "LeaderboardSetItemStyle", "LeaderboardSetItemLabelColor",
				"LeaderboardSetItemValueColor" }) {
			define(name, (a, g, s) -> null);
		}
		define("LeaderboardSetStyle", (a, g, s) -> {
			final Leaderboard lb = obj(a, 0);
			if (lb != null) {
				lb.showValues = b(a, 3);
			}
			return null;
		});
	}
}
