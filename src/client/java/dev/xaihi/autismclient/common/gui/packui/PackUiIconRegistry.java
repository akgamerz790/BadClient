package dev.xaihi.autismclient.common.gui.packui;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PackUiIconRegistry {
    public final Map<String, Identifier> icons = new LinkedHashMap<>();

    public PackUiIconRegistry register(String key, Identifier textureId) {
        if (key == null || key.isBlank() || textureId == null) return this;
        icons.put(key, textureId);
        return this;
    }

    public Identifier get(String key) {
        return icons.get(key);
    }
}
