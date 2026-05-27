package dev.xaihi.autismclient.client;

import dev.xaihi.autismclient.client.addons.AutismClientAddon;
import dev.xaihi.autismclient.common.gui.packui.PackUiText;
import dev.xaihi.autismclient.common.gui.packui.PackUiTextPipelines;
import dev.xaihi.autismclient.common.modules.PackUtilModule;
import dev.xaihi.autismclient.common.util.PackUtilInstaBreakRenderer;
import dev.xaihi.autismclient.common.util.PackUtilWindowBranding;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

public final class AutismClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AutismClientAddon.FOLDER.mkdirs();

        PackUiText.eagerInitFonts();
        PackUtilModule.get().initialize();
        PackUtilInstaBreakRenderer.initialize();

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
            @Override
            public net.minecraft.resources.Identifier getFabricId() {
                return net.minecraft.resources.Identifier.fromNamespaceAndPath("autismclient", "packui_shaders");
            }

            @Override
            public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                PackUiTextPipelines.precompile(manager);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PackUtilWindowBranding.apply(client);
            PackUtilModule.get().tick();
        });
        ClientConfigurationConnectionEvents.INIT.register((listener, client) -> PackUtilModule.get().onConfigurationConnectionStarted());
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> {
            PackUtilInstaBreakRenderer.clear();
            PackUtilModule.get().onGameLeft();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> PackUtilModule.get().onGameJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PackUtilInstaBreakRenderer.clear();
            PackUtilModule.get().onGameLeft();
        });
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) ->
            PackUtilModule.get().appendTooltip(stack, lines)
        );
    }
}
