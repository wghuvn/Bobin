package com.bobincraft.drone;

import net.minecraft.util.math.Vec3d;

/** Distinct fictional game payloads. The server owns release, arming and detonation. */
public enum DronePayloadProfile {
    GRENADE(60, 0, .045, .992, .28, 7),
    HIGH_EXPLOSIVE(160, 6, .060, .998, .06, 0),
    FRAGMENTATION(60, 12, .040, .990, .16, 12),
    HEAVY_STRUCTURAL(180, 8, .068, .998, .04, 0);

    public final int fuseTicks, armingTicks;
    public final double gravity, drag, bounce;
    public final float spin;
    DronePayloadProfile(int fuseTicks, int armingTicks, double gravity, double drag, double bounce, float spin) {
        this.fuseTicks = fuseTicks; this.armingTicks = armingTicks;
        this.gravity = gravity; this.drag = drag; this.bounce = bounce; this.spin = spin;
    }
    public static DronePayloadProfile of(DroneBlastType blast) {
        return switch (blast) {
            case BOMB_HIGH_EXPLOSIVE -> HIGH_EXPLOSIVE;
            case BOMB_FRAGMENTATION -> FRAGMENTATION;
            case HEAVY_STRUCTURAL_BOMB -> HEAVY_STRUCTURAL;
            default -> GRENADE;
        };
    }
    public Vec3d initialVelocity(Vec3d carrier) {
        double inheritance = (this == HIGH_EXPLOSIVE || this == HEAVY_STRUCTURAL) ? .75 : 1;
        return new Vec3d(carrier.x * inheritance, Math.min(-.03, carrier.y - .03), carrier.z * inheritance);
    }
    public Vec3d fallingVelocity(Vec3d previous) { return previous.multiply(drag).add(0, -gravity, 0); }
    public boolean detonates(int age, int fuse, boolean contact, double groundDistance) {
        if (fuse <= 0) return true;
        if (age < armingTicks) return false;
        return (this == HIGH_EXPLOSIVE || this == HEAVY_STRUCTURAL) && contact || this == FRAGMENTATION && (contact
                || Double.isFinite(groundDistance) && groundDistance >= 0 && groundDistance <= 1.8);
    }
    public Vec3d releasePosition(net.minecraft.util.math.Box carrier, double height, float yaw, int releaseIndex) {
        Vec3d center = DronePayloadSafety.releasePosition(carrier, height);
        if (this == GRENADE) return center;
        double side = releaseIndex % 2 == 0 ? -.16 : .16;
        double rear = this == FRAGMENTATION && releaseIndex >= 2 ? -.2 : 0;
        double radians = Math.toRadians(yaw);
        return center.add(Math.cos(radians) * side - Math.sin(radians) * rear, 0,
                Math.sin(radians) * side + Math.cos(radians) * rear);
    }
}
