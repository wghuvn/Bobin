package com.bobincraft.destruction;

import com.bobincraft.warzone.WarzoneTerrain;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Budgeted, chunk-grouped server block update queue for structure stage transitions (Section 6.1 & Section 3.3).
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Standard Differential Transition ({@link #enqueueTransition})</b>: Processes up to {@code maxBlockChangesPerTick} (64)
 *       blocks per tick across at most {@code maxConcurrentStructureTransitions} (2) structures.</li>
 *   <li><b>Staged Structural Collapse ({@link #enqueueStagedTransition})</b>: Orders block changes radially outward
 *       from the impact point and top-down, holding main structural span removal during the initial blast/fracture
 *       phase ({@code crackPhaseTicks}) and then progressively collapsing blocks in sync with the falling keyframe bones
 *       so structures never vanish in a single frame.</li>
 * </ul>
 */
public final class StructureUpdateQueue {
    public static int maxBlockChangesPerTick = 64;
    public static int maxConcurrentStructureTransitions = 2;

    public static final class TransitionTask {
        public final String structureId;
        public final StructureDamageState fromStage;
        public final StructureDamageState targetStage;
        private final List<StructureCollapseManager.BlockChange> orderedChanges;
        private int cursor;
        private int crackPhaseTicksRemaining;
        private final int crackPhaseRateCap;
        private final int mainCollapseRateCap;

        public TransitionTask(String structureId,
                              StructureDamageState fromStage,
                              StructureDamageState targetStage,
                              List<StructureCollapseManager.BlockChange> changes,
                              int startCursor) {
            this(structureId, fromStage, targetStage, changes, startCursor, 0, maxBlockChangesPerTick, maxBlockChangesPerTick);
        }

        public TransitionTask(String structureId,
                              StructureDamageState fromStage,
                              StructureDamageState targetStage,
                              List<StructureCollapseManager.BlockChange> changes,
                              int startCursor,
                              int crackPhaseTicks,
                              int crackPhaseRateCap,
                              int mainCollapseRateCap) {
            this.structureId = Objects.requireNonNull(structureId);
            this.fromStage = Objects.requireNonNull(fromStage);
            this.targetStage = Objects.requireNonNull(targetStage);
            this.orderedChanges = new ArrayList<>(changes);
            this.cursor = Math.max(0, Math.min(startCursor, changes.size()));
            this.crackPhaseTicksRemaining = Math.max(0, crackPhaseTicks);
            this.crackPhaseRateCap = Math.max(1, crackPhaseRateCap);
            this.mainCollapseRateCap = Math.max(1, mainCollapseRateCap);
        }

        public int remaining() {
            return Math.max(0, orderedChanges.size() - cursor);
        }

        public int total() {
            return orderedChanges.size();
        }

        public int cursor() {
            return cursor;
        }

        public boolean isDone() {
            return cursor >= orderedChanges.size();
        }
    }

    private static final Map<String, TransitionTask> ACTIVE_OR_PENDING = new LinkedHashMap<>();
    private static int lastTickBlockChanges = 0;

    private static boolean managed(DestructibleStructure s) {
        return switch(s.structureType().category()) {
            case REFINERY_TANK,SUBSTATION_TRANSFORMER,SUBSTATION_EQUIPMENT,BRIDGE_SECTION -> true;
            default -> false;
        };
    }
    private StructureUpdateQueue() {}

    public static synchronized void enqueueTransition(WarzoneTerrain terrain,
                                                      DestructibleStructure structure,
                                                      StructureDamageState fromStage,
                                                      StructureDamageState targetStage,
                                                      int resumeCursor) {
        enqueueInternal(terrain, structure, fromStage, targetStage, resumeCursor, null, 0, maxBlockChangesPerTick, maxBlockChangesPerTick);
    }

    /**
     * Enqueues a staged structural failure where the initial fracture phase (`crackPhaseTicks`) only breaks
     * blocks immediately adjacent to `impactOrigin` (`crackPhaseRateCap` blocks/tick), followed by progressive
     * outward and top-down collapse (`mainCollapseRateCap` blocks/tick) synchronized with the keyframe animation.
     */
    public static synchronized void enqueueStagedTransition(WarzoneTerrain terrain,
                                                            DestructibleStructure structure,
                                                            StructureDamageState fromStage,
                                                            StructureDamageState targetStage,
                                                            Vec3d impactOrigin,
                                                            int crackPhaseTicks,
                                                            int crackPhaseRateCap,
                                                            int mainCollapseRateCap) {
        enqueueInternal(terrain, structure, fromStage, targetStage, 0,
                impactOrigin, crackPhaseTicks, crackPhaseRateCap, mainCollapseRateCap);
    }

    private static void enqueueInternal(WarzoneTerrain terrain,
                                        DestructibleStructure structure,
                                        StructureDamageState fromStage,
                                        StructureDamageState targetStage,
                                        int resumeCursor,
                                        Vec3d impactOrigin,
                                        int crackPhaseTicks,
                                        int crackPhaseRateCap,
                                        int mainCollapseRateCap) {
        if (structure == null || targetStage == null) return;
        if (managed(structure)) {
            RefineryTransitions.enqueue(terrain,structure,targetStage);return;
        }
        StructureDamageState normTarget = targetStage.templateStage();
        List<StructureCollapseManager.BlockChange> baseDiff = StructureCollapseManager.computeDiff(terrain, structure, fromStage, normTarget);
        List<StructureCollapseManager.BlockChange> diff = baseDiff;

        // If an impact origin is provided for staged failure, sort changes into 3 physical waves:
        // 1. Immediate breach core near impactOrigin (air removals within 4.5m)
        // 2. Main span/shell air removals propagating outward from impactOrigin and top-down
        // 3. Settled rubble / ruin block placements at the bottom (placed last as falling pieces land)
        if (impactOrigin != null && !diff.isEmpty()) {
            List<StructureCollapseManager.BlockChange> staged = new ArrayList<>(diff);
            staged.sort((a, b) -> {
                boolean aAir = a.spec().isAir();
                boolean bAir = b.spec().isAir();
                // Air removals (structural break) happen before lower rubble placement
                if (aAir != bAir) {
                    return aAir ? -1 : 1;
                }
                double da = impactOrigin.squaredDistanceTo(a.pos().getX() + 0.5, a.pos().getY() + 0.5, a.pos().getZ() + 0.5);
                double db = impactOrigin.squaredDistanceTo(b.pos().getX() + 0.5, b.pos().getY() + 0.5, b.pos().getZ() + 0.5);
                int bandA = (int) (Math.sqrt(da) / 2.5);
                int bandB = (int) (Math.sqrt(db) / 2.5);
                if (bandA != bandB) {
                    return Integer.compare(bandA, bandB);
                }
                int cmpY = Integer.compare(b.pos().getY(), a.pos().getY());
                if (cmpY != 0) return cmpY;
                return Long.compare(
                        ChunkPos.toLong(a.pos().getX() >> 4, a.pos().getZ() >> 4),
                        ChunkPos.toLong(b.pos().getX() >> 4, b.pos().getZ() >> 4)
                );
            });
            diff = Collections.unmodifiableList(staged);
        }

        if (diff.isEmpty()) {
            structure.setTransitionProgress(targetStage, 0);
            DestructibleStructureRegistry.updateSnapshot(structure);
            return;
        }
        TransitionTask task = new TransitionTask(
                structure.structureId(), fromStage, targetStage, diff, resumeCursor,
                crackPhaseTicks, crackPhaseRateCap, mainCollapseRateCap);
        ACTIVE_OR_PENDING.put(structure.structureId(), task);
        structure.setTransitionProgress(targetStage, Math.max(1, resumeCursor));
        DestructibleStructureRegistry.updateSnapshot(structure);
    }

    /**
     * Processes up to maxBlockChangesPerTick across at most maxConcurrentStructureTransitions
     * loaded structures. Never forces distant chunk loading.
     */
    public static synchronized int tick(ServerWorld world) {
        lastTickBlockChanges = 0;
        int refineryWork=world==null?0:RefineryTransitions.tick(world,Math.max(1,maxBlockChangesPerTick/2));
        if (ACTIVE_OR_PENDING.isEmpty()) { lastTickBlockChanges=world==null?0:RefineryTransitions.lastApplied(); return lastTickBlockChanges; }

        int budget = Math.max(1, maxBlockChangesPerTick-refineryWork);
        int maxConcurrent = Math.max(1, maxConcurrentStructureTransitions);
        int activeStructuresProcessed = 0;
        int totalApplied = 0;

        Iterator<Map.Entry<String, TransitionTask>> it = ACTIVE_OR_PENDING.entrySet().iterator();
        List<DestructibleStructure> completed = new ArrayList<>();

        while (it.hasNext() && activeStructuresProcessed < maxConcurrent && budget > 0) {
            TransitionTask task = it.next().getValue();
            DestructibleStructure structure = DestructibleStructureRegistry.get(task.structureId);
            if (structure == null) {
                it.remove();
                continue;
            }
            activeStructuresProcessed++;
            com.bobincraft.destruction.debris.StructureDebrisManager debris=world==null?null:com.bobincraft.destruction.debris.StructureDebrisManager.get(world);
            String debrisOperation="stage/"+task.targetStage.templateStage().index;
            boolean physical=task.targetStage.templateStage()!=StructureDamageState.INTACT&&task.targetStage.templateStage().index>=task.fromStage.templateStage().index;
            if(debris!=null&&physical) {
                var source=new ArrayList<com.bobincraft.destruction.debris.DebrisPlan.Source>();
                for(var change:task.orderedChanges)if(change.spec().isAir()&&change.prevSpec()!=null&&!change.prevSpec().isAir()) {
                    var pos=change.pos();
                    if(world.isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)) {
                        var actual=world.getBlockState(pos);
                        if(!actual.isAir()&&StructureTemplateAdapter.shouldApplyBlockChange(actual,change.prevSpec(),change.spec()))
                            source.add(new com.bobincraft.destruction.debris.DebrisPlan.Source(pos.getX(),pos.getY(),pos.getZ(),RefineryTransitions.encode(actual)));
                    }
                }
                if(!debris.reserve(task.structureId,debrisOperation,source))continue;
                debris.prearm(task.structureId,debrisOperation,source,64);
            }
            int baseShare = Math.max(1, budget / Math.max(1, maxConcurrent - activeStructuresProcessed + 1));
            int stageCap = task.crackPhaseTicksRemaining > 0 ? task.crackPhaseRateCap : task.mainCollapseRateCap;
            if (task.crackPhaseTicksRemaining > 0) {
                task.crackPhaseTicksRemaining--;
            }
            int perStructureBudget = Math.min(baseShare, stageCap);
            int appliedForThis = 0,checkedForThis=0;
            boolean deferredUnloaded = false;

            while (task.cursor < task.orderedChanges.size() && checkedForThis < perStructureBudget && budget > 0) {
                checkedForThis++;budget--;
                StructureCollapseManager.BlockChange change = task.orderedChanges.get(task.cursor);
                BlockPos pos = change.pos();
                int cx = pos.getX() >> 4;
                int cz = pos.getZ() >> 4;
                // Section 6.1 & 14.5: Never force-load unloaded chunks!
                if (world != null && !world.isChunkLoaded(cx, cz)) {
                    deferredUnloaded = true;
                    break;
                }
                if (world != null && world.isInBuildLimit(pos)) {
                    BlockState currentState = world.getBlockState(pos);
                    // New decorative ruin blocks have no detached source owner. Physical source fragments replace that path.
                    if(physical&&!change.spec().isAir()&&(change.prevSpec()==null||change.prevSpec().isAir())){task.cursor++;continue;}
                    boolean removal=change.spec().isAir()&&!currentState.isAir();
                    if(removal&&physical) {
                        boolean emptyOriginalHopper=currentState.isOf(net.minecraft.block.Blocks.HOPPER)
                                &&world.getBlockEntity(pos) instanceof net.minecraft.inventory.Inventory inventory&&inventory.isEmpty();
                        if(!StructureTemplateAdapter.shouldApplyBlockChange(currentState,change.prevSpec(),change.spec())
                                ||currentState.hasBlockEntity()&&!emptyOriginalHopper) {
                            debris.reject(task.structureId,debrisOperation,pos);task.cursor++;continue;
                        }
                        if(!debris.beforeRemove(task.structureId,debrisOperation,pos,currentState)) {
                            if(debris.conflicted(task.structureId,debrisOperation,pos)){task.cursor++;continue;}
                            // Capacity/barrier waits must not block later cells of already reserved connected pieces.
                            task.orderedChanges.add(task.orderedChanges.remove(task.cursor));continue;
                        }
                    }
                    task.cursor++;
                    if (StructureTemplateAdapter.shouldApplyBlockChange(currentState, change.prevSpec(), change.spec())) {
                        BlockState nextState = StructureCollapseManager.resolveBlockState(change.spec());
                        com.bobincraft.destruction.debris.StructureDebrisManager.writeSource(world,pos,nextState);
                        if(!world.getBlockState(pos).equals(nextState)){debris.reject(task.structureId,debrisOperation,pos);continue;}
                        if(removal&&physical)debris.removed(task.structureId,debrisOperation,pos);
                        appliedForThis++;
                        totalApplied++;
                    }
                    else if(physical)debris.reject(task.structureId,debrisOperation,pos);
                } else {
                    task.cursor++;
                    appliedForThis++;
                    totalApplied++;
                }
            }

            if (task.isDone()) {
                structure.setTransitionProgress(task.targetStage, 0);
                completed.add(structure);
                it.remove();
            } else {
                structure.setTransitionProgress(task.targetStage, Math.max(1, task.cursor));
                if (!deferredUnloaded && world != null) {
                    StructurePersistentState.get(world).recordStructure(structure);
                }
            }
        }

        if (world != null && !completed.isEmpty()) {
            StructurePersistentState state = StructurePersistentState.get(world);
            for (DestructibleStructure s : completed) {
                state.recordStructure(s);
                StructureNetworkSync.broadcastStructureState(world, s);
            }
        }

        lastTickBlockChanges = totalApplied + (world==null?0:RefineryTransitions.lastApplied());
        return lastTickBlockChanges;
    }

    /**
     * Recovers any interrupted transitions after world reload so no structure is ever left half-intact and half-destroyed.
     */
    public static synchronized void recoverInterruptedTransitions(WarzoneTerrain terrain) {
        for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
            if (managed(s)) {
                if(s.transitionProgress()>0 || s.damageStage()!=StructureDamageState.INTACT) RefineryTransitions.enqueue(terrain,s,s.targetStage());
                continue;
            }
            if (s.transitionProgress() > 0 || s.damageStage() != s.targetStage()) {
                enqueueTransition(terrain, s, StructureDamageState.INTACT, s.targetStage(), 0);
            }
        }
    }

    public static synchronized int pendingTransitionCount() {
        return ACTIVE_OR_PENDING.size()+RefineryTransitions.pendingCount();
    }

    public static synchronized int totalQueuedBlockChanges() {
        int sum = 0;
        for (TransitionTask task : ACTIVE_OR_PENDING.values()) {
            sum += task.remaining();
        }
        return sum+RefineryTransitions.pendingCells();
    }

    public static int lastTickBlockChanges() {
        return lastTickBlockChanges;
    }

    public static synchronized void clear() {
        ACTIVE_OR_PENDING.clear();
        lastTickBlockChanges = 0;
    }
}
