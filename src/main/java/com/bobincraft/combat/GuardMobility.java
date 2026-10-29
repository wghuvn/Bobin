package com.bobincraft.combat;

/** Temporary leg trauma is separate from permanent missing-limb state and its appearance. */
public final class GuardMobility {
    public static final int RECOVERY_TICKS=1200;
    private GuardMobility() { }
    public static boolean legWound(HitRegion region,float damage){return region!=null&&(region.limbMask&HitRegion.LEGS)!=0&&Float.isFinite(damage)&&damage>=2;}
    public static int savedTicks(int value){return Math.max(0,Math.min(RECOVERY_TICKS,value));}
    public static double speedPenalty(int missing,boolean temporary){double permanent=GuardInjuryPose.speedPenalty(missing);return permanent!=0?permanent:temporary?-.72:0;}
}
