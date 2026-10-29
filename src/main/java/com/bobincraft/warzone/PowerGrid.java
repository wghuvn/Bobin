package com.bobincraft.warzone;

import java.util.*;
import static com.bobincraft.warzone.ZonePlan.*;

/** Abandoned three-phase power distribution following the district road network. */
public final class PowerGrid {
    public record Pole(int id,int x,int y,int z,boolean alongX,int height,boolean steel) { }
    public record Span(Pole a,Pole b) { }
    private record Cell(int x,int y,int z) { }
    private final WarzoneTerrain terrain;
    private final List<Pole> poles;
    private final List<Span> spans;
    private final Map<Long,List<Span>> corridors;
    private volatile List<Blueprint> blueprints;
    private final Map<Long,List<CableGeometry.Segment>> physicalCables;

    PowerGrid(WarzoneTerrain terrain,ZonePlan plan){
        this.terrain=terrain;List<Pole> posts=new ArrayList<>();List<Span> links=new ArrayList<>();
        for(Road road:plan.roads())if(road.paved()&&road.points().size()>20){
            Pole previous=null;List<Integer> indexes=new ArrayList<>();for(int i=0;i<road.points().size();i+=28)indexes.add(i);indexes.add(road.points().size()-1);
            for(int index:indexes){
                Point p=road.points().get(index),before=road.points().get(Math.max(0,index-6)),after=road.points().get(Math.min(road.points().size()-1,index+6));
                boolean alongX=Math.abs(after.x()-before.x())>=Math.abs(after.z()-before.z());
                int x=p.x()+(alongX?0:8),z=p.z()+(alongX?8:0);var raw=raw(x,z);
                boolean blocked=raw.height<64||raw.channel>.04||plan.crossingAt(x,z,0)!=null;
                for(Site site:plan.allSites())if(site.contains(x,z,10)){
                    for(Building b:site.buildings())if(b.contains(x,z,7))blocked=true;
                    for(Field f:site.fields())if(f.contains(x,z,4))blocked=true;
                }
                if(blocked)continue;
                Pole pole=null;for(Pole old:posts)if(Math.hypot(old.x-x,old.z-z)<12){pole=old;break;}
                if(pole==null){pole=new Pole(posts.size()+1,x,plan.surfaceHeight(x,z,raw.height),z,alongX,14,false);posts.add(pole);}
                if(previous!=null&&previous.id!=pole.id)links.add(new Span(previous,pole));previous=pole;
            }
        }
        for(Site site:plan.sites())if(site.theme()==Theme.SUBSTATION){
            Pole feed=new Pole(posts.size()+1,site.x()-7,site.y(),site.z()+16,false,14,false);posts.add(feed);
            int z=site.z()+site.radius()+5;
            Pole gate=new Pole(posts.size()+1,site.x()-7,plan.surfaceHeight(site.x()-7,z,raw(site.x()-7,z).height),z,false,14,false);posts.add(gate);links.add(new Span(feed,gate));
        }
        Set<Integer> connected=new HashSet<>();if(!posts.isEmpty())connected.add(posts.get(posts.size()-1).id);
        while(connected.size()<posts.size()){
            boolean progress=true;while(progress){progress=false;for(Span link:links)if(connected.contains(link.a.id)||connected.contains(link.b.id)){
                if(connected.add(link.a.id))progress=true;if(connected.add(link.b.id))progress=true;}}
            Pole a=null,b=null;double best=Double.POSITIVE_INFINITY;
            for(Pole from:posts)if(connected.contains(from.id))for(Pole to:posts)if(!connected.contains(to.id)){
                double d=Math.hypot(from.x-to.x,from.z-to.z);if(d<best){a=from;b=to;best=d;}}
            if(a==null)break;
            int segments=(int)Math.ceil(best/44);Pole previous=a;
            for(int i=1;i<segments;i++){
                int x=(int)Math.round(a.x+(b.x-a.x)*i/(double)segments),z=(int)Math.round(a.z+(b.z-a.z)*i/(double)segments);
                boolean blocked=false;for(Site site:plan.allSites())for(Building building:site.buildings())if(building.contains(x,z,5))blocked=true;
                if(blocked)continue;
                Pole middle=new Pole(posts.size()+1,x,Math.max(65,plan.surfaceHeight(x,z,raw(x,z).height)),z,Math.abs(b.x-a.x)>=Math.abs(b.z-a.z),18,true);posts.add(middle);
                links.add(new Span(previous,middle));connected.add(middle.id);previous=middle;
            }
            links.add(new Span(previous,b));connected.add(b.id);
        }
        List<Span> supported=new ArrayList<>();for(Span link:links){
            int count=(int)Math.ceil(Math.hypot(link.b.x-link.a.x,link.b.z-link.a.z)/72);Pole previous=link.a;
            for(int i=1;i<count;i++){int x=(int)Math.round(link.a.x+(link.b.x-link.a.x)*i/(double)count),z=(int)Math.round(link.a.z+(link.b.z-link.a.z)*i/(double)count);
                Pole middle=new Pole(posts.size()+1,x,Math.max(65,plan.surfaceHeight(x,z,raw(x,z).height)),z,link.a.alongX,18,true);posts.add(middle);supported.add(new Span(previous,middle));previous=middle;}
            supported.add(new Span(previous,link.b));
        }
        int[] heights=new int[posts.size()+1];boolean[] steel=new boolean[heights.length];for(Pole p:posts){heights[p.id]=p.height;steel[p.id]=p.steel;}
        for(Span link:supported)if(Math.hypot(link.a.x-link.b.x,link.a.z-link.b.z)>44){steel[link.a.id]=steel[link.b.id]=true;heights[link.a.id]=Math.max(18,heights[link.a.id]);heights[link.b.id]=Math.max(18,heights[link.b.id]);}
        for(Span link:supported){int steps=Math.max(Math.abs(link.a.x-link.b.x),Math.abs(link.a.z-link.b.z));double raise=0;
            for(int i=0;i<=steps;i+=2){double f=steps==0?0:i/(double)steps;int x=(int)Math.round(link.a.x+(link.b.x-link.a.x)*f),z=(int)Math.round(link.a.z+(link.b.z-link.a.z)*f);
                int safe=plan.surfaceHeight(x,z,raw(x,z).height)+8;
                for(Site site:plan.allSites())if(site.contains(x,z,3))for(Building b:site.buildings())if(b.contains(x,z,3))safe=Math.max(safe,b.y()+(b.use()==Use.UNFINISHED?22:b.use()==Use.HALL?18:15));
                double cable=(link.a.y+heights[link.a.id])*(1-f)+(link.b.y+heights[link.b.id])*f-2.2*Math.sin(Math.PI*f);raise=Math.max(raise,safe-cable);
            }
            int extra=(int)Math.ceil(raise);heights[link.a.id]+=extra;heights[link.b.id]+=extra;
        }
        Map<Integer,Pole> finalPosts=new LinkedHashMap<>();for(Pole p:posts)finalPosts.put(p.id,new Pole(p.id,p.x,p.y,p.z,p.alongX,heights[p.id],steel[p.id]||heights[p.id]>17));
        poles=List.copyOf(finalPosts.values());List<Span> finalLinks=new ArrayList<>();for(Span link:supported)finalLinks.add(new Span(finalPosts.get(link.a.id),finalPosts.get(link.b.id)));spans=List.copyOf(finalLinks);
        Map<Long,List<Span>> buckets=new HashMap<>();for(Span span:spans)for(int z=(Math.min(span.a.z,span.b.z)-18)>>4;z<=(Math.max(span.a.z,span.b.z)+18)>>4;z++)
            for(int x=(Math.min(span.a.x,span.b.x)-18)>>4;x<=(Math.max(span.a.x,span.b.x)+18)>>4;x++)buckets.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(span);
        buckets.replaceAll((k,v)->List.copyOf(v));corridors=Map.copyOf(buckets);
        Map<Long,List<CableGeometry.Segment>> wireBuckets=new HashMap<>();
        for(Span span:spans)for(int phase=-2;phase<=2;phase+=2)for(var segment:CableGeometry.conductor(span,phase)){
            int cx=(int)Math.floor((segment.a().x()+segment.b().x())*.5)>>4,cz=(int)Math.floor((segment.a().z()+segment.b().z())*.5)>>4;
            wireBuckets.computeIfAbsent(key(cx,cz),k->new ArrayList<>()).add(segment);
        }
        wireBuckets.replaceAll((k,v)->List.copyOf(v));physicalCables=Map.copyOf(wireBuckets);
    }
    private WarzoneTerrain.Sample raw(int x,int z){var s=new WarzoneTerrain.Sample();terrain.naturalLandscape().sample(x,z,s);return s;}
    public List<CableGeometry.Segment> cablesInChunk(int cx,int cz){return physicalCables.getOrDefault(key(cx,cz),List.of());}
    public List<Pole> poles(){return poles;}
    public List<Span> spans(){return spans;}
    public boolean near(int x,int z,int margin){for(Span span:corridors.getOrDefault(key(x>>4,z>>4),List.of())){
        double dx=span.b.x-span.a.x,dz=span.b.z-span.a.z,f=Math.max(0,Math.min(1,((x-span.a.x)*dx+(z-span.a.z)*dz)/Math.max(1,dx*dx+dz*dz)));
        if(Math.hypot(x-span.a.x-f*dx,z-span.a.z-f*dz)<=margin)return true;}return false;}
    void generate(ChunkWriter writer){for(Blueprint bp:blueprints())if(bp.intersectsChunk(writer.minX,writer.minZ))BlueprintBlitter.blit(bp,writer);}
    public List<Blueprint> blueprints(){if(blueprints==null)synchronized(this){if(blueprints==null)blueprints=build();}return blueprints;}
    private List<Blueprint> build(){Map<Cell,BpSpec> cells=new HashMap<>();
        for(Pole p:poles){int bottom=Math.min(-2,raw(p.x,p.z).height-p.y-2);
            for(int h=bottom;h<=p.height-2;h++)cells.put(new Cell(p.x,p.y+h,p.z),h<=1?BpSpec.of("bobin_craft:cracked_concrete"):p.steel?BpSpec.of("iron_bars"):BpSpec.of("bobin_craft:weathered_bark").with("axis","y"));
            if(p.steel)for(int h=1;h<=p.height-2;h++){cells.put(new Cell(p.x+1,p.y+h,p.z),BpSpec.of("iron_bars"));if(h%4==0)cells.put(new Cell(p.x,p.y+h,p.z+1),BpSpec.of("iron_bars"));}
            for(int offset=-3;offset<=3;offset++)cells.put(new Cell(p.x+(p.alongX?0:offset),p.y+p.height-2,p.z+(p.alongX?offset:0)),p.steel?BpSpec.of("bobin_craft:rusted_sheet_metal"):BpSpec.of("dark_oak_log").with("axis",p.alongX?"z":"x"));
            for(int phase=-2;phase<=2;phase+=2)cells.put(new Cell(p.x+(p.alongX?0:phase),p.y+p.height-1,p.z+(p.alongX?phase:0)),BpSpec.of("end_rod").with("facing","up"));
        }
        Map<Long,List<CableGeometry.Segment>> conductors=new TreeMap<>();
        for(Span span:spans)for(int phase=-2;phase<=2;phase+=2)for(var segment:CableGeometry.conductor(span,phase)){
            int cx=(int)Math.floor((segment.a().x()+segment.b().x())*.5)>>4,cz=(int)Math.floor((segment.a().z()+segment.b().z())*.5)>>4;
            conductors.computeIfAbsent(key(cx,cz),k->new ArrayList<>()).add(segment);
        }
        String[] directions={"north","east","south","west","up","down"};int[][] offsets={{0,0,-1},{1,0,0},{0,0,1},{-1,0,0},{0,1,0},{0,-1,0}};
        Map<Long,List<Cell>> buckets=new TreeMap<>();for(Cell c:cells.keySet())buckets.computeIfAbsent(key(c.x>>4,c.z>>4),k->new ArrayList<>()).add(c);
        List<Blueprint> result=new ArrayList<>();for(var entry:buckets.entrySet()){
            int lo=entry.getValue().stream().mapToInt(Cell::y).min().orElseThrow(),hi=entry.getValue().stream().mapToInt(Cell::y).max().orElseThrow();
            Blueprint bp=new Blueprint("power_grid_"+entry.getKey(),x(entry.getKey())*16,lo,z(entry.getKey())*16,16,hi-lo+1,16);
            entry.getValue().sort(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::z).thenComparingInt(Cell::x));for(Cell c:entry.getValue())bp.set(c.x,c.y,c.z,cells.get(c));
            for(Cell c:entry.getValue())if("iron_bars".equals(cells.get(c).id)){BpSpec spec=cells.get(c);for(int i=0;i<4;i++){BpSpec neighbor=cells.get(new Cell(c.x+offsets[i][0],c.y,c.z+offsets[i][2]));spec=spec.with(directions[i],neighbor!=null&&!neighbor.isAir());}bp.set(c.x,c.y,c.z,spec);}
            result.add(bp);
        }
        for(var entry:conductors.entrySet()){
            int cx=x(entry.getKey())*16,cz=z(entry.getKey())*16;
            int anchorY=(int)Math.ceil(entry.getValue().stream().mapToDouble(segment->Math.max(segment.a().y(),segment.b().y())).max().orElseThrow())+1;
            Blueprint bp=new Blueprint("cable_mesh_"+entry.getKey(),cx,anchorY,cz,16,1,16);
            bp.addCable(new Blueprint.Cable(cx+8,anchorY,cz+8,entry.getValue()));result.add(bp);
        }
        return List.copyOf(result);
    }

    private static volatile boolean substationOffline = false;
    private static final Map<String, List<String>> SUBSTATION_GRAPH = Map.of(
            "substation_01", List.of("refinery_lights", "checkpoint_lights", "industrial_equipment")
    );

    public static Map<String, List<String>> consumerGraph() {
        return SUBSTATION_GRAPH;
    }

    public static boolean isSubstationOffline() {
        return substationOffline || com.bobincraft.destruction.DestructibleStructureRegistry.isSubstationOffline();
    }

    /**
     * Event-driven update of connected power consumers when substation transitions between ONLINE and OFFLINE (Section 9).
     * Also severs/Restores physical overhead feeder cables leaving the substation gantry when the substation is destroyed!
     */
    public static void onSubstationOfflineChanged(net.minecraft.server.world.ServerWorld world, boolean offline) {
        substationOffline = offline;
        if (world == null) return;
        var gen = WarzoneDimension.generatorOf(world);
        if (gen == null) return;
        WarzoneTerrain t;
        try { t = gen.terrain(); } catch (IllegalStateException e) { return; }
        if (!t.isAuthoredZone()) return;
        ZonePlan plan = t.zonePlan();
        for (Site site : plan.sites()) {
            // Check refinery lights, checkpoint lights, and industrial equipment in loaded chunks
            int[][] checkPoints = {
                    {site.x() + 23, site.y() + 3, site.z() + 1},
                    {site.x() + 46, site.y() + 3, site.z() + 1},
                    {site.x() - 3, site.y() + 6, site.z() + 45}
            };
            for (int[] pt : checkPoints) {
                net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(pt[0], pt[1], pt[2]);
                if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                var state = world.getBlockState(pos);
                if (state.contains(net.minecraft.state.property.Properties.LIT)) {
                    world.setBlockState(pos, state.with(net.minecraft.state.property.Properties.LIT, !offline), net.minecraft.block.Block.NOTIFY_LISTENERS);
                }
            }
            if (site.theme() == Theme.SUBSTATION) {
                // Sever or restore the overhead feeder cable spans attached to the substation gantry
                int scx = site.x() >> 4;
                int scz = site.z() >> 4;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 4; dz++) {
                        int cx = scx + dx;
                        int cz = scz + dz;
                        if (!world.isChunkLoaded(cx, cz)) continue;
                        var cables = plan.power().cablesInChunk(cx, cz);
                        if (cables.isEmpty()) continue;
                        int anchorY = (int) Math.ceil(cables.stream()
                                .mapToDouble(seg -> Math.max(seg.a().y(), seg.b().y())).max().orElse(site.y() + 14)) + 1;
                        net.minecraft.util.math.BlockPos cablePos = new net.minecraft.util.math.BlockPos(cx * 16 + 8, anchorY, cz * 16 + 8);
                        if (world.getBlockEntity(cablePos) instanceof CableSpanBlockEntity cableBe) {
                            if (offline) {
                                // Filter out segments within 36 blocks of the substation gantry (severed feeder lines)
                                List<CableGeometry.Segment> surviving = new ArrayList<>();
                                for (var seg : cables) {
                                    double dA = Math.hypot(seg.a().x() - site.x(), seg.a().z() - (site.z() + 16));
                                    double dB = Math.hypot(seg.b().x() - site.x(), seg.b().z() - (site.z() + 16));
                                    if (dA > 28.0 && dB > 28.0) {
                                        surviving.add(seg);
                                    }
                                }
                                cableBe.setSegments(surviving);
                            } else {
                                cableBe.setSegments(cables);
                            }
                            world.getChunkManager().markForUpdate(cablePos);
                        }
                    }
                }
            }
        }
        // Return radio tower redstone lamp
        net.minecraft.util.math.BlockPos radioLamp = new net.minecraft.util.math.BlockPos(t.landingX() + 13, t.landingY() + 14, t.landingZ() - 12);
        if (world.isChunkLoaded(radioLamp.getX() >> 4, radioLamp.getZ() >> 4)) {
            var st = world.getBlockState(radioLamp);
            if (st.contains(net.minecraft.state.property.Properties.LIT)) {
                world.setBlockState(radioLamp, st.with(net.minecraft.state.property.Properties.LIT, !offline), net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
        }
    }
}
