package com.bobincraft.combat;

/** Logical humanoid hit regions; severed limbs and decapitation use a compact synchronized bit mask. */
public enum HitRegion {
    TORSO(0), HEAD(0), FACE(0), LEFT_ARM(1), RIGHT_ARM(2), LEFT_LEG(4), RIGHT_LEG(8);

    public static final int ARMS = 3, LEGS = 12, ALL_LIMBS = 15, HEAD_SEVERED = 64, ALL_SEVERABLE = ALL_LIMBS | HEAD_SEVERED;
    public final int limbMask;

    HitRegion(int limbMask) { this.limbMask = limbMask; }

    /** Bit mask used when a high-energy hit or lethal headshot severs a limb or the head. */
    public int severMask() {
        return (this == HEAD || this == FACE) ? HEAD_SEVERED : limbMask;
    }

    /** Coordinates are relative to the feet, width/height are the actual entity dimensions. */
    public static HitRegion classify(double dx, double dy, double dz, double width, double height, float yaw) {
        return classify(dx, dy, dz, width, height, yaw, yaw);
    }

    public static HitRegion classify(double dx, double dy, double dz, double width, double height,
                                     float bodyYaw, float headYaw) {
        double angle = Math.toRadians(bodyYaw);
        double side = dx * Math.cos(angle) + dz * Math.sin(angle);
        double fraction = dy / Math.max(0.01, height);
        if (fraction >= 0.80) {
            double headAngle = Math.toRadians(headYaw);
            double front = -dx * Math.sin(headAngle) + dz * Math.cos(headAngle);
            return front > 0.02 ? FACE : HEAD;
        }
        if (fraction < 0.43) return side > 0 ? LEFT_LEG : RIGHT_LEG;
        if (fraction < 0.77 && Math.abs(side) >= width * 0.29)
            return side > 0 ? LEFT_ARM : RIGHT_ARM;
        return TORSO;
    }

    public static double fragmentEnergy(double distance, double range, double transmission) {
        if (!Double.isFinite(distance) || !Double.isFinite(range) || !Double.isFinite(transmission)
                || range <= 0 || distance < 0 || distance >= range) return 0;
        double falloff = 1.0 - distance / range;
        return Math.max(0, Math.min(1, transmission)) * falloff * falloff;
    }
}
