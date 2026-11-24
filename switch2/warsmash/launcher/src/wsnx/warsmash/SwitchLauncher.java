package wsnx.warsmash;

import java.io.FileInputStream;
import java.io.IOException;

import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.WarsmashGdxMultiScreenGame;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.NxCrumbs;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.util.WarsmashConstants;
import com.thelogicmaster.switchgdx.SwitchApplication;

/**
 * Warsmash on the Switch 2: the console's counterpart of desktop's DesktopLauncher.main. Same order:
 * extensions, warsmash.ini, constants, the game, then the menu screen. SwitchApplication's constructor
 * runs the main loop and never returns, so the menu screen is set from create() instead of a
 * postRunnable after the constructor. The loading screen is installed first (create() is the first
 * moment GL exists), so the minutes the menu takes to load are not a black screen.
 */
public class SwitchLauncher {
	/** Everything Warsmash reads or writes on the card lives under here (the guard's jail). */
	public static final String ROOT = "sdmc:/switch/warsmash/";

	public static void main(final String[] args) {
		System.out.println("Warsmash engine is starting (Switch 2)...");
		NxExtensions.install();
		NxFolderLister.install();
		NxProfiler.start(Thread.currentThread()); // where the game thread spends its time, every 30 s
		final DataTable warsmashIni = loadWarsmashIni(ROOT + "warsmash.ini");
		final Element emulatorConstants = warsmashIni.get("Emulator");
		WarsmashConstants.loadConstants(emulatorConstants, warsmashIni);
		// Edge scrolling (MeleeUI) runs only with CatchCursor; the console's pointer always stays on
		// the screen (its other effect, catching the cursor, is a no-op here), so it is always on.
		WarsmashConstants.CATCH_CURSOR = true;
		// Every campaign and mission selectable from the start.
		com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CampaignProgressStore.UNLOCK_ALL = true;
		// a burst of path requests spread over more ticks (1500 a tick on the PC) instead of one long frame
		com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathfindingProcessor.WORK_BUDGET_PER_TICK = 750;
		// Worker threads on the cores the game thread and the GL driver leave free (model poses are
		// evaluated there, WorldScene). [Emulator] Workers=N overrides: 0 = all on the game thread.
		NxCores.install(emulatorConstants.getField("Workers").trim().isEmpty() ? -1
				: emulatorConstants.getFieldValue("Workers"));

		final WarsmashGdxMultiScreenGame game = new WarsmashGdxMultiScreenGame() {
			@Override
			public void create() {
				final NxLoadingScreen loading = NxLoadingScreen.install("Starting Warsmash");
				try {
					super.create();
					setScreen(new WarsmashGdxMenuScreen(warsmashIni, this));
				}
				catch (final RuntimeException | Error e) {
					loading.fail(e); // shows why on screen for a while, then the game ends as before
					throw e;
				}
				// The first frames after loading leave breadcrumbs in the log (a CPU fault on kaiHEN
				// leaves nothing else); the native side adds the swaps, memory and audio events.
				// Only 8: each crumb is a line, i.e. an SD-card write, and 600 frames of them held the
				// menu at about 8 fps (2026-11-22). Raise it again to hunt a crash in later frames.
				NxCrumbs.enable(8);
			}

			private long renderNs;
			private int renderFrames;

			@Override
			public void render() {
				NxCrumbs.crumb("render");
				// the pad's camera stick, read by SwitchInput this frame, for the game camera
				com.etheller.warsmash.util.PadSticks.cameraX = com.thelogicmaster.switchgdx.SwitchInput.cameraStickX;
				com.etheller.warsmash.util.PadSticks.cameraY = com.thelogicmaster.switchgdx.SwitchInput.cameraStickY;
				final long start = System.nanoTime();
				super.render();
				this.renderNs += System.nanoTime() - start;
				if (++this.renderFrames == 600) {
					// with [NxFrame]'s game time: the rest of it is SwitchApplication's loop around this
					System.out.println(String.format("[NxRender] 600 frames: render() %.1f ms, of it simulation ticks %.1f ms, per frame",
							this.renderNs / 600e6,
							com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.nxSimulationNs / 600e6));
					this.renderNs = 0;
					this.renderFrames = 0;
					com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.nxSimulationNs = 0;
				}
				NxCrumbs.crumb("rendered");
				NxCrumbs.endFrame();
			}
		};
		new SwitchApplication(game, new SwitchApplication.Config(true));
	}

	static DataTable loadWarsmashIni(final String iniPath) {
		final DataTable warsmashIni = new DataTable(StringBundle.EMPTY);
		try (FileInputStream in = new FileInputStream(iniPath)) {
			warsmashIni.readTXT(in, true);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return warsmashIni;
	}
}
