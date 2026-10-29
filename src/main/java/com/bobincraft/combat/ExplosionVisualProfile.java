package com.bobincraft.combat;

import com.bobincraft.drone.DroneBlastType;

/** Cosmetic parameters only. Stable wire names deliberately do not use enum ordinals. */
public enum ExplosionVisualProfile {
    FPV_STANDARD("fpv_standard", 1.0, 28, 48, 12, 0.30, 1.0, 3, 1.0, 14, 1.8),
    FPV_FRAGMENTATION("fpv_fragmentation", .80, 18, 36, 28, 0.10, 1.25, 2, 0.75, 8, 2.8),
    FPV_BREACHING("fpv_breaching", .90, 22, 42, 8, 0.22, .55, 2, 0.9, 10, 3.2),
    BOMBER_HE("bomber_he", 1.15, 30, 52, 10, .32, 1.35, 4, 1.0, 18, 1.6),
    BOMBER_FRAGMENTATION("bomber_fragmentation", .95, 20, 40, 32, .08, 1.65, 2, 0.8, 7, 2.6),
    BOMBER_HEAVY("bomber_heavy", 1.75, 44, 68, 18, .38, 1.60, 5, 1.0, 24, 1.4),
    FP1_STRIKE("fp1_strike", 3.2, 64, 90, 28, .42, 2.0, 7, 1.0, 34, 1.2),
    GROM7_DEMOLITION("grom7_demolition", 1.25, 36, 60, 6, .26, .65, 4, 1.0, 20, 2.1),
    TANK_HE("tank_he", 1.35, 32, 50, 16, .24, 1.10, 2, 1.0, 12, 3.0),
    RPG_IMPACT("rpg_impact", .85, 24, 38, 10, .20, .40, 2, 0.9, 10, 3.6),
    HAND_GRENADE("hand_grenade", .48, 12, 28, 14, .06, .85, 1, 0.55, 6, 2.7),
    DROPPED_GRENADE("dropped_grenade", .60, 16, 32, 16, .10, 1.05, 2, 0.65, 8, 2.3);

    public final String id;
    public final double scale, heatFraction, spread;
    public final int smokeCount, smokeLife, streakCount, flashTicks, fireTicks;
    public final double flashIntensity, expansionSpeed;
    ExplosionVisualProfile(String name, double scale, int count, int life, int streaks, double heat, double spread, int flashTicks, double flashIntensity, int fireTicks, double expansionSpeed) {
        this.id = "bobin_craft:" + name;
        this.scale = scale; smokeCount = count; smokeLife = life; streakCount = streaks;
        heatFraction = heat; this.spread = spread;
        this.flashTicks = flashTicks; this.flashIntensity = flashIntensity;
        this.fireTicks = fireTicks; this.expansionSpeed = expansionSpeed;
    }
    /** Unknown identifiers use a small neutral effect, never an industrial profile. */
    public static ExplosionVisualProfile find(String id) {
        for (var profile : values()) if (profile.id.equals(id)) return profile;
        return null;
    }
    public static ExplosionVisualProfile select(DroneBlastType type, boolean rpg, boolean handThrown) {
        if (rpg) return RPG_IMPACT;
        if (handThrown) return HAND_GRENADE;
        return switch (type) {
            case KAMIKAZE -> FPV_STANDARD;
            case KAMIKAZE_FRAGMENTATION -> FPV_FRAGMENTATION;
            case KAMIKAZE_BREACH -> FPV_BREACHING;
            case BOMB_HIGH_EXPLOSIVE -> BOMBER_HE;
            case BOMB_FRAGMENTATION -> BOMBER_FRAGMENTATION;
            case HEAVY_STRUCTURAL_BOMB -> BOMBER_HEAVY;
            case FP1_DEEP_STRIKE -> FP1_STRIKE;
            case ARMOR_HIGH_EXPLOSIVE -> TANK_HE;
            case DROPPED_GRENADE -> DROPPED_GRENADE;
        };
    }
}
