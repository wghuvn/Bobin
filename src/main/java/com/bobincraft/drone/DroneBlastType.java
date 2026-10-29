package com.bobincraft.drone;

/** Gameplay profiles, measured in blocks/ticks rather than real weapon specifications. */
public enum DroneBlastType {
    ARMOR_HIGH_EXPLOSIVE(4.2f, 44, 8, 7, 24, 640, 12, 22, true),
    DROPPED_GRENADE(2.3f, 10, 4.5, 3.6, 14, 192, 4, 6, false),
    KAMIKAZE(2.8f, 16, 6, 5, 18, 288, 5, 8, true),
    KAMIKAZE_FRAGMENTATION(2.3f, 6, 4.5, 3, 18, 512, 4, 8, true),
    KAMIKAZE_BREACH(2.8f, 14, 5, 4, 10, 96, 3, 4, true),
    BOMB_HIGH_EXPLOSIVE(2.8f, 12, 6, 5, 10, 96, 2, 3, false),
    BOMB_FRAGMENTATION(2f, 4, 3.5, 2, 18, 512, 4, 8, false),
    HEAVY_STRUCTURAL_BOMB(3.8f, 32, 7.5, 6.5, 18, 320, 8, 16, false),
    FP1_DEEP_STRIKE(12.0f, 114, 24.0, 21.0, 55.0, 840, 30, 54, true);

    public final float pressurePower;
    public final float pressureDamage, maxFragmentDamage;
    public final boolean kamikaze;
    public final double windowRadius, scorchRadius, fragmentRange;
    public final int fragmentCount;
    public final float fragmentDamage;

    DroneBlastType(float pressurePower, float pressureDamage, double windowRadius, double scorchRadius,
                   double fragmentRange, int fragmentCount, float fragmentDamage, float maxFragmentDamage, boolean kamikaze) {
        this.pressurePower = pressurePower;
        this.pressureDamage = pressureDamage;
        this.maxFragmentDamage = maxFragmentDamage;
        this.kamikaze = kamikaze;
        this.windowRadius = windowRadius;
        this.scorchRadius = scorchRadius;
        this.fragmentRange = fragmentRange;
        this.fragmentCount = fragmentCount;
        this.fragmentDamage = fragmentDamage;
    }

    public double pressureRadius() { return pressurePower * 2.0; }

    /** Health damage is independent of fragment energy used to determine limb injuries. */
    public float pressureDamageAt(double distance, double exposure) {
        if (!Double.isFinite(distance) || !Double.isFinite(exposure) || distance < 0 || distance >= pressureRadius()) return 0;
        double impact = (1 - distance / pressureRadius()) * Math.max(0, Math.min(1, exposure));
        return (float) (pressureDamage * (impact * impact + impact) * .5);
    }

    public float addFragmentDamage(float accumulated, double energy) {
        return Math.min(maxFragmentDamage, accumulated + (float) energy * fragmentDamage);
    }

    public static DroneBlastType read(String name) {
        try { return valueOf(name); }
        catch (IllegalArgumentException | NullPointerException ignored) { return DROPPED_GRENADE; }
    }
}
