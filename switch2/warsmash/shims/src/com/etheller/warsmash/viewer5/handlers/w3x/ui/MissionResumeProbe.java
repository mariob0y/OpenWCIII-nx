package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;

/**
 * warsmash-nx console build: OpenWCIII's QA probe (desktop audits started with -Dwarsmash.* properties)
 * reduced to no-ops. The original uses Java 16 syntax and APIs the console runtime lacks; on the console no
 * audit runs, so the hooks the game calls do nothing. The PC build keeps the real probe.
 */
public final class MissionResumeProbe {
	private MissionResumeProbe() {
	}

	public static void install(final com.etheller.interpreter.ast.util.JassProgram program, final War3MapViewer viewer) {
	}

	public static boolean installAI(final com.etheller.interpreter.ast.util.JassProgram program, final String path) {
		return false;
	}

	public static void afterMapRender(final War3MapViewer viewer, final MeleeUI ui,
			final com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment environment) {
	}

	public static void afterTick(final War3MapViewer viewer, final MeleeUI ui) {
	}

	public static void afterUIRender() {
	}

	public static void afterMenuRender() {
	}

	public static void afterLoadFailure(final String error) {
	}
}
