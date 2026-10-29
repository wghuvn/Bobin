package com.bobincraft.warzone;

import java.util.*;

/** Small, isolated destinations selected in existing woodland openings; never adds a road or power spur. */
public final class ZoneDiscoveries {
    public enum Kind { BURNT_GARAGE, CHAPEL_RUIN, RADIO_SHACK, HUNTERS_CAMP, FIELD_HOSPITAL, WATER_TOWER, SUPPLY_CACHE, QUARRY_SHELTER, RIDGE_RELAY, TRENCH_DEPOT }
    public record Spot(int id,Kind kind,int x,int z,int y,int rotation,int variant) { public int radius(){return kind==Kind.RIDGE_RELAY||kind==Kind.TRENCH_DEPOT?20:kind==Kind.FIELD_HOSPITAL?13:11;} }
    private record Candidate(int x,int z,int y,double score) { }
    private final List<Spot> spots;
    ZoneDiscoveries(WarzoneTerrain terrain,ZonePlan plan){
        if(terrain.generationVersion()<8){spots=List.of();return;}
        List<Candidate> candidates=new ArrayList<>();var sample=new WarzoneTerrain.Sample();
        for(int z=-864;z<=864;z+=72)for(int x=-864;x<=864;x+=72){
            long hash=WarzoneNoise.hash(terrain.seed+11317,x,z);int px=x+(int)Math.floorMod(hash,25)-12,pz=z+(int)Math.floorMod(hash>>>8,25)-12;
            terrain.naturalLandscape().sample(px,pz,sample);if(sample.height<66||sample.height>116||sample.channel>.01||sample.edge<100)continue;
            boolean blocked=Math.hypot(px-terrain.landingX(),pz-terrain.landingZ())<120;
            for(var s:plan.allSites())if(Math.hypot(px-s.x(),pz-s.z())<s.radius()+64)blocked=true;
            if(plan.protectedWithin(px,pz,28))blocked=true;
            if(terrain.generationVersion()>=9&&(Math.hypot(px+900,pz+672)<150||Math.hypot(px+132,pz+830)<150))blocked=true;
            if(blocked)continue;
            int lo=sample.height,hi=sample.height;
            for(int dz=-12;dz<=12;dz+=6)for(int dx=-12;dx<=12;dx+=6){terrain.naturalLandscape().sample(px+dx,pz+dz,sample);lo=Math.min(lo,sample.height);hi=Math.max(hi,sample.height);}
            if(hi-lo>6)continue;
            candidates.add(new Candidate(px,pz,(hi+lo)/2,terrain.naturalLandscape().forest(px,pz)*3+(hi-lo)*.12+WarzoneNoise.unit(hash)*.4));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).thenComparingInt(Candidate::x).thenComparingInt(Candidate::z));
        List<Spot> chosen=new ArrayList<>();for(var c:candidates){boolean near=false;for(var old:chosen)if(Math.hypot(c.x-old.x,c.z-old.z)<150)near=true;
            if(near)continue;int id=chosen.size();chosen.add(new Spot(1000+id,Kind.values()[id%8],c.x,c.z,c.y,(id+id/8)%4,id));if(chosen.size()==24)break;}
        if(terrain.generationVersion()>=9){
            for(int[] location:new int[][]{{-900,-672},{-132,-830}}){int id=chosen.size();terrain.naturalLandscape().sample(location[0],location[1],sample);
                chosen.add(new Spot(1000+id,id==24?Kind.RIDGE_RELAY:Kind.TRENCH_DEPOT,location[0],location[1],sample.height,0,id));}
        }
        spots=List.copyOf(chosen);
    }
    public List<Spot> spots(){return spots;}
    public boolean near(int x,int z,int reach){for(var s:spots)if(Math.hypot(x-s.x,z-s.z)<s.radius()+reach)return true;return false;}
    int height(int x,int z,int natural){for(var s:spots){if(s.kind==Kind.RIDGE_RELAY||s.kind==Kind.TRENCH_DEPOT)continue;double d=Math.hypot(x-s.x,z-s.z);double weight=ZonePlan.fade(Math.max(0,d-6),7);if(weight>0)return (int)Math.round(natural+(s.y-natural)*weight);}return natural;}
}
