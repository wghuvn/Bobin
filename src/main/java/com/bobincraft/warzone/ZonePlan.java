package com.bobincraft.warzone;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable, baked geography for the one curated exclusion zone. Runtime never runs the layout search. */
public final class ZonePlan {
    public enum Theme { ALDER, BRICK, PINE, FARM, SAWMILL, CONCRETE, ESTATE, WATERWORKS, SHACKS, REFINERY, SUBSTATION }
    public enum Use { HOUSE, SHOP, CLINIC, BARN, WORKSHOP, HALL, OFFICE, UNFINISHED, PUMP, SHACK }
    public record Point(int x,int y,int z) { }
    public record Building(int id,Use use,int x,int z,int y,int hw,int hd,int rotation,int variant) {
        public int wx(int u,int v){return x+switch(rotation&3){case 1->-v;case 2->-u;case 3->v;default->u;};}
        public int wz(int u,int v){return z+switch(rotation&3){case 1->u;case 2->-v;case 3->-u;default->v;};}
        public int u(int px,int pz){int a=px-x,b=pz-z;return switch(rotation&3){case 1->b;case 2->-a;case 3->-b;default->a;};}
        public int v(int px,int pz){int a=px-x,b=pz-z;return switch(rotation&3){case 1->-a;case 2->-b;case 3->a;default->b;};}
        public Point door(){return new Point(wx(0,-hd-2),y+1,wz(0,-hd-2));}
        public boolean contains(int px,int pz,int margin){return Math.abs(u(px,pz))<=hw+margin&&Math.abs(v(px,pz))<=hd+margin;}
        public double distance(int px,int pz){return Math.hypot(Math.max(0,Math.abs(u(px,pz))-hw-2),Math.max(0,Math.abs(v(px,pz))-hd-2));}
        public double weight(int px,int pz){return fade(distance(px,pz),10);}
    }
    public record Field(int x,int z,int hw,int hd,int y,int crop) {
        public boolean contains(int px,int pz,int margin){return Math.abs(px-x)<=hw+margin&&Math.abs(pz-z)<=hd+margin;}
        public double distance(int px,int pz){return Math.hypot(Math.max(0,Math.abs(px-x)-hw-1),Math.max(0,Math.abs(pz-z)-hd-1));}
    }
    public record Site(int id,String name,Theme theme,int x,int z,int y,int radius,List<Building> buildings,List<Field> fields,Point entrance) {
        public Site { buildings=List.copyOf(buildings);fields=List.copyOf(fields); }
        public boolean contains(int px,int pz,int margin){return Math.abs(px-x)<=radius+margin&&Math.abs(pz-z)<=radius+margin;}
    }
    public record Pad(int x,int z,int hw,int hd,int y) {
        public double distance(int px,int pz){return Math.hypot(Math.max(0,Math.abs(px-x)-hw),Math.max(0,Math.abs(pz-z)-hd));}
    }
    public record Road(int id,String name,boolean paved,List<Point> points,int width) {
        public Road { points=List.copyOf(points); }
        public int halfWidth(){return width>0?width:paved?4:2;}
    }
    public record RoadCell(int y,boolean paved,int distance,int width) { }
    public record Drain(int id,String name,List<Point> points,boolean riverOutfall){public Drain{points=List.copyOf(points);}}
    public record Crossing(int id,int x,int z,boolean alongX,int negative,int positive,int deckY,int leftY,int rightY,int style) {
        public int wx(int u,int v){return x+(alongX?u:v);}
        public int wz(int u,int v){return z+(alongX?v:u);}
        public int x(int u,int v){return wx(u,v);}
        public int z(int u,int v){return wz(u,v);}
        public int u(int px,int pz){return alongX?px-x:pz-z;}
        public int floor(int u){int ramp=24;
            if(u<-negative+ramp)return leftY+(int)Math.round((deckY-leftY)*(u+negative)/(double)ramp);
            if(u>positive-ramp)return rightY+(int)Math.round((deckY-rightY)*(positive-u)/(double)ramp);return deckY;}
        public boolean contains(int px,int pz,int margin){int u=u(px,pz),v=alongX?pz-z:px-x;
            return u>=-negative-margin&&u<=positive+margin&&Math.abs(v)<=4+margin;}
        public boolean containsLateralCorridor(int px,int pz,int lateralMargin){int u=u(px,pz),v=alongX?pz-z:px-x;
            return u>=-negative&&u<=positive&&Math.abs(v)<=4+lateralMargin;}
    }
    record Layout(int format,long seed,int size,int generationVersion,List<Site> sites,List<Site> outposts,List<Crossing> crossings,List<Road> roads,List<Drain> drains) { }
    private final WarzoneTerrain terrain;
    private final List<Site> sites,outposts,allSites;
    private final List<Crossing> crossings;
    private final List<Road> roads;
    private final List<Drain> drains;
    private final ZoneGeography geography;
    private final Map<Long,RoadCell> roadCells,verges;
    private final Map<Long,List<Long>> roadChunks,roadDressingChunks;
    private final ResidentialPlan residents;
    private final PowerGrid power;
    private final ZoneDiscoveries discoveries;

    public ZonePlan(WarzoneTerrain terrain){this(terrain,readLayout(terrain));}
    private ZonePlan(WarzoneTerrain terrain,Layout layout){
        this.terrain=terrain;sites=List.copyOf(layout.sites);outposts=layout.outposts==null?List.of():List.copyOf(layout.outposts);
        List<Site> areas=new ArrayList<>(sites);areas.addAll(outposts);allSites=List.copyOf(areas);
        crossings=List.copyOf(layout.crossings);roads=List.copyOf(layout.roads);drains=List.copyOf(layout.drains);
        geography=new ZoneGeography(terrain,allSites);
        Map<Long,RoadCell> cells=new LinkedHashMap<>(),edges=new HashMap<>();
        for(Road road:roads)for(Point p:road.points)for(int dz=-16;dz<=16;dz++)for(int dx=-16;dx<=16;dx++){
            int square=dx*dx+dz*dz;if(square>256)continue;int d=(int)Math.ceil(Math.sqrt(square));
            int x=p.x+dx,z=p.z+dz;long key=key(x,z);
            RoadCell edge=edges.get(key),old=cells.get(key);int width=road.halfWidth();
            boolean changeEdge=edge==null||d-width<edge.distance-edge.width||d==edge.distance&&road.paved&&!edge.paved;
            boolean changeRoad=d<=width&&(old==null||d<old.distance||d==old.distance&&road.paved&&!old.paved);
            if(!changeEdge&&!changeRoad)continue;
            int y=p.y-1;
            Crossing bridge=crossingAt(x,z,0);if(bridge!=null)y=bridge.floor(bridge.u(x,z));
            RoadCell candidate=new RoadCell(y,road.paved,d,width);
            if(changeEdge)edges.put(key,candidate);
            if(changeRoad)cells.put(key,candidate);
        }
        joinCarriageways(cells,edges);
        // Packed coordinate Longs have many equal hash codes. Map.copyOf uses linear probing,
        // which makes a large coordinate map quadratic to construct. Keep HashMap's tree bins.
        roadCells=Collections.unmodifiableMap(cells);verges=Collections.unmodifiableMap(edges);
        Map<Long,List<Long>> buckets=new HashMap<>();for(long k:cells.keySet())buckets.computeIfAbsent(key(x(k)>>4,z(k)>>4),a->new ArrayList<>()).add(k);
        buckets.replaceAll((k,v)->List.copyOf(v));roadChunks=Map.copyOf(buckets);
        Map<Long,List<Long>> dressing=new HashMap<>();for(var entry:edges.entrySet())if(entry.getValue().distance<=entry.getValue().width+3){long k=entry.getKey();dressing.computeIfAbsent(key(x(k)>>4,z(k)>>4),a->new ArrayList<>()).add(k);}
        dressing.replaceAll((k,v)->{v.sort(Long::compare);return List.copyOf(v);});roadDressingChunks=Map.copyOf(dressing);
        discoveries=new ZoneDiscoveries(terrain,this);power=new PowerGrid(terrain,this);residents=makeResidents();
    }
    /** Centerline grades meet across the full carriageway, including the inside of angled junctions. */
    private void joinCarriageways(Map<Long,RoadCell> cells,Map<Long,RoadCell> edges){
        ArrayDeque<Long> queue=new ArrayDeque<>(cells.keySet());
        while(!queue.isEmpty()){
            long key=queue.remove();RoadCell at=cells.get(key);if(!at.paved)continue;int x=x(key),z=z(key);
            for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                long next=key(x+offset[0],z+offset[1]);RoadCell other=cells.get(next);if(other==null||!other.paved||Math.abs(at.y-other.y)<=1)continue;
                long high=at.y>other.y?key:next,low=at.y>other.y?next:key;RoadCell hi=cells.get(high),lo=cells.get(low);
                boolean anchored=crossingAt(x(high),z(high),0)!=null;
                long changed=anchored?low:high;RoadCell old=cells.get(changed);
                RoadCell replacement=new RoadCell(anchored?hi.y-1:lo.y+1,old.paved,old.distance,old.width);
                cells.put(changed,replacement);RoadCell edge=edges.get(changed);if(edge!=null&&edge.distance<=replacement.distance)edges.put(changed,replacement);
                queue.add(changed);for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){long near=key(x(changed)+d[0],z(changed)+d[1]);if(cells.containsKey(near))queue.add(near);}
                at=cells.get(key);
            }
        }
    }
    /** Offline source preparation. Only this explicit path runs the deterministic road/utility search. */
    public static ZonePlan recompute(WarzoneTerrain terrain){return new ZonePlan(terrain,ZonePlanner.build(terrain));}
    static Layout readLegacy(){return read("/data/bobin_craft/warzone/black_alder_layout.json");}
    private static Layout readLayout(WarzoneTerrain terrain){
        Layout layout=read("/data/bobin_craft/warzone/black_alder_layout"+(terrain.generationVersion()>=10?"_v10":terrain.generationVersion()>=9?"_v9":terrain.generationVersion()>=8?"_v8":terrain.generationVersion()>=7?"_v7":"")+".json");
        int count=terrain.generationVersion()>=7?11:9;
        if(layout.seed!=terrain.seed||layout.size!=terrain.size||layout.generationVersion!=terrain.generationVersion()||layout.sites.size()!=count
                ||layout.crossings.size()!=3||layout.roads.isEmpty()||layout.drains.size()!=count)throw new IllegalStateException("Fixed zone layout does not match the terrain profile");
        return layout;
    }
    private static Layout read(String path){
        try(var stream=ZonePlan.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing fixed zone layout: "+path);
            return new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Layout.class);
        }catch(java.io.IOException error){throw new IllegalStateException("Unable to read zone layout",error);}
    }
    public List<Site> sites(){return sites;}
    public List<Site> outposts(){return outposts;}
    public List<Site> allSites(){return allSites;}
    public List<Crossing> crossings(){return crossings;}
    public List<Road> roads(){return roads;}
    public List<Drain> drains(){return drains;}
    public ResidentialPlan residents(){return residents;}
    public PowerGrid power(){return power;}
    public ZoneDiscoveries discoveries(){return discoveries;}
    public List<Pad> propPads(Site site){return geography.pads(site);}
    public RoadCell roadAt(int x,int z){return roadCells.get(key(x,z));}
    public List<Long> roadChunk(int cx,int cz){return roadChunks.getOrDefault(key(cx,cz),List.of());}
    public RoadCell roadDressingAt(int x,int z){return verges.get(key(x,z));}
    public List<Long> roadDressingChunk(int x,int z){return roadDressingChunks.getOrDefault(key(x,z),List.of());}
    public static long key(int x,int z){return (long)x<<32|(z&0xffffffffL);}
    public static int x(long k){return (int)(k>>32);}
    public static int z(long k){return (int)k;}
    public Crossing crossingAt(int x,int z,int margin){for(Crossing b:crossings)if(b.contains(x,z,margin))return b;return null;}
    public Crossing crossingLateralAt(int x,int z,int lateralMargin){for(Crossing b:crossings)if(b.containsLateralCorridor(x,z,lateralMargin))return b;return null;}
    static double fade(double distance,double width){double f=Math.max(0,Math.min(1,distance/width));return 1-f*f*(3-2*f);}
    public static boolean industrial(Theme theme){return switch(theme){case SAWMILL,CONCRETE,ESTATE,WATERWORKS,REFINERY,SUBSTATION->true;default->false;};}
    public static Point parking(Site s){
        if(s.id>=100||s.theme==Theme.SHACKS)return null;
        int dx=switch(s.theme){case ALDER->47;case BRICK->48;case PINE->36;case FARM->52;case SAWMILL->42;case CONCRETE->49;case ESTATE->38;case WATERWORKS->39;case REFINERY->51;case SUBSTATION->-35;default->0;};
        return new Point(s.x+dx,s.y+1,s.z+(s.theme==Theme.SUBSTATION?30:48));
    }
    public double yardDistance(Site site,int x,int z){return ZoneGeography.yardDistance(site,x,z);}
    public int padHeight(int x,int z,int natural){return geography.height(x,z,natural);}
    public int surfaceHeight(int x,int z,int natural){
        int base=padHeight(x,z,natural);if(discoveries!=null)base=discoveries.height(x,z,base);
        // Bug 6.1 Fix: Do not raise terrain walls alongside bridge spans (|v| <= 20, -negative <= u <= positive)
        if(crossingLateralAt(x,z,16)!=null)return base;
        if(geography.hardFloor(x,z))return base;
        RoadCell road=roadAt(x,z);if(road!=null)return road.y;
        RoadCell edge=verges.get(key(x,z));if(edge!=null)return (int)Math.round(base+(edge.y-base)*fade(edge.distance-edge.width,12));
        return base;
    }
    public boolean reserved(int x,int z){return protectedWithin(x,z,0);}
    /** Whole-plant exclusion. The renderer never cuts individual crown blocks at clearing boundaries. */
    public boolean protectedWithin(int x,int z,int reach){
        RoadCell edge=verges.get(key(x,z));if(edge!=null&&edge.distance<=edge.width+reach)return true;
        if(crossingAt(x,z,reach+3)!=null||geography.protectedWithin(x,z,reach))return true;
        for(Site site:sites)if(OccupationPlan.near(site,x,z,reach))return true;
        return discoveries!=null&&discoveries.near(x,z,reach)||power!=null&&power.near(x,z,reach+5)||Math.hypot(x-terrain.landingX(),z-terrain.landingZ())<27+reach;
    }
    private ResidentialPlan makeResidents(){List<ResidentialPlan.Site> list=new ArrayList<>();
        for(Site s:sites){List<ResidentialPlan.Plot> plots=new ArrayList<>();
            for(Building b:s.buildings)plots.add(new ResidentialPlan.Plot(b.id,b.x,b.z,b.y,b.hw,b.hd,new String[]{"north","east","south","west"}[b.rotation],b.variant));
            List<ResidentialPlan.Point> lane=new ArrayList<>();
            for(int z=s.z-12;z<=s.entrance.z;z++){int y=surfaceHeight(s.x,z,geography.rawHeight(s.x,z))+1;lane.add(new ResidentialPlan.Point(s.x,y,z));}
            List<ResidentialPlan.Post> posts=new ArrayList<>();
            int[] indexes={lane.size()-8,lane.size()/2,8};var duties=ResidentialPlan.Duty.values();
            for(int i=0;i<3;i++)posts.add(new ResidentialPlan.Post(i,new ResidentialPlan.Duty[]{ResidentialPlan.Duty.SENTRY,ResidentialPlan.Duty.PATROL,ResidentialPlan.Duty.REST}[i],lane.get(indexes[i]),lane.get(lane.size()-1),List.of(lane.get(8),lane.get(lane.size()/2),lane.get(lane.size()-8))));
            if(terrain.generationVersion()>=10){
                for(int i=0;i<2;i++){int offset=i==0?-7:17,z=s.entrance.z-2;List<ResidentialPlan.Point> access=new ArrayList<>();
                    for(int step=0;step<=Math.abs(offset);step++){int x=s.x+step*Integer.signum(offset);access.add(new ResidentialPlan.Point(x,surfaceHeight(x,z,geography.rawHeight(x,z))+1,z));}
                    var spawn=access.get(access.size()-1);
                    posts.add(new ResidentialPlan.Post(3+i,ResidentialPlan.Duty.SENTRY,spawn,lane.get(lane.size()-1),access));}
            }
            ResidentialPlan.Kind kind=s.theme==Theme.SHACKS?ResidentialPlan.Kind.HOMESTEAD:s.theme==Theme.ESTATE?ResidentialPlan.Kind.DERELICT:ResidentialPlan.Kind.HAMLET;
            list.add(new ResidentialPlan.Site(s.id,kind,ResidentialPlan.Group.values()[(s.id-1)%3],s.x,s.z,s.radius,s.y,plots,lane,posts,s.name));
        }return ResidentialPlan.fromSites(list);
    }
}
