package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;

/**
 * warsmash-nx console build: OpenWCIII's QA probe (desktop audits started with -Dwarsmash.* properties)
 * reduced to no-ops. The original uses Java 16 syntax and APIs the console runtime lacks; on the console no
 * audit runs, so the hooks the game calls do nothing. The PC build keeps the real probe.
 */
public final class HumanCampaignPlaythroughProbe {
	private HumanCampaignPlaythroughProbe() {
	}

	public static void afterMapRender(final War3MapViewer viewer, final MeleeUI ui, final boolean initialized) {
	}

	public static void afterUIRender() {
	}
}
