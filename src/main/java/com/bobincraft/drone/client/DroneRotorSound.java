package com.bobincraft.drone.client;

import com.bobincraft.drone.DroneContent;
import com.bobincraft.drone.DroneEntity;
import com.bobincraft.drone.DroneType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Realistic flight acoustics:
 * - Multirotors (FPV, Bomber, Heavy): high-RPM electric brushless rotor whine.
 * - Fixed-wing FP-1: authentic 2-stroke twin-cylinder piston engine + pusher propeller roar
 *   with throttle, terminal dive RPM boost, and Doppler shift for ground observers.
 */
final class DroneRotorSound extends MovingSoundInstance {
    static final double OBSERVER_RANGE_SQUARED = 64 * 64;
    static final double FIXED_WING_OBSERVER_RANGE_SQUARED = 160 * 160;
    private final DroneEntity drone;
    private final boolean pilot;

    DroneRotorSound(DroneEntity drone, boolean pilot) {
        super(drone.getDroneType() == DroneType.FIXED_WING
                        ? DroneContent.FP1_ENGINE_SOUND
                        : DroneContent.FPV_MOTOR_SOUND,
                SoundCategory.NEUTRAL, Random.create());
        this.drone = drone;
        this.pilot = pilot;
        repeat = true;
        repeatDelay = 0;
        relative = pilot;
        attenuationType = pilot ? SoundInstance.AttenuationType.NONE : SoundInstance.AttenuationType.LINEAR;
        boolean fixedWing = drone.getDroneType() == DroneType.FIXED_WING;
        volume = pilot ? (fixedWing ? 0.28f : 0.24f) : (fixedWing ? 1.45f : 0.55f);
        pitch = fixedWing ? 0.95f : 1.2f;
        updatePosition();
    }

    @Override public void tick() {
        if (!canContinue()) { setDone(); return; }
        updatePosition();
        float power = drone.getThrottle();
        DroneType type = drone.getDroneType();
        boolean heavy = type == DroneType.HEAVY;
        boolean fixedWing = type == DroneType.FIXED_WING;

        if (fixedWing) {
            Vec3d vel = drone.getVelocity();
            float diveBoost = (float) MathHelper.clamp(-vel.y * 0.42, -0.08, 0.28);
            float doppler = 0.0f;
            float distFactor = 1.0f;
            if (!pilot) {
                MinecraftClient client = MinecraftClient.getInstance();
                Entity listener = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
                if (listener != null) {
                    Vec3d toListener = listener.getEyePos().subtract(drone.getPos());
                    double dist = toListener.length();
                    distFactor = (float) MathHelper.clamp(1.0 - (dist / 160.0) * 0.65, 0.25, 1.0);
                    if (dist > 0.5 && vel.lengthSquared() > 1e-4) {
                        double radialSpeed = vel.dotProduct(toListener.normalize());
                        doppler = (float) MathHelper.clamp(radialSpeed * 0.16, -0.18, 0.22);
                    }
                }
            }
            float targetVol = pilot
                    ? (0.24f + power * 0.34f + Math.max(0.0f, diveBoost * 0.35f))
                    : (0.75f + power * 0.85f + Math.max(0.0f, diveBoost * 0.45f)) * distFactor;
            volume = MathHelper.lerp(0.20f, volume, targetVol);
            float targetPitch = MathHelper.clamp(0.84f + power * 0.38f + diveBoost + doppler, 0.68f, 1.55f);
            pitch = MathHelper.lerp(0.18f, pitch, targetPitch);
            return;
        }

        power = MathHelper.clamp(power, 0.0f, 1.0f);
        float volMult = heavy ? 1.12f : 1.0f;
        float targetVolume = (pilot ? 0.24f + power * 0.34f : 0.55f + power * 0.45f) * volMult;
        volume = MathHelper.lerp(0.18f, volume, targetVolume);
        float basePitch = heavy ? 0.68f : 0.84f;
        float pitchSpan = heavy ? 0.48f : 0.76f;
        pitch = MathHelper.lerp(0.15f, pitch, basePitch + power * pitchSpan);
    }

    boolean canContinue() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || drone.getWorld() != client.world || !drone.isAlive() || !(drone.isArmed() || drone.isAutoLanding())
                || pilot != DroneClient.isViewing(drone)) return false;
        if (pilot) return true;
        Entity listener = DroneClient.isActive() && DroneClient.getDrone() != null ? DroneClient.getDrone() : client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        double maxRangeSq = drone.getDroneType() == DroneType.FIXED_WING ? FIXED_WING_OBSERVER_RANGE_SQUARED : OBSERVER_RANGE_SQUARED;
        return listener != null && listener.squaredDistanceTo(drone) <= maxRangeSq;
    }

    private void updatePosition() {
        x = pilot ? 0 : drone.getX();
        y = pilot ? 0 : drone.getY();
        z = pilot ? 0 : drone.getZ();
    }

    @Override public boolean shouldAlwaysPlay() { return true; }
}
