package com.bobincraft.mixin;

import com.bobincraft.raid.HubNpcInteraction;
import com.bobincraft.raid.client.HubMerchantScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Choose the camp interface before a vanilla screen can close its shared handler. */
@Mixin(HandledScreens.class)
public abstract class HubMerchantScreenFactoryMixin {
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private static void bobin$openService(ScreenHandlerType<?> type, MinecraftClient client, int syncId,
                                          Text title, CallbackInfo ci) {
        if (type != ScreenHandlerType.MERCHANT || client.player == null) return;
        HubNpcInteraction.Role role = HubNpcInteraction.roleFromTitle(title);
        if (role == null || !role.service()) return;
        MerchantScreenHandler handler = new MerchantScreenHandler(syncId, client.player.getInventory());
        client.player.currentScreenHandler = handler;
        client.setScreen(new HubMerchantScreen(handler, client.player.getInventory(), title, role));
        ci.cancel();
    }
}
