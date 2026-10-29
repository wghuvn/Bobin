package com.bobincraft.mixin;

import com.bobincraft.vehicle.ArmorContent;
import com.bobincraft.weapon.grom7.Grom7Content;
import com.bobincraft.weapon.grom7.client.Grom7Client;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void bobin_craft$suppressVehicleAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player != null) {
            if (client.player.getMainHandStack().isOf(Grom7Content.GROM7_REMOTE)
                    || client.player.getOffHandStack().isOf(Grom7Content.GROM7_REMOTE)) {
                Grom7Client.onRemoteAttackClick();
                cir.setReturnValue(false);
                return;
            }
            if (client.player.getMainHandStack().isOf(ArmorContent.GRENADE)
                    || client.player.getOffHandStack().isOf(ArmorContent.GRENADE)) {
                cir.setReturnValue(false);
                return;
            }
            if (client.player.getVehicle() instanceof com.bobincraft.vehicle.ArmoredVehicleEntity) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void bobin_craft$suppressVehicleMining(boolean breaking, CallbackInfo ci) {
        var client = (MinecraftClient) (Object) this;
        if (client.player != null && (client.player.getVehicle() instanceof com.bobincraft.vehicle.ArmoredVehicleEntity
                || client.player.getMainHandStack().isOf(Grom7Content.GROM7_REMOTE)
                || client.player.getMainHandStack().isOf(ArmorContent.GRENADE))) {
            ci.cancel();
        }
    }
}
