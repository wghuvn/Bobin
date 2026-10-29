package com.bobincraft.raid.guard;

/** Hearing is not a firing solution. A close armed drone or an injury prioritizes survival. */
public final class DroneThreatPolicy {
    public enum Response { IGNORE, WATCH, ENGAGE, TAKE_COVER }
    private DroneThreatPolicy() { }
    public static Response response(boolean hostileOwner,boolean armed,boolean visible,double distance,boolean approaching,boolean wounded){
        if(!hostileOwner||!Double.isFinite(distance)||distance>48||distance<0)return Response.IGNORE;
        if(!armed)return distance<=20?Response.WATCH:Response.IGNORE;
        if(!visible)return distance<=22?Response.TAKE_COVER:Response.IGNORE;
        if(wounded||distance<14||approaching&&distance<26)return Response.TAKE_COVER;
        return Response.ENGAGE;
    }
    public static boolean crawling(int missingLimbs){return (missingLimbs&com.bobincraft.combat.HitRegion.LEGS)!=0;}
}
