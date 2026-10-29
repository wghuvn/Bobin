package com.bobincraft.warzone;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Chunk-local roads, three modern bridge designs, storm collectors and the return shelter. */
public final class ZoneInfrastructure {
    private final WarzoneTerrain terrain;
    private final ConcurrentHashMap<Integer,Blueprint> bridges=new ConcurrentHashMap<>(),drains=new ConcurrentHashMap<>();
    private volatile Blueprint extraction;
    private final DiscoveryStructures discoveries;
    public ZoneInfrastructure(WarzoneTerrain terrain){this.terrain=terrain;this.discoveries=new DiscoveryStructures(terrain);}
    void generateRoads(ChunkWriter writer){
        if(!terrain.isAuthoredZone())return;
        var plan=terrain.zonePlan();
        for(long column:plan.roadDressingChunk(writer.minX>>4,writer.minZ>>4)){
            int x=ZonePlan.x(column),z=ZonePlan.z(column);var road=plan.roadDressingAt(x,z);
            if(road==null||plan.crossingLateralAt(x,z,16)!=null)continue;
            int y=terrain.height(x,z);double patch=ZoneWeathering.patch(terrain.seed+117,x,z,5),fine=WarzoneNoise.unit(WarzoneNoise.hash(terrain.seed+613,x,z));
            double edge=road.distance()-road.width();
            // An irregular, progressively thinning verge, rather than a straight strip of coarse dirt.
            double strength=edge<=-1?1:ZonePlan.fade(edge+1.2,4.5);
            if(!road.paved()&&fine>strength*(.65+patch*.35))continue;
            if(road.paved()&&edge>0&&fine>strength*.7)continue;
            String surface;
            if(road.paved()&&road.distance()<road.width())surface=patch<.16?"bobin_craft:trampled_mud":"bobin_craft:cracked_asphalt";
            else if(road.paved())surface=patch>.64?"bobin_craft:weathered_gravel":"bobin_craft:dark_forest_soil";
            else surface=edge>-1?patch>.56?"bobin_craft:forest_litter":"bobin_craft:dark_forest_soil":patch>.7?"bobin_craft:rutted_track":"bobin_craft:trampled_mud";
            writer.set(x,y,z,BlueprintBlitter.state(BpSpec.of(surface)));
            boolean center=edge<=0;
            if(center)for(int dy=1;dy<=5;dy++)writer.set(x,y+dy,z,BlueprintBlitter.state(BpSpec.AIR));
            else if(fine<strength*.35)writer.set(x,y+1,z,BlueprintBlitter.state(BpSpec.AIR));
            if(road.paved()&&road.distance()<road.width()){
                boolean rise=false;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){var neighbor=plan.roadAt(x+d[0],z+d[1]);if(neighbor!=null&&neighbor.paved()&&terrain.height(x+d[0],z+d[1])==y+1)rise=true;}
                if(rise)writer.set(x,y+1,z,BlueprintBlitter.state(BpSpec.of("bobin_craft:weathered_asphalt_slab").with("type","bottom")));
            }
        }
    }
    void generateStructures(ChunkWriter writer){
        if(!terrain.isAuthoredZone())return;
        com.bobincraft.destruction.DestructibleStructureRegistry.ensureInitialized(terrain);
        for(var b:terrain.zonePlan().crossings()){
            int x0=Math.min(b.wx(-b.negative(),-6),b.wx(b.positive(),6)),z0=Math.min(b.wz(-b.negative(),-6),b.wz(b.positive(),6));
            int x1=Math.max(b.wx(-b.negative(),-6),b.wx(b.positive(),6)),z1=Math.max(b.wz(-b.negative(),-6),b.wz(b.positive(),6));
            if(writer.intersects(x0,z0,x1,z1)){
                BlueprintBlitter.blit(bridges.computeIfAbsent(b.id(),id->bridgeBlueprint(terrain,b)),writer);
                for(var sec:com.bobincraft.destruction.DestructibleStructureRegistry.bridgeSectionsForCrossing(b.id())){
                    var st=com.bobincraft.destruction.DestructibleStructureRegistry.stageSnapshot(sec.structureId());
                    if(st!=com.bobincraft.destruction.StructureDamageState.INTACT){
                        var secBp=com.bobincraft.destruction.StructureCollapseManager.getStageBlueprint(terrain,sec,st);
                        if(secBp!=null)BlueprintBlitter.blit(secBp,writer);
                    }
                }
            }
        }
        for(var d:terrain.zonePlan().drains()){
            int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
            for(var p:d.points()){minX=Math.min(minX,p.x()-5);minZ=Math.min(minZ,p.z()-5);maxX=Math.max(maxX,p.x()+5);maxZ=Math.max(maxZ,p.z()+5);}
            if(writer.intersects(minX,minZ,maxX,maxZ))BlueprintBlitter.blit(drains.computeIfAbsent(d.id(),id->drainBlueprint(terrain,d)),writer);
        }
        int x=terrain.landingX(),z=terrain.landingZ();
        if(writer.intersects(x-23,z-23,x+23,z+23)){
            if(extraction==null)synchronized(this){if(extraction==null)extraction=returnBlueprint(terrain);}
            BlueprintBlitter.blit(extraction,writer);
        }
        discoveries.generate(writer);
        terrain.zonePlan().power().generate(writer);
    }
    public List<Blueprint> blueprints(){List<Blueprint> result=new ArrayList<>();for(var b:terrain.zonePlan().crossings())result.add(bridgeBlueprint(terrain,b));
        for(var d:terrain.zonePlan().drains())result.add(drainBlueprint(terrain,d));result.add(returnBlueprint(terrain));result.addAll(discoveries.blueprints());result.addAll(terrain.zonePlan().power().blueprints());return List.copyOf(result);}
    private static int raw(WarzoneTerrain t,int x,int z){var s=new WarzoneTerrain.Sample();t.naturalLandscape().sample(x,z,s);return s.height;}
    private static BpSpec block(String id){return BpSpec.of(id);}
    public static Blueprint bridgeBlueprint(WarzoneTerrain t,ZonePlan.Crossing b){
        int x0=Math.min(b.wx(-b.negative(),-6),b.wx(b.positive(),6)),z0=Math.min(b.wz(-b.negative(),-6),b.wz(b.positive(),6));
        int sx=b.alongX()?b.negative()+b.positive()+1:13,sz=b.alongX()?13:b.negative()+b.positive()+1;
        Blueprint bp=new Blueprint("zone_bridge_"+b.id(),x0,40,z0,sx,b.deckY()-40+20,sz);
        String axis=b.alongX()?"x":"z";
        for(int u=-b.negative();u<=b.positive();u++){
            int y=b.floor(u);
            for(int v=-4;v<=4;v++){
                int x=b.wx(u,v),z=b.wz(u,v);
                for(int h=y+1;h<=Math.max(y+10,94)&&h<=bp.maxY();h++)bp.air(x,h,z);
                bp.set(x,y-1,z,b.style()==2?block("dark_oak_log").with("axis",axis):block("bobin_craft:cracked_concrete"));
                String deck=Math.abs(v)>=3?"bobin_craft:cracked_concrete":b.style()==2?"bobin_craft:rusted_sheet_metal":"bobin_craft:cracked_asphalt";
                bp.set(x,y,z,block(deck));
                if(Math.abs(v)==4){bp.set(x,y+1,z,block(b.style()==0?"bobin_craft:cracked_concrete":"iron_bars"));
                    if(b.style()==1&&Math.floorMod(u,8)==0)for(int h=2;h<=5;h++)bp.set(x,y+h,z,block("waxed_oxidized_copper"));}
            }
            if(Math.floorMod(u,16)==0&&u>-b.negative()+12&&u<b.positive()-12){
                for(int v:new int[]{-3,3})for(int h=raw(t,b.wx(u,v),b.wz(u,v));h<y-1;h++)bp.set(b.wx(u,v),h,b.wz(u,v),block(b.style()==2?"dark_oak_log":"polished_andesite"));
                for(int v=-4;v<=4;v++)bp.set(b.wx(u,v),y-2,b.wz(u,v),block("bobin_craft:mossy_concrete"));}
            if(b.style()==1&&u>=-b.negative()+16&&u<=b.positive()-16){
                for(int v:new int[]{-4,4}){bp.set(b.wx(u,v),y+5,b.wz(u,v),block("waxed_oxidized_copper"));
                    int diagonal=1+Math.floorMod(u,8)/2;bp.set(b.wx(u,v),y+diagonal,b.wz(u,v),block("iron_bars"));}}
        }
        // Abutments touch the actual banks. Riverbed and water remain open between the piers.
        for(int u:new int[]{-b.negative(),b.positive()})for(int v=-5;v<=5;v++){
            int x=b.wx(u,v),z=b.wz(u,v),y=b.floor(u);for(int h=raw(t,x,z)-2;h<=y;h++)bp.set(x,h,z,block("bobin_craft:cracked_concrete"));
            if(Math.abs(v)==5)for(int h=1;h<=2;h++)bp.set(x,y+h,z,block("bobin_craft:mossy_concrete"));}
        bp.connect((x,z)->raw(t,x,z));check(bp);return bp;
    }
    public static Blueprint drainBlueprint(WarzoneTerrain t,ZonePlan.Drain d){
        int x0=2048,z0=2048,x1=-2048,z1=-2048,low=256,high=0;
        for(var p:d.points()){x0=Math.min(x0,p.x()-6);z0=Math.min(z0,p.z()-6);x1=Math.max(x1,p.x()+6);z1=Math.max(z1,p.z()+6);low=Math.min(low,p.y()-2);high=Math.max(high,Math.max(p.y()+6,t.height(p.x(),p.z())+3));}
        Blueprint bp=new Blueprint("zone_storm_drain_"+d.id(),x0,low,z0,x1-x0+1,high-low+1,z1-z0+1);
        var points=d.points();int end=points.size()-1;
        if(d.riverOutfall())for(int i=1;i<points.size();i++)if(t.height(points.get(i).x(),points.get(i).z())<WarzoneTerrain.SEA_LEVEL){end=i-1;break;}
        // Keep the human-sized collector underground. Its last section reduces to a small service pipe.
        for(int i=0;i<=end;i++){
            var p=points.get(i);boolean narrow=i>end-12;int radius=narrow?1:2,roof=narrow?2:4;
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)for(int dy=0;dy<=roof;dy++){
                int x=p.x()+dx,z=p.z()+dz,y=p.y()+dy;
                if(y>t.height(x,z))continue;
                bp.set(x,y,z,block((dx+dz)%5==0?"mossy_stone_bricks":"bobin_craft:cracked_concrete"));
            }
        }
        for(int i=0;i<=end;i++){
            var p=points.get(i);boolean narrow=i>end-12;int radius=narrow?0:1,roof=narrow?1:3;
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)for(int dy=1;dy<=roof;dy++){
                int x=p.x()+dx,z=p.z()+dz,y=p.y()+dy;if(y>=t.height(x,z))continue;
                bp.set(x,y,z,dy==1?block("water"):BpSpec.AIR);
            }
        }
        // Descending neighboring bores overlap. Restore their center water after all air cuts.
        for(int i=0;i<=end;i++){var p=points.get(i);if(p.y()+1<t.height(p.x(),p.z()))bp.set(p.x(),p.y()+1,p.z(),block("water"));}
        var first=points.get(0);int top=t.height(first.x(),first.z());
        var exit=points.get(Math.min(4,end));int ex=Integer.signum(exit.x()-first.x()),ez=Integer.signum(exit.z()-first.z());
        if(Math.abs(exit.x()-first.x())>=Math.abs(exit.z()-first.z()))ez=0;else ex=0;
        if(ex==0&&ez==0)ez=1;
        String ladderFace=ex>0?"east":ex<0?"west":ez>0?"south":"north";
        for(int y=first.y()+1;y<top;y++){
            // The low shaft is part of the walkable collector. Only the ladder backing may close it.
            for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                if(y>=first.y()+4||(offset[0]==-ex&&offset[1]==-ez))
                    bp.set(first.x()+offset[0],y,first.z()+offset[1],block("bobin_craft:cracked_concrete"));
            bp.set(first.x(),y,first.z(),block("ladder").with("facing",ladderFace));
        }
        // A three-block landing gives a climber room to turn before entering the main bore.
        for(int step=1;step<=3;step++)for(int side=-1;side<=1;side++)for(int h=1;h<=3;h++){
            int x=first.x()+ex*step+ez*side,z=first.z()+ez*step+ex*side;
            if(first.y()+h<t.height(x,z))bp.set(x,first.y()+h,z,h==1?block("water"):BpSpec.AIR);
        }
        bp.set(first.x(),top,first.z(),block("bobin_craft:manhole_cover").with("facing","east"));
        // Clear the lid and its sliding landing; adjacent surface materials remain the original ground.
        for(int dx=0;dx<=1;dx++)for(int dy=1;dy<=2;dy++)bp.air(first.x()+dx,top+dy,first.z());
        var mouth=points.get(end);var previous=points.get(Math.max(0,end-1));
        int dx=Integer.signum(mouth.x()-previous.x()),dz=Integer.signum(mouth.z()-previous.z());
        String facing=Math.abs(dx)>0?(dx>0?"east":"west"):(dz>0?"south":"north");
        int mouthY=d.riverOutfall()?WarzoneTerrain.SEA_LEVEL+1:Math.min(t.height(mouth.x(),mouth.z()),mouth.y()+2);
        // A half-metre dark pipe end at the bank: no headwall, concrete basin or excavated river square.
        for(int h=mouth.y()+2;h<mouthY;h++)bp.set(mouth.x(),h,mouth.z(),block("bobin_craft:utility_pipe").with("up",true).with("down",true));
        bp.set(mouth.x(),mouthY,mouth.z(),block("bobin_craft:drain_outlet").with("facing",facing));
        if(!d.riverOutfall()){
            for(int ox=-2;ox<=2;ox++)for(int oz=-2;oz<=2;oz++)if(ox*ox+oz*oz<5){int g=t.height(mouth.x()+ox,mouth.z()+oz);
                bp.set(mouth.x()+ox,g,mouth.z()+oz,block("bobin_craft:trampled_mud"));}
        }
        bp.connect((x,z)->t.height(x,z));check(bp);return bp;
    }
    public static Blueprint returnBlueprint(WarzoneTerrain t){
        int cx=t.landingX(),cz=t.landingZ(),g=t.landingY();Blueprint bp=new Blueprint("zone_return_radio",cx-23,g-8,cz-23,47,40,47);
        for(int x=-22;x<=22;x++)for(int z=-22;z<=22;z++){
            double distance=Math.hypot(x,z),wear=ZoneWeathering.patch(t.seed+901,cx+x,cz+z,6);if(distance>17+wear*5)continue;
            int ground=t.height(cx+x,cz+z);for(int y=ground+1;y<=ground+27&&y<=bp.maxY();y++)bp.air(cx+x,y,cz+z);
            boolean letter=(Math.abs(x)==3&&Math.abs(z)<=4)||(z==0&&Math.abs(x)<=3);
            bp.set(cx+x,ground,cz+z,block(letter?"yellow_terracotta":wear>.63?"coarse_dirt":"bobin_craft:weathered_gravel"));
        }
        for(int x=-7;x<=7;x++)for(int z=-18;z<=-9;z++){
            bp.set(cx+x,g,cz+z,block("spruce_planks"));bp.set(cx+x,g+5,cz+z,block("bobin_craft:tarp_olive"));
            if(Math.abs(x)==7&&(z==-18||z==-9))for(int y=1;y<=4;y++)bp.set(cx+x,g+y,cz+z,block("spruce_log"));
            if(z==-18&&Math.abs(x)>1)for(int y=1;y<=3;y++)bp.set(cx+x,g+y,cz+z,block("bobin_craft:rusted_sheet_metal"));}
        bp.set(cx,g+1,cz-15,block("bobin_craft:radio").with("facing","south"));
        for(int x:new int[]{-4,4}){bp.set(cx+x,g+1,cz-14,block("bobin_craft:supply_crate"));bp.set(cx+x,g+2,cz-14,block("bobin_craft:supply_crate"));}
        for(int y=1;y<=13;y++)bp.set(cx+13,g+y,cz-12,block("iron_bars"));
        for(int x=10;x<=16;x++)bp.set(cx+x,g+11,cz-12,block("iron_bars"));
        bp.set(cx+13,g+14,cz-12,block("redstone_lamp").with("lit",true));
        for(int side:new int[]{-1,1})for(int x=9;x<=15;x++)for(int y=1;y<=2;y++)bp.set(cx+side*x,g+y,cz+10,block("bobin_craft:sand_bags"));
        bp.addBlockEntity(new Blueprint.BlockEntity(cx-10,g+1,cz-3,Blueprint.Kind.CAMPFIRE,null,null,0,true,true),block("campfire").with("lit",true).with("signal_fire",true));
        bp.connect((x,z)->t.height(x,z));check(bp);return bp;
    }
    private static void check(Blueprint bp){if(bp.droppedWrites!=0)throw new IllegalStateException("Infrastructure exceeds bounds: "+bp.name+": "+bp.droppedWrites);}
}
