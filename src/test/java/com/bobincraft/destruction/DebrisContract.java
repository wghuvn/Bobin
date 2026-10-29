package com.bobincraft.destruction;

import com.bobincraft.destruction.debris.*;
import java.util.*;
import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Actual production planner/rigid solver/support index. Engine IO/network adapters are covered separately by GameTest. */
public final class DebrisContract {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static DebrisFragment fragment(int x,int y,int z) {
        var f=DebrisPlan.create("test:zone","tank",UUID.fromString("11111111-2222-3333-4444-555555555555"),List.of(new DebrisPlan.Source(x,y,z,"minecraft:stone"))).get(0);
        var c=f.cells.get(0);c.commit=Commit.REMOVED;c.shapes=List.of(new B(c.local.x(),c.local.y(),c.local.z(),c.local.x()+1,c.local.y()+1,c.local.z()+1));f.phase=Phase.FALLING;f.reconciled=true;return f;
    }
    private static void journal() {
        java.nio.file.Path directory=null;
        try {
            directory=java.nio.file.Files.createTempDirectory("bobin-real-journal-contract-");var wal=directory.resolve("records.wal");var snapshot=directory.resolve("records.dat");
            byte[] first="durable source intent".getBytes(java.nio.charset.StandardCharsets.UTF_8),second="committed geometry/pose".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            long end=DebrisJournal.append(wal,0,1,first);end=DebrisJournal.append(wal,end,2,second);var read=DebrisJournal.read(wal);
            check(read.frames().size()==2&&java.util.Arrays.equals(read.frames().get(0).payload(),first),"actual fsynced WAL read reproduces durable intent");
            byte[] all=java.nio.file.Files.readAllBytes(wal);
            for(int cut=1;cut<24+second.length;cut++) {
                java.nio.file.Files.write(wal,java.util.Arrays.copyOf(all,all.length-cut));var partial=DebrisJournal.read(wal);
                check(partial.frames().size()==1&&partial.validBytes()==24+first.length,"every incomplete trailing frame preserves valid prefix");
                DebrisJournal.append(wal,partial.validBytes(),2,second);check(DebrisJournal.read(wal).frames().size()==2,"retry truncates incomplete tail without duplicate frame");
            }
            byte[] corrupt=all.clone();corrupt[corrupt.length-1]^=1;java.nio.file.Files.write(wal,corrupt);boolean checksum=false;
            try{DebrisJournal.read(wal);}catch(java.io.IOException expected){checksum=true;}check(checksum&&java.util.Arrays.equals(corrupt,java.nio.file.Files.readAllBytes(wal)),"corruption rejected without rewriting evidence");
            java.nio.file.Files.write(wal,all);DebrisJournal.atomicSnapshot(snapshot,second);
            check(java.util.Arrays.equals(second,java.nio.file.Files.readAllBytes(snapshot)),"actual atomic snapshot replacement");
            check(DebrisJournal.read(wal).frames().stream().filter(frame->frame.sequence()>2).count()==0,"checkpoint sequence suppresses old WAL replay if crash precedes truncation");
            DebrisJournal.truncate(wal);check(DebrisJournal.read(wal).frames().isEmpty(),"checkpoint permits empty WAL");
            DebrisJournal.append(wal,0,3,first);check(DebrisJournal.read(wal).frames().get(0).sequence()==3,"post-checkpoint sequence does not restart");
            var file=directory.resolve("not_a_directory");java.nio.file.Files.writeString(file,"barrier failure fixture");boolean blocked=false;
            try{DebrisJournal.append(file.resolve("records.wal"),0,1,first);}catch(java.io.IOException expected){blocked=true;}check(blocked,"actual failed filesystem barrier blocks detachment");
        }catch(java.io.IOException e){throw new AssertionError(e);}finally{if(directory!=null)try(var paths=java.nio.file.Files.walk(directory)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(path);}catch(java.io.IOException e){throw new AssertionError(e);}}
    }
    public static void main(String[] args) { runAll(); }
    public static void runAll() {
        checks=0;UUID op=UUID.fromString("11111111-2222-3333-4444-555555555555");
        var input=new ArrayList<DebrisPlan.Source>();
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)if(x*x+z*z<=81)for(int y=1;y<=11;y++)
            if(y==11||x*x+z*z>64)input.add(new DebrisPlan.Source(x,y,z,y==11?"test:roof[variant=old]":"test:shell[variant=aged]"));
        var pieces=DebrisPlan.create("test:zone","tank",op,input);Set<Long> cells=new HashSet<>();Set<UUID> ids=new HashSet<>();
        for(var f:pieces){check(ids.add(f.id),"stable unique fragment identity");check(f.cells.size()<=16,"bounded recognizable patches");
            double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
            for(var c:f.cells){check(cells.add(c.source),"no duplicated source cell");check(!f.visible(),"reservation cannot reveal unremoved geometry");
                check(c.state.startsWith("test:"),"exact source material/property preserved");lo=Math.min(lo,c.local.y());hi=Math.max(hi,c.local.y()+1);
                double wx=f.sourcePose.x()+c.local.x(),wy=f.sourcePose.y()+c.local.y(),wz=f.sourcePose.z()+c.local.z();
                check(Math.abs(wx-Math.rint(wx))<1e-9&&Math.abs(wy-Math.rint(wy))<1e-9&&Math.abs(wz-Math.rint(wz))<1e-9,"exact initial world placement, no scale or scatter");}
            check(hi-lo<=2.00001,"no generated tall column from unrelated animation cube dimensions");}
        check(cells.size()==input.size(),"every actual source constituent conserved once");
        Collections.shuffle(input,new Random(42));var again=DebrisPlan.create("test:zone","tank",op,input);
        check(pieces.stream().map(f->f.id).toList().equals(again.stream().map(f->f.id).toList()),"planner independent of input iteration");
        check(!pieces.get(0).id.equals(DebrisPlan.create("test:other","tank",op,input).get(0).id),"dimension identity separate");
        var floor=new DebrisCollision.Obstacle(new B(-100,0,-100,100,.5,100),1);
        var wall=new DebrisCollision.Obstacle(new B(2,-100,-100,2.1,100,100),2);
        var ceiling=new DebrisCollision.Obstacle(new B(-100,5,-100,100,5.25,100),3);
        for(int speed=1;speed<=15;speed++) {
            var f=fragment(0,2,0);f.velocity=new V(speed/10.,-speed/10.,0);
            for(int tick=0;tick<100;tick++){DebrisCollision.tick(f,b->List.of(floor,wall,ceiling));check(f.bounds().x1()<=2.000001&&f.bounds().y0()>=.499999,"swept whole-shape side/floor collision");}
            check(f.phase==Phase.SLEEPING,"verified finite settling on slab");
            double prior=f.position.y();DebrisCollision.tick(f,b->List.of(wall,ceiling));check(f.phase==Phase.FALLING&&f.position.y()<prior,"support loss resumes falling from existing pose");
        }
        var up=fragment(0,3,0);up.velocity=new V(0,1.5,0);DebrisCollision.tick(up,b->List.of(ceiling));check(up.bounds().y1()<=5.000001&&up.phase!=Phase.SLEEPING,"overhead contact never becomes floor");
        var fall=fragment(0,12,0);for(int tick=0;tick<100;tick++)DebrisCollision.tick(fall,b->List.of(new DebrisCollision.Obstacle(new B(-10,-20,-10,10,-19,10),9)));
        check(fall.position.y()<0,"deep water has no guessed sea-level floor");
        var unloaded=fragment(0,6,0);unloaded.velocity=new V(.1,-.7,0);V pose=unloaded.position;Q rotation=unloaded.rotation;
        DebrisCollision.tick(unloaded,b->null);check(unloaded.phase==Phase.PAUSED_UNLOADED&&unloaded.resume==Phase.FALLING&&unloaded.position.equals(pose)&&unloaded.rotation.equals(rotation),"unavailable swept volume pauses exact pose");
        DebrisCollision.tick(unloaded,b->List.of());check(unloaded.phase==Phase.FALLING&&unloaded.position.y()<pose.y(),"loaded collision context resumes without floor teleport");
        var rotating=fragment(0,3,0);rotating.angularVelocity=new V(.12,0,.1);
        for(int i=0;i<150;i++){DebrisCollision.tick(rotating,b->List.of(floor,wall,ceiling));for(var v:rotating.volumes(rotating.rotation,rotating.position))check(!v.intersects(wall.box())&&!v.intersects(floor.box())&&!v.intersects(ceiling.box()),"rotating volume avoids walls/floor/ceiling");}
        var shape=new B(-2,-.125,-1,2,.125,1);var q=Q.turn(new V(.7,.3,.2),1);B bounds=shape.transformed(q,new V(2,5,1));
        for(int i=0;i<5000;i++){var r=new Random(i);V p=q.rotate(new V(-2+r.nextDouble()*4,-.125+r.nextDouble()*.25,-1+r.nextDouble()*2)).add(new V(2,5,1));
            check(p.x()>=bounds.x0()-1e-8&&p.x()<=bounds.x1()+1e-8&&p.y()>=bounds.y0()-1e-8&&p.y()<=bounds.y1()+1e-8&&p.z()>=bounds.z0()-1e-8&&p.z()<=bounds.z1()+1e-8,"rotated subshape encloses all source geometry");}
        var supports=new DebrisSupportIndex();UUID id=UUID.randomUUID();supports.update(id,Set.of(1L,2L));supports.changed(999);supports.changed(1);supports.changed(1);
        check(supports.drain(1).equals(Set.of(id))&&supports.drain(8).isEmpty(),"support wake deduplicated, unrelated edits fast-exit");
        supports.update(id,Set.of(3L));supports.changed(1);check(supports.drain(10).isEmpty(),"old support dependencies removed");supports.changed(3);check(supports.drain(1).contains(id),"piston/explosion/block write support notification wakes identity");
        // A multi-cell plate must tip about surviving support when its mass leaves the support polygon.
        var longPlate=DebrisPlan.create("test:zone","roof",op,List.of(new DebrisPlan.Source(0,1,0,"minecraft:stone"),new DebrisPlan.Source(1,1,0,"minecraft:stone"),new DebrisPlan.Source(2,1,0,"minecraft:stone"))).get(0);
        for(var c:longPlate.cells){c.commit=Commit.REMOVED;c.shapes=List.of(new B(c.local.x(),c.local.y(),c.local.z(),c.local.x()+1,c.local.y()+1,c.local.z()+1));}
        longPlate.phase=Phase.SLEEPING;longPlate.reconciled=true;double initialY=longPlate.position.y();
        var narrowSupport=new DebrisCollision.Obstacle(new B(0,0,0,1,1,1),77);
        for(int i=0;i<30;i++)DebrisCollision.tick(longPlate,b->List.of(narrowSupport));
        check(longPlate.phase!=Phase.SLEEPING&&longPlate.position.y()<initialY,"loss of partial support tips source plate instead of leaving a vertical frozen cluster");
        for(int i=0;i<1000;i++) {
            var random=new Random(i);B box=new B(-2,-.3,-1,2,.3,1);V omega=new V(random.nextDouble()*.1,random.nextDouble()*.1,random.nextDouble()*.1);
            V pivot=new V(1,0,0);B swept=DebrisCollision.sweptRotation(box,Q.IDENTITY,new V(0,2,0),pivot,omega,1);
            for(int j=0;j<=10;j++){Q turn=Q.turn(omega,j/10.0);V origin=pivot.add(turn.rotate(new V(0,2,0).add(pivot.mul(-1))));B at=box.transformed(turn,origin);
                check(swept.x0()<=at.x0()+1e-8&&swept.y0()<=at.y0()+1e-8&&swept.z0()<=at.z0()+1e-8&&swept.x1()>=at.x1()-1e-8&&swept.y1()>=at.y1()-1e-8&&swept.z1()>=at.z1()-1e-8,"entire angular sweep includes intermediate corner extrema");}
        }
        // A broad-phase hull corner is empty space, not physical contact/support.
        var diagonal=new DebrisShape(new B(-2,-.1,-.15,2,.1,.15),Q.turn(new V(0,Math.PI/4,0),1),V.ZERO);
        var corner=new DebrisShape(new B(1.1,-.1,1.1,1.4,.1,1.4),Q.IDENTITY,V.ZERO);
        check(diagonal.bounds.intersects(corner.bounds)&&diagonal.penetration(corner)==null,"rotated broad hull overlap cannot manufacture support");
        check(diagonal.sweep(corner,new V(0,-.01,0))==null,"empty rotated corner cannot block falling");
        var embedded=fragment(0,0,0);embedded.position=embedded.position.add(new V(0,-.03,0));
        var contactFloor=new DebrisCollision.Obstacle(new B(-10,-1,-10,10,0,10),88);
        DebrisCollision.tick(embedded,b->List.of(contactFloor));
        check(embedded.bounds().y0()>=-1e-6&&embedded.phase!=Phase.PAUSED_UNLOADED,"bounded collision-normal initial-overlap recovery, no guessed floor");
        for(int i=0;i<40;i++)DebrisCollision.tick(embedded,b->List.of(contactFloor));
        check(embedded.phase==Phase.SLEEPING,"recovered body completes normal sleeping lifecycle");
        var imprisoned=fragment(0,0,0);V prisonPose=imprisoned.position;imprisoned.velocity=new V(.1,-.2,0);
        var enclosing=new DebrisCollision.Obstacle(new B(-10,-10,-10,10,10,10),99);
        DebrisCollision.tick(imprisoned,b->List.of(enclosing));
        check(imprisoned.phase==Phase.PAUSED_UNLOADED&&imprisoned.pauseReason.equals("OVERLAP_RETRY")&&imprisoned.retryCount>0,"unrecoverable contact has an explicit retry phase");
        DebrisCollision.tick(imprisoned,b->List.of());
        check(imprisoned.phase==Phase.FALLING&&imprisoned.velocity.length()>0,"overlap retry resumes when blocking geometry disappears");
        var thinFence=new DebrisShape(new B(-.01,-2,-2,.01,2,2),Q.turn(new V(0,.7,0),1),new V(2,0,0));
        var projectileBox=new DebrisShape(new B(-.2,-.2,-.2,.2,.2,.2),Q.IDENTITY,V.ZERO);
        var sweptHit=projectileBox.sweep(thinFence,new V(5,0,0));
        check(sweptHit!=null&&sweptHit.time()>0&&sweptHit.time()<1,"continuous oriented translation catches thin rotated fence at high speed");
        var plate=fragment(0,3,0);plate.cells.get(0).shapes=List.of(new B(-1,-.1,-.15,1,.1,.15));
        plate.rotation=Q.turn(new V(0,Math.PI/4,0),1);
        var falseSupport=DebrisCollision.Obstacle.rigid(new B(-1,-.1,-.15,1,.1,.15),plate.rotation,new V(1.2,plate.position.y()-.2,1.2),105);
        double plateY=plate.position.y();for(int i=0;i<8;i++)DebrisCollision.tick(plate,b->List.of(falseSupport));
        check(plate.position.y()<plateY-.05&&plate.phase!=Phase.SLEEPING,"oriented debris body cannot rest on an empty other-debris AABB corner");
        var commitCell=new Cell(1,V.ZERO,"minecraft:stone");
        for(var status:Commit.values())for(boolean protectedCell:new boolean[]{false,true})for(boolean entity:new boolean[]{false,true}) {
            commitCell.commit=status;commitCell.durable=false;
            check(!DebrisLifecycle.mayRemove(commitCell,"minecraft:stone"),"no source removal before successful durable journal barrier");
            commitCell.durable=true;
            check(DebrisLifecycle.mayRemove(commitCell,"minecraft:stone")== (status==Commit.ARMED),"only durably armed exact source can be removed");
            check(!DebrisLifecycle.mayRemove(commitCell,"minecraft:gold_block"),"mismatching source is never removal permission");
            var removed=DebrisLifecycle.recover(commitCell,"minecraft:air",true,protectedCell,entity);
            if(status==Commit.RESERVED||status==Commit.CONFLICT)check(removed==DebrisLifecycle.Recovery.IGNORE,"unarmed/denied geometry never invented during recovery");
            if(status==Commit.ARMED)check(removed==(protectedCell?DebrisLifecycle.Recovery.CONFLICT:DebrisLifecycle.Recovery.CONFIRM_REMOVAL),"chunk saved before commit checkpoint recovers exactly once");
            if(status==Commit.REMOVED)check(removed==DebrisLifecycle.Recovery.CONFIRM_REMOVAL,"confirmed fragment survives subsequent empty source edits");
            var restored=DebrisLifecycle.recover(commitCell,"minecraft:stone",false,protectedCell,entity);
            if(status==Commit.ARMED||status==Commit.REMOVED)check(restored==(!protectedCell&&!entity?DebrisLifecycle.Recovery.REMOVE_EXPECTED:status==Commit.REMOVED?DebrisLifecycle.Recovery.KEEP_COMMITTED:DebrisLifecycle.Recovery.CONFLICT),"rolled-back source replay requires exact intent; subsequent player rebuild cannot erase an already committed fragment");
        }
        var crowded=new DebrisSupportIndex();for(int i=0;i<8192;i++)crowded.update(new UUID(0,i+1),Set.of(77L));crowded.changed(77);
        Set<UUID> allWoken=new HashSet<>();for(int tick=0;tick<300;tick++){var batch=crowded.drain(32);check(batch.size()<=32,"crowded support rechecks have a real per-fragment bound");allWoken.addAll(batch);}
        check(allWoken.size()==8192,"bounded wake cursor eventually reaches every stored fragment without loss");
        var ownership=new Cell(7,V.ZERO,"minecraft:iron_block");
        for(var phase:Commit.values()){ownership.commit=phase;ownership.protectedSource=false;
            check(DebrisLifecycle.preventsNewClaim(ownership,true)==(phase==Commit.REMOVED),"retarget can transfer only unremoved source geometry");
            check(DebrisLifecycle.preventsNewClaim(ownership,false)==(phase!=Commit.CONFLICT),"overlapping structure cannot steal another live source claim");
            ownership.protectedSource=true;check(DebrisLifecycle.preventsNewClaim(ownership,true),"external same-material replacement stays protected across later operations");}
        var window=new DebrisRevisionWindow();UUID session=UUID.randomUUID(),fragmentId=UUID.randomUUID();
        check(window.reset("test:zone",session,1),"initial snapshot stream generation");
        check(window.accept("test:zone",session,2,fragmentId,17,false),"late observer accepts current sleeping fragment without explosion replay");
        check(!window.accept("test:zone",session,2,fragmentId,17,false),"duplicate packet rejected");
        check(!window.accept("test:other",session,3,fragmentId,18,false),"wrong dimension snapshot rejected");
        check(!window.accept("test:zone",UUID.randomUUID(),3,fragmentId,18,false),"old connection generation rejected");
        check(!window.accept("test:zone",session,3,fragmentId,16,false),"older representation revision rejected");
        check(window.accept("test:zone",session,4,fragmentId,18,false),"new geometry/motion correction accepted");
        check(window.accept("test:zone",session,5,fragmentId,0,true),"visibility tombstone accepted");
        check(!window.accept("test:zone",session,4,fragmentId,18,false),"old snapshot cannot resurrect a tombstoned representation");
        check(window.accept("test:zone",session,6,fragmentId,18,false),"returning observer receives same persistent fragment identity");
        window.clear();check(!window.accept("test:zone",session,7,fragmentId,18,false),"disconnect resets cosmetic cache generation");
        journal();
        boolean rejected=false;try{new V(Double.NaN,0,0);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid pose rejected");
        System.out.println("PASS Stage 5 production geometry/collision/support kernels: "+checks+" checks; "+pieces.size()+" recognizable source patches, "+cells.size()+" conserved source cells");
    }
}
