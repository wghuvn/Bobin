package com.bobincraft.mixin;

import com.bobincraft.vehicle.client.GrenadeClient;
import com.bobincraft.weapon.grom7.client.Grom7Client;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, CallbackInfo ci) {
        if (com.bobincraft.drone.client.DroneClient.isActive()
                || com.bobincraft.vehicle.client.ArmorClient.station() != null
) {
            ci.cancel();
            return;
        }
        Grom7Client.renderHud(context);
        GrenadeClient.renderHud(context);
    }
}
