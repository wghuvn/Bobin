package com.bobincraft.warzone;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Eight silhouettes and separately furnished variants in off-road clearings. */
public final class DiscoveryStructures {
    private final WarzoneTerrain terrain;private final Map<Integer,Blueprint> cache=new ConcurrentHashMap<>();
    public DiscoveryStructures(WarzoneTerrain terrain){this.terrain=terrain;}
    public Blueprint blueprint(ZoneDiscoveries.Spot spot){return cache.computeIfAbsent(spot.id(),i->new Builder(terrain,spot).build());}
    public List<Blueprint> blueprints(){return terrain.zonePlan().discoveries().spots().stream().map(this::blueprint).toList();}
    void generate(ChunkWriter w){for(var s:terrain.zonePlan().discoveries().spots())if(w.intersects(s.x()-s.radius()-1,s.z()-s.radius()-1,s.x()+s.radius()+1,s.z()+s.radius()+1))BlueprintBlitter.blit(blueprint(s),w);}
    private static final class Builder {
        final WarzoneTerrain t;final ZoneDiscoveries.Spot s;final Blueprint bp;
        Builder(WarzoneTerrain t,ZoneDiscoveries.Spot s){this.t=t;this.s=s;bp=new Blueprint("discovery_"+s.id()+"_"+s.kind(),s.x()-s.radius()-2,s.y()-14,s.z()-s.radius()-2,s.radius()*2+5,56,s.radius()*2+5);}
        int x(int u,int v){u*=s.variant()/8%2==0?1:-1;return s.x()+switch(s.rotation()){case 1->-v;case 2->-u;case 3->v;default->u;};}
        int z(int u,int v){u*=s.variant()/8%2==0?1:-1;return s.z()+switch(s.rotation()){case 1->u;case 2->-v;case 3->-u;default->v;};}
        void p(int u,int h,int v,String id){bp.set(x(u,v),s.y()+h,z(u,v),BpSpec.of(id));}
        void b(int u,int h,int v,BpSpec spec){bp.set(x(u,v),s.y()+h,z(u,v),spec);}
        void loot(int u,int h,int v,String name){bp.addBlockEntity(new Blueprint.BlockEntity(x(u,v),s.y()+h,z(u,v),Blueprint.Kind.BARREL,"up","bobin_craft:chests/"+name,WarzoneNoise.hash(t.seed,x(u,v),z(u,v)),false,false),BpSpec.of("barrel").with("facing","up"));}
        void floor(int hw,int hd,String material){for(int u=-hw;u<=hw;u++)for(int v=-hd;v<=hd;v++){
            for(int h=-5;h<0;h++)p(u,h,v,"stone");p(u,0,v,material);for(int h=1;h<=12;h++)p(u,h,v,"air");}}
        void ruin(int hw,int hd,int height){hw+=s.variant()/8%2;floor(hw,hd,"bobin_craft:mossy_concrete");
            for(int u=-hw;u<=hw;u++)for(int v=-hd;v<=hd;v++)if(Math.abs(u)==hw||Math.abs(v)==hd){
                int top=1+Math.floorMod(u*17+v*31+s.variant()*7,height);
                for(int h=1;h<=top;h++)if(!(v==-hd&&Math.abs(u)<=1))p(u,h,v,h==1?"mossy_stone_bricks":u%3==0?"bobin_craft:cracked_concrete":"bricks");}}
        void tent(int cx,int cz,int hw,int hd,String cloth){for(int u=-hw;u<=hw;u++)for(int v=-hd;v<=hd;v++){
            int h=2+hw-Math.abs(u);p(cx+u,h,cz+v,cloth);if(Math.abs(v)==hd&&u!=0)for(int y=1;y<h;y++)p(cx+u,y,cz+v,cloth);
            if(u==0&&Math.abs(v)==hd)for(int y=1;y<h;y++)p(cx,y,cz+v,"spruce_fence");}}
        void ridge(){
            int cx=s.x(),cz=s.z(),g=t.height(cx,cz);
            Earthworks.trench(t,bp,cx-7,cz+2,24,true);
            Earthworks.dugout(t,bp,cx-8,cz-7,g-1,true);
            int nx=cx-7,nz=cz+1,ny=g+3;Earthworks.net(bp,nx,ny,nz,10,3);
            for(int dx:new int[]{-10,10})for(int dz:new int[]{-3,3})for(int y=t.height(nx+dx,nz+dz)-2;y<=ny;y++)bp.set(nx+dx,y,nz+dz,BpSpec.of("bobin_craft:weathered_bark"));
            if(s.kind()==ZoneDiscoveries.Kind.RIDGE_RELAY){
                int mx=cx+11,mz=cz-9,base=t.height(mx,mz);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=Math.min(base,t.height(mx+dx,mz+dz))-2;y<=base;y++)bp.set(mx+dx,y,mz+dz,BpSpec.of("bobin_craft:cracked_concrete"));
                for(int h=1;h<=16;h++)bp.set(mx,base+h,mz,BpSpec.of("iron_bars"));
                for(int u=-3;u<=3;u++)bp.set(mx+u,base+13,mz,BpSpec.of("iron_bars"));
                bp.set(mx+1,base+1,mz,BpSpec.of("bobin_craft:equipment_panel"));
            }else{
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                    int px=cx+11+dx,pz=cz-8+dz,base=t.height(px,pz);bp.set(px,base+1,pz,BpSpec.of((dx+dz)%2==0?"bobin_craft:ammo_crate":"bobin_craft:supply_crate"));}
                // Place the field table inside the covered supply bay rather than exposed in the open approach.
                int px=cx+9,pz=cz-6,base=t.height(px,pz);bp.set(px,base+1,pz,BpSpec.of("bobin_craft:field_table"));
            }
        }
        Blueprint build(){switch(s.kind()){
            case BURNT_GARAGE->{ruin(7,6,5);for(int u=-6;u<=1;u++)for(int v=-6;v<=6;v++)if((u+v+s.variant())%5!=0)p(u,5,v,"bobin_craft:rusted_sheet_metal");
                for(int u=-5;u<=5;u+=5){p(u,1,4,"smithing_table");p(u,2,4,"grindstone");}p(-4,1,0,"anvil");p(3,1,2,"black_concrete");loot(5,1,4,"warzone_technical");}
            case CHAPEL_RUIN->{ruin(6,9,7);for(int h=1;h<=13;h++)for(int u=-2;u<=2;u++)for(int v=4;v<=8;v++)if(Math.abs(u)==2||v==4||v==8)p(u,h,v,h<3?"mossy_stone_bricks":"bricks");
                for(int u=-2;u<=2;u++)p(u,11,6,"dark_oak_fence");for(int h=9;h<=14;h++)p(0,h,6,"dark_oak_fence");
                for(int v=-5;v<=1;v+=3)for(int u:new int[]{-4,3})for(int a=0;a<2;a++)b(u+a,1,v,BpSpec.of("spruce_stairs").with("facing","south"));loot(-4,1,7,"warzone_civilian");}
            case RADIO_SHACK->{ruin(5,5,4);for(int u=-5;u<=5;u++)for(int v=-5;v<=5;v++)p(u,4,v,u>1?"bobin_craft:tarp_olive":"bobin_craft:rusted_sheet_metal");
                p(-3,1,2,"bobin_craft:radio");p(2,1,2,"bobin_craft:field_table");for(int h=1;h<=19;h++){p(10,h,0,"iron_bars");if(h%4==0)p(11,h,0,"iron_bars");}for(int u=7;u<=13;u++)p(u,16,0,"iron_bars");loot(3,1,-3,"warzone_command");}
            case HUNTERS_CAMP->{floor(7,6,"bobin_craft:trampled_mud");tent(-3,0,3,4,"bobin_craft:tarp_olive");
                for(int u=3;u<=6;u++)p(u,1,3,"bobin_craft:field_bench");p(5,1,-2,"campfire");loot(0,1,4,"warzone_civilian");p(-4,1,1,"hay_block");}
            case FIELD_HOSPITAL->{floor(10,7,"bobin_craft:dark_forest_soil");tent(-4,0,4,6,"bobin_craft:tarp_olive");tent(6,1,3,4,"bobin_craft:tarp_blue");
                for(int v=-3;v<=3;v+=3){b(-6,1,v,BpSpec.of("white_bed").with("part","foot").with("facing","south"));b(-6,1,v+1,BpSpec.of("white_bed").with("part","head").with("facing","south"));}
                p(6,1,2,"water_cauldron");p(5,1,0,"bobin_craft:field_table");loot(-2,1,4,"warzone_medical");p(8,1,3,"bobin_craft:ammo_crate");}
            case WATER_TOWER->{floor(6,6,"bobin_craft:dark_forest_soil");
                // A narrower tank with a tapered bottom, domed crown, service deck and visible bracing.
                for(int u:new int[]{-3,3})for(int v:new int[]{-3,3})for(int h=0;h<=11;h++)p(u,h,v,h<2?"bobin_craft:cracked_concrete":"bobin_craft:rusted_sheet_metal");
                for(int h:new int[]{4,8,11})for(int a=-3;a<=3;a++){p(a,h,-3,"iron_bars");p(a,h,3,"iron_bars");p(-3,h,a,"iron_bars");p(3,h,a,"iron_bars");}
                for(int i=0;i<=6;i++)for(int side:new int[]{-3,3}){p(i-3,3+i/2,side,"iron_bars");p(side,7+i/2,i-3,"iron_bars");}
                for(int u=-4;u<=4;u++)for(int v=-4;v<=4;v++)if(u*u+v*v<=24){b(u,11,v,BpSpec.of("dark_oak_slab").with("type","top"));if(u*u+v*v>=16)p(u,12,v,"iron_bars");}
                for(int h=12;h<=18;h++){int r=h==12?1:h==13||h==18?2:3;
                    for(int u=-r;u<=r;u++)for(int v=-r;v<=r;v++)if(u*u+v*v<=r*r+1)p(u,h,v,h==18||h==12||u*u+v*v>=(r-1)*(r-1)?h%3==0?"gray_terracotta":"bobin_craft:rusted_sheet_metal":"air");}
                for(int h=1;h<=11;h++)b(-3,h,-4,BpSpec.of("ladder").with("facing","north"));p(0,1,0,"water_cauldron");loot(2,1,0,"warzone_technical");}
            case SUPPLY_CACHE->{floor(5,4,"bobin_craft:dark_forest_soil");for(int u=-5;u<=5;u++)for(int v=-4;v<=4;v++)if(v==4||Math.abs(u)==5)for(int h=1;h<=2;h++)p(u,h,v,"bobin_craft:sand_bags");
                for(int u=-4;u<=4;u++)for(int v=0;v<=4;v++)p(u,4,v,"bobin_craft:camouflage_net");for(int u:new int[]{-4,4})for(int h=1;h<=3;h++)p(u,h,4,"spruce_fence");
                loot(-3,1,2,"warzone_supply");loot(2,1,2,"warzone_technical");p(0,1,2,"bobin_craft:ammo_crate");}
            case QUARRY_SHELTER->{ruin(7,5,4);tent(2,0,3,4,"bobin_craft:rusted_sheet_metal");p(-5,1,2,"stonecutter");p(-3,1,2,"blast_furnace");
                for(int u=-10;u<=-7;u++)for(int v=-4;v<=1;v++)p(u,1,v,"mossy_cobblestone");loot(4,1,2,"warzone_technical");}
            case RIDGE_RELAY,TRENCH_DEPOT->ridge();
        }
            // Variant-specific supplies and a broken perimeter fragment distinguish repeated uses.
            int side=s.variant()%2==0?-1:1;for(int v=5;v<=9;v++)if(Math.floorMod(v+s.variant(),3)>0){int u=side*12,g=t.height(x(u,v),z(u,v));bp.set(x(u,v),g+1,z(u,v),BpSpec.of("mossy_cobblestone_wall"));}
            bp.connect((x,z)->t.height(x,z));if(bp.droppedWrites!=0)throw new IllegalStateException("Discovery outside bounds "+s+": "+bp.droppedWrites);return bp;
        }
    }
}
