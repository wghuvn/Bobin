package com.bobincraft.combat;

public enum InjurySeverity {
    NONE(0, 0), LIGHT(12_000, 0.025f), SEVERE(6_000, 0.05f), CRITICAL(1_200, 0.25f);

    public final int durationTicks;
    public final float damagePerSecond;

    InjurySeverity(int durationTicks, float damagePerSecond) {
        this.durationTicks = durationTicks;
        this.damagePerSecond = damagePerSecond;
    }

    public static InjurySeverity fromEnergy(double energy) {
        if (!Double.isFinite(energy) || energy < 0.06) return NONE;
        return energy >= 0.55 ? CRITICAL : energy >= 0.24 ? SEVERE : LIGHT;
    }

    public static InjurySeverity fromId(int id) {
        return values()[Math.max(0, Math.min(values().length - 1, id))];
    }
}
