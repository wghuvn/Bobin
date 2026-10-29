package com.bobincraft.warzone;

/** Stable checkpoint features and vehicle footprints, shared by dressing and server-side entity spawning. */
public final class OccupationPlan {
    public enum Style { CHICANE, LOGGING_BARRIER, CONCRETE_GATE, FLOOD_CONTROL, CARGO_INSPECTION }
    public record Trench(int x,int z,int length,boolean dugout) { }
    private static final java.util.Map<ZonePlan.Site,java.util.Optional<Trench>> TRENCHES=new java.util.concurrent.ConcurrentHashMap<>();
    public record ArmorPosition(int id,int x,int z,boolean buried,float yaw) { }
    private OccupationPlan() { }
    public static Style style(ZonePlan.Site site){return Style.values()[Math.floorMod(site.id()-1,5)];}
    public static ArmorPosition armor(ZonePlan.Site s){
        if(s.id()>=100||switch(s.id()){case 2,4,5,6,8,10,11->false;default->true;})return null;
        return new ArmorPosition(s.id(),s.x()+13,s.entrance().z()-9,s.id()%3!=1,0);
    }
    public static Trench trench(ZonePlan.Site site){return TRENCHES.computeIfAbsent(site,OccupationPlan::chooseTrench).orElse(null);}
    public static int trenchFloor(WarzoneTerrain terrain,Trench p){int y=Integer.MAX_VALUE;for(int i=4;i<p.length()-4;i++)y=Math.min(y,terrain.height(p.x(),p.z()-p.length()/2+i)-2);return y;}
    private static java.util.Optional<Trench> chooseTrench(ZonePlan.Site s){
        if(s.id()>=100)return java.util.Optional.empty();
        // Verified tactical flank positions defending each district's approach without intersecting any road, footpath, basin, or building pad.
        Trench curated=switch(s.id()){
            case 1->new Trench(s.x()-24,s.entrance().z()-12,24,false);
            case 2->new Trench(s.x()-48,s.entrance().z()-20,30,true);
            case 3->new Trench(s.x()-48,s.entrance().z()-16,31,false);
            case 4->new Trench(s.x()-56,s.entrance().z()-28,32,false);
            case 5->new Trench(s.x()+44,s.entrance().z()-32,28,false);
            case 6->new Trench(s.x()-18,s.entrance().z()-48,24,true);
            case 7->new Trench(s.x()-52,s.entrance().z()-14,30,false);
            case 8->new Trench(s.x()-48,s.entrance().z()-48,31,true);
            case 9->new Trench(s.x()+30,s.entrance().z()-16,32,false);
            case 10->new Trench(s.x()-52,s.entrance().z()-24,28,true);
            case 11->new Trench(s.x()+40,s.entrance().z()-14,29,false);
            default->null;
        };
        if(curated!=null&&clear(s,curated.x()-4,curated.z()-curated.length()/2-3,curated.x()+5,curated.z()+(curated.length()-1)/2+3)
                &&(!curated.dugout()||clear(s,curated.x()+2,curated.z()-12,curated.x()+11,curated.z()+4))){
            return java.util.Optional.of(curated);
        }
        boolean wantsDugout=s.id()%2==0;
        for(boolean dugout:wantsDugout?new boolean[]{true,false}:new boolean[]{false})
            for(int length:new int[]{28+s.id()%5,24})for(int dx:new int[]{-24,-34,-42,34,42,-48,48})for(int dz:new int[]{-4,0,-8,4,-12,-16,-24}){
                int x=s.x()+dx,z=s.entrance().z()-8+dz;
                if(!clear(s,x-4,z-length/2-3,x+5,z+(length-1)/2+3))continue;
                if(dugout&&!clear(s,x+2,z-12,x+11,z+4))continue;
                return java.util.Optional.of(new Trench(x,z,length,dugout));
            }
        return java.util.Optional.empty();
    }
    private static boolean clear(ZonePlan.Site s,int x0,int z0,int x1,int z1){
        int r=s.radius();
        if(x0<s.x()-r+3||x1>s.x()+r-3||z0<s.z()-r+3||z1>s.z()+r-3)return false;
        if(!s.contains(x0-2,z0-2,0)||!s.contains(x1+2,z1+2,0))return false;
        for(int sx:new int[]{s.x()-r+3,s.x()+r-3})for(int sz:new int[]{s.z()-r+3,s.z()+r-3}){
            if(sx>=x0-2&&sx<=x1+2&&sz>=z0-2&&sz<=z1+2)return false;
        }
        var park=ZonePlan.parking(s);
        for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){
            for(var b:s.buildings())if(b.contains(x,z,2))return false;
            for(var f:s.fields())if(f.contains(x,z,2))return false;
            for(int[] ep:ZoneExpansionStructures.extraPads(s)){
                if(Math.abs(x-(s.x()+ep[0]))<=ep[2]+2&&Math.abs(z-(s.z()+ep[1]))<=ep[3]+2)return false;
            }
            if(park!=null&&Math.abs(x-(park.x()+2))<=6&&Math.abs(z-(park.z()+4))<=8)return false;
            var armor=armor(s);if(armor!=null&&Math.abs(x-armor.x())<=6&&Math.abs(z-armor.z())<=7)return false;
            int u=x-s.x(),v=z-(s.entrance().z()-8);if(u>=-17&&u<=8&&v>=-10&&v<=7)return false;
        }
        return true;
    }
    public static boolean near(ZonePlan.Site s,int x,int z,int reach){
        if(s.id()>=100||Math.abs(x-s.x())>s.radius()+reach||Math.abs(z-s.z())>s.radius()+reach)return false;
        int u=x-s.x(),v=z-(s.entrance().z()-8);
        if(Math.abs(u)<=29+reach&&v>=-23-reach&&v<=16+reach)return true;
        var p=trench(s);if(p==null)return false;
        if(Math.abs(x-p.x())<=5+reach&&Math.abs(z-p.z())<=p.length()/2+5+reach)return true;
        return p.dugout()&&x>=p.x()+1-reach&&x<=p.x()+12+reach&&z>=p.z()-13-reach&&z<=p.z()+5+reach;
    }
}
