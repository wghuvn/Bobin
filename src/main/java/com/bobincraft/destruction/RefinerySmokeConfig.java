package com.bobincraft.destruction;

/**
 * Configurable parameters for the 3-Layer Industrial Refinery Smoke System (Section 4 & Bug 6.2).
 *
 * <ul>
 *   <li>{@link #smokeHeightMultiplier}: Vertical height multiplier (1.5x–2.0x over previous baseline).</li>
 *   <li>{@link #baseOpacity}: High opacity at the lower core so the fire base is densely shrouded in dark smoke.</li>
 *   <li>{@link #columnDensity}: Multi-puff density factor for the rising black/charcoal column.</li>
 *   <li>{@link #topDispersionWidth}: Horizontal expansion width of the upper dissipating plume canopy.</li>
 *   <li>{@link #particleScaleCurve}: Scale growth exponent across particle lifetime.</li>
 *   <li>{@link #alphaOverLifetime}: Retention fraction before soft upper-canopy dissipation begins.</li>
 * </ul>
 */
public final class RefinerySmokeConfig {
    public static float smokeHeightMultiplier = 1.90f;
    public static float baseOpacity = 0.98f;
    public static float columnOpacity = 0.95f;
    public static float canopyOpacity = 0.88f;
    public static float columnDensity = 1.75f;
    public static float topDispersionWidth = 2.60f;
    public static float particleScaleCurve = 0.65f;
    public static float alphaOverLifetime = 0.82f;

    private RefinerySmokeConfig() {}
}
