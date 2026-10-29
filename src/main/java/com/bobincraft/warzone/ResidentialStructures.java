package com.bobincraft.warzone;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Authored, furnished structures for the fixed exclusion zone. No legacy structure templates. */
public final class ResidentialStructures {
    private final WarzoneTerrain terrain;
    private final ConcurrentHashMap<Integer,Blueprint> cache=new ConcurrentHashMap<>();
    public ResidentialStructures(WarzoneTerrain terrain,ResidentialPlan ignored){this.terrain=terrain;}
    public Blueprint siteBlueprint(ZonePlan.Site site){return cache.computeIfAbsent(site.id(),id->new Builder(terrain,site).build());}
    public Blueprint siteBlueprint(ResidentialPlan.Site site){return siteBlueprint(terrain.zonePlan().allSites().stream().filter(s->s.id()==site.id()).findFirst().orElseThrow());}
    public List<Blueprint> blueprints(){return terrain.zonePlan().allSites().stream().map(this::siteBlueprint).toList();}
    void generate(ChunkWriter w){if(!terrain.isAuthoredZone())return;for(var site:terrain.zonePlan().allSites())
        if(w.intersects(site.x()-site.radius(),site.z()-site.radius(),site.x()+site.radius(),site.z()+site.radius()))BlueprintBlitter.blit(siteBlueprint(site),w);}
    public static int[] world(ResidentialPlan.Plot p,int u,int v){return switch(p.facing()){
        case "east"->new int[]{p.x()-v,p.z()+u};case "south"->new int[]{p.x()-u,p.z()-v};case "west"->new int[]{p.x()+v,p.z()-u};default->new int[]{p.x()+u,p.z()+v};};}

    private static final class Builder {
        private final WarzoneTerrain t;private final ZonePlan.Site s;private final Blueprint bp;
        private final BpSpec concrete=BpSpec.of("bobin_craft:cracked_concrete"),rust=BpSpec.of("bobin_craft:rusted_sheet_metal");
        Builder(WarzoneTerrain t,ZonePlan.Site s){this.t=t;this.s=s;int low=s.y(),high=s.y();
            for(var b:s.buildings()){low=Math.min(low,b.y());high=Math.max(high,b.y());}
            for(var f:s.fields()){low=Math.min(low,f.y());high=Math.max(high,f.y());}
            bp=new Blueprint("zone_"+s.id()+"_"+s.theme().name().toLowerCase(),s.x()-s.radius(),low-14,s.z()-s.radius(),s.radius()*2+1,high-low+51,s.radius()*2+1);}
        Blueprint build(){
            if(industrial())yard();
            for(var b:s.buildings()){foundation(b);switch(b.use()){
                case UNFINISHED->unfinished(b);case HALL->hall(b);case BARN->barn(b);case SHACK->shack(b);default->house(b);}}
            for(var f:s.fields())field(f);
            landmarks();
            if(s.id()<100)serviceDetails();
            backyards();
            new ZoneExpansionStructures(t,s,bp).build();
            new OccupationDressing(t,s,bp).build();
            // Match all task points to the same traversable street surface used by guards and players.
            for(var resident:t.residentialPlan().sites())if(resident.id()==s.id())for(var post:resident.posts()){
                stand(post.spawn());for(var p:post.route())stand(p);}
            bp.connect((x,z)->t.height(x,z));
            if(bp.droppedWrites!=0)throw new IllegalStateException("Authored geometry exceeds area "+s.name()+": "+bp.droppedWrites);
            return bp;
        }
        private boolean industrial(){return ZonePlan.industrial(s.theme());}
        private long hash(int x,int z){return WarzoneNoise.hash(t.seed+s.id()*197L,x,z);}
        private void put(int x,int y,int z,String id){bp.set(x,y,z,BpSpec.of(id));}
        private void set(ZonePlan.Building b,int u,int y,int v,BpSpec spec){bp.set(b.wx(u,v),b.y()+y,b.wz(u,v),rotate(spec,b.rotation()));}
        private void set(ZonePlan.Building b,int u,int y,int v,String id){set(b,u,y,v,BpSpec.of(id));}
        private BpSpec rotate(BpSpec spec,int rotation){String f=spec.prop("facing");if(f==null)return spec;
            List<String> dirs=List.of("north","east","south","west");int index=dirs.indexOf(f);return index<0?spec:spec.with("facing",dirs.get((index+rotation)%4));}
        private String windowBlock(ZonePlan.Building b,int u,int y,int v,boolean service){
            int h=Math.floorMod((int)hash(b.wx(u,v)+y*17,b.wz(u,v)+b.variant()*11),12);
            return switch(h){
                case 0->"bobin_craft:damaged_wood_panel";
                case 1->"spruce_planks";
                case 2,3->"bobin_craft:old_window_frame";
                case 4,5,6->"bobin_craft:broken_window_glass";
                case 7->service?"tinted_glass":"gray_stained_glass_pane";
                case 8,9->"gray_stained_glass_pane";
                default->"brown_stained_glass_pane";
            };
        }
        private void yard(){
            int r=s.radius()-5;
            for(int z=s.z()-r;z<=s.z()+r;z++)for(int x=s.x()-r;x<=s.x()+r;x++){
                double edge=t.zonePlan().yardDistance(s,x,z),wear=ZoneWeathering.patch(t.seed+s.id(),x,z,9);
                if(edge>0||t.zonePlan().roadAt(x,z)!=null)continue;
                int y=t.height(x,z);
                String surface=s.theme()==ZonePlan.Theme.SUBSTATION?wear>.75?"bobin_craft:dark_forest_soil":"bobin_craft:weathered_gravel":s.theme()==ZonePlan.Theme.SAWMILL?wear>.58?"podzol":"coarse_dirt":
                    wear>.69?"bobin_craft:mossy_concrete":wear<.24?"coarse_dirt":"bobin_craft:cracked_concrete";
                if(ZoneWeathering.patch(t.seed+701,x,z,5)>.73&&wear>.43)surface="bobin_craft:trampled_mud";
                put(x,y,z,surface);
                if((wear>.69||edge>-6)&&Math.floorMod(hash(x,z),9)==0)bp.set(x,y+1,z,BpSpec.of("bobin_craft:dry_undergrowth"),Blueprint.Mode.IF_AIR);
            }
            if(s.theme()!=ZonePlan.Theme.SUBSTATION){
                for(int side:new int[]{-1,1})for(int i=-32;i<=31;i++){
                    if(Math.floorMod(i+s.id()*3,23)<6)continue;
                    int x=s.x()+side*(s.radius()-16),z=s.z()+i,y=t.height(x,z);
                    for(int h=1;h<=2;h++)put(x,y+h,z,"iron_bars");
                    if(Math.floorMod(i,8)==0)for(int h=0;h<=3;h++)put(x,y+h,z,"bobin_craft:cracked_concrete");
                }
            }
        }
        private void foundation(ZonePlan.Building b){
            for(int v=-b.hd()-2;v<=b.hd()+2;v++)for(int u=-b.hw()-2;u<=b.hw()+2;u++){
                for(int y=-8;y<=-1;y++)set(b,u,y,v,y==-1?concrete:BpSpec.of("stone"));
                boolean inside=Math.abs(u)<=b.hw()&&Math.abs(v)<=b.hd();
                if(inside)set(b,u,0,v,b.use()==ZonePlan.Use.HALL||b.use()==ZonePlan.Use.WORKSHOP||b.use()==ZonePlan.Use.PUMP?"bobin_craft:cracked_concrete":
                    ZoneWeathering.patch(t.seed+b.variant(),b.wx(u,v),b.wz(u,v),4)>.7?"bobin_craft:rotten_wood":"spruce_planks");
                else if(ZoneWeathering.patch(t.seed+b.variant(),b.wx(u,v),b.wz(u,v),5)>.35)set(b,u,0,v,"bobin_craft:dark_forest_soil");
                for(int y=1;y<=25;y++)set(b,u,y,v,"air");
            }
        }
        private void shell(ZonePlan.Building b,int height,String wall,boolean metal){
            for(int v=-b.hd();v<=b.hd();v++)for(int u=-b.hw();u<=b.hw();u++){
                boolean edge=Math.abs(u)==b.hw()||Math.abs(v)==b.hd();if(!edge)continue;
                for(int y=1;y<=height;y++){
                    boolean corner=Math.abs(u)==b.hw()&&Math.abs(v)==b.hd();
                    // Keep the top wall course (y == height) as a solid wall plate (мауэрлат) so roofs never sit over window gaps.
                    boolean window=y<height&&((y>=2&&y<=3)||(height>=8&&y>=6&&y<=7))
                        &&((Math.abs(v)==b.hd()&&Math.abs(u)==b.hw()-2)||(Math.abs(u)==b.hw()&&Math.floorMod(v+5,5)==0));
                    double damp=ZoneWeathering.patch(t.seed+b.variant()*37L,b.wx(u,v)+y/2,b.wz(u,v),4);
                    String aged=wall;
                    if(damp>.62)aged=y<=2?"bobin_craft:mossy_concrete":wall.contains("plank")||wall.contains("wood")?"bobin_craft:rotten_wood":"bricks";
                    else if(damp<.24&&y<height)aged=wall.contains("plank")||wall.contains("wood")?"dark_oak_planks":"bobin_craft:cracked_concrete";
                    String block=corner?(metal?"bobin_craft:cracked_concrete":"bobin_craft:weathered_bark"):window?
                        windowBlock(b,u,y,v,metal):
                        y==1?(damp>.48?"mossy_stone_bricks":"bricks"):aged;
                    set(b,u,y,v,block);
                }
            }
        }
        private void pitchedRoof(ZonePlan.Building b,int eave,boolean axis){
            int across=axis?b.hd():b.hw();
            String gable=b.variant()%2==0?"dark_oak_planks":"bricks";
            for(int v=-b.hd()-1;v<=b.hd()+1;v++)for(int u=-b.hw()-1;u<=b.hw()+1;u++){
                int span=Math.abs(axis?v:u);
                int top=eave+Math.max(0,(across+1-span)/2);
                boolean inFootprint=Math.abs(u)<=b.hw()&&Math.abs(v)<=b.hd();
                boolean onWall=inFootprint&&(Math.abs(u)==b.hw()||Math.abs(v)==b.hd());
                // Close both gable ends and side-wall eaves completely from wall top (eave) up to roof slope (top).
                if(onWall){
                    for(int y=eave;y<top;y++)set(b,u,y,v,gable);
                }else if(inFootprint&&top>eave){
                    // Close internal step transitions so diagonal roof pitches never show 1-block air slits.
                    set(b,u,top-1,v,"dark_oak_planks");
                }else if(!inFootprint&&top>eave){
                    // Constructive cornice/bargeboard under gable overhangs.
                    set(b,u,top-1,v,BpSpec.of("dark_oak_slab").with("type","top"));
                }
                boolean interior=Math.abs(u)<b.hw()-1&&Math.abs(v)<b.hd()-1;
                boolean damage=interior&&b.variant()%4==1&&u>0&&v>0;
                if(!damage){
                    set(b,u,top,v,ZoneWeathering.patch(t.seed+b.variant(),b.wx(u,v),b.wz(u,v),5)>.62?"bobin_craft:roof_shingles":"bobin_craft:rusted_sheet_metal");
                }else{
                    // Constructive roof weathering: exposed rafters on the roof plane backed by tie-beams below.
                    set(b,u,top-1,v,"dark_oak_planks");
                    if((u+v)%2==0)set(b,u,top,v,BpSpec.of("dark_oak_slab").with("type","bottom"));
                }
            }
        }
        private void house(ZonePlan.Building b){
            boolean service=b.use()!=ZonePlan.Use.HOUSE;
            String wall=switch(s.theme()){
                case ALDER->b.variant()%2==0?"white_terracotta":"stripped_spruce_wood";
                case BRICK->b.variant()%2==0?"bricks":"light_gray_terracotta";
                case PINE,SHACKS->"spruce_planks";case FARM->"white_terracotta";default->"light_gray_terracotta";};
            int style=Math.floorMod(b.variant()+s.theme().ordinal()*2,7);
            if(service)style=switch(b.use()){case OFFICE,PUMP,CLINIC->4;case WORKSHOP->1;default->style;};
            int height=style==3&&!service?8:4;
            shell(b,height,style==1?"bricks":style==5?"dark_oak_planks":wall,service);
            if(style==0)hipRoof(b,5);
            else if(style==4)flatRoof(b,5);
            else if(style==2)splitRoof(b);
            else pitchedRoof(b,height+1,style==5||b.variant()%3==1);
            door(b,0,-b.hd(),"north");
            for(int u=-2;u<=2;u++)set(b,u,0,-b.hd()-1,"mossy_cobblestone");
            switch(b.use()){
                case HOUSE->domestic(b);
                case SHOP->shop(b);
                case CLINIC->clinic(b);
                case OFFICE->office(b);
                case WORKSHOP->workshop(b);
                case PUMP->pump(b);
                default->throw new IllegalArgumentException("Unexpected room use "+b.use());
            }
            // A wired wall lamp and repair boards fit the established muted, utilitarian art set.
            set(b,-b.hw()+1,3,-b.hd()-1,BpSpec.of("bobin_craft:wall_light").with("facing","north"));
            if(b.variant()%3==0)for(int y=2;y<=3;y++)set(b,b.hw()-2,y,b.hd(),"spruce_planks");
            if(b.use()==ZonePlan.Use.HOUSE){
                porch(b);
                if(style==3)upperFloor(b);
                if(style==6)ruinedWing(b);
                // Fenced kitchen patch beside each home; no uniform compound perimeter.
                int x=b.wx(-b.hw()-1,b.hd()+1),z=b.wz(-b.hw()-1,b.hd()+1);
                if(bp.inBounds(x,b.y()+1,z))put(x,b.y()+1,z,"composter");
                for(int v=-b.hd();v<=b.hd();v+=2)set(b,-b.hw()-2,1,v,"spruce_fence");
            }
        }
        private void hipRoof(ZonePlan.Building b,int eave){
            String roofMat=Math.floorMod(b.variant(),2)==0?"bobin_craft:roof_shingles":"bobin_craft:rusted_sheet_metal";
            for(int u=-b.hw()-1;u<=b.hw()+1;u++)for(int v=-b.hd()-1;v<=b.hd()+1;v++){
                int inset=Math.min(b.hw()+1-Math.abs(u),b.hd()+1-Math.abs(v));
                int top=eave+inset/2;
                if(inset>0&&top>eave)set(b,u,top-1,v,"dark_oak_planks");
                else if(inset==0&&(Math.abs(u)<=b.hw()||Math.abs(v)<=b.hd()))set(b,u,eave-1,v,BpSpec.of("dark_oak_slab").with("type","top"));
                set(b,u,top,v,roofMat);
            }
        }
        private void flatRoof(ZonePlan.Building b,int h){
            for(int u=-b.hw()-1;u<=b.hw()+1;u++)for(int v=-b.hd()-1;v<=b.hd()+1;v++){
                boolean overhang=Math.abs(u)>b.hw()||Math.abs(v)>b.hd();
                if(overhang){
                    set(b,u,h,v,BpSpec.of("stone_brick_slab").with("type","bottom"));
                }else{
                    set(b,u,h,v,ZoneWeathering.patch(t.seed+b.id(),b.wx(u,v),b.wz(u,v),3)>.63?"bobin_craft:mossy_concrete":"bobin_craft:cracked_concrete");
                    if(Math.abs(u)==b.hw()||Math.abs(v)==b.hd())set(b,u,h+1,v,BpSpec.of("stone_brick_slab"));
                }
            }
            set(b,b.hw()-2,h+1,0,"hopper");set(b,b.hw()-2,h+2,0,"iron_bars");
        }
        private void splitRoof(ZonePlan.Building b){
            for(int u=-b.hw()-1;u<=b.hw()+1;u++)for(int v=-b.hd()-1;v<=b.hd()+1;v++){
                int h=u>1?5:6+Math.max(0,(b.hd()+1-Math.abs(v))/2);
                boolean inFootprint=Math.abs(u)<=b.hw()&&Math.abs(v)<=b.hd();
                if(inFootprint&&(u==1||Math.abs(u)==b.hw()||Math.abs(v)==b.hd())){
                    for(int dy=5;dy<h;dy++)set(b,u,dy,v,"dark_oak_planks");
                }else if(inFootprint&&h>5){
                    set(b,u,h-1,v,"dark_oak_planks");
                }else if(!inFootprint&&h>5){
                    set(b,u,h-1,v,BpSpec.of("dark_oak_slab").with("type","top"));
                }
                set(b,u,h,v,u>1?"bobin_craft:rusted_sheet_metal":"bobin_craft:roof_shingles");
            }
            for(int v=1;v<b.hd();v++)if(v!=2&&v!=3)for(int h=1;h<=4;h++)set(b,1,h,v,"bricks");
        }
        private void upperFloor(ZonePlan.Building b){
            int stair=b.hw()-2,front=-b.hd()+1;
            for(int u=-b.hw()+1;u<b.hw();u++)for(int v=-b.hd()+1;v<b.hd();v++){
                if(!(u>=stair-1&&u<=stair&&v<=front+5))set(b,u,5,v,"dark_oak_planks");
            }
            for(int i=0;i<5;i++){
                for(int h=i+1;h<=Math.min(8,i+4);h++){
                    set(b,stair,h,front+i,"air");
                    if(stair-1>-b.hw())set(b,stair-1,h,front+i,"air");
                }
                set(b,stair,i+1,front+i,BpSpec.of("spruce_stairs").with("facing","south"));
            }
            // Constructive stairwell railing on the upper floor so the opening doesn't look like a raw hole.
            for(int v=front;v<=front+4;v++){
                if(stair-2>-b.hw())set(b,stair-2,6,v,"spruce_fence");
            }
            for(int u=-b.hw()+1;u<b.hw();u++)if(u!=-1&&u!=0&&u<stair-1)for(int h=6;h<=8;h++)set(b,u,h,1,"white_terracotta");
            set(b,-b.hw()+2,6,b.hd()-3,BpSpec.of("gray_bed").with("part","foot").with("facing","south"));
            set(b,-b.hw()+2,6,b.hd()-2,BpSpec.of("gray_bed").with("part","head").with("facing","south"));
            barrel(b,b.hw()-1,6,b.hd()-1,"warzone_supply");
            for(int side:new int[]{-1,1})for(int u=-2;u<=2;u++)set(b,u,7,side*b.hd(),windowBlock(b,u,7,side*b.hd(),false));
        }
        private void ruinedWing(ZonePlan.Building b){
            // A collapsed rear roof section supported by an intact cross-wall and remaining charred rafters.
            for(int u=-b.hw();u<=b.hw();u++){
                for(int h=4;h<=7;h++)if(bp.inBounds(b.wx(u,1),b.y()+h,b.wz(u,1))){
                    var cur=bp.get(b.wx(u,1),b.y()+h,b.wz(u,1));
                    if(cur==null||cur.isAir())set(b,u,h,1,"bricks");
                }
            }
            for(int u=1;u<=b.hw()+1;u++)for(int v=2;v<=b.hd()+1;v++)for(int h=5;h<=14;h++)set(b,u,h,v,"air");
            for(int u=1;u<b.hw();u++)for(int v=2;v<b.hd();v++){
                if(v%2==0)set(b,u,4,v,BpSpec.of("dark_oak_slab").with("type","top"));
            }
            for(int v=2;v<b.hd();v++){
                var cell=bp.get(b.wx(b.hw()-1,v),b.y()+1,b.wz(b.hw()-1,v));
                if(cell==null||!cell.id.equals("barrel"))set(b,b.hw()-1,1,v,BpSpec.of("cobblestone_slab"));
            }
            for(int u=1;u<b.hw();u++)if(Math.floorMod(u+b.variant(),3)==0)set(b,u,3,b.hd(),"bobin_craft:old_window_frame");
            set(b,0,1,b.hd()-2,"campfire");
        }
        private void domestic(ZonePlan.Building b){
            int layout=b.variant()%8;String partition=layout%2==0?"stripped_spruce_wood":"white_terracotta";
            if(layout==0||layout==2||layout==5){int u=layout==0?-1:layout==2?2:1;
                for(int v=-b.hd()+3;v<b.hd();v++)if(v!=1&&v!=2)for(int y=1;y<=3;y++)set(b,u,y,v,partition);
            }else if(layout==1||layout==3||layout==6){int v=layout==1?1:layout==3?0:b.hd()-5;
                for(int u=-b.hw()+1;u<b.hw();u++)if(Math.abs(u)>1)for(int y=1;y<=3;y++)set(b,u,y,v,partition);
            }else if(layout==4){
                for(int v=-b.hd()+3;v<b.hd();v++)if(v!=-1&&v!=0)for(int y=1;y<=3;y++)set(b,-b.hw()+3,y,v,partition);
                for(int u=1;u<b.hw();u++)if(u!=2)for(int y=1;y<=3;y++)set(b,u,y,0,partition);
            }else {for(int v=1;v<b.hd()-1;v++)set(b,-1,1,v,"bookshelf");}
            int side=b.variant()%2==0?-1:1;int bedU=side*(b.hw()-2),bedV=b.hd()-3;
            bed(b,bedU,bedV,b.variant()%3==0?"brown_bed":"gray_bed");
            if(layout==2||layout==6)bed(b,-bedU,b.hd()-3,"green_bed");
            set(b,-side*(b.hw()-1),1,-b.hd()+2,"smoker");
            set(b,-side*(b.hw()-1),1,-b.hd()+3,"cauldron");
            set(b,-side*(b.hw()-1),1,-b.hd()+4,"crafting_table");
            set(b,side*(b.hw()-1),1,-b.hd()+2,"bookshelf");
            barrel(b,side*(b.hw()-1),1,b.hd()-1,"warzone_civilian");
            set(b,side*(b.hw()-2),1,-1,"spruce_fence");set(b,side*(b.hw()-2),2,-1,"spruce_pressure_plate");
            set(b,side*(b.hw()-2),1,-3,BpSpec.of("spruce_stairs").with("facing","south"));
            set(b,0,4,-b.hd()+2,BpSpec.of("lantern").with("hanging",true));
            // Stove flue follows its actual kitchen position through the roof.
            for(int y=2;y<=10;y++)set(b,-side*(b.hw()-1),y,-b.hd()+2,"bricks");
            set(b,-side*(b.hw()-1),11,-b.hd()+2,"flower_pot");
        }
        private void shop(ZonePlan.Building b){
            for(int u=-b.hw()+2;u<=b.hw()-2;u++)if(u!=0)set(b,u,1,0,"spruce_planks");
            for(int v=-b.hd()+2;v<b.hd();v+=2){set(b,b.hw()-1,1,v,"bookshelf");barrel(b,-b.hw()+1,1,v,"warzone_supply");}
            set(b,2,2,0,"stone_pressure_plate");barrel(b,2,1,b.hd()-1,"warzone_technical");
        }
        private void clinic(ZonePlan.Building b){
            for(int u:new int[]{-b.hw()+2,2,b.hw()-2}){bed(b,u,1,"white_bed");barrel(b,u,1,b.hd()-1,"warzone_medical");}
            for(int v=-b.hd()+2;v<=0;v++)set(b,-3,1,v,"white_wool");
            set(b,b.hw()-1,1,-b.hd()+2,"water_cauldron");
            set(b,0,3,-b.hd()-1,"red_terracotta");set(b,-1,3,-b.hd()-1,"white_terracotta");set(b,1,3,-b.hd()-1,"white_terracotta");
        }
        private void office(ZonePlan.Building b){
            for(int u:new int[]{-b.hw()+2,b.hw()-2}){set(b,u,1,0,"spruce_fence");set(b,u,2,0,"spruce_pressure_plate");
                set(b,u,1,2,BpSpec.of("spruce_stairs").with("facing","north"));set(b,u,1,b.hd()-1,"bookshelf");}
            set(b,b.hw()-1,1,-b.hd()+2,"bobin_craft:radio");barrel(b,-b.hw()+1,1,-b.hd()+2,"warzone_command");
            for(int v=2;v<b.hd();v++)if(v!=3)for(int y=1;y<=3;y++)set(b,0,y,v,"white_terracotta");
        }
        private void workshop(ZonePlan.Building b){
            for(int u=-2;u<=2;u++)for(int y=1;y<=3;y++)set(b,u,y,-b.hd(),"air");
            door(b,-b.hw()+2,-b.hd(),"north");
            for(int v=-b.hd()+2;v<b.hd();v+=3){set(b,-b.hw()+1,1,v,"smithing_table");set(b,b.hw()-1,1,v,"blast_furnace");}
            barrel(b,-b.hw()+2,1,b.hd()-1,"warzone_technical");set(b,b.hw()-2,1,b.hd()-1,"anvil");
            for(int u=-2;u<=2;u++)set(b,u,0,0,"polished_andesite");
        }
        private void pump(ZonePlan.Building b){
            for(int u:new int[]{-4,4})for(int v=-b.hd()+3;v<=b.hd()-2;v++){
                set(b,u,1,v,"waxed_oxidized_copper");if(v%3==0)set(b,u,2,v,"polished_andesite");}
            set(b,-b.hw()+1,2,0,"bobin_craft:equipment_panel");barrel(b,b.hw()-1,1,b.hd()-1,"warzone_technical");
            for(int u=-4;u<=4;u++)set(b,u,1,b.hd()-2,"waxed_oxidized_copper");
        }
        private void barn(ZonePlan.Building b){
            shell(b,6,"spruce_planks",false);pitchedRoof(b,7,false);
            door(b,-b.hw()+2,-b.hd(),"north");
            for(int u=-2;u<=2;u++)for(int y=1;y<=4;y++)set(b,u,y,-b.hd(),"air");
            for(int v=-b.hd()+3;v<=b.hd()-2;v+=4)for(int side:new int[]{-1,1}){
                for(int u=b.hw()-4;u<b.hw();u++){set(b,side*u,1,v,"hay_block");set(b,side*u,2,v,"hay_block");}
                set(b,side*(b.hw()-4),1,v-1,"spruce_fence");}
            for(int v=b.hd()-6;v<b.hd();v++)for(int u=-b.hw()+1;u<b.hw();u++)set(b,u,5,v,"spruce_planks");
            // Constructive loft railing and wide unobstructed ladder exit.
            for(int u=-b.hw()+1;u<b.hw();u++)if(Math.abs(u)>1)set(b,u,6,b.hd()-6,"spruce_fence");
            for(int u=-1;u<=1;u++)for(int y=6;y<=7;y++)set(b,u,y,b.hd()-5,"air");
            for(int y=1;y<=5;y++)set(b,0,y,b.hd()-5,BpSpec.of("ladder").with("facing","north"));
            barrel(b,b.hw()-1,1,-b.hd()+2,"warzone_supply");
        }
        private void hall(ZonePlan.Building b){
            shell(b,8,"bricks",true);
            if(s.theme()==ZonePlan.Theme.CONCRETE){
                // North-light roof bays distinguish the production plant from the timber sawmill.
                for(int v=-b.hd()-1;v<=b.hd()+1;v++)for(int u=-b.hw()-1;u<=b.hw()+1;u++){
                    int bay=Math.floorMod(u+b.hw()+1,8),top=9+bay/3;
                    for(int y=9;y<=top;y++)set(b,u,y,v,bay==7&&y>9?BpSpec.of("tinted_glass"):rust);
                }
            }else pitchedRoof(b,9,false);
            door(b,-b.hw()+2,-b.hd(),"north");
            for(int u=-3;u<=3;u++)for(int y=1;y<=5;y++)set(b,u,y,-b.hd(),"air");
            for(int v=-b.hd()+4;v<b.hd()-2;v+=7){
                for(int u=-b.hw()+1;u<b.hw();u++)set(b,u,7,v,"gray_concrete");
                for(int side:new int[]{-1,1})for(int y=1;y<=7;y++)set(b,side*(b.hw()-1),y,v,"polished_andesite");
                if(s.theme()==ZonePlan.Theme.SAWMILL){
                    for(int u=-7;u<=-3;u++)set(b,u,1,v,BpSpec.of("spruce_log").with("axis","x"));
                    set(b,4,1,v,"stonecutter");set(b,4,2,v,"iron_bars");set(b,5,1,v,"crafting_table");
                }else {for(int u:new int[]{-6,6}){set(b,u,1,v,"stonecutter");set(b,u,2,v,"hopper");set(b,u,3,v,"polished_andesite");
                    set(b,u+1,1,v,"blast_furnace");}}
            }
            barrel(b,-b.hw()+2,1,b.hd()-1,"warzone_technical");barrel(b,b.hw()-2,1,b.hd()-1,"warzone_supply");
        }
        private void unfinished(ZonePlan.Building b){
            for(int y:new int[]{0,5,10,15})for(int v=-b.hd();v<=b.hd();v++)for(int u=-b.hw();u<=b.hw();u++){
                if(y==15&&((u>2&&v>0)||hash(b.wx(u,v),b.wz(u,v))%11==0))continue;
                set(b,u,y,v,concrete);
            }
            for(int v=-b.hd();v<=b.hd();v+=5)for(int u=-b.hw();u<=b.hw();u+=5)for(int y=1;y<=14;y++)set(b,u,y,v,concrete);
            // Straight 2-wide service stair with 4 blocks of headroom and framed safety railings on each landing.
            for(int i=0;i<15;i++)for(int u=-b.hw()+2;u<=-b.hw()+3;u++){
                int v=-b.hd()+1+i;
                for(int y=i+1;y<=i+4;y++)set(b,u,y,v,"air");
            }
            for(int i=0;i<15;i++)for(int u=-b.hw()+2;u<=-b.hw()+3;u++)
                set(b,u,i,-b.hd()+1+i,BpSpec.of("stone_brick_stairs").with("facing","south"));
            for(int floorY:new int[]{5,10,15}){
                for(int v=-b.hd()+2;v<=Math.min(b.hd()-1,-b.hd()+floorY+2);v++)set(b,-b.hw()+4,floorY+1,v,"iron_bars");
            }
            for(int y:new int[]{1,6,11})for(int u=3;u<=b.hw();u++)if(u%4!=0)set(b,u,y,-b.hd(),"bricks");
            for(int y=16;y<=18;y++)for(int u:new int[]{-b.hw(),b.hw()-1})set(b,u,y,-b.hd(),"iron_bars");
            barrel(b,b.hw()-2,1,b.hd()-2,"warzone_technical");
        }
        private void shack(ZonePlan.Building b){
            shell(b,3,b.variant()%2==0?"spruce_planks":"bobin_craft:rotten_wood",false);
            for(int u=-b.hw()-1;u<=b.hw()+1;u++)for(int v=-b.hd()-1;v<=b.hd()+1;v++){
                boolean overhang=Math.abs(u)>b.hw()||Math.abs(v)>b.hd();
                set(b,u,4,v,overhang?BpSpec.of("dark_oak_slab").with("type","bottom"):
                    BpSpec.of(Math.floorMod(u+v,5)==0?"bobin_craft:tarp_olive":"bobin_craft:rusted_sheet_metal"));
            }
            door(b,0,-b.hd(),"north");bed(b,-b.hw()+2,b.hd()-3,"brown_bed");barrel(b,b.hw()-1,1,b.hd()-1,"warzone_civilian");
            set(b,-b.hw()+1,1,-b.hd()+2,"smoker");set(b,b.hw()-1,2,-b.hd()+1,"lantern");
        }
        private void field(ZonePlan.Field f){
            for(int v=-f.hd();v<=f.hd();v++)for(int u=-f.hw();u<=f.hw();u++){
                int x=f.x()+u,z=f.z()+v;for(int dy=-3;dy<0;dy++)put(x,f.y()+dy,z,"dirt");for(int dy=1;dy<=15;dy++)bp.air(x,f.y()+dy,z);
                boolean water=Math.floorMod(u+f.hw(),8)==0;
                if(water)put(x,f.y(),z,"water");else if(ZoneWeathering.patch(t.seed+f.crop(),x,z,5)>.67)put(x,f.y(),z,"coarse_dirt");
                else {bp.set(x,f.y(),z,BpSpec.of("farmland").with("moisture","7"));bp.set(x,f.y()+1,z,BpSpec.of(f.crop()==1?"potatoes":"wheat").with("age",Integer.toString(Math.min(7,(int)(ZoneWeathering.patch(t.seed+633,x,z,6)*8)))));}
            }
            for(int u=-f.hw();u<=f.hw();u++)if(Math.abs(u)>2)put(f.x()+u,f.y()+1,f.z()+f.hd()+1,"spruce_fence");
        }
        private void landmarks(){
            int x=s.x(),z=s.z(),y=s.y();
            if(s.id()>=100){homesteadDetail(x+17,z+15);return;}
            switch(s.theme()){
                case ALDER->{well(x-12,z+4);busStop(x+28,z+50);vehicle("humvee",3,3,5,x+47,z+38);}
                case BRICK->{well(x+12,z+2);logStack(x-28,z+50);vehicle("truck",3,4,7,x+48,z+37);}
                case PINE->{logStack(x-43,z+3);vehicle("truck",3,4,7,x+36,z+39);}
                case FARM->{silo(x-60,z-31,3,14);silo(x-51,z-31,3,11);vehicle("truck",3,4,7,x+52,z+40);}
                case SAWMILL->{for(int dz=-24;dz<=24;dz+=12)logStack(x-49,z+dz);cargo(x+23,z+3,"container_green");vehicle("truck",3,4,7,x+42,z+40);}
                case CONCRETE->{chimney(x-48,z-38,22);for(int dx=0;dx<=14;dx+=7)silo(x-44+dx,z+36,3,11);
                    cargo(x+29,z+2,"container_red");vehicle("tank",4,3,9,x+49,z+39);}
                case ESTATE->{for(int dx=-13;dx<=9;dx+=6)for(int dz=0;dz<=4;dz+=2)for(int dy=1;dy<=2;dy++)put(x+dx,y+dy,z+36+dz,"bricks");
                    vehicle("truck",3,4,7,x+38,z+36);}
                case WATERWORKS->{basin(x-34,z+27,5,13);basin(x-18,z+27,5,13);silo(x-46,z-37,3,13);vehicle("humvee",3,3,5,x+39,z+38);
                    for(int pz=z-13;pz<=z+13;pz++)put(x-26,y+1,pz,"waxed_oxidized_copper");
                    for(int px=x-34;px<=x-18;px++)put(px,y+1,z+13,"waxed_oxidized_copper");}
                case SHACKS->{well(x+3,z-16);logStack(x-22,z+20);}
                case REFINERY->refinery();
                case SUBSTATION->substation();
            }
            // Small open-front lean-tos with full corner posts and back wall planks.
            if(s.theme()==ZonePlan.Theme.ALDER||s.theme()==ZonePlan.Theme.PINE||s.theme()==ZonePlan.Theme.BRICK){
                int ox=x-12,oz=z+44,gy=t.height(ox,oz);
                for(int u=-3;u<=3;u++)for(int v=-2;v<=2;v++){
                    put(ox+u,gy,oz+v,"coarse_dirt");
                    put(ox+u,gy+4,oz+v,"bobin_craft:rusted_sheet_metal");
                    if((u==-3||u==3)&&Math.abs(v)==2)for(int h=1;h<=3;h++)put(ox+u,gy+h,oz+v,"spruce_log");
                    else if(v==2)for(int h=1;h<=3;h++)put(ox+u,gy+h,oz+v,"dark_oak_planks");
                }
                put(ox-2,gy+1,oz+1,"composter");
            }
        }
        private void porch(ZonePlan.Building b){
            for(int u=-2;u<=2;u++)for(int v=-b.hd()-3;v<=-b.hd()-1;v++){
                set(b,u,0,v,"mossy_stone_bricks");
                if(b.variant()%3!=2){
                    boolean edge=Math.abs(u)==2||v==-b.hd()-3;
                    set(b,u,4,v,edge?BpSpec.of("dark_oak_slab").with("type","bottom"):BpSpec.of("bobin_craft:rusted_sheet_metal"));
                }
            }
            if(b.variant()%3!=2){
                for(int side:new int[]{-2,2}){
                    for(int y=1;y<=3;y++)set(b,side,y,-b.hd()-3,"bobin_craft:weathered_bark");
                    set(b,side,1,-b.hd()-2,"spruce_fence");
                    set(b,side,3,-b.hd()-1,BpSpec.of("spruce_stairs").with("facing","south").with("half","top"));
                }
            }
            for(int v=b.hd()-4;v<=b.hd()-1;v++)for(int y=1;y<=2;y++)set(b,b.hw()+1,y,v,BpSpec.of("spruce_log").with("axis","z"));
            if(b.variant()%2==0)for(int y=1;y<=3;y++)for(int u=1;u<=2;u++)set(b,u,y,b.hd()+1,BpSpec.of("vine").with("north",true));
        }
        private void backyards(){
            for(var b:s.buildings())if(b.use()==ZonePlan.Use.HOUSE||b.use()==ZonePlan.Use.SHACK){
                int rear=b.hd()+5;
                for(int u=-b.hw()-2;u<=b.hw()+2;u++){
                    int x=b.wx(u,rear),z=b.wz(u,rear);boolean garden=false;for(var f:s.fields())if(f.contains(x,z,1))garden=true;
                    if(!garden&&Math.abs(u)>1&&Math.floorMod(u+b.variant(),7)>1){int y=t.height(x,z);put(x,y+1,z,"spruce_fence");}}
                int bx=b.wx(b.hw()+2,b.hd()+2),bz=b.wz(b.hw()+2,b.hd()+2),by=t.height(bx,bz);
                if(bp.inBounds(bx,by+2,bz)){put(bx,by+1,bz,"composter");bp.set(bx,by+2,bz,BpSpec.of("dark_oak_trapdoor").with("half","bottom"));}
                if(b.variant()%3==2)for(int u=-b.hw()-2;u<=-b.hw()-1;u++)for(int v=b.hd()-2;v<=b.hd()+2;v++){
                    int x=b.wx(u,v),z=b.wz(u,v),y=t.height(x,z);bp.set(x,y+1,z,BpSpec.of((u+v)%2==0?"brick_slab":"cobblestone_slab"));
                }
            }
        }
        private void serviceDetails(){
            int x=s.x(),z=s.z(),y=s.y();
            switch(s.theme()){
                case SAWMILL->{timberRack(x+17,z-4);timberRack(x+29,z+14);leanTo(x-35,z+29,7,5);
                    for(int u=-5;u<=5;u++)put(x-35+u,t.height(x-35+u,z+29)+1,z+29,"oak_planks");pallet(x+12,z+31);pallet(x+17,z+34);}
                case CONCRETE->{aggregate(x-33,z+11,6);aggregate(x-46,z+5,5);pipe(x-43,z+27,x-26,z+27,y+5);
                    for(int dz=-5;dz<=7;dz++)put(x-10,y+2,z+dz,"hopper");
                    for(int dz=-5;dz<=7;dz+=4)for(int h=1;h<=4;h++)put(x-12,y+h,z+dz,"iron_bars");pallet(x+13,z+26);pallet(x+19,z+27);leanTo(x+28,z-9,9,4);}
                case WATERWORKS->{for(int px=x-39;px<=x-13;px++){put(px,y+1,z+27,"iron_bars");put(px,y+2,z+27,"iron_bars");}
                    for(int pz=z-13;pz<=z+13;pz+=7){put(x-27,y,pz,"bobin_craft:cracked_concrete");put(x-27,y+1,pz,"bobin_craft:equipment_panel");}
                    pipe(x-24,z-14,x-24,z+13,y+3);pipe(x-34,z+12,x-18,z+12,y+3);pallet(x+13,z+5);leanTo(x+27,z-5,6,4);}
                case ESTATE->{aggregate(x-11,z+25,5);aggregate(x+6,z-28,4);
                    for(int px=x-40;px<=x-10;px+=5)for(int h=1;h<=10;h++)put(px,y+h,z+24,"iron_bars");
                    for(int px=x-40;px<=x-10;px++)put(px,y+5,z+24,"spruce_slab");
                    for(int dz=-4;dz<=8;dz++)put(x+43,y+1,z+dz,"bobin_craft:rusted_sheet_metal");leanTo(x-35,z+37,6,4);pallet(x+11,z-5);}
                case REFINERY->{vehicle("truck",3,4,7,0,0);pallet(x+14,z+29);cargo(x-52,z+8,"container_blue");}
                case SUBSTATION->{vehicle("humvee",3,3,5,0,0);pallet(x+34,z-6);}
                case FARM->{leanTo(x-53,z+7,7,5);timberRack(x+12,z+37);pallet(x-8,z-19);}
                case ALDER,BRICK,PINE->{int side=s.id()%2==0?-1:1;for(int u=17;u<=39;u++){
                    int px=x+side*u,pz=z+55,ground=t.height(px,pz);if(Math.floorMod(u+s.id(),9)<2)continue;put(px,ground+1,pz,u%4==0?"mossy_cobblestone":"mossy_cobblestone_wall");}pallet(x+12,z+29);}
                default->{ }
            }
        }
        private void refinery(){new IndustrialFacilities(t,s,bp).refinery();}
        private void substation(){new IndustrialFacilities(t,s,bp).substation();}
        private void fenceAt(int x,int z){int y=t.height(x,z);for(int h=1;h<=3;h++)put(x,y+h,z,"iron_bars");if(Math.floorMod(x+z,8)==0)for(int h=0;h<=4;h++)put(x,y+h,z,"bobin_craft:cracked_concrete");}
        private void pipe(int ax,int az,int bx,int bz,int y){
            int x=ax,z=az;put(x,y,z,"bobin_craft:utility_pipe");
            while(x!=bx){x+=Integer.signum(bx-x);put(x,y,z,"bobin_craft:utility_pipe");}while(z!=bz){z+=Integer.signum(bz-z);put(x,y,z,"bobin_craft:utility_pipe");}
            for(int i=0;i<=Math.max(Math.abs(bx-ax),Math.abs(bz-az));i+=8){double f=i/(double)Math.max(1,Math.max(Math.abs(bx-ax),Math.abs(bz-az)));int px=(int)Math.round(ax+(bx-ax)*f),pz=(int)Math.round(az+(bz-az)*f);
                for(int h=t.height(px,pz)+1;h<y;h++)put(px,h,pz,"iron_bars");}
        }
        private void aggregate(int x,int z,int radius){int y=t.height(x,z);for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)for(int h=1;h<=Math.max(0,3-(int)Math.hypot(dx,dz)/2);h++)put(x+dx,y+h,z+dz,"gravel");}
        private void pallet(int x,int z){int y=t.height(x,z);for(int dx=-2;dx<=2;dx++)for(int dz=-1;dz<=1;dz++){
            bp.set(x+dx,y+1,z+dz,BpSpec.of("spruce_slab").with("type","double"));if(Math.abs(dx)<2)put(x+dx,y+2,z+dz,"bobin_craft:supply_crate");}}
        private void timberRack(int x,int z){int y=t.height(x,z);for(int dx=-5;dx<=5;dx++)for(int dz=-2;dz<=2;dz++){
            put(x+dx,y+1,z+dz,"oak_planks");if(dx%5==0)for(int h=2;h<=5;h++)put(x+dx,y+h,z+dz,"iron_bars");
            if(dz%2==0)put(x+dx,y+3,z+dz,"spruce_planks");put(x+dx,y+6,z+dz,"bobin_craft:rusted_sheet_metal");}}
        private void leanTo(int x,int z,int hw,int hd){int y=t.height(x,z);for(int dx=-hw;dx<=hw;dx++)for(int dz=-hd;dz<=hd;dz++){
            put(x+dx,y,z+dz,"coarse_dirt");put(x+dx,y+4,z+dz,"bobin_craft:rusted_sheet_metal");
            if((Math.abs(dx)==hw||dx==0)&&Math.abs(dz)==hd)for(int h=-2;h<=3;h++)put(x+dx,y+h,z+dz,"bobin_craft:weathered_bark");
            else if(dz==hd&&Math.floorMod(dx+s.id(),7)>1)for(int h=1;h<=3;h++)put(x+dx,y+h,z+dz,"dark_oak_planks");}}
        private void well(int x,int z){int y=t.height(x,z);
            for(int dx=-3;dx<=3;dx++)for(int dz=-2;dz<=2;dz++)for(int h=1;h<=5;h++)bp.air(x+dx,y+h,z+dz);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int h=-3;h<=0;h++)put(x+dx,y+h,z+dz,h==-3?"mossy_cobblestone":"water");for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            boolean edge=Math.abs(dx)==2||Math.abs(dz)==2;put(x+dx,y,z+dz,edge?"mossy_cobblestone":"water");
            if(edge)put(x+dx,y+1,z+dz,"mossy_cobblestone");}
            for(int side:new int[]{-2,2})for(int dy=2;dy<=4;dy++)put(x+side,y+dy,z,"spruce_fence");
            for(int dx=-3;dx<=3;dx++)for(int dz=-2;dz<=2;dz++)put(x+dx,y+5,z+dz,"bobin_craft:rusted_sheet_metal");}
        private void busStop(int x,int z){
            // Oriented facing SOUTH (+Z) toward the main district road at z + 58..66.
            int y=t.height(x,z);
            for(int dx=-4;dx<=4;dx++)for(int dz=0;dz<=3;dz++){
                put(x+dx,y,z+dz,"bobin_craft:cracked_concrete");
                for(int dy=1;dy<=3;dy++)bp.air(x+dx,y+dy,z+dz);
                put(x+dx,y+4,z+dz,dz==3?"stone_brick_slab":"bobin_craft:rusted_sheet_metal");
                if(dz==0||(Math.abs(dx)==4&&dz<=2))for(int dy=1;dy<=3;dy++)put(x+dx,y+dy,z+dz,"bricks");
                else if(Math.abs(dx)==4&&dz==3)for(int dy=1;dy<=3;dy++)put(x+dx,y+dy,z+dz,"iron_bars");
            }
            for(int dx=-3;dx<=3;dx++)bp.set(x+dx,y+1,z+1,BpSpec.of("spruce_stairs").with("facing","south"));
        }
        private void logStack(int x,int z){int y=t.height(x,z),variant=Math.floorMod(x+z+s.id(),4),length=variant==0?3:variant==1?4:2;boolean across=variant%2==0;
            for(int v=-length;v<=length;v++)for(int u=-2;u<=2;u++)for(int h=1;h<=Math.max(1,3-Math.abs(u)-variant/3);h++){
                if(v==length&&Math.floorMod(u+h+variant,3)==0)continue;
                bp.set(x+(across?v:u),y+h,z+(across?u:v),BpSpec.of(variant==2?"oak_log":"spruce_log").with("axis",across?"x":"z"));}
        }
        private void homesteadDetail(int x,int z){int y=t.height(x,z);switch(s.id()%5){
            case 0->logStack(x,z);
            case 1->{leanTo(x,z,3,4);for(int v=-2;v<=2;v++)put(x-2,y+1,z+v,"barrel");put(x+1,y+1,z,"anvil");}
            case 2->{for(int side:new int[]{-2,2})for(int h=1;h<=3;h++)put(x+side,y+h,z,"spruce_fence");for(int u=-2;u<=2;u++)put(x+u,y+3,z,"chain");put(x,y+1,z+2,"water_cauldron");put(x+2,y+1,z+2,"composter");}
            case 3->{for(int u=-2;u<=2;u++)for(int v=-3;v<=3;v++){if(v==-3||Math.abs(u)==2)for(int h=1;h<=2;h++)put(x+u,y+h,z+v,"spruce_planks");put(x+u,y+3,z+v,"dark_oak_slab");}put(x,y+1,z+1,"hay_block");put(x,y+1,z-1,"composter");}
            default->{for(int u=-2;u<=2;u++)for(int v=-3;v<=3;v++)if(Math.abs(u)==2||v==3)put(x+u,y+1,z+v,"mossy_cobblestone_wall");put(x-1,y+1,z,"crafting_table");put(x+1,y+1,z,"smoker");put(x,y+1,z-2,"bobin_craft:ammo_crate");}
        }}
        private void silo(int x,int z,int radius,int height){int y=t.height(x,z);for(int dy=-3;dy<=height;dy++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            int d=dx*dx+dz*dz;if(d>radius*radius+1)continue;
            put(x+dx,y+dy,z+dz,dy<=0||dy==height?"polished_andesite":d>=(radius-1)*(radius-1)?"light_gray_concrete":"air");}
            for(int h=1;h<=height;h++)bp.set(x+radius+1,y+h,z,BpSpec.of("ladder").with("facing","east"));}
        private void chimney(int x,int z,int height){int y=t.height(x,z);for(int dy=-3;dy<=height;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
            put(x+dx,y+dy,z+dz,(dx==0&&dz==0&&dy>0)?"air":dy%7==0?"light_gray_terracotta":"bricks");}
        private void basin(int x,int z,int hw,int hd){int y=s.y();for(int dx=-hw;dx<=hw;dx++)for(int dz=-hd;dz<=hd;dz++){
            boolean edge=Math.abs(dx)==hw||Math.abs(dz)==hd;put(x+dx,y-3,z+dz,"bobin_craft:cracked_concrete");
            for(int dy=-2;dy<=0;dy++)put(x+dx,y+dy,z+dz,edge?"bobin_craft:cracked_concrete":dy==-2?"water":"air");
            if(edge)put(x+dx,y+1,z+dz,"iron_bars");}}
        private void cargo(int x,int z,String id){int y=t.height(x,z);for(int dx=0;dx<5;dx++)for(int dz=0;dz<10;dz++)for(int dy=1;dy<=4;dy++)
            put(x+dx,y+dy,z+dz,dx==0||dx==4||dy==1||dy==4||dz==9?"bobin_craft:"+id:"air");}
        private void vehicle(String kind,int width,int height,int length,int ignoredX,int ignoredZ){
            var park=ZonePlan.parking(s);if(park==null)return;
            int x=park.x(),z=park.z(),ground=t.height(x,z);
            for(int dx=-3;dx<=width+2;dx++)for(int dz=-3;dz<=length+2;dz++){
                int px=x+dx,pz=z+dz;double outside=Math.hypot(Math.max(0,Math.max(-dx,dx-width+1)),Math.max(0,Math.max(-dz,dz-length+1)));
                double wear=ZoneWeathering.patch(t.seed+919,px,pz,4);if(outside>1+wear*2)continue;
                int floor=outside==0?ground:t.height(px,pz);
                for(int y=Math.min(floor,ground)-3;y<=floor;y++)put(px,y,pz,y==floor?(wear>.68?"bobin_craft:trampled_mud":"bobin_craft:dark_forest_soil"):"dirt");
                if(dx>=-1&&dx<=width&&dz>=-1&&dz<=length)for(int dy=1;dy<=height+2;dy++)bp.air(px,ground+dy,pz);
            }
            for(int dx=0;dx<width;dx++)for(int dy=0;dy<height;dy++)for(int dz=0;dz<length;dz++)bp.set(x+dx,ground+1+dy,z+dz,
                    BpSpec.of("bobin_craft:military_"+kind+"_"+dx+"_"+dy+"_"+dz).with("facing","north"));
        }
        private void bed(ZonePlan.Building b,int u,int v,String id){set(b,u,1,v,BpSpec.of(id).with("facing","south").with("part","foot"));set(b,u,1,v+1,BpSpec.of(id).with("facing","south").with("part","head"));}
        private void door(ZonePlan.Building b,int u,int v,String face){for(int y=1;y<=2;y++)set(b,u,y,v,BpSpec.of("spruce_door").with("facing",face).with("half",y==1?"lower":"upper").with("hinge",b.variant()%2==0?"left":"right"));}
        private void barrel(ZonePlan.Building b,int u,int y,int v,String loot){int x=b.wx(u,v),z=b.wz(u,v);bp.addBlockEntity(new Blueprint.BlockEntity(x,b.y()+y,z,Blueprint.Kind.BARREL,"up","bobin_craft:chests/"+loot,hash(x,z),false,false),BpSpec.of("barrel").with("facing","up"));}
        private void stand(ResidentialPlan.Point p){put(p.x(),p.y()-1,p.z(),"bobin_craft:trampled_mud");bp.air(p.x(),p.y(),p.z());bp.air(p.x(),p.y()+1,p.z());}
    }
}
