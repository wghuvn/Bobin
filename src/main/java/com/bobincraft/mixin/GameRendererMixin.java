package com.bobincraft.mixin;

import com.bobincraft.drone.DroneType;
import com.bobincraft.drone.client.DroneCameraGeometry;
import com.bobincraft.drone.client.DroneClient;
import com.bobincraft.drone.client.DroneVideoFeed;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @ModifyConstant(method = "getBasicProjectionMatrix", constant = @Constant(floatValue = .05f))
    private float bobin_craft$fpvNearPlane(float vanilla) {
        if (DroneClient.isFpvCamera()) {
            return DroneCameraGeometry.nearPlane(DroneType.FPV, vanilla);
        }
        return vanilla;
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void bobin_craft$droneLens(Camera camera, float tickDelta, boolean changingFov,
                                     CallbackInfoReturnable<Double> cir) {
        if (com.bobincraft.vehicle.client.ArmorClient.optics()) {
            cir.setReturnValue(38d);
            return;
        }
        if (DroneClient.isActive() && DroneClient.getDrone() != null)
            cir.setReturnValue((double) DroneClient.cameraFieldOfView());
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void bobin_craft$videoFeed(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        DroneVideoFeed.render();
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        if (DroneClient.isActive() || com.bobincraft.vehicle.client.ArmorClient.inside()) {
            ci.cancel();
        }
    }

    // Minecraft builds its view matrix from yaw/pitch, so bank must precede those rotations.
    @Inject(method = "renderWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER))
    private void bobin_craft$droneBank(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        if (DroneClient.isActive()) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(DroneClient.cameraRoll(tickDelta)));
        }
    }
}
