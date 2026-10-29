package com.bobincraft.destruction.debris;

import com.bobincraft.destruction.RefineryTransitions;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;
import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Actual NBT/filesystem/block/VoxelShape adapters. Called only by the registered Fabric GameTest suite. */
public final class DebrisGameTests {
    private DebrisGameTests(){}
    private static DebrisFragment fixture() {
        var f=DebrisPlan.create("test:dimension","refinery",UUID.fromString("12345678-1234-1234-1234-123456789012"),
                List.of(new DebrisPlan.Source(-17,79,-33,"minecraft:stone"),new DebrisPlan.Source(-18,79,-33,"minecraft:iron_block"))).get(0);
        for(var c:f.cells){c.commit=Commit.REMOVED;c.shapes=List.of(new B(c.local.x(),c.local.y(),c.local.z(),c.local.x()+1,c.local.y()+1,c.local.z()+1));}
        f.supportToken=Long.MIN_VALUE+1;f.position=new V(-4,52,7);f.rotation=Q.turn(new V(.1,.2,.3),1);f.velocity=new V(.1,-.2,.3);f.angularVelocity=new V(.02,.03,.04);
        f.launchVelocity=new V(.02,.01,-.03);f.launchAngular=new V(.01,.02,0);f.lastSimulationTick=1234;f.successfulSteps=57;f.retryCount=3;
        f.supports.add(123L);f.dependencies.add(321L);f.revision=77;f.geometryRevision=9;return f;
    }
    public static void roundTrip(TestContext context) {
        var f=fixture();for(var phase:Phase.values()){f.phase=phase;f.resume=Phase.FALLING;var encoded=DebrisPersistentState.encode(f);
            if(!encoded.equals(DebrisPersistentState.encode(DebrisPersistentState.decode(encoded))))throw new AssertionError("NBT changed phase/pose/source/support/identity: "+phase);}
        var packet=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        try{packet.writeNbt(DebrisPersistentState.encode(f));if(packet.readableBytes()>DebrisNetworking.MAX_PACKET_BYTES)throw new AssertionError("Fragment exceeds network page");
            if(!DebrisPersistentState.decode(Objects.requireNonNull(packet.readNbt())).id.equals(f.id))throw new AssertionError("Network NBT changed identity");}
        finally{packet.release();}
        context.complete();
    }
    public static void disk(TestContext context)throws Exception {
        Path directory=Files.createTempDirectory("bobin-debris-gametest-");
        try {
            Path path=directory.resolve("journal.dat");var state=DebrisPersistentState.load(path,"test:dimension");var f=fixture();state.fragments.put(f.id,f);state.operations.put("refinery/stage/3",f.operation);state.dirty=true;
            if(!state.flush())throw new AssertionError("Durable intent/pose barrier failed");
            var loaded=DebrisPersistentState.load(path,"test:dimension");if(!loaded.encode().equals(state.encode()))throw new AssertionError("Real journal reload changed records");
            var future=state.encode();future.putInt("Version",99);future.putString("UnknownFutureData","retain");NbtIo.writeCompressed(future,path.toFile());byte[] before=Files.readAllBytes(path);
            var frozen=DebrisPersistentState.load(path,"test:dimension");frozen.dirty=true;
            if(frozen.writable()||frozen.flush()||!Arrays.equals(before,Files.readAllBytes(path))||!future.equals(frozen.encode()))throw new AssertionError("Future journal not read-only/preserved");
            Path badParent=directory.resolve("not_a_directory");Files.writeString(badParent,"fixture");var broken=DebrisPersistentState.load(badParent.resolve("journal.dat"),"test:dimension");broken.dirty=true;
            if(broken.flush()||broken.writable())throw new AssertionError("Failed disk barrier allowed removal");
        } finally {try(var paths=Files.walk(directory)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        context.complete();
    }
    public static void commitAndRecovery(TestContext context)throws Exception {
        var world=context.getWorld();var manager=StructureDebrisManager.get(world);String structure="gametest_commit_"+UUID.randomUUID(),op="operation";
        var p=context.getAbsolutePos(new BlockPos(2,5,2));world.setBlockState(p,Blocks.STONE.getDefaultState(),3);
        var src=List.of(new DebrisPlan.Source(p.getX(),p.getY(),p.getZ(),RefineryTransitions.encode(world.getBlockState(p))));
        if(!manager.reserve(structure,op,src))throw new AssertionError("Source reservation failed");
        if(manager.beforeRemove(structure,op,p,world.getBlockState(p))||world.getBlockState(p).isAir())throw new AssertionError("Removal preceded durable intent");
        manager.beginTick();if(!manager.beforeRemove(structure,op,p,world.getBlockState(p)))throw new AssertionError("Durable reservation not released");
        StructureDebrisManager.writeSource(world,p,Blocks.AIR.getDefaultState());manager.removed(structure,op,p);
        var f=manager.state.fragments.values().stream().filter(a->a.structure.equals(structure)).findFirst().orElseThrow();UUID identity=f.id;long revision=f.revision;
        manager.removed(structure,op,p);if(f.revision!=revision)throw new AssertionError("Duplicate commit was not idempotent");
        // Simulate chunk saved before removal, while the durable fragment journal already contains its commit.
        StructureDebrisManager.writeSource(world,p,Blocks.STONE.getDefaultState());f.reconciled=false;V savedPose=f.position;
        var reconcile=StructureDebrisManager.class.getDeclaredMethod("reconcile",DebrisFragment.class);reconcile.setAccessible(true);
        if(!(boolean)reconcile.invoke(manager,f)||!world.getBlockState(p).isAir()||!f.position.equals(savedPose)||!f.id.equals(identity))throw new AssertionError("Rolled-back source did not recover same fragment once");
        if(!(boolean)reconcile.invoke(manager,f)||manager.state.fragments.values().stream().filter(a->a.structure.equals(structure)).count()!=1)throw new AssertionError("Repeated recovery duplicated fragment");
        StructureDebrisManager.resetStructure(world,structure);context.complete();
    }
    public static void protectedPartial(TestContext context) {
        var world=context.getWorld();var manager=StructureDebrisManager.get(world);String structure="gametest_partial_"+UUID.randomUUID(),op="operation";
        var p=context.getAbsolutePos(new BlockPos(1,5,1));var q=p.east();world.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),3);world.setBlockState(q,Blocks.IRON_BLOCK.getDefaultState(),3);
        var source=new ArrayList<DebrisPlan.Source>();for(var cell:List.of(p,q))source.add(new DebrisPlan.Source(cell.getX(),cell.getY(),cell.getZ(),RefineryTransitions.encode(world.getBlockState(cell))));
        if(!manager.reserve(structure,op,source))throw new AssertionError("Plan failed");manager.prearm(structure,op,source,16);manager.beginTick();
        if(!manager.beforeRemove(structure,op,p,world.getBlockState(p)))throw new AssertionError("Commit not armed");StructureDebrisManager.writeSource(world,p,Blocks.AIR.getDefaultState());manager.removed(structure,op,p);
        world.setBlockState(q,Blocks.AIR.getDefaultState(),3);world.setBlockState(q,Blocks.IRON_BLOCK.getDefaultState(),3);
        if(!manager.conflicted(structure,op,q)||manager.beforeRemove(structure,op,q,world.getBlockState(q)))throw new AssertionError("Real remove/place observation did not protect same-material replacement");
        long rendered=manager.state.fragments.values().stream().filter(f->f.structure.equals(structure)).flatMap(f->f.cells.stream()).filter(c->c.commit==Commit.REMOVED).count();
        if(rendered!=1||world.getBlockState(q).isAir())throw new AssertionError("Protected cell duplicated in mesh or deleted");
        StructureDebrisManager.resetStructure(world,structure);context.complete();
    }
    public static void actualSlabSupport(TestContext context) {
        var world=context.getWorld();String structure="gametest_physics_"+UUID.randomUUID(),op="operation";var manager=StructureDebrisManager.get(world);
        var floor=context.getAbsolutePos(new BlockPos(3,1,3));var source=floor.up(5);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)world.setBlockState(floor.add(dx,0,dz),Blocks.STONE_SLAB.getDefaultState(),3);
        world.setBlockState(source,Blocks.IRON_BLOCK.getDefaultState(),3);
        var plan=List.of(new DebrisPlan.Source(source.getX(),source.getY(),source.getZ(),RefineryTransitions.encode(world.getBlockState(source))));
        if(!manager.reserve(structure,op,plan))throw new AssertionError("Reserve");manager.prearm(structure,op,plan,1);manager.beginTick();
        if(!manager.beforeRemove(structure,op,source,world.getBlockState(source)))throw new AssertionError("Durable source");StructureDebrisManager.writeSource(world,source,Blocks.AIR.getDefaultState());manager.removed(structure,op,source);
        var fragment=manager.state.fragments.values().stream().filter(f->f.structure.equals(structure)).findFirst().orElseThrow();
        context.runAtTick(140,()->{
            if(fragment.phase!=Phase.SLEEPING||fragment.supports.isEmpty())throw new AssertionError("Actual VoxelShape slab did not settle fragment");
            double y=fragment.position.y();for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)world.setBlockState(floor.add(dx,0,dz),Blocks.AIR.getDefaultState(),3);
            context.runAtTick(150,()->{if(fragment.phase==Phase.SLEEPING||fragment.position.y()>=y)throw new AssertionError("Actual block observer failed support-loss wake");StructureDebrisManager.resetStructure(world,structure);context.complete();});
        });
    }
    public static void budgetFairness(TestContext context) {
        var world=context.getWorld();var manager=StructureDebrisManager.get(world);var ids=new ArrayList<String>();var fragments=new ArrayList<DebrisFragment>();
        int previousBudget=StructureDebrisManager.maxBlockQueriesPerTick;
        try {
            for(int i=0;i<8;i++) {
                String name="gametest_fair_"+UUID.randomUUID();ids.add(name);
                var p=context.getAbsolutePos(new BlockPos(1+(i%4)*3,10,1+(i/4)*3));world.setBlockState(p,Blocks.STONE.getDefaultState(),3);
                var src=List.of(new DebrisPlan.Source(p.getX(),p.getY(),p.getZ(),RefineryTransitions.encode(world.getBlockState(p))));
                if(!manager.reserve(name,"op",src))throw new AssertionError("Reserve fairness fixture");
                manager.prearm(name,"op",src,1);manager.beginTick();
                if(!manager.beforeRemove(name,"op",p,world.getBlockState(p)))throw new AssertionError("Arm fairness fixture");
                StructureDebrisManager.writeSource(world,p,Blocks.AIR.getDefaultState());manager.removed(name,"op",p);
                fragments.add(manager.state.fragments.values().stream().filter(f->f.structure.equals(name)).findFirst().orElseThrow());
            }
            StructureDebrisManager.maxBlockQueriesPerTick=600;
            context.runAtTick(90,()->{
                try {for(var f:fragments)if(f.successfulSteps==0)throw new AssertionError("Starved fragment: "+f.id+" "+f.pauseReason);context.complete();}
                finally {StructureDebrisManager.maxBlockQueriesPerTick=previousBudget;for(String id:ids)StructureDebrisManager.resetStructure(world,id);}
            });
        } catch(RuntimeException|Error failure) {
            StructureDebrisManager.maxBlockQueriesPerTick=previousBudget;for(String id:ids)StructureDebrisManager.resetStructure(world,id);throw failure;
        }
    }

}
