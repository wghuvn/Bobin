package com.bobincraft.drone;

import net.minecraft.util.math.Vec3d;

/** Small in-game charge: gravity in flight, low-energy rebounds and rolling friction until it settles. */
public final class DronePayloadPhysics {
    private static final double GRAVITY = 0.045;
    private static final double AIR_DRAG = 0.992;
    private static final double BOUNCE = 0.28;
    private static final double GROUND_FRICTION = 0.58;
    private static final double GRENADE_ROLL_FRICTION = 0.80;
    private static final double SETTLE_VERTICAL = 0.07;
    private static final double SETTLE_HORIZONTAL = 0.008;
    private DronePayloadPhysics() { }

    public static Vec3d fallingVelocity(Vec3d previous) {
        return previous.multiply(AIR_DRAG).add(0, -GRAVITY, 0);
    }

    public static Vec3d afterCollision(Vec3d requested, boolean blockedX, boolean blockedY, boolean blockedZ, boolean supported) {
        return afterCollision(requested, blockedX, blockedY, blockedZ, supported, BOUNCE);
    }

    public static Vec3d afterCollision(Vec3d requested, boolean blockedX, boolean blockedY, boolean blockedZ, boolean supported, double bounce) {
        return afterCollision(requested, blockedX, blockedY, blockedZ, supported, bounce, GROUND_FRICTION);
    }

    public static Vec3d afterCollision(Vec3d requested, boolean blockedX, boolean blockedY, boolean blockedZ, boolean supported, double bounce, double groundFriction) {
        double x = blockedX ? -requested.x * bounce : requested.x;
        double y = blockedY ? -requested.y * bounce : requested.y;
        double z = blockedZ ? -requested.z * bounce : requested.z;
        if (blockedY && Math.abs(y) < SETTLE_VERTICAL) y = 0;
        if (supported && y == 0) { x *= groundFriction; z *= groundFriction; }
        if (supported && Math.abs(x) < SETTLE_HORIZONTAL) x = 0;
        if (supported && Math.abs(z) < SETTLE_HORIZONTAL) z = 0;
        return new Vec3d(x, y, z);
    }

    public static Vec3d afterHandGrenadeCollision(Vec3d requested, boolean blockedX, boolean blockedY, boolean blockedZ, boolean supported) {
        return afterCollision(requested, blockedX, blockedY, blockedZ, supported, 0.34, GRENADE_ROLL_FRICTION);
    }
}
