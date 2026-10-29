package com.bobincraft.combat;

/** Shared policy for pose, item use, movement, death pose packing and horizontal fragment hit classification. */
public final class GuardInjuryPose {
    public static final float WIDTH = 0.65f, LENGTH = 2.35f, HEIGHT = 0.90f;
    public static final int FALL_TICKS = 8, CORPSE_TICKS = 240;
    public static final int POSE_NONE = 0, POSE_CRAWL = 1, POSE_BLAST = 2, POSE_FALLEN = 3;
    private GuardInjuryPose() { }

    public static int deathPose(int missing) { return (missing >> 4) & 3; }
    public static boolean headMissing(int missing) { return (missing & HitRegion.HEAD_SEVERED) != 0; }
    public static int pack(int limbs, int deathPose) { return (limbs & HitRegion.ALL_SEVERABLE) | ((deathPose & 3) << 4); }
    public static boolean prone(int missing) { return (missing & HitRegion.LEGS) != 0 || deathPose(missing) != 0; }
    public static boolean canUseHands(int missing) { return (missing & HitRegion.ARMS) == 0; }
    public static double speedPenalty(int missing) {
        int legs = Integer.bitCount(missing & HitRegion.LEGS);
        return legs == 0 ? 0 : legs == 1 ? -0.78 : -0.94;
    }
    public static float advanceFall(float amount, boolean prone) {
        return Math.max(0, Math.min(1, amount + (prone ? 1f : -1f) / FALL_TICKS));
    }

    /** Conservative rotated horizontal body box, centered on the entity so Entity.move keeps its origin. */
    public static net.minecraft.util.math.Box bounds(net.minecraft.util.math.Vec3d feet, float yaw) {
        double a = Math.toRadians(yaw);
        double x = (Math.abs(Math.cos(a)) * WIDTH + Math.abs(Math.sin(a)) * LENGTH) * .5;
        double z = (Math.abs(Math.sin(a)) * WIDTH + Math.abs(Math.cos(a)) * LENGTH) * .5;
        return new net.minecraft.util.math.Box(feet.x-x,feet.y,feet.z-z,feet.x+x,feet.y+HEIGHT,feet.z+z);
    }

    public static HitRegion hitRegion(double dx, double dy, double dz, float yaw) {
        double angle = Math.toRadians(yaw);
        double side = dx * Math.cos(angle) + dz * Math.sin(angle);
        double front = -dx * Math.sin(angle) + dz * Math.cos(angle);
        if (front > 0.68) return dy < 0.78 ? HitRegion.FACE : HitRegion.HEAD;
        if (front < -0.15) return side > 0 ? HitRegion.LEFT_LEG : HitRegion.RIGHT_LEG;
        if (Math.abs(side) > 0.17) return side > 0 ? HitRegion.LEFT_ARM : HitRegion.RIGHT_ARM;
        return HitRegion.TORSO;
    }
}
