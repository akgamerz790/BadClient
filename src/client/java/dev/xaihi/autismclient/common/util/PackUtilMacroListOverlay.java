package dev.xaihi.autismclient.common.util;

import dev.xaihi.autismclient.common.gui.packui.PackUiAssets;
import dev.xaihi.autismclient.common.gui.packui.PackUiButton;
import dev.xaihi.autismclient.common.gui.packui.PackUiHeaderControls;
import dev.xaihi.autismclient.common.gui.packui.PackUiInsets;
import dev.xaihi.autismclient.common.gui.packui.PackUiLabel;
import dev.xaihi.autismclient.common.gui.packui.PackUiRenderContext;
import dev.xaihi.autismclient.common.gui.packui.PackUiRow;
import dev.xaihi.autismclient.common.gui.packui.PackUiSurface;
import dev.xaihi.autismclient.common.gui.packui.PackUiScrollbar;
import dev.xaihi.autismclient.common.gui.packui.PackUiSmoothScroll;
import dev.xaihi.autismclient.common.gui.packui.PackUiSizing;
import dev.xaihi.autismclient.common.gui.packui.PackUiText;
import dev.xaihi.autismclient.common.gui.packui.PackUiTextField;
import dev.xaihi.autismclient.common.gui.packui.PackUiTheme;
import dev.xaihi.autismclient.common.gui.packui.PackUiTone;
import dev.xaihi.autismclient.common.gui.packui.PackUiViewport;
import dev.xaihi.autismclient.common.gui.packui.PackUiViewportSlot;
import dev.xaihi.autismclient.common.gui.packui.PackUiWindowNode;
import dev.xaihi.autismclient.common.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PackUtilMacroListOverlay extends PackUtilOverlayBase {
    public static final Minecraft MC = Minecraft.getInstance();
    public static final int MIN_PANEL_WIDTH = 258;
    public static final int ROW_HEIGHT = 18;
    public static final int MAX_VISIBLE_ROWS = 6;
    public static final int PAD = 6;
    public static final int HEADER_CONTROL = 12;
    public static final int HEADER_ARROW_WIDTH = 10;
    public static final int HEADER_ARROW_GAP = 3;
    public static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0f;
    public static final long METEOR_CACHE_MS = 2000L;

    public static final int ROW_BUTTON_HEIGHT = 14;
    public static final int DELETE_BUTTON_WIDTH = 16;
    public static final int ROW_BUTTON_GAP = 4;
    public static final int ROW_BUTTON_PADDING = 2;
    public static final int VIEWPORT_BORDER = 1;

    public final Font textRenderer;
    public final PackUtilMacroEditorOverlay activeEditor;
    public final PackUiTheme theme = new PackUiTheme();
    public final PackUiWindowNode windowNode = new PackUiWindowNode("Macro Library");
    public final PackUiSurface surface = new PackUiSurface(theme, windowNode);
    public final PackUiTextField searchField = new PackUiTextField();
    public final PackUiTextField pasteNameField = new PackUiTextField();
    public final PackUiViewportSlot listSlot = new PackUiViewportSlot();

    public int panelX = 500;
    public int panelY = 250;
    public int panelWidth = MIN_PANEL_WIDTH;
    public int panelHeight = 220;
    public boolean visible = false;
    public boolean collapsed = false;
    public boolean dragging = false;
    public float dragOffsetX;
    public float dragOffsetY;
    public float pressStartUiX;
    public float pressStartUiY;
    public int pressStartPanelX;
    public int pressStartPanelY;
    public boolean dragMoved = false;
    public final PackUiSmoothScroll listScroll = new PackUiSmoothScroll();
    public boolean pasteMode = false;
    public float closeHover = 0.0f;
    public float closeVisibility = 1.0f;
    public int contentHeight = 0;
    public boolean scrollbarDragging = false;
    public int scrollbarGrabOffset = 0;

    public int lastKnownMacroCount = -1;
    public boolean lastKnownPasteMode = false;
    public boolean needsUiRebuild = true;

    public final List<ClickRegion> clickRegions = new ArrayList<>();
    public List<DisplayItem> currentItems = List.of();
    public List<PackUtilMacro> cachedMeteorMacros = Collections.emptyList();
    public long meteorMacroCacheTime = 0L;

    public PackUtilMacroListOverlay(Font textRenderer) {
        this(textRenderer, null);
    }

    public PackUtilMacroListOverlay(Font textRenderer, PackUtilMacroEditorOverlay activeEditor) {
        this.textRenderer = textRenderer;
        this.activeEditor = activeEditor;
        buildUi();
    }

    public void buildUi() {
        panelWidth = panelMinimumWidth();
        windowNode.setCenterTitle(false);
        windowNode.setTitleTone(PackUiTone.LABEL);
        windowNode.setTitleAreaInsets(panelPadding() + 2, panelPadding() + headerControlSize() + headerArrowWidth() + headerArrowGap() + 12);
        windowNode.content().setGap(windowContentGap()).setPadding(PackUiInsets.all(panelPadding()));

        searchField
            .setPlaceholder("Search macros...")
            .setFieldHeight(searchFieldHeight())
            .setGrowX(true)
            .setOnChange(text -> {
                listScroll.jumpTo(0, 0);
                rebuildData();
            });

        pasteNameField
            .setPlaceholder("New macro name...")
            .setFieldHeight(searchFieldHeight())
            .setGrowX(true)
            .setOnSubmit(text -> pasteMacroFromClipboard());

        rebuildUi();
    }

    public void rebuildData() {
        PackUtilLANSync sync = PackUtilLANSync.getInstance();
        currentItems = buildDisplayItems(
            PackUtilMacroManager.get().getAll(),
            getCachedMeteorMacros(),
            sync.getAllRemoteMacros(),
            sync.getAllRemoteMeteorMacros(),
            sync
        );
        contentHeight = currentItems.size() * rowHeight();
    }

    public void rebuildUi() {
        windowNode.content().clearChildren();
        rebuildData();
        windowNode.setTitle("Macro Library [" + PackUtilMacroManager.get().getAll().size() + "]");

        windowNode.content().add(searchField);

        if (currentItems.isEmpty()) {
            windowNode.content().add(new PackUiLabel("No macros match the current filter.", PackUiTone.MUTED).setTrimToBounds(true));
        } else {
            listSlot.setPreferredHeight(computeViewportHeight(currentItems.size()));
            windowNode.content().add(listSlot);
        }

        if (pasteMode) {
            windowNode.content().add(pasteNameField);

            PackUiRow actions = new PackUiRow().setGap(actionRowGap());
            actions.add(new PackUiButton("Paste", PackUiButton.Variant.SUCCESS, this::pasteMacroFromClipboard).setGrowX(true).setButtonHeight(actionButtonHeight()));
            actions.add(new PackUiButton("Cancel", PackUiButton.Variant.SECONDARY, this::cancelPasteMode).setGrowX(true).setButtonHeight(actionButtonHeight()));
            windowNode.content().add(actions);
            windowNode.content().add(new PackUiLabel("Paste a copied macro under a new name.", PackUiTone.MUTED).setTrimToBounds(true));
        } else {
            PackUiRow actions = new PackUiRow().setGap(actionRowGap());
            actions.add(new PackUiButton("Create New", PackUiButton.Variant.SECONDARY, this::openCreateNew).setGrowX(true).setButtonHeight(actionButtonHeight()));
            actions.add(new PackUiButton("Paste", PackUiButton.Variant.SECONDARY, this::beginPasteMode).setGrowX(true).setButtonHeight(actionButtonHeight()));
            windowNode.content().add(actions);
        }
    }

    public int computeViewportHeight(int itemCount) {
        int visibleRows = Math.max(3, Math.min(maxVisibleRows(), itemCount));
        return visibleRows * rowHeight() + (VIEWPORT_BORDER * 2);
    }

    public List<PackUtilMacro> getCachedMeteorMacros() {
        long now = System.currentTimeMillis();
        if (now - meteorMacroCacheTime > METEOR_CACHE_MS) {
            cachedMeteorMacros = MeteorMacroAdapter.getMeteorMacros();
            meteorMacroCacheTime = now;
        }
        return cachedMeteorMacros;
    }

    public PackUtilMacroEditorOverlay getActiveEditor() {
        return activeEditor;
    }

    public void saveState() {
        PackUtilSharedState shared = PackUtilSharedState.get();
        shared.setMacroListOverlayVisible(visible);
        shared.setMacroListOverlayX(panelX);
        shared.setMacroListOverlayY(panelY);
        shared.setMacroListOverlayScrollOffset(listScroll.targetOffset());
        shared.setMacroListOverlaySearch(searchField.text());
        saveLayout();
    }

    public void restoreState() {
        PackUtilSharedState shared = PackUtilSharedState.get();
        restoreLayout();
        this.visible = shared.isMacroListOverlayVisible();
        this.panelX = shared.getMacroListOverlayX();
        this.panelY = shared.getMacroListOverlayY();
        listScroll.restore(shared.getMacroListOverlayScrollOffset());
        searchField.setText(shared.getMacroListOverlaySearch());
        needsUiRebuild = true;
        windowNode.restoreShowBody(!collapsed);
    }

    @Override
    public String getOverlayId() {
        return "packutil-macrolist";
    }

    @Override
    public int getMinWidth() {
        return panelMinimumWidth();
    }

    @Override
    public int getMinHeight() {
        return theme.headerHeight() + searchFieldHeight() + actionButtonHeight() + (panelPadding() * 2) + windowContentGap();
    }

    @Override
    public PackUtilWindowLayout getBounds() {
        return new PackUtilWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
    }

    @Override
    public void setBounds(PackUtilWindowLayout bounds) {
        if (bounds == null) return;
        PackUtilWindowLayout clamped = clampToViewport(bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible && activeEditor != null && activeEditor.isVisible()) {
            this.visible = false;
            saveState();
            PackUtilOverlayManager.get().bringToFront(activeEditor);
            return;
        }
        this.visible = visible;
        if (!visible) {
            surface.clearFocusedTextInputs();
            dragging = false;
            dragMoved = false;
        } else {
            needsUiRebuild = true;
            windowNode.restoreShowBody(!collapsed);
            PackUtilOverlayManager.get().bringToFront(this);
        }
        saveState();
    }

    public void toggle() {
        setVisible(!visible);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        dragging = false;
        dragMoved = false;
        if (collapsed) {
            surface.clearFocusedTextInputs();
        }
        saveState();
    }

    @Override
    public boolean hasTextFieldFocused() {
        return surface.hasFocusedTextInput();
    }

    @Override
    public void clearTextFieldFocus() {
        surface.clearFocusedTextInputs();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        PackUiViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mouseX);
        float uiY = viewport.toUiY(mouseY);
        return uiX >= panelX && uiX <= panelX + panelWidth
            && uiY >= panelY && uiY <= panelY + panelHeight;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        PackUiViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mouseX);
        float uiY = viewport.toUiY(mouseY);
        return isOverHeaderUi(uiX, uiY) && !isOverCloseButton(uiX, uiY);
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible || MC == null || MC.font == null) return;

        int currentMacroCount = PackUtilMacroManager.get().getAll().size();
        if (needsUiRebuild || currentMacroCount != lastKnownMacroCount || pasteMode != lastKnownPasteMode) {
            rebuildUi();
            lastKnownMacroCount = currentMacroCount;
            lastKnownPasteMode = pasteMode;
            needsUiRebuild = false;
        } else {

            windowNode.setTitle("Macro Library [" + currentMacroCount + "]");
        }

        PackUiViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        boolean active = PackUtilOverlayManager.get().isFocusedOverlay(this) || PackUtilOverlayManager.get().isTopOverlay(this);
        boolean headerHovered = isOverHeaderUi(uiMouseX, uiMouseY);

        PackUiRenderContext metrics = new PackUiRenderContext(context, MC.font, viewport, theme, uiMouseX, uiMouseY, delta);
        windowNode.setShowBody(!collapsed);
        windowNode.setActive(active);
        windowNode.setHeaderHovered(headerHovered);

        panelHeight = Math.round(windowNode.preferredHeight(metrics, panelWidth));
        panelHeight = Math.max(theme.headerHeight(), panelHeight);
        windowNode.setBounds(panelX, panelY, panelWidth, panelHeight);

        surface.render(context, mouseX, mouseY, delta);
        if (panelHeight > theme.headerHeight() + 1 && !currentItems.isEmpty()) {
            renderlistViewport(context, viewport, uiMouseX, uiMouseY, delta, active);
        } else {
            clickRegions.clear();
        }
        renderHeaderControls(context, viewport, uiMouseX, uiMouseY, delta, active);
    }

    public void renderHeaderControls(GuiGraphicsExtractor context, PackUiViewport viewport, float uiMouseX, float uiMouseY, float delta, boolean active) {
        closeVisibility = animate(closeVisibility, 1.0f, delta);
        closeHover = animate(closeHover, isOverCloseButton(uiMouseX, uiMouseY) ? 1.0f : 0.0f, delta);
        viewport.push(context);
        try {
            int arrowX = collapseArrowX();
            int closeX = closeButtonX();
            int controlY = controlButtonY();
            drawCollapseArrow(context, arrowX, controlY, active);
            drawCloseButton(context, closeX, controlY, headerControlSize(), headerControlSize(), closeHover, active, closeVisibility);
        } finally {
            viewport.pop(context);
        }
    }

    public void renderlistViewport(GuiGraphicsExtractor context, PackUiViewport viewport, float uiMouseX, float uiMouseY, float delta, boolean active) {
        clickRegions.clear();

        int viewX = Math.round(listSlot.x());
        int viewY = Math.round(listSlot.y());
        int viewW = Math.round(listSlot.width());
        int viewH = Math.round(listSlot.height());
        if (viewW <= 2 || viewH <= 2) return;

        int maxScroll = Math.max(0, contentHeight - Math.max(0, viewH - (VIEWPORT_BORDER * 2)));
        int drawScroll = listScroll.tick(delta, maxScroll);

        float alpha = active ? 1.0f : 0.56f;
        PackUiText.fill(context, viewX, viewY, viewX + viewW, viewY + viewH, PackUiRenderContext.applyAlpha(theme.listFill(), alpha));
        PackUiText.fill(context, viewX, viewY, viewX + viewW, viewY + 1, PackUiRenderContext.applyAlpha(theme.borderSoft(), alpha));
        PackUiText.fill(context, viewX, viewY + viewH - 1, viewX + viewW, viewY + viewH, PackUiRenderContext.applyAlpha(theme.borderSoft(), alpha));
        PackUiText.fill(context, viewX, viewY, viewX + 1, viewY + viewH, PackUiRenderContext.applyAlpha(theme.borderSoft(), alpha));
        PackUiText.fill(context, viewX + viewW - 1, viewY, viewX + viewW, viewY + viewH, PackUiRenderContext.applyAlpha(theme.borderSoft(), alpha));

        viewport.enableScissor(context, viewX + VIEWPORT_BORDER, viewY + VIEWPORT_BORDER, viewX + viewW - VIEWPORT_BORDER, viewY + viewH - VIEWPORT_BORDER);
        try {
            PackUiRenderContext rowContext = new PackUiRenderContext(context, textRenderer, viewport, theme, uiMouseX, uiMouseY, delta, alpha);
            int baseY = viewY + VIEWPORT_BORDER - drawScroll;
            for (int i = 0; i < currentItems.size(); i++) {
                int rowY = baseY + (i * rowHeight());
                if (rowY + rowHeight() <= viewY + VIEWPORT_BORDER || rowY >= viewY + viewH - VIEWPORT_BORDER) continue;
                DisplayItem item = currentItems.get(i);
                if (item.type == ItemType.SECTION_HEADER) {
                    renderSectionHeader(context, rowContext, item, viewX + VIEWPORT_BORDER, rowY, viewW - (VIEWPORT_BORDER * 2));
                } else {
                    renderMacroRow(context, rowContext, item, viewX + VIEWPORT_BORDER, rowY, viewW - (VIEWPORT_BORDER * 2));
                }
            }
        } finally {
            viewport.disableScissor(context);
        }

        renderScrollbar(context, viewX, viewY, viewW, viewH, uiMouseX, uiMouseY, active ? 1.0f : 0.56f);
    }

    public PackUiScrollbar.Metrics getScrollbarMetrics(int viewX, int viewY, int viewW, int viewH) {
        return PackUiScrollbar.compute(contentHeight, Math.max(0, viewH - (VIEWPORT_BORDER * 2)), viewX + viewW - 5, viewY + VIEWPORT_BORDER, 3, Math.max(0, viewH - (VIEWPORT_BORDER * 2)), listScroll.tick(0.0f, Math.max(0, contentHeight - Math.max(0, viewH - (VIEWPORT_BORDER * 2)))));
    }

    public void renderScrollbar(GuiGraphicsExtractor context, int viewX, int viewY, int viewW, int viewH, float uiMouseX, float uiMouseY, float alpha) {
        PackUiScrollbar.Metrics metrics = getScrollbarMetrics(viewX, viewY, viewW, viewH);
        if (!metrics.hasScroll()) return;
        PackUiScrollbar.draw(context, metrics, metrics.contains(uiMouseX, uiMouseY), scrollbarDragging);
    }

    public void renderSectionHeader(GuiGraphicsExtractor context, PackUiRenderContext rowContext, DisplayItem item, int x, int y, int width) {
        int textY = PackUiSizing.alignTextY(y, rowHeight(), theme.fontHeight(PackUiTone.LABEL), theme.bodyTextNudge());
        PackUiText.fill(context, x + sectionHeaderInset(), y + rowHeight() - 2, x + width - sectionHeaderInset(), y + rowHeight() - 1, rowContext.applyAlpha(0x42FFFFFF));
        PackUiText.draw(
            context,
            textRenderer,
            item.label,
            theme.fontFor(PackUiTone.LABEL),
            rowContext.applyAlpha(item.color),
            x + sectionHeaderInset(),
            textY,
            false
        );
    }

    public void renderMacroRow(GuiGraphicsExtractor context, PackUiRenderContext rowContext, DisplayItem item, int x, int y, int width) {
        boolean hovered = uiContains(x, y, width, rowHeight(), rowContext.mouseX(), rowContext.mouseY());
        if (hovered) {
            PackUiText.fill(context, x, y, x + width, y + rowHeight(), rowContext.applyAlpha(0x1AFF4A4A));
        }

        List<RowButton> buttons = buildRowButtons(item);
        int buttonCursor = x + width - rowTextInset();
        int buttonY = y + rowButtonTopInset();
        for (int i = buttons.size() - 1; i >= 0; i--) {
            RowButton button = buttons.get(i);
            buttonCursor -= button.width;
            drawRowButton(context, rowContext, buttonCursor, buttonY, button.width, button);
            clickRegions.add(new ClickRegion(buttonCursor, buttonY, button.width, rowButtonHeight(), button.action));
            buttonCursor -= rowButtonGap();
        }

        int textLeft = x + rowTextInset();
        if (item.type == ItemType.LOCAL_MACRO) {
            boolean running = MacroExecutor.isMacroRunning(item.macro.name);
            int dotColor = running ? rowContext.applyAlpha(0xFF33D968) : rowContext.applyAlpha(0xFF656565);
            int dotTop = y + PackUiSizing.alignMiddle(0, rowHeight(), rowDotSize());
            PackUiText.fill(context, textLeft, dotTop, textLeft + rowDotSize(), dotTop + rowDotSize(), dotColor);
            textLeft += rowTextInset() + 2;
        }

        int rightLimit = Math.max(textLeft + 20, buttonCursor - 2);
        int labelWidth = Math.max(20, rightLimit - textLeft);
        String primaryText = switch (item.type) {
            case LOCAL_MACRO -> item.macro.name;
            case REMOTE_MACRO -> item.label;
            case METEOR_MACRO -> "Meteor: " + item.macro.name;
            default -> item.label;
        };
        String secondaryText = switch (item.type) {
            case LOCAL_MACRO -> item.macro.actions.size() + " steps";
            case REMOTE_MACRO -> item.remoteSource == null || item.remoteSource.isBlank() ? "" : item.remoteSource;
            case METEOR_MACRO -> item.macro.actions.size() + " steps";
            default -> "";
        };

        int primaryColor = switch (item.type) {
            case LOCAL_MACRO -> rowContext.applyAlpha(0xFFF3ECE7);
            case REMOTE_MACRO -> rowContext.applyAlpha(item.remoteMeteor ? 0xFFD6D2FF : 0xFFFFD0A6);
            case METEOR_MACRO -> rowContext.applyAlpha(0xFFD8E6FF);
            default -> rowContext.applyAlpha(theme.color(PackUiTone.BODY));
        };
        int secondaryColor = rowContext.applyAlpha(theme.color(PackUiTone.MUTED));
        int textY = PackUiSizing.alignTextY(y, rowHeight(), theme.fontHeight(PackUiTone.BODY), theme.bodyTextNudge());

        if (item.type == ItemType.LOCAL_MACRO) {
            int secondaryWidth = PackUiText.width(textRenderer, secondaryText, theme.fontFor(PackUiTone.BODY), secondaryColor);
            int secondaryX = Math.max(textLeft + 10, rightLimit - secondaryWidth);
            int primaryMaxWidth = Math.max(20, secondaryX - textLeft - 8);
            String trimmedPrimary = PackUiText.trimToWidth(textRenderer, primaryText, primaryMaxWidth, theme.fontFor(PackUiTone.BODY), primaryColor);
            PackUiText.draw(context, textRenderer, trimmedPrimary, theme.fontFor(PackUiTone.BODY), primaryColor, textLeft, textY, false);
            PackUiText.draw(context, textRenderer, secondaryText, theme.fontFor(PackUiTone.BODY), secondaryColor, secondaryX, textY, false);
        } else {
            String trimmedPrimary = PackUiText.trimToWidth(textRenderer, primaryText, labelWidth, theme.fontFor(PackUiTone.BODY), primaryColor);
            PackUiText.draw(context, textRenderer, trimmedPrimary, theme.fontFor(PackUiTone.BODY), primaryColor, textLeft, textY, false);
            if (!secondaryText.isEmpty()) {
                int secondaryX = textLeft + Math.min(labelWidth - 10, Math.max(PackUiText.width(textRenderer, trimmedPrimary, theme.fontFor(PackUiTone.BODY), primaryColor) + 10, labelWidth - 90));
                int secondaryWidth = Math.max(0, rightLimit - secondaryX);
                if (secondaryWidth > 18) {
                    String trimmedSecondary = PackUiText.trimToWidth(textRenderer, secondaryText, secondaryWidth, theme.fontFor(PackUiTone.BODY), secondaryColor);
                    PackUiText.draw(context, textRenderer, trimmedSecondary, theme.fontFor(PackUiTone.BODY), secondaryColor, secondaryX, textY, false);
                }
            }
        }
    }

    public void drawRowButton(GuiGraphicsExtractor context, PackUiRenderContext rowContext, int x, int y, int width, RowButton button) {
        PackUiButton node = new PackUiButton(button.label, button.variant, button.action)
            .setButtonHeight(rowButtonHeight())
            .setPreferredWidth(width)
            .setHorizontalPadding(rowButtonPadding())
            .setTextYOffset("X".equals(button.label) ? -1 : 0);
        node.setBounds(x, y, width, rowButtonHeight());
        node.render(rowContext);
    }

    public int measureRowButtonWidth(String label, int minWidth, int maxWidth) {
        return PackUiSizing.fitTextWidthInt(
            textRenderer,
            label,
            theme.fontFor(PackUiTone.BODY),
            theme.color(PackUiTone.BODY),
            rowButtonPadding(),
            minWidth,
            maxWidth
        );
    }

    public List<RowButton> buildRowButtons(DisplayItem item) {
        List<RowButton> buttons = new ArrayList<>();
        switch (item.type) {
            case LOCAL_MACRO -> {
                boolean running = MacroExecutor.isMacroRunning(item.macro.name);
                String runLabel = running ? "STOP" : "RUN";
                buttons.add(new RowButton(runLabel, running ? PackUiButton.Variant.DANGER : PackUiButton.Variant.SUCCESS, measureRowButtonWidth(runLabel, 22, 46), () -> {
                    if (MacroExecutor.isMacroRunning(item.macro.name)) {
                        MacroExecutor.stopMacro(item.macro.name);
                    } else {
                        item.macro.execute();
                    }
                }));
                buttons.add(new RowButton("EDIT", PackUiButton.Variant.SECONDARY, measureRowButtonWidth("EDIT", 24, 42), () -> {
                    setVisible(false);
                    PackUtilMacroEditorOverlay.getSharedOverlay().open(item.macro, true);
                }));
                buttons.add(new RowButton("COPY", PackUiButton.Variant.SECONDARY, measureRowButtonWidth("COPY", 26, 44), () -> {
                    try {
                        if (!PackUtilClipboardHelper.copyMacroToClipboard(item.macro)) {
                            PackUtilClientMessaging.sendPrefixed("Failed to copy macro.");
                            return;
                        }
                        PackUtilClientMessaging.sendPrefixed("Copied macro: " + item.macro.name);
                    } catch (Exception ignored) {
                        PackUtilClientMessaging.sendPrefixed("Failed to copy macro.");
                    }
                }));
                buttons.add(new RowButton("X", PackUiButton.Variant.DANGER, deleteButtonWidth(), () -> PackUtilMacroManager.get().delete(item.macro)));
            }
            case REMOTE_MACRO -> buttons.add(new RowButton("IMPORT", PackUiButton.Variant.SECONDARY, measureRowButtonWidth("IMPORT", 34, 58), () -> importRemoteMacro(item)));
            case METEOR_MACRO -> buttons.add(new RowButton("REFACTOR", PackUiButton.Variant.SECONDARY, measureRowButtonWidth("REFACTOR", 42, 70), () -> importMeteorMacro(item)));
            default -> {
            }
        }
        return buttons;
    }

    public void importRemoteMacro(DisplayItem item) {
        if (item.remoteSource == null || item.remoteSource.isBlank()) return;
        PackUtilLANSync sync = PackUtilLANSync.getInstance();
        Map<String, Map<String, PackUtilMacro>> allRemote = item.remoteMeteor ? sync.getAllRemoteMeteorMacros() : sync.getAllRemoteMacros();
        Map<String, PackUtilMacro> remoteMacros = allRemote.get(item.remoteSource);
        PackUtilMacro source = remoteMacros != null ? remoteMacros.get(item.label) : null;
        if (source == null) {
            PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§cRemote macro is no longer available: " + item.label);
            return;
        }

        PackUtilMacro imported = PackUtilMacroManager.get().addImportedCopy(source, source.name);
        if (imported == null) {
            PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§cFailed to import macro: " + item.label);
            return;
        }

        String sourceType = item.remoteMeteor ? "Meteor macro" : "macro";
        PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§aImported " + sourceType + " as: " + imported.name);
        rebuildData();
    }

    public void importMeteorMacro(DisplayItem item) {
        if (item.macro == null) return;
        PackUtilMacro imported = PackUtilMacroManager.get().addImportedCopy(item.macro, item.macro.name);
        if (imported == null) {
            PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§cFailed to import from Meteor.");
            return;
        }
        PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§aImported Meteor macro as: " + imported.name);
        rebuildData();
    }

    public void openCreateNew() {
        setVisible(false);
                    PackUtilMacroEditorOverlay.getSharedOverlay().open(null, true);
    }

    public void beginPasteMode() {
        pasteMode = true;
        pasteNameField.setText("");
        listScroll.jumpTo(0, 0);
        needsUiRebuild = true;
    }

    public void cancelPasteMode() {
        pasteMode = false;
        pasteNameField.setText("");
        pasteNameField.setFocused(false);
        needsUiRebuild = true;
    }

    public boolean pasteMacroFromClipboard() {
        String name = pasteNameField.text().trim();
        if (name.isEmpty()) {
            PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§cEnter a name for the pasted macro.");
            return true;
        }
        if (PackUtilMacroManager.get().get(name) != null) {
            PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§cA macro with that name already exists.");
            return true;
        }

        PackUtilMacro pasted = PackUtilClipboardHelper.pasteMacroFromClipboard();
        if (pasted == null) {
            PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§cClipboard does not contain a valid copied macro.");
            return true;
        }

        pasted.name = name;
        PackUtilMacroManager.get().add(pasted);
        PackUtilClientMessaging.sendPrefixed("Ãƒâ€šÃ‚Â§aPasted macro: " + pasted.name);
        cancelPasteMode();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        PackUiViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (button == 0 && isOverCloseButton(uiMouseX, uiMouseY) && closeVisibility > 0.01f) {
            setVisible(false);
            return true;
        }

        if (button == 0 && isOverHeaderUi(uiMouseX, uiMouseY)) {
            dragging = true;
            dragMoved = false;
            dragOffsetX = uiMouseX - panelX;
            dragOffsetY = uiMouseY - panelY;
            pressStartUiX = uiMouseX;
            pressStartUiY = uiMouseY;
            pressStartPanelX = panelX;
            pressStartPanelY = panelY;
            return true;
        }

        if (!collapsed && surface.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (!collapsed && button == 0 && uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            PackUiScrollbar.Metrics metrics = getScrollbarMetrics(Math.round(listSlot.x()), Math.round(listSlot.y()), Math.round(listSlot.width()), Math.round(listSlot.height()));
            if (metrics.hasScroll() && metrics.contains(uiMouseX, uiMouseY)) {
                scrollbarDragging = true;
                scrollbarGrabOffset = metrics.overThumb(uiMouseX, uiMouseY) ? (int) Math.round(uiMouseY) - metrics.thumbY() : metrics.thumbHeight() / 2;
                listScroll.setFromThumb(metrics, uiMouseY, scrollbarGrabOffset);
                return true;
            }
        }

        if (!collapsed && button == 0) {
            for (int i = clickRegions.size() - 1; i >= 0; i--) {
                ClickRegion region = clickRegions.get(i);
                if (region.contains(uiMouseX, uiMouseY)) {
                    region.action.run();
                    return true;
                }
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        PackUiViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (button == 0 && dragging) {
            boolean moved = dragMoved
                || Math.abs(uiMouseX - pressStartUiX) >= HEADER_CLICK_DRAG_THRESHOLD
                || Math.abs(uiMouseY - pressStartUiY) >= HEADER_CLICK_DRAG_THRESHOLD
                || panelX != pressStartPanelX
                || panelY != pressStartPanelY;
            dragging = false;
            dragMoved = false;
            if (!moved && isOverHeaderUi(uiMouseX, uiMouseY) && !isOverCloseButton(uiMouseX, uiMouseY)) {
                setCollapsed(!collapsed);
            }
            saveState();
            return true;
        }

        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            saveState();
            return true;
        }

        if (!collapsed && surface.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!visible) return false;

        PackUiViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (dragging && button == 0) {
            int nextX = Math.round(uiMouseX - dragOffsetX);
            int nextY = Math.round(uiMouseY - dragOffsetY);
            if (nextX != panelX || nextY != panelY) {
                dragMoved = true;
            }
            PackUtilWindowLayout clamped = clampToViewport(new PackUtilWindowLayout(nextX, nextY, panelWidth, panelHeight, visible, collapsed));
            panelX = clamped.x;
            panelY = clamped.y;
            return true;
        }

        if (scrollbarDragging && button == 0) {
            PackUiScrollbar.Metrics metrics = getScrollbarMetrics(Math.round(listSlot.x()), Math.round(listSlot.y()), Math.round(listSlot.width()), Math.round(listSlot.height()));
            listScroll.setFromThumb(metrics, uiMouseY, scrollbarGrabOffset);
            return true;
        }

        if (!collapsed && surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed) return false;
        PackUiViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (surface.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }

        if (uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            int visibleHeight = Math.max(0, Math.round(listSlot.height()) - (VIEWPORT_BORDER * 2));
            int maxScroll = Math.max(0, contentHeight - visibleHeight);
            listScroll.nudge(amount, rowHeight(), maxScroll);
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (surface.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && pasteMode) {
            cancelPasteMode();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return visible && surface.charTyped(chr, modifiers);
    }

    public List<DisplayItem> buildDisplayItems(
        List<PackUtilMacro> localMacros,
        List<PackUtilMacro> meteorMacros,
        Map<String, Map<String, PackUtilMacro>> remoteMacros,
        Map<String, Map<String, PackUtilMacro>> remoteMeteorMacros,
        PackUtilLANSync sync
    ) {
        String filter = searchField.text().trim().toLowerCase(Locale.ROOT);
        List<DisplayItem> items = new ArrayList<>();

        List<DisplayItem> localSection = new ArrayList<>();
        for (PackUtilMacro macro : localMacros) {
            if (!matchesFilter(macro.name, filter)) continue;
            localSection.add(DisplayItem.localMacro(macro));
        }
        if (!localSection.isEmpty()) {
            items.add(DisplayItem.section("AUTISM Client Macros", 0xFFFF5555));
            items.addAll(localSection);
        }

        List<DisplayItem> remoteSection = new ArrayList<>();
        if (sync.isInSession()) {
            for (Map.Entry<String, Map<String, PackUtilMacro>> entry : remoteMacros.entrySet()) {
                for (PackUtilMacro macro : entry.getValue().values()) {
                    if (PackUtilMacroManager.get().get(macro.name) != null) continue;
                    if (!matchesFilter(macro.name, filter)) continue;
                    remoteSection.add(DisplayItem.remoteMacro(macro.name, entry.getKey(), false));
                }
            }
        }
        if (!remoteSection.isEmpty()) {
            if (localSection.isEmpty()) {
                items.add(DisplayItem.section("AUTISM Client Macros", 0xFFFF5555));
            }
            items.addAll(remoteSection);
        }

        if (!PackUtilCompatManager.isMeteorAvailable()) return items;

        List<DisplayItem> meteorSection = new ArrayList<>();
        for (PackUtilMacro macro : meteorMacros) {
            if (!matchesFilter(macro.name, filter)) continue;
            meteorSection.add(DisplayItem.meteorMacro(macro));
        }
        if (sync.isInSession()) {
            for (Map.Entry<String, Map<String, PackUtilMacro>> entry : remoteMeteorMacros.entrySet()) {
                for (PackUtilMacro macro : entry.getValue().values()) {
                    boolean alreadyLocalMeteor = false;
                    for (PackUtilMacro localMeteor : meteorMacros) {
                        if (localMeteor.name.equals(macro.name)) {
                            alreadyLocalMeteor = true;
                            break;
                        }
                    }
                    if (alreadyLocalMeteor) continue;
                    if (!matchesFilter(macro.name, filter)) continue;
                    meteorSection.add(DisplayItem.remoteMacro(macro.name, entry.getKey(), true));
                }
            }
        }
        if (!meteorSection.isEmpty()) {
            items.add(DisplayItem.section("Meteor Macros", 0xFFD8E6FF));
            items.addAll(meteorSection);
        }

        return items;
    }

    public boolean matchesFilter(String name, String filter) {
        return filter.isEmpty() || (name != null && name.toLowerCase(Locale.ROOT).contains(filter));
    }

    public float animate(float current, float target, float delta) {
        return PackUiHeaderControls.animate(current, target, delta);
    }

    public void drawCollapseArrow(GuiGraphicsExtractor context, int x, int y, boolean active) {
        PackUiHeaderControls.drawAnimatedArrow(context, x, y + 1, headerArrowWidth(), collapsed ? 0.0f : 1.0f, active ? 1.0f : 0.56f);
    }

    public void drawCloseButton(GuiGraphicsExtractor context, int x, int y, int width, int height, float hover, boolean active, float visibility) {
        PackUiHeaderControls.drawCloseButton(context, x, y, width, height, hover, active, visibility);
    }

    public boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
        return uiMouseX >= panelX && uiMouseX < panelX + panelWidth
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
    }

    public boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
        return PackUiHeaderControls.isCloseHit(closeVisibility, uiMouseX, uiMouseY, closeButtonX(), controlButtonY(), headerControlSize());
    }

    public int controlButtonY() {
        return PackUiHeaderControls.controlY(panelY, theme.headerHeight(), headerControlSize());
    }

    public int closeButtonX() {
        return PackUiHeaderControls.closeX(panelX, panelWidth, headerControlSize(), 2);
    }

    public int collapseArrowX() {
        return PackUiHeaderControls.expandedArrowX(closeButtonX(), headerArrowGap(), headerArrowWidth());
    }

    public PackUtilWindowLayout clampToViewport(PackUtilWindowLayout bounds) {
        PackUiViewport viewport = surface.viewport();
        int width = Math.max(getMinWidth(), Math.min(bounds.width, Math.round(viewport.uiWidth())));
        int minHeight = bounds.collapsed ? theme.headerHeight() : getMinHeight();
        int height = Math.max(minHeight, Math.min(bounds.height, Math.round(viewport.uiHeight())));
        int x = Math.max(0, Math.min(bounds.x, Math.max(0, Math.round(viewport.uiWidth()) - width)));
        int y = Math.max(0, Math.min(bounds.y, Math.max(0, Math.round(viewport.uiHeight()) - theme.headerHeight())));
        return new PackUtilWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    public boolean uiContains(float x, float y, float width, float height, float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

        public int panelMinimumWidth() {
        return 258;
    }

    public int rowHeight() {
        return 18;
    }

    public int maxVisibleRows() {
        return 6;
    }

    public int panelPadding() {
        return 6;
    }

    public int windowContentGap() {
        return 4;
    }

    public int searchFieldHeight() {
        return 16;
    }

    public int actionRowGap() {
        return 4;
    }

    public int actionButtonHeight() {
        return 16;
    }

    public int headerControlSize() {
        return 12;
    }

    public int headerArrowWidth() {
        return 10;
    }

    public int headerArrowGap() {
        return 3;
    }

    public int rowButtonHeight() {
        return 14;
    }

    public int deleteButtonWidth() {
        return 16;
    }

    public int rowButtonGap() {
        return 4;
    }

    public int rowButtonPadding() {
        return 2;
    }

    public int rowButtonTopInset() {
        return Math.max(1, (rowHeight() - rowButtonHeight()) / 2);
    }

    public int rowTextInset() {
        return 8;
    }

    public int sectionHeaderInset() {
        return 4;
    }

    public int rowDotSize() {
        return 4;
    }

    public enum ItemType {
        SECTION_HEADER,
        LOCAL_MACRO,
        REMOTE_MACRO,
        METEOR_MACRO
    }

    public static final class DisplayItem {
        public ItemType type;
        public String label;
        public int color;
        public PackUtilMacro macro;
        public String remoteSource;
        public boolean remoteMeteor;

        public static DisplayItem section(String label, int color) {
            DisplayItem item = new DisplayItem();
            item.type = ItemType.SECTION_HEADER;
            item.label = label;
            item.color = color;
            return item;
        }

        public static DisplayItem localMacro(PackUtilMacro macro) {
            DisplayItem item = new DisplayItem();
            item.type = ItemType.LOCAL_MACRO;
            item.macro = macro;
            return item;
        }

        public static DisplayItem remoteMacro(String label, String source, boolean remoteMeteor) {
            DisplayItem item = new DisplayItem();
            item.type = ItemType.REMOTE_MACRO;
            item.label = label;
            item.remoteSource = source;
            item.remoteMeteor = remoteMeteor;
            return item;
        }

        public static DisplayItem meteorMacro(PackUtilMacro macro) {
            DisplayItem item = new DisplayItem();
            item.type = ItemType.METEOR_MACRO;
            item.macro = macro;
            return item;
        }
    }

    public static final class ClickRegion {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final Runnable action;

        public ClickRegion(int x, int y, int width, int height, Runnable action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.action = action;
        }

        public boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public static final class RowButton {
        public final String label;
        public final PackUiButton.Variant variant;
        public final int width;
        public final Runnable action;

        public RowButton(String label, PackUiButton.Variant variant, int width, Runnable action) {
            this.label = label;
            this.variant = variant;
            this.width = width;
            this.action = action;
        }
    }
}
