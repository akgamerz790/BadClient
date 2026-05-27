package dev.xaihi.autismclient.common.util;

import dev.xaihi.autismclient.common.gui.packui.PackUiAssets;
import dev.xaihi.autismclient.common.gui.packui.PackUiListRenderer;
import dev.xaihi.autismclient.common.gui.packui.PackUiOverlayButton;
import dev.xaihi.autismclient.common.gui.packui.PackUiRenderContext;
import dev.xaihi.autismclient.common.gui.packui.PackUiSizing;
import dev.xaihi.autismclient.common.gui.packui.PackUiScrollbar;
import dev.xaihi.autismclient.common.gui.packui.PackUiSmoothScroll;
import dev.xaihi.autismclient.common.gui.packui.PackUiTextField;
import dev.xaihi.autismclient.common.gui.packui.PackUiTheme;
import dev.xaihi.autismclient.common.gui.packui.PackUiTone;
import dev.xaihi.autismclient.common.gui.packui.PackUiViewport;
import dev.xaihi.autismclient.common.modules.PackUtilModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PackUtilCustomFilterPresetOverlay extends PackUtilOverlayBase {
    public final Font textRenderer;
    public final PackUiTheme theme = new PackUiTheme();
    public final PackUiTextField nameField;
    public final List<ActionButton> buttons = new ArrayList<>();
    public final PresetListState presetListState = new PresetListState();

    public int panelX = 570;
    public int panelY = 48;
    public int panelWidth;
    public int panelHeight;
    public boolean visible = false;
    public boolean collapsed = false;

    public boolean dragging = false;
    public boolean resizing = false;
    public double dragOffsetX = 0;
    public double dragOffsetY = 0;
    public double resizeStartMouseX = 0;
    public double resizeStartMouseY = 0;
    public int resizeStartWidth = 0;
    public int resizeStartHeight = 0;
    public boolean presetScrollbarDragging = false;
    public int presetScrollbarGrabOffset = 0;

    public String selectedPresetName;

    public PackUtilCustomFilterPresetOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        this.panelWidth = defaultPanelWidth();
        this.panelHeight = defaultPanelHeight();
        this.nameField = new PackUiTextField()
            .setPlaceholder("Preset name")
            .setFieldHeight(inputHeight())
            .setMinWidth(120)
            .setPreferredWidth(120)
            .setTextTone(PackUiTone.BODY)
            .setPlaceholderTone(PackUiTone.MUTED);
    }

    @Override
    public String getOverlayId() {
        return "packutil-custom-filter-presets";
    }

    @Override
    public int getMinWidth() {
        return defaultPanelWidth();
    }

    @Override
    public int getMinHeight() {
        return minimumPanelHeight();
    }

    @Override
    public PackUtilWindowLayout getBounds() {
        return new PackUtilWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
    }

    @Override
    public void setBounds(PackUtilWindowLayout bounds) {
        if (bounds == null) return;
        PackUtilWindowLayout clamped = clampToScreen(this, bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = defaultPanelWidth();
        panelHeight = Math.max(getMinHeight(), clamped.height);
        visible = clamped.visible;
        collapsed = clamped.collapsed;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            PackUtilOverlayManager.get().bringToFront(this);
        } else {
            clearFocus();
        }
        saveLayout();
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
        saveLayout();
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        PackUtilWindowLayout bounds = getBounds();
        return mouseX >= panelX && mouseX <= panelX + panelWidth
            && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT
            && !isOverWindowControl(mouseX, mouseY, bounds);
    }

    @Override
    public boolean hasTextFieldFocused() {
        return nameField.isFocused();
    }

    @Override
    public void clearTextFieldFocus() {
        clearFocus();
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        buttons.clear();
        PackUtilWindowLayout clamped = clampToScreen(this, new PackUtilWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        PackUtilWindowLayout bounds = new PackUtilWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
        renderWindowFrame(context, mouseX, mouseY, bounds, "Preset Manager", collapsed, dragging || resizing);
        boolean clipBody = beginWindowBodyClip(context, bounds, collapsed);
        if (!clipBody) {
            renderWindowInactiveOverlay(context, bounds, collapsed, dragging || resizing);
            return;
        }

        try {
            int x = panelX + panelInset();
            int y = panelY + HEADER_HEIGHT + topInset();
            int width = panelWidth - (panelInset() * 2);
            PackUtilModule module = PackUtilModule.get();

            PackUtilText.draw(context, textRenderer,
                "C2S " + module.getC2SPackets().size() + " | S2C " + module.getS2CPackets().size(),
                PackUtilText.Tone.MUTED, x, y, false);
            y += infoLineHeight();

            PackUiListRenderer.drawHeader(context, textRenderer, "Preset Name", x, y);
            y += sectionHeaderHeight();

            nameField.setBounds(x, y, width, inputHeight());
            nameField.render(renderContext(context, mouseX, mouseY, delta));
            y += inputHeight() + actionGap();

            int halfWidth = (width - actionGap()) / 2;
            y = drawActionRow(context, mouseX, mouseY, x, y, halfWidth,
                "Save", this::saveNamedPreset, canSaveNamed(),
                "Load", this::loadSelectedPreset, canLoadSelected());
            y = drawActionRow(context, mouseX, mouseY, x, y, halfWidth,
                "Overwrite", this::overwriteSelectedPreset, canOverwriteSelected(),
                "Delete", this::deleteSelectedPreset, canDeleteSelected());
            y = drawAction(context, mouseX, mouseY, x, y, width, "Reset To Default", this::resetDefaults, true);
            y += sectionGap() - actionGap();

            y = renderPresetSection(context, mouseX, mouseY, delta, x, y, width, Math.max(listMinimumHeight(), panelHeight - (y - panelY) - contentBottomPadding()));
        } finally {
            endWindowBodyClip(context, clipBody);
            renderWindowInactiveOverlay(context, bounds, collapsed, dragging || resizing);
        }
    }

    public int renderPresetSection(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, int x, int y, int width, int sectionHeight) {
        PackUiListRenderer.drawHeader(context, textRenderer, "Saved Presets", x, y);
        y += sectionHeaderHeight();

        List<PresetRow> rows = buildPresetRows();
        presetListState.rows = rows;
        int listHeight = Math.max(listMinimumHeight(), sectionHeight - sectionHeaderHeight() - footerHeight() - footerGap());
        int viewHeight = Math.max(1, alignViewportHeight(Math.max(1, listHeight - 4), presetRowStep()));
        int contentHeight = rows.size() * presetRowStep();
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        int visualScroll = presetListState.scroll.tick(delta, maxScroll);
        PackUiScrollbar.Metrics scrollbar = computePresetScrollbarMetrics(x, y, width, listHeight, contentHeight, visualScroll);
        int contentWidth = Math.max(40, width - 4 - (scrollbar.hasScroll() ? scrollbarGutter() : 0));
        presetListState.setBounds(x, y, width, listHeight, scrollbar);

        boolean focused = PackUtilOverlayManager.get().isFocusedOverlay(this) || PackUtilOverlayManager.get().isTopOverlay(this);
        PackUiListRenderer.drawFrame(context, x, y, width, listHeight, focused);

        if (rows.isEmpty()) {
            PackUiListRenderer.drawEmptyState(context, textRenderer, "No presets", x, y, contentWidth);
        } else {
            PackUiViewport viewport = PackUiViewport.current(1.0f);
            viewport.enableScissor(context, x + 2, y + 2, x + 2 + contentWidth, y + 2 + viewHeight);
            try {
                int scrollPixels = visualScroll;
                int firstVisible = scrollPixels / presetRowStep();
                int rowY = y + 2 - (scrollPixels % presetRowStep());
                int rowTextY;
                for (int i = firstVisible; i < rows.size() && rowY < y + 2 + viewHeight; i++) {
                    PresetRow row = rows.get(i);
                    if (rowY + presetRowHeight() > y + 2) {
                        rowTextY = PackUiSizing.alignTextY(rowY, presetRowHeight(), theme.fontHeight(PackUiTone.BODY), theme.bodyTextNudge());
                        if (row.header) {
                            PackUiListRenderer.drawRow(context, textRenderer, row.label, x + 2, rowY, contentWidth, presetRowHeight(), false, false, PackUiListRenderer.RowTone.WARNING);
                        } else if (row.note) {
                            PackUiListRenderer.drawRow(context, textRenderer, row.label, x + 2, rowY, contentWidth, presetRowHeight(), false, false, PackUiListRenderer.RowTone.NORMAL);
                        } else if (row.entry != null) {
                            boolean selected = row.entry.name().equals(selectedPresetName);
                            boolean hovered = mouseX >= x + 2 && mouseX < x + 2 + contentWidth && mouseY >= rowY && mouseY < rowY + presetRowHeight();
                            PackUiListRenderer.drawRow(
                                context,
                                textRenderer,
                                "",
                                x + 2,
                                rowY,
                                contentWidth,
                                presetRowHeight(),
                                hovered,
                                selected,
                                row.entry.builtIn() ? PackUiListRenderer.RowTone.WARNING : PackUiListRenderer.RowTone.NORMAL
                            );
                            int textColor = selected
                                ? PackUtilColors.rowSelectedText()
                                : (row.entry.builtIn() ? 0xFFB7D4FF : PackUtilColors.textLight());
                            String prefix = row.entry.builtIn() ? "[DEV] " : "[USR] ";
                            String name = PackUtilText.trimToWidth(textRenderer, prefix + row.entry.name(), contentWidth - 70, PackUtilText.Tone.BODY);
                            PackUtilText.draw(context, textRenderer, name, textColor, x + 5, rowTextY, false);

                            String counts = row.entry.c2sCount() + " / " + row.entry.s2cCount();
                            int countsWidth = PackUtilText.width(textRenderer, counts, PackUtilText.Tone.BODY);
                            PackUtilText.draw(context, textRenderer, counts, textColor, x + contentWidth - countsWidth - 1, rowTextY, false);
                        }
                        PackUiListRenderer.drawDivider(context, x + 2, rowY + presetRowHeight(), contentWidth);
                    }
                    rowY += presetRowStep();
                }
            } finally {
                viewport.disableScissor(context);
            }
        }

        PackUiScrollbar.draw(context, scrollbar, scrollbar.contains(mouseX, mouseY), presetScrollbarDragging);

        String footer = maxScroll > 0
            ? "Scroll: " + (Math.min(rows.size(), Math.round(presetListState.scroll.targetOffset() / (float) presetRowStep()) + 1))
                + "/" + (Math.max(1, Math.round(maxScroll / (float) presetRowStep()) + 1))
            : (selectedPresetName == null ? "Select a preset row" : "Selected: " + selectedPresetName);
        PackUtilText.draw(context, textRenderer, footer, PackUtilText.Tone.MUTED, x + 4, y + listHeight + footerGap(), false);
        return y + listHeight + footerHeight() + footerGap();
    }

    public List<PresetRow> buildPresetRows() {
        PackUtilPresetManager manager = PackUtilPresetManager.get();
        List<PresetRow> rows = new ArrayList<>();
        rows.add(PresetRow.header("Developer Templates"));
        for (PackUtilPresetManager.PresetEntry entry : manager.getBuiltInPresetEntries()) {
            rows.add(PresetRow.entry(entry));
        }
        rows.add(PresetRow.header("User Presets"));
        List<PackUtilPresetManager.PresetEntry> userEntries = manager.getUserPresetEntries();
        if (userEntries.isEmpty()) {
            rows.add(PresetRow.note("No user presets yet"));
        } else {
            for (PackUtilPresetManager.PresetEntry entry : userEntries) {
                rows.add(PresetRow.entry(entry));
            }
        }
        return rows;
    }

    public void saveNamedPreset() {
        String name = sanitizeNameField();
        if (name == null) {
            PackUtilClientMessaging.sendPrefixed("Enter a preset name first.");
            return;
        }
        if (PackUtilPresetManager.get().savePreset(name)) {
            selectedPresetName = name;
            nameField.setText(name);
        }
    }

    public void loadSelectedPreset() {
        String presetName = resolveTargetPresetName();
        if (presetName == null) {
            PackUtilClientMessaging.sendPrefixed("Select a preset first.");
            return;
        }
        if (PackUtilPresetManager.get().loadPreset(presetName)) {
            selectedPresetName = PackUtilPresetManager.get().getPresetEntry(presetName) != null
                ? PackUtilPresetManager.get().getPresetEntry(presetName).name()
                : presetName;
            nameField.setText(selectedPresetName);
        }
    }

    public void overwriteSelectedPreset() {
        String presetName = resolveTargetPresetName();
        if (presetName == null) {
            PackUtilClientMessaging.sendPrefixed("Select a user preset first.");
            return;
        }
        PackUtilPresetManager.PresetEntry entry = PackUtilPresetManager.get().getPresetEntry(presetName);
        if (entry == null || entry.builtIn()) {
            PackUtilClientMessaging.sendPrefixed("Select a user preset to overwrite.");
            return;
        }
        if (PackUtilPresetManager.get().overwriteUserPreset(entry.name())) {
            selectedPresetName = entry.name();
            nameField.setText(entry.name());
        }
    }

    public void deleteSelectedPreset() {
        String presetName = resolveTargetPresetName();
        if (presetName == null) {
            PackUtilClientMessaging.sendPrefixed("Select a user preset first.");
            return;
        }
        PackUtilPresetManager.PresetEntry entry = PackUtilPresetManager.get().getPresetEntry(presetName);
        if (entry == null || entry.builtIn()) {
            PackUtilClientMessaging.sendPrefixed("Developer presets cannot be deleted.");
            return;
        }
        if (PackUtilPresetManager.get().deleteUserPreset(entry.name())) {
            if (entry.name().equals(selectedPresetName)) {
                selectedPresetName = null;
            }
            nameField.setText("");
        }
    }

    public void resetDefaults() {
        PackUtilModule module = PackUtilModule.get();
        module.resetC2SPacketsToDefault();
        module.resetS2CPacketsToDefault();
        PackUtilClientMessaging.sendPrefixed("Reset current filter to default packet lists.");
    }

    public String sanitizeNameField() {
        String value = nameField.text();
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    public String resolveTargetPresetName() {
        if (selectedPresetName != null) {
            PackUtilPresetManager.PresetEntry selectedEntry = PackUtilPresetManager.get().getPresetEntry(selectedPresetName);
            if (selectedEntry != null) return selectedEntry.name();
        }

        String typedName = sanitizeNameField();
        if (typedName == null) return null;
        PackUtilPresetManager.PresetEntry typedEntry = PackUtilPresetManager.get().getPresetEntry(typedName);
        return typedEntry != null ? typedEntry.name() : null;
    }

    public PackUtilPresetManager.PresetEntry getSelectedPresetEntry() {
        if (selectedPresetName == null) return null;
        return PackUtilPresetManager.get().getPresetEntry(selectedPresetName);
    }

    public boolean canSaveNamed() {
        String name = sanitizeNameField();
        return name != null && !PackUtilPresetManager.get().isReservedPresetName(name);
    }

    public boolean canLoadSelected() {
        return resolveTargetPresetName() != null;
    }

    public boolean canOverwriteSelected() {
        PackUtilPresetManager.PresetEntry entry = getSelectedPresetEntry();
        return entry != null && !entry.builtIn();
    }

    public boolean canDeleteSelected() {
        return canOverwriteSelected();
    }

    public int drawAction(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, Runnable action, boolean enabled) {
        drawActionButton(context, mouseX, mouseY, x, y, width, label, enabled);
        buttons.add(new ActionButton(x, y, width, actionHeight(), action, enabled));
        return y + actionHeight() + actionGap();
    }

    public int drawActionRow(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int buttonWidth,
                              String leftLabel, Runnable leftAction, boolean leftEnabled,
                              String rightLabel, Runnable rightAction, boolean rightEnabled) {
        drawActionButton(context, mouseX, mouseY, x, y, buttonWidth, leftLabel, leftEnabled);
        buttons.add(new ActionButton(x, y, buttonWidth, actionHeight(), leftAction, leftEnabled));

        int rightX = x + buttonWidth + actionGap();
        int rightWidth = panelWidth - (panelInset() * 2) - buttonWidth - actionGap();
        drawActionButton(context, mouseX, mouseY, rightX, y, rightWidth, rightLabel, rightEnabled);
        buttons.add(new ActionButton(rightX, y, rightWidth, actionHeight(), rightAction, rightEnabled));
        return y + actionHeight() + actionGap();
    }

    public void drawActionButton(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, boolean enabled) {
        if (enabled) {
            PackUiOverlayButton enabledButton = PackUiOverlayButton.create(x, y, width, actionHeight(), net.minecraft.network.chat.Component.literal(label), ignored -> {});
            enabledButton.setVariant(PackUiOverlayButton.Variant.SECONDARY);
            PackUiOverlayButton.renderStyled(context, textRenderer, enabledButton, mouseX, mouseY);
            return;
        }
        PackUiOverlayButton disabledButton = PackUiOverlayButton.create(x, y, width, actionHeight(), net.minecraft.network.chat.Component.literal(label), ignored -> {});
        disabledButton.active = false;
        disabledButton.setVariant(PackUiOverlayButton.Variant.GHOST);
        PackUiOverlayButton.renderStyled(context, textRenderer, disabledButton, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        if (false && button == 0 && !collapsed && isResizeActive(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)) {
            resizing = true;
            resizeStartMouseX = mouseX;
            resizeStartMouseY = mouseY;
            resizeStartWidth = panelWidth;
            resizeStartHeight = panelHeight;
            return true;
        }

        if (button == 0 && mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= panelY && mouseY < panelY + HEADER_HEIGHT) {
            PackUtilWindowLayout bounds = getBounds();
            if (isOverCloseButton(mouseX, mouseY, bounds)) {
                setVisible(false);
                return true;
            }
            if (isOverCollapseButton(mouseX, mouseY, bounds)) {
                toggleCollapsed();
                return true;
            }
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            clearFocus();
            return true;
        }

        if (collapsed) return false;

        if (button == 0) {
            PackUiScrollbar.Metrics scrollbar = presetListState.scrollbarMetrics;
            if (scrollbar != null && scrollbar.hasScroll() && scrollbar.contains(mouseX, mouseY)) {
                presetScrollbarDragging = true;
                presetScrollbarGrabOffset = Math.max(0, (int) mouseY - scrollbar.thumbY());
                presetListState.scroll.setFromThumbStepped(scrollbar, mouseY, presetScrollbarGrabOffset, presetRowStep());
                return true;
            }
        }

        if (handleTextFieldClick(nameField, mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && presetListState.contains(mouseX, mouseY)) {
            PresetRow row = presetListState.getRowAt(mouseY);
            if (row != null && row.entry != null) {
                selectedPresetName = row.entry.name();
                nameField.setText(selectedPresetName);
                clearFocus();
                return true;
            }
        }

        clearFocus();
        if (button == 0) {
            for (ActionButton actionButton : buttons) {
                if (actionButton.contains(mouseX, mouseY)) {
                    if (!actionButton.enabled()) {
                        return true;
                    }
                    actionButton.action.run();
                    return true;
                }
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    public boolean handleTextFieldClick(PackUiTextField field, double mouseX, double mouseY, int button) {
        boolean clicked = field.mouseClicked(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button);
        if (clicked) {
            clearFocus();
            field.setFocused(true);
        }
        return clicked;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (nameField.mouseReleased(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button)) return true;
        if (button == 0 && presetScrollbarDragging) {
            presetScrollbarDragging = false;
            return true;
        }
        if (button == 0) {
            if (dragging || resizing) saveLayout();
            dragging = false;
            resizing = false;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (nameField.mouseDragged(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button, (float) deltaX, (float) deltaY)) return true;
        if (presetScrollbarDragging) {
            presetListState.scroll.setFromThumbStepped(presetListState.scrollbarMetrics, mouseY, presetScrollbarGrabOffset, presetRowStep());
            return true;
        }
        if (resizing && button == 0) {
            PackUtilWindowLayout nextBounds = clampToScreen(this,
                new PackUtilWindowLayout(panelX, panelY,
                    resizeStartWidth + (int) Math.round(mouseX - resizeStartMouseX),
                    resizeStartHeight + (int) Math.round(mouseY - resizeStartMouseY),
                    visible, collapsed));
            panelX = nextBounds.x;
            panelY = nextBounds.y;
            panelWidth = nextBounds.width;
            panelHeight = nextBounds.height;
            return true;
        }
        if (dragging && button == 0) {
            PackUtilWindowLayout nextBounds = clampToScreen(this,
                new PackUtilWindowLayout((int) (mouseX - dragOffsetX), (int) (mouseY - dragOffsetY),
                    panelWidth, panelHeight, visible, collapsed));
            panelX = nextBounds.x;
            panelY = nextBounds.y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (presetListState.contains(mouseX, mouseY)) {
            int maxScroll = Math.max(0, (presetListState.rows.size() * presetRowStep()) - alignViewportHeight(Math.max(1, presetListState.height - 4), presetRowStep()));
            presetListState.scroll.nudge(amount, presetRowStep(), maxScroll);
            return true;
        }
        return false;
    }

    public PackUiScrollbar.Metrics computePresetScrollbarMetrics(int x, int y, int width, int listHeight, int contentHeight, int scrollOffset) {
        int viewHeight = Math.max(1, alignViewportHeight(Math.max(1, listHeight - 4), presetRowStep()));
        return PackUiScrollbar.compute(contentHeight, viewHeight, x + width - 5, y + 2, scrollbarTrackWidth(), Math.max(1, listHeight - 4), scrollOffset);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (nameField.isFocused()) {
                clearFocus();
                return true;
            }
            setVisible(false);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (nameField.isFocused() && canSaveNamed()) {
                saveNamedPreset();
                return true;
            }
        }

        if (!nameField.isFocused()) return false;
        return nameField.keyPressed(inputContext(0, 0), keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible || !nameField.isFocused()) return false;
        return nameField.charTyped(inputContext(0, 0), chr, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        PackUtilWindowLayout bounds = getBounds();
        int frameHeight = collapsed ? HEADER_HEIGHT : panelHeight;
        if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width && mouseY >= bounds.y && mouseY <= bounds.y + frameHeight) return true;
        return mouseX >= nameField.x() && mouseX <= nameField.x() + nameField.width()
            && mouseY >= nameField.y() && mouseY <= nameField.y() + nameField.height();
    }

    public void clearFocus() {
        nameField.setFocused(false);
    }

    public PackUiRenderContext renderContext(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        PackUiViewport viewport = PackUiViewport.current(1.0f);
        return new PackUiRenderContext(context, textRenderer, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), delta);
    }

    public PackUiRenderContext inputContext(double mouseX, double mouseY) {
        PackUiViewport viewport = PackUiViewport.current(1.0f);
        return new PackUiRenderContext(null, textRenderer, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0f);
    }

        public int defaultPanelWidth() {
        return 236;
    }

    public int defaultPanelHeight() {
        return 318;
    }

    public int minimumPanelHeight() {
        return 250;
    }

    public int panelInset() {
        return 10;
    }

    public int topInset() {
        return 6;
    }

    public int contentBottomPadding() {
        return 12;
    }

    public int infoLineHeight() {
        return theme.lineHeight(PackUiTone.MUTED, 1);
    }

    public int sectionHeaderHeight() {
        return theme.lineHeight(PackUiTone.LABEL, 0);
    }

    public int actionHeight() {
        return 13;
    }

    public int actionGap() {
        return 2;
    }

    public int sectionGap() {
        return 6;
    }

    public int inputHeight() {
        return 16;
    }

    public int presetRowHeight() {
        return 14;
    }

    public int presetRowStep() {
        return presetRowHeight() + 1;
    }

    public int footerGap() {
        return 4;
    }

    public int footerHeight() {
        return theme.lineHeight(PackUiTone.MUTED, 1);
    }

    public int scrollbarGutter() {
        return 8;
    }

    public int scrollbarTrackWidth() {
        return 3;
    }

    public int listMinimumHeight() {
        return 52;
    }

    public record ActionButton(int x, int y, int width, int height, Runnable action, boolean enabled) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public final class PresetListState {
        public int x;
        public int y;
        public int width;
        public int height;
        public final PackUiSmoothScroll scroll = new PackUiSmoothScroll();
        public PackUiScrollbar.Metrics scrollbarMetrics = null;
        public List<PresetRow> rows = List.of();

        public void setBounds(int x, int y, int width, int height, PackUiScrollbar.Metrics scrollbarMetrics) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scrollbarMetrics = scrollbarMetrics;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        public PresetRow getRowAt(double mouseY) {
            int idx = (int) (((mouseY - y - 2) + scroll.visualOffsetInt()) / PackUtilCustomFilterPresetOverlay.this.presetRowStep());
            if (idx < 0 || idx >= rows.size()) return null;
            return rows.get(idx);
        }
    }

    public static final class PresetRow {
        public final boolean header;
        public final boolean note;
        public final String label;
        public final PackUtilPresetManager.PresetEntry entry;

        public PresetRow(boolean header, boolean note, String label, PackUtilPresetManager.PresetEntry entry) {
            this.header = header;
            this.note = note;
            this.label = label;
            this.entry = entry;
        }

        public static PresetRow header(String label) {
            return new PresetRow(true, false, label, null);
        }

        public static PresetRow note(String label) {
            return new PresetRow(false, true, label, null);
        }

        public static PresetRow entry(PackUtilPresetManager.PresetEntry entry) {
            return new PresetRow(false, false, null, entry);
        }
    }
}
