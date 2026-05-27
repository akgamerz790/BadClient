package dev.xaihi.autismclient.common.util;

import net.minecraft.client.Minecraft;

public final class PackUtilGuiClipboardUtil {
    public static final Minecraft MC = Minecraft.getInstance();

    public PackUtilGuiClipboardUtil() {
    }

    public static void copyGuiTitleJson() {
        if (MC.screen == null || MC.keyboardHandler == null) {
            PackUtilClientMessaging.sendPrefixed("Copy failed: No screen/keyboard.");
            return;
        }

        String title = MC.screen.getTitle() == null ? "" : MC.screen.getTitle().getString();
        MC.keyboardHandler.setClipboard(title);
        PackUtilClientMessaging.sendPrefixed("GUI title copied.");
    }
}
