package com.bobincraft.warzone;

/** Shared animation and collision timing, independent of rendering frame rate. */
public final class ManholeMotion {
    public static final int TICKS=16;
    private ManholeMotion(){ }
    public static float progress(float from,boolean open,double elapsed){
        double t=Math.max(0,Math.min(1,elapsed/TICKS)),ease=t*t*(3-2*t);
        return (float)(from+((open?1:0)-from)*ease);
    }
    public static boolean passable(float progress){return progress>.92f;}
    public static double slide(float progress){return Math.max(0,(progress-.15)/.85)*1.08;}
    public static double lift(float progress){return Math.min(1,progress/.15)*.09;}
}
