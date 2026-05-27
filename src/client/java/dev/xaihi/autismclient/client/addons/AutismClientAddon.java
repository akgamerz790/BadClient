package dev.xaihi.autismclient.client.addons;

import com.mojang.logging.LogUtils;
import java.io.File;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class AutismClientAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String MOD_ID = "packutil";
    public static final File FOLDER = FabricLoader.getInstance().getConfigDir().resolve("packutil").toFile();

    static {
        FOLDER.mkdirs();
    }
}