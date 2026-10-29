package com.bobincraft.mixin;

import com.bobincraft.camera.BobinCameraCollision;
import com.bobincraft.drone.client.DroneClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    private boolean thirdPerson;

    // The remote FPV camera is attached to the drone, but the local player's
    // avatar must remain eligible for third-person world rendering.
    @Shadow
    private Entity focusedEntity;

    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void onIsThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (DroneClient.isActive()) {
            cir.setReturnValue(true);
            return;
        }
        if (com.bobincraft.vehicle.client.ArmorClient.isClimbingOut(MinecraftClient.getInstance().getCameraEntity())) {
            cir.setReturnValue(true);
            return;
        }
        if (com.bobincraft.vehicle.client.ArmorClient.thirdPersonTank()) {
            cir.setReturnValue(true);
            return;
        }
        if (com.bobincraft.vehicle.client.ArmorClient.inside()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void onCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (DroneClient.isActive()) {
            Vec3d dronePos = DroneClient.cameraPosition(tickDelta);
            if (dronePos != null) {
                this.thirdPerson = true;
                this.setPos(dronePos);
                // Keep the physical camera at the drone, while keeping the
                // operator as Camera.focusedEntity for WorldRenderer's
                // local-player visibility test. Without this, Minecraft
                // may suppress the operator's body in the drone feed.
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) this.focusedEntity = client.player;
                this.setRotation(DroneClient.cameraYaw(tickDelta), DroneClient.cameraPitch(tickDelta));
                Camera camera = (Camera) (Object) this;
                camera.getRotation().rotateZ((float) Math.toRadians(-DroneClient.cameraRoll(tickDelta)));
                camera.getHorizontalPlane().set(0, 0, 1).rotate(camera.getRotation());
                camera.getVerticalPlane().set(0, 1, 0).rotate(camera.getRotation());
                camera.getDiagonalPlane().set(1, 0, 0).rotate(camera.getRotation());
            }
            return;
        }
        if (focusedEntity instanceof PlayerEntity climbPlayer && com.bobincraft.vehicle.client.ArmorClient.isClimbingOut(climbPlayer)) {
            this.thirdPerson = true;
            this.setRotation(
                com.bobincraft.vehicle.client.ArmorClient.climbOutCameraYaw(climbPlayer, tickDelta),
                com.bobincraft.vehicle.client.ArmorClient.climbOutCameraPitch(climbPlayer, tickDelta)
            );
            this.setPos(com.bobincraft.vehicle.client.ArmorClient.climbOutCameraPos(area, climbPlayer, tickDelta));
            return;
        }
        if (com.bobincraft.vehicle.client.ArmorClient.thirdPersonTank() && focusedEntity instanceof PlayerEntity player) {
            var v = com.bobincraft.vehicle.client.ArmorClient.station();
            if (v != null) {
                com.bobincraft.vehicle.client.ArmorClient.pollHotbarScrollZoom(player);
                this.thirdPerson = true;
                float yaw = com.bobincraft.vehicle.client.ArmorClient.cameraYaw(tickDelta);
                float pitch = MathHelper.clamp(com.bobincraft.vehicle.client.ArmorClient.cameraPitch(tickDelta), -32f, 62f);
                float dist = com.bobincraft.vehicle.client.ArmorClient.thirdPersonDistance();
                this.setRotation(yaw, pitch);
                this.setPos(com.bobincraft.vehicle.client.ArmorClient.thirdPersonCameraPos(area, v, yaw, pitch, dist, tickDelta));
                return;
            }
        }
        if (com.bobincraft.vehicle.client.ArmorClient.inside()) {
            this.thirdPerson = false;
            setPos(com.bobincraft.vehicle.client.ArmorClient.cameraPosition(tickDelta));
            setRotation(com.bobincraft.vehicle.client.ArmorClient.cameraYaw(tickDelta), com.bobincraft.vehicle.client.ArmorClient.cameraPitch(tickDelta));
            return;
        }
        if (focusedEntity instanceof PlayerEntity p && com.bobincraft.combat.client.ConcussionClient.amount() > 0) {
            var camera = (Camera) (Object) this;
            setRotation(camera.getYaw() + com.bobincraft.combat.client.ConcussionClient.yaw(p.age + tickDelta),
                    camera.getPitch() + com.bobincraft.combat.client.ConcussionClient.pitch(p.age + tickDelta));
        }
        if (thirdPerson && focusedEntity != null) {
            Camera camera = (Camera) (Object) this;
            Vec3d eye = focusedEntity.getLerpedPos(tickDelta).add(0, focusedEntity.getStandingEyeHeight(), 0);
            this.setPos(BobinCameraCollision.clipToArmor(area, eye, camera.getPos()));
        }
    }
}
