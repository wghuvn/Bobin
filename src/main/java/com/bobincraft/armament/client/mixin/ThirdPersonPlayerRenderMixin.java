package com.bobincraft.armament.client.mixin;

import com.bobincraft.armament.client.thirdperson.fabric.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply AFTER vanilla and injury/grenade model hooks, restore after all features.
 * The guard yields to all existing systems and only accepts the local diagnostic player.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ThirdPersonPlayerRenderMixin<T extends LivingEntity> {
    @Shadow protected EntityModel<T> model;
    @Unique private ThirdPersonModelAdapter bobin$thirdPersonPose;
    @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"))
    private void bobin$clearBorrowedPose(T entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){
        bobin$restore();
    }
    @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V",shift=At.Shift.AFTER))
    private void bobin$poseDiagnostic(T entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){
        if(entity instanceof AbstractClientPlayerEntity player && model instanceof PlayerEntityModel<?> playerModel)
            bobin$thirdPersonPose=ThirdPersonDiagnostic.pose(player,playerModel,delta,consumers,matrices);
    }
    @Inject(method="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("RETURN"))
    private void bobin$restoreDiagnostic(T entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){bobin$restore();}
    @Unique private void bobin$restore(){if(bobin$thirdPersonPose!=null){bobin$thirdPersonPose.close();bobin$thirdPersonPose=null;}}
}
