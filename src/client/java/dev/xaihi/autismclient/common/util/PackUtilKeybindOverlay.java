package dev.xaihi.autismclient.common.util;

import dev.xaihi.autismclient.common.gui.packui.PackUiLabel;
import dev.xaihi.autismclient.common.gui.packui.PackUiNode;
import dev.xaihi.autismclient.common.gui.packui.PackUiButton;
import dev.xaihi.autismclient.common.gui.packui.PackUiCompactRow;
import dev.xaihi.autismclient.common.gui.packui.PackUiFormRow;
import dev.xaihi.autismclient.common.gui.packui.PackUiHeaderControls;
import dev.xaihi.autismclient.common.gui.packui.PackUiAssets;
import dev.xaihi.autismclient.common.gui.packui.PackUiInsets;
import dev.xaihi.autismclient.common.gui.packui.PackUiRenderContext;
import dev.xaihi.autismclient.common.gui.packui.PackUiSurface;
import dev.xaihi.autismclient.common.gui.packui.PackUiTheme;
import dev.xaihi.autismclient.common.gui.packui.PackUiSizing;
import dev.xaihi.autismclient.common.gui.packui.PackUiText;
import dev.xaihi.autismclient.common.gui.packui.PackUiTone;
import dev.xaihi.autismclient.common.gui.packui.PackUiViewport;
import dev.xaihi.autismclient.common.gui.packui.PackUiWindowNode;
import dev.xaihi.autismclient.common.modules.PackUtilModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PackUtilKeybindOverlay extends PackUtilOverlayBase {
    public static final Minecraft MC = Minecraft.getInstance();
    public static final String WINDOW_TITLE = "Settings";

    public static final String INSIDE_GUI_TOOLTIP = "Fire keybinds while a container screen is open. Blocked when any text field is focused.";
    public static final String INVENTORY_MOVE_TOOLTIP = "Allows movement keys while normal container screens are open. Disabled on text-entry screens and while macros are running.";
    public static final String XCARRY_TOOLTIP = "Keeps crafting-grid items and one cursor-carried stack by cancelling the close packet for your inventory screen. Macro XCarry actions still work even when this is off.";
    public static final int MIN_PANEL_WIDTH = 196;
    public static final int ROW_HEIGHT = 18;
    public static final int PAD = 5;
    public static final int HEADER_CONTROL = 12;
    public static final int HEADER_ARROW_WIDTH = 10;
    public static final int HEADER_ARROW_GAP = 3;
    public static final int ROW_LABEL_GAP = 4;
    public static final int BUTTON_GAP = 2;
    public static final int BIND_BTN_W = 36;
    public static final int CLEAR_BTN_W = 12;
    public static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0f;

    public final PackUiTheme theme = new PackUiTheme();
    public final PackUiWindowNode windowNode = new PackUiWindowNode(WINDOW_TITLE);
    public final PackUiSurface surface = new PackUiSurface(theme, windowNode);
    public final List<KeybindEntry> entries = new ArrayList<>();
    public final List<KeybindRowNode> rowNodes = new ArrayList<>();

    public boolean dragging = false;
    public float dragOffsetX;
    public float dragOffsetY;
    public float pressStartUiX;
    public float pressStartUiY;
    public int pressStartPanelX;
    public int pressStartPanelY;
    public boolean dragMoved = false;
    public int capturingIndex = -1;
    public float closeHover = 0.0f;
    public float closeVisibility = 1.0f;

    public String hoveredTooltip = null;
    public float tooltipUiX;
    public float tooltipUiY;

    public PackUtilKeybindOverlay() {
        super("packutil-keybinds", MIN_PANEL_WIDTH, 142);
        this.panelX = 160;
        this.panelY = 46;
        entries.add(new KeybindEntry("Load GUI", "Restores the last saved GUI screen and handler", () -> getConfig().keybindLoadGui, v -> getConfig().keybindLoadGui = v));
        entries.add(new KeybindEntry("Flush Queue", "Sends all queued packets to the server", () -> getConfig().keybindFlushQueue, v -> getConfig().keybindFlushQueue = v));
        entries.add(new KeybindEntry("Clear Queue", "Discards all queued packets", () -> getConfig().keybindClearQueue, v -> getConfig().keybindClearQueue = v));
        entries.add(new KeybindEntry("Toggle Logger", "Shows or hides the packet logger overlay", () -> getConfig().keybindToggleLogger, v -> getConfig().keybindToggleLogger = v));
        entries.add(new KeybindEntry("Toggle Send", "Turns packet sending on or off", () -> getConfig().keybindToggleSend, v -> getConfig().keybindToggleSend = v));
        entries.add(new KeybindEntry("Toggle Delay", "Turns packet delay on or off", () -> getConfig().keybindToggleDelay, v -> getConfig().keybindToggleDelay = v));

        buildUi();
    }

    public void buildUi() {
        windowNode.setCenterTitle(false);
        windowNode.setTitleTone(PackUiTone.LABEL);
        windowNode.setTitleAreaInsets(panelPadding() + 2, panelPadding() + headerControlSize() + headerArrowWidth() + headerArrowGap() + 10);
        windowNode.content().clearChildren();
        windowNode.content().setGap(windowContentGap()).setPadding(PackUiInsets.all(panelPadding()));

        windowNode.content().add(new ToggleRowNode(
            "Inside GUI",
            INSIDE_GUI_TOOLTIP,
            () -> getConfig().keybindInsideGui,
            v -> getConfig().keybindInsideGui = v
        ));
        windowNode.content().add(new ToggleRowNode(
            "Inv Move",
            INVENTORY_MOVE_TOOLTIP,
            () -> PackUtilModule.get().isInventoryMoveEnabled(),
            v -> PackUtilModule.get().setInventoryMoveEnabled(v)
        ));
        windowNode.content().add(new ToggleRowNode(
            "XCarry",
            XCARRY_TOOLTIP,
            () -> PackUtilModule.get().isXCarryEnabled(),
            v -> PackUtilModule.get().setXCarryEnabled(v)
        ));

        rowNodes.clear();
        for (int i = 0; i < entries.size(); i++) {
            KeybindRowNode row = new KeybindRowNode(i, entries.get(i));
            rowNodes.add(row);
            windowNode.content().add(row);
        }
    }

    public PackUtilConfig getConfig() {
        return PackUtilConfig.getGlobal();
    }

    @Override
    public int getMinWidth() {
        return computePanelWidth();
    }

    @Override
    public int getMinHeight() {
        return theme.headerHeight() + bodyMinimumHeight();
    }

    @Override
    public PackUtilWindowLayout getBounds() {
        return new PackUtilWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
    }

    @Override
    public void setBounds(PackUtilWindowLayout bounds) {
        PackUtilWindowLayout clamped = clampToViewport(bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
        windowNode.syncShowBody(!collapsed);
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        hoveredTooltip = null;
        PackUiViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        panelWidth = computePanelWidth();
        boolean active = PackUtilOverlayManager.get().isFocusedOverlay(this) || PackUtilOverlayManager.get().isTopOverlay(this);
        boolean headerHovered = uiMouseX >= panelX && uiMouseX < panelX + panelWidth
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
        PackUiRenderContext metrics = new PackUiRenderContext(context, MC.font, viewport, theme, uiMouseX, uiMouseY, delta);

        windowNode.setShowBody(!collapsed);
        windowNode.setActive(active);
        windowNode.setHeaderHovered(headerHovered);
        panelHeight = Math.round(windowNode.preferredHeight(metrics, panelWidth));
        panelHeight = Math.max(theme.headerHeight(), panelHeight);
        PackUtilWindowLayout clamped = clampToViewport(new PackUtilWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        windowNode.setBounds(panelX, panelY, panelWidth, panelHeight);

        surface.render(context, mouseX, mouseY, delta);
        renderHeaderControls(context, viewport, uiMouseX, uiMouseY, delta, active);

        if (!collapsed && hoveredTooltip != null) {
            context.nextStratum();
            PackUiText.interOverlayFlush(context);
            renderTooltip(context, viewport, hoveredTooltip, tooltipUiX, tooltipUiY);
        }
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
            int scaledControlSize = headerControlSize();
            drawCloseButton(context, closeX, controlY, scaledControlSize, scaledControlSize, closeHover, active, closeVisibility);
        } finally {
            viewport.pop(context);
        }
    }

    public void drawCollapseArrow(GuiGraphicsExtractor context, int x, int y, boolean active) {
        PackUiHeaderControls.drawAnimatedArrow(context, x, y + iconYOffset(), headerArrowWidth(), collapsed ? 0.0f : 1.0f, active ? 1.0f : 0.56f);
    }

    public void drawCloseButton(GuiGraphicsExtractor context, int x, int y, int width, int height, float hover, boolean active, float visibility) {
        PackUiHeaderControls.drawCloseButton(context, x, y, width, height, hover, active, visibility);
    }

    public void renderTooltip(GuiGraphicsExtractor context, PackUiViewport viewport, String text, float uiX, float uiY) {
        List<String> lines = wrapTooltipText(text, tooltipMaxWidth());
        int lineH = theme.lineHeight(PackUiTone.BODY, 0);
        int maxW = 0;
        for (String line : lines) {
            maxW = Math.max(maxW, PackUiText.width(MC.font, line, theme.fontFor(PackUiTone.BODY), theme.color(PackUiTone.BODY)));
        }

        int width = maxW + tooltipPadding() * 2;
        int height = lines.size() * lineH + tooltipPadding() * 2;
        int drawX = Math.round(Math.min(uiX, viewport.uiWidth() - width - tooltipPadding() * 2));
        int drawY = Math.round(Math.min(uiY, viewport.uiHeight() - height - tooltipPadding() * 2));

        viewport.push(context);
        try {
            PackUiText.fill(context, drawX, drawY, drawX + width, drawY + height, PackUtilColors.tooltipBg());
            PackUiText.fill(context, drawX, drawY, drawX + width, drawY + 1, 0xFFFF4A4A);
            PackUiText.fill(context, drawX, drawY + height - 1, drawX + width, drawY + height, 0xFFFF4A4A);
            PackUiText.fill(context, drawX, drawY, drawX + 1, drawY + height, 0xFFFF4A4A);
            PackUiText.fill(context, drawX + width - 1, drawY, drawX + width, drawY + height, 0xFFFF4A4A);

            for (int i = 0; i < lines.size(); i++) {
                PackUiText.draw(context, MC.font, lines.get(i), theme.fontFor(PackUiTone.BODY), 0xFFF0ECE7, drawX + tooltipPadding(), drawY + tooltipPadding() + i * lineH, false);
            }
        } finally {
            viewport.pop(context);
        }
    }

    public List<String> wrapTooltipText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (PackUiText.width(MC.font, candidate, theme.fontFor(PackUiTone.BODY), theme.color(PackUiTone.BODY)) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    public static String getKeyName(int keyCode) {
        return PackUtilBindUtil.getBindName(keyCode);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        PackUiViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (isOverCloseButton(uiMouseX, uiMouseY)) {
            visible = false;
            capturingIndex = -1;
            dragging = false;
            dragMoved = false;
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

        if (collapsed) return isMouseOver(mouseX, mouseY);

        if (capturingIndex >= 0 && PackUtilBindUtil.isAllowedMouseButton(button)) {
            captureBind(PackUtilBindUtil.encodeMouseButton(button));
            return true;
        }

        if (surface.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (surface.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (!visible || capturingIndex < 0) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            capturingIndex = -1;
            return true;
        }

        captureBind(keyCode);
        return true;
    }

    public void captureBind(int bindCode) {
        for (int i = 0; i < entries.size(); i++) {
            if (i != capturingIndex && entries.get(i).getter.getAsInt() == bindCode) {
                entries.get(i).setter.accept(-1);
            }
        }

        entries.get(capturingIndex).setter.accept(bindCode);
        getConfig().save();
        capturingIndex = -1;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            boolean shouldCollapse = !dragMoved;
            dragging = false;
            if (shouldCollapse) {
                collapsed = !collapsed;
            }
            saveLayout();
            return true;
        }
        if (collapsed) return false;
        return surface.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            PackUiViewport viewport = surface.viewport();
            float uiMouseX = viewport.toUiX(mouseX);
            float uiMouseY = viewport.toUiY(mouseY);
            PackUtilWindowLayout clamped = clampToViewport(new PackUtilWindowLayout(
                Math.round(uiMouseX - dragOffsetX),
                Math.round(uiMouseY - dragOffsetY),
                panelWidth,
                panelHeight,
                visible,
                collapsed
            ));
            panelX = clamped.x;
            panelY = clamped.y;
            dragMoved = dragMoved
                || Math.abs(uiMouseX - pressStartUiX) >= HEADER_CLICK_DRAG_THRESHOLD
                || Math.abs(uiMouseY - pressStartUiY) >= HEADER_CLICK_DRAG_THRESHOLD
                || panelX != pressStartPanelX
                || panelY != pressStartPanelY;
            return true;
        }
        if (collapsed) return false;
        return surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (collapsed) return false;
        return surface.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return surface.charTyped(chr, modifiers);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean v) {
        visible = v;
        if (!v) {
            capturingIndex = -1;
            dragging = false;
            dragMoved = false;
        } else {
            windowNode.syncShowBody(!collapsed);
            PackUtilOverlayManager.get().bringToFront(this);
        }
    }

    public void toggle() {
        setVisible(!visible);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        PackUiViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mouseX);
        float uiY = viewport.toUiY(mouseY);
        return uiX >= panelX && uiX < panelX + panelWidth
            && uiY >= panelY && uiY < panelY + panelHeight;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        PackUiViewport viewport = surface.viewport();
        return isOverDragBarUi(viewport.toUiX(mouseX), viewport.toUiY(mouseY));
    }

    public boolean isOverDragBarUi(float uiMouseX, float uiMouseY) {
        return uiMouseX >= panelX && uiMouseX < closeButtonX() - headerControlInset()
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
    }

    public boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
        return uiMouseX >= panelX && uiMouseX < panelX + panelWidth
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean c) {
        collapsed = c;
        windowNode.syncShowBody(!collapsed);
    }

    @Override
    public boolean hasTextFieldFocused() {
        return surface.hasFocusedTextInput();
    }

    @Override
    public boolean wantsKeyboardCapture() {
        return visible && capturingIndex >= 0;
    }

    @Override
    public void clearTextFieldFocus() {
        surface.clearFocusedTextInputs();
    }

    public boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
        return PackUiHeaderControls.isCloseHit(closeVisibility, uiMouseX, uiMouseY, closeButtonX(), controlButtonY(), headerControlSize());
    }

    public int controlButtonY() {
        return PackUiHeaderControls.controlY(panelY, theme.headerHeight(), headerControlSize());
    }

    public int closeButtonX() {
        return PackUiHeaderControls.closeX(panelX, panelWidth, headerControlSize(), headerControlInset());
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

    public int computeLabelColumnWidth() {
        if (MC.font == null) return labelColumnFallbackWidth();
        List<String> labels = new ArrayList<>(entries.size());
        for (KeybindEntry entry : entries) labels.add(entry.label);
        return Math.max(labelColumnMinimumWidth(), PackUiSizing.measureWidestText(MC.font, theme.fontFor(PackUiTone.BODY), theme.color(PackUiTone.BODY), labels) + labelColumnExtraWidth());
    }

    public int computeBodyWidth() {
        if (MC.font == null) return MIN_PANEL_WIDTH - (PAD * 2);
        int keybindWidth = computeLabelColumnWidth() + rowLabelGap() + bindButtonWidth() + buttonGap() + clearButtonWidth();
        return keybindWidth;
    }

    public int computePanelWidth() {
        if (MC.font == null) return MIN_PANEL_WIDTH;
        int headerReserve = headerControlSize() + headerArrowWidth() + headerArrowGap() + (panelPadding() * 2) + 14;
        int titleWidth = PackUiText.width(MC.font, WINDOW_TITLE, theme.fontFor(PackUiTone.TITLE), theme.color(PackUiTone.TITLE));
        return Math.max(panelMinimumWidth(), Math.max(computeBodyWidth() + (panelPadding() * 2), titleWidth + headerReserve + panelPadding()));
    }

    public String bindButtonText(int index, KeybindEntry entry) {
        if (capturingIndex == index) return "PRESS";
        int keyCode = entry.getter.getAsInt();
        return keyCode == -1 ? "NONE" : getKeyName(keyCode);
    }

    public float animate(float current, float target, float delta) {
        return PackUiHeaderControls.animate(current, target, delta);
    }

    public final class ToggleRowNode extends PackUiFormRow {
        public final java.util.function.BooleanSupplier getter;
        public final java.util.function.Consumer<Boolean> setter;
        public final String tooltip;
        public final PackUiLabel labelNode;
        public final PackUiButton toggleButton;

        public ToggleRowNode(String label, String tooltip,
                              java.util.function.BooleanSupplier getter,
                              java.util.function.Consumer<Boolean> setter) {
            super(
                new PackUiLabel(label, PackUiTone.BODY).setTrimToBounds(true),
                new PackUiButton("OFF", PackUiButton.Variant.SECONDARY, null)
                    .setPreferredWidth(bindButtonWidth())
                    .setMinWidth(bindButtonWidth())
                    .setMaxWidth(bindButtonWidth())
                    .setHorizontalPadding(bindButtonPadding())
                    .setButtonHeight(chooserButtonHeight())
                    .setTextYOffset(0)
            );
            this.getter = getter;
            this.setter = setter;
            this.tooltip = tooltip;
            this.labelNode = (PackUiLabel) labelNode();
            this.toggleButton = (PackUiButton) controlNode();
            this.toggleButton.setOnPress(() -> {
                setter.accept(!getter.getAsBoolean());
                getConfig().save();
            });
            this.height = ROW_HEIGHT;
            this.growX = true;
            setLabelWidth(computeLabelColumnWidth());
            setGap(rowLabelGap());
            setAlignControlEnd(true);
            setPadding(PackUiInsets.NONE);
        }

        @Override
        public float preferredWidth(PackUiRenderContext context) {
            return panelWidth - theme.scale(PAD * 2);
        }

        @Override
        public float preferredHeight(PackUiRenderContext context, float availableWidth) {
            return rowHeight();
        }

        @Override
        public void render(PackUiRenderContext context) {
            boolean on = getter.getAsBoolean();
            toggleButton.setText(on ? "ON" : "OFF");
            toggleButton.setVariant(on ? PackUiButton.Variant.SUCCESS : PackUiButton.Variant.SECONDARY);
            super.render(context);
            if (labelNode.contains(context.mouseX(), context.mouseY())) {
                hoveredTooltip = tooltip;
                tooltipUiX = context.mouseX() + tooltipOffsetX();
                tooltipUiY = context.mouseY() - tooltipOffsetY();
            }
        }
    }

    public final class KeybindRowNode extends PackUiFormRow {
        public final int index;
        public final KeybindEntry entry;
        public final PackUiLabel labelNode;
        public final PackUiCompactRow controlRow;
        public final PackUiButton bindButton;
        public final PackUiButton clearButton;

        public KeybindRowNode(int index, KeybindEntry entry) {
            super(new PackUiLabel(entry.label, PackUiTone.BODY).setTrimToBounds(true), new PackUiCompactRow());
            this.index = index;
            this.entry = entry;
            this.height = ROW_HEIGHT;
            this.growX = true;
            this.labelNode = (PackUiLabel) labelNode();
            this.controlRow = (PackUiCompactRow) controlNode();
            this.bindButton = controlRow.add(new PackUiButton("NONE", PackUiButton.Variant.SECONDARY, this::toggleCapture)
                .setPreferredWidth(bindButtonWidth())
                .setMinWidth(bindButtonWidth())
                .setMaxWidth(bindButtonWidth())
                .setHorizontalPadding(bindButtonPadding())
                .setButtonHeight(chooserButtonHeight())
                .setTextYOffset(0));
            this.clearButton = controlRow.add(new PackUiButton("X", PackUiButton.Variant.DANGER, this::clearBind)
                .setPreferredWidth(clearButtonWidth())
                .setMinWidth(clearButtonWidth())
                .setMaxWidth(clearButtonWidth())
                .setHorizontalPadding(0)
                .setButtonHeight(chooserButtonHeight())
                .setTextYOffset(0));
            controlRow.setGap(buttonGap()).setPadding(PackUiInsets.NONE).setUnderlineOnHover(false);
            setLabelWidth(computeLabelColumnWidth());
            setGap(rowLabelGap());
            setAlignControlEnd(true);
            setPadding(PackUiInsets.NONE);
        }

        @Override
        public float preferredWidth(PackUiRenderContext context) {
            return panelWidth - (panelPadding() * 2);
        }

        @Override
        public float preferredHeight(PackUiRenderContext context, float availableWidth) {
            return rowHeight();
        }

        @Override
        public void render(PackUiRenderContext context) {
            bindButton.setText(bindButtonText(index, entry));
            bindButton.setVariant(capturingIndex == index
                ? PackUiButton.Variant.PRIMARY
                : entry.getter.getAsInt() == -1 ? PackUiButton.Variant.GHOST : PackUiButton.Variant.SECONDARY);
            labelNode.setTone(PackUiTone.BODY);

            super.render(context);

            if (labelNode.contains(context.mouseX(), context.mouseY())) {
                hoveredTooltip = entry.tooltip;
                tooltipUiX = context.mouseX() + tooltipOffsetX();
                tooltipUiY = context.mouseY() - tooltipOffsetY();
            }
        }

        @Override
        public boolean mouseClicked(PackUiRenderContext context, float mouseX, float mouseY, int button) {
            return super.mouseClicked(context, mouseX, mouseY, button);
        }

        public void toggleCapture() {
            capturingIndex = capturingIndex == index ? -1 : index;
        }

        public void clearBind() {
            entry.setter.accept(-1);
            getConfig().save();
            if (capturingIndex == index) capturingIndex = -1;
        }
    }

    public static class KeybindEntry {
        final String label;
        final String tooltip;
        final java.util.function.IntSupplier getter;
        final java.util.function.IntConsumer setter;

        KeybindEntry(String label, String tooltip, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
            this.label = label;
            this.tooltip = tooltip;
            this.getter = getter;
            this.setter = setter;
        }
    }

    public int panelMinimumWidth() {
        return 196;
    }

    public int panelPadding() {
        return 5;
    }

    public int windowContentGap() {
        return 2;
    }

    public int bodyMinimumHeight() {
        return 20;
    }

    public int headerControlSize() {
        return 11;
    }

    public int headerControlInset() {
        return 2;
    }

    public int headerArrowWidth() {
        return 10;
    }

    public int headerArrowGap() {
        return 3;
    }

    public int iconYOffset() {
        return 1;
    }

    public int tooltipMaxWidth() {
        return 180;
    }

    public int tooltipPadding() {
        return 3;
    }

    public int tooltipOffsetX() {
        return 8;
    }

    public int tooltipOffsetY() {
        return 2;
    }

    public int labelColumnFallbackWidth() {
        return 90;
    }

    public int labelColumnMinimumWidth() {
        return 74;
    }

    public int labelColumnExtraWidth() {
        return 2;
    }

    public int rowLabelGap() {
        return 4;
    }

    public int buttonGap() {
        return 2;
    }

    public int bindButtonWidth() {
        return 36;
    }

    public int clearButtonWidth() {
        return 12;
    }

    public int bindButtonPadding() {
        return 3;
    }

    public int chooserButtonHeight() {
        return 16;
    }

    public int rowHeight() {
        return 19;
    }
}
