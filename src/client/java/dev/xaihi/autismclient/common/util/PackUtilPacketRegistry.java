package dev.xaihi.autismclient.common.util;

import net.minecraft.network.protocol.Packet;
import java.util.HashSet;
import java.util.Set;

public class PackUtilPacketRegistry {
    private static final Set<Class<? extends Packet<?>>> C2S_PACKETS = new HashSet<>();
    private static final Set<Class<? extends Packet<?>>> S2C_PACKETS = new HashSet<>();

    public static Set<Class<? extends Packet<?>>> getC2SPackets() {
        return C2S_PACKETS;
    }

    public static Set<Class<? extends Packet<?>>> getS2CPackets() {
        return S2C_PACKETS;
    }
}