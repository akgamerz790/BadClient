package dev.xaihi.autismclient.common.util;

import dev.xaihi.autismclient.common.gui.packui.PackUiAssets;
import dev.xaihi.autismclient.common.gui.packui.PackUiLegacyLayout;
import dev.xaihi.autismclient.common.gui.packui.PackUiListRenderer;
import dev.xaihi.autismclient.common.gui.packui.PackUiOverlayButton;
import dev.xaihi.autismclient.common.gui.packui.PackUiScrollbar;
import dev.xaihi.autismclient.common.gui.packui.PackUiSmoothScroll;
import dev.xaihi.autismclient.common.gui.packui.PackUiSizing;
import dev.xaihi.autismclient.common.gui.packui.PackUiText;
import dev.xaihi.autismclient.common.gui.packui.PackUiTheme;
import dev.xaihi.autismclient.common.gui.packui.PackUiTone;
import dev.xaihi.autismclient.common.modules.PackUtilModule;
import dev.xaihi.autismclient.common.util.PackUtilSharedState.QueuedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class PackUtilQueueEditorOverlay extends PackUtilOverlayBase {
    public static final Minecraft MC = Minecraft.getInstance();

    public int panelX = 100;
    public int panelY = 100;
    public int PANEL_WIDTH = 248;
    public int PANEL_HEIGHT = 176;

    public boolean isDragging = false;
    public double dragOffsetX = 0;
    public double dragOffsetY = 0;

    public int scrollOffset = 0;
    public boolean collapsed = false;
    public boolean visible = false;
    public int selectedPacketId = -1;
    public boolean keepSelectedPacketVisible = false;
    public long lastObservedQueueChangeMs = 0L;
    public String lastObservedQueueSignature = "";
    public boolean pendingAutoReorder = false;
    public boolean scrollbarDragging = false;
    public int scrollbarGrabOffset = 0;

    public final Font textRenderer;
    public final PackUiTheme theme = new PackUiTheme();
    public final PackUiSmoothScroll listScrollState = new PackUiSmoothScroll();
    public List<QueuedPacket> cachedDisplayQueue = new ArrayList<>();
    public final List<ClickRegion> toolbarRegions = new ArrayList<>();
    public final List<RowRegion> rowRegions = new ArrayList<>();

    public String hoveredTooltip = null;
    public int tooltipX = 0;
    public int tooltipY = 0;

    public PackUtilQueueEditorOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        this.PANEL_WIDTH = defaultPanelWidth();
        this.PANEL_HEIGHT = defaultPanelHeight();
    }

    public static class ClickRegion {
        final int x;
        final int y;
        final int width;
        final int height;
        final Runnable action;

        public ClickRegion(int x, int y, int width, int height, Runnable action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.action = action;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public static class RowRegion {
        final QueuedPacket packet;
        final int x;
        final int y;
        final int width;
        final int height;
        final int delayX;
        final int delayWidth;
        final int removeX;
        final int removeWidth;

        public RowRegion(QueuedPacket packet, int x, int y, int width, int height, int delayX, int delayWidth, int removeX, int removeWidth) {
            this.packet = packet;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.delayX = delayX;
            this.delayWidth = delayWidth;
            this.removeX = removeX;
            this.removeWidth = removeWidth;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        public boolean overDelay(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) && mouseX >= delayX && mouseX < delayX + delayWidth;
        }

        public boolean overRemove(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) && mouseX >= removeX && mouseX < removeX + removeWidth;
        }
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
        return new PackUtilWindowLayout(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, visible, collapsed);
    }

    @Override
    public void setBounds(PackUtilWindowLayout bounds) {
        if (bounds == null) return;
        PackUtilWindowLayout clamped = clampToScreen(this, bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        PANEL_WIDTH = defaultPanelWidth();
        PANEL_HEIGHT = Math.max(getMinHeight(), Math.max(defaultPanelHeight(), clamped.height));
        visible = clamped.visible;
        collapsed = clamped.collapsed;
    }

    public boolean isCaptureMode() {
        return PackUtilSharedState.get().isCaptureMode();
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            scrollOffset = 0;
            listScrollState.jumpTo(0, 0);
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
        saveLayout();
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        List<QueuedPacket> displayQueue = cachedDisplayQueue.isEmpty() ? getCurrentQueue() : cachedDisplayQueue;
        int panelHeight = collapsed
            ? HEADER_HEIGHT
            : clampToScreen(this, new PackUtilWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(displayQueue), visible, collapsed)).height;
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
            && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        PackUtilWindowLayout bounds = getBounds();
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
            && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT
            && !isOverWindowControl(mouseX, mouseY, bounds);
    }

    public void saveState() {
        PackUtilSharedState shared = PackUtilSharedState.get();
        shared.setQueueEditorOverlayVisible(visible);
        shared.setQueueEditorOverlayX(panelX);
        shared.setQueueEditorOverlayY(panelY);
        saveLayout();
    }

    public void restoreState() {
        PackUtilSharedState shared = PackUtilSharedState.get();
        restoreLayout();
        this.visible = shared.isQueueEditorOverlayVisible();
        this.panelX = shared.getQueueEditorOverlayX();
        this.panelY = shared.getQueueEditorOverlayY();
    }

    public int getContentStartY() {
        return panelY + getContentStartOffset();
    }

    public int getContentStartOffset() {
        int toolbarRows = PackUtilLANSync.getInstance().isInSession() ? 4 : 3;
        return toolbarTopOffset() + (toolbarRows * toolbarButtonHeight()) + ((toolbarRows - 1) * toolbarGap()) + contentTopGap();
    }

    public int getListX() {
        return panelX + panelInset();
    }

    public int getListWidth() {
        return PackUiLegacyLayout.contentWidth(PANEL_WIDTH, panelInset());
    }

    public int getListContentWidth(boolean hasScrollbar) {
        return Math.max(40, PackUiLegacyLayout.reserveScrollbar(getListWidth(), hasScrollbar, listScrollbarGutter()));
    }

    public int getToolbarRowWidth() {
        return getListWidth();
    }

    public int fitToolbarButtonWidth(String label, int minWidth, int maxWidth) {
        return PackUiLegacyLayout.fitOverlayButtonWidth(textRenderer, theme, PackUiTone.BODY, label, 5, minWidth, maxWidth);
    }

    public int getListHeight(int panelHeight) {
        return Math.max(rowHeight() + 4, panelHeight - getContentStartOffset() - panelInset());
    }

    public int getlistViewportHeight(int panelHeight) {
        return alignViewportHeight(Math.max(0, getListHeight(panelHeight) - 2), rowHeight());
    }

    public int getListClipTop() {
        return getContentStartY() - 1;
    }

    public int getListClipBottom(int panelHeight) {
        return getListClipTop() + getlistViewportHeight(panelHeight);
    }

    public PackUiScrollbar.Metrics getScrollbarMetrics(int panelHeight) {
        int visibleHeight = getlistViewportHeight(panelHeight);
        int contentHeight = cachedDisplayQueue.size() * rowHeight();
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        return PackUiScrollbar.compute(
            contentHeight,
            Math.max(1, visibleHeight),
            getListX() + getListWidth() - 5,
            getListClipTop(),
            4,
            Math.max(1, getListHeight(panelHeight) - 2),
            listScrollState.tick(0.0f, maxScroll)
        );
    }

    public int getRemoveButtonX(int rowX, int rowWidth) {
        return rowX + rowWidth - rowButtonSize() - panelInset();
    }

    public int getDelayButtonWidth() {
        return fixedDelayButtonWidth();
    }

    public int getDelayButtonX(int rowX, int rowWidth) {
        return getRemoveButtonX(rowX, rowWidth) - 4 - getDelayButtonWidth();
    }

    public int getRowLabelMaxWidth(int rowX, int rowWidth) {
        return Math.max(40, getDelayButtonX(rowX, rowWidth) - (rowX + 8) - 6);
    }

    public int getPanelHeight(List<QueuedPacket> displayQueue) {
        int contentHeight = Math.min(displayQueue.size(), maxVisibleRows()) * rowHeight();
        return Math.max(getMinHeight(), getContentStartOffset() + contentHeight + contentBottomGap());
    }

    public QueuedPacket getSelectedPacket(List<QueuedPacket> queue) {
        if (selectedPacketId == -1) return null;
        for (QueuedPacket packet : queue) {
            if (packet.getId() == selectedPacketId) return packet;
        }
        selectedPacketId = -1;
        return null;
    }

    public void clearSelection() {
        selectedPacketId = -1;
    }

    public List<QueuedPacket> getCurrentQueue() {
        PackUtilSharedState shared = PackUtilSharedState.get();
        List<QueuedPacket> queue = shared.getStaggeredQueue();
        if (queue.isEmpty()) queue = shared.getDelayedPackets();
        return queue;
    }

    public int drawAutoToolbarButton(GuiGraphicsExtractor context, int x, int y, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action) {
        int width = PackUiLegacyLayout.fitOverlayButtonWidth(textRenderer, theme, PackUiTone.BODY, label, 5, 24, 96);
        PackUiOverlayButton button = PackUiOverlayButton.create(x, y, width, h, Component.literal(label), ignored -> action.run());
        button.setVariant(active ? PackUiOverlayButton.Variant.SUCCESS : PackUiOverlayButton.Variant.GHOST);
        button.active = true;
        PackUiOverlayButton.renderStyled(context, textRenderer, button, mouseX, mouseY);
        toolbarRegions.add(new ClickRegion(x, y, width, h, action));
        checkBtnHover(x, y, width, h, mouseX, mouseY, tip);
        return x + width;
    }

    public int drawAutoToolbarButton(GuiGraphicsExtractor context, int x, int y, int h, String label, int mouseX, int mouseY, String tip, Runnable action) {
        return drawAutoToolbarButton(context, x, y, h, label, false, mouseX, mouseY, tip, action);
    }

    public int drawAutoToolbarStatusButton(GuiGraphicsExtractor context, int x, int y, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action) {
        int width = PackUiLegacyLayout.fitOverlayButtonWidth(textRenderer, theme, PackUiTone.BODY, label, 5, 24, 112);
        PackUiOverlayButton button = PackUiOverlayButton.create(x, y, width, h, Component.literal(label), ignored -> action.run());
        button.setVariant(active ? PackUiOverlayButton.Variant.SUCCESS : PackUiOverlayButton.Variant.GHOST);
        button.active = true;
        PackUiOverlayButton.renderStyled(context, textRenderer, button, mouseX, mouseY);
        toolbarRegions.add(new ClickRegion(x, y, width, h, action));
        checkBtnHover(x, y, width, h, mouseX, mouseY, tip);
        return x + width;
    }

    public void drawFixedToolbarButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action) {
        drawFixedToolbarButton(context, x, y, w, h, label, PackUiOverlayButton.Variant.SECONDARY, active, mouseX, mouseY, tip, action);
    }

    public void drawFixedToolbarButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, PackUiOverlayButton.Variant variant, boolean active, int mouseX, int mouseY, String tip, Runnable action) {
        PackUiOverlayButton button = PackUiOverlayButton.create(x, y, w, h, Component.literal(label), ignored -> action.run());
        button.setVariant(variant);
        button.active = active;
        PackUiOverlayButton.renderStyled(context, textRenderer, button, mouseX, mouseY);
        toolbarRegions.add(new ClickRegion(x, y, w, h, action));
        checkBtnHover(x, y, w, h, mouseX, mouseY, tip);
    }

    public void drawFixedToolbarButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, int mouseX, int mouseY, String tip, Runnable action) {
        drawFixedToolbarButton(context, x, y, w, h, label, false, mouseX, mouseY, tip, action);
    }

    public void drawFixedToolbarStateButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean enabled, int mouseX, int mouseY, String tip, Runnable action) {
        PackUiOverlayButton button = PackUiOverlayButton.create(x, y, w, h, Component.literal(label), ignored -> action.run());
        button.setVariant(enabled ? PackUiOverlayButton.Variant.SUCCESS : PackUiOverlayButton.Variant.DANGER);
        PackUiOverlayButton.renderStyled(context, textRenderer, button, mouseX, mouseY);
        toolbarRegions.add(new ClickRegion(x, y, w, h, action));
        checkBtnHover(x, y, w, h, mouseX, mouseY, tip);
    }

    public boolean isQueueSortedByDelay(List<QueuedPacket> queue) {
        for (int i = 1; i < queue.size(); i++) {
            QueuedPacket previous = queue.get(i - 1);
            QueuedPacket current = queue.get(i);
            if (previous.getDelay() > current.getDelay()) return false;
            if (previous.getDelay() == current.getDelay() && previous.getId() > current.getId()) return false;
        }
        return true;
    }

    public String buildQueueSignature(List<QueuedPacket> queue) {
        if (queue.isEmpty()) return "";

        StringBuilder signature = new StringBuilder(queue.size() * 16);
        for (QueuedPacket packet : queue) {
            signature.append(packet.getId()).append(':').append(packet.getDelay()).append(';');
        }
        return signature.toString();
    }

    public void observeQueueChanges(List<QueuedPacket> queue) {
        String signature = buildQueueSignature(queue);
        if (!signature.equals(lastObservedQueueSignature)) {
            lastObservedQueueSignature = signature;
            lastObservedQueueChangeMs = System.currentTimeMillis();
            pendingAutoReorder = true;
        }
    }

    public void maybeAutoReorderDelayedQueue(PackUtilSharedState shared) {
        List<QueuedPacket> delayedQueue = shared.getDelayedPackets();
        observeQueueChanges(delayedQueue);
        if (delayedQueue.size() < 2) return;
        if (!pendingAutoReorder) return;
        if (System.currentTimeMillis() - lastObservedQueueChangeMs < 800L) return;
        if (isQueueSortedByDelay(delayedQueue)) {
            pendingAutoReorder = false;
            return;
        }

        shared.sortDelayedPacketsByDelayPreservingIds();
        lastObservedQueueSignature = buildQueueSignature(shared.getDelayedPackets());
        pendingAutoReorder = false;
        keepSelectedPacketVisible = true;
    }

    public void ensureSelectedPacketVisible(List<QueuedPacket> queue, QueuedPacket selectedPacket, int panelHeight) {
        if (selectedPacket == null || queue.isEmpty()) return;

        int selectedIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == selectedPacket.getId()) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < 0) return;

        int visibleHeight = getlistViewportHeight(panelHeight);
        int maxScroll = Math.max(0, queue.size() * rowHeight() - visibleHeight);
        int rowTop = selectedIndex * rowHeight();
        int rowBottom = rowTop + rowHeight();

        if (rowTop < scrollOffset) {
            scrollOffset = rowTop;
        } else if (rowBottom > scrollOffset + visibleHeight) {
            scrollOffset = rowBottom - visibleHeight;
        }

        scrollOffset = quantizeScrollOffset(scrollOffset, rowHeight(), maxScroll);
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        PackUtilSharedState shared = PackUtilSharedState.get();
        List<QueuedPacket> staggeredQueue = shared.getStaggeredQueue();
        if (staggeredQueue.isEmpty()) {
            maybeAutoReorderDelayedQueue(shared);
            cachedDisplayQueue = shared.getDelayedPackets();
        } else {
            cachedDisplayQueue = new ArrayList<>(staggeredQueue);
        }
        QueuedPacket selectedPacket = getSelectedPacket(cachedDisplayQueue);
        PackUtilWindowLayout clamped = clampToScreen(
            this,
            new PackUtilWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(cachedDisplayQueue), visible, collapsed)
        );
        panelX = clamped.x;
        panelY = clamped.y;
        PANEL_WIDTH = clamped.width;
        int panelHeight = clamped.height;
        PANEL_HEIGHT = panelHeight;
        if (!collapsed && keepSelectedPacketVisible) {
            ensureSelectedPacketVisible(cachedDisplayQueue, selectedPacket, panelHeight);
            keepSelectedPacketVisible = false;
        }

        toolbarRegions.clear();
        rowRegions.clear();
        hoveredTooltip = null;

        String title = "Queue (" + cachedDisplayQueue.size() + ")";
        PackUtilWindowLayout bounds = new PackUtilWindowLayout(panelX, panelY, PANEL_WIDTH, panelHeight, visible, collapsed);
        renderWindowFrame(context, mouseX, mouseY, bounds, title, collapsed, isDragging);
        boolean clipBody = beginWindowBodyClip(context, bounds, collapsed);
        if (!clipBody) {
            renderWindowInactiveOverlay(context, bounds, collapsed, isDragging);
            return;
        }

        try {
        int gap = toolbarGap();
        int row1Y = panelY + toolbarTopOffset();
        int row2Y = row1Y + toolbarButtonHeight() + gap;
        int row3Y = row2Y + toolbarButtonHeight() + gap;
        int row4Y = row3Y + toolbarButtonHeight() + gap;
        int leftX = panelX + panelInset();
        int rowWidth = getToolbarRowWidth();
        int rowRight = leftX + rowWidth;

        int bx = leftX;
        bx = drawAutoToolbarButton(context, bx, row1Y, toolbarButtonHeight(), "Copy", mouseX, mouseY,
            "Copy queue to clipboard (serialized)",
            () -> {
                PackUtilClipboardHelper.copyToClipboard(getCurrentQueue());
                PackUtilClientMessaging.sendPrefixed("Ã‚Â§aQueue copied!");
            }) + gap;
        bx = drawAutoToolbarButton(context, bx, row1Y, toolbarButtonHeight(), "Paste", mouseX, mouseY,
            "Replace queue from clipboard",
            () -> {
                if (PackUtilSharedState.get().getStaggeredQueue().isEmpty()) {
                    PackUtilSharedState.QueuedPacket.resetIdCounter();
                }
                List<QueuedPacket> newQueue = PackUtilClipboardHelper.pasteFromClipboard();
                if (newQueue != null) {
                    PackUtilSharedState.get().setDelayedPackets(newQueue);
                    clearSelection();
                    scrollOffset = 0;
                    PackUtilClientMessaging.sendPrefixed("Ã‚Â§aQueue pasted!");
                }
            }) + gap;
        bx = drawAutoToolbarButton(context, bx, row1Y, toolbarButtonHeight(), "Dupe", mouseX, mouseY,
            "Duplicate all packets in the queue",
            () -> {
                List<QueuedPacket> currentQueue = PackUtilSharedState.get().getDelayedPackets();
                if (!currentQueue.isEmpty()) {
                    List<QueuedPacket> duplicatedQueue = new ArrayList<>(currentQueue);
                    for (QueuedPacket packet : currentQueue) duplicatedQueue.add(new QueuedPacket(packet.packet, packet.getDelay()));
                    PackUtilSharedState.get().setDelayedPackets(duplicatedQueue);
                    clearSelection();
                    PackUtilClientMessaging.sendPrefixed("Ã‚Â§aDuplicated " + currentQueue.size() + " packet(s).");
                }
            }) + gap;
        bx = drawAutoToolbarButton(context, bx, row1Y, toolbarButtonHeight(), "Ins", mouseX, mouseY,
            "Insert clipboard packets at the end of the queue",
            () -> {
                List<QueuedPacket> newPackets = PackUtilClipboardHelper.pasteFromClipboard();
                if (newPackets != null && !newPackets.isEmpty()) {
                    List<QueuedPacket> combinedQueue = new ArrayList<>(PackUtilSharedState.get().getDelayedPackets());
                    combinedQueue.addAll(newPackets);
                    PackUtilSharedState.get().setDelayedPackets(combinedQueue);
                    clearSelection();
                    PackUtilClientMessaging.sendPrefixed("Ã‚Â§aInserted " + newPackets.size() + " packet(s).");
                }
            }) + gap;
        int replayWidth = Math.max(fitToolbarButtonWidth("Replay", 48, 84), rowRight - bx);
        drawFixedToolbarButton(context, bx, row1Y, replayWidth, toolbarButtonHeight(), "Replay", PackUiOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Restore the last flushed queue back into the editor",
            () -> {
                if (PackUtilSharedState.get().restoreLastFlushedQueue()) {
                    clearSelection();
                    scrollOffset = 0;
                    PackUtilClientMessaging.sendPrefixed("Ã‚Â§aRestored - packets will regenerate on send");
                } else {
                    PackUtilClientMessaging.sendPrefixed("Ã‚Â§cNo history");
                }
            });

        boolean flushOnDelay = PackUtilModule.get().shouldFlushQueueOnDelayDisable();
        int clearWidth = fitToolbarButtonWidth("Clear", 42, 58);
        int flushWidth = Math.max(fitToolbarButtonWidth("FLUSH ON DELAY", 120, 164), rowWidth - clearWidth - gap);
        drawFixedToolbarStateButton(context, leftX, row2Y, flushWidth, toolbarButtonHeight(), "FLUSH ON DELAY", flushOnDelay, mouseX, mouseY,
            flushOnDelay
                ? "When delay turns off, queued packets auto-flush"
                : "When delay turns off, keep the queued packets until you flush or clear them",
            () -> {
                PackUtilModule module = PackUtilModule.get();
                boolean newValue = !module.shouldFlushQueueOnDelayDisable();
                module.setFlushQueueOnDelayDisable(newValue);
                PackUtilClientMessaging.sendPrefixed(newValue
                    ? "\u00a7aFlush on Delay ON"
                    : "\u00a7eFlush on Delay OFF");
            });
        drawFixedToolbarButton(context, leftX + flushWidth + gap, row2Y, clearWidth, toolbarButtonHeight(), "Clear", PackUiOverlayButton.Variant.DANGER, true, mouseX, mouseY,
            "Remove all packets from the queue",
            () -> {
                int cleared = PackUtilSharedState.get().clearQueuedPackets();
                clearSelection();
                scrollOffset = 0;
                if (cleared > 0) {
                    PackUtilClientMessaging.sendPrefixed("\u00a7aCleared " + cleared + " packet" + (cleared == 1 ? "" : "s"));
                } else {
                    PackUtilClientMessaging.sendPrefixed("\u00a7cQueue empty");
                }
            });

        boolean captureModeOn = PackUtilSharedState.get().isCaptureMode();
        String delayModeLabel = PackUtilSharedState.get().getDelayMode() == PackUtilSharedState.DelayMode.TICKS ? "Ticks" : "Ms";
        int modeWidth = fitToolbarButtonWidth(delayModeLabel, 34, 44);
        int captureWidth = fitToolbarButtonWidth("Capture", 56, 72);
        int plus1Width = fitToolbarButtonWidth("+1", 22, 28);
        int plus10Width = fitToolbarButtonWidth("+10", 26, 34);
        int plus20Width = fitToolbarButtonWidth("+20", 26, 34);
        int sendWidth = Math.max(fitToolbarButtonWidth("Send Q", 54, 82), rowWidth - modeWidth - captureWidth - plus1Width - plus10Width - plus20Width - (gap * 5));
        drawFixedToolbarButton(context, leftX, row3Y, modeWidth, toolbarButtonHeight(), delayModeLabel, PackUiOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Toggle delay unit: Ticks or milliseconds",
            () -> {
                PackUtilSharedState.get().toggleDelayMode();
                String newMode = PackUtilSharedState.get().getDelayMode() == PackUtilSharedState.DelayMode.TICKS ? "Ticks" : "Ms";
                PackUtilClientMessaging.sendPrefixed("\u00a7aDelay mode: " + newMode);
            });
        drawFixedToolbarButton(context, leftX + modeWidth + gap, row3Y, captureWidth, toolbarButtonHeight(), "Capture", captureModeOn ? PackUiOverlayButton.Variant.SUCCESS : PackUiOverlayButton.Variant.DANGER, true, mouseX, mouseY,
            "Record real timing between captured packets",
            () -> {
                boolean newCapture = !PackUtilSharedState.get().isCaptureMode();
                PackUtilSharedState.get().setCaptureMode(newCapture);
                PackUtilClientMessaging.sendPrefixed(newCapture ? "\u00a7aCapture Real Delays ON" : "\u00a7eCapture Real Delays OFF");
            });
        int adjustX = leftX + modeWidth + gap + captureWidth + gap;
        drawFixedToolbarButton(context, adjustX, row3Y, plus1Width, toolbarButtonHeight(), "+1", PackUiOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Add +1 to the selected packet, or apply a +1 incremental pattern to the whole queue",
            () -> handleIncrementalDelay(1));
        drawFixedToolbarButton(context, adjustX + plus1Width + gap, row3Y, plus10Width, toolbarButtonHeight(), "+10", PackUiOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Add +10 to the selected packet, or apply a +10 incremental pattern to the whole queue",
            () -> handleIncrementalDelay(10));
        drawFixedToolbarButton(context, adjustX + plus1Width + gap + plus10Width + gap, row3Y, plus20Width, toolbarButtonHeight(), "+20", PackUiOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Add +20 to the selected packet, or apply a +20 incremental pattern to the whole queue",
            () -> handleIncrementalDelay(20));
        drawFixedToolbarButton(context, rowRight - sendWidth, row3Y, sendWidth, toolbarButtonHeight(), "Send Q", PackUiOverlayButton.Variant.SUCCESS, true, mouseX, mouseY,
            "Send all queued packets to the server",
            () -> {
                if (MC.getConnection() != null) {
                    int count = PackUtilSharedState.get().flushDelayedPackets(MC.getConnection());
                    if (count > 0) {
                        if (PackUtilSharedState.get().isStaggering()) {
                            PackUtilSharedState.get().setPendingQueueCompletionMessage("Sent " + count + " packets.");
                        } else {
                            PackUtilClientMessaging.sendPrefixed("Sent " + count + " packets.");
                        }
                    } else {
                        PackUtilClientMessaging.sendPrefixed("Queue empty.");
                    }
                }
            });

        boolean inLanSession = PackUtilLANSync.getInstance().isInSession();
        if (inLanSession) {
            int syncWidth = fitToolbarButtonWidth("Sync", 46, 58);
            int syncExecWidth = Math.max(fitToolbarButtonWidth("Sync Exec", 92, 118), rowWidth - syncWidth - gap);
            drawFixedToolbarButton(context, leftX, row4Y, syncWidth, toolbarButtonHeight(), "Sync", PackUiOverlayButton.Variant.GHOST, true, mouseX, mouseY,
                "Broadcast this queue to all LAN peers",
                this::handleSyncButton);
            drawFixedToolbarButton(context, leftX + syncWidth + gap, row4Y, syncExecWidth, toolbarButtonHeight(), "Sync Exec", PackUiOverlayButton.Variant.SUCCESS, true, mouseX, mouseY,
                "Tell LAN peers to execute their queues together",
                () -> PackUtilLANSync.getInstance().sendQueuedPackets());
        }

        if (cachedDisplayQueue.isEmpty()) {
            PackUtilText.draw(context, textRenderer, "Queue empty", PackUtilText.Tone.MUTED, panelX + 10, getContentStartY(), false);
        } else {
            int visibleHeight = getlistViewportHeight(panelHeight);
            int totalContentHeight = cachedDisplayQueue.size() * rowHeight();
            int maxScroll = Math.max(0, totalContentHeight - visibleHeight);
            scrollOffset = quantizeScrollOffset(scrollOffset, rowHeight(), maxScroll);
            listScrollState.setTarget(scrollOffset, maxScroll);
            int drawScroll = listScrollState.tick(delta, maxScroll);

            int contentStartY = getContentStartY();
            int listX = getListX();
            int listY = contentStartY - 2;
            int listWidth = getListWidth();
            int listHeight = getListHeight(panelHeight);
            int clipTop = getListClipTop();
            int clipBottom = getListClipBottom(panelHeight);
            PackUiListRenderer.drawFrame(context, listX, listY, listWidth, listHeight, selectedPacket != null);
            PackUiScrollbar.Metrics scrollbarMetrics = getScrollbarMetrics(panelHeight);
            int listContentWidth = getListContentWidth(scrollbarMetrics.hasScroll());
            PackUtilUiScale.enableOverlayScissor(context, listX + 1, clipTop, listX + listWidth - 1, clipBottom);
            int baseY = contentStartY - drawScroll;
            int groupColorIndex = 0;
            int lastDelay = Integer.MIN_VALUE;

            for (int i = 0; i < cachedDisplayQueue.size(); i++) {
                QueuedPacket packet = cachedDisplayQueue.get(i);
                int itemY = baseY + i * rowHeight();
                int rowY = itemY - 1;
                if (rowY + rowHeight() <= clipTop || rowY >= clipBottom) continue;

                boolean grouped = false;
                if (i > 0 && cachedDisplayQueue.get(i - 1).getDelay() == packet.getDelay()) grouped = true;
                if (i < cachedDisplayQueue.size() - 1 && cachedDisplayQueue.get(i + 1).getDelay() == packet.getDelay()) grouped = true;

                int textColor = PackUtilColors.textPrimary();
                if (grouped) {
                    if (packet.getDelay() != lastDelay) groupColorIndex++;
                    textColor = (groupColorIndex % 2 == 0) ? 0xFF44CCFF : 0xFFFFAA44;
                }
                lastDelay = packet.getDelay();

                int rowX = listX + 1;
                int rowW = Math.max(36, listContentWidth - 2);
                boolean selected = selectedPacket != null && selectedPacket.getId() == packet.getId();
                boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY && mouseY < rowY + rowHeight();
                PackUiListRenderer.drawRow(
                    context,
                    textRenderer,
                    "",
                    rowX,
                    rowY,
                    rowW,
                    rowHeight(),
                    hovered,
                    selected,
                    grouped ? PackUiListRenderer.RowTone.WARNING : PackUiListRenderer.RowTone.NORMAL
                );

                String label = "#" + packet.getId() + " " + PackUtilPacketNamer.getFriendlyName(packet.packet);
                String delayText = String.valueOf(packet.getDelay());
                int labelMaxWidth = getRowLabelMaxWidth(rowX, rowW);
                String trimmed = PackUiText.width(textRenderer, label, theme.fontFor(PackUiTone.BODY), textColor) > labelMaxWidth
                    ? PackUiText.trimToWidth(textRenderer, label, Math.max(1, labelMaxWidth), theme.fontFor(PackUiTone.BODY), textColor)
                    : label;
                int rowTextY = PackUiSizing.alignTextY(rowY, rowHeight(), theme.fontHeight(PackUiTone.BODY), theme.bodyTextNudge());
                PackUiText.draw(context, textRenderer, trimmed, theme.fontFor(PackUiTone.BODY), selected ? PackUtilColors.rowSelectedText() : textColor, rowX + 7, rowTextY, false);

                int delayBtnWidth = getDelayButtonWidth();
                int delayBtnX = getDelayButtonX(rowX, rowW);
                int buttonY = rowY + Math.max(0, (rowHeight() - rowButtonSize()) / 2);
                PackUiOverlayButton delayButton = PackUiOverlayButton.create(delayBtnX, buttonY, delayBtnWidth, rowButtonSize(), Component.literal(delayText), ignored -> {});
                delayButton.setVariant(PackUiOverlayButton.Variant.PRIMARY);
                PackUiOverlayButton.renderStyled(context, textRenderer, delayButton, mouseX, mouseY);

                int removeBtnX = getRemoveButtonX(rowX, rowW);
                boolean removeHovered = mouseX >= removeBtnX && mouseX < removeBtnX + rowButtonSize() && mouseY >= buttonY && mouseY < buttonY + rowButtonSize();
                PackUiListRenderer.drawIconButton(context, removeBtnX, buttonY, rowButtonSize(), PackUiAssets.ICON_WINDOW_CLOSE, removeHovered, true);
                PackUiListRenderer.drawDivider(context, rowX, rowY + rowHeight(), rowW);

                rowRegions.add(new RowRegion(packet, rowX, rowY, rowW, rowHeight(), delayBtnX, delayBtnWidth, removeBtnX, rowButtonSize()));
            }

            context.disableScissor();
            PackUiScrollbar.draw(context, scrollbarMetrics, scrollbarMetrics.contains(mouseX, mouseY), scrollbarDragging);
        }
        } finally {
            endWindowBodyClip(context, clipBody);
            renderWindowInactiveOverlay(context, bounds, collapsed, isDragging);
        }

        if (!collapsed && hoveredTooltip != null) {
            context.nextStratum();
            PackUiText.interOverlayFlush(context);
            drawTooltip(context, hoveredTooltip, tooltipX, tooltipY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        PackUtilSharedState shared = PackUtilSharedState.get();
        List<QueuedPacket> displayQueue = cachedDisplayQueue.isEmpty() ? getCurrentQueue() : cachedDisplayQueue;
        int panelHeight = collapsed
            ? HEADER_HEIGHT
            : clampToScreen(this, new PackUtilWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(displayQueue), visible, collapsed)).height;

        if (button == 0 && mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT) {
            PackUtilWindowLayout bounds = new PackUtilWindowLayout(panelX, panelY, PANEL_WIDTH, panelHeight, visible, collapsed);
            if (isOverCollapseButton(mouseX, mouseY, bounds)) {
                toggleCollapsed();
                return true;
            }
            if (isOverCloseButton(mouseX, mouseY, bounds)) {
                setVisible(false);
                return true;
            }
            isDragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return true;
        }

        if (collapsed) return false;

        if (button == 0) {
            for (ClickRegion region : toolbarRegions) {
                if (region.contains(mouseX, mouseY)) {
                    region.action.run();
                    return true;
                }
            }
        }

        PackUiScrollbar.Metrics scrollbarMetrics = getScrollbarMetrics(panelHeight);
        if (button == 0 && scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(mouseX, mouseY)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = scrollbarMetrics.overThumb(mouseX, mouseY)
                ? (int) Math.round(mouseY) - scrollbarMetrics.thumbY()
                : scrollbarMetrics.thumbHeight() / 2;
            scrollOffset = quantizeScrollOffset(PackUiScrollbar.scrollFromThumb(scrollbarMetrics, mouseY, scrollbarGrabOffset), rowHeight(), scrollbarMetrics.maxScroll());
            listScrollState.jumpTo(scrollOffset, scrollbarMetrics.maxScroll());
            return true;
        }

        for (RowRegion region : rowRegions) {
            if (!region.contains(mouseX, mouseY)) continue;

            if (region.overRemove(mouseX, mouseY) && button == 0) {
                shared.removeQueuedPacket(region.packet);
                if (selectedPacketId == region.packet.getId()) clearSelection();
                return true;
            }

            if (region.overDelay(mouseX, mouseY)) {
                if (button == 0) {
                    shared.updatePacketDelay(region.packet, region.packet.getDelay() + 1);
                    return true;
                }
                if (button == 1) {
                    shared.updatePacketDelay(region.packet, Math.max(0, region.packet.getDelay() - 1));
                    return true;
                }
            }

            if (button == 0) {
                selectedPacketId = selectedPacketId == region.packet.getId() ? -1 : region.packet.getId();
                return true;
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && button == 0) {
            PackUtilWindowLayout nextBounds = clampToScreen(this,
                new PackUtilWindowLayout((int) (mouseX - dragOffsetX), (int) (mouseY - dragOffsetY),
                    PANEL_WIDTH, PANEL_HEIGHT, visible, collapsed));
            panelX = nextBounds.x;
            panelY = nextBounds.y;
            saveState();
            return true;
        }
        if (scrollbarDragging && button == 0) {
            List<QueuedPacket> displayQueue = cachedDisplayQueue.isEmpty() ? getCurrentQueue() : cachedDisplayQueue;
            int panelHeight = collapsed
                ? HEADER_HEIGHT
                : clampToScreen(this, new PackUtilWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(displayQueue), visible, collapsed)).height;
            PackUiScrollbar.Metrics scrollbarMetrics = getScrollbarMetrics(panelHeight);
            scrollOffset = quantizeScrollOffset(PackUiScrollbar.scrollFromThumb(scrollbarMetrics, mouseY, scrollbarGrabOffset), rowHeight(), scrollbarMetrics.maxScroll());
            listScrollState.jumpTo(scrollOffset, scrollbarMetrics.maxScroll());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            saveState();
            return true;
        }
        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed || !isMouseOver(mouseX, mouseY)) return false;

        List<QueuedPacket> displayQueue = cachedDisplayQueue.isEmpty() ? getCurrentQueue() : cachedDisplayQueue;
        int panelHeight = clampToScreen(this, new PackUtilWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(displayQueue), visible, collapsed)).height;
        int visibleHeight = getlistViewportHeight(panelHeight);
        int maxScroll = Math.max(0, displayQueue.size() * rowHeight() - visibleHeight);
        if (maxScroll <= 0) return false;

        scrollOffset = quantizeScrollOffset(scrollOffset - (int) (Math.signum(amount) * rowHeight()), rowHeight(), maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    public void handleSyncButton() {
        PackUtilLANSync sync = PackUtilLANSync.getInstance();
        if (!sync.isInSession()) {
            PackUtilClientMessaging.sendPrefixed("\u00a7cNot in LAN session");
            return;
        }

        List<QueuedPacket> queue = getCurrentQueue();
        if (queue.isEmpty()) {
            PackUtilClientMessaging.sendPrefixed("\u00a7cQueue is empty");
            return;
        }

        String queueData = PackUtilClipboardHelper.serializeQueueToBase64(queue);
        if (queueData == null) {
            PackUtilClientMessaging.sendPrefixed("\u00a7cFailed to serialize queue");
            return;
        }

        sync.broadcastQueueSync(queueData);
        PackUtilClientMessaging.sendPrefixed("\u00a7aQueue sent to " + sync.getConnectedClientCount() + " clients");
    }

    public void checkBtnHover(int x, int y, int w, int h, int mx, int my, String tip) {
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            hoveredTooltip = tip;
            tooltipX = mx + 8;
            tooltipY = my + 12;
        }
    }

    public void drawTooltip(GuiGraphicsExtractor ctx, String text, int x, int y) {
        List<String> lines = wrapTooltipText(text, 200);
        int lineH = theme.lineHeight(PackUiTone.MUTED, 2);
        int maxW = 0;
        for (String line : lines) maxW = Math.max(maxW, PackUiText.width(textRenderer, line, theme.fontFor(PackUiTone.MUTED), theme.color(PackUiTone.MUTED)));
        int tw = maxW + 6;
        int th = lines.size() * lineH + 6;

        int screenW = PackUtilUiScale.getVirtualScreenWidth();
        int screenH = PackUtilUiScale.getVirtualScreenHeight();
        if (x + tw > screenW - 4) x = screenW - tw - 4;
        if (y + th > screenH - 4) y = screenH - th - 4;

        PackUiText.fill(ctx, x - 2, y - 2, x + tw, y + th, PackUtilColors.tooltipBg());
        PackUiText.fill(ctx, x - 2, y - 2, x + tw, y - 1, theme.borderColor());
        PackUiText.fill(ctx, x - 2, y + th - 1, x + tw, y + th, theme.borderColor());
        PackUiText.fill(ctx, x - 2, y - 2, x - 1, y + th, theme.borderColor());
        PackUiText.fill(ctx, x + tw - 1, y - 2, x + tw, y + th, theme.borderColor());
        PackUiText.fill(ctx, x - 2, y - 2, x + tw, y - 1, theme.headerAccent());

        for (int i = 0; i < lines.size(); i++) {
            PackUiText.draw(ctx, textRenderer, lines.get(i), theme.fontFor(PackUiTone.MUTED), theme.color(PackUiTone.BODY), x + 2, y + 2 + i * lineH, false);
        }
    }

    public List<String> wrapTooltipText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String next = current.length() == 0 ? word : current + " " + word;
            if (PackUiText.width(textRenderer, next, theme.fontFor(PackUiTone.MUTED), theme.color(PackUiTone.MUTED)) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                if (current.length() > 0) current.append(" ");
                current.append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    public void handleIncrementalDelay(int increment) {
        List<QueuedPacket> queue = getCurrentQueue();
        if (queue.isEmpty()) {
            PackUtilClientMessaging.sendPrefixed("\u00a7cQueue is empty");
            return;
        }

        if (selectedPacketId != -1) {
            for (QueuedPacket packet : queue) {
                if (packet.getId() == selectedPacketId) {
                    packet.setDelay(packet.getDelay() + increment);
                    String modeStr = PackUtilSharedState.get().getQueueDisplayDelayMode() == PackUtilSharedState.DelayMode.TICKS ? "ticks" : "ms";
                    PackUtilClientMessaging.sendPrefixed("\u00a7aPacket #" + packet.getId() + " delay: " + packet.getDelay() + " " + modeStr);
                    return;
                }
            }
            clearSelection();
            PackUtilClientMessaging.sendPrefixed("\u00a7cSelected packet not found");
            return;
        }

        for (int i = 0; i < queue.size(); i++) {
            queue.get(i).setDelay(i * increment);
        }
        PackUtilClientMessaging.sendPrefixed("\u00a7aApplied +" + increment + " incremental delays");
    }

        public int defaultPanelWidth() {
        return 248;
    }

    public int defaultPanelHeight() {
        return 176;
    }

    public int minimumPanelHeight() {
        return 168;
    }

    public int rowHeight() {
        return 14;
    }

    public int rowButtonSize() {
        return 12;
    }

    public int toolbarButtonHeight() {
        return 13;
    }

    public int maxVisibleRows() {
        return 6;
    }

    public int fixedDelayButtonWidth() {
        return 40;
    }

    public int listScrollbarGutter() {
        return 12;
    }

    public int panelInset() {
        return 4;
    }

    public int toolbarGap() {
        return 2;
    }

    public int toolbarTopOffset() {
        return HEADER_HEIGHT + 4;
    }

    public int contentTopGap() {
        return 6;
    }

    public int contentBottomGap() {
        return 6;
    }
}
