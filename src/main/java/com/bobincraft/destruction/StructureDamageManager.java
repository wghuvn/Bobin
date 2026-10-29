package com.bobincraft.destruction;

import com.bobincraft.combat.ImpactDebris;
import com.bobincraft.drone.DroneBlastType;
import com.bobincraft.drone.DroneExplosionEffects;
import com.bobincraft.drone.DronePressure;
import com.bobincraft.raid.HubSafety;
import com.bobincraft.warzone.Blueprint;
import com.bobincraft.warzone.BpSpec;
import com.bobincraft.warzone.PowerGrid;
import com.bobincraft.warzone.WarzoneChunkGenerator;
import com.bobincraft.warzone.WarzoneDimension;
import com.bobincraft.warzone.WarzoneTerrain;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer A (Server Damage Logic) authoritative damage dispatcher for all destructible industrial structures
 * and bridges (Sections 1 & 2).
 *
 * <p>Enforces:
 * <ul>
 *   <li><b>Single Primary Component Selection (Section 1)</b>: Structural damage is NEVER broadcast to all
 *       supports/components inside {@code distance <= explosionRadius}. Instead, {@link #selectPrimaryTarget}
 *       resolves the single struck {@link DestructibleStructure} and single {@code ComponentId} using actual
 *       first-contact geometry, surface priority, and minimum surface distance.</li>
 *   <li><b>Localized Point-of-Impact Breaches & Breach Fire/Smoke</b>: When an RPG, tank shell, FPV drone, or
 *       demolition charge strikes a refinery tank or substation transformer, a physical hole is punched at the
 *       exact impact point and ignites a directional jet of black hydrocarbon smoke and fire pouring out of the hole
 *       instead of instantly collapsing the whole tank!</li>
 *   <li><b>Multi-Shot Refinery Tank HP & Catastrophic 0-HP Explosion</b>: Refinery tanks absorb ~4-5 RPG hits
 *       before reaching 0 HP, at which point they detonate in a massive hydrocarbon explosion that blasts
 *       tank fragments outward in all directions!</li>
 *   <li><b>Support-Aware Bridge Failure</b>: When a bridge deck or pier support ({@code left_support}, {@code center_support},
 *       {@code right_support}) is struck or destroyed, a 3D hole is carved and any unsupported bridge span above a
 *       severed pier collapses into falling blocks that settle as real blocks on the riverbed below.</li>
 * </ul>
 */
public final class StructureDamageManager {
    private static final int MAX_TRACKED_EVENTS = 256;
    private static final Map<UUID, Set<String>> PROCESSED_EVENT_HITS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Set<String>> eldest) {
            return size() > MAX_TRACKED_EVENTS;
        }
    };

    public enum DamageEventType {
        NONE,
        STRUCTURE_HIT,
        STRUCTURE_DESTROYED
    }

    public record PrimaryHitTarget(
            DestructibleStructure structure,
            DestructibleStructure.HitZone hitZone,
            String componentId,
            double surfaceDistance,
            double attenuation
    ) {}

    public record DelayedCollapseRecord(
            String structureId,
            float pendingDamagePerStep,
            int remainingSteps,
            long nextStepTick,
            int stepIntervalTicks,
            Vec3d impactOrigin
    ) {}

    private static final Map<String, DelayedCollapseRecord> DELAYED_COLLAPSES = new ConcurrentHashMap<>();
    private static volatile DamageEventType lastDispatchedEventType = DamageEventType.NONE;
    private static volatile String lastAffectedStructureId = "";
    private static volatile String lastAffectedComponentId = "";

    private StructureDamageManager() {}

    /**
     * Authoritative entry point (Section 1.2):
     * 1. Projectile/explosion records actual first collision point {@code impactPosition}.
     * 2. System resolves the single primary {@code StructureId}.
     * 3. Determines the specific {@link DestructibleStructure.HitZone}.
     * 4. Selects ONE primary {@code ComponentId} (never all supports in {@code explosionRadius}).
     * 5. Applies structural damage to that component only.
     * 6. Evaluates HP, localized breach holes, and state machine transition ({@code STRUCTURE_HIT} vs {@code STRUCTURE_DESTROYED}).
     */
    public static int applyDamage(World world,
                                  Vec3d impactPosition,
                                  StructureDamageSource damageSource,
                                  float gameDamage) {
        if (world == null || world.isClient || impactPosition == null || damageSource == null) return 0;
        if (!Float.isFinite(gameDamage) || gameDamage <= 0.0f) return 0;
        ServerWorld serverWorld = world instanceof ServerWorld sw ? sw : null;
        if (serverWorld != null && HubSafety.isHub(serverWorld)) {
            trace(damageSource, impactPosition, null, 0, "HUB_PROTECTED");
            return 0;
        }

        ensureRegistryReady(serverWorld);
        return applyDamageInternal(serverWorld, impactPosition, damageSource, gameDamage);
    }

    /**
     * Testable core entry point that works both with a live {@link ServerWorld} and in headless unit tests.
     */
    public static synchronized int applyDamageInternal(ServerWorld serverWorld,
                                                       Vec3d impactPosition,
                                                       StructureDamageSource damageSource,
                                                       float gameDamage) {
        if (impactPosition == null || damageSource == null || !Float.isFinite(gameDamage) || gameDamage <= 0.0f) {
            return 0;
        }
        // Event-level deduplication: one explosion/projectile event can only apply structural damage ONCE total!
        if (!markEventProcessed(damageSource.eventId())) {
            trace(damageSource, impactPosition, null, 0, "DUPLICATE_EVENT");
            return 0;
        }

        Optional<PrimaryHitTarget> targetOpt = selectPrimaryTarget(impactPosition, damageSource);
        if (targetOpt.isEmpty()) {
            trace(damageSource, impactPosition, null, 0, "NO_HIT_ZONE");
            return 0;
        }

        PrimaryHitTarget target = targetOpt.get();
        DestructibleStructure structure = target.structure();
        boolean allowBridgeContinuedBreak = serverWorld != null
                && structure.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION;
        if (!allowBridgeContinuedBreak && (structure.destroyed() || structure.damageStage().isTerminalOrDestroying())) {
            trace(damageSource, impactPosition, target, 0, "ALREADY_TERMINAL");
            return 0;
        }

        if (serverWorld != null && !serverWorld.getRegistryKey().getValue().equals(structure.dimensionId())) {
            trace(damageSource, impactPosition, target, 0, "WRONG_DIMENSION");
            return 0;
        }
        float effectiveDamage = (float) (gameDamage
                * damageSource.kind().structuralMultiplier
                * target.hitZone().damageMultiplier
                * target.attenuation());

        // Heavy double-walled refinery storage tanks require 4-5 RPG hits before catastrophic detonation (instead of 2).
        if (structure.structureType().category() == DestructibleStructureType.Category.REFINERY_TANK
                && damageSource.kind() != StructureDamageSource.Kind.ADMIN_COMMAND
                && damageSource.kind() != StructureDamageSource.Kind.FIRE_TICK) {
            effectiveDamage *= 0.44f;
        }

        if (effectiveDamage <= 0.05f) {
            trace(damageSource, impactPosition, target, effectiveDamage, "ZERO_EFFECTIVE_DAMAGE");
            return 0;
        }

        trace(damageSource, impactPosition, target, effectiveDamage, "TARGET_SELECTED");
        boolean applied = applyDirectStructureDamage(
                serverWorld,
                structure,
                damageSource,
                effectiveDamage,
                target.componentId(),
                target.hitZone()
        );
        return applied ? 1 : 0;
    }

    /**
     * Resolves AT MOST ONE primary structure and ONE primary component for an impact/detonation point (Section 1.2).
     * Never selects multiple bridge supports or adjacent structures just because they lie within {@code explosionRadius}.
     */
    public static Optional<PrimaryHitTarget> selectPrimaryTarget(Vec3d impactPosition, StructureDamageSource damageSource) {
        if (impactPosition == null) return Optional.empty();
        double maxContactTolerance = 2.0; // Tight structural contact envelope; ignores far blast radius for multi-component selection
        double searchRadius = Math.min(maxContactTolerance, Math.max(1.25, damageSource != null ? damageSource.effectRadius() : 2.0));

        List<DestructibleStructure> candidates = DestructibleStructureRegistry.spatialIndex()
                .getIntersectingSphere(impactPosition, searchRadius);
        if (candidates.isEmpty()) {
            DestructibleStructure direct = DestructibleStructureRegistry.spatialIndex().findPrimaryAt(impactPosition);
            if (direct != null) candidates = List.of(direct);
        }
        if (StructureDamageTrace.enabled()) {
            StructureDamageTrace.add("CANDIDATES event=" + (damageSource == null ? "none" : damageSource.eventId()) + " count=" + candidates.size());
        }
        if (candidates.isEmpty()) return Optional.empty();

        DestructibleStructure bestStructure = null;
        DestructibleStructure.HitMatch bestMatch = DestructibleStructure.HitMatch.NONE;
        double bestScore = Double.POSITIVE_INFINITY;
        double bestSurfaceDist = Double.POSITIVE_INFINITY;

        for (DestructibleStructure candidate : candidates) {
            DestructibleStructure.HitMatch match = candidate.matchAt(impactPosition, 1.25);
            double surfDist = candidate.distanceToSurface(impactPosition);
            if (!match.isHit()) {
                if (surfDist > maxContactTolerance) continue;
                match = candidate.matchSphere(impactPosition, maxContactTolerance);
                if (!match.isHit()) continue;
            }

            double regDist = match.region() != null
                    ? match.region().distanceToSurface(impactPosition)
                    : surfDist;
            double originTieBreaker = candidate.originPosition().getSquaredDistance(impactPosition.x, impactPosition.y, impactPosition.z) * 1e-4;
            double score = regDist + originTieBreaker;
            if (score < bestScore) {
                bestScore = score;
                bestStructure = candidate;
                bestMatch = match;
                bestSurfaceDist = Math.max(0.0, surfDist);
            }
        }

        if (bestStructure == null || !bestMatch.isHit()) {
            return Optional.empty();
        }

        double attenuation = bestSurfaceDist <= 0.85
                ? 1.0
                : Math.max(0.45, 1.0 - (bestSurfaceDist / (maxContactTolerance + 1.0)));

        return Optional.of(new PrimaryHitTarget(
                bestStructure,
                bestMatch.zone(),
                bestMatch.componentId(),
                bestSurfaceDist,
                attenuation
        ));
    }

    public static synchronized boolean applyDirectStructureDamage(ServerWorld serverWorld,
                                                                  DestructibleStructure structure,
                                                                  StructureDamageSource source,
                                                                  float effectiveDamage) {
        if (structure == null) return false;
        Vec3d origin = source != null ? source.origin() : Vec3d.ofCenter(structure.originPosition());
        DestructibleStructure.HitMatch match = structure.matchAt(origin, 2.0);
        String comp = match.isHit() ? match.componentId() : structure.defaultComponentId();
        DestructibleStructure.HitZone zone = match.isHit() ? match.zone() : DestructibleStructure.HitZone.HULL;
        return applyDirectStructureDamage(serverWorld, structure, source, effectiveDamage, comp, zone);
    }

    public static synchronized boolean applyDirectStructureDamage(ServerWorld serverWorld,
                                                                  DestructibleStructure structure,
                                                                  StructureDamageSource source,
                                                                  float effectiveDamage,
                                                                  String componentId,
                                                                  DestructibleStructure.HitZone hitZone) {
        if (structure == null || !Float.isFinite(effectiveDamage) || effectiveDamage <= 0.0f) {
            return false;
        }
        boolean allowBridgeContinuedBreak = serverWorld != null
                && structure.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION;
        if (!allowBridgeContinuedBreak && (structure.destroyed() || structure.damageStage().isTerminalOrDestroying())) {
            return false;
        }
        // Shared policy boundary covers normal, direct, delayed and fire damage entry points.
        if (source != null && !StructureDamagePolicy.allows(source.kind().id, structure.structureType().category().name())) {
            if (StructureDamageTrace.enabled()) {
                StructureDamageTrace.add("REJECT event=" + source.eventId() + " source=" + source.kind() + " structure=" + structure.structureId() + " reason=EXCLUDED_WEAPON_POLICY");
            }
            return false;
        }
        long tick = serverWorld != null ? serverWorld.getTime() : 0L;
        float previousHealth = structure.currentHealth();
        StructureDamageState previousStage = structure.damageStage().templateStage();
        boolean wasOffline = DestructibleStructureRegistry.isSubstationOffline();

        float immediateDamage = effectiveDamage;
        float deferredDamage = 0.0f;

        // Progressive structural failure for FP-1 fixed-wing strikes on intact refinery tanks:
        // First heavy hit creates a dramatic breach hole with pouring black smoke & fire rather than instant total collapse!
        if (source != null && source.kind() == StructureDamageSource.Kind.FIXED_WING_STRIKE) {
            float currentHp = structure.currentHealth();
            float maxHp = structure.maxHealth();
            if (previousStage == StructureDamageState.INTACT) {
                float targetHpAfterInitialBreach = maxHp * 0.52f;
                if (currentHp - immediateDamage < targetHpAfterInitialBreach) {
                    immediateDamage = Math.max(1.0f, currentHp - targetHpAfterInitialBreach);
                    deferredDamage = effectiveDamage - immediateDamage;
                }
            } else if (previousStage == StructureDamageState.DAMAGED) {
                float targetHpAfterSecondStage = maxHp * 0.20f;
                if (currentHp - immediateDamage < targetHpAfterSecondStage) {
                    immediateDamage = Math.max(1.0f, currentHp - targetHpAfterSecondStage);
                    deferredDamage = effectiveDamage - immediateDamage;
                }
            }
        }

        Vec3d impactOrigin = source != null ? source.origin() : Vec3d.ofCenter(structure.originPosition());
        Vec3d surfaceNormal = source != null ? source.surfaceNormal() : null;
        if (source != null && source.kind() != StructureDamageSource.Kind.FIRE_TICK && source.kind() != StructureDamageSource.Kind.ADMIN_COMMAND) {
            structure.recordImpactVector(impactOrigin, surfaceNormal);
        }

        float previousComponentHealth = structure.componentHealth(componentId);
        boolean changed = structure.applyComponentDamage(componentId, hitZone, immediateDamage, tick);
        if (!changed) {
            if (StructureDamageTrace.enabled()) {
                StructureDamageTrace.add("REJECT structure=" + structure.structureId() + " reason=HEALTH_UNCHANGED");
            }
            return false;
        }
        if (serverWorld != null && source != null) {
            com.bobincraft.destruction.debris.StructureDebrisManager.get(serverWorld)
                    .noteImpact(structure.structureId(), source.origin(), Math.max(effectiveDamage, 450.0f), source.eventId());
        }

        float currentHealth = structure.currentHealth();
        StructureDamageState newStage = structure.damageStage();
        if (StructureDamageTrace.enabled()) {
            StructureDamageTrace.add("HP event=" + (source == null ? "direct" : source.eventId()) + " structure=" + structure.structureId() + " component=" + componentId
                    + " effective=" + effectiveDamage + " hp=" + previousHealth + "->" + currentHealth + " component_hp=" + previousComponentHealth + "->" + structure.componentHealth(componentId)
                    + " stage=" + previousStage + "->" + newStage + " geometry=" + (newStage != previousStage ? "SCHEDULED" : "LOCAL_BREACH"));
        }
        DestructibleStructureRegistry.updateSnapshot(structure);
        lastAffectedStructureId = structure.structureId();
        lastAffectedComponentId = structure.lastHitComponentId();

        if (deferredDamage > 0.0f && !structure.destroyed()) {
            int steps = previousStage == StructureDamageState.INTACT ? 2 : 1;
            int interval = 60;
            float perStep = Math.max(deferredDamage / steps, structure.maxHealth() * 0.36f);
            DELAYED_COLLAPSES.put(structure.structureId(), new DelayedCollapseRecord(
                    structure.structureId(), perStep, steps, tick + interval, interval, impactOrigin
            ));
        }

        WarzoneTerrain terrain = resolveTerrain(serverWorld);

        // Compute localized breach hole at the impact point for penetrating weapons (RPG, Armor Shell, Kamikaze Drone, GROM-7, FP-1)
        Map<Long, BpSpec> localBreach = Map.of();
        if (source != null && isPenetratingWeapon(source.kind()) && terrain != null) {
            Blueprint currentBp = StructureCollapseManager.getStageBlueprint(terrain, structure, newStage);
            BlockPos o = structure.originPosition();
            Vec3d norm = structure.lastImpactNormal() != null ? structure.lastImpactNormal() : new Vec3d(0, 1, 0);
            if (structure.structureType().category() == DestructibleStructureType.Category.REFINERY_TANK) {
                localBreach = StructuralSupportSolver.computeTankBreach(
                        currentBp, o.getX(), o.getY(), o.getZ(),
                        structure.radius(), structure.height(),
                        impactOrigin.x, impactOrigin.y, impactOrigin.z,
                        norm.x, norm.y, norm.z, effectiveDamage);
            } else if (structure.structureType().category() == DestructibleStructureType.Category.SUBSTATION_TRANSFORMER
                    || structure.structureType().category() == DestructibleStructureType.Category.SUBSTATION_EQUIPMENT) {
                localBreach = StructuralSupportSolver.computeSubstationBreach(
                        currentBp, o.getX(), o.getY(), o.getZ(),
                        impactOrigin.x, impactOrigin.y, impactOrigin.z,
                        effectiveDamage);
            } else if (structure.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION) {
                boolean supportHit = hitZone == DestructibleStructure.HitZone.SUPPORT
                        || (componentId != null && componentId.contains("support"));
                boolean pierDestroyed = supportHit && (structure.componentHealth(componentId) <= 0.05f
                        || newStage == StructureDamageState.CRITICAL
                        || newStage.isTerminalOrDestroying());
                localBreach = StructureCollapseManager.computeBridgeImpactBreach(
                        terrain, structure, currentBp,
                        impactOrigin.x, impactOrigin.y, impactOrigin.z,
                        supportHit, pierDestroyed, effectiveDamage);
            }
            if (!localBreach.isEmpty()) {
                structure.recordLocalBreachCells(localBreach);
            }
        }

        // Ignite fire & black smoke on ANY penetrating or incendiary hit to a flammable tank or transformer!
        // Ensures EVERY damaged/burning refinery tank emits thick black smoke and flames from its holes and roof.
        if (structure.structureType().flammable() && source != null && source.kind().canIgnite) {
            boolean isFp1Strike = source.kind() == StructureDamageSource.Kind.FIXED_WING_STRIKE;
            boolean isPenetratingHit = isPenetratingWeapon(source.kind()) && effectiveDamage >= 35.0f;
            if (isFp1Strike || isPenetratingHit || newStage != StructureDamageState.INTACT || !localBreach.isEmpty()) {
                float intensity = isFp1Strike ? 1.0f
                        : (newStage.isTerminalOrDestroying() ? 0.98f
                        : (newStage == StructureDamageState.CRITICAL ? 0.90f
                        : (newStage == StructureDamageState.DAMAGED ? 0.82f : 0.75f)));
                int duration = isFp1Strike
                        ? (int) (structure.structureType().defaultBurnTicks() * 1.6f)
                        : (int) (structure.structureType().defaultBurnTicks() * 1.35f);
                StructureFireManager.igniteOrIntensify(serverWorld, structure, intensity, duration, tick);
            }
        }

        // Section 2: Strict split between STRUCTURE_HIT (HP > 0) and STRUCTURE_DESTROYED (previousHealth > 0 && currentHealth <= 0)
        boolean justDestroyed = previousHealth > 0.0f && currentHealth <= 0.0f;
        if (justDestroyed) {
            lastDispatchedEventType = DamageEventType.STRUCTURE_DESTROYED;
            structure.beginDestroyingAnimation(tick);

            // When a refinery tank or transformer reaches 0 HP, detonate a massive catastrophic explosion at its center
            // so blocks fly apart in all directions and players see/hear a real industrial explosion!
            if (serverWorld != null && (structure.structureType().category() == DestructibleStructureType.Category.REFINERY_TANK
                    || structure.structureType().category() == DestructibleStructureType.Category.SUBSTATION_TRANSFORMER)) {
                triggerCatastrophicStructureExplosion(serverWorld, structure, impactOrigin);
            }

            Optional<DestructionScenarioDirector.ScenarioEvent> scenarioOpt =
                    DestructionScenarioDirector.triggerCollapseScenario(
                            serverWorld, structure, previousStage, structure.lastHitComponentId(),
                            structure.lastHitZone(), impactOrigin, tick);

            if (structure.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION && !localBreach.isEmpty()) {
                RefineryTransitions.enqueueLocalBreach(terrain, structure, localBreach);
            } else if (serverWorld != null && scenarioOpt.isPresent()) {
                int crackTicks = scenarioOpt.get().profile().crackPhaseTicks;
                StructureUpdateQueue.enqueueStagedTransition(
                        terrain, structure, previousStage, StructureDamageState.DESTROYED,
                        impactOrigin, crackTicks, 12, 32);
            } else {
                StructureUpdateQueue.enqueueTransition(terrain, structure, previousStage, StructureDamageState.DESTROYED, 0);
            }
            structure.completeDestroyingAnimation(tick);
            DestructibleStructureRegistry.updateSnapshot(structure);

            if (serverWorld != null && scenarioOpt.isPresent()) {
                spawnTransitionDebris(serverWorld, structure, impactOrigin, StructureDamageState.DESTROYED);
                StructureNetworkSync.broadcastStageTransition(
                        serverWorld, structure, previousStage, StructureDamageState.DESTROYED, scenarioOpt.get());
            }

            boolean nowOffline = DestructibleStructureRegistry.isSubstationOffline();
            if (!wasOffline && nowOffline && serverWorld != null) {
                PowerGrid.onSubstationOfflineChanged(serverWorld, true);
            }
        } else {
            // STRUCTURE_HIT: HP decreased, but object is NOT destroyed (currentHealth > 0)
            // Apply localized hole + stage wear WITHOUT playing the full collapse animation!
            lastDispatchedEventType = DamageEventType.STRUCTURE_HIT;
            DestructionScenarioDirector.recordNonTerminalHit(structure, newStage);
            if (structure.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION && !localBreach.isEmpty()) {
                RefineryTransitions.enqueueLocalBreach(terrain, structure, localBreach);
            } else if (newStage != previousStage) {
                if (serverWorld != null) {
                    StructureUpdateQueue.enqueueStagedTransition(
                            terrain, structure, previousStage, newStage, impactOrigin, 4, 16, 40);
                } else {
                    StructureUpdateQueue.enqueueTransition(terrain, structure, previousStage, newStage, 0);
                }
            } else if (!localBreach.isEmpty()) {
                // Even if HP did not cross a coarse stage threshold, immediately carve the local RPG/shell hole!
                RefineryTransitions.enqueueLocalBreach(terrain, structure, localBreach);
            }
            if (serverWorld != null) {
                spawnLocalHitDebris(serverWorld, structure, impactOrigin, newStage);
                StructureNetworkSync.broadcastStructureHitEffect(
                        serverWorld, structure, previousStage, newStage,
                        structure.lastHitComponentId(), structure.lastHitZone(), impactOrigin);
            }
        }

        if (serverWorld != null) {
            StructurePersistentState.get(serverWorld).recordStructure(structure);
            StructureNetworkSync.broadcastStructureState(serverWorld, structure);
        }
        return true;
    }

    /**
     * Triggers a catastrophic explosion when a refinery storage tank or substation transformer reaches 0 HP.
     * Records a central high-energy radial blast vector in {@link com.bobincraft.destruction.debris.StructureDebrisManager}
     * so all detached tank/transformer blocks blow outward in 360 degrees and plays the full multi-layer fireball,
     * shockwave, pressure wave, and explosion sound choreography.
     */
    private static void triggerCatastrophicStructureExplosion(ServerWorld serverWorld,
                                                              DestructibleStructure structure,
                                                              Vec3d impactOrigin) {
        BlockPos o = structure.originPosition();
        Vec3d blastCenter = new Vec3d(
                o.getX() + 0.5,
                o.getY() + Math.max(2.0, structure.height() * 0.45),
                o.getZ() + 0.5
        );
        com.bobincraft.destruction.debris.StructureDebrisManager.get(serverWorld)
                .noteImpact(structure.structureId(), blastCenter, 2400.0f, UUID.randomUUID());

        DroneBlastType blastType = structure.structureType().category() == DestructibleStructureType.Category.REFINERY_TANK
                ? DroneBlastType.FP1_DEEP_STRIKE
                : DroneBlastType.ARMOR_HIGH_EXPLOSIVE;
        DronePressure.apply(serverWorld, null, null, blastCenter, blastType, null);
        DroneExplosionEffects.play(
                serverWorld,
                null,
                null,
                blastCenter,
                blastType,
                false,
                Blocks.IRON_BLOCK.getDefaultState(),
                blastCenter
        );
    }

    private static boolean isPenetratingWeapon(StructureDamageSource.Kind kind) {
        if (kind == null) return false;
        return switch (kind) {
            case RPG_PROJECTILE, ARMOR_SHELL, KAMIKAZE_DRONE, DEMOLITION_CHARGE, FIXED_WING_STRIKE, GENERIC_EXPLOSION -> true;
            default -> false;
        };
    }

    public static synchronized int tickDelayedCollapses(ServerWorld world, long worldTick) {
        if (DELAYED_COLLAPSES.isEmpty()) return 0;
        int progressed = 0;
        Iterator<Map.Entry<String, DelayedCollapseRecord>> it = DELAYED_COLLAPSES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, DelayedCollapseRecord> entry = it.next();
            DelayedCollapseRecord rec = entry.getValue();
            DestructibleStructure s = DestructibleStructureRegistry.get(rec.structureId);
            if (s == null || s.destroyed() || rec.remainingSteps <= 0) {
                it.remove();
                continue;
            }
            if (worldTick < rec.nextStepTick) continue;

            StructureDamageSource aftershockSource = new StructureDamageSource(
                    UUID.randomUUID(), StructureDamageSource.Kind.FIRE_TICK, null, rec.impactOrigin, 2.0);
            if (applyDirectStructureDamage(world, s, aftershockSource, rec.pendingDamagePerStep)) {
                progressed++;
            }
            int nextRemaining = rec.remainingSteps - 1;
            if (nextRemaining <= 0 || s.destroyed()) {
                it.remove();
            } else {
                entry.setValue(new DelayedCollapseRecord(
                        rec.structureId, rec.pendingDamagePerStep, nextRemaining,
                        worldTick + rec.stepIntervalTicks, rec.stepIntervalTicks, rec.impactOrigin));
            }
        }
        return progressed;
    }

    public static int pendingDelayedCollapseCount() {
        return DELAYED_COLLAPSES.size();
    }

    public static synchronized void forceStage(ServerWorld serverWorld,
                                               DestructibleStructure structure,
                                               StructureDamageState targetStage) {
        if (structure == null || targetStage == null) return;
        long tick = serverWorld != null ? serverWorld.getTime() : 0L;
        StructureDamageState normalizedTarget = targetStage.templateStage();
        StructureDamageState prevStage = structure.damageStage().templateStage();
        boolean wasOffline = DestructibleStructureRegistry.isSubstationOffline();

        DELAYED_COLLAPSES.remove(structure.structureId());
        if (normalizedTarget != StructureDamageState.INTACT && normalizedTarget.index < prevStage.index) {
            com.bobincraft.destruction.debris.StructureDebrisManager.resetStructure(serverWorld, structure.structureId());
        }
        if (normalizedTarget == StructureDamageState.INTACT) {
            DestructionScenarioDirector.clearForStructure(structure.structureId());
            com.bobincraft.destruction.debris.StructureDebrisManager.resetStructure(serverWorld, structure.structureId());
        }
        structure.forceStage(normalizedTarget, tick);
        DestructibleStructureRegistry.updateSnapshot(structure);

        if ((normalizedTarget == StructureDamageState.CRITICAL || normalizedTarget == StructureDamageState.DAMAGED)
                && structure.structureType().flammable()) {
            float fInt = normalizedTarget == StructureDamageState.CRITICAL ? 0.85f : 0.65f;
            StructureFireManager.igniteOrIntensify(serverWorld, structure, fInt, structure.structureType().defaultBurnTicks(), tick);
        } else if (normalizedTarget == StructureDamageState.INTACT) {
            StructureFireManager.extinguish(serverWorld, structure, tick);
        }

        if (normalizedTarget != prevStage && normalizedTarget != StructureDamageState.INTACT) {
            if (normalizedTarget == StructureDamageState.DESTROYED) {
                if (serverWorld != null && (structure.structureType().category() == DestructibleStructureType.Category.REFINERY_TANK
                        || structure.structureType().category() == DestructibleStructureType.Category.SUBSTATION_TRANSFORMER)) {
                    triggerCatastrophicStructureExplosion(serverWorld, structure, Vec3d.ofCenter(structure.originPosition()));
                }
                Optional<DestructionScenarioDirector.ScenarioEvent> scenarioOpt =
                        DestructionScenarioDirector.triggerCollapseScenario(
                                serverWorld, structure, prevStage,
                                structure.lastHitComponentId(), structure.lastHitZone(),
                                Vec3d.ofCenter(structure.originPosition()), tick);
                if (serverWorld != null && scenarioOpt.isPresent()) {
                    StructureNetworkSync.broadcastStageTransition(serverWorld, structure, prevStage, normalizedTarget, scenarioOpt.get());
                }
            } else {
                DestructionScenarioDirector.recordNonTerminalHit(structure, normalizedTarget);
                if (serverWorld != null) {
                    StructureNetworkSync.broadcastStructureHitEffect(
                            serverWorld, structure, prevStage, normalizedTarget,
                            structure.lastHitComponentId(), structure.lastHitZone(),
                            Vec3d.ofCenter(structure.originPosition()));
                }
            }
        }

        WarzoneTerrain terrain = resolveTerrain(serverWorld);
        StructureUpdateQueue.enqueueTransition(terrain, structure, prevStage, normalizedTarget, 0);

        boolean nowOffline = DestructibleStructureRegistry.isSubstationOffline();
        if (wasOffline != nowOffline && serverWorld != null) {
            PowerGrid.onSubstationOfflineChanged(serverWorld, nowOffline);
        }

        if (serverWorld != null) {
            StructurePersistentState.get(serverWorld).recordStructure(structure);
            StructureNetworkSync.broadcastStructureState(serverWorld, structure);
        }
    }

    public static synchronized void resetAll(ServerWorld serverWorld) {
        long tick = serverWorld != null ? serverWorld.getTime() : 0L;
        WarzoneTerrain terrain = resolveTerrain(serverWorld);
        DELAYED_COLLAPSES.clear();
        PROCESSED_EVENT_HITS.clear();
        lastDispatchedEventType = DamageEventType.NONE;
        DestructionScenarioDirector.resetAll();
        for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
            StructureDamageState prev = s.damageStage().templateStage();
            boolean hadBreach = !s.localBreachCells().isEmpty();
            com.bobincraft.destruction.debris.StructureDebrisManager.resetStructure(serverWorld, s.structureId());
            s.reset(tick);
            DestructibleStructureRegistry.updateSnapshot(s);
            if (prev != StructureDamageState.INTACT || hadBreach) {
                StructureUpdateQueue.enqueueTransition(terrain, s, prev, StructureDamageState.INTACT, 0);
            }
        }
        StructureFireManager.clear();
        if (serverWorld != null) {
            PowerGrid.onSubstationOfflineChanged(serverWorld, false);
            StructurePersistentState.get(serverWorld).clearAll(tick);
            for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
                StructureNetworkSync.broadcastStructureState(serverWorld, s);
            }
        }
    }

    private static void trace(StructureDamageSource source, Vec3d impact, PrimaryHitTarget target, float damage, String reason) {
        if (!StructureDamageTrace.enabled()) return;
        StructureDamageTrace.add("IMPACT event=" + source.eventId() + " weapon=" + source.kind() + " position=" + impact
                + " surface_normal=" + source.surfaceNormal() + " structure=" + (target == null ? "none" : target.structure().structureId()) + " component=" + (target == null ? "none" : target.componentId())
                + " zone=" + (target == null ? "none" : target.hitZone()) + " effective=" + damage + " reason=" + reason);
    }

    private record DebrisSurface(net.minecraft.block.BlockState state, Vec3d point, Vec3d normal) {}
    private static DebrisSurface debrisSurface(ServerWorld world, DestructibleStructure structure, Vec3d impact) {
        BlockPos center = BlockPos.ofFloored(impact);
        for (BlockPos p : BlockPos.iterate(center.add(-1, -1, -1), center.add(1, 1, 1))) {
            if (!world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4) || !world.isInBuildLimit(p)) continue;
            var state = world.getBlockState(p);
            if (state.isAir() || state.getCollisionShape(world, p).isEmpty()
                    || structure.hitZoneAt(Vec3d.ofCenter(p), .75) == DestructibleStructure.HitZone.NONE) continue;
            Vec3d delta = impact.subtract(Vec3d.ofCenter(p));
            Vec3d normal = Math.abs(delta.y) >= Math.max(Math.abs(delta.x), Math.abs(delta.z))
                    ? new Vec3d(0, delta.y < 0 ? -1 : 1, 0) : Math.abs(delta.x) > Math.abs(delta.z)
                    ? new Vec3d(delta.x < 0 ? -1 : 1, 0, 0) : new Vec3d(0, 0, delta.z < 0 ? -1 : 1);
            return new DebrisSurface(state, Vec3d.ofCenter(p).add(normal.multiply(.56)), normal);
        }
        return null;
    }

    private static void spawnLocalHitDebris(ServerWorld world, DestructibleStructure structure, Vec3d impact, StructureDamageState stage) {
        var surface = debrisSurface(world, structure, impact);
        if (surface != null) ImpactDebris.send(world, surface.state(), surface.point(), surface.normal(), 1.85f, null);
    }

    private static void spawnTransitionDebris(ServerWorld world, DestructibleStructure structure, Vec3d impact, StructureDamageState newStage) {
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0;
            var surface = debrisSurface(world, structure, impact.add(Math.cos(a) * 1.5, .25, Math.sin(a) * 1.5));
            if (surface != null) ImpactDebris.send(world, surface.state(), surface.point(), surface.normal(), 2.65f, null);
        }
    }

    private static synchronized boolean markEventProcessed(UUID eventId) {
        if (eventId == null) return true;
        if (PROCESSED_EVENT_HITS.containsKey(eventId)) {
            return false;
        }
        Set<String> set = new HashSet<>(2);
        set.add("primary");
        PROCESSED_EVENT_HITS.put(eventId, set);
        return true;
    }

    public static DamageEventType lastDispatchedEventType() {
        return lastDispatchedEventType;
    }

    public static String lastAffectedStructureId() {
        return lastAffectedStructureId;
    }

    public static String lastAffectedComponentId() {
        return lastAffectedComponentId;
    }

    public static void ensureRegistryReady(ServerWorld world) {
        WarzoneTerrain terrain = resolveTerrain(world);
        if (terrain != null) {
            DestructibleStructureRegistry.ensureInitialized(terrain);
        }
    }

    public static WarzoneTerrain resolveTerrain(ServerWorld world) {
        if (world != null) {
            WarzoneChunkGenerator gen = WarzoneDimension.generatorOf(world);
            if (gen != null) {
                try {
                    return gen.terrain();
                } catch (IllegalStateException ignored) {
                }
            }
        }
        return DestructibleStructureRegistry.lastTerrain();
    }
}
