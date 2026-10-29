package com.bobincraft.drone;

/** Deliberately bounded game tuning, expressed in Minecraft blocks and ticks. */
public enum DroneType {
    FPV("fpv_drone", 7200, 224, 1.15, 0, 0.48f, 0.26f, 0.38f),
    BOMBER("bomber_drone", 9600, 192, 0.68, 3, 1.75f, 0.85f, 1.36f),
    HEAVY("heavy_drone", 12000, 256, 0.52, 2, 2.35f, 1.05f, 1.72f),
    FIXED_WING("fp1_drone", 48000, 1120, 1.45, 0, 4.30f, 1.28f, 2.96f);

    public final String id;
    public final int batteryTicks;
    public final double range;
    public final double maxSpeed;
    public final int payloads;
    public final float width, height, renderScale;

    DroneType(String id, int batteryTicks, double range, double maxSpeed, int payloads,
              float width, float height, float renderScale) {
        this.id = id;
        this.batteryTicks = batteryTicks;
        this.range = range;
        this.maxSpeed = maxSpeed;
        this.payloads = payloads;
        this.width = width;
        this.height = height;
        this.renderScale = renderScale;
    }

    public boolean isCarrier() {
        return this == BOMBER || this == HEAVY;
    }

    public boolean isFixedWing() {
        return this == FIXED_WING;
    }

    public boolean isStrikeImpact() {
        return this == FPV || this == FIXED_WING;
    }
}
