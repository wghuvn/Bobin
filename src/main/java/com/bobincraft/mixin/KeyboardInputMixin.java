package com.bobincraft.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (com.bobincraft.drone.client.DroneClient.isActive()) {
            this.movementForward = 0;
            this.movementSideways = 0;
            this.pressingForward = this.pressingBack = this.pressingLeft = this.pressingRight = false;
            this.jumping = this.sneaking = false;
        }
    }
}
