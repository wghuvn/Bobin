package com.bobincraft.mixin;

import com.bobincraft.drone.DroneEntity;
import com.bobincraft.drone.DroneNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.EntityTrackingListener;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Keeps the operator subscribed to their actively controlled drone AND nearby entities (NPCs, tanks, projectiles,
 * demolition charges, payloads) within the drone's camera view radius even when the drone is 300-1000+ blocks
 * away from the operator's stationary body (Section 5.5).
 *
 * <p>Uses hysteresis (start tracking within 96m, stop tracking outside 112m) and cleanly unsubscribes remote-only
 * entities when the drone moves away, preventing packet leaks or repeated tracker rebuilds.
 */
@Mixin(targets = "net.minecraft.server.world.ThreadedAnvilChunkStorage$EntityTracker")
public abstract class DroneEntityTrackerMixin {
    private static final double REMOTE_START_RADIUS_SQ = 96.0 * 96.0;
    private static final double REMOTE_STOP_RADIUS_SQ = 112.0 * 112.0;

    @Shadow @Final private Entity entity;
    @Shadow @Final private EntityTrackerEntry entry;
    @Shadow @Final private Set<EntityTrackingListener> listeners;

    @Inject(method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private void bobin$keepDroneAndRemoteViewEntitiesTracked(ServerPlayerEntity player, CallbackInfo ci) {
        if (entity instanceof DroneEntity drone && DroneNetworking.controls(player, drone)
                && DroneNetworking.validOperator(player, drone)) {
            if (listeners.add(player.networkHandler)) entry.startTracking(player);
            ci.cancel();
            return;
        }
        // Also track entities near the operator's active drone view origin so distant NPCs, vehicles,
        // and projectiles remain visible in the drone camera feed beyond vanilla player tracking range.
        if (entity != player && entity.isAlive() && DroneNetworking.hasActiveSession(player)) {
            Vec3d viewPos = DroneNetworking.viewOrigin(player);
            if (viewPos != null) {
                double dx = entity.getX() - viewPos.x;
                double dz = entity.getZ() - viewPos.z;
                double horizDistSq = dx * dx + dz * dz;
                boolean alreadyListening = listeners.contains(player.networkHandler);
                double thresholdSq = alreadyListening ? REMOTE_STOP_RADIUS_SQ : REMOTE_START_RADIUS_SQ;
                if (horizDistSq <= thresholdSq) {
                    if (!alreadyListening && listeners.add(player.networkHandler)) {
                        entry.startTracking(player);
                    }
                    ci.cancel();
                }
            }
        }
    }
}
