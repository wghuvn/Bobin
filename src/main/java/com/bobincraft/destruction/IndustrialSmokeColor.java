package com.bobincraft.destruction;

/** Stateless illumination; callers always supply immutable base channels. */
public final class IndustrialSmokeColor {
    private IndustrialSmokeColor(){}
    public static float warmth(double ageFraction,double heightAboveSource) {
        return (float)(.10*Math.exp(-Math.max(0,heightAboveSource)/3.5)*(1-Math.max(0,Math.min(1,ageFraction))));
    }
    public static float channel(float base,float light){return Math.max(0,Math.min(1,base+light));}
}
