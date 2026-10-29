package com.bobincraft.destruction;

import com.bobincraft.combat.*;
import com.bobincraft.drone.DroneBlastType;
import java.util.*;

/** Executes production kernels with Java 17, also called by JUnit; not a substitute for Fabric runtime. */
public final class StabilizationContract {
    private static int checks;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private static long pos(int x,int y,int z){return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|((long)y&0xfffL);}
    public static void runAll() {
        checks=0;
        String[] forbidden={"hand_grenade","drone_payload","heavy_drone_payload"};
        String[] allowed={"armor_shell","fixed_wing_strike","rpg_projectile","kamikaze_drone","demolition_charge","fire_tick","admin_command","generic_explosion"};
        for(String cat:new String[]{"BRIDGE_SECTION","REFINERY_TANK","SUBSTATION_TRANSFORMER","SUBSTATION_EQUIPMENT"}) {
            for(String source:forbidden)check(StructureDamagePolicy.allows(source,cat)==cat.startsWith("SUBSTATION"),"source/category policy "+cat+source);
            for(String source:allowed)check(StructureDamagePolicy.allows(source,cat),"allowed heavy/source preserved");
        }
        for(int age=0;age<=600;age++) {
            float light=IndustrialSmokeColor.warmth(age/600.,age/25.);
            for(int repeat=0;repeat<20;repeat++) {
                float red=IndustrialSmokeColor.channel(.15f,light),green=IndustrialSmokeColor.channel(.13f,light*.25f);
                check(red>=.14999&&red<=.251&&green>=.12999&&green<=.156,"bounded immutable-base illumination");
                check(red==IndustrialSmokeColor.channel(.15f,light),"no accumulation between repeated updates");
            }
        }
        for(var p:ExplosionVisualProfile.values())for(int budget:new int[]{0,1,3,12,64}) {
            var a=WeaponExplosionLayers.create(p,277,budget);
            check(a.size()<=budget&&a.equals(WeaponExplosionLayers.create(p,277,budget)),"choreography bound/determinism");
            for(var layer:a)check(layer.delayTicks()>=0&&layer.delayTicks()<=20,"bounded phase delay");
        }
        var fp=WeaponExplosionLayers.create(ExplosionVisualProfile.FP1_STRIKE,7,64);
        var gr=WeaponExplosionLayers.create(ExplosionVisualProfile.GROM7_DEMOLITION,7,64);
        check(fp.stream().anyMatch(l->l.delayTicks()>=14&&l.puff().y()>5),"FP-1 separate elevated late cloud");
        check(gr.stream().allMatch(l->l.delayTicks()<8&&l.puff().y()<1),"GROM local short demolition crown");
        check(fp.stream().anyMatch(l->l.kind()==WeaponExplosionLayers.Kind.DUST&&l.puff().vx()>.2),"FP-1 pressure skirt");
        for(int charges:new int[]{1,3,10}) {
            var mixer=new ExplosionSoundMixer<String>(128);
            for(int i=0;i<charges;i++)for(int layer=0;layer<7;layer++)
                check(mixer.offer("zone",i*.1,64,0,"layer"+layer,1,.9f,100,layer==0?0:layer*4),"audio submitted");
            var out=new ArrayList<ExplosionSoundMixer.Emission<String>>();
            for(int t=100;t<140;t++)out.addAll(mixer.drain("zone",t));
            check(out.size()==7,"cluster preserves one complete seven-layer identity");
            for(var e:out)check(e.volume()>=1&&e.volume()<=1.35f,"sublinear bounded auditory gain");
            if(charges==1)check(out.stream().allMatch(e->e.volume()==1),"single charge exact gain");
            check(mixer.size()==0,"finished sounds removed");
        }
        // Actual GROM-7 sympathetic charges fire two ticks after the initiating charge.
        // Drain between events: a simultaneous-only test misses already-played duplicate onsets.
        for(int charges:new int[]{2,3,10}) {
            var chainMixer=new ExplosionSoundMixer<String>(128);
            var chainVoices=new ArrayList<ExplosionSoundMixer.Emission<String>>();
            int[] delays={0,0,0,5,6,14,22};
            for(int tick=100;tick<140;tick++) {
                int detonations=tick==100?1:tick==102?charges-1:0;
                for(int charge=0;charge<detonations;charge++)for(int layer=0;layer<delays.length;layer++)
                    check(chainMixer.offer("zone",charge*.1,64,0,"layer"+layer,1,.9f,tick,delays[layer]),"two-tick chain audio accepted");
                chainVoices.addAll(chainMixer.drain("zone",tick));
            }
            check(chainVoices.size()==7,"two-tick GROM chain must not replay heard onset layers; actual="+chainVoices.size());
        }

        var mixer=new ExplosionSoundMixer<String>(2);
        mixer.offer("a",0,0,0,"x",1,1,0,0);mixer.offer("a",30,0,0,"x",1,1,0,0);
        check(!mixer.offer("a",60,0,0,"x",1,1,0,0),"sound queue bounded");
        check(mixer.drain("b",10).isEmpty(),"sound dimensions isolated");
        check(mixer.drain("a",10).size()==2,"distant explosions independent");
        mixer.offer("a",0,0,0,"x",1,1,100,0);mixer.offer("a",0,0,0,"x",1,1,110,0);
        check(mixer.drain("a",200).size()==2,"sequential explosions independent");
        // Played-voice history must not hide distant, other-dimension or later detonations.
        var heardMixer=new ExplosionSoundMixer<String>(2);
        heardMixer.offer("a",0,0,0,"crack",1,1,100,0);
        var firstVoice=heardMixer.drain("a",101);
        check(firstVoice.size()==1&&firstVoice.get(0).volume()==1,"single onset unchanged");
        check(heardMixer.offer("a",0,0,0,"crack",1,1,102,0),"late matching onset accepted by auditory grouping");
        check(heardMixer.size()==0&&heardMixer.drain("a",103).isEmpty(),"late matching onset not replayed");
        heardMixer.offer("a",30,0,0,"crack",1,1,102,0);
        heardMixer.offer("b",0,0,0,"crack",1,1,102,0);
        check(heardMixer.drain("a",103).size()==1,"heard history preserves distant onset");
        check(heardMixer.drain("b",103).size()==1,"heard history preserves other dimension");
        heardMixer.offer("a",0,0,0,"crack",1,1,110,0);
        check(heardMixer.drain("a",111).size()==1,"heard history expires for later onset");
        heardMixer.clear();check(heardMixer.size()==0&&heardMixer.recentSize()==0,"world reset clears queue and played history");
        for(int i=0;i<64;i++) {
            String dim="dimension"+i;
            heardMixer.offer(dim,0,0,0,"crack",1,1,200+i*10,0);
            check(heardMixer.drain(dim,201+i*10).size()==1,"independent dimension emits once");
            check(heardMixer.recentSize()<=2,"played history bounded across inactive dimensions");
        }
        heardMixer.clear();heardMixer.offer("a",0,0,0,"crack",1,1,100,0);heardMixer.drain("a",101);
        heardMixer.offer("a",0,0,0,"crack",1,1,10,0);
        check(heardMixer.drain("a",11).size()==1,"clock rollback cannot suppress a new session onset");

        var l=new TransitionLedger();
        var target=new LinkedHashMap<Long,String>();
        for(int i=0;i<10000;i++)target.put(pos(i,100,0),"air");
        l.retarget(target);
        check(l.pending.size()==10000&&!l.hasReady(),"legacy backlog parked, not active");
        long before=l.mutations();for(int i=0;i<10000;i++)check(l.poll()==null,"idle legacy queue costs no candidates");
        check(l.mutations()==before,"idle queue does not dirty journal");
        long cell=pos(-17,77,-33);l.noteOwned(cell,"roof");l.retarget(Map.of(cell,"air"));
        check(l.poll()==cell,"provenance wakes work");l.defer(cell);
        check(!l.hasReady()&&l.deferredChunks().contains(TransitionLedger.chunk(cell)),"unloaded work parked");
        check((int)TransitionLedger.chunk(cell)==-2&&(int)(TransitionLedger.chunk(cell)>>32)==-3,"negative chunk packing");
        check(l.wakeChunk(TransitionLedger.chunk(cell),1)==1&&l.poll()==cell,"chunk event wakes exactly bounded work");
        check(l.canApply(cell,"roof"),"owned expected write");l.applied(cell);
        check(!l.canApply(cell,"air")&&!l.pending.containsKey(cell),"completion idempotent");
        l.retarget(Map.of(cell,"rust"));l.protect(cell);l.rebuildScheduling();
        check(!l.hasReady()&&l.pending.containsKey(cell),"protected target retained outside active queue");
        check(!l.canApply(cell,"air"),"same-material replacement cannot bypass protection");
        smokeAllocation();
        collision();
        System.out.println("PASS stabilization production kernels: "+checks+" checks; policy, smoke color/allocation, phase schedules, audio clusters, parked ledger, swept cosmetic collision");
    }
    private static void smokeAllocation() {
        record View(String stage, boolean industrial, boolean burning, float intensity, boolean loaded, double distance) {
            boolean emits() { return IndustrialSmokeBudget.isEmitter(industrial, burning, intensity, loaded, distance, 192); }
        }
        View refinery = new View("CRITICAL", true, true, .9f, true, 16);
        for (double distance : new double[]{0, 32, 32.01, 96, 96.01, 192}) {
            double alone = IndustrialSmokeBudget.particlesPerSecond(distance, .9f, 1, 32, 96, 192, 8, 2, 200);
            var views = new ArrayList<View>(); views.add(refinery);
            for (int added = 0; added <= 256; added++) {
                if (added > 0) views.add(new View(added % 2 == 0 ? "DAMAGED" : "CRITICAL", true, false, .9f, true, 16));
                int emitters = (int) views.stream().filter(View::emits).count();
                check(emitters == 1, "non-burning damaged/critical structures consume no refinery smoke allocation");
                check(IndustrialSmokeBudget.particlesPerSecond(distance, .9f, emitters, 32, 96, 192, 8, 2, 200) == alone,
                        "burning refinery rate unchanged after adding non-burning damaged structures at every LOD");
            }
        }
        for (View excluded : List.of(new View("INTACT",true,false,1,true,16),
                new View("CRITICAL",true,true,0,true,16), new View("CRITICAL",true,true,1,false,16),
                new View("CRITICAL",true,true,1,true,192.01), new View("CRITICAL",false,true,1,true,16),
                new View("CRITICAL",true,true,1,true,Double.NaN)))
            check(!excluded.emits(), "only eligible active industrial emitters share allocation");
        check(new View("CRITICAL",true,true,1,true,192).emits(), "existing inclusive far LOD boundary preserved");
        check(IndustrialSmokeBudget.particlesPerSecond(16,1,8,32,96,192,8,2,200)==6.25, "multiple real fires retain global cap scaling");
        check(IndustrialSmokeBudget.particlesPerSecond(16,1,1,32,96,192,8,2,200)==8, "near appearance rate preserved");
        check(IndustrialSmokeBudget.particlesPerSecond(64,1,1,32,96,192,8,2,200)==2, "medium LOD rate preserved");
        check(IndustrialSmokeBudget.particlesPerSecond(128,1,1,32,96,192,8,2,200)==.65, "far LOD rate preserved");
        check(IndustrialSmokeBudget.particlesPerSecond(193,1,1,32,96,192,8,2,200)==0, "outside far LOD emits nothing");
    }

    private static void collision() {
        var floor=new TransientDebrisCollision.Bounds(-10,0,-10,10,.5,10);
        var wall=new TransientDebrisCollision.Bounds(1,-2,-10,1.1,10,10);
        var ceiling=new TransientDebrisCollision.Bounds(-10,2,-10,10,2.25,10);
        for(int i=1;i<=100;i++) {
            double r=.015+i*.001;
            var box=new TransientDebrisCollision.Bounds(-r,1-r,-r,r,1+r,r);
            var down=TransientDebrisCollision.move(box,0,-4,0,List.of(floor));
            check(down.supported()&&Math.abs(box.y0()+down.y()-.5)<1e-8,"swept floor/slab landing, no tunneling");
            var side=TransientDebrisCollision.move(box,4,0,0,List.of(wall));
            check(Math.abs(box.x1()+side.x()-1)<1e-8,"thin lateral wall clips entire volume");
            var up=TransientDebrisCollision.move(box,0,4,0,List.of(ceiling));
            check(!up.supported()&&Math.abs(box.y1()+up.y()-2)<1e-8,"overhead hit never becomes support");
            var noFloor=TransientDebrisCollision.move(box,0,-.03,0,List.of());
            check(!noFloor.supported()&&noFloor.y()==-.03,"support loss resumes movement without teleport");
            var corner=TransientDebrisCollision.move(box,4,-4,4,List.of(floor,wall,ceiling));
            check(!box.offset(corner.x(),corner.y(),corner.z()).overlaps(wall),"diagonal volume does not enter wall");
        }
    }
    public static void main(String[] args){runAll();}
}
