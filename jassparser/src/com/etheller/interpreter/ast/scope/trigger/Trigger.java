package com.etheller.interpreter.ast.scope.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.interpreter.ast.debug.JassException;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.JassValue;

public class Trigger implements CHandle {
	private static int STUPID_STATIC_TRIGGER_COUNT_DELETE_THIS_LATER = 452354453;

	public static int getNextHandleId() {
		return STUPID_STATIC_TRIGGER_COUNT_DELETE_THIS_LATER;
	}

	public static void restoreNextHandleId(final int value) {
		STUPID_STATIC_TRIGGER_COUNT_DELETE_THIS_LATER = value;
	}

	private final int handleId = STUPID_STATIC_TRIGGER_COUNT_DELETE_THIS_LATER++;
	private final List<TriggerBooleanExpression> conditions = new ArrayList<>();
	private final List<JassFunction> actions = new ArrayList<>();
	private final List<RemovableTriggerEvent> events = new ArrayList<>();
	private int evalCount;
	private int execCount;
	private boolean enabled = true;
	// used for eval
	private transient final TriggerExecutionScope triggerExecutionScope = new TriggerExecutionScope(this);
	private boolean waitOnSleeps = true;

	public void addEvent(final RemovableTriggerEvent event) {
		this.events.add(event);
	}

	public int addAction(final JassFunction function) {
		final int index = this.actions.size();
		this.actions.add(function);
		return index;
	}

	public int addAction(final CodeJassValue function) {
		final int index = this.actions.size();
		this.actions.add(new JassThreadActionFunc(function));
		return index;
	}

	public int addCondition(final TriggerBooleanExpression boolexpr) {
		final int index = this.conditions.size();
		this.conditions.add(boolexpr);
		return index;
	}

	public void removeCondition(final TriggerBooleanExpression boolexpr) {
		this.conditions.remove(boolexpr);
	}

	public void removeConditionAtIndex(final int conditionIndex) {
		this.conditions.remove(conditionIndex);
	}

	public void clearConditions() {
		this.conditions.clear();
	}

	public int getEvalCount() {
		return this.evalCount;
	}

	public int getExecCount() {
		return this.execCount;
	}

	public boolean evaluate(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		return this.enabled && evaluateConditions(globalScope, triggerScope);
	}

	public boolean evaluateConditions(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		for (final TriggerBooleanExpression condition : this.conditions) {
			if (!condition.evaluate(globalScope, triggerScope)) {
				return false;
			}
		}
		return true;
	}

	public void execute(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		if (this.enabled) {
			execute(globalScope, triggerScope, false, false);
		}
	}

	/** warsmash-nx: an event's execution: its queued threads do not start if the trigger is disabled first. */
	public void executeFromEvent(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		if (this.enabled) {
			execute(globalScope, triggerScope, false, true);
		}
	}

	/** Explicit TriggerExecute calls run their actions before the caller continues, up to a sleep. */
	public void executeImmediately(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		execute(globalScope, triggerScope, true, false);
	}

	private void execute(final GlobalScope globalScope, final TriggerExecutionScope triggerScope,
			final boolean immediate, final boolean fromEvent) {
		this.execCount++;
		for (final JassFunction action : this.actions) {
			try {
				if (immediate && action instanceof JassThreadActionFunc) {
					((JassThreadActionFunc) action).callImmediately(globalScope, triggerScope);
				}
				else if (fromEvent && action instanceof JassThreadActionFunc) {
					final JassThread triggerThread = globalScope
							.createThread(((JassThreadActionFunc) action).codeJassValue, triggerScope);
					triggerThread.startOnlyIfEnabled = this;
					globalScope.queueThread(triggerThread);
				}
				else {
					action.call(Collections.emptyList(), globalScope, triggerScope);
				}
			}
			catch (final Exception e) {
				throw new JassException(globalScope, "Exception during Trigger action execute", e);
			}
		}
	}

	/**
	 * warsmash-nx: TriggerExecute as Warcraft III runs it: each action now, inside the calling thread,
	 * until it ends or sleeps (execute() queues them for later). Campaign maps chain triggers with
	 * ConditionalTriggerExecute and rely on that: in Frozen Throne's OrcX01, "Init 02 Units" ->
	 * "Load Heroes" -> "Load Rexxar" -> "Create Default Rexxar" must have made Rexxar before the
	 * intro, queued earlier, pauses him (a null unit -> NullPointerException in PauseUnit, on the PC
	 * as on the console).
	 */
	public void executeNow(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		for (final JassFunction action : this.actions) {
			try {
				if (action instanceof JassThreadActionFunc) {
					globalScope.runThreadNow(
							globalScope.createThread(((JassThreadActionFunc) action).codeJassValue, triggerScope));
				}
				else {
					action.call(Collections.emptyList(), globalScope, triggerScope);
				}
			}
			catch (final Exception e) {
				throw new JassException(globalScope, "Exception during Trigger action execute", e);
			}
		}
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	public void destroy() {
		for (final RemovableTriggerEvent event : this.events) {
			event.remove();
		}
		this.events.clear();
	}

	public void reset() {
//		this.actions.clear();
//		this.conditions.clear();
//		this.enabled = true;
//		this.waitOnSleeps = true;
		this.evalCount = 0;
		this.execCount = 0;
	}

	public void setWaitOnSleeps(final boolean waitOnSleeps) {
		this.waitOnSleeps = waitOnSleeps;
	}

	public boolean isWaitOnSleeps() {
		return this.waitOnSleeps;
	}

	@Override
	public int getHandleId() {
		return this.handleId;
	}

	private final class JassThreadActionFunc implements JassFunction {
		private final CodeJassValue codeJassValue;

		public JassThreadActionFunc(final CodeJassValue codeJassValue) {
			this.codeJassValue = codeJassValue;
		}

		public CodeJassValue getCodeJassValue() {
			return this.codeJassValue;
		}

		public void callImmediately(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
			final JassThread triggerThread = globalScope.createThread(this.codeJassValue, triggerScope);
			globalScope.runThreadUntilCompletion(triggerThread);
			if (triggerThread.instructionPtr != -1) {
				globalScope.queueThread(triggerThread);
			}
		}

		@Override
		public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
				final TriggerExecutionScope triggerScope) {
			final JassThread triggerThread = globalScope.createThread(this.codeJassValue, triggerScope);
			globalScope.queueThread(triggerThread);
			return null;
		}

	}

	public TriggerExecutionScope getTriggerExecutionScope() {
		return this.triggerExecutionScope;
	}

	public void removeEvent(final RemovableTriggerEvent evt) {
		if (evt != null) {
			evt.remove();
			this.events.remove(evt);
		}
	}
}
