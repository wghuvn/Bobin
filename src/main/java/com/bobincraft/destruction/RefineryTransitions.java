package com.bobincraft.destruction;

import com.bobincraft.warzone.*;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Generated structural-cell reconciliation (existing tank ledger extended to bridge/substation cells). Unresolved/protected cells are durable but never active tick work. */
public final class RefineryTransitions {
    private static final ThreadLocal<Boolean> GENERATING=ThreadLocal.withInitial(()->false);
    private static volatile long generationEpoch;
    private static final ThreadLocal<Long> SCOPE_EPOCH=ThreadLocal.withInitial(()->-1L);
    public record GenerationScope(boolean generating,long epoch) {}
    private static final ThreadLocal<Boolean> WRITING=ThreadLocal.withInitial(()->false);
    private static final Map<String,Map<Long,String>> GENERATED=new ConcurrentHashMap<>();
    private static final Set<Long> EXTERNALLY_CHANGED=ConcurrentHashMap.newKeySet();
    private static final Map<String,Map<Long,String>> REQUESTS=new LinkedHashMap<>();
    private static final ArrayDeque<String> READY=new ArrayDeque<>();
    private static final Set<String> QUEUED=new HashSet<>();
    private static final ArrayDeque<Long> CHUNK_WAKE=new ArrayDeque<>();
    private static final Set<Long> CHUNK_QUEUED=new HashSet<>();
    private static final ArrayDeque<Long> FALLBACK=new ArrayDeque<>();
    private static final Set<Long> FALLBACK_KEYS=new HashSet<>();
    private static final Set<String> DEBRIS_DIRTY=new HashSet<>(),DEBRIS_READY=new HashSet<>();
    private static final Set<ServerWorld> HEALED_WORLDS=Collections.newSetFromMap(new WeakHashMap<>());

    // Fast O(1) caches for BlockState <-> String encoding/decoding to eliminate repeated String.split and TreeMap allocations
    private static final Map<BlockState, String> ENCODE_CACHE = new ConcurrentHashMap<>(512);
    private static final Map<String, BlockState> DECODE_CACHE = new ConcurrentHashMap<>(512);

    private static RefineryTransitionState active;
    private static ServerWorld boundWorld;
    private static int lastApplied,lastChecked,lastMaintenance;
    public static int lastApplied(){return lastApplied;}
    private RefineryTransitions(){}
    public static void reset() {
        generationEpoch++;GENERATED.clear();EXTERNALLY_CHANGED.clear();REQUESTS.clear();READY.clear();QUEUED.clear();
        CHUNK_WAKE.clear();CHUNK_QUEUED.clear();FALLBACK.clear();FALLBACK_KEYS.clear();DEBRIS_DIRTY.clear();DEBRIS_READY.clear();
        HEALED_WORLDS.clear();active=null;boundWorld=null;
    }
    /** Direct mod-owned hooks avoid fragile mixins into package-private generator methods. */
    public static GenerationScope beginGeneration(boolean refinery) {
        var previous=new GenerationScope(GENERATING.get(),SCOPE_EPOCH.get());
        GENERATING.set(refinery);SCOPE_EPOCH.set(generationEpoch);return previous;
    }
    public static void endGeneration(GenerationScope previous){GENERATING.set(previous.generating());SCOPE_EPOCH.set(previous.epoch());}
    private static DestructibleStructure tank(int x,int y,int z) {
        for(var s:DestructibleStructureRegistry.spatialIndex().getInChunk(x>>4,z>>4))
            if(s.structureType().category()==DestructibleStructureType.Category.REFINERY_TANK) {
                if(StructureGeometrySnapshot.includes(s,x,y,z))return s;
            } else if(s.boundingRegions().stream().anyMatch(region->region.box().contains(new net.minecraft.util.math.Vec3d(x+.5,y+.5,z+.5)))
                    || s.overallBounds().expand(1.0).contains(x+.5,y+.5,z+.5))return s;
        return null;
    }
    public static String encode(BlockState state) {
        String cached = ENCODE_CACHE.get(state);
        if (cached != null) return cached;
        var props=new TreeMap<String,String>();
        for(var e:state.getEntries().entrySet())props.put(e.getKey().getName(),e.getValue().toString());
        var text=new StringBuilder(Registries.BLOCK.getId(state.getBlock()).toString());
        if(!props.isEmpty()){text.append('[');boolean first=true;for(var e:props.entrySet()){
            if(!first)text.append(',');text.append(e.getKey()).append('=').append(e.getValue());first=false;}text.append(']');}
        String built = text.toString();
        ENCODE_CACHE.put(state, built);
        return built;
    }
    public static BlockState decode(String text) {
        BlockState cached = DECODE_CACHE.get(text);
        if (cached != null) return cached;
        int bracket=text.indexOf('[');
        if(bracket>=0&&(!text.endsWith("]")||bracket+1>=text.length()-1))throw new IllegalArgumentException("Bad properties");
        String id=bracket<0?text:text.substring(0,bracket);
        var key=new Identifier(id);if(!Registries.BLOCK.containsId(key))throw new IllegalArgumentException("Missing block "+id);
        BpSpec spec=BpSpec.of(id);
        if(bracket>=0)for(String pair:text.substring(bracket+1,text.length()-1).split(",")){
            String[] kv=pair.split("=",2);if(kv.length!=2)throw new IllegalArgumentException("Bad state");spec=spec.with(kv[0],kv[1]);}
        BlockState result=BlueprintBlitter.state(spec);
        if(!encode(result).equals(text))throw new IllegalArgumentException("Unresolvable state "+text);
        DECODE_CACHE.put(text, result);
        return result;
    }
    public static void generated(int x,int y,int z,BlockState state) {
        if(!GENERATING.get()||SCOPE_EPOCH.get()!=generationEpoch)return;var s=tank(x,y,z);if(s==null)return;
        if(state.isAir()&&s.structureType().category()!=DestructibleStructureType.Category.REFINERY_TANK)return;
        GENERATED.computeIfAbsent(s.structureId(),id->new ConcurrentHashMap<>()).put(BlockPos.asLong(x,y,z),encode(state));
    }
    private static boolean relevant(ServerWorld world) {
        return world.getRegistryKey().equals(WarzoneDimension.WORLD)||world.getServer().getWorld(WarzoneDimension.WORLD)==null;
    }
    public static void external(ServerWorld world,BlockPos pos) {
        if(WRITING.get()||GENERATING.get()||!relevant(world))return;
        var s=tank(pos.getX(),pos.getY(),pos.getZ());if(s==null)return;
        EXTERNALLY_CHANGED.add(pos.asLong());var state=RefineryTransitionState.get(world);
        if(!state.writable()||state.frozen(s.structureId()))return;
        var ledger=state.ledgers.computeIfAbsent(s.structureId(),id->new TransitionLedger());
        long before=ledger.mutations();ledger.protect(pos.asLong());if(before!=ledger.mutations())state.markDirty();
    }
    public static void bind(ServerWorld world) {
        if(!relevant(world)||boundWorld==world)return;
        boundWorld=world;active=RefineryTransitionState.get(world);READY.clear();QUEUED.clear();
        CHUNK_WAKE.clear();CHUNK_QUEUED.clear();FALLBACK.clear();FALLBACK_KEYS.clear();
        if(!active.writable())return;
        for(var e:active.ledgers.entrySet())if(e.getValue().revision==StructureGeometrySnapshot.REVISION) {
            e.getValue().rebuildScheduling();schedule(e.getKey(),e.getValue());DEBRIS_DIRTY.add(e.getKey());
        }
    }
    private static void schedule(String id,TransitionLedger ledger) {
        if(ledger.hasReady()&&QUEUED.add(id))READY.add(id);
    }
    public static void chunkLoaded(ServerWorld world,int x,int z) {
        if(world!=boundWorld)return;
        long key=((long)x&0xffffffffL)|((long)z<<32);
        boolean waiting=active!=null&&active.ledgers.values().stream().anyMatch(l->l.deferredChunks().contains(key));
        if(waiting&&CHUNK_QUEUED.add(key))CHUNK_WAKE.add(key);
    }
    private static int ingestGenerated(int budget,Set<String> changed) {
        int work=0;
        for(var e:GENERATED.entrySet()) {
            if(active.frozen(e.getKey()))continue;
            var l=active.ledgers.computeIfAbsent(e.getKey(),id->new TransitionLedger());
            if(l.revision!=StructureGeometrySnapshot.REVISION)continue;
            var it=e.getValue().entrySet().iterator();
            while(it.hasNext()&&work<budget) {
                var cell=it.next();long p=cell.getKey();String state=cell.getValue();work++;
                if(!EXTERNALLY_CHANGED.contains(p)&&l.noteOwned(p,state))changed.add(e.getKey());
                e.getValue().remove(p,state);
            }
            schedule(e.getKey(),l);if(work>=budget)break;
        }
        return work;
    }
    /**
     * Seeds baseline ownership from the authoritative INTACT blueprint for any structure cells not yet in the ledger.
     */
    private static void seedReferenceOwnership(WarzoneTerrain terrain, DestructibleStructure s, TransitionLedger l) {
        if (terrain == null || s == null || l == null) return;
        Blueprint reference = StructureCollapseManager.getStageBlueprint(terrain, s, StructureDamageState.INTACT);
        if (reference == null) return;
        reference.forEachCell((x, y, z, spec, mode) -> {
            if (spec == null) return;
            if (spec.isAir() && s.structureType().category() != DestructibleStructureType.Category.REFINERY_TANK) return;
            long packed = BlockPos.asLong(x, y, z);
            if (!EXTERNALLY_CHANGED.contains(packed) && !l.owned.containsKey(packed) && !l.protectedCells.contains(packed)) {
                l.noteOwned(packed, encode(BlueprintBlitter.state(spec)));
            }
        });
    }
    /**
     * One-time automatic world heal (v6): clears any leftover floating bridge slabs from v5 and restores clean
     * bridge geometry so full-span bridge collapse after pile destruction works immediately.
     */
    private static void migrateAndHealExistingWorldIfNeeded(ServerWorld world, WarzoneTerrain terrain) {
        if (world == null || terrain == null || !HEALED_WORLDS.add(world)) return;
        if (!world.getRegistryKey().equals(WarzoneDimension.WORLD)) return;
        try {
            Path dataDir = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data");
            Files.createDirectories(dataDir);
            Path marker = dataDir.resolve("bobin_craft_realistic_v7.marker");
            if (Files.exists(marker)) return;

            StructureDamageManager.ensureRegistryReady(world);
            var debris = com.bobincraft.destruction.debris.StructureDebrisManager.get(world);
            debris.clearAllNonTestFragments();
            EXTERNALLY_CHANGED.clear();
            if (active != null && active.writable()) {
                active.ledgers.clear();
                active.markDirty();
            }
            if (terrain.isAuthoredZone()) {
                for (ZonePlan.Crossing b : terrain.zonePlan().crossings()) {
                    Blueprint intactBridge = ZoneInfrastructure.bridgeBlueprint(terrain, b);
                    for (int u = -b.negative(); u <= b.positive(); u++) {
                        int deckY = b.floor(u);
                        for (int v = -6; v <= 6; v++) {
                            int x = b.wx(u, v), z = b.wz(u, v);
                            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                            int gy = StructureCollapseManager.rawHeight(terrain, x, z);
                            for (int y = gy + 1; y < deckY - 2; y++) {
                                BpSpec spec = intactBridge.inBounds(x, y, z) ? intactBridge.get(x, y, z) : null;
                                if (spec == null || spec.isAir()) {
                                    BlockPos pos = new BlockPos(x, y, z);
                                    BlockState cur = world.getBlockState(pos);
                                    if (!cur.isAir() && !cur.isOf(Blocks.WATER)) {
                                        BlockState restore = y < WarzoneTerrain.SEA_LEVEL ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
                                        com.bobincraft.destruction.debris.StructureDebrisManager.writeSource(world, pos, restore);
                                    }
                                }
                            }
                        }
                    }
                    intactBridge.forEachCell((x, y, z, spec, mode) -> {
                        if (!world.isChunkLoaded(x >> 4, z >> 4) || spec == null) return;
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState targetState = BlueprintBlitter.state(spec);
                        if (!world.getBlockState(pos).equals(targetState)) {
                            com.bobincraft.destruction.debris.StructureDebrisManager.writeSource(world, pos, targetState);
                        }
                    });
                }
            }
            long tick = world.getTime();
            DestructionScenarioDirector.resetAll();
            StructureFireManager.clear();
            for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
                s.reset(tick);
                DestructibleStructureRegistry.updateSnapshot(s);
                enqueue(terrain, s, StructureDamageState.INTACT);
            }
            StructurePersistentState.get(world).clearAll(tick);
            for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
                StructureNetworkSync.broadcastStructureState(world, s);
            }
            Files.writeString(marker, "v7\n");
        } catch (Exception ignored) {
        }
    }
    /** Graceful shutdown drains the finite generation backlog before persistent-state saving. */
    public static void flushProvenance(ServerWorld world) {
        bind(world);if(world!=boundWorld||active==null||!active.writable())return;
        var changed=new LinkedHashSet<String>();
        while(ingestGenerated(512,changed)>0) { /* finite, coalesced tank cells; no block writes */ }
        if(!changed.isEmpty())active.markDirty();
    }
    public static synchronized void enqueue(WarzoneTerrain terrain,DestructibleStructure s,StructureDamageState target) {
        boolean useExactBridgeBreach = s.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION
                && target != StructureDamageState.INTACT
                && !s.localBreachCells().isEmpty();
        var bp = StructureCollapseManager.getStageBlueprint(
                terrain, s, useExactBridgeBreach ? StructureDamageState.INTACT : target);
        if(bp==null)return;
        Map<Long,String> desired=new LinkedHashMap<>();
        var reference=StructureCollapseManager.getStageBlueprint(terrain,s,StructureDamageState.INTACT);
        bp.forEachCell((x,y,z,spec,mode)->{
            var original=reference!=null?reference.get(x,y,z):null;
            if((original==null||original.isAir())&&spec.isAir()&&target!=StructureDamageState.INTACT)return;
            if(target==StructureDamageState.INTACT&&spec.isAir()&&s.structureType().category()!=DestructibleStructureType.Category.REFINERY_TANK)return;
            desired.put(BlockPos.asLong(x,y,z),encode(BlueprintBlitter.state(spec)));
        });
        if (target != StructureDamageState.INTACT) {
            for (var entry : s.localBreachCells().entrySet()) {
                desired.put(entry.getKey(), encode(BlueprintBlitter.state(entry.getValue())));
            }
        }
        if(StructureDamageTrace.enabled())StructureDamageTrace.add("SCHEDULE structure="+s.structureId()+" target="+target+" cells="+desired.size()+" ownership="+status(s.structureId()));
        REQUESTS.put(s.structureId(),desired);s.setTransitionProgress(target,1);
    }
    /**
     * Enqueues a localized breach (hole punched by RPG, tank shell, FPV drone, or GROM-7) immediately,
     * even when the structure's overall HP has not yet crossed a coarse stage threshold.
     */
    public static synchronized void enqueueLocalBreach(WarzoneTerrain terrain, DestructibleStructure s, Map<Long, BpSpec> breachCells) {
        if (s == null || breachCells == null || breachCells.isEmpty()) return;
        s.recordLocalBreachCells(breachCells);
        enqueue(terrain, s, s.damageStage());
    }
    private static int wakeChunks(ServerWorld world,int budget) {
        int work=0;
        if(world.getTime()%40==0) {
            int checks=Math.min(16,FALLBACK.size());
            while(checks-->0) {
                long key=FALLBACK.poll();
                boolean waiting=active.ledgers.values().stream().anyMatch(l->l.deferredChunks().contains(key));
                if(waiting) {
                    if(world.isChunkLoaded((int)key,(int)(key>>32))&&CHUNK_QUEUED.add(key))CHUNK_WAKE.add(key);
                    FALLBACK.add(key);
                } else FALLBACK_KEYS.remove(key);
            }
        }
        int notifications=Math.min(8,CHUNK_WAKE.size());
        while(!CHUNK_WAKE.isEmpty()&&work<budget&&notifications-->0) {
            long key=CHUNK_WAKE.poll();CHUNK_QUEUED.remove(key);boolean remaining=false;
            for(var e:active.ledgers.entrySet()) {
                var l=e.getValue();if(l.revision!=StructureGeometrySnapshot.REVISION)continue;
                work+=l.wakeChunk(key,Math.max(0,budget-work));schedule(e.getKey(),l);
                remaining|=l.deferredChunks().contains(key);
            }
            if(remaining&&CHUNK_QUEUED.add(key))CHUNK_WAKE.add(key);
        }
        return work;
    }

    /**
     * Full-Crossing Live Bridge Support Solver:
     * Evaluates the ENTIRE bridge crossing (`-b.negative() .. +b.positive()`) across all sections so that when a
     * pile is destroyed, the entire unsupported span both BEFORE and AFTER that pile (even across section boundaries!)
     * and any floating `4x6` island, overhang, copper truss, or `iron_bars` fence collapses into the river!
     */
    private static void sweepFloatingBridgeBlocksInWorld(ServerWorld world, WarzoneTerrain terrain, DestructibleStructure s) {
        if (world == null || terrain == null || s == null) return;
        if (s.structureType().category() != DestructibleStructureType.Category.BRIDGE_SECTION) return;
        if (s.damageStage() == StructureDamageState.INTACT && s.localBreachCells().isEmpty()) return;
        ZonePlan.Crossing b = StructureCollapseManager.findCrossing(terrain, s);
        if (b == null) return;

        int minU = -b.negative();
        int maxU = b.positive();
        int totalU = maxU - minU + 1;
        if (totalU <= 0) return;

        boolean previous = WRITING.get();
        WRITING.set(true);
        try {
            // 1. Check every pier along the entire bridge crossing to see which piles (v = -3 and v = +3) are intact in the live world
            Set<Integer> intactLeftPiers = new HashSet<>();
            Set<Integer> intactRightPiers = new HashSet<>();
            Set<Integer> brokenLeftPiers = new HashSet<>();
            Set<Integer> brokenRightPiers = new HashSet<>();

            for (int u = minU; u <= maxU; u++) {
                if (Math.floorMod(u, 16) != 0 || u <= minU + 12 || u >= maxU - 12) continue;
                int deckY = b.floor(u);
                for (int sideV : new int[]{-3, 3}) {
                    int wx = b.wx(u, sideV), wz = b.wz(u, sideV);
                    if (!world.isChunkLoaded(wx >> 4, wz >> 4)) {
                        if (sideV < 0) intactLeftPiers.add(u); else intactRightPiers.add(u);
                        continue;
                    }
                    int gy = StructureCollapseManager.rawHeight(terrain, wx, wz);
                    if (gy >= deckY - 2) {
                        if (sideV < 0) intactLeftPiers.add(u); else intactRightPiers.add(u);
                        continue;
                    }
                    int gap = 0;
                    int firstGapY = -1;
                    for (int y = gy + 1; y < deckY - 1; y++) {
                        BlockState st = world.getBlockState(new BlockPos(wx, y, wz));
                        if (st.isAir() || st.isOf(Blocks.WATER) || st.isOf(Blocks.IRON_BARS)) {
                            gap++;
                            if (firstGapY < 0) firstGapY = y;
                        }
                    }
                    if (gap >= 1) {
                        if (sideV < 0) brokenLeftPiers.add(u); else brokenRightPiers.add(u);
                        // Clear any remaining floating upper blocks of this broken pile
                        for (int y = Math.max(gy + 1, firstGapY); y <= deckY - 2; y++) {
                            BlockPos p = new BlockPos(wx, y, wz);
                            if (!world.getBlockState(p).isAir()) {
                                com.bobincraft.destruction.debris.StructureDebrisManager.writeSource(world, p, Blocks.AIR.getDefaultState());
                            }
                        }
                    } else {
                        if (sideV < 0) intactLeftPiers.add(u); else intactRightPiers.add(u);
                    }
                }
            }

            // 2. Build 2D grid of live deck/girder presence across the entire bridge crossing (minU..maxU x v=-4..4)
            boolean[][] hasDeck = new boolean[totalU][9];
            boolean[][] anchored = new boolean[totalU][9];
            for (int u = minU; u <= maxU; u++) {
                int ui = u - minU;
                int deckY = b.floor(u);
                for (int v = -4; v <= 4; v++) {
                    int vi = v + 4;
                    int wx = b.wx(u, v), wz = b.wz(u, v);
                    if (!world.isChunkLoaded(wx >> 4, wz >> 4)) {
                        hasDeck[ui][vi] = true;
                        anchored[ui][vi] = true;
                        continue;
                    }
                    BlockState dSt = world.getBlockState(new BlockPos(wx, deckY, wz));
                    BlockState gSt = world.getBlockState(new BlockPos(wx, deckY - 1, wz));
                    boolean present = (!dSt.isAir() && !dSt.isOf(Blocks.WATER) && !dSt.isOf(Blocks.IRON_BARS))
                            || (!gSt.isAir() && !gSt.isOf(Blocks.WATER) && !gSt.isOf(Blocks.IRON_BARS));
                    hasDeck[ui][vi] = present;
                    if (!present) continue;

                    int gy = StructureCollapseManager.rawHeight(terrain, wx, wz);
                    boolean onBank = (deckY <= gy + 2) || (u <= minU + 2) || (u >= maxU - 2);
                    boolean onIntactPier = (v <= 0 && intactLeftPiers.contains(u)) || (v >= 0 && intactRightPiers.contains(u));
                    if (onBank || onIntactPier) {
                        anchored[ui][vi] = true;
                    }
                }
            }

            // 3. Apply the balanced, organic rounded/jagged collapse profile around each broken pile (across section boundaries!),
            //    and let the 2D Graph Reachability BFS in Step 4 drop any chunk that gets completely cut off between two gaps!
            for (int pierU : brokenLeftPiers) {
                boolean alsoRightBroken = brokenRightPiers.contains(pierU);
                for (int v = -4; v <= 4; v++) {
                    double radNeg = alsoRightBroken
                            ? StructuralSupportSolver.dualPileCollapseRadiusU(pierU, v, false)
                            : StructuralSupportSolver.singlePileCollapseRadiusU(pierU, v, -1, false);
                    double radPos = alsoRightBroken
                            ? StructuralSupportSolver.dualPileCollapseRadiusU(pierU, v, true)
                            : StructuralSupportSolver.singlePileCollapseRadiusU(pierU, v, -1, true);
                    if (radNeg < 0 && radPos < 0) continue;
                    int u0 = Math.max(minU + 2, (int) Math.floor(pierU - Math.max(0.0, radNeg)));
                    int u1 = Math.min(maxU - 2, (int) Math.ceil(pierU + Math.max(0.0, radPos)));
                    for (int u = u0; u <= u1; u++) {
                        double lim = (u < pierU) ? radNeg : radPos;
                        if (Math.abs(u - pierU) <= lim && !anchored[u - minU][v + 4]) {
                            hasDeck[u - minU][v + 4] = false;
                        }
                    }
                }
            }
            for (int pierU : brokenRightPiers) {
                if (brokenLeftPiers.contains(pierU)) continue;
                for (int v = -4; v <= 4; v++) {
                    double radNeg = StructuralSupportSolver.singlePileCollapseRadiusU(pierU, v, 1, false);
                    double radPos = StructuralSupportSolver.singlePileCollapseRadiusU(pierU, v, 1, true);
                    if (radNeg < 0 && radPos < 0) continue;
                    int u0 = Math.max(minU + 2, (int) Math.floor(pierU - Math.max(0.0, radNeg)));
                    int u1 = Math.min(maxU - 2, (int) Math.ceil(pierU + Math.max(0.0, radPos)));
                    for (int u = u0; u <= u1; u++) {
                        double lim = (u < pierU) ? radNeg : radPos;
                        if (Math.abs(u - pierU) <= lim && !anchored[u - minU][v + 4]) {
                            hasDeck[u - minU][v + 4] = false;
                        }
                    }
                }
            }

            // 4. Run 2D Graph Reachability BFS from anchored[ui][vi] through hasDeck[ui][vi] across the ENTIRE bridge!
            //    Any floating 4x6 or 9x8 island not connected to an intact pier or bank is unreachable and collapses!
            boolean[][] reachable = new boolean[totalU][9];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (int ui = 0; ui < totalU; ui++) {
                for (int vi = 0; vi < 9; vi++) {
                    if (anchored[ui][vi] && hasDeck[ui][vi]) {
                        reachable[ui][vi] = true;
                        queue.add(new int[]{ui, vi});
                    }
                }
            }
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!queue.isEmpty()) {
                int[] cur = queue.poll();
                int ui = cur[0], vi = cur[1];
                for (int[] d : dirs) {
                    int nui = ui + d[0], nvi = vi + d[1];
                    if (nui < 0 || nui >= totalU || nvi < 0 || nvi >= 9) continue;
                    if (!hasDeck[nui][nvi] || reachable[nui][nvi]) continue;
                    reachable[nui][nvi] = true;
                    queue.add(new int[]{nui, nvi});
                }
            }

            // 5. Remove all unreachable bridge columns (and settle their deck blocks onto the riverbed below!) + clean floating iron_bars/copper
            var debrisManager = com.bobincraft.destruction.debris.StructureDebrisManager.get(world);
            for (int u = minU; u <= maxU; u++) {
                int ui = u - minU;
                int deckY = b.floor(u);
                for (int v = -4; v <= 4; v++) {
                    int vi = v + 4;
                    int wx = b.wx(u, v), wz = b.wz(u, v);
                    if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                    int gy = StructureCollapseManager.rawHeight(terrain, wx, wz);

                    if (!reachable[ui][vi]) {
                        BlockState settledMat = null;
                        for (int h = deckY + 7; h >= Math.max(gy + 2, deckY - 2); h--) {
                            BlockPos p = new BlockPos(wx, h, wz);
                            BlockState st = world.getBlockState(p);
                            if (st.isAir() || st.isOf(Blocks.WATER)) continue;
                            if (settledMat == null && !st.isOf(Blocks.IRON_BARS) && (h == deckY || h == deckY - 1)) {
                                settledMat = st;
                            }
                            com.bobincraft.destruction.debris.StructureDebrisManager.writeSource(world, p, Blocks.AIR.getDefaultState());
                        }
                        if (settledMat != null) {
                            debrisManager.settleCollapsedBridgeBlock(new BlockPos(wx, deckY, wz), settledMat, gy);
                        }
                        continue;
                    }

                    // For reachable columns, verify iron_bars and upper copper trusses have unbroken support down to the deck
                    BlockPos deckPos = new BlockPos(wx, deckY, wz);
                    BlockState deckState = world.getBlockState(deckPos);
                    boolean solidDeckHere = !deckState.isAir() && !deckState.isOf(Blocks.WATER) && !deckState.isOf(Blocks.IRON_BARS);

                    for (int h = deckY - 2; h <= deckY + 7; h++) {
                        BlockPos pos = new BlockPos(wx, h, wz);
                        BlockState st = world.getBlockState(pos);
                        if (st.isAir()) continue;
                        boolean isBars = st.isOf(Blocks.IRON_BARS);
                        boolean isCopper = st.isOf(Blocks.WAXED_OXIDIZED_COPPER) || st.isOf(Blocks.OXIDIZED_COPPER);
                        if (!isBars && !isCopper) continue;

                        boolean shouldRemove = false;
                        if (h == deckY + 1 && !solidDeckHere) {
                            shouldRemove = true;
                        } else if (h >= deckY + 2) {
                            int postU = Math.round(u / 8.0f) * 8;
                            int pui = postU - minU;
                            boolean postOk = (pui >= 0 && pui < totalU && reachable[pui][vi]);
                            if (postOk) {
                                int pwx = b.wx(postU, v), pwz = b.wz(postU, v);
                                if (world.isChunkLoaded(pwx >> 4, pwz >> 4)) {
                                    int pDeckY = b.floor(postU);
                                    for (int py = pDeckY; py <= pDeckY + 5; py++) {
                                        BlockState pst = world.getBlockState(new BlockPos(pwx, py, pwz));
                                        if (pst.isAir() || pst.isOf(Blocks.WATER)) {
                                            postOk = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!postOk || (world.getBlockState(pos.down()).isAir() && world.getBlockState(pos.up()).isAir())) {
                                shouldRemove = true;
                            }
                        } else if (isBars && world.getBlockState(pos.down()).isAir()) {
                            shouldRemove = true;
                        }

                        if (shouldRemove) {
                            com.bobincraft.destruction.debris.StructureDebrisManager.writeSource(world, pos, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
        } finally {
            WRITING.set(previous);
        }
    }

    public static synchronized int tick(ServerWorld world,int budget) {
        lastApplied=lastChecked=lastMaintenance=0;bind(world);
        if(world!=boundWorld||active==null||!active.writable()||!StructurePersistentState.get(world).supportsTransitions())return 0;
        WarzoneTerrain terrain = StructureDamageManager.resolveTerrain(world);
        migrateAndHealExistingWorldIfNeeded(world, terrain);
        var changed=new LinkedHashSet<String>();
        var debris=com.bobincraft.destruction.debris.StructureDebrisManager.get(world);
        lastMaintenance=ingestGenerated(512,changed);
        for(var e:REQUESTS.entrySet()) {
            if(active.frozen(e.getKey()))continue;var s=DestructibleStructureRegistry.get(e.getKey());if(s==null)continue;
            var l=active.ledgers.computeIfAbsent(e.getKey(),id->new TransitionLedger());
            if(l.revision!=StructureGeometrySnapshot.REVISION)continue;
            l.protectedCells.clear();l.conflicts.clear();
            seedReferenceOwnership(terrain, s, l);
            l.variant=s.destroyedVariantIndex();
            l.stage=s.targetStage()==StructureDamageState.INTACT ? 0 : Math.max(s.targetStage().index, s.localBreachRevision() > 0 ? 1 : 0);
            var desired=new LinkedHashMap<>(e.getValue());
            if(l.stage>0)desired.replaceAll((pos,next)->debris.committedSource(e.getKey(),pos)?"minecraft:air":next);
            l.retarget(desired);
            changed.add(e.getKey());schedule(e.getKey(),l);
        }
        REQUESTS.clear();lastMaintenance+=wakeChunks(world,64);
        DEBRIS_DIRTY.addAll(changed);
        for(var entry:active.ledgers.entrySet()) {
            if(!DEBRIS_DIRTY.contains(entry.getKey()))continue;
            DEBRIS_READY.remove(entry.getKey());
            var ledger=entry.getValue();if(active.frozen(entry.getKey())||ledger.revision!=StructureGeometrySnapshot.REVISION)continue;
            if(ledger.stage==0){DEBRIS_READY.add(entry.getKey());DEBRIS_DIRTY.remove(entry.getKey());continue;}
            var s = DestructibleStructureRegistry.get(entry.getKey());
            String opTag = "refinery/"+ledger.operation+"/"+ledger.stage+"_"+(s!=null?s.localBreachRevision():0);
            var source=new ArrayList<com.bobincraft.destruction.debris.DebrisPlan.Source>();
            for(var cell:ledger.pending.entrySet()) {
                String ownedState = ledger.owned.get(cell.getKey());
                if("minecraft:air".equals(cell.getValue())&&ownedState!=null
                        &&!"minecraft:air".equals(ownedState)
                        &&!"bobin_craft:crude_oil".equals(ownedState)
                        &&!ownedState.startsWith("minecraft:iron_bars")
                        &&!ledger.protectedCells.contains(cell.getKey())) {
                    var pos=BlockPos.fromLong(cell.getKey());
                    source.add(new com.bobincraft.destruction.debris.DebrisPlan.Source(pos.getX(),pos.getY(),pos.getZ(),ownedState));
                }
            }
            if(debris.reserve(entry.getKey(),opTag,source)) {
                DEBRIS_READY.add(entry.getKey());DEBRIS_DIRTY.remove(entry.getKey());
                debris.prearm(entry.getKey(),opTag,source,1024);
                debris.syncDurableBarrier();
            }
        }
        int effectiveBudget = Math.max(budget, 256);
        Set<String> touchedBridges = new LinkedHashSet<>();
        while(lastChecked<Math.max(0,effectiveBudget)&&!READY.isEmpty()) {
            String id=READY.poll();QUEUED.remove(id);var l=active.ledgers.get(id);
            if(l==null)continue;Long packed=l.poll();lastChecked++;
            if(packed==null){continue;}
            var s = DestructibleStructureRegistry.get(id);
            String operation="refinery/"+l.operation+"/"+l.stage+"_"+(s!=null?s.localBreachRevision():0);
            long before=l.mutations();
            if(l.actionable(packed)) {
                BlockPos pos=BlockPos.fromLong(packed);
                if(!world.isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)) {
                    l.defer(packed);long key=TransitionLedger.chunk(packed);
                    if(FALLBACK_KEYS.add(key))FALLBACK.add(key);
                }
                else if(!world.isInBuildLimit(pos))l.protect(packed);
                else {
                    BlockState current=world.getBlockState(pos);
                    if(current.hasBlockEntity()) {
                        var be=world.getBlockEntity(pos);
                        boolean originalEmptyHopper=current.isOf(Blocks.HOPPER)&&be instanceof net.minecraft.inventory.Inventory inv&&inv.isEmpty();
                        if(!originalEmptyHopper)l.protect(packed);
                    }
                    String encodedCurrent = encode(current);
                    if (l.owned.containsKey(packed) && !Objects.equals(l.owned.get(packed), encodedCurrent)
                            && (l.stage == 0 || !current.hasBlockEntity())) {
                        l.owned.put(packed, encodedCurrent);
                    }
                    if(l.actionable(packed)&&l.canApply(packed,encodedCurrent)) {
                        try {
                            BlockState next=decode(l.pending.get(packed));
                            boolean removal=l.stage>0&&next.isAir()&&!current.isAir()
                                    &&!current.isOf(CombatZoneBlocks.CRUDE_OIL)
                                    &&!current.isOf(Blocks.IRON_BARS)
                                    &&debris.isReservedCell(id,operation,packed);
                            if(removal&&(!DEBRIS_READY.contains(id)||!debris.beforeRemove(id,operation,pos,current))) {
                                debris.syncDurableBarrier();
                                removal = debris.beforeRemove(id,operation,pos,current);
                            }
                            boolean previous=WRITING.get();WRITING.set(true);
                            try {com.bobincraft.destruction.debris.StructureDebrisManager.writeSource(world,pos,next);} finally {WRITING.set(previous);}
                            if(world.getBlockState(pos).equals(next)){
                                if(removal)debris.removed(id,operation,pos);
                                l.applied(packed);lastApplied++;
                                if(s!=null&&s.structureType().category()==DestructibleStructureType.Category.BRIDGE_SECTION&&l.stage>0) {
                                    touchedBridges.add(id);
                                }
                                if(StructureDamageTrace.enabled())StructureDamageTrace.add("COMMIT structure="+id+" operation="+operation+" cell="+packed+" debris="+removal);
                            }
                            else {l.protect(packed);if(StructureDamageTrace.enabled())StructureDamageTrace.add("WRITE_REJECTED structure="+id+" operation="+operation+" cell="+packed);}
                        } catch(IllegalArgumentException invalid){l.protect(packed);}
                    }
                }
            }
            if(l.protectedCells.contains(packed)||!l.pending.containsKey(packed))
                debris.reject(id,operation,BlockPos.fromLong(packed));
            if(before!=l.mutations())changed.add(id);
            schedule(id,l);
        }
        for (String bridgeId : touchedBridges) {
            DestructibleStructure bs = DestructibleStructureRegistry.get(bridgeId);
            if (bs != null) sweepFloatingBridgeBlocksInWorld(world, terrain, bs);
        }
        if(!changed.isEmpty()) {
            active.markDirty();
            for(String id:changed) {
                var s=DestructibleStructureRegistry.get(id);var l=active.ledgers.get(id);
                if(s!=null&&l!=null) {
                    boolean done=l.pending.isEmpty()&&l.conflicts.isEmpty();
                    s.setTransitionProgress(s.targetStage(),done?0:1);StructurePersistentState.get(world).recordStructure(s);
                }
            }
        }
        return lastChecked;
    }
    public static synchronized int pendingCount() {
        var ids=new HashSet<>(REQUESTS.keySet());
        if(active!=null)for(var e:active.ledgers.entrySet())if(!e.getValue().pending.isEmpty())ids.add(e.getKey());return ids.size();
    }
    public static synchronized int pendingCells() {
        int count=0;if(active!=null)for(var ledger:active.ledgers.values())count+=ledger.pending.size();
        for(var e:REQUESTS.entrySet())if(active==null||!active.ledgers.containsKey(e.getKey()))count+=e.getValue().size();return count;
    }
    public static String status(String id) {
        if(active!=null&&active.frozen(id))return "UNSUPPORTED_GEOMETRY_REVISION: original entry preserved";
        if(active==null||!active.ledgers.containsKey(id))return "READY_ON_DEMAND: reference blueprint available";
        var l=active.ledgers.get(id);return "revision="+l.revision+" pending="+l.pending.size()+" conflicts="+l.conflicts.size()
                +" owned="+l.owned.size()+l.counts()+" last_checks="+lastChecked+" last_writes="+lastApplied+" maintenance="+lastMaintenance;
    }
}
