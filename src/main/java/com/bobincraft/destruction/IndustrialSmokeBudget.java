package com.bobincraft.destruction;

/** Shared client smoke eligibility and the existing emission-rate calculation, without Minecraft dependencies. */
public final class IndustrialSmokeBudget {
    private IndustrialSmokeBudget() {}

    public static boolean isEmitter(boolean industrial, boolean burning, float intensity,
                                    boolean chunkLoaded, double distance, double farDistance) {
        return industrial && burning && intensity > 0.0f && chunkLoaded
                && Double.isFinite(distance) && distance <= farDistance;
    }

    public static double particlesPerSecond(double distance, float intensity, int emitters,
                                            double nearDistance, double mediumDistance, double farDistance,
                                            int nearRate, int mediumRate, int maximumParticles) {
        if (!Double.isFinite(distance) || distance > farDistance || intensity <= 0.0f || emitters <= 0) return 0.0;
        double rate;
        if (distance <= nearDistance) rate = nearRate * (0.6 + 0.4 * intensity);
        else if (distance <= mediumDistance) rate = mediumRate * (0.5 + 0.5 * intensity);
        else rate = 0.65 * intensity;
        double maximumPerSecond = maximumParticles / 4.0;
        double total = rate * emitters;
        if (total > maximumPerSecond && total > 0.0) rate *= maximumPerSecond / total;
        return rate;
    }
}
