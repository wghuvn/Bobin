package com.bobincraft.mixin;

import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /** Native overlay also colours corpses red through deathTime, independently of hurtTime. */
    @Inject(method = "getOverlay", at = @At("HEAD"), cancellable = true)
    private static void bobin_craft$guardDamageOverlay(LivingEntity entity, float whiteOverlay,
                                                       CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof WarzoneGuardEntity) cir.setReturnValue(OverlayTexture.DEFAULT_UV);
    }
}
