package com.bobincraft.destruction;

import com.bobincraft.destruction.debris.StructureDebrisManager;
import com.bobincraft.warzone.BpSpec;
import com.bobincraft.warzone.CombatZoneBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Specialized industrial structure fire manager (Section 6.1 & Section 8).
 * Avoids placing thousands of vanilla minecraft:fire blocks.
 * Server tracks logical burn state, intensity, duration, periodic HP degradation, and consumes 1 block of
 * non-flowing {@code bobin_craft:crude_oil} inside burning refinery tanks every few seconds;
 * Client renders distance-LOD smoke and flame emitters within global particle budgets.
 */
public final class StructureFireManager {
    public static int maxActiveLargeFireSources = 16;
    public static int nearSmokeParticlesPerSecond = 12;
    public static int mediumSmokeParticlesPerSecond = 4;
    public static int maxStructureFireParticles = 1600;

    public static double nearLodDistance = 32.0;
    public static double mediumLodDistance = 96.0;
    public static double farLodDistance = 192.0;

    public record FireRecord(
            String structureId,
            boolean isBurning,
            float fireIntensity,
            int remainingBurnTime,
            long lastFireUpdate
    ) {}

    private static final Map<String, FireRecord> ACTIVE_FIRES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> OIL_BURN_COUNTERS = new ConcurrentHashMap<>();

    private StructureFireManager() {}

    public static synchronized boolean igniteOrIntensify(ServerWorld world,
                                                         DestructibleStructure structure,
                                                         float addedIntensity,
                                                         int durationTicks,
                                                         long worldTick) {
        if (structure == null || !structure.structureType().flammable()) return false;
        String id = structure.structureId();
        FireRecord existing = ACTIVE_FIRES.get(id);

        if (existing == null && ACTIVE_FIRES.size() >= maxActiveLargeFireSources) {
            String weakestId = null;
            int minRemaining = Integer.MAX_VALUE;
            for (FireRecord rec : ACTIVE_FIRES.values()) {
                if (rec.remainingBurnTime < minRemaining) {
                    minRemaining = rec.remainingBurnTime;
                    weakestId = rec.structureId;
                }
            }
            if (weakestId != null) {
                extinguish(world, DestructibleStructureRegistry.get(weakestId), worldTick);
            }
        }

        float nextIntensity = MathHelper.clamp((existing != null ? existing.fireIntensity : 0.35f) + addedIntensity, 0.35f, 1.0f);
        int baseDuration = durationTicks > 0 ? durationTicks : structure.structureType().defaultBurnTicks();
        int nextDuration = Math.max(existing != null ? existing.remainingBurnTime : 0, baseDuration);

        structure.setFireState(true, nextIntensity, nextDuration, worldTick);
        FireRecord updated = new FireRecord(id, true, nextIntensity, nextDuration, worldTick);
        ACTIVE_FIRES.put(id, updated);

        if (world != null) {
            StructurePersistentState.get(world).recordStructure(structure);
            StructureNetworkSync.broadcastStructureState(world, structure);
        }
        return true;
    }

    public static synchronized void extinguish(ServerWorld world, DestructibleStructure structure, long worldTick) {
        if (structure == null) return;
        ACTIVE_FIRES.remove(structure.structureId());
        OIL_BURN_COUNTERS.remove(structure.structureId());
        structure.setFireState(false, 0.0f, 0, worldTick);
        if (world != null) {
            StructurePersistentState.get(world).recordStructure(structure);
            StructureNetworkSync.broadcastStructureState(world, structure);
        }
    }

    /**
     * Periodic server update (runs once per second / 20 ticks) for active fires.
     * Never forces chunk loading: if a burning structure is in unloaded chunks, only its logical
     * timer and HP advance without block updates or particle packets.
     */
    public static synchronized void tickServer(ServerWorld world, long worldTick) {
        if (ACTIVE_FIRES.isEmpty()) return;
        Iterator<Map.Entry<String, FireRecord>> it = ACTIVE_FIRES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FireRecord> entry = it.next();
            FireRecord rec = entry.getValue();
            DestructibleStructure s = DestructibleStructureRegistry.get(rec.structureId);
            if (s == null || !s.isBurning()) {
                it.remove();
                OIL_BURN_COUNTERS.remove(rec.structureId);
                continue;
            }
            int elapsed = (int) Math.max(1L, Math.min(200L, worldTick - rec.lastFireUpdate));
            int remaining = s.remainingBurnTime() - elapsed;
            if (remaining <= 0) {
                s.setFireState(false, 0.0f, 0, worldTick);
                it.remove();
                OIL_BURN_COUNTERS.remove(rec.structureId);
                if (world != null) {
                    StructurePersistentState.get(world).recordStructure(s);
                    StructureNetworkSync.broadcastStructureState(world, s);
                }
                continue;
            }

            // Fire intensity develops and tapers off near the end of burn time
            float intensity = s.fireIntensity();
            if (remaining < 200) {
                intensity = Math.max(0.20f, remaining / 200.0f);
            } else if (s.damageStage() == StructureDamageState.CRITICAL || s.damageStage() == StructureDamageState.DESTROYED) {
                intensity = Math.min(1.0f, intensity + 0.04f);
            }
            s.setFireState(true, intensity, remaining, worldTick);
            entry.setValue(new FireRecord(s.structureId(), true, intensity, remaining, worldTick));

            // Consume 1 block of crude oil inside a burning refinery tank every 3 seconds (3 server fire ticks)
            if (world != null && s.structureType().category() == DestructibleStructureType.Category.REFINERY_TANK && !s.destroyed()) {
                int step = OIL_BURN_COUNTERS.getOrDefault(s.structureId(), 0) + 1;
                if (step >= 3) {
                    step = 0;
                    consumeOneCrudeOilBlock(world, s);
                }
                OIL_BURN_COUNTERS.put(s.structureId(), step);
            }

            // Gradual HP degradation during active fire (if not yet destroyed)
            if (!s.destroyed() && s.structureType().fireDamagePerSecond() > 0.0f) {
                float fireDmg = s.structureType().fireDamagePerSecond() * intensity * (elapsed / 20.0f);
                StructureDamageManager.applyDirectStructureDamage(
                        world,
                        s,
                        new StructureDamageSource(UUID.randomUUID(), StructureDamageSource.Kind.FIRE_TICK, null,
                                net.minecraft.util.math.Vec3d.ofCenter(s.originPosition()), 1.0),
                        fireDmg
                );
            } else if (world != null) {
                StructurePersistentState.get(world).recordStructure(s);
            }
        }
    }

    /**
     * Consumes exactly 1 block of {@code bobin_craft:crude_oil} from the top surface of the oil pool inside
     * a burning refinery tank (prioritizing blocks nearest the breach hole) so the oil visibly burns down over time
     * without ever flowing out!
     */
    private static void consumeOneCrudeOilBlock(ServerWorld world, DestructibleStructure tank) {
        BlockPos o = tank.originPosition();
        if (!world.isChunkLoaded(o.getX() >> 4, o.getZ() >> 4)) return;
        int r = Math.max(1, tank.radius() - 1);
        int h = tank.height();
        List<StructuralSupportSolver.BreachOpening> openings = tank.breachOpenings();
        int targetX = !openings.isEmpty() ? openings.get(openings.size() - 1).centerX() : o.getX();
        int targetZ = !openings.isEmpty() ? openings.get(openings.size() - 1).centerZ() : o.getZ();

        BlockPos bestPos = null;
        double bestDistSq = Double.POSITIVE_INFINITY;

        for (int dy = h - 1; dy >= 1; dy--) {
            int y = o.getY() + dy;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    BlockPos pos = new BlockPos(o.getX() + dx, y, o.getZ() + dz);
                    if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(CombatZoneBlocks.CRUDE_OIL)) {
                        double dSq = (pos.getX() - targetX) * (pos.getX() - targetX)
                                + (pos.getZ() - targetZ) * (pos.getZ() - targetZ);
                        if (dSq < bestDistSq) {
                            bestDistSq = dSq;
                            bestPos = pos;
                        }
                    }
                }
            }
            if (bestPos != null) break; // Always consume from the highest remaining oil layer first!
        }

        if (bestPos != null) {
            StructureDebrisManager.writeSource(world, bestPos, Blocks.AIR.getDefaultState());
            long packed = StructuralSupportSolver.packBlockPos(bestPos.getX(), bestPos.getY(), bestPos.getZ());
            tank.recordLocalBreachCells(Map.of(packed, BpSpec.AIR));
            var state = RefineryTransitionState.get(world);
            if (state != null && state.writable()) {
                var ledger = state.ledgers.get(tank.structureId());
                if (ledger != null) {
                    ledger.owned.put(packed, "minecraft:air");
                    ledger.pending.remove(packed);
                    state.markDirty();
                }
            }
        }
    }

    public static synchronized void syncFromRegistry(long worldTick) {
        ACTIVE_FIRES.clear();
        for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
            if (s.isBurning() && s.remainingBurnTime() > 0 && ACTIVE_FIRES.size() < maxActiveLargeFireSources) {
                ACTIVE_FIRES.put(s.structureId(), new FireRecord(
                        s.structureId(), true, s.fireIntensity(), s.remainingBurnTime(),
                        s.lastUpdateTick() > 0 ? s.lastUpdateTick() : worldTick));
            }
        }
    }

    public static int activeFireCount() {
        return ACTIVE_FIRES.size();
    }

    public static Collection<FireRecord> activeFires() {
        return Collections.unmodifiableCollection(ACTIVE_FIRES.values());
    }

    /**
     * Computes the per-second particle emission rate for a burning structure at a given distance,
     * scaled by the global budget across all simultaneously burning structures (Section 8.2).
     */
    public static double budgetedParticlesPerSecond(double distanceBlocks, float intensity, int activeBurningCount) {
        return IndustrialSmokeBudget.particlesPerSecond(distanceBlocks, intensity, activeBurningCount,
                nearLodDistance, mediumLodDistance, farLodDistance,
                nearSmokeParticlesPerSecond, mediumSmokeParticlesPerSecond, maxStructureFireParticles);
    }

    public static synchronized void clear() {
        ACTIVE_FIRES.clear();
        OIL_BURN_COUNTERS.clear();
    }
}
