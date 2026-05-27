package dev.xaihi.autismclient.common.gui.screen;

import dev.xaihi.autismclient.common.gui.packui.PackUiOverlayButton;
import dev.xaihi.autismclient.common.gui.packui.PackUiScrollbar;
import dev.xaihi.autismclient.common.gui.packui.PackUiSmoothScroll;
import dev.xaihi.autismclient.common.gui.packui.PackUiText;
import dev.xaihi.autismclient.common.gui.packui.PackUiTheme;
import dev.xaihi.autismclient.common.gui.packui.PackUiToastStack;
import dev.xaihi.autismclient.common.gui.packui.PackUiTone;
import dev.xaihi.autismclient.common.util.PackUtilProxy;
import dev.xaihi.autismclient.common.util.PackUtilProxyManager;
import dev.xaihi.autismclient.common.util.PackUtilProxyType;
import dev.xaihi.autismclient.common.util.PackUtilUiScale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PackUtilProxiesScreen extends Screen {
    public static final PackUiTheme THEME = new PackUiTheme();
    public static final int BG = 0xFF0E0E10;
    public static final int PANEL_BG = 0xE818181B;
    public static final int PANEL_BG_SOFT = 0xB8141417;
    public static final int BORDER = 0xFF332428;
    public static final int BORDER_ACTIVE = 0xFF35D873;
    public static final int TEXT = 0xFFF2F2F2;
    public static final int MUTED = 0xFF9A9A9A;
    public static final int SUCCESS = 0xFF35D873;
    public static final int ERROR = 0xFFFF5B5B;
    public static final int WARN = 0xFFFFC857;
    public static final int DEFAULT_COLOR = 0xFF8EA0FF;
    public static final int PANEL_WIDTH = 620;
    public static final int PANEL_MARGIN = 12;
    public static final int ROW_HEIGHT = 31;
    public static final int TOP_PANEL_Y = 20;
    public static final int TOP_PANEL_HEIGHT = 188;
    public static final int LIST_TOP = 210;
    public static final int LIST_HEADER_HEIGHT = 28;
    public static final int LIST_BOTTOM_MARGIN = 12;
    public static final int LIST_SCROLLBAR_WIDTH = 4;
    public static final int LIST_SCROLLBAR_GUTTER = 12;

    public final Screen parent;
    public final List<PackUiOverlayButton> buttons = new ArrayList<>();
    public final List<ProxyRow> proxyRows = new ArrayList<>();
    public final PackUiToastStack toastStack = new PackUiToastStack();
    public final PackUiSmoothScroll proxyListScroll = new PackUiSmoothScroll();
    public EditBox nameField;
    public EditBox addressField;
    public EditBox portField;
    public EditBox usernameField;
    public EditBox passwordField;
    public EditBox searchField;
    public PackUtilProxyType type = PackUtilProxyType.Socks5;
    public ProxyFilter filter = ProxyFilter.ALL;
    public PackUtilProxy selectedProxy;
    public String searchQuery = "";
    public int proxyListScrollOffset;
    public boolean proxyScrollbarDragging;
    public int proxyScrollbarGrabOffset;

    public PackUtilProxiesScreen(Screen parent) {
        super(Component.literal("Proxies"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int formX = panelX() + 10;
        this.nameField = addField(formX + 8, 54, 200, "Name");
        this.addressField = addField(formX + 216, 54, 300, "Address");
        this.portField = addField(formX + 524, 54, 72, "Port");
        this.usernameField = addField(formX + 8, 82, 295, "Username");
        this.passwordField = addField(formX + 311, 82, 285, "Password");
        this.searchField = addField(formX + 8, 146, 588, "Search proxies");
        this.searchField.setHint(Component.literal("Search name/address..."));
        this.searchField.setResponder(value -> {
            searchQuery = safeTrim(value);
            proxyListScrollOffset = 0;
            proxyListScroll.jumpTo(0, 0);
            rebuildButtons();
        });
        rebuildButtons();
    }

    public EditBox addField(int x, int y, int w, String hint) {
        EditBox field = new EditBox(this.font, x, y, w, 20, Component.literal(hint));
        field.setHint(Component.literal(hint));
        field.setMaxLength(256);
        this.addRenderableWidget(field);
        return field;
    }

    public void rebuildButtons() {
        buttons.clear();
        proxyRows.clear();
        int formX = panelX() + 10;

        buttons.add(PackUiOverlayButton.create(10, 10, 112, 26, Component.literal("Back"), b -> this.minecraft.setScreen(parent)).setVariant(PackUiOverlayButton.Variant.SECONDARY));
        buttons.add(PackUiOverlayButton.create(formX + 8, 114, 112, 20, Component.literal("Type: " + type), b -> {
            type = type == PackUtilProxyType.Socks5 ? PackUtilProxyType.Socks4 : PackUtilProxyType.Socks5;
            rebuildButtons();
        }).setVariant(PackUiOverlayButton.Variant.SECONDARY));
        buttons.add(PackUiOverlayButton.create(formX + 128, 114, 64, 20, Component.literal(selectedProxy == null ? "Add" : "Update"), b -> {
            if (selectedProxy == null) addProxy();
            else updateProxy();
        }).setVariant(PackUiOverlayButton.Variant.PRIMARY));
        buttons.add(PackUiOverlayButton.create(formX + 200, 114, 64, 20, Component.literal(selectedProxy == null ? "Clear" : "Cancel"), b -> clearProxySelection()).setVariant(selectedProxy == null ? PackUiOverlayButton.Variant.SECONDARY : PackUiOverlayButton.Variant.DANGER));
        buttons.add(PackUiOverlayButton.create(formX + 272, 114, 72, 20, Component.literal(PackUtilProxyManager.get().isRefreshing() ? "Refreshing" : "Refresh"), b -> refreshProxies()).setVariant(PackUiOverlayButton.Variant.SECONDARY));
        buttons.add(PackUiOverlayButton.create(formX + 352, 114, 72, 20, Component.literal("Cleanup"), b -> cleanupProxies()).setVariant(PackUiOverlayButton.Variant.SECONDARY));
        buttons.add(PackUiOverlayButton.create(formX + 432, 114, 64, 20, Component.literal("Import"), b -> importProxies()).setVariant(PackUiOverlayButton.Variant.SECONDARY));
        buttons.add(PackUiOverlayButton.create(formX + 504, 114, 64, 20, Component.literal("Config"), b -> this.minecraft.setScreen(new PackUtilProxyConfigScreen(this))).setVariant(PackUiOverlayButton.Variant.SECONDARY));

        addFilterButton(formX + 8, 174, 78, ProxyFilter.ALL, "All");
        addFilterButton(formX + 92, 174, 78, ProxyFilter.ALIVE, "Alive");
        addFilterButton(formX + 176, 174, 78, ProxyFilter.DEAD, "Dead");
        addFilterButton(formX + 260, 174, 94, ProxyFilter.UNCHECKED, "Unchecked");
        addFilterButton(formX + 360, 174, 86, ProxyFilter.ENABLED, "Enabled");

        List<PackUtilProxy> proxies = filteredProxies();
        int maxScroll = proxyMaxScroll(proxies.size());
        proxyListScrollOffset = quantizeScrollOffset(proxyListScrollOffset, ROW_HEIGHT, maxScroll);
        proxyListScroll.jumpTo(proxyListScrollOffset, maxScroll);
        int firstVisible = proxyListScrollOffset / ROW_HEIGHT;
        int rowY = proxyRowsTop() - (proxyListScrollOffset % ROW_HEIGHT);
        for (int i = firstVisible; i < proxies.size() && rowY + ROW_HEIGHT - 3 <= proxyRowsBottom(); i++) {
            PackUtilProxy proxy = proxies.get(i);
            if (rowY + ROW_HEIGHT - 3 <= proxyRowsTop()) {
                rowY += ROW_HEIGHT;
                continue;
            }
            PackUiOverlayButton toggle = PackUiOverlayButton.create(proxyRowX() + 8, rowY + 6, 48, 18, Component.literal(proxy.enabled ? "ON" : "OFF"), b -> toggleProxy(proxy));
            toggle.setVariant(proxy.enabled ? PackUiOverlayButton.Variant.SUCCESS : PackUiOverlayButton.Variant.SECONDARY);
            PackUiOverlayButton check = PackUiOverlayButton.create(proxyRowRight() - 130, rowY + 6, 58, 18, Component.literal(proxy.status == PackUtilProxy.Status.CHECKING ? "..." : "Check"), b -> checkProxy(proxy));
            check.setVariant(PackUiOverlayButton.Variant.PRIMARY);
            check.active = proxy.status != PackUtilProxy.Status.CHECKING;
            PackUiOverlayButton delete = PackUiOverlayButton.create(proxyRowRight() - 66, rowY + 6, 58, 18, Component.literal("Delete"), b -> deleteProxy(proxy));
            delete.setVariant(PackUiOverlayButton.Variant.DANGER);
            proxyRows.add(new ProxyRow(proxy, rowY, toggle, check, delete));
            rowY += ROW_HEIGHT;
        }
    }

    public void addFilterButton(int x, int y, int width, ProxyFilter value, String label) {
        PackUiOverlayButton button = PackUiOverlayButton.create(x, y, width, 18, Component.literal(label), b -> {
            filter = value;
            proxyListScrollOffset = 0;
            proxyListScroll.jumpTo(0, 0);
            rebuildButtons();
        });
        button.setVariant(filter == value ? PackUiOverlayButton.Variant.FILTER_ON : PackUiOverlayButton.Variant.FILTER_OFF);
        buttons.add(button);
    }

    @Override
    public void tick() {
        super.tick();
        proxyListScroll.tick(1.0F, 1);
    }

    public void addProxy() {
        PackUtilProxy proxy = proxyFromFields();
        if (proxy == null) return;
        if (PackUtilProxyManager.get().add(proxy)) {
            selectedProxy = null;
            clearProxyFields();
            showProxyToast("Added proxy " + proxy.address + ":" + proxy.port + ".", SUCCESS);
            checkProxy(proxy);
        } else {
            showProxyToast("Proxy already exists or is invalid.", ERROR);
        }
        rebuildButtons();
    }

    public void updateProxy() {
        if (selectedProxy == null) return;
        PackUtilProxy updated = proxyFromFields();
        if (updated == null) return;
        if (PackUtilProxyManager.get().update(selectedProxy, updated)) {
            showProxyToast("Updated " + selectedProxy.displayName() + ".", SUCCESS);
            clearProxySelection();
        } else {
            showProxyToast("Proxy already exists or is invalid.", ERROR);
            rebuildButtons();
        }
    }

    public void toggleProxy(PackUtilProxy proxy) {
        if (proxy == null) return;
        PackUtilProxyManager.get().setEnabled(proxy, !proxy.enabled);
        showProxyToast(proxy.enabled ? "Enabled " + proxy.displayName() + "." : "Disabled proxy.", proxy.enabled ? SUCCESS : MUTED);
        rebuildButtons();
    }

    public void deleteProxy(PackUtilProxy proxy) {
        if (proxy == null) return;
        PackUtilProxyManager.get().remove(proxy);
        if (proxy.equals(selectedProxy)) selectedProxy = null;
        showProxyToast("Deleted " + proxy.displayName() + ".", MUTED);
        rebuildButtons();
    }

    public void checkProxy(PackUtilProxy proxy) {
        if (proxy == null || proxy.status == PackUtilProxy.Status.CHECKING) return;
        rebuildButtons();
        Thread thread = new Thread(() -> {
            proxy.checkStatus(PackUtilProxyManager.get().getTimeoutMs());
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    showProxyToast(proxy.displayName() + " is " + statusText(proxy) + ".", proxy.status.color());
                    rebuildButtons();
                });
            }
        }, "PackUtil-Proxy-Check");
        thread.setDaemon(true);
        thread.start();
    }

    public void refreshProxies() {
        PackUtilProxyManager manager = PackUtilProxyManager.get();
        if (manager.all().isEmpty()) {
            showProxyToast("No proxies to refresh.", WARN);
            return;
        }
        if (manager.isRefreshing()) {
            showProxyToast("Proxy refresh is already running.", WARN);
            return;
        }
        manager.checkProxies(true);
        showProxyToast("Refreshing proxies...", DEFAULT_COLOR);
        rebuildButtons();
        Thread watcher = new Thread(() -> {
            while (manager.isRefreshing()) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    showProxyToast("Proxy refresh finished.", SUCCESS);
                    rebuildButtons();
                });
            }
        }, "PackUtil-Proxy-Refresh-Watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    public void cleanupProxies() {
        PackUtilProxyManager manager = PackUtilProxyManager.get();
        int before = manager.all().size();
        manager.clean();
        int removed = Math.max(0, before - manager.all().size());
        showProxyToast(removed == 0 ? "Cleanup finished. Nothing removed." : "Cleanup removed " + removed + " proxies.", removed == 0 ? MUTED : SUCCESS);
        rebuildButtons();
    }

    public void importProxies() {
        PointerBuffer filters = BufferUtils.createPointerBuffer(1);
        ByteBuffer txtFilter = MemoryUtil.memASCII("*.txt");
        filters.put(txtFilter);
        filters.rewind();
        String selectedFile = TinyFileDialogs.tinyfd_openFileDialog("Import Proxies", null, filters, null, false);
        if (selectedFile != null) {
            File file = new File(selectedFile);
            int added = PackUtilProxyManager.get().importFromFile(file);
            showProxyToast("Imported " + added + " proxies.", added > 0 ? SUCCESS : WARN);
            proxyListScrollOffset = 0;
            proxyListScroll.jumpTo(0, 0);
            rebuildButtons();
        }
        MemoryUtil.memFree(txtFilter);
    }

    public void clearProxyFields() {
        nameField.setValue("");
        addressField.setValue("");
        portField.setValue("");
        usernameField.setValue("");
        passwordField.setValue("");
    }

    public void clearProxySelection() {
        selectedProxy = null;
        clearProxyFields();
        clearInputFocus();
        rebuildButtons();
    }

    public PackUtilProxy proxyFromFields() {
        PackUtilProxy proxy = new PackUtilProxy();
        proxy.name = safeTrim(nameField.getValue());
        proxy.address = safeTrim(addressField.getValue());
        proxy.username = safeTrim(usernameField.getValue());
        proxy.password = passwordField.getValue();
        proxy.type = type;
        if (proxy.address.isBlank()) {
            showProxyToast("Enter a proxy address first.", WARN);
            return null;
        }
        try {
            proxy.port = Integer.parseInt(safeTrim(portField.getValue()));
        } catch (NumberFormatException e) {
            showProxyToast("Invalid proxy port.", WARN);
            return null;
        }
        if (!proxy.isValid()) {
            showProxyToast("Proxy must have a valid address and port.", WARN);
            return null;
        }
        return proxy;
    }

    public void showProxyToast(String message, int accentColor) {
        if (message == null || message.isBlank() || this.minecraft == null) return;
        this.minecraft.execute(() -> toastStack.show(message, accentColor));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = PackUtilUiScale.toVirtualInt(mouseX);
        int virtualMouseY = PackUtilUiScale.toVirtualInt(mouseY);
        PackUtilUiScale.pushOverlayScale(graphics);
        try {
        graphics.fill(0, 0, screenWidth(), screenHeight(), BG);
        int panelX = panelX();
        drawPanel(graphics, panelX + 10, TOP_PANEL_Y, PANEL_WIDTH - 20, TOP_PANEL_HEIGHT, PANEL_BG);
        drawPanel(graphics, listX(), LIST_TOP, listWidth(), listPanelHeight(), PANEL_BG_SOFT);

        PackUiText.beginManagedLayer(graphics);
        try {
            drawText(graphics, "Proxies", panelX + 22, TOP_PANEL_Y + 10, TEXT, false);
            List<PackUtilProxy> proxies = filteredProxies();
            int total = PackUtilProxyManager.get().all().size();
            int enabled = PackUtilProxyManager.get().getEnabled() == null ? 0 : 1;
            String summary = proxies.size() == total
                ? total + " proxies  enabled " + enabled
                : proxies.size() + " / " + total + " proxies  enabled " + enabled;
            drawText(graphics, summary, listX() + 12, LIST_TOP + 10, TEXT, false, listWidth() - 24);
            String refreshing = PackUtilProxyManager.get().isRefreshing() ? "REFRESHING" : "";
            if (!refreshing.isBlank()) drawText(graphics, refreshing, listX() + listWidth() - 92, LIST_TOP + 10, WARN, false, 80);
            for (ProxyRow row : proxyRows) renderProxyRow(graphics, row, virtualMouseX, virtualMouseY);
            if (PackUtilProxyManager.get().all().isEmpty()) {
                drawText(graphics, "No proxies saved yet. Add one manually or import a .txt list.", listX() + 12, proxyRowsTop() + 12, MUTED, false, listWidth() - 24);
            } else if (proxies.isEmpty()) {
                drawText(graphics, "No proxies match the current search or filter.", listX() + 12, proxyRowsTop() + 12, MUTED, false, listWidth() - 24);
            }
            for (PackUiOverlayButton button : buttons) {
                PackUiOverlayButton.renderStyled(graphics, this.font, button, virtualMouseX, virtualMouseY);
            }
            PackUiScrollbar.Metrics scrollbar = proxyScrollbarMetrics(proxies.size());
            PackUiScrollbar.draw(graphics, scrollbar, scrollbar.contains(virtualMouseX, virtualMouseY), proxyScrollbarDragging);
            toastStack.render(graphics, this.font, THEME, listX(), 8, listWidth());
        } finally {
            PackUiText.endManagedLayer(graphics);
        }

        super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            PackUtilUiScale.popOverlayScale(graphics);
        }
    }

    @Override
    public void removed() {
        PackUiText.discardPendingOverlayText();
        super.removed();
    }

    public void renderProxyRow(GuiGraphicsExtractor graphics, ProxyRow row, int mouseX, int mouseY) {
        PackUtilProxy proxy = row.proxy;
        boolean selected = proxy.equals(selectedProxy);
        int x = proxyRowX();
        int y = row.y;
        int w = proxyRowWidth();
        int fill = proxy.enabled ? 0x3324D86A : selected ? 0x242B1A1D : 0x18111113;
        int border = proxy.enabled ? BORDER_ACTIVE : selected ? 0xFF5A3038 : BORDER;
        graphics.fill(x, y, x + w, y + ROW_HEIGHT - 3, fill);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + ROW_HEIGHT - 4, x + w, y + ROW_HEIGHT - 3, border);
        graphics.fill(x, y, x + 1, y + ROW_HEIGHT - 3, border);
        graphics.fill(x + w - 1, y, x + w, y + ROW_HEIGHT - 3, border);

        String name = proxy.displayName().isBlank() ? "(unnamed)" : proxy.displayName();
        String address = proxy.type + "  " + proxy.address + ":" + proxy.port;
        String auth = proxy.username == null || proxy.username.isBlank() ? "NoA" : "Auth";
        String status = statusText(proxy);
        int nameX = x + 64;
        int metaRight = row.checkButton.getX() - 8;
        int metaW = 72;
        int metaX = Math.max(nameX + 36, metaRight - metaW);
        int textRight = Math.max(nameX + 24, metaX - 8);
        int textW = Math.max(20, textRight - nameX);
        int metaTextW = Math.max(20, metaRight - metaX);
        drawText(graphics, name, nameX, y + 4, proxy.enabled ? SUCCESS : TEXT, false, textW);
        drawText(graphics, address, nameX, y + 17, MUTED, false, textW);
        drawText(graphics, auth, metaX, y + 4, proxy.username == null || proxy.username.isBlank() ? MUTED : DEFAULT_COLOR, false, metaTextW);
        drawText(graphics, status, metaX, y + 17, proxy.status.color(), false, metaTextW);
        PackUiOverlayButton.renderStyled(graphics, this.font, row.toggleButton, mouseX, mouseY);
        PackUiOverlayButton.renderStyled(graphics, this.font, row.checkButton, mouseX, mouseY);
        PackUiOverlayButton.renderStyled(graphics, this.font, row.deleteButton, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (virtualEvent.button() != 0) return super.mouseClicked(virtualEvent, doubleClick);
        PackUiScrollbar.Metrics scrollbar = proxyScrollbarMetrics(filteredProxies().size());
        if (scrollbar.hasScroll() && scrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
            proxyScrollbarDragging = true;
            proxyScrollbarGrabOffset = scrollbar.overThumb(virtualEvent.x(), virtualEvent.y()) ? Math.max(0, (int) Math.round(virtualEvent.y()) - scrollbar.thumbY()) : scrollbar.thumbHeight() / 2;
            proxyListScrollOffset = quantizeScrollOffset(PackUiScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), proxyScrollbarGrabOffset), ROW_HEIGHT, scrollbar.maxScroll());
            proxyListScroll.jumpTo(proxyListScrollOffset, scrollbar.maxScroll());
            rebuildButtons();
            clearInputFocus();
            return true;
        }
        for (PackUiOverlayButton button : buttons) {
            if (PackUiOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
        }
        for (ProxyRow row : proxyRows) {
            if (overButton(row.toggleButton, virtualEvent.x(), virtualEvent.y())
                || overButton(row.checkButton, virtualEvent.x(), virtualEvent.y())
                || overButton(row.deleteButton, virtualEvent.x(), virtualEvent.y())) {
                PackUiOverlayButton.fireIfHit(row.toggleButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
                PackUiOverlayButton.fireIfHit(row.checkButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
                PackUiOverlayButton.fireIfHit(row.deleteButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button());
                return true;
            }
            if (virtualEvent.x() >= proxyRowX() && virtualEvent.x() < proxyRowRight() && virtualEvent.y() >= row.y && virtualEvent.y() < row.y + ROW_HEIGHT - 3) {
                selectProxy(row.proxy);
                return true;
            }
        }
        clearInputFocus();
        return super.mouseClicked(virtualEvent, doubleClick);
    }

    public boolean overButton(PackUiOverlayButton button, double mouseX, double mouseY) {
        return button != null
            && button.visible
            && mouseX >= button.getX()
            && mouseX < button.getX() + button.getWidth()
            && mouseY >= button.getY()
            && mouseY < button.getY() + button.getHeight();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (proxyScrollbarDragging) {
            proxyScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(virtualEvent(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (proxyScrollbarDragging) {
            PackUiScrollbar.Metrics scrollbar = proxyScrollbarMetrics(filteredProxies().size());
            proxyListScrollOffset = quantizeScrollOffset(PackUiScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), proxyScrollbarGrabOffset), ROW_HEIGHT, scrollbar.maxScroll());
            proxyListScroll.jumpTo(proxyListScrollOffset, scrollbar.maxScroll());
            rebuildButtons();
            return true;
        }
        return super.mouseDragged(virtualEvent, PackUtilUiScale.toVirtual(dx), PackUtilUiScale.toVirtual(dy));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        x = PackUtilUiScale.toVirtual(x);
        y = PackUtilUiScale.toVirtual(y);
        if (x < listX() || x >= listX() + listWidth() || y < LIST_TOP || y >= LIST_TOP + listPanelHeight()) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        int maxScroll = proxyMaxScroll(filteredProxies().size());
        if (maxScroll <= 0) return true;
        int next = proxyListScrollOffset - (int) Math.signum(scrollY) * ROW_HEIGHT;
        proxyListScrollOffset = quantizeScrollOffset(next, ROW_HEIGHT, maxScroll);
        proxyListScroll.setTarget(proxyListScrollOffset, maxScroll);
        rebuildButtons();
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    public void selectProxy(PackUtilProxy proxy) {
        selectedProxy = proxy;
        if (proxy != null) {
            nameField.setValue(safeTrim(proxy.name));
            addressField.setValue(safeTrim(proxy.address));
            portField.setValue(proxy.port <= 0 ? "" : Integer.toString(proxy.port));
            usernameField.setValue(safeTrim(proxy.username));
            passwordField.setValue(proxy.password == null ? "" : proxy.password);
            type = proxy.type == null ? PackUtilProxyType.Socks5 : proxy.type;
        }
        rebuildButtons();
    }

    public List<PackUtilProxy> filteredProxies() {
        List<PackUtilProxy> proxies = PackUtilProxyManager.get().all();
        String query = normalize(searchQuery);
        if (query.isEmpty() && filter == ProxyFilter.ALL) return proxies;
        List<PackUtilProxy> filtered = new ArrayList<>();
        for (PackUtilProxy proxy : proxies) {
            if (proxy == null || !matchesFilter(proxy)) continue;
            if (!query.isEmpty() && !matchesSearch(proxy, query)) continue;
            filtered.add(proxy);
        }
        return filtered;
    }

    public boolean matchesFilter(PackUtilProxy proxy) {
        return switch (filter) {
            case ALL -> true;
            case ALIVE -> proxy.status == PackUtilProxy.Status.ALIVE;
            case DEAD -> proxy.status == PackUtilProxy.Status.DEAD;
            case UNCHECKED -> proxy.status == PackUtilProxy.Status.UNCHECKED || proxy.status == PackUtilProxy.Status.CHECKING;
            case ENABLED -> proxy.enabled;
        };
    }

    public boolean matchesSearch(PackUtilProxy proxy, String query) {
        String haystack = normalize(proxy.displayName() + " " + proxy.address + " " + proxy.port + " " + proxy.type + " " + proxy.status + " " + safeTrim(proxy.username));
        return haystack.contains(query);
    }

    public String statusText(PackUtilProxy proxy) {
        if (proxy == null) return "";
        return switch (proxy.status) {
            case ALIVE -> proxy.latency + "ms";
            case DEAD -> "Dead";
            case CHECKING -> "Checking";
            case UNCHECKED -> "Unchecked";
        };
    }

    public int panelX() {
        return Math.max(PANEL_MARGIN, screenWidth() / 2 - PANEL_WIDTH / 2);
    }

    public int listX() {
        return panelX() + 10;
    }

    public int listWidth() {
        return PANEL_WIDTH - 20;
    }

    public int listPanelHeight() {
        return Math.max(70, screenHeight() - LIST_TOP - LIST_BOTTOM_MARGIN);
    }

    public int proxyRowsTop() {
        return LIST_TOP + LIST_HEADER_HEIGHT;
    }

    public int proxyRowsBottom() {
        return LIST_TOP + listPanelHeight() - 6;
    }

    public int proxyViewportHeight() {
        return Math.max(ROW_HEIGHT, alignViewportHeight(Math.max(1, proxyRowsBottom() - proxyRowsTop()), ROW_HEIGHT));
    }

    public int proxyMaxScroll(int rows) {
        return Math.max(0, rows * ROW_HEIGHT - proxyViewportHeight());
    }

    public PackUiScrollbar.Metrics proxyScrollbarMetrics(int rows) {
        int contentPixels = Math.max(0, rows) * ROW_HEIGHT;
        int trackX = listX() + listWidth() - 8;
        int trackY = proxyRowsTop();
        int trackHeight = proxyViewportHeight();
        return PackUiScrollbar.compute(contentPixels, proxyViewportHeight(), trackX, trackY, LIST_SCROLLBAR_WIDTH, trackHeight, proxyListScroll.tick(0.0f, proxyMaxScroll(rows)));
    }

    public int proxyRowX() {
        return listX() + 8;
    }

    public int proxyRowRight() {
        return listX() + listWidth() - 8 - LIST_SCROLLBAR_GUTTER;
    }

    public int proxyRowWidth() {
        return Math.max(80, proxyRowRight() - proxyRowX());
    }

    public void clearInputFocus() {
        if (nameField != null) nameField.setFocused(false);
        if (addressField != null) addressField.setFocused(false);
        if (portField != null) portField.setFocused(false);
        if (usernameField != null) usernameField.setFocused(false);
        if (passwordField != null) passwordField.setFocused(false);
        if (searchField != null) searchField.setFocused(false);
        this.setFocused(null);
    }

    public void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, BORDER);
        graphics.fill(x, y, x + 1, y + h, BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    public void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center) {
        drawText(graphics, text, x, y, color, center, Integer.MAX_VALUE);
    }

    public void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center, int maxWidth) {
        Font renderer = this.font;
        Identifier font = THEME.fontFor(PackUiTone.BODY);
        String value = text == null ? "" : text;
        if (maxWidth != Integer.MAX_VALUE) value = PackUiText.trimToWidth(renderer, value, maxWidth, font, color);
        int w = PackUiText.width(renderer, value, font, color);
        int drawX = center ? x - w / 2 : x;
        PackUiText.draw(graphics, renderer, value, font, color, drawX, y, false);
    }

    public static int alignViewportHeight(int height, int step) {
        if (step <= 0) return Math.max(1, height);
        return Math.max(step, (Math.max(1, height) / step) * step);
    }

    public static int quantizeScrollOffset(int value, int step, int maxScroll) {
        int clamped = Math.max(0, Math.min(maxScroll, value));
        if (step <= 0) return clamped;
        int rounded = Math.round(clamped / (float) step) * step;
        return Math.max(0, Math.min(maxScroll, rounded));
    }

    public static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public static String normalize(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    public int screenWidth() {
        int width = PackUtilUiScale.getVirtualScreenWidth();
        return width <= 0 ? this.width : width;
    }

    public int screenHeight() {
        int height = PackUtilUiScale.getVirtualScreenHeight();
        return height <= 0 ? this.height : height;
    }

    public static MouseButtonEvent virtualEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(PackUtilUiScale.toVirtual(event.x()), PackUtilUiScale.toVirtual(event.y()), new MouseButtonInfo(event.button(), 0));
    }

    public enum ProxyFilter {
        ALL,
        ALIVE,
        DEAD,
        UNCHECKED,
        ENABLED
    }

    public record ProxyRow(PackUtilProxy proxy, int y, PackUiOverlayButton toggleButton, PackUiOverlayButton checkButton, PackUiOverlayButton deleteButton) {
    }
}
