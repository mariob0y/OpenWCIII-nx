package com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.ScrollBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextAreaFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CDefeatCondition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuestItem;

/**
 * In-game Quest Dialog driven by Warcraft III's original {@code UI\FrameDef\UI\QuestDialog.fdf}
 * layout, racial skin borders/highlights, quest icons, state backdrops, requirement list,
 * and scrollable description area.
 */
public class QuestDialogUI {
	private static final int VISIBLE_QUEST_ROWS = 3;
	private static final int VISIBLE_ITEM_ROWS = 4;

	private static final float DIALOG_TOP_OFFSET_FDF = -0.03f;
	private static final float QUEST_CONTAINER_WIDTH_FDF = 0.21f;
	private static final float QUEST_ROW_HEIGHT_FDF = 0.035f;
	private static final float QUEST_ROW_GAP_FDF = 0.002f;
	private static final float QUEST_SCROLLBAR_RESERVE_FDF = 0.015f;
	private static final float QUEST_ICON_WIDTH_FDF = 0.035f;
	private static final float QUEST_BUTTON_TEXT_LEFT_FDF = 0.011f;
	private static final float QUEST_BUTTON_TEXT_TOP_WITH_STATUS_FDF = -0.0065f;
	private static final float QUEST_BUTTON_STATUS_BOTTOM_FDF = 0.0055f;
	private static final float QUEST_BUTTON_TEXT_RIGHT_PAD_FDF = 0.009f;
	private static final float QUEST_BUTTON_HIGHLIGHT_INSET_FDF = 0.0045f;
	private static final float QUEST_BUTTON_TITLE_FONT_FDF = 0.0105f;

	private static final float ITEM_ROW_HEIGHT_FDF = 0.014f;
	private static final float ITEM_FONT_FDF = 0.010f;
	private static final float ITEM_TEXT_LEFT_FDF = 0.008f;
	private static final float ITEM_TEXT_WIDTH_FDF = 0.382f;

	private static final Color COLOR_WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
	private static final Color COLOR_GOLD = new Color(0.99f, 0.827f, 0.0705f, 1.0f);
	private static final Color COLOR_COMPLETED_TEXT = new Color(0.80f, 0.80f, 0.80f, 1.0f);
	private static final Color COLOR_FAILED_STATUS = new Color(0.95f, 0.25f, 0.25f, 1.0f);
	private static final Color COLOR_DISABLED = new Color(0.65f, 0.65f, 0.65f, 1.0f);
	private static final Color COLOR_SHADOW = new Color(0.0f, 0.0f, 0.0f, 0.9f);

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final War3MapViewer war3MapViewer;

	private final SimpleFrame questDialog;
	private final StringFrame questTitleValue;
	private final StringFrame questSubtitleValue;
	private final SimpleFrame questMainContainer;
	private final SimpleFrame questOptionalContainer;
	private final BackdropFrame questDisplayBackdrop;
	private final StringFrame questDetailsTitle;
	private final SimpleFrame questItemListContainer;
	private final TextAreaFrame questDisplay;
	private final GlueTextButtonFrame questAcceptButton;

	private final ScrollBarFrame mainScrollBar;
	private final ScrollBarFrame optionalScrollBar;
	private final ScrollBarFrame itemScrollBar;

	private final QuestSlot[] mainSlots = new QuestSlot[VISIBLE_QUEST_ROWS];
	private final QuestSlot[] optionalSlots = new QuestSlot[VISIBLE_QUEST_ROWS];
	private final StringFrame[] itemLineFrames = new StringFrame[VISIBLE_ITEM_ROWS];
	private final BitmapFont questItemFont;
	private final BitmapFont questButtonTitleFont;

	private final Texture undiscoveredIconTexture;
	private final String textCompleted;
	private final String textFailed;
	private final String textUndiscoveredQuest;
	private final String textDefeatConditions;

	private final List<CQuest> mainQuests = new ArrayList<>();
	private final List<CQuest> optionalQuests = new ArrayList<>();
	private final List<ItemDisplayLine> itemDisplayLines = new ArrayList<>();
	private final List<CDefeatCondition> cachedDefeatConditions = new ArrayList<>();

	private CQuest selectedQuest;
	private int mainScrollOffset;
	private int optionalScrollOffset;
	private int itemScrollOffset;
	private boolean updatingScrollBars;

	private static final class QuestSlot {
		final SimpleFrame root;
		final BackdropFrame iconContainer;
		final GlueButtonFrame button;
		final UIFrame selectedHighlight;
		final StringFrame titleText;
		final StringFrame statusText;
		CQuest quest;

		QuestSlot(final SimpleFrame root, final BackdropFrame iconContainer, final GlueButtonFrame button,
				final UIFrame selectedHighlight, final StringFrame titleText, final StringFrame statusText) {
			this.root = root;
			this.iconContainer = iconContainer;
			this.button = button;
			this.selectedHighlight = selectedHighlight;
			this.titleText = titleText;
			this.statusText = statusText;
		}
	}

	private static final class ItemDisplayLine {
		final String text;
		final Color color;

		ItemDisplayLine(final String text, final Color color) {
			this.text = text;
			this.color = color;
		}
	}

	public QuestDialogUI(final GameUI rootFrame, final Viewport uiViewport, final War3MapViewer war3MapViewer,
			final Runnable onClose) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.war3MapViewer = war3MapViewer;

		this.textCompleted = getDecoratedString("QUESTCOMPLETED", "Completed");
		this.textFailed = getDecoratedString("QUESTFAILED", "Failed");
		this.textUndiscoveredQuest = getDecoratedString("UNDISCOVERED_QUEST", "Undiscovered Quest");
		this.textDefeatConditions = getDecoratedString("QUESTDEFEATCONDITIONS", "Defeat Conditions");
		this.undiscoveredIconTexture = rootFrame.loadTexture(rootFrame.trySkinField("UndiscoveredQuestIcon"));

		this.questDialog = (SimpleFrame) rootFrame.createFrame("QuestDialog", rootFrame, 0, 0);
		this.questDialog.addAnchor(new AnchorDefinition(FramePoint.TOP, 0,
				GameUI.convertY(uiViewport, DIALOG_TOP_OFFSET_FDF)));
		this.questDialog.setVisible(false);

		this.questTitleValue = (StringFrame) rootFrame.getFrameByName("QuestTitleValue", 0);
		this.questSubtitleValue = (StringFrame) rootFrame.getFrameByName("QuestSubtitleValue", 0);
		this.questMainContainer = (SimpleFrame) rootFrame.getFrameByName("QuestMainContainer", 0);
		this.questOptionalContainer = (SimpleFrame) rootFrame.getFrameByName("QuestOptionalContainer", 0);
		this.questDisplayBackdrop = (BackdropFrame) rootFrame.getFrameByName("QuestDisplayBackdrop", 0);
		this.questDetailsTitle = (StringFrame) rootFrame.getFrameByName("QuestDetailsTitle", 0);
		this.questItemListContainer = (SimpleFrame) rootFrame.getFrameByName("QuestItemListContainer", 0);
		this.questDisplay = (TextAreaFrame) rootFrame.getFrameByName("QuestDisplay", 0);
		this.questAcceptButton = (GlueTextButtonFrame) rootFrame.getFrameByName("QuestAcceptButton", 0);

		if (this.questDetailsTitle != null) {
			this.questDetailsTitle.setWidth(GameUI.convertX(uiViewport, 0.41f));
		}
		if (this.questAcceptButton != null) {
			this.questAcceptButton.setOnClick(onClose);
		}

		this.questButtonTitleFont = rootFrame.generateFont(QUEST_BUTTON_TITLE_FONT_FDF);
		this.questItemFont = rootFrame.generateFont(ITEM_FONT_FDF);

		this.mainScrollBar = createListScrollBar("QuestMainListScrollBar", this.questDialog, this.questMainContainer,
				-0.002f, new ScrollBarFrame.ScrollBarChangeListener() {
					@Override
					public void onChange(final GameUI gameUI, final Viewport viewport, final int newValue) {
						if (!QuestDialogUI.this.updatingScrollBars) {
							QuestDialogUI.this.mainScrollOffset = percentToOffset(newValue,
									QuestDialogUI.this.mainQuests.size(), VISIBLE_QUEST_ROWS);
							updateQuestColumn(QuestDialogUI.this.mainSlots, QuestDialogUI.this.mainQuests,
									QuestDialogUI.this.mainScrollOffset, QuestDialogUI.this.mainScrollBar);
							QuestDialogUI.this.questMainContainer.positionBounds(gameUI, viewport);
						}
					}
				});

		this.optionalScrollBar = createListScrollBar("QuestOptionalListScrollBar", this.questDialog,
				this.questOptionalContainer, -0.002f, new ScrollBarFrame.ScrollBarChangeListener() {
					@Override
					public void onChange(final GameUI gameUI, final Viewport viewport, final int newValue) {
						if (!QuestDialogUI.this.updatingScrollBars) {
							QuestDialogUI.this.optionalScrollOffset = percentToOffset(newValue,
									QuestDialogUI.this.optionalQuests.size(), VISIBLE_QUEST_ROWS);
							updateQuestColumn(QuestDialogUI.this.optionalSlots, QuestDialogUI.this.optionalQuests,
									QuestDialogUI.this.optionalScrollOffset, QuestDialogUI.this.optionalScrollBar);
							QuestDialogUI.this.questOptionalContainer.positionBounds(gameUI, viewport);
						}
					}
				});

		this.itemScrollBar = createListScrollBar("QuestItemListScrollBar", this.questDisplayBackdrop,
				this.questItemListContainer, 0.014f, new ScrollBarFrame.ScrollBarChangeListener() {
					@Override
					public void onChange(final GameUI gameUI, final Viewport viewport, final int newValue) {
						if (!QuestDialogUI.this.updatingScrollBars) {
							QuestDialogUI.this.itemScrollOffset = percentToOffset(newValue,
									QuestDialogUI.this.itemDisplayLines.size(), VISIBLE_ITEM_ROWS);
							updateItemLines();
							QuestDialogUI.this.questItemListContainer.positionBounds(gameUI, viewport);
						}
					}
				});

		for (int i = 0; i < VISIBLE_QUEST_ROWS; i++) {
			this.mainSlots[i] = createQuestSlot(this.questMainContainer, i);
			this.optionalSlots[i] = createQuestSlot(this.questOptionalContainer, i);
		}

		final float shadowOffX = GameUI.convertX(uiViewport, 0.002f);
		final float shadowOffY = GameUI.convertY(uiViewport, -0.002f);
		final float itemRowHeight = GameUI.convertY(uiViewport, ITEM_ROW_HEIGHT_FDF);
		final float itemLeftOffset = GameUI.convertX(uiViewport, ITEM_TEXT_LEFT_FDF);
		final float itemWidth = GameUI.convertX(uiViewport, ITEM_TEXT_WIDTH_FDF);
		for (int i = 0; i < VISIBLE_ITEM_ROWS; i++) {
			final StringFrame line = new StringFrame("QuestItemLine" + i, this.questItemListContainer, COLOR_WHITE,
					TextJustify.LEFT, TextJustify.MIDDLE, this.questItemFont, "", COLOR_WHITE, COLOR_DISABLED);
			line.setFontShadowColor(COLOR_SHADOW);
			line.setFontShadowOffsetX(shadowOffX);
			line.setFontShadowOffsetY(shadowOffY);
			line.setWidth(itemWidth);
			line.setHeight(itemRowHeight);
			line.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.questItemListContainer, FramePoint.TOPLEFT,
					itemLeftOffset, -(i * itemRowHeight)));
			line.setVisible(false);
			this.questItemListContainer.add(line);
			this.itemLineFrames[i] = line;
		}

		populateMissionHeader();
		this.questDialog.positionBounds(rootFrame, uiViewport);
	}

	private ScrollBarFrame createListScrollBar(final String templateName, final UIFrame parent,
			final UIFrame anchorTarget, final float rightOffsetFdf,
			final ScrollBarFrame.ScrollBarChangeListener listener) {
		final ScrollBarFrame bar = (ScrollBarFrame) this.rootFrame.createFrame(templateName, parent, 0, 0);
		bar.clearFramePointAssignments();
		final float offsetX = GameUI.convertX(this.uiViewport, rightOffsetFdf);
		bar.addSetPoint(new SetPoint(FramePoint.TOPRIGHT, anchorTarget, FramePoint.TOPRIGHT, offsetX, 0));
		bar.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, anchorTarget, FramePoint.BOTTOMRIGHT, offsetX, 0));
		bar.setMinValue(0);
		bar.setMaxValue(100);
		bar.setStepSize(20);
		bar.setChangeListener(listener);
		bar.setVisible(false);
		if (parent instanceof SimpleFrame) {
			((SimpleFrame) parent).add(bar);
		}
		else if (parent instanceof BackdropFrame) {
			((BackdropFrame) parent).add(bar);
		}
		return bar;
	}

	private QuestSlot createQuestSlot(final SimpleFrame container, final int rowIndex) {
		final SimpleFrame slotRoot = (SimpleFrame) this.rootFrame.createFrame("QuestListItem", container, 0, 0);
		container.add(slotRoot);

		final BackdropFrame iconContainer = (BackdropFrame) this.rootFrame.getFrameByName("QuestListItemIconContainer",
				0);
		final GlueButtonFrame button = (GlueButtonFrame) this.rootFrame.getFrameByName("QuestListItemButton", 0);
		final StringFrame statusText = (StringFrame) this.rootFrame.getFrameByName("QuestListItemComplete", 0);
		final StringFrame oldTitleText = (StringFrame) this.rootFrame.getFrameByName("QuestListItemTitle", 0);

		final float rowHeight = GameUI.convertY(this.uiViewport, QUEST_ROW_HEIGHT_FDF);
		final float rowStep = GameUI.convertY(this.uiViewport, QUEST_ROW_HEIGHT_FDF + QUEST_ROW_GAP_FDF);
		slotRoot.setWidth(GameUI.convertX(this.uiViewport, QUEST_CONTAINER_WIDTH_FDF));
		slotRoot.setHeight(rowHeight);
		slotRoot.clearFramePointAssignments();
		slotRoot.addSetPoint(new SetPoint(FramePoint.TOPLEFT, container, FramePoint.TOPLEFT, 0, -(rowIndex * rowStep)));

		// Clear template Width/Height inherited from QuestButtonTemplate so the button stretches
		// between QuestListItemIconContainer.TOPRIGHT and QuestListItem.BOTTOMRIGHT.
		button.setWidth(0);
		button.setHeight(0);
		button.setHighlightOnMouseOver(true);

		final float highlightInset = GameUI.convertX(this.uiViewport, QUEST_BUTTON_HIGHLIGHT_INSET_FDF);
		if (button.getControlMouseOverHighlight() != null) {
			button.getControlMouseOverHighlight().setSetAllPoints(true, highlightInset);
		}
		final UIFrame selectedHighlight = this.rootFrame.createFrame("QuestButtonMouseOverHighlightTemplate", button, 0,
				0);
		selectedHighlight.setSetAllPoints(true, highlightInset);
		selectedHighlight.setVisible(false);
		button.setOverlayFrame(selectedHighlight);

		// Replace QuestListItemTitle with crisp 0.0105 font positioned in the upper-left of the button
		// (matching original WC3 and leaving space below for "Completed" / "Failed").
		if (oldTitleText != null) {
			slotRoot.remove(oldTitleText);
		}
		final StringFrame titleText = new StringFrame("QuestListItemTitleSlot" + rowIndex, slotRoot, COLOR_WHITE,
				TextJustify.LEFT, TextJustify.TOP, this.questButtonTitleFont, "", COLOR_WHITE, COLOR_DISABLED);
		titleText.setFontShadowColor(COLOR_SHADOW);
		titleText.setFontShadowOffsetX(GameUI.convertX(this.uiViewport, 0.002f));
		titleText.setFontShadowOffsetY(GameUI.convertY(this.uiViewport, -0.002f));
		titleText.addSetPoint(new SetPoint(FramePoint.TOPLEFT, button, FramePoint.TOPLEFT,
				GameUI.convertX(this.uiViewport, QUEST_BUTTON_TEXT_LEFT_FDF), 0));
		slotRoot.add(titleText);

		if (statusText != null) {
			statusText.setWidth(GameUI.convertX(this.uiViewport, 0.14f));
			statusText.clearFramePointAssignments();
			statusText.addSetPoint(new SetPoint(FramePoint.BOTTOMLEFT, button, FramePoint.BOTTOMLEFT,
					GameUI.convertX(this.uiViewport, QUEST_BUTTON_TEXT_LEFT_FDF),
					GameUI.convertY(this.uiViewport, QUEST_BUTTON_STATUS_BOTTOM_FDF)));
			statusText.setVisible(false);
		}

		final QuestSlot slot = new QuestSlot(slotRoot, iconContainer, button, selectedHighlight, titleText, statusText);
		button.setOnClick(new Runnable() {
			@Override
			public void run() {
				if ((slot.quest != null) && slot.quest.isDiscovered()) {
					selectQuest(slot.quest);
				}
			}
		});
		slotRoot.setVisible(false);
		return slot;
	}

	private void populateMissionHeader() {
		String headerTitle = "";
		String headerSubtitle = "";
		final War3MapW3i w3i = this.war3MapViewer.getLastLoadedMapInformation();
		if (w3i != null) {
			headerTitle = resolveMapText(w3i.getLoadingScreenTitle());
			headerSubtitle = resolveMapText(w3i.getLoadingScreenSubtitle());
			if (headerTitle.isEmpty() && headerSubtitle.isEmpty()) {
				headerSubtitle = resolveMapText(w3i.getName());
			}
		}
		if (headerTitle.isEmpty() && headerSubtitle.isEmpty() && (this.war3MapViewer.getMapConfig() != null)) {
			headerSubtitle = resolveMapText(this.war3MapViewer.getMapConfig().getMapName());
		}
		if (this.questTitleValue != null) {
			this.rootFrame.setText(this.questTitleValue, headerTitle);
		}
		if (this.questSubtitleValue != null) {
			this.rootFrame.setText(this.questSubtitleValue, headerSubtitle);
		}
	}

	private String resolveMapText(final String raw) {
		if (raw == null) {
			return "";
		}
		final String trimmed = raw.trim();
		if (trimmed.isEmpty()) {
			return "";
		}
		try {
			final String resolved = this.rootFrame.getTrigStr(trimmed);
			return resolved != null ? resolved.trim() : "";
		}
		catch (final Exception exc) {
			return trimmed;
		}
	}

	private String getDecoratedString(final String key, final String fallback) {
		final String value = this.rootFrame.getTemplates().getDecoratedString(key);
		if ((value == null) || value.equals(key)) {
			return fallback;
		}
		return value;
	}

	public SimpleFrame getRootFrame() {
		return this.questDialog;
	}

	public boolean isVisible() {
		return this.questDialog.isVisible();
	}

	public void setVisible(final boolean visible) {
		this.questDialog.setVisible(visible);
		if (visible) {
			this.questDialog.positionBounds(this.rootFrame, this.uiViewport);
		}
	}

	public void refresh(final List<CQuest> registeredQuests, final List<CDefeatCondition> defeatConditions) {
		this.mainQuests.clear();
		this.optionalQuests.clear();
		this.cachedDefeatConditions.clear();
		if (defeatConditions != null) {
			this.cachedDefeatConditions.addAll(defeatConditions);
		}

		if (registeredQuests != null) {
			for (final CQuest quest : registeredQuests) {
				if ((quest == null) || !quest.isEnabled()) {
					continue;
				}
				if (quest.isRequired()) {
					this.mainQuests.add(quest);
				}
				else {
					this.optionalQuests.add(quest);
				}
			}
		}

		// Preserve selection if still valid & discovered; otherwise select first discovered main/optional quest
		if ((this.selectedQuest == null) || !this.selectedQuest.isEnabled() || !this.selectedQuest.isDiscovered()
				|| (!this.mainQuests.contains(this.selectedQuest)
						&& !this.optionalQuests.contains(this.selectedQuest))) {
			this.selectedQuest = findFirstDiscoveredQuest();
		}

		this.mainScrollOffset = clampScrollOffset(this.mainScrollOffset, this.mainQuests.size(), VISIBLE_QUEST_ROWS);
		this.optionalScrollOffset = clampScrollOffset(this.optionalScrollOffset, this.optionalQuests.size(),
				VISIBLE_QUEST_ROWS);

		syncScrollBar(this.mainScrollBar, this.mainScrollOffset, this.mainQuests.size(), VISIBLE_QUEST_ROWS);
		syncScrollBar(this.optionalScrollBar, this.optionalScrollOffset, this.optionalQuests.size(),
				VISIBLE_QUEST_ROWS);

		updateQuestColumn(this.mainSlots, this.mainQuests, this.mainScrollOffset, this.mainScrollBar);
		updateQuestColumn(this.optionalSlots, this.optionalQuests, this.optionalScrollOffset, this.optionalScrollBar);

		updateDetails(true);
		this.questDialog.positionBounds(this.rootFrame, this.uiViewport);
	}

	private CQuest findFirstDiscoveredQuest() {
		for (final CQuest quest : this.mainQuests) {
			if (quest.isDiscovered()) {
				return quest;
			}
		}
		for (final CQuest quest : this.optionalQuests) {
			if (quest.isDiscovered()) {
				return quest;
			}
		}
		return null;
	}

	private void selectQuest(final CQuest quest) {
		if (this.selectedQuest == quest) {
			return;
		}
		this.selectedQuest = quest;
		updateQuestColumn(this.mainSlots, this.mainQuests, this.mainScrollOffset, this.mainScrollBar);
		updateQuestColumn(this.optionalSlots, this.optionalQuests, this.optionalScrollOffset, this.optionalScrollBar);
		updateDetails(true);
		this.questDialog.positionBounds(this.rootFrame, this.uiViewport);
	}

	private void updateQuestColumn(final QuestSlot[] slots, final List<CQuest> quests, final int scrollOffset,
			final ScrollBarFrame scrollBar) {
		final boolean hasScrollBar = quests.size() > VISIBLE_QUEST_ROWS;
		final float slotWidthFdf = hasScrollBar ? (QUEST_CONTAINER_WIDTH_FDF - QUEST_SCROLLBAR_RESERVE_FDF)
				: QUEST_CONTAINER_WIDTH_FDF;
		final float slotWidth = GameUI.convertX(this.uiViewport, slotWidthFdf);
		final float maxTitleWidth = GameUI.convertX(this.uiViewport,
				slotWidthFdf - QUEST_ICON_WIDTH_FDF - QUEST_BUTTON_TEXT_LEFT_FDF - QUEST_BUTTON_TEXT_RIGHT_PAD_FDF);

		for (int i = 0; i < slots.length; i++) {
			final QuestSlot slot = slots[i];
			final int index = scrollOffset + i;
			if (index >= quests.size()) {
				slot.quest = null;
				slot.root.setVisible(false);
				continue;
			}
			final CQuest quest = quests.get(index);
			slot.quest = quest;
			slot.root.setVisible(true);
			slot.root.setWidth(slotWidth);

			final boolean discovered = quest.isDiscovered();
			final boolean completed = discovered && quest.isCompleted();
			final boolean failed = discovered && !completed && quest.isFailed();
			final boolean selected = discovered && (quest == this.selectedQuest);

			// Icon
			Texture iconTex = null;
			if (discovered && (quest.getIconPath() != null) && !quest.getIconPath().isEmpty()) {
				iconTex = this.rootFrame.loadTexture(quest.getIconPath());
			}
			if (iconTex == null) {
				iconTex = this.undiscoveredIconTexture;
			}
			if (slot.iconContainer != null) {
				slot.iconContainer.setBackground(iconTex);
			}

			// Button state backdrop (Active / Completed / Failed / Undiscovered)
			if (!discovered) {
				slot.button.setEnabled(false);
				slot.button.setForcedBackdrop(slot.button.getControlDisabledBackdrop());
			}
			else if (completed) {
				slot.button.setEnabled(true);
				slot.button.setForcedBackdrop(slot.button.getControlPushedBackdrop());
			}
			else if (failed) {
				slot.button.setEnabled(true);
				slot.button.setForcedBackdrop(slot.button.getControlDisabledPushedBackdrop());
			}
			else {
				slot.button.setEnabled(true);
				slot.button.setForcedBackdrop(slot.button.getControlBackdrop());
			}
			slot.selectedHighlight.setVisible(selected);

			// Title text
			final String rawTitle = discovered ? safeString(quest.getTitle()) : this.textUndiscoveredQuest;
			final String fittedTitle = truncateToWidth(rawTitle, this.questButtonTitleFont,
					this.rootFrame.getGlyphLayout(), maxTitleWidth);
			// Alone, the title is centred on the button (QuestListItemTitle's LEFT anchor); with a
			// "Completed" / "Failed" line under it, it moves up to leave that line room.
			final float titleLeft = GameUI.convertX(this.uiViewport, QUEST_BUTTON_TEXT_LEFT_FDF);
			slot.titleText.clearFramePointAssignments();
			if (completed || failed) {
				slot.titleText.setJustifyV(TextJustify.TOP);
				slot.titleText.setHeight(this.questButtonTitleFont.getLineHeight());
				slot.titleText.addSetPoint(new SetPoint(FramePoint.TOPLEFT, slot.button, FramePoint.TOPLEFT, titleLeft,
						GameUI.convertY(this.uiViewport, QUEST_BUTTON_TEXT_TOP_WITH_STATUS_FDF)));
			}
			else {
				slot.titleText.setJustifyV(TextJustify.MIDDLE);
				slot.titleText.addSetPoint(
						new SetPoint(FramePoint.TOPLEFT, slot.button, FramePoint.TOPLEFT, titleLeft, 0));
				slot.titleText.addSetPoint(
						new SetPoint(FramePoint.BOTTOMLEFT, slot.button, FramePoint.BOTTOMLEFT, titleLeft, 0));
			}
			// the frame keeps the right margin as slack: a title cut to maxTitleWidth must not wrap
			slot.titleText.setWidth(maxTitleWidth + GameUI.convertX(this.uiViewport, QUEST_BUTTON_TEXT_RIGHT_PAD_FDF));
			if (!discovered) {
				slot.titleText.setColor(COLOR_DISABLED);
			}
			else if (completed || failed) {
				slot.titleText.setColor(COLOR_COMPLETED_TEXT);
			}
			else {
				slot.titleText.setColor(COLOR_WHITE);
			}
			this.rootFrame.setText(slot.titleText, fittedTitle);

			// Status sub-label ("Completed" / "Failed")
			if (slot.statusText != null) {
				if (completed) {
					slot.statusText.setColor(COLOR_GOLD);
					this.rootFrame.setText(slot.statusText, this.textCompleted);
					slot.statusText.setVisible(true);
				}
				else if (failed) {
					slot.statusText.setColor(COLOR_FAILED_STATUS);
					this.rootFrame.setText(slot.statusText, this.textFailed);
					slot.statusText.setVisible(true);
				}
				else {
					slot.statusText.setVisible(false);
				}
			}
		}
		if (scrollBar != null) {
			scrollBar.setVisible(hasScrollBar);
		}
	}

	private void updateDetails(final boolean resetScroll) {
		this.itemDisplayLines.clear();
		if (resetScroll) {
			this.itemScrollOffset = 0;
		}

		if (this.selectedQuest == null) {
			if (!this.cachedDefeatConditions.isEmpty()) {
				if (this.questDetailsTitle != null) {
					this.rootFrame.setText(this.questDetailsTitle, this.textDefeatConditions);
				}
				final float itemWidth = GameUI.convertX(this.uiViewport, ITEM_TEXT_WIDTH_FDF);
				for (final CDefeatCondition cond : this.cachedDefeatConditions) {
					final String desc = safeString(cond.getDescription());
					if (!desc.isEmpty()) {
						final List<String> wrapped = wrapText("- " + desc, this.questItemFont,
								this.rootFrame.getGlyphLayout(), itemWidth, "  ");
						for (final String line : wrapped) {
							this.itemDisplayLines.add(new ItemDisplayLine(line, COLOR_WHITE));
						}
					}
				}
			}
			else if (this.questDetailsTitle != null) {
				this.rootFrame.setText(this.questDetailsTitle, "");
			}
			syncScrollBar(this.itemScrollBar, this.itemScrollOffset, this.itemDisplayLines.size(), VISIBLE_ITEM_ROWS);
			updateItemLines();
			if (this.questDisplay != null) {
				this.questDisplay.setItems(new ArrayList<String>(), this.rootFrame, this.uiViewport);
				this.questDisplay.scrollToTop(this.rootFrame, this.uiViewport);
			}
			return;
		}

		// Details title
		if (this.questDetailsTitle != null) {
			final StringBuilder detailsTitle = new StringBuilder(safeString(this.selectedQuest.getTitle()));
			if (this.selectedQuest.isCompleted()) {
				detailsTitle.append(" - ").append(this.textCompleted);
			}
			else if (this.selectedQuest.isFailed()) {
				detailsTitle.append(" - ").append(this.textFailed);
			}
			this.rootFrame.setText(this.questDetailsTitle, detailsTitle.toString());
		}

		// Quest requirements / items
		final float itemWidth = GameUI.convertX(this.uiViewport, ITEM_TEXT_WIDTH_FDF);
		for (final CQuestItem item : this.selectedQuest.getItems()) {
			final String desc = safeString(item.getDescription());
			if (desc.isEmpty()) {
				continue;
			}
			final String formatted = item.isCompleted()
					? ("- " + desc + " (" + this.textCompleted + ")")
					: ("- " + desc);
			final Color lineColor = item.isCompleted() ? COLOR_DISABLED : COLOR_WHITE;
			final List<String> wrapped = wrapText(formatted, this.questItemFont, this.rootFrame.getGlyphLayout(),
					itemWidth, "  ");
			for (final String line : wrapped) {
				this.itemDisplayLines.add(new ItemDisplayLine(line, lineColor));
			}
		}
		this.itemScrollOffset = clampScrollOffset(this.itemScrollOffset, this.itemDisplayLines.size(),
				VISIBLE_ITEM_ROWS);
		syncScrollBar(this.itemScrollBar, this.itemScrollOffset, this.itemDisplayLines.size(), VISIBLE_ITEM_ROWS);
		updateItemLines();

		// Quest description in TextAreaFrame
		if (this.questDisplay != null) {
			final float descAreaWidth = this.questDisplay.getRenderBounds().width > 0
					? this.questDisplay.getRenderBounds().width
					: GameUI.convertX(this.uiViewport, 0.395f);
			final float inset = this.questDisplay.getInset();
			final float scrollReserve = GameUI.convertX(this.uiViewport, 0.016f);
			final float wrapWidth = Math.max(1f, descAreaWidth - (inset * 2f) - scrollReserve);
			final BitmapFont descFont = this.questDisplay.getFrameFont() != null ? this.questDisplay.getFrameFont()
					: this.questItemFont;
			final List<String> wrappedDesc = wrapText(safeString(this.selectedQuest.getDescription()), descFont,
					this.rootFrame.getGlyphLayout(), wrapWidth, "");
			this.questDisplay.setItems(wrappedDesc, this.rootFrame, this.uiViewport);
			if (resetScroll) {
				this.questDisplay.scrollToTop(this.rootFrame, this.uiViewport);
			}
		}
	}

	private void updateItemLines() {
		for (int i = 0; i < VISIBLE_ITEM_ROWS; i++) {
			final StringFrame frame = this.itemLineFrames[i];
			final int index = this.itemScrollOffset + i;
			if (index < this.itemDisplayLines.size()) {
				final ItemDisplayLine line = this.itemDisplayLines.get(index);
				frame.setColor(line.color);
				this.rootFrame.setText(frame, line.text);
				frame.setVisible(true);
			}
			else {
				frame.setVisible(false);
			}
		}
	}

	public boolean scrolled(final float uiX, final float uiY, final float amountY) {
		if (!isVisible()) {
			return false;
		}
		final int step = amountY > 0 ? 1 : -1;
		if ((this.questMainContainer != null) && this.questMainContainer.getRenderBounds().contains(uiX, uiY)
				&& (this.mainQuests.size() > VISIBLE_QUEST_ROWS)) {
			this.mainScrollOffset = clampScrollOffset(this.mainScrollOffset + step, this.mainQuests.size(),
					VISIBLE_QUEST_ROWS);
			syncScrollBar(this.mainScrollBar, this.mainScrollOffset, this.mainQuests.size(), VISIBLE_QUEST_ROWS);
			updateQuestColumn(this.mainSlots, this.mainQuests, this.mainScrollOffset, this.mainScrollBar);
			this.questMainContainer.positionBounds(this.rootFrame, this.uiViewport);
			return true;
		}
		if ((this.questOptionalContainer != null) && this.questOptionalContainer.getRenderBounds().contains(uiX, uiY)
				&& (this.optionalQuests.size() > VISIBLE_QUEST_ROWS)) {
			this.optionalScrollOffset = clampScrollOffset(this.optionalScrollOffset + step, this.optionalQuests.size(),
					VISIBLE_QUEST_ROWS);
			syncScrollBar(this.optionalScrollBar, this.optionalScrollOffset, this.optionalQuests.size(),
					VISIBLE_QUEST_ROWS);
			updateQuestColumn(this.optionalSlots, this.optionalQuests, this.optionalScrollOffset,
					this.optionalScrollBar);
			this.questOptionalContainer.positionBounds(this.rootFrame, this.uiViewport);
			return true;
		}
		if ((this.questItemListContainer != null) && this.questItemListContainer.getRenderBounds().contains(uiX, uiY)
				&& (this.itemDisplayLines.size() > VISIBLE_ITEM_ROWS)) {
			this.itemScrollOffset = clampScrollOffset(this.itemScrollOffset + step, this.itemDisplayLines.size(),
					VISIBLE_ITEM_ROWS);
			syncScrollBar(this.itemScrollBar, this.itemScrollOffset, this.itemDisplayLines.size(), VISIBLE_ITEM_ROWS);
			updateItemLines();
			this.questItemListContainer.positionBounds(this.rootFrame, this.uiViewport);
			return true;
		}
		if ((this.questDisplay != null) && this.questDisplay.getRenderBounds().contains(uiX, uiY)) {
			this.questDisplay.scrollByWheel(this.rootFrame, this.uiViewport, amountY);
			return true;
		}
		return true;
	}

	private void syncScrollBar(final ScrollBarFrame scrollBar, final int offset, final int totalItems,
			final int visibleRows) {
		if (scrollBar == null) {
			return;
		}
		final int maxOffset = totalItems - visibleRows;
		if (maxOffset <= 0) {
			scrollBar.setVisible(false);
			return;
		}
		scrollBar.setVisible(true);
		final int percent = Math.round(((maxOffset - offset) / (float) maxOffset) * 100f);
		this.updatingScrollBars = true;
		try {
			scrollBar.setValue(this.rootFrame, this.uiViewport, percent);
		}
		finally {
			this.updatingScrollBars = false;
		}
	}

	private static int clampScrollOffset(final int offset, final int totalItems, final int visibleRows) {
		final int maxOffset = Math.max(0, totalItems - visibleRows);
		return Math.max(0, Math.min(offset, maxOffset));
	}

	private static int percentToOffset(final int percent, final int totalItems, final int visibleRows) {
		final int maxOffset = Math.max(0, totalItems - visibleRows);
		if (maxOffset == 0) {
			return 0;
		}
		final int offset = Math.round(((100 - percent) / 100f) * maxOffset);
		return Math.max(0, Math.min(offset, maxOffset));
	}

	private static String safeString(final String s) {
		return s != null ? s : "";
	}

	private static String stripColorCodes(final String s) {
		final StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			if ((c == '|') && ((i + 1) < s.length())) {
				final char next = s.charAt(i + 1);
				if (((next == 'c') || (next == 'C')) && ((i + 9) < s.length())) {
					i += 9;
					continue;
				}
				if ((next == 'r') || (next == 'R')) {
					i += 1;
					continue;
				}
			}
			out.append(c);
		}
		return out.toString();
	}

	private static String updateActiveColorTag(final String chunk, String activeColorTag) {
		for (int i = 0; i < chunk.length(); i++) {
			if ((chunk.charAt(i) == '|') && ((i + 1) < chunk.length())) {
				final char next = chunk.charAt(i + 1);
				if (((next == 'c') || (next == 'C')) && ((i + 9) < chunk.length())) {
					activeColorTag = chunk.substring(i, i + 10);
					i += 9;
				}
				else if ((next == 'r') || (next == 'R')) {
					activeColorTag = null;
					i += 1;
				}
			}
		}
		return activeColorTag;
	}

	private static float measureWidth(final BitmapFont font, final GlyphLayout glyphLayout, final String text) {
		if ((font == null) || text.isEmpty()) {
			return 0f;
		}
		glyphLayout.setText(font, stripColorCodes(text));
		return glyphLayout.width;
	}

	private static String truncateToWidth(final String text, final BitmapFont font, final GlyphLayout glyphLayout,
			final float maxWidth) {
		final String singleLine = text.replace("|n", " ").replace("|N", " ").replace('\r', ' ').replace('\n', ' ')
				.trim();
		if ((maxWidth <= 0f) || (measureWidth(font, glyphLayout, singleLine) <= maxWidth)) {
			return singleLine;
		}
		final String ellipsis = "...";
		final float ellipsisWidth = measureWidth(font, glyphLayout, ellipsis);
		if (ellipsisWidth >= maxWidth) {
			return ellipsis;
		}
		final String plain = stripColorCodes(singleLine);
		int end = plain.length();
		while ((end > 0) && ((measureWidth(font, glyphLayout, plain.substring(0, end)) + ellipsisWidth) > maxWidth)) {
			end--;
		}
		return plain.substring(0, end).trim() + ellipsis;
	}

	private static List<String> wrapText(final String text, final BitmapFont font, final GlyphLayout glyphLayout,
			final float maxWidth, final String continuationPrefix) {
		final List<String> result = new ArrayList<>();
		if ((text == null) || text.isEmpty()) {
			return result;
		}
		final String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("|n", "\n").replace("|N",
				"\n");
		final String[] paragraphs = normalized.split("\n", -1);
		String activeColor = null;
		for (int p = 0; p < paragraphs.length; p++) {
			final String paragraph = paragraphs[p];
			if (paragraph.isEmpty()) {
				result.add("");
				continue;
			}
			final String[] words = paragraph.split(" ", -1);
			final StringBuilder currentLine = new StringBuilder();
			if (activeColor != null) {
				currentLine.append(activeColor);
			}
			boolean lineHasWord = false;
			for (int w = 0; w < words.length; w++) {
				final String word = words[w];
				final String candidate = lineHasWord ? (currentLine.toString() + " " + word)
						: (currentLine.toString() + word);
				if (lineHasWord && (maxWidth > 0f) && (measureWidth(font, glyphLayout, candidate) > maxWidth)) {
					final String finished = currentLine.toString();
					activeColor = updateActiveColorTag(finished, activeColor);
					result.add(finished);
					currentLine.setLength(0);
					if (activeColor != null) {
						currentLine.append(activeColor);
					}
					if (!continuationPrefix.isEmpty()) {
						currentLine.append(continuationPrefix);
					}
					currentLine.append(word);
					lineHasWord = true;
				}
				else {
					if (lineHasWord) {
						currentLine.append(' ');
					}
					currentLine.append(word);
					lineHasWord = true;
				}
			}
			final String finished = currentLine.toString();
			activeColor = updateActiveColorTag(finished, activeColor);
			result.add(finished);
		}
		return result;
	}
}
