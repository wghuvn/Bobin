package com.bobincraft.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {

    protected PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void bobin$resetCrawl(T entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        leftArm.pivotY = rightArm.pivotY = 2;
        leftArm.pivotZ = rightArm.pivotZ = 0;
        body.roll = 0;
        rightLeg.pivotX = -1.9f;
        leftLeg.pivotX = 1.9f;
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void bobin$limbs(T entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player)) return;
        var model = (PlayerEntityModel<?>) (Object) this;
        int mask = com.bobincraft.combat.PlayerInjuries.missing(entity);
        if (com.bobincraft.combat.GuardInjuryPose.prone(mask) && !entity.hasVehicle() && !entity.isSleeping()) {
            com.bobincraft.combat.client.InjuryCrawlPose.apply(model, mask, 1, c * .30f, entity.isAlive() ? b : 0);
            model.leftSleeve.copyTransform(model.leftArm);
            model.rightSleeve.copyTransform(model.rightArm);
            model.leftPants.copyTransform(model.leftLeg);
            model.rightPants.copyTransform(model.rightLeg);
            model.jacket.copyTransform(model.body);
        }
        com.bobincraft.vehicle.client.GrenadeClient.pose(player, model, c - entity.age);
        com.bobincraft.weapon.grom7.client.Grom7Client.poseThirdPerson(player, model, c - entity.age);
        com.bobincraft.vehicle.client.ArmorClient.poseClimbOut(player, model, c - entity.age);
        model.jacket.copyTransform(model.body);
        com.bobincraft.combat.client.InjuryCrawlPose.lockMissing(model, mask);
        model.leftSleeve.copyTransform(model.leftArm);
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftPants.copyTransform(model.leftLeg);
        model.rightPants.copyTransform(model.rightLeg);
        model.leftArm.visible &= (mask & 1) == 0;
        model.leftSleeve.visible &= (mask & 1) == 0;
        model.rightArm.visible &= (mask & 2) == 0;
        model.rightSleeve.visible &= (mask & 2) == 0;
        model.leftLeg.visible &= (mask & 4) == 0;
        model.leftPants.visible &= (mask & 4) == 0;
        model.rightLeg.visible &= (mask & 8) == 0;
        model.rightPants.visible &= (mask & 8) == 0;
    }
}
