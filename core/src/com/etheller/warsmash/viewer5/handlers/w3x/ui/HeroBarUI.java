package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.Vector4Definition;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.UnitIconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.QueueItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableActionFrame;

/**
 * The hero bar: one button per hero of the local player down the top left of the screen, under the upper
 * button bar, in the order the heroes were gained. Each button shows the hero's icon (greyed while dead), a
 * life and a mana bar (for a dead hero, the life bar is its revival at an altar), and the skin's HeroBarPointModel (UI\Buttons\HeroLevel) while the hero has unspent
 * skill points. Clicks go to the {@link Listener}.
 */
public class HeroBarUI {
	public static final int MAX_BUTTONS = 7;

	private static final float BUTTON_SIZE_FDF = 0.038f;
	private static final float BAR_HEIGHT_FDF = 0.004f;
	private static final float BAR_GAP_FDF = 0.0008f;
	private static final float BUTTON_STRIDE_FDF = 0.052f;
	private static final float FIRST_BUTTON_X_FDF = 0.004f;
	private static final float FIRST_BUTTON_GAP_FDF = 0.006f;
	/** The HeroLevel model is made for a command button's size. */
	private static final float GLOW_MODEL_SCALE = BUTTON_SIZE_FDF / MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH;

	public interface Listener {
		/** Left click on a button; doubleClick when it is the second click on the same hero in a short time. */
		void heroButtonClicked(CUnit hero, boolean doubleClick);

		/** Right click on a button. */
		void heroButtonRightClicked(CUnit hero);
	}

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final War3MapViewer war3MapViewer;
	private final Listener listener;
	private final SimpleFrame container;
	private final HeroButton[] buttons = new HeroButton[MAX_BUTTONS];
	private final SpriteFrame[] glows = new SpriteFrame[MAX_BUTTONS];
	/** The heroes on the bar, in the order they were gained. */
	private final List<CUnit> heroes = new ArrayList<>();
	private final List<CUnit> scratch = new ArrayList<>();
	private final String levelLabel;
	private boolean shown = true;
	private CUnit lastClickedHero;
	private long lastClickMillis;

	public HeroBarUI(final GameUI rootFrame, final Viewport uiViewport, final War3MapViewer war3MapViewer,
			final UIFrame anchorBelow, final Listener listener) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.war3MapViewer = war3MapViewer;
		this.listener = listener;
		this.levelLabel = rootFrame.getTemplates().getDecoratedString("LEVEL");
		this.container = (SimpleFrame) rootFrame.createSimpleFrame("SmashHeroBar", rootFrame, 0);
		this.container.addSetPoint(new SetPoint(FramePoint.TOPLEFT, anchorBelow, FramePoint.BOTTOMLEFT,
				GameUI.convertX(uiViewport, FIRST_BUTTON_X_FDF), -GameUI.convertY(uiViewport, FIRST_BUTTON_GAP_FDF)));
		this.container.setWidth(GameUI.convertX(uiViewport, BUTTON_SIZE_FDF));
		this.container.setHeight(GameUI.convertY(uiViewport, BUTTON_STRIDE_FDF * MAX_BUTTONS));

		final float size = GameUI.convertX(uiViewport, BUTTON_SIZE_FDF);
		final float barHeight = GameUI.convertY(uiViewport, BAR_HEIGHT_FDF);
		final float barGap = GameUI.convertY(uiViewport, BAR_GAP_FDF);
		final String glowModel = rootFrame.trySkinField("HeroBarPointModel");
		for (int i = 0; i < MAX_BUTTONS; i++) {
			final HeroButton button = new HeroButton("SmashHeroBarButton" + i, this.container);
			final TextureFrame icon = new TextureFrame("SmashHeroBarButtonIcon" + i, button, false,
					new Vector4Definition(0, 1, 0, 1));
			icon.setWidth(size);
			icon.setHeight(size);
			icon.addSetPoint(new SetPoint(FramePoint.TOPLEFT, button, FramePoint.TOPLEFT, 0, 0));

			final SimpleStatusBarFrame hpBar = new SimpleStatusBarFrame("SmashHeroBarHpBar" + i, button, true, true,
					3.0f);
			hpBar.getBarFrame().setTexture("SimpleHpBarConsoleSmall", rootFrame);
			hpBar.getBorderFrame().setTexture("Textures\\Black32.blp", rootFrame);
			hpBar.setWidth(size);
			hpBar.setHeight(barHeight);
			hpBar.addSetPoint(new SetPoint(FramePoint.TOP, icon, FramePoint.BOTTOM, 0, -barGap));

			final SimpleStatusBarFrame manaBar = new SimpleStatusBarFrame("SmashHeroBarManaBar" + i, button, true,
					true, 3.0f);
			manaBar.getBarFrame().setTexture("SimpleManaBarConsoleSmall", rootFrame);
			manaBar.getBorderFrame().setTexture("Textures\\Black32.blp", rootFrame);
			manaBar.getBarFrame().setColor(0f, 0f, 1f, 1f);
			manaBar.setWidth(size);
			manaBar.setHeight(barHeight);
			manaBar.addSetPoint(new SetPoint(FramePoint.TOP, hpBar, FramePoint.BOTTOM, 0, -barGap));

			button.set(icon, hpBar, manaBar);
			button.setWidth(size);
			button.setHeight(size + (2 * (barHeight + barGap)));
			button.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.container, FramePoint.TOPLEFT, 0,
					-GameUI.convertY(uiViewport, BUTTON_STRIDE_FDF * i)));
			button.setVisible(false);
			this.container.add(button);
			this.buttons[i] = button;

			final SpriteFrame glow = (SpriteFrame) rootFrame.createFrameByType("SPRITE", "SmashHeroBarGlow" + i,
					rootFrame, "", 0);
			glow.addSetPoint(new SetPoint(FramePoint.CENTER, icon, FramePoint.CENTER, 0, 0));
			glow.setWidth(size);
			glow.setHeight(size);
			if (glowModel != null) {
				rootFrame.setSpriteFrameModel(glow, glowModel);
				glow.setModelScale(GLOW_MODEL_SCALE);
			}
			glow.setVisible(false);
			this.glows[i] = glow;
		}
		this.container.positionBounds(rootFrame, uiViewport);
	}

	/** Hidden with the rest of the interface (cinematics, the score screen). */
	public void setVisible(final boolean visible) {
		this.shown = visible;
		this.container.setVisible(visible);
		if (!visible) {
			for (final SpriteFrame glow : this.glows) {
				glow.setVisible(false);
			}
		}
	}

	public CUnit getHero(final int index) {
		return index < this.heroes.size() ? this.heroes.get(index) : null;
	}

	/** Once a frame: picks up gained and lost heroes and refreshes icons, bars and level-up glows. */
	public void update(final int localPlayerIndex) {
		this.scratch.clear();
		for (final CUnit unit : this.war3MapViewer.simulation.getUnits()) {
			if (isBarHero(unit, localPlayerIndex)) {
				this.scratch.add(unit);
			}
		}
		// keep the order the heroes were gained in; new ones go last, by creation order (handle id)
		this.heroes.retainAll(this.scratch);
		this.scratch.removeAll(this.heroes);
		Collections.sort(this.scratch, (a, b) -> Integer.compare(a.getHandleId(), b.getHandleId()));
		this.heroes.addAll(this.scratch);

		boolean layoutChanged = false;
		for (int i = 0; i < MAX_BUTTONS; i++) {
			final HeroButton button = this.buttons[i];
			final SpriteFrame glow = this.glows[i];
			final CUnit hero = i < this.heroes.size() ? this.heroes.get(i) : null;
			if (hero == null) {
				if (button.isVisible()) {
					button.setVisible(false);
					layoutChanged = true;
				}
				button.hero = null;
				glow.setVisible(false);
				continue;
			}
			if (!button.isVisible()) {
				button.setVisible(true);
				layoutChanged = true;
			}
			final boolean dead = hero.isDead();
			final CAbilityHero heroData = hero.getHeroData();
			if ((button.hero != hero) || (button.dead != dead)) {
				final UnitIconUI unitUI = this.war3MapViewer.getAbilityDataUI().getUnitUI(hero.getTypeId());
				if (unitUI != null) {
					button.iconFrame.setTexture(dead ? unitUI.getIconDisabled() : unitUI.getIcon());
				}
				button.hero = hero;
				button.dead = dead;
			}
			// a dead hero's life bar shows how far its revival at an altar has got
			final float life = dead ? reviveProgress(hero, localPlayerIndex)
					: hero.getLife() / Math.max(1f, hero.getMaximumLife());
			button.setLifeRatio(life);
			final boolean hasMana = hero.getMaximumMana() > 0;
			button.manaBar.setVisible(hasMana);
			button.setManaRatio(dead || !hasMana ? 0 : hero.getMana() / hero.getMaximumMana());
			button.toolTip = heroData.getProperName();
			button.uberTip = this.levelLabel + " " + heroData.getHeroLevel();
			glow.setVisible(this.shown && !dead && (heroData.getSkillPoints() > 0));
		}
		if (layoutChanged) {
			this.container.positionBounds(this.rootFrame, this.uiViewport);
		}
	}

	/**
	 * How far the altar reviving this dead hero has got (0..1), or 0 when the hero is not being revived or is
	 * still waiting behind other items in the altar's queue.
	 */
	private float reviveProgress(final CUnit hero, final int localPlayerIndex) {
		if (!hero.getHeroData().isReviving()) {
			return 0;
		}
		for (final CUnit altar : this.war3MapViewer.simulation.getUnits()) {
			if ((altar.getPlayerIndex() != localPlayerIndex) || !altar.isBuildQueueActive()) {
				continue;
			}
			final War3ID queued = altar.getBuildQueue()[0];
			if ((altar.getBuildQueueTypes()[0] == QueueItemType.HERO_REVIVE) && (queued != null)
					&& (queued.getValue() == hero.getHandleId())) {
				final float reviveTime = altar.getBuildQueueTimeRemaining(this.war3MapViewer.simulation);
				return reviveTime <= 0 ? 0 : Math.min(altar.getConstructionProgress() / reviveTime, 1f);
			}
		}
		return 0;
	}

	private static boolean isBarHero(final CUnit unit, final int localPlayerIndex) {
		return (unit.getHeroData() != null) && (unit.getPlayerIndex() == localPlayerIndex) && !unit.isNxIllusion()
				&& !unit.isIllusion()
				&& (!unit.isHidden() || unit.isDead());
	}

	private void clicked(final HeroButton button, final int mouseButton) {
		final CUnit hero = button.hero;
		if (hero == null) {
			return;
		}
		if (mouseButton == com.badlogic.gdx.Input.Buttons.RIGHT) {
			this.listener.heroButtonRightClicked(hero);
			return;
		}
		final long now = System.currentTimeMillis();
		final boolean doubleClick = (hero == this.lastClickedHero) && ((now - this.lastClickMillis) < 400);
		this.lastClickedHero = doubleClick ? null : hero;
		this.lastClickMillis = now;
		this.listener.heroButtonClicked(hero, doubleClick);
	}

	/** A hero hotkey (F1, F2, F3): the same as a left click on that button. */
	public boolean hotkey(final int index) {
		if ((index >= this.heroes.size()) || (index >= MAX_BUTTONS)) {
			return false;
		}
		clicked(this.buttons[index], com.badlogic.gdx.Input.Buttons.LEFT);
		return true;
	}

	private final class HeroButton extends AbstractRenderableFrame implements ClickableActionFrame {
		private TextureFrame iconFrame;
		private SimpleStatusBarFrame hpBar;
		private SimpleStatusBarFrame manaBar;
		private CUnit hero;
		private boolean dead;
		private String toolTip;
		private String uberTip;

		HeroButton(final String name, final UIFrame parent) {
			super(name, parent);
		}

		void set(final TextureFrame iconFrame, final SimpleStatusBarFrame hpBar, final SimpleStatusBarFrame manaBar) {
			this.iconFrame = iconFrame;
			this.hpBar = hpBar;
			this.manaBar = manaBar;
		}

		void setLifeRatio(final float ratio) {
			this.hpBar.getBarFrame().setColor(Math.min(1.0f, 2.0f - (ratio * 2)), Math.min(1.0f, ratio * 2), 0, 1.0f);
			this.hpBar.setValue(ratio);
		}

		void setManaRatio(final float ratio) {
			this.manaBar.setValue(ratio);
		}

		@Override
		protected void innerPositionBounds(final GameUI gameUI, final Viewport viewport) {
			this.iconFrame.positionBounds(gameUI, viewport);
			this.hpBar.positionBounds(gameUI, viewport);
			this.manaBar.positionBounds(gameUI, viewport);
		}

		@Override
		protected void internalRender(final SpriteBatch batch, final BitmapFont baseFont,
				final GlyphLayout glyphLayout) {
			this.iconFrame.render(batch, baseFont, glyphLayout);
			this.hpBar.render(batch, baseFont, glyphLayout);
			this.manaBar.render(batch, baseFont, glyphLayout);
		}

		@Override
		public UIFrame touchDown(final float screenX, final float screenY, final int button) {
			if (isVisible() && this.renderBounds.contains(screenX, screenY)) {
				return this;
			}
			return null;
		}

		@Override
		public UIFrame touchUp(final float screenX, final float screenY, final int button) {
			if (isVisible() && this.renderBounds.contains(screenX, screenY)) {
				return this;
			}
			return null;
		}

		@Override
		public UIFrame getFrameChildUnderMouse(final float screenX, final float screenY) {
			if (isVisible() && this.renderBounds.contains(screenX, screenY)) {
				return this;
			}
			return null;
		}

		@Override
		public void mouseDown(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseUp(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseEnter(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseExit(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseDragged(final GameUI rootFrame, final Viewport uiViewport, final float x, final float y) {
		}

		@Override
		public void onClick(final int button) {
			clicked(this, button);
		}

		@Override
		public String getSoundKey() {
			return SOUND_KEY_INTERFACE_CLICK;
		}

		@Override
		public String getToolTip() {
			return this.toolTip;
		}

		@Override
		public String getUberTip() {
			return this.uberTip;
		}

		@Override
		public int getToolTipGoldCost() {
			return 0;
		}

		@Override
		public int getToolTipLumberCost() {
			return 0;
		}

		@Override
		public int getToolTipFoodCost() {
			return 0;
		}

		@Override
		public int getToolTipManaCost() {
			return 0;
		}
	}
}
