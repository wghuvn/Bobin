package com.bobincraft.warzone;

import static com.bobincraft.warzone.ZonePlan.*;

/** Scaled industrial arrangements: storage -> pumps -> process -> dispatch, and incoming -> switching -> transformers -> feeders. */
final class IndustrialFacilities {
    private final WarzoneTerrain t;private final Site s;private final Blueprint bp;private final int x,z,y;
    IndustrialFacilities(WarzoneTerrain t,Site s,Blueprint bp){this.t=t;this.s=s;this.bp=bp;x=s.x();z=s.z();y=s.y();}
    private void p(int u,int h,int v,String id){bp.set(x+u,y+h,z+v,BpSpec.of(id));}
    private void spec(int u,int h,int v,BpSpec b){bp.set(x+u,y+h,z+v,b);}
    private String age(int u,int h,int v){double n=ZoneWeathering.patch(t.seed+s.id()*43,x+u,z+v+h/4,4);return h<3||n>.57?"bobin_craft:rusted_sheet_metal":n<.2?"gray_terracotta":"light_gray_terracotta";}
    private void pad(int u,int v,int hw,int hd){for(int a=-hw;a<=hw;a++)for(int b=-hd;b<=hd;b++){
        p(u+a,0,v+b,"bobin_craft:cracked_concrete");for(int h=1;h<=3;h++)p(u+a,h,v+b,"air");}}
    private void pipe(int ax,int ay,int az,int bx,int by,int bz){
        int px=ax,py=ay,pz=az;p(px,py,pz,"bobin_craft:utility_pipe");
        while(px!=bx){px+=Integer.signum(bx-px);p(px,py,pz,"bobin_craft:utility_pipe");}
        while(pz!=bz){pz+=Integer.signum(bz-pz);p(px,py,pz,"bobin_craft:utility_pipe");}
        while(py!=by){py+=Integer.signum(by-py);p(px,py,pz,"bobin_craft:utility_pipe");}
    }
    private void bus(int ax,int ay,int az,int bx,int by,int bz){
        int px=ax,py=ay,pz=az;p(px,py,pz,"bobin_craft:busbar");
        while(px!=bx){px+=Integer.signum(bx-px);p(px,py,pz,"bobin_craft:busbar");}
        while(pz!=bz){pz+=Integer.signum(bz-pz);p(px,py,pz,"bobin_craft:busbar");}
        while(py!=by){py+=Integer.signum(by-py);p(px,py,pz,"bobin_craft:busbar");}
    }
    void refinery(){
        // Four tanks share one bund and a transfer manifold; sizes, roofs and damage differ.
        int index=0;for(int u:new int[]{22,45})for(int v:new int[]{-39,-15}){
            int radius=new int[]{8,6,7,5}[index],height=new int[]{10,8,11,9}[index];tank(u,v,radius,height,index++);
            pipe(u,2,v+radius,u,2,4);pipe(u,2,4,u,6,4);
            p(u+1,1,1,"polished_andesite");p(u+1,2,1,"hopper");spec(u+1,3,1,BpSpec.of("redstone_torch").with("lit",false));
        }
        // A supported rack, with open traffic clearance below and properly connected branches.
        for(int u=-44;u<=52;u+=12){for(int v:new int[]{1,13}){
            p(u,0,v,"bobin_craft:cracked_concrete");for(int h=1;h<=4;h++)p(u,h,v,"iron_bars");}
            for(int v=1;v<=13;v++)p(u,5,v,"bobin_craft:rusted_sheet_metal");}
        for(int v:new int[]{4,7,10})pipe(-44,6,v,52,6,v);
        // Atmospheric/vacuum columns, platforms and a heat exchanger train occupy the process strip.
        column(-3,-26,3,25);column(7,-27,2,20);
        pipe(-3,5,-22,-3,6,7);pipe(7,6,-24,7,6,10);
        for(int h:new int[]{8,16,23})platform(-3,-26,5,5,h);
        for(int h=1;h<=24;h++)spec(-7,h,-26,BpSpec.of("ladder").with("facing","west"));
        for(int v:new int[]{-8,-1}){
            horizontalVessel(-29,v,8,2);pipe(-29,2,v,-29,6,7);
            for(int u=-36;u<=-22;u+=7){p(u,1,v,"bobin_craft:equipment_panel");p(u,2,v,"hopper");}
        }
        // Furnace/stack to the rear of the process hall; service access and loading remain open.
        for(int u=-53;u<=-43;u++)for(int v=-52;v<=-43;v++)for(int h=0;h<=5;h++)
            p(u,h,v,h==0?"bobin_craft:cracked_concrete":u==-53||u==-43||v==-52||v==-43||h==5?age(u,h,v):"air");
        for(int u=-51;u<=-45;u+=3){p(u,1,-42,"blast_furnace");pipe(u,3,-43,u,3,-39);}
        for(int h=0;h<=25;h++)for(int u=-55;u<=-53;u++)for(int v=-48;v<=-46;v++)p(u,h,v,u==-54&&v==-47&&h>0?"air":h%8==0?"gray_terracotta":"bricks");
        pipe(-43,3,-43,-44,6,7);
        // Dispatch manifold beside the truck apron, fire-water standpipes and an isolated unlit flare.
        for(int u:new int[]{35,42,49}){pipe(u,1,10,u,3,20);p(u+1,1,20,"bobin_craft:equipment_panel");}
        for(int h=0;h<=24;h++)p(-62,h,53,h<3?"bobin_craft:cracked_concrete":"iron_bars");
        pipe(-44,6,10,-62,1,10);pipe(-62,1,10,-62,1,53);p(-62,25,53,"lightning_rod");
        for(int u:new int[]{-58,58}){p(u,1,0,"red_terracotta");p(u,2,0,"hopper");}
    }
    private void tank(int u,int v,int radius,int height,int index){
        for(int a=-radius-2;a<=radius+2;a++)for(int b=-radius-2;b<=radius+2;b++){
            int d=a*a+b*b;if(d>(radius+2)*(radius+2))continue;p(u+a,0,v+b,"bobin_craft:cracked_concrete");
            if(d>radius*radius)continue;
            boolean shell=d>(radius-1)*(radius-1);
            for(int h=1;h<=height;h++)p(u+a,h,v+b,h==height?age(u+a,h,v+b):shell?age(u+a,h,v+b):h<=height-2?"bobin_craft:crude_oil":"air");
        }
        for(int h=1;h<=height;h++)spec(u+radius,h,v,BpSpec.of("ladder").with("facing","east"));
        p(u,2,v+radius,"bobin_craft:utility_pipe");p(u,1,v+radius+1,"bobin_craft:equipment_panel");
        p(u,height+1,v,"hopper");if(index==1){for(int a=-2;a<=2;a++)p(u+a,height,v,"gray_terracotta");}
    }
    private void column(int u,int v,int radius,int height){
        for(int h=0;h<=height;h++)for(int a=-radius;a<=radius;a++)for(int b=-radius;b<=radius;b++)if(a*a+b*b<=radius*radius+1)
            p(u+a,h,v+b,h==0||h==height||a*a+b*b>=(radius-1)*(radius-1)?age(u+a,h,v+b):"air");
        p(u,height+1,v,"hopper");
    }
    private void platform(int u,int v,int hw,int hd,int h){for(int a=-hw;a<=hw;a++)for(int b=-hd;b<=hd;b++)if(Math.abs(a)==hw||Math.abs(b)==hd){
        spec(u+a,h,v+b,BpSpec.of("dark_oak_slab").with("type","top"));if(!(a==-hw&&b==0))p(u+a,h+1,v+b,"iron_bars");}}
    private void horizontalVessel(int u,int v,int length,int radius){
        for(int a=-length;a<=length;a++)for(int h=1;h<=radius*2+1;h++)for(int b=-radius;b<=radius;b++)if((h-radius-1)*(h-radius-1)+b*b<=radius*radius)
            p(u+a,h,v+b,Math.abs(a)==length||b*b+(h-radius-1)*(h-radius-1)>=(radius-1)*(radius-1)?age(u+a,h,v+b):"air");
        for(int a:new int[]{-length+2,length-2})for(int b=-radius;b<=radius;b++)p(u+a,1,v+b,"bobin_craft:cracked_concrete");
    }
    void substation(){
        // Incoming portal -> arresters/disconnectors -> breakers -> bus -> two transformer bays.
        // Keep central service lane (u == -4..4) clear of ground equipment pads.
        gantry(-29,11);gantry(16,11);
        int[] phases=new int[]{6,12,18};
        for(int phase:phases){
            for(int v:new int[]{-23,-17,-9}){
                pad(phase,v,2,2);p(phase,1,v,"bobin_craft:cracked_concrete");p(phase,2,v,"gray_terracotta");
                for(int h=3;h<=5;h++)p(phase,h,v,"bobin_craft:insulator");
                if(v==-9){p(phase-1,1,v,"bobin_craft:equipment_panel");p(phase+1,1,v,"gray_terracotta");}
                bus(phase,6,v,phase,9,v);
            }
            bus(phase,9,-29,phase,9,-5);
            for(int h=8;h<=10;h++)p(phase,h,-29,"bobin_craft:insulator");
        }
        transformer(-13,5);transformer(13,5);
        // Short, separated jumpers; no copper pipes masquerading as unsupported power lines.
        for(int side:new int[]{-1,1})for(int idx=0;idx<3;idx++){
            int phase=idx-1;
            int u=side*13+phase*2;bus(u,8,5,u,9,-5);bus(u,9,-5,phases[idx],9,-5);
            bus(u,8,7,u,10,16);
        }
        for(int idx=0;idx<3;idx++){
            int phase=idx-1;
            int u=-7+phase*2;bus(u,11,16,u,14,16);bus(u,11,16,phases[idx],11,16);
        }
        // Recessed cable trenches connect the switching cubicles and control house.
        for(int u=-28;u<=29;u++)if(Math.abs(u)>3)spec(u,0,20,BpSpec.of("dark_oak_slab").with("type","top"));
        for(int v=-25;v<=20;v++)spec(-23,0,v,BpSpec.of("dark_oak_slab").with("type","top"));
        for(int u:new int[]{-20,20}){p(u,1,20,"bobin_craft:equipment_panel");p(u,2,20,"bobin_craft:rusted_sheet_metal");}
        for(int side:new int[]{-1,1})for(int i=-43;i<=43;i++){
            fence(side*43,i);if(side<0||Math.abs(i)>5)fence(i,side*43);
        }
        for(int h=1;h<=19;h++)p(37,h,-38,h<2?"bobin_craft:cracked_concrete":"iron_bars");p(37,20,-38,"lightning_rod");
    }
    private void gantry(int v,int height){
        for(int u:new int[]{-19,19}){p(u,0,v,"bobin_craft:cracked_concrete");for(int h=1;h<=height;h++){p(u,h,v,"iron_bars");if(h%4==0)p(u+1,h,v,"iron_bars");}}
        for(int u=-19;u<=19;u++)p(u,height,v,"iron_bars");
    }
    private void transformer(int u,int v){
        pad(u,v,6,7);
        for(int a=-3;a<=3;a++)for(int b=-4;b<=4;b++)for(int h=1;h<=5;h++)p(u+a,h,v+b,h==1?"bobin_craft:cracked_concrete":h==5?"bobin_craft:rusted_sheet_metal":ZoneWeathering.patch(t.seed,x+u+a,z+v+b,3)>.72?"brown_terracotta":"green_terracotta");
        for(int side:new int[]{-4,4})for(int b=-4;b<=4;b+=2){p(u+side,1,v+b,"bobin_craft:cracked_concrete");for(int h=2;h<=4;h++)p(u+side,h,v+b,"iron_bars");}
        for(int a:new int[]{-2,0,2}){p(u+a,6,v,"bobin_craft:insulator");p(u+a,7,v,"bobin_craft:insulator");p(u+a,6,v+2,"bobin_craft:insulator");p(u+a,7,v+2,"bobin_craft:insulator");}
        p(u,6,v-4,"hopper");p(u-4,1,v-5,"bobin_craft:equipment_panel");
        for(int a=-5;a<=5;a++)for(int b=-6;b<=6;b++)if(Math.abs(a)==5||Math.abs(b)==6)spec(u+a,1,v+b,BpSpec.of("cobblestone_slab"));
    }
    private void fence(int u,int v){int ground=t.height(x+u,z+v)-y;for(int h=1;h<=3;h++)p(u,ground+h,v,"iron_bars");if(Math.floorMod(u+v,8)==0)for(int h=0;h<=4;h++)p(u,ground+h,v,"bobin_craft:cracked_concrete");}
}
