package com.bobincraft.warzone;

/** Broad coherent patches keep surface damage from looking like single-block confetti. */
public final class ZoneWeathering {
    private ZoneWeathering() { }
    public static double patch(long seed,int x,int z,int scale){
        int cx=Math.floorDiv(x,scale),cz=Math.floorDiv(z,scale);
        double a=WarzoneNoise.smoothstep(0,1,Math.floorMod(x,scale)/(double)scale),b=WarzoneNoise.smoothstep(0,1,Math.floorMod(z,scale)/(double)scale);
        double top=WarzoneNoise.lerp(WarzoneNoise.unit(WarzoneNoise.hash(seed,cx,cz)),WarzoneNoise.unit(WarzoneNoise.hash(seed,cx+1,cz)),a);
        double bottom=WarzoneNoise.lerp(WarzoneNoise.unit(WarzoneNoise.hash(seed,cx,cz+1)),WarzoneNoise.unit(WarzoneNoise.hash(seed,cx+1,cz+1)),a);
        return WarzoneNoise.lerp(top,bottom,b);
    }
}
