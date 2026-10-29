package com.bobincraft.mixin;
import com.bobincraft.combat.PlayerInjuries;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class InjuredPlayerRendererMixin {
    @Inject(method="setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",at=@At("HEAD"),cancellable=true)
    private void bobin$crawl(AbstractClientPlayerEntity p,MatrixStack m,float age,float yaw,float delta,CallbackInfo ci){
        if(!PlayerInjuries.prone(p)||p.hasVehicle()||p.isSleeping()||p.isFallFlying())return;
        // A forced SWIMMING collision pose does not set vanilla's swimming animation blend.
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-yaw));m.translate(0,.30,.85);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));ci.cancel();
    }
}
