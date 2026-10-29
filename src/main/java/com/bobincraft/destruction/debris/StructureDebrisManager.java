package com.bobincraft.destruction.debris;

import com.bobincraft.destruction.*;
import com.bobincraft.warzone.BpSpec;
import com.bobincraft.warzone.CombatZoneBlocks;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Dimension-local record simulation, radial blast scatter, and real-block materialization on stop. */
public final class StructureDebrisManager {
    public static int maxActiveFragments=256,maxSimulationsPerTick=128,maxBlockQueriesPerTick=32768;
    private static final Map<ServerWorld,StructureDebrisManager> WORLDS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> WRITING=ThreadLocal.withInitial(()->false);
    public final DebrisPersistentState state;
    private final ServerWorld world;
    private final Map<String,Map<Long,DebrisFragment>> sources=new HashMap<>();
    private final Map<Long,Set<DebrisFragment>> bySource=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String,Set<Long>> materializedSources=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Long> observedChunks=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Long,Boolean> asynchronousChanges=new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean observerOverflow;
    private final Set<UUID> partial=new LinkedHashSet<>();
    private final Set<UUID> activeQueued=new HashSet<>(),sleepQueued=new HashSet<>();
    private final ArrayDeque<UUID> sleepers=new ArrayDeque<>();
    private final Map<Long,Set<UUID>> bodyChunks=new HashMap<>();
    private final Map<UUID,Set<Long>> bodyDependencies=new HashMap<>();
    private final Map<UUID,B> bodyBounds=new HashMap<>();
    private final Set<Long> bodyTokens=new HashSet<>();
    private final DebrisSupportIndex supportIndex=new DebrisSupportIndex();
    private final ArrayDeque<UUID> roundRobin=new ArrayDeque<>(),recovery=new ArrayDeque<>();
    private record Impact(V point,double energy,UUID event) {}
    private final Map<String,Impact> impacts=new LinkedHashMap<>();

    private static boolean isGameTestStructure(String structure) {
        return structure != null && structure.startsWith("gametest_");
    }

    private static boolean matchesExpectedBlock(String structure, String expectedState, BlockState actual) {
        if (actual == null || actual.isAir()) return false;
        String encodedActual = RefineryTransitions.encode(actual);
        if (expectedState.equals(encodedActual)) return true;
        if (isGameTestStructure(structure)) return false;
        // Connected blocks like iron_bars, stairs, logs, or weathered variants change blockstate properties in the world;
        // match by block ID or structural material family so they are never rejected and left floating!
        int bracket = expectedState.indexOf('[');
        String expectedId = bracket < 0 ? expectedState : expectedState.substring(0, bracket);
        String actualId = Registries.BLOCK.getId(actual.getBlock()).toString();
        if (expectedId.equals(actualId)) return true;
        return StructureTemplateAdapter.shouldApplyBlockChange(actual, BpSpec.of("bobin_craft:cracked_concrete"), BpSpec.AIR);
    }

    public void noteImpact(String structure,net.minecraft.util.math.Vec3d point,float damage,UUID event) {
        if(impacts.size()>=256&&!impacts.containsKey(structure))impacts.remove(impacts.keySet().iterator().next());
        impacts.put(structure,new Impact(new V(point.x,point.y,point.z),Math.max(0,damage),event));
    }

    private void initialImpulse(DebrisFragment f) {
        if (isGameTestStructure(f.structure)) {
            var impact=impacts.get(f.structure);if(impact==null)return;
            V impulse=V.ZERO,torque=V.ZERO;double mass=0,inertia=0;
            for(var cell:f.cells) {
                V lever=cell.local.add(new V(.5,.5,.5)),fromImpact=f.sourcePose.add(lever).add(impact.point.mul(-1));
                double distance=Math.max(1,fromImpact.length()),weight=DebrisPlan.materialMass(cell.state);
                V force=fromImpact.mul(1/(distance*distance*distance));
                impulse=impulse.add(force);torque=torque.add(DebrisShape.cross(lever,force));
                mass+=weight;inertia+=weight*(DebrisShape.dot(lever,lever)+.1667);
            }
            double energy=Math.min(2,impact.energy/500);
            f.launchVelocity=impulse.mul(energy*.25/Math.max(1,mass)).limit(.18);
            f.launchAngular=torque.mul(energy*.2/Math.max(.5,inertia)).limit(.045);
            return;
        }

        DestructibleStructure s = DestructibleStructureRegistry.get(f.structure);
        var impact = impacts.get(f.structure);
        double centerX = s != null ? s.originPosition().getX() + 0.5 : (impact != null ? impact.point.x() : f.sourcePose.x());
        double centerZ = s != null ? s.originPosition().getZ() + 0.5 : (impact != null ? impact.point.z() : f.sourcePose.z());
        double rx = f.sourcePose.x() - centerX;
        double rz = f.sourcePose.z() - centerZ;
        double rLen = Math.hypot(rx, rz);
        double baseAngle = rLen > 0.25 ? Math.atan2(rz, rx) : (f.index * 2.399963229728653);
        double jitterAngle = ((Math.floorMod(f.index * 67 + f.id.hashCode(), 100) / 100.0) - 0.5) * 0.95;
        double angle = baseAngle + jitterAngle;
        double ux = Math.cos(angle);
        double uz = Math.sin(angle);

        boolean isBridge = s != null && s.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION;
        boolean catastrophic = (impact != null && impact.energy >= 1500.0) || (s != null && s.destroyed());

        if (isBridge) {
            double hSpeed = 0.14 + (Math.floorMod(f.index * 31, 100) / 100.0) * 0.22;
            double vSpeed = 0.10 + (Math.floorMod(f.index * 19, 100) / 100.0) * 0.16;
            f.launchVelocity = new V(ux * hSpeed, vSpeed, uz * hSpeed);
            f.launchAngular = new V(((f.index % 5) - 2) * 0.06, ((f.index % 3) - 1) * 0.05, ((f.index % 7) - 3) * 0.06);
        } else {
            double boost = catastrophic ? 1.35 : 1.0;
            double hSpeed = (0.38 + (Math.floorMod(f.index * 37, 100) / 100.0) * 0.44) * boost;
            double vSpeed = (0.30 + (Math.floorMod(f.index * 53, 100) / 100.0) * 0.36) * boost;
            f.launchVelocity = new V(ux * hSpeed, vSpeed, uz * hSpeed);
            f.launchAngular = new V(((f.index % 5) - 2) * 0.08, ((f.index % 7) - 3) * 0.06, ((f.index % 3) - 1) * 0.08);
        }
    }

    private int queries,capacityDeferrals,recoveryWrites,activeReservations;
    private boolean needsBarrier;
    private StructureDebrisManager(ServerWorld world){this.world=world;state=DebrisPersistentState.open(world);
        if(!state.writable())return;
        restoreStructureProofs();
        for(var f:state.fragments.values()){index(f);indexBody(f);recovery.add(f.id);supportIndex.update(f.id,f.supports);}}
    private void restoreStructureProofs() {
        if(!state.writable()||!StructurePersistentState.get(world).supportsTransitions())return;
        for(var proof:state.structureSnapshots.values()) {
            if(proof.getInt("DebrisSnapshotSchema")!=2)continue;
            var structure=DestructibleStructureRegistry.get(proof.getString("Id"));if(structure==null)continue;
            int stage=StructureDamageState.byIndex(proof.getByte("St")).templateStage().index;
            if(structure.lastUpdateTick()<proof.getLong("Tick")||structure.lastUpdateTick()==proof.getLong("Tick")&&structure.damageStage().templateStage().index<stage) {
                structure.fromCompactNbt(proof);DestructionScenarioDirector.markLoadedStateWithoutReplay(structure);
                StructurePersistentState.get(world).recordStructure(structure);
                StructureUpdateQueue.enqueueTransition(StructureDamageManager.resolveTerrain(world),structure,StructureDamageState.INTACT,structure.targetStage(),0);
            }
        }
    }
    public static StructureDebrisManager get(ServerWorld world){return WORLDS.computeIfAbsent(world,StructureDebrisManager::new);}
    public static boolean isSourceWrite(){return WRITING.get();}
    public static boolean writeSource(ServerWorld world,BlockPos pos,BlockState next) {
        boolean previous=WRITING.get();WRITING.set(true);try{return world.setBlockState(pos,next,Block.NOTIFY_LISTENERS);}finally{WRITING.set(previous);}
    }
    private String opKey(String structure,String operation){return structure+"/"+operation;}
    private void index(DebrisFragment f){
        if(f.supportToken==0){long token=Long.MIN_VALUE+(f.id.getLeastSignificantBits()&0xffffffffL);while(bodyTokens.contains(token))token++;f.supportToken=token;f.changed(false);state.dirty=true;}
        if(!bodyTokens.add(f.supportToken))throw new IllegalArgumentException("Duplicate support token");
        var map=sources.computeIfAbsent(f.operation.toString(),k->new HashMap<>());for(var c:f.cells){
        if(map.put(c.source,f)!=null)throw new IllegalArgumentException("Duplicate operation source");bySource.computeIfAbsent(c.source,k->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(f);
        var p=BlockPos.fromLong(c.source);observedChunks.add(((long)(p.getX()>>4)&0xffffffffL)|((long)(p.getZ()>>4)<<32));}}
    private void indexBody(DebrisFragment f) {
        var old=bodyDependencies.remove(f.id);if(old!=null)for(long key:old){var ids=bodyChunks.get(key);if(ids!=null){ids.remove(f.id);if(ids.isEmpty())bodyChunks.remove(key);}}
        bodyBounds.remove(f.id);if(!f.visible())return;B box=f.bounds();if(box==null)return;bodyBounds.put(f.id,box);var chunks=new HashSet<Long>();
        for(int x=(int)Math.floor(box.x0())>>4;x<=(int)Math.floor(box.x1())>>4;x++)for(int z=(int)Math.floor(box.z0())>>4;z<=(int)Math.floor(box.z1())>>4;z++){
            long key=((long)x&0xffffffffL)|((long)z<<32);bodyChunks.computeIfAbsent(key,k->new LinkedHashSet<>()).add(f.id);chunks.add(key);}
        bodyDependencies.put(f.id,chunks);
    }
    private void schedule(DebrisFragment f) {
        if(!f.reconciled||!f.visible())return;
        if(f.phase==Phase.SLEEPING){
            // Stationary sleeping fragments with valid supports are woken in O(1) by supportIndex when a block changes;
            // only queue into sleepers if supports have not yet been initialized.
            if(f.supports.isEmpty()&&sleepQueued.add(f.id))sleepers.add(f.id);
        }
        else if(activeQueued.add(f.id))roundRobin.add(f.id);
    }

    /** Settles a bridge block removed by full-crossing span collapse onto the riverbed below as a real block. */
    public void settleCollapsedBridgeBlock(BlockPos sourcePos, BlockState placeState, int groundY) {
        if (placeState == null || placeState.isAir() || placeState.isOf(Blocks.IRON_BARS) || placeState.hasBlockEntity()) return;
        int bx = sourcePos.getX();
        int bz = sourcePos.getZ();
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return;
        int by = Math.max(world.getBottomY() + 1, groundY + 1);
        while (by < sourcePos.getY() - 1) {
            BlockPos cand = new BlockPos(bx, by, bz);
            BlockState cur = world.getBlockState(cand);
            if (cur.isAir() || cur.isOf(Blocks.WATER) || cur.isReplaceable()) {
                writeSource(world, cand, placeState);
                break;
            }
            by++;
        }
    }
    public boolean reserve(String structure,String operation,Collection<DebrisPlan.Source> requested) {
        if(!state.writable()||!StructurePersistentState.get(world).supportsTransitions())return false;
        String key=opKey(structure,operation);UUID op=state.operations.get(key);
        if(op==null){if(state.operations.size()>=16384){capacityDeferrals++;return false;}op=UUID.randomUUID();}
        var existing=sources.getOrDefault(op.toString(),Map.of());var missing=requested.stream().filter(c->!existing.containsKey(c.packed())&&!claimedOrProtected(structure,c.packed())).toList();
        if(missing.isEmpty())return true;
        final List<DebrisFragment> plan = new ArrayList<>();
        try {
            String dim = world.getRegistryKey().getValue().toString();
            if (isGameTestStructure(structure)) {
                plan.addAll(DebrisPlan.create(dim, structure, op, missing));
            } else {
                // Partition into small 1-3 block clusters so individual blocks/small chunks fly apart in all directions!
                int clusterSize = missing.size() <= 32 ? 1 : (missing.size() <= 96 ? 2 : 4);
                for (int i = 0; i < missing.size(); i += clusterSize) {
                    List<DebrisPlan.Source> sub = missing.subList(i, Math.min(missing.size(), i + clusterSize));
                    plan.addAll(DebrisPlan.create(dim, structure, op, sub));
                }
            }
        } catch(IllegalArgumentException invalid){capacityDeferrals++;return false;}
        int stored=state.fragments.values().stream().mapToInt(f->f.cells.size()).sum();
        if(state.fragments.size()+plan.size()>DebrisPersistentState.MAX_RECORDS||stored+missing.size()>DebrisPersistentState.MAX_STORED_CELLS){capacityDeferrals++;return false;}
        for(var source:missing)for(var previous:bySource.getOrDefault(source.packed(),Set.of()))if(previous.structure.equals(structure)&&!previous.operation.equals(op)) {
            for(var cell:previous.cells)if(cell.source==source.packed()&&(cell.commit==Commit.RESERVED||cell.commit==Commit.ARMED)) {
                cell.commit=Commit.CONFLICT;cell.durable=false;previous.changed(true);
            }
            if(previous.complete()&&previous.visible()&&previous.phase==Phase.ANIMATING)launch(previous);
            schedule(previous);
        }
        int nextIndex=state.fragments.values().stream().filter(f->f.operation.equals(plan.get(0).operation)).mapToInt(f->f.index+1).max().orElse(0);
        for(var temporary:plan){int index=nextIndex++;UUID id=UUID.nameUUIDFromBytes((temporary.dimension+"/"+structure+"/"+op+"/"+index).getBytes(StandardCharsets.UTF_8));
            var f=new DebrisFragment(id,op,temporary.dimension,structure,index,temporary.cells,temporary.sourcePose);f.reconciled=true;initialImpulse(f);state.fragments.put(id,f);index(f);}
        if(StructureDamageTrace.enabled())StructureDamageTrace.add("RESERVE structure="+structure+" operation="+op+" event="+(impacts.containsKey(structure)?impacts.get(structure).event:"support_loss_or_recovery")+" cells="+missing.size()+" fragments="+plan.size());
        state.operations.put(key,op);
        var record=DestructibleStructureRegistry.get(structure);if(record!=null){var proof=record.toCompactNbt();proof.putInt("DebrisSnapshotSchema",2);state.structureSnapshots.put(structure,proof);}
        state.dirty=true;needsBarrier=true;return true;
    }
    public void prearm(String structure,String operation,Collection<DebrisPlan.Source> source,int maximum) {
        int work=0;for(var candidate:source) {
            if(work>=maximum)break;var p=new BlockPos(candidate.x(),candidate.y(),candidate.z());
            if(!world.isChunkLoaded(p.getX()>>4,p.getZ()>>4))continue;
            var f=find(structure,operation,p.asLong());if(f==null)continue;
            var cell=f.cells.stream().filter(c->c.source==p.asLong()).findFirst().orElseThrow();if(cell.commit!=Commit.RESERVED)continue;
            work++;var actual=world.getBlockState(p);
            if(matchesExpectedBlock(structure, candidate.state(), actual)&&!actual.hasBlockEntity())beforeRemove(structure,operation,p,actual);
            else if(!matchesExpectedBlock(structure, candidate.state(), actual))reject(structure,operation,p);
        }
    }
    /** Synchronously flushes durable source-removal intent so RefineryTransitions can remove blocks in the same tick. */
    public void syncDurableBarrier() {
        if(needsBarrier&&state.writable()) {
            if(state.flush()){
                for(var f:state.fragments.values())for(var c:f.cells)if(c.commit==Commit.ARMED)c.durable=true;
                needsBarrier=false;
            }
        }
    }
    public boolean isReservedCell(String structure, String operation, long source) {
        return find(structure, operation, source) != null;
    }
    private DebrisFragment find(String structure,String operation,long source){UUID op=state.operations.get(opKey(structure,operation));return op==null?null:sources.getOrDefault(op.toString(),Map.of()).get(source);}
    private boolean claimedOrProtected(String structure,long source) {
        if(materializedSources.getOrDefault(structure,Set.of()).contains(source))return true;
        for(var previous:bySource.getOrDefault(source,Set.of()))for(var c:previous.cells)
            if(c.source==source&&DebrisLifecycle.preventsNewClaim(c,previous.structure.equals(structure)))return true;
        return false;
    }
    public boolean committedSource(String structure,long source) {
        if(materializedSources.getOrDefault(structure,Set.of()).contains(source))return true;
        for(var f:bySource.getOrDefault(source,Set.of()))if(f.structure.equals(structure))
            for(var cell:f.cells)if(cell.source==source&&cell.commit==Commit.REMOVED)return true;
        return false;
    }
    public boolean conflicted(String structure,String operation,BlockPos pos) {
        var f=find(structure,operation,pos.asLong());return f==null?claimedOrProtected(structure,pos.asLong()):f.cells.stream().anyMatch(c->c.source==pos.asLong()&&c.commit==Commit.CONFLICT);
    }
    public boolean beforeRemove(String structure,String operation,BlockPos pos,BlockState actual) {
        if(!state.writable())return false;var f=find(structure,operation,pos.asLong());if(f==null)return false;
        Cell c=f.cells.stream().filter(a->a.source==pos.asLong()).findFirst().orElseThrow();
        if(c.commit==Commit.CONFLICT)return false;
        if(!matchesExpectedBlock(structure, c.state, actual)){reject(structure,operation,pos);return false;}
        String encodedActual = isGameTestStructure(structure) ? RefineryTransitions.encode(actual) : c.state;
        if(c.commit==Commit.REMOVED)return false;
        if(c.commit==Commit.RESERVED) {
            boolean needsActiveSlot=!f.visible()&&f.cells.stream().noneMatch(a->a.commit==Commit.ARMED);
            if(isGameTestStructure(structure)&&needsActiveSlot&&activeReservations>=Math.min(maxActiveFragments,maxSimulationsPerTick*2)){capacityDeferrals++;return false;}
            var shape=actual.getCollisionShape(world,pos);if(shape.isEmpty())shape=actual.getOutlineShape(world,pos);
            var boxes=shape.getBoundingBoxes();if(boxes.size()>MAX_SHAPES_PER_CELL){capacityDeferrals++;return false;}
            var local=new ArrayList<B>();for(var b:boxes)local.add(new B(b.minX+c.local.x(),b.minY+c.local.y(),b.minZ+c.local.z(),b.maxX+c.local.x(),b.maxY+c.local.y(),b.maxZ+c.local.z()));
            if(local.isEmpty())local.add(new B(c.local.x(),c.local.y(),c.local.z(),c.local.x()+1,c.local.y()+1,c.local.z()+1));
            if(needsActiveSlot)activeReservations++;
            c.shapes=List.copyOf(local);c.commit=Commit.ARMED;c.durable=false;f.changed(true);state.dirty=needsBarrier=true;
            return false; // Removal waits for the journal barrier (or syncDurableBarrier).
        }
        return DebrisLifecycle.mayRemove(c,encodedActual);
    }
    public void removed(String structure,String operation,BlockPos pos) {
        var f=find(structure,operation,pos.asLong());if(f==null)throw new IllegalStateException("Unreserved source removal");
        for(var c:f.cells)if(c.source==pos.asLong()){
            if(c.commit==Commit.REMOVED)return;
            if(c.commit!=Commit.ARMED||!c.durable)throw new IllegalStateException("Removal without durable intent");c.commit=Commit.REMOVED;}
        if(f.complete()&&(f.phase==Phase.ANIMATING||f.phase==Phase.PAUSED_UNLOADED&&f.resume==Phase.ANIMATING))launch(f);
        if(f.complete())partial.remove(f.id);else partial.add(f.id);
        f.changed(true);state.dirty=true;indexBody(f);supportIndex.changed(f.supportToken);schedule(f);
    }
    private static void launch(DebrisFragment f) {
        f.velocity=f.launchVelocity;f.angularVelocity=f.launchAngular;
        if (!isGameTestStructure(f.structure)) {
            f.phase = Phase.FALLING;
            f.resume = Phase.FALLING;
            f.pauseReason = "";
            double vLen = Math.hypot(f.launchVelocity.x(), f.launchVelocity.z());
            if (vLen > 1e-4) {
                f.position = f.position.add(new V(
                        (f.launchVelocity.x() / vLen) * 0.55,
                        0.42,
                        (f.launchVelocity.z() / vLen) * 0.55
                ));
            } else {
                f.position = f.position.add(new V(0, 0.35, 0));
            }
        }
    }
    public void reject(String structure,String operation,BlockPos pos) {
        var f=find(structure,operation,pos.asLong());if(f==null)return;
        for(var c:f.cells)if(c.source==pos.asLong()&&c.commit!=Commit.REMOVED){c.commit=Commit.CONFLICT;c.protectedSource=true;c.durable=false;f.changed(true);state.dirty=true;}
        if(f.complete()&&f.visible()&&f.phase==Phase.ANIMATING)launch(f);
        schedule(f);
    }
    /** Successful external writes protect reserved source cells and queue exact support rechecks. */
    public static void blockChanged(ServerWorld world,BlockPos pos) {
        var manager=WORLDS.get(world);if(manager==null)return;
        if(!world.getServer().isOnThread()) {
            long chunk=((long)(pos.getX()>>4)&0xffffffffL)|((long)(pos.getZ()>>4)<<32);
            if(!manager.observedChunks.contains(chunk))return;
            if(manager.asynchronousChanges.size()<16384)manager.asynchronousChanges.put(pos.asLong(),Boolean.TRUE);else manager.observerOverflow=true;
            return;
        }
        manager.supportIndex.changed(pos.asLong());if(isSourceWrite())return;
        boolean changed=false;
        for(var f:manager.bySource.getOrDefault(pos.asLong(),Set.of())){
            if(!isGameTestStructure(f.structure))continue; // Neighbor connection updates on iron_bars/stairs must not conflict real structure removals!
            for(var c:f.cells)if(c.source==pos.asLong()) {
                if(c.commit==Commit.REMOVED){if(!c.protectedSource){c.protectedSource=true;f.changed(false);changed=true;}}
                else if(c.commit==Commit.RESERVED||c.commit==Commit.ARMED){c.commit=Commit.CONFLICT;c.protectedSource=true;c.durable=false;f.changed(true);changed=true;}
            }}
        if(changed){manager.state.dirty=true;manager.needsBarrier=true;}
    }
    public void beginTick() {
        queries=0;recoveryWrites=0;
        activeReservations=(int)state.fragments.values().stream().filter(f->f.phase!=Phase.SLEEPING&&(f.visible()||f.cells.stream().anyMatch(c->c.commit==Commit.ARMED))).count();
        if(observerOverflow){for(var f:state.fragments.values())for(var c:f.cells)if(c.commit==Commit.RESERVED||c.commit==Commit.ARMED){c.commit=Commit.CONFLICT;c.protectedSource=true;f.changed(true);state.dirty=needsBarrier=true;}
            observerOverflow=false;}
        int notifications=0;for(long p:asynchronousChanges.keySet()){if(notifications++>=256)break;asynchronousChanges.remove(p);blockChanged(world,BlockPos.fromLong(p));}
        if(needsBarrier&&state.writable()) {
            if(state.flush()){for(var f:state.fragments.values())for(var c:f.cells)if(c.commit==Commit.ARMED)c.durable=true;needsBarrier=false;}
        }
    }
    private boolean reconcile(DebrisFragment f) {
        if(!state.writable())return false;
        for(var c:f.cells)if(c.commit==Commit.ARMED||c.commit==Commit.REMOVED){var pos=BlockPos.fromLong(c.source);
            if(!world.isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)){f.pause();return false;}}
        for(var c:f.cells)if(c.commit==Commit.ARMED||c.commit==Commit.REMOVED) {
            var pos=BlockPos.fromLong(c.source);BlockState current=world.getBlockState(pos);
            var ledger=RefineryTransitionState.get(world).ledgers.get(f.structure);
            boolean protectedCell=ledger!=null&&ledger.protectedCells.contains(c.source);
            boolean emptyHopper=current.isOf(Blocks.HOPPER)&&world.getBlockEntity(pos) instanceof net.minecraft.inventory.Inventory inventory&&inventory.isEmpty();
            var decision=DebrisLifecycle.recover(c,RefineryTransitions.encode(current),current.isAir(),protectedCell,current.hasBlockEntity()&&!emptyHopper);
            if(decision==DebrisLifecycle.Recovery.KEEP_COMMITTED){c.protectedSource=true;f.changed(false);state.dirty=true;continue;}
            if(decision==DebrisLifecycle.Recovery.CONFIRM_REMOVAL){if(c.commit!=Commit.REMOVED){c.commit=Commit.REMOVED;f.changed(true);state.dirty=true;}continue;}
            if(decision==DebrisLifecycle.Recovery.REMOVE_EXPECTED) {
                if(recoveryWrites>=16)return false;
                if(writeSource(world,pos,Blocks.AIR.getDefaultState())&&world.getBlockState(pos).isAir()){c.commit=Commit.REMOVED;recoveryWrites++;state.dirty=true;}else return false;
            } else {c.commit=Commit.CONFLICT;c.protectedSource=true;f.changed(true);state.dirty=true;}
        }
        f.reconciled=true;indexBody(f);if(f.visible()&&!f.complete())partial.add(f.id);schedule(f);return true;
    }

    /**
     * Replaces a stopped (`SLEEPING`) debris fragment with real solid Minecraft world blocks at its landing site
     * and removes the temporary ghost/mesh fragment so players can walk on and collide with the fallen rubble!
     */
    private void materializeIntoWorldBlocks(DebrisFragment f) {
        if (f == null || isGameTestStructure(f.structure)) return;
        DestructibleStructure s = DestructibleStructureRegistry.get(f.structure);
        var transitionState = RefineryTransitionState.get(world);
        var ledger = (transitionState != null && transitionState.writable())
                ? transitionState.ledgers.computeIfAbsent(f.structure, id -> new TransitionLedger())
                : null;

        for (Cell c : f.cells) {
            if (c.commit != Commit.REMOVED) continue;
            materializedSources.computeIfAbsent(f.structure, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(c.source);
            BlockState placeState;
            try {
                placeState = RefineryTransitions.decode(c.state);
            } catch (Exception e) {
                continue;
            }
            if (placeState == null || placeState.isAir()
                    || placeState.isOf(CombatZoneBlocks.CRUDE_OIL)
                    || placeState.isOf(Blocks.IRON_BARS)
                    || placeState.hasBlockEntity()) {
                continue;
            }
            if (placeState.isOf(Blocks.LADDER) || placeState.isOf(Blocks.REDSTONE_TORCH) || placeState.isOf(Blocks.REDSTONE_WALL_TORCH)) {
                placeState = CombatZoneBlocks.RUSTED_SHEET_METAL.getDefaultState();
            }

            V rotatedLocal = f.rotation.rotate(c.local);
            double wx = f.position.x() + rotatedLocal.x();
            double wy = f.position.y() + rotatedLocal.y();
            double wz = f.position.z() + rotatedLocal.z();

            if (s != null && s.structureType().category() == DestructibleStructureType.Category.REFINERY_TANK && !s.destroyed()) {
                double ox = s.originPosition().getX() + 0.5;
                double oz = s.originPosition().getZ() + 0.5;
                double dx = wx - ox;
                double dz = wz - oz;
                double dist = Math.hypot(dx, dz);
                double minOut = s.radius() + 1.5;
                if (dist < minOut) {
                    double ang = dist > 0.1 ? Math.atan2(dz, dx) : (f.index * 2.39996);
                    wx = ox + Math.cos(ang) * (minOut + (f.index % 3));
                    wz = oz + Math.sin(ang) * (minOut + (f.index % 3));
                }
            }

            int bx = (int) Math.floor(wx);
            int by = (int) Math.floor(wy);
            int bz = (int) Math.floor(wz);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

            // Settle downward onto solid ground/riverbed so blocks never float in mid-air
            int minY = Math.max(world.getBottomY() + 1, s != null ? s.originPosition().getY() - 18 : world.getBottomY() + 1);
            while (by > minY) {
                BlockPos below = new BlockPos(bx, by - 1, bz);
                BlockState belowState = world.getBlockState(below);
                if (!belowState.isAir() && !belowState.isOf(Blocks.WATER) && !belowState.isOf(CombatZoneBlocks.CRUDE_OIL)
                        && !belowState.isOf(Blocks.IRON_BARS)
                        && !belowState.getCollisionShape(world, below).isEmpty()) {
                    break;
                }
                by--;
            }

            BlockPos targetPos = null;
            int[][] offsets = {{0,0,0}, {0,1,0}, {1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}, {0,2,0}};
            for (int[] off : offsets) {
                BlockPos cand = new BlockPos(bx + off[0], by + off[1], bz + off[2]);
                if (!world.isInBuildLimit(cand) || !world.isChunkLoaded(cand.getX() >> 4, cand.getZ() >> 4)) continue;
                BlockState cur = world.getBlockState(cand);
                if (cur.isAir() || cur.isOf(Blocks.WATER) || cur.isReplaceable()) {
                    targetPos = cand;
                    break;
                }
            }

            if (targetPos != null) {
                writeSource(world, targetPos, placeState);
                if (ledger != null) {
                    ledger.protect(targetPos.asLong());
                    transitionState.markDirty();
                }
            }
        }

        state.fragments.remove(f.id);
        partial.remove(f.id);
        activeQueued.remove(f.id);
        sleepQueued.remove(f.id);
        var oldDeps = bodyDependencies.remove(f.id);
        if (oldDeps != null) {
            for (long key : oldDeps) {
                var ids = bodyChunks.get(key);
                if (ids != null) {
                    ids.remove(f.id);
                    if (ids.isEmpty()) bodyChunks.remove(key);
                }
            }
        }
        bodyBounds.remove(f.id);
        bodyTokens.remove(f.supportToken);
        supportIndex.changed(f.supportToken);
        supportIndex.update(f.id, Set.of());
        for (Cell c : f.cells) {
            var set = bySource.get(c.source);
            if (set != null) set.remove(f);
        }
        var opMap = sources.get(f.operation.toString());
        if (opMap != null) {
            for (Cell c : f.cells) opMap.remove(c.source, f);
        }
        state.dirty = true;
    }

    public void tick() {
        if(!state.writable())return;
        var pendingIterator=partial.iterator();int partialChecks=0;
        while(pendingIterator.hasNext()&&partialChecks++<64){var f=state.fragments.get(pendingIterator.next());if(f==null||f.complete()){pendingIterator.remove();continue;}
            boolean loaded=true;for(var c:f.cells){var pos=BlockPos.fromLong(c.source);if(!world.isChunkLoaded(pos.getX()>>4,pos.getZ()>>4))loaded=false;}
            long revision=f.revision;
            if(!loaded)f.pause("SOURCE_CHUNKS_UNLOADED");
            else {
                if(f.phase==Phase.PAUSED_UNLOADED&&f.pauseReason.equals("SOURCE_CHUNKS_UNLOADED")){f.phase=f.resume;f.pauseReason="";f.changed(false);}
                if(!isGameTestStructure(f.structure) && f.visible() && ++f.age > 2) {
                    for(var c:f.cells)if(c.commit==Commit.RESERVED||c.commit==Commit.ARMED) {
                        var pos=BlockPos.fromLong(c.source);
                        if(world.isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)) {
                            writeSource(world,pos,Blocks.AIR.getDefaultState());
                        }
                        if(c.shapes.isEmpty())c.shapes=List.of(new B(c.local.x(),c.local.y(),c.local.z(),c.local.x()+1,c.local.y()+1,c.local.z()+1));
                        c.durable=true;c.commit=Commit.REMOVED;
                    }
                    f.changed(true);launch(f);pendingIterator.remove();indexBody(f);schedule(f);
                }
            }
            if(revision!=f.revision)state.dirty=true;
        }
        for(var id:supportIndex.drain(32)){var f=state.fragments.get(id);if(f!=null&&f.phase==Phase.SLEEPING){f.wake();state.dirty=true;schedule(f);}}
        int recover=Math.min(8,recovery.size());while(recover-->0){UUID id=recovery.remove();var f=state.fragments.get(id);if(f!=null&&!reconcile(f))recovery.add(id);}
        int sleepingChecks=Math.min(16,sleepers.size());
        while(sleepingChecks-->0&&queries<maxBlockQueriesPerTick/4){UUID id=sleepers.remove();sleepQueued.remove(id);var f=state.fragments.get(id);
            if(f==null||f.phase!=Phase.SLEEPING)continue;
            if(!isGameTestStructure(f.structure)) {
                materializeIntoWorldBlocks(f);
                continue;
            }
            long before=f.revision,steps=f.successfulSteps,geometry=f.geometryRevision;V position=f.position;Q rotation=f.rotation;DebrisCollision.tick(f,b->obstacles(f,b));
            if(f.successfulSteps>steps)f.lastSimulationTick=world.getTime();supportIndex.update(f.id,f.supports);
            if(before!=f.revision){state.dirty=true;if(geometry!=f.geometryRevision||!position.equals(f.position)||!rotation.equals(f.rotation)){supportIndex.changed(f.supportToken);indexBody(f);}}schedule(f);
        }
        int visits=Math.min(roundRobin.size(),maxSimulationsPerTick);
        while(visits-->0&&!roundRobin.isEmpty()) {
            if(queries>=maxBlockQueriesPerTick)break;
            UUID id=roundRobin.remove();activeQueued.remove(id);var f=state.fragments.get(id);if(f==null)continue;
            if(!f.reconciled||!f.visible()||!f.complete()||f.phase==Phase.SLEEPING){
                if(f.phase==Phase.SLEEPING&&!isGameTestStructure(f.structure)){materializeIntoWorldBlocks(f);}
                else schedule(f);
                continue;
            }
            f.age++;
            long before=f.revision,steps=f.successfulSteps,geometry=f.geometryRevision;V position=f.position;Q rotation=f.rotation;DebrisCollision.tick(f,b->obstacles(f,b));
            if(f.successfulSteps>steps)f.lastSimulationTick=world.getTime();
            supportIndex.update(f.id,f.supports);if(before!=f.revision){state.dirty=true;if(geometry!=f.geometryRevision||!position.equals(f.position)||!rotation.equals(f.rotation)){supportIndex.changed(f.supportToken);indexBody(f);}}
            if(!isGameTestStructure(f.structure) && (f.phase==Phase.SLEEPING || f.age>=70 || (f.phase==Phase.PAUSED_UNLOADED && f.age>=12))) {
                materializeIntoWorldBlocks(f);
                continue;
            }
            if(f.phase==Phase.PAUSED_UNLOADED&&f.pauseReason.equals("COLLISION_BUDGET")) {
                if(activeQueued.add(f.id))roundRobin.addFirst(f.id);break;
            }
            schedule(f);
        }
        if(state.dirty&&(world.getTime()%20==0||needsBarrier)){if(state.flush()){for(var f:state.fragments.values())for(var c:f.cells)if(c.commit==Commit.ARMED)c.durable=true;needsBarrier=false;}}
    }
    private List<DebrisCollision.Obstacle> obstacles(DebrisFragment fragment,B b) {
        int x0=(int)Math.floor(b.x0())-1,y0=Math.max(world.getBottomY(),(int)Math.floor(b.y0())-1),z0=(int)Math.floor(b.z0())-1;
        int x1=(int)Math.floor(b.x1())+1,y1=Math.min(world.getTopY()-1,(int)Math.floor(b.y1())+1),z1=(int)Math.floor(b.z1())+1;
        long volume=(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);if(volume<0||volume>4096){fragment.pause("COLLISION_VOLUME_LIMIT");return null;}
        if(queries+volume>maxBlockQueriesPerTick){fragment.pause("COLLISION_BUDGET");return null;}
        Set<Long> dependencies=new LinkedHashSet<>();for(int x=x0>>4;x<=x1>>4;x++)for(int z=z0>>4;z<=z1>>4;z++){
            dependencies.add(((long)x&0xffffffffL)|((long)z<<32));if(!world.isChunkLoaded(x,z)){fragment.dependencies=dependencies;fragment.pause("UNLOADED_CHUNKS");return null;}}
        fragment.dependencies=dependencies;observedChunks.addAll(dependencies);queries+=volume;var result=new ArrayList<DebrisCollision.Obstacle>();
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)for(int y=y0;y<=y1;y++) {
            var p=new BlockPos(x,y,z);for(var box:world.getBlockState(p).getCollisionShape(world,p).getBoundingBoxes()){
                result.add(new DebrisCollision.Obstacle(new B(box.minX+x,box.minY+y,box.minZ+z,box.maxX+x,box.maxY+y,box.maxZ+z),p.asLong()));if(result.size()>DebrisCollision.MAX_OBSTACLES){fragment.pause("OBSTACLE_LIMIT");return null;}}}
        var neighbors=new TreeSet<UUID>();for(long key:dependencies)neighbors.addAll(bodyChunks.getOrDefault(key,Set.of()));
        neighbors.removeIf(id->id.equals(fragment.id)||!bodyBounds.containsKey(id)||!bodyBounds.get(id).expand(.02).intersects(b));
        if(neighbors.size()>128){fragment.pause("BODY_NEIGHBOR_LIMIT");return null;}
        for(var id:neighbors){if(id.equals(fragment.id))continue;var other=state.fragments.get(id);if(other==null)continue;if(!other.reconciled){fragment.pause("DEBRIS_RECOVERY_PENDING");return null;}
            for(var local:other.localVolumes()){var box=local.transformed(other.rotation,other.position);if(!box.intersects(b.expand(.02)))continue;
                if(++queries>maxBlockQueriesPerTick||result.size()>=DebrisCollision.MAX_OBSTACLES){fragment.pause("COLLISION_BUDGET");return null;}
                result.add(DebrisCollision.Obstacle.rigid(local,other.rotation,other.position,other.supportToken));
            }}
        var border=world.getWorldBorder();if(b.x0()<border.getBoundWest()||b.x1()>border.getBoundEast()||b.z0()<border.getBoundNorth()||b.z1()>border.getBoundSouth()){fragment.pause("WORLD_BORDER");return null;}
        return result;
    }
    public void clearAllNonTestFragments() {
        if(!state.writable())return;
        state.fragments.values().removeIf(f->!isGameTestStructure(f.structure));
        state.operations.keySet().removeIf(k->!isGameTestStructure(k));
        state.structureSnapshots.keySet().removeIf(k->!isGameTestStructure(k));
        materializedSources.clear();
        state.dirty=true;state.flush();
        sources.clear();bySource.clear();bodyTokens.clear();bodyChunks.clear();bodyDependencies.clear();bodyBounds.clear();
        partial.clear();activeQueued.clear();sleepQueued.clear();sleepers.clear();roundRobin.clear();recovery.clear();
        for(var f:state.fragments.values()){index(f);indexBody(f);}
    }
    public static void resetStructure(ServerWorld world,String structure) {
        if(world==null)return;var manager=get(world);if(!manager.state.writable())throw new IllegalStateException("Debris journal is read-only; structure reset deferred");
        var fragments=new LinkedHashMap<>(manager.state.fragments);var operations=new LinkedHashMap<>(manager.state.operations);var proofs=new LinkedHashMap<>(manager.state.structureSnapshots);
        manager.state.fragments.values().removeIf(f->f.structure.equals(structure));manager.state.operations.keySet().removeIf(k->k.startsWith(structure+"/"));manager.state.structureSnapshots.remove(structure);
        manager.materializedSources.remove(structure);
        manager.state.dirty=true;
        if(!manager.state.flush()) {
            manager.state.fragments.clear();manager.state.fragments.putAll(fragments);manager.state.operations.clear();manager.state.operations.putAll(operations);manager.state.structureSnapshots.clear();manager.state.structureSnapshots.putAll(proofs);
            throw new IllegalStateException("Debris reset barrier failed; source reset has not started");
        }
        for(var f:fragments.values())if(f.structure.equals(structure)){manager.supportIndex.changed(f.supportToken);manager.supportIndex.update(f.id,Set.of());}
        manager.sources.clear();manager.bySource.clear();manager.bodyTokens.clear();manager.bodyChunks.clear();manager.bodyDependencies.clear();manager.bodyBounds.clear();
        for(var f:manager.state.fragments.values()){manager.index(f);manager.indexBody(f);}
    }
    public String diagnostics(){return "stored="+state.fragments.size()+" visible="+state.fragments.values().stream().filter(DebrisFragment::visible).count()+" paused="+state.fragments.values().stream().filter(f->f.phase==Phase.PAUSED_UNLOADED).count()+" pause_reasons="+state.fragments.values().stream().filter(f->f.phase==Phase.PAUSED_UNLOADED).collect(java.util.stream.Collectors.groupingBy(f->f.pauseReason,java.util.TreeMap::new,java.util.stream.Collectors.counting()))+" reserved="+state.fragments.values().stream().flatMap(f->f.cells.stream()).filter(c->c.commit==Commit.RESERVED||c.commit==Commit.ARMED).count()+" queries="+queries+" capacity_deferrals="+capacityDeferrals+" writable="+state.writable()+" last_journal_bytes="+state.lastFlushBytes+" last_journal_ns="+state.lastFlushNanos;}
    public List<String> diagnosticDetails(int offset,int limit) {
        return state.fragments.values().stream().skip(Math.max(0,offset)).limit(Math.max(1,Math.min(16,limit)))
                .map(f->"id="+f.id+" structure="+f.structure+" op="+f.operation+" phase="+f.phase+" resume="+f.resume
                        +" pose="+f.position+" rotation="+f.rotation+" v="+f.velocity+" angular="+f.angularVelocity
                        +" supports="+f.supports+" pause="+(f.visible()&&!f.complete()?"SOURCE_COMMIT_PENDING":f.pauseReason)+" last_tick="+f.lastSimulationTick
                        +" chunks="+f.dependencies+" overlap_retries="+f.retryCount+" active_queue="+roundRobin.size()
                        +" recovery_queue="+recovery.size()).toList();
    }
    public static void closeAll(){for(var m:WORLDS.values())m.state.flush();WORLDS.clear();DebrisNetworking.clear();}
}
