package com.bobincraft.warzone;

import java.util.*;
import static com.bobincraft.warzone.ZonePlan.*;

/** Small blended foundations and machinery pads; never clears an entire square district. */
final class ZoneGeography {
    private final WarzoneTerrain terrain;
    private final List<Site> areas;
    private final Map<Integer,List<Pad>> pads=new HashMap<>();
    ZoneGeography(WarzoneTerrain terrain,List<Site> areas){this.terrain=terrain;this.areas=List.copyOf(areas);for(Site site:areas)pads.put(site.id(),props(site));}
    int rawHeight(int x,int z){var sample=new WarzoneTerrain.Sample();terrain.naturalLandscape().sample(x,z,sample);return sample.height;}
    List<Pad> pads(Site site){return pads.getOrDefault(site.id(),List.of());}
    static double yardDistance(Site s,int x,int z){double dx=x-s.x(),dz=z-s.z();
        double radius=(s.radius()-12)*(.81+.06*Math.sin(dx*.075+s.id())+.05*Math.cos(dz*.09));return Math.hypot(dx,dz*1.04)-radius;}
    int height(int x,int z,int natural){
        double strongest=0,target=natural;
        for(Site site:areas)if(site.contains(x,z,20)){
            if(industrial(site.theme())){double w=fade(yardDistance(site,x,z),16);if(w>strongest){strongest=w;target=natural+(site.y()-natural)*w;}}
            for(Pad pad:pads(site)){double w=fade(pad.distance(x,z),10);if(w>=strongest){strongest=w;target=natural+(pad.y()-natural)*w;}}
            for(Building b:site.buildings()){double w=b.weight(x,z);if(w>=strongest){strongest=w;target=natural+(b.y()-natural)*w;}}
            for(Field f:site.fields()){double w=fade(f.distance(x,z),10);if(w>=strongest){strongest=w;target=natural+(f.y()-natural)*w;}}
        }return (int)Math.round(target);
    }
    boolean hardFloor(int x,int z){for(Site s:areas)if(s.contains(x,z,0)){
        for(Building b:s.buildings())if(b.contains(x,z,2))return true;
        for(Field f:s.fields())if(f.contains(x,z,0))return true;
        for(Pad pad:pads(s))if(pad.distance(x,z)==0)return true;
    }return false;}
    boolean protectedWithin(int x,int z,int reach){
        for(Site s:areas)if(s.contains(x,z,reach+12)){
            if(industrial(s.theme())&&yardDistance(s,x,z)<reach+3)return true;
            if(industrial(s.theme())){
                int dx=Math.abs(x-s.x()),dz=Math.abs(z-s.z()),fence=s.theme()==Theme.SUBSTATION?43:s.radius()-16;
                if(Math.abs(dx-fence)<=reach+2&&dz<=((s.theme()==Theme.SUBSTATION)?43:32)+reach+2)return true;
                if(s.theme()==Theme.SUBSTATION&&Math.abs(dz-43)<=reach+2&&dx<=43+reach+2)return true;
            }
            for(Building b:s.buildings())if(b.distance(x,z)<=reach+2)return true;
            for(Field f:s.fields())if(f.distance(x,z)<=reach+2)return true;
            for(Pad pad:pads(s))if(pad.distance(x,z)<=reach+2)return true;
            for(int[] ep:ZoneExpansionStructures.extraPads(s)){
                double d=Math.hypot(Math.max(0,Math.abs(x-(s.x()+ep[0]))-ep[2]),Math.max(0,Math.abs(z-(s.z()+ep[1]))-ep[3]));
                if(d<=reach+2)return true;
            }
        }return false;
    }
    private List<Pad> props(Site s){
        List<Pad> result=new ArrayList<>();int[][] shapes;
        if(s.id()>=100)shapes=new int[][]{{17,15,3,5}};
        else shapes=switch(s.theme()){
            case ALDER->new int[][]{{-12,4,4,4},{28,51,5,4},{-12,44,4,3},{12,29,3,2},{28,55,13,2}};
            case BRICK->new int[][]{{12,2,4,4},{-28,50,4,5},{-12,44,4,3},{12,29,3,2},{-28,55,13,2}};
            case PINE->new int[][]{{-43,3,4,6},{-12,44,4,3},{12,29,3,2},{28,55,13,2}};
            case FARM->new int[][]{{-55,-31,9,5},{-53,7,8,6},{10,37,6,3},{-8,-19,3,2}};
            case SAWMILL->new int[][]{{-49,0,4,31},{23,7,4,7},{17,-4,6,3},{29,14,6,3},{-35,29,8,6},{15,33,7,5}};
            case CONCRETE->new int[][]{{-48,-38,4,4},{-37,36,13,5},{29,6,4,7},{-33,11,8,7},{-46,5,6,6},{28,-9,10,5},{16,24,7,5}};
            case ESTATE->new int[][]{{-15,37,29,8},{-11,25,7,7},{6,-28,6,6},{43,2,2,8},{11,-5,3,2}};
            case WATERWORKS->new int[][]{{-26,27,15,15},{-46,-37,5,5},{-26,0,3,15},{27,-5,7,5},{13,5,3,2}};
            case SHACKS->new int[][]{{3,-16,4,4},{-22,20,4,6}};
            case REFINERY->new int[][]{{22,-39,11,11},{45,-39,11,11},{22,-15,11,11},{45,-15,11,11},{-3,-26,10,8},{-54,-25,4,4},{4,7,52,5},{-50,12,5,7},{14,29,3,2}};
            case SUBSTATION->new int[][]{{0,-7,19,9},{0,-27,20,3},{0,9,20,3},{34,-6,3,2}};
        };
        for(int[] f:shapes)result.add(new Pad(s.x()+f[0],s.z()+f[1],f[2],f[3],industrial(s.theme())?s.y():rawHeight(s.x()+f[0],s.z()+f[1])));
        if(s.id()<100){
            result.add(new Pad(s.x()-12,s.entrance().z()-10,4,3,rawHeight(s.x()-12,s.entrance().z()-10)));
            for(int side:new int[]{-1,1})result.add(new Pad(s.x()+side*7,s.entrance().z()-4,3,3,rawHeight(s.x()+side*7,s.entrance().z()-4)));
        }
        if(s.theme()==Theme.REFINERY){result.add(new Pad(s.x()-48,s.z()-48,9,8,s.y()));result.add(new Pad(s.x()-62,s.z()+53,3,3,s.y()));result.add(new Pad(s.x()-29,s.z()-4,11,10,s.y()));}
        if(s.theme()==Theme.SUBSTATION)result.add(new Pad(s.x(),s.z()+4,20,17,s.y()));
        Point park=parking(s);if(park!=null)result.add(new Pad(park.x()+2,park.z()+4,5,7,industrial(s.theme())?s.y():rawHeight(park.x(),park.z())));
        return List.copyOf(result);
    }
}
