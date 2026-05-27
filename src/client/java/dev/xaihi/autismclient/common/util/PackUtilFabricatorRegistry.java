package dev.xaihi.autismclient.common.util;

import org.jetbrains.annotations.Nullable;

public class PackUtilFabricatorRegistry {
    public static volatile PackUtilFabricatorOverlay activeOverlay = null;

    public static void setActiveOverlay(@Nullable PackUtilFabricatorOverlay overlay) {
        activeOverlay = overlay;
    }

    @Nullable
    public static PackUtilFabricatorOverlay getActiveOverlay() {
        return activeOverlay;
    }
}
