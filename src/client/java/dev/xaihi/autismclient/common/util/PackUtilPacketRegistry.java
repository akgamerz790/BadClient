package dev.xaihi.autismclient.common.util;

import net.minecraft.network.protocol.Packet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PackUtilPacketRegistry {
    private static final Set<Class<? extends Packet<?>>> C2S_PACKETS = new HashSet<>();
    private static final Set<Class<? extends Packet<?>>> S2C_PACKETS = new HashSet<>();
    private static final Map<String, Class<? extends Packet<?>>> PACKET_NAME_MAP = new HashMap<>();
    private static final Map<Class<? extends Packet<?>>, String> PACKET_CLASS_NAME_MAP = new HashMap<>();
    // Add new registry
    public static final Set<Class<? extends Packet<?>>> PACKETS = new HashSet<>();

    public static Set<Class<? extends Packet<?>>> getC2SPackets() {
        return C2S_PACKETS;
    }

    public static Set<Class<? extends Packet<?>>> getS2CPackets() {
        return S2C_PACKETS;
    }

    public static String getName(Class<? extends Packet<?>> packetClass) {
        if (PACKET_CLASS_NAME_MAP.containsKey(packetClass)) {
            return PACKET_CLASS_NAME_MAP.get(packetClass);
        }
        // Fallback: return simple class name without "S2C" or "C2S" prefix
        String simpleName = packetClass.getSimpleName();
        String cleaned = simpleName.replaceAll("^(S2C|C2S)", "");
        return cleaned;
    }

    public static Class<? extends Packet<?>> getPacket(String packetName) {
        if (packetName == null || packetName.isBlank()) return null;
        return PACKET_NAME_MAP.get(packetName);
    }

    public static void registerPacket(Class<? extends Packet<?>> packetClass, String name, boolean isC2S) {
        PACKET_NAME_MAP.put(name, packetClass);
        PACKET_CLASS_NAME_MAP.put(packetClass, name);
        if (isC2S) {
            C2S_PACKETS.add(packetClass);
        } else {
            S2C_PACKETS.add(packetClass);
        }
    }

    static {
        // Register common Minecraft packets - add as needed
        // Example:
        // registerPacket(ServerboundContainerClickPacket.class, "ContainerClick", true);
        // registerPacket(ClientboundContainerSetContentPacket.class, "ContainerSetContent", false);
    }
}