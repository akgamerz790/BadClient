package dev.xaihi.autismclient.mixin;

import dev.xaihi.autismclient.common.gui.packui.PackUiScreenButton;
import dev.xaihi.autismclient.mixin.accessor.PackUtilScreenAccessor;
import dev.xaihi.autismclient.common.modules.PackUtilModule;
import dev.xaihi.autismclient.common.util.PackUtilLecternButtons;
import dev.xaihi.autismclient.common.util.PackUtilOverlayManager;
import dev.xaihi.autismclient.common.util.PackUtilPacketLoggerOverlay;
import dev.xaihi.autismclient.common.util.PackUtilQueueEditorOverlay;
import dev.xaihi.autismclient.common.util.PackUtilUiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class PackUtilScreenMixin {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Unique private boolean packutil$lecternInitialized;
    @Unique private PackUtilQueueEditorOverlay packutil$queueEditorOverlay;
    @Unique private PackUtilPacketLoggerOverlay packutil$packetLoggerOverlay;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void packutil$onInit(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!(screen instanceof LecternScreen) || !packutil$isModuleActive()) return;

        if (packutil$lecternInitialized) {
            if (packutil$queueEditorOverlay != null) packutil$queueEditorOverlay.restoreState();
            if (packutil$queueEditorOverlay != null) PackUtilOverlayManager.get().register(packutil$queueEditorOverlay);
            if (packutil$packetLoggerOverlay != null) PackUtilOverlayManager.get().register(packutil$packetLoggerOverlay);
            return;
        }

        Font textRenderer = ((PackUtilScreenAccessor) this).getFont();
        packutil$queueEditorOverlay = new PackUtilQueueEditorOverlay(textRenderer);
        packutil$queueEditorOverlay.restoreState();
        PackUtilOverlayManager.get().register(packutil$queueEditorOverlay);

        PackUtilModule module = PackUtilModule.get();
        if (module != null) {
            packutil$packetLoggerOverlay = module.getPacketLoggerOverlay();
            if (packutil$packetLoggerOverlay != null) {
                PackUtilOverlayManager.get().register(packutil$packetLoggerOverlay);
            }
        }

        packutil$lecternInitialized = true;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void packutil$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!packutil$isModuleActive()) return;

        Screen screen = (Screen) (Object) this;
        Font textRenderer = ((PackUtilScreenAccessor) this).getFont();
        if (textRenderer == null) return;

        if (!(screen instanceof LecternScreen) || MC.player == null) return;

        AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler != null) {
            int virtualMouseX = PackUtilUiScale.toVirtualInt(mouseX);
            int virtualMouseY = PackUtilUiScale.toVirtualInt(mouseY);
            PackUtilUiScale.pushOverlayScale(context);
            try {
                for (PackUiScreenButton button : PackUtilLecternButtons.build(MC, packutil$queueEditorOverlay)) {
                    button.render(context, textRenderer, virtualMouseX, virtualMouseY);
                }
            } finally {
                PackUtilUiScale.popOverlayScale(context);
            }
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void packutil$onClose(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof LecternScreen) {
            PackUtilOverlayManager.get().unregister(packutil$queueEditorOverlay);
            PackUtilOverlayManager.get().unregister(packutil$packetLoggerOverlay);
        }
    }

    @Unique
    private boolean packutil$isModuleActive() {
        PackUtilModule module = PackUtilModule.get();
        return module != null && module.isActive();
    }

}
