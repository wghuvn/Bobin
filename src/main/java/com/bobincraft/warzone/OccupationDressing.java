package com.bobincraft.warzone;

/** Five checkpoint plans use actual road obstacles, defended flanks and occupied underground shelters. */
final class OccupationDressing {
    private final WarzoneTerrain t;private final ZonePlan.Site s;private final Blueprint bp;private final int cx,cz,group;
    OccupationDressing(WarzoneTerrain t,ZonePlan.Site s,Blueprint bp){this.t=t;this.s=s;this.bp=bp;cx=s.x();cz=s.entrance().z()-8;group=(s.id()-1)%3;}
    private void put(int x,int y,int z,String id){if(bp.inBounds(x,y,z))bp.set(x,y,z,BpSpec.of(id));}
    private void at(int u,int h,int v,String id){int x=cx+u,z=cz+v;put(x,t.height(x,z)+h,z,id);}
    private void barricade(int from,int to,int v,String material,int height){for(int u=from;u<=to;u++)for(int h=1;h<=height;h++)at(u,h,v,material);}
    void build(){
        if(s.id()>=100){if(s.id()%3==0){var b=s.buildings().get(0);int x=b.wx(-b.hw()+1,-b.hd()-1),z=b.wz(-b.hw()+1,-b.hd()-1);put(x,t.height(x,z)+1,z,"bobin_craft:ammo_crate");}return;}
        switch(OccupationPlan.style(s)){
            case CHICANE->{barricade(-6,-1,2,"bobin_craft:cracked_concrete",1);barricade(2,6,-5,"bobin_craft:burlap_sandbags",1);shelter(-11,-5,3,2,"bobin_craft:rusted_sheet_metal");at(-6,2,2,"bobin_craft:warning_sign");}
            case LOGGING_BARRIER->{for(int u=-7;u<=-1;u++)for(int h=1;h<=1;h++){
                    int x=cx+u,z=cz+2;if(bp.inBounds(x,t.height(x,z)+h,z))bp.set(x,t.height(x,z)+h,z,BpSpec.of("spruce_log").with("axis","x"));}
                barricade(3,7,-5,"bobin_craft:rusted_sheet_metal",2);shelter(-12,-6,4,3,"bobin_craft:tarp_olive");at(-7,2,2,"bobin_craft:warning_sign");}
            case CONCRETE_GATE->{barricade(-7,-2,1,"bobin_craft:mossy_concrete",1);barricade(2,7,-6,"bobin_craft:cracked_concrete",1);
                bunker(-12,-6);for(int u=-7;u<=7;u++)at(u,5,1,"bobin_craft:anti_drone_net");for(int u:new int[]{-7,7})for(int h=1;h<=5;h++)at(u,h,1,"spruce_fence");}
            case FLOOD_CONTROL->{barricade(-6,-2,2,"bobin_craft:burlap_sandbags",1);barricade(2,6,-5,"mossy_stone_bricks",1);shelter(-12,-5,3,3,"bobin_craft:tarp_blue");at(-11,1,-4,"water_cauldron");at(-7,1,-3,"bobin_craft:utility_pipe");}
            case CARGO_INSPECTION->{barricade(-7,-2,2,"bobin_craft:rusted_sheet_metal",2);barricade(3,7,-6,"bobin_craft:cracked_concrete",1);shelter(-12,-6,4,2,"bobin_craft:container_green");
                for(int u=-7;u<=-5;u++)for(int v=-5;v<=-4;v++)at(u,1,v,"bobin_craft:supply_crate");}
        }
        // Unique supply placement and asymmetric firing cover; the approach retains a navigable chicane.
        int sx=cx-9,sz=cz+4,sy=t.height(sx,sz);for(int h=1;h<=3;h++)put(sx,sy+h,sz,"spruce_fence");
        bp.set(sx,sy+3,sz,BpSpec.of("bobin_craft:faction_marker").with("variant",Integer.toString(group)).with("facing","south"));
        at(-8,1,-1,"bobin_craft:ammo_crate");at(7,1,3,"bobin_craft:warning_sign");
        var trench=OccupationPlan.trench(s);
        if(trench!=null){int tx=trench.x(),tz=trench.z();int floor=Earthworks.trench(t,bp,tx,tz,trench.length(),false);
            if(trench.dugout()){Earthworks.dugout(t,bp,tx+7,tz-7,floor+3,true);Earthworks.connector(t,bp,tx,tx+5,tz-7,floor);}}
        var armor=OccupationPlan.armor(s);
        if(armor!=null){int x=armor.x(),z=armor.z(),ground=t.height(x,z);
            for(int dx=-4;dx<=4;dx++)for(int dz=-5;dz<=5;dz++){
                int px=x+dx,pz=z+dz;if(!bp.inBounds(px,ground+5,pz))continue;
                for(int h=1;h<=5;h++)put(px,ground+h,pz,"air");
                // Stable support without a bright square pad: weathered earth and a feathered outer edge.
                for(int y=ground-3;y<=ground;y++)put(px,y,pz,y==ground?"bobin_craft:dark_forest_soil":"dirt");
                // Earth conceals the hull; keep the barrel sweep clear of raised perimeter blocks.
            }
            // The roof is a real sheet collider, with timber supports outside the hull and the barrel arc.
            int netY=ground+(armor.buried()?4:6);Earthworks.net(bp,x,netY,z-1,4,4);
            for(int dx:new int[]{-4,4})for(int dz:new int[]{-5,3})for(int h=1;h<=netY-ground;h++)put(x+dx,ground+h,z+dz,"spruce_fence");
        }
    }
    private void shelter(int u,int v,int hw,int hd,String roof){int x=cx+u,z=cz+v,y=t.height(x,z);
        for(int a=-hw;a<=hw;a++)for(int b=-hd;b<=hd;b++){
            for(int h=Math.min(t.height(x+a,z+b),y)-2;h<=y;h++)put(x+a,h,z+b,"bobin_craft:dark_forest_soil");
            for(int h=1;h<=4;h++)put(x+a,y+h,z+b,"air");if(bp.inBounds(x+a,y+4,z+b))bp.set(x+a,y+4,z+b,BpSpec.of("bobin_craft:checkpoint_roof").with("variant",roof.contains("olive")?"1":roof.contains("blue")?"2":"0"));
            if(Math.abs(a)==hw&&Math.abs(b)==hd)for(int h=1;h<=3;h++)put(x+a,y+h,z+b,"spruce_fence");
            if(b==-hd&&Math.abs(a)>1)for(int h=1;h<=2;h++)put(x+a,y+h,z+b,group==1?"bobin_craft:rusted_sheet_metal":"dark_oak_planks");
        }
        put(x-hw+1,y+1,z,"bobin_craft:radio");put(x+hw-1,y+1,z-hd+1,"bobin_craft:supply_crate");put(x,y+1,z+hd-1,"bobin_craft:field_bench");
    }
    private void bunker(int u,int v){int x=cx+u,z=cz+v,y=t.height(x,z);for(int a=-3;a<=3;a++)for(int b=-3;b<=3;b++){
        for(int h=-2;h<=0;h++)put(x+a,y+h,z+b,"bobin_craft:cracked_concrete");for(int h=1;h<=3;h++)put(x+a,y+h,z+b,(Math.abs(a)==3||Math.abs(b)==3)?"bobin_craft:mossy_concrete":"air");
        if(Math.abs(a)==3&&hWindow(b))put(x+a,y+2,z+b,"iron_bars");put(x+a,y+4,z+b,"bobin_craft:dark_forest_soil");}
        for(int a=-1;a<=1;a++)for(int h=1;h<=2;h++)put(x+a,y+h,z+3,"air");put(x-2,y+1,z,"bobin_craft:ammo_crate");}
    private boolean hWindow(int b){return Math.abs(b)<=1;}
}
