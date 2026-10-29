package com.bobincraft.destruction;

/** Smooth shared wind plus bounded, deterministic multi-scale local turbulence. No per-tick RNG. */
public final class IndustrialSmokeMotion {
    private IndustrialSmokeMotion() {}
    public static double windX(double time) { return .012 + .004*Math.sin(time/420); }
    public static double windZ(double time) { return .008 + .003*Math.cos(time/510); }
    public static double curl(double x, double y, double z, double time, double phase) {
        return .004*Math.sin(x*.11 + y*.07 + time*.025 + phase)
                + .0018*Math.sin(z*.39 - y*.21 + time*.081 + phase*1.7);
    }
    public static double variation(double phase, double age) {
        return 1 + .09*Math.sin(phase+age*.071) + .035*Math.sin(phase*2.3+age*.19);
    }
}
