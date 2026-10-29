package com.bobincraft.warzone;

import java.util.*;

/** Three-block clear passages, graded floors, firing bays, sandbag parapets and earth-mounded timber dugouts. */
final class Earthworks {
    private Earthworks() { }
    static int trench(WarzoneTerrain t,Blueprint bp,int cx,int cz,int length,boolean alongX){
        length=Math.max(24,length); int start=-length/2;
        int floor=Integer.MAX_VALUE;
        for(int i=4;i<length-4;i++)floor=Math.min(floor,t.height(cx+(alongX?start+i:0),cz+(alongX?0:start+i))-2);
        Map<Long,Integer> floors=new LinkedHashMap<>();
        for(int i=-2;i<length+2;i++){
            // A traversable traverse: each turn advances only one cell, with overlapping 3-wide landings.
            int offset=i<length/3?0:i==length/3?1:i<length*2/3?2:i==length*2/3?1:0;
            int x=cx+(alongX?start+i:offset),z=cz+(alongX?offset:start+i);
            int natural=t.height(x,z),edge=Math.max(0,Math.min(i,length-1-i));
            int h=Math.max(floor,natural-Math.min(edge,Math.max(0,natural-floor)));
            for(int w=-1;w<=1;w++)floors.merge(ZonePlan.key(x+(alongX?0:w),z+(alongX?w:0)),h,Math::min);
            // Add asymmetric firing bays (огневые ячейки) and side recesses along the defended flanks.
            if(i>=4&&i<=7){
                floors.merge(ZonePlan.key(x+(alongX?0:-2),z+(alongX?-2:0)),h,Math::min);
            }else if(i>=11&&i<=14){
                floors.merge(ZonePlan.key(x+(alongX?0:2),z+(alongX?2:0)),h,Math::min);
            }else if(i>=length-9&&i<=length-6){
                floors.merge(ZonePlan.key(x+(alongX?0:-2),z+(alongX?-2:0)),h,Math::min);
            }
        }
        excavate(t,bp,floors);return floor;
    }
    static void excavate(WarzoneTerrain t,Blueprint bp,Map<Long,Integer> floors){
        // Lower high cells until a walking step is at most one block; stairs turn it into two half-steps.
        boolean changed;do{changed=false;for(var e:floors.entrySet())for(var d:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            long k=ZonePlan.key(ZonePlan.x(e.getKey())+d.getOffsetX(),ZonePlan.z(e.getKey())+d.getOffsetZ());Integer other=floors.get(k);
            if(other!=null&&e.getValue()>other+1){e.setValue(other+1);changed=true;}
        }}while(changed);
        // All retaining work is outside the entire excavation, including turns, firing bays and entrance landings.
        for(var e:floors.entrySet())for(var d:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            int x=ZonePlan.x(e.getKey()),z=ZonePlan.z(e.getKey());
            int wx=x+d.getOffsetX(),wz=z+d.getOffsetZ();if(floors.containsKey(ZonePlan.key(wx,wz)))continue;
            var existing=bp.get(wx,e.getValue()+1,wz);if(existing!=null&&(existing.isAir()||existing.id.equals("spruce_stairs")))continue;
            boolean oldFloor=false;for(int yy=e.getValue()-2;yy<=e.getValue()+3;yy++){var prior=bp.get(wx,yy,wz);if(prior!=null&&prior.id.equals("bobin_craft:trampled_mud"))oldFloor=true;}if(oldFloor)continue;
            int top=t.height(wx,wz);
            for(int y=e.getValue()-2;y<=top;y++){
                String wallMat=y==top?"bobin_craft:dark_forest_soil":
                    (Math.floorMod(wx+wz+y,4)==0?"bobin_craft:damaged_wood_panel":"spruce_planks");
                put(bp,wx,y,wz,wallMat);
            }
            if(Math.floorMod(wx*3+wz*5,4)==0)for(int y=e.getValue();y<top;y++)put(bp,wx,y,wz,"stripped_spruce_log");
            // Sandbag parapets and earthen breastworks along the deep defended rim, outside the walking channel.
            if(top>=e.getValue()+2&&Math.floorMod(wx*7+wz*11,5)<=2){
                var above=bp.get(wx,top+1,wz);
                if(above==null||above.isAir()){
                    put(bp,wx,top+1,wz,Math.floorMod(wx+wz,3)==0?"bobin_craft:sand_bags":"bobin_craft:burlap_sandbags");
                }
            }
        }
        for(var e:floors.entrySet()){
            int x=ZonePlan.x(e.getKey()),z=ZonePlan.z(e.getKey()),f=e.getValue();
            for(int y=Math.min(t.height(x,z)-2,f-2);y<f;y++)put(bp,x,y,z,"dirt");put(bp,x,f,z,"bobin_craft:trampled_mud");
            for(int y=f+1;y<=Math.max(f+3,t.height(x,z)+2);y++)put(bp,x,y,z,"air");
        }
        for(var e:floors.entrySet()){
            int x=ZonePlan.x(e.getKey()),z=ZonePlan.z(e.getKey()),f=e.getValue();
            for(var d:net.minecraft.util.math.Direction.Type.HORIZONTAL){Integer h=floors.get(ZonePlan.key(x+d.getOffsetX(),z+d.getOffsetZ()));
                if(h!=null&&h==f+1){if(bp.inBounds(x,f+1,z))bp.set(x,f+1,z,BpSpec.of("spruce_stairs").with("facing",d.asString()));break;}}
        }
    }
    static void dugout(WarzoneTerrain t,Blueprint bp,int x,int z,int y,boolean entrySouth){
        int floor=y-3;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            for(int h=Math.min(floor-2,t.height(x+dx,z+dz)-2);h<=floor;h++){
                String fMat=(h==floor&&Math.floorMod(dx+dz,3)==0)?"bobin_craft:damaged_wood_panel":"spruce_planks";
                put(bp,x+dx,h,z+dz,fMat);
            }
            boolean wall=Math.abs(dx)==3||Math.abs(dz)==3;
            boolean pillar=(Math.abs(dx)==3&&Math.abs(dz)==3)||(wall&&(dx==0||dz==0));
            for(int h=floor+1;h<y;h++){
                if(wall){
                    boolean slit=(dz==(entrySouth?-3:3)&&dx==0&&h==floor+2);
                    String wMat=slit?"iron_bars":(pillar?"stripped_spruce_log":
                        (Math.floorMod(dx+dz+h,3)==0?"bobin_craft:damaged_wood_panel":"spruce_planks"));
                    put(bp,x+dx,h,z+dz,wMat);
                }else{
                    put(bp,x+dx,h,z+dz,"air");
                }
            }
            // Heavy timber roof logs (накат брёвен) topped by a sloped earthen mound (земляная обсыпка).
            if(bp.inBounds(x+dx,y,z+dz)){
                bp.set(x+dx,y,z+dz,wall?BpSpec.of("bobin_craft:dark_forest_soil"):BpSpec.of("stripped_spruce_log").with("axis","x"));
            }
            put(bp,x+dx,y+1,z+dz,(Math.abs(dx)<=1&&Math.abs(dz)<=1)?"bobin_craft:mossy_forest_soil":"bobin_craft:dark_forest_soil");
            if(Math.abs(dx)<=2&&Math.abs(dz)<=2){
                put(bp,x+dx,y+2,z+dz,(Math.abs(dx)<=1&&Math.abs(dz)<=1)?"bobin_craft:mossy_forest_soil":"bobin_craft:dark_forest_soil");
            }
        }
        if(bp.inBounds(x,y+3,z))bp.set(x,y+3,z,BpSpec.of("bobin_craft:dry_undergrowth"),Blueprint.Mode.IF_AIR);
        int sign=entrySouth?1:-1;
        Map<Long,Integer> entry=new LinkedHashMap<>();
        for(int step=3;step<=9;step++)for(int dx=-1;dx<=1;dx++)entry.put(ZonePlan.key(x+dx,z+sign*step),Math.min(t.height(x+dx,z+sign*step),floor+Math.max(0,step-5)));
        excavate(t,bp,entry);
        // Framed timber portal at the dugout entrance.
        for(int side:new int[]{-2,2}){
            for(int h=floor+1;h<=y;h++)put(bp,x+side,h,z+sign*3,"stripped_spruce_log");
            int rimY=t.height(x+side,z+sign*4);
            put(bp,x+side,rimY+1,z+sign*4,"bobin_craft:burlap_sandbags");
        }
        for(int dx=-1;dx<=1;dx++){
            if(bp.inBounds(x+dx,y,z+sign*3))bp.set(x+dx,y,z+sign*3,BpSpec.of("stripped_spruce_log").with("axis","x"));
        }
        put(bp,x-2,floor+1,z-1,"bobin_craft:field_table");
        put(bp,x-2,floor+2,z-1,"bobin_craft:radio");
        put(bp,x-2,floor+1,z+1,"bobin_craft:ammo_crate");
        put(bp,x+2,floor+1,z+1,"bobin_craft:field_bench");
        if(bp.inBounds(x,y-1,z))bp.set(x,y-1,z,BpSpec.of("lantern").with("hanging",true));
        if(bp.inBounds(x+2,floor+1,z-2))bp.addBlockEntity(new Blueprint.BlockEntity(x+2,floor+1,z-2,Blueprint.Kind.BARREL,"up","bobin_craft:chests/warzone_supply",WarzoneNoise.hash(t.seed,x,z),false,false),BpSpec.of("barrel").with("facing","up"));
    }
    static void connector(WarzoneTerrain t,Blueprint bp,int x0,int x1,int z,int floor){
        Map<Long,Integer> path=new LinkedHashMap<>();for(int x=x0;x<=x1;x++)for(int dz=-1;dz<=1;dz++)path.put(ZonePlan.key(x,z+dz),floor);
        excavate(t,bp,path);
    }
    static void net(Blueprint bp,int x,int y,int z,int hw,int hd){
        for(int dx=-hw;dx<=hw;dx++)for(int dz=-hd;dz<=hd;dz++)if(bp.inBounds(x+dx,y,z+dz))bp.set(x+dx,y,z+dz,BpSpec.of("bobin_craft:anti_drone_net").with("axis","y"));
    }
    private static void put(Blueprint bp,int x,int y,int z,String id){if(bp.inBounds(x,y,z))bp.set(x,y,z,BpSpec.of(id));}
}
