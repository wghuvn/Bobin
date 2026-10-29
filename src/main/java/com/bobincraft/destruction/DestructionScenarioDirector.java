package com.bobincraft.destruction;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer B (Destruction Scenarios) authoritative orchestrator for Scripted Structural Destruction (Section 2 & Section 4 Level B).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Strictly separates {@code STRUCTURE_HIT} (non-terminal wear/damage while {@code currentHealth > 0})
 *       from {@code STRUCTURE_DESTROYED} (terminal collapse when {@code previousHealth > 0 && currentHealth <= 0}).</li>
 *   <li>Full collapse animations ({@link ScenarioEvent}) are triggered ONLY when a structure transitions to
 *       {@link StructureDamageState#DESTROYED} (through {@link StructureDamageState#DESTROYING}).</li>
 *   <li>Deduplicates collapse scenarios per {@code structureId} so repeated impacts or chunk reloads never
 *       replay the collapse animation.</li>
 * </ul>
 */
public final class DestructionScenarioDirector {

    public enum ScenarioProfile {
        BRIDGE_SPAN_COLLAPSE("bridge_collapse", 10, 22, 52),
        BRIDGE_PIER_SHEAR("bridge_collapse", 8, 20, 48),
        REFINERY_TANK_RUPTURE("refinery_tank_collapse", 10, 22, 56),
        SUBSTATION_TRANSFORMER_BLOWOUT("substation_collapse", 8, 18, 46),
        SUBSTATION_SWITCHGEAR_COLLAPSE("substation_collapse", 8, 16, 44);

        public final String modelId;
        public final int crackPhaseTicks;
        public final int commitTickOffset;
        public final int totalDurationTicks;

        ScenarioProfile(String modelId, int crackPhaseTicks, int commitTickOffset, int totalDurationTicks) {
            this.modelId = modelId;
            this.crackPhaseTicks = crackPhaseTicks;
            this.commitTickOffset = commitTickOffset;
            this.totalDurationTicks = totalDurationTicks;
        }
    }

    public record ScenarioEvent(
            long sequenceId,
            String structureId,
            DestructibleStructureType.Category category,
            StructureDamageState fromStage,
            StructureDamageState toStage,
            ScenarioProfile profile,
            String componentId,
            DestructibleStructure.HitZone hitZone,
            BlockPos origin,
            Vec3d impactPoint,
            int orientation,
            int variantIndex,
            long startTick,
            int durationTicks
    ) {
        public String animationKey() {
            String prefix = switch (category) {
                case BRIDGE_SECTION -> "animation.bobin_craft.bridge.";
                case REFINERY_TANK -> "animation.bobin_craft.tank.";
                default -> "animation.bobin_craft.substation.";
            };
            return prefix + toStage.templateStage().modelName;
        }
    }

    private record ExecutedStageKey(String structureId, StructureDamageState stage) {}

    private static final Set<ExecutedStageKey> EXECUTED_SCENARIOS = ConcurrentHashMap.newKeySet();
    private static final Set<String> EXECUTED_COLLAPSE_ANIMATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<String, ScenarioEvent> ACTIVE_SCENARIOS = new ConcurrentHashMap<>();
    private static long sequenceCounter = 1L;

    private DestructionScenarioDirector() {}

    /**
     * Records a non-terminal {@code STRUCTURE_HIT} wear stage transition ({@code DAMAGED} or {@code CRITICAL})
     * WITHOUT launching any active collapse animation (Section 2).
     */
    public static synchronized void recordNonTerminalHit(DestructibleStructure structure, StructureDamageState newStage) {
        if (structure == null || newStage == null || newStage == StructureDamageState.INTACT) return;
        StructureDamageState normalized = newStage.templateStage();
        if (normalized == StructureDamageState.DAMAGED || normalized == StructureDamageState.CRITICAL) {
            EXECUTED_SCENARIOS.add(new ExecutedStageKey(structure.structureId(), normalized));
        }
    }

    /**
     * Triggers a full collapse scenario ONLY when a structure transitions to {@link StructureDamageState#DESTROYED}
     * (Section 2.2: {@code previousHealth > 0 && currentHealth <= 0 && !animationPlayed}).
     */
    public static synchronized Optional<ScenarioEvent> triggerCollapseScenario(ServerWorld world,
                                                                               DestructibleStructure structure,
                                                                               StructureDamageState fromStage,
                                                                               String hitComponentId,
                                                                               DestructibleStructure.HitZone hitZone,
                                                                               Vec3d impactPoint,
                                                                               long worldTick) {
        return triggerScenario(world, structure, fromStage, StructureDamageState.DESTROYED,
                hitComponentId, hitZone, impactPoint, worldTick);
    }

    /**
     * Selects and triggers a scripted destruction scenario.
     * Per Section 2, full collapse scenarios ({@link #ACTIVE_SCENARIOS}) are ONLY created when {@code toStage}
     * is terminal ({@link StructureDamageState#DESTROYING} or {@link StructureDamageState#DESTROYED}).
     * Non-terminal stages ({@code DAMAGED}, {@code CRITICAL}) record their wear stage transition but return
     * {@link Optional#empty()} so the full collapse animation never plays on ordinary hits!
     */
    public static synchronized Optional<ScenarioEvent> triggerScenario(ServerWorld world,
                                                                       DestructibleStructure structure,
                                                                       StructureDamageState fromStage,
                                                                       StructureDamageState toStage,
                                                                       String hitComponentId,
                                                                       DestructibleStructure.HitZone hitZone,
                                                                       Vec3d impactPoint,
                                                                       long worldTick) {
        if (structure == null || toStage == null || toStage == StructureDamageState.INTACT) {
            return Optional.empty();
        }
        StructureDamageState normalizedTo = toStage.templateStage();
        if (fromStage != null && normalizedTo.index <= fromStage.templateStage().index) {
            return Optional.empty();
        }

        // Section 2: If the target stage is DAMAGED or CRITICAL (HP > 0), record the wear stage but NEVER start a collapse scenario!
        if (!normalizedTo.isTerminalOrDestroying()) {
            EXECUTED_SCENARIOS.add(new ExecutedStageKey(structure.structureId(), normalizedTo));
            return Optional.empty();
        }

        ExecutedStageKey key = new ExecutedStageKey(structure.structureId(), StructureDamageState.DESTROYED);
        if (!EXECUTED_SCENARIOS.add(key) || !EXECUTED_COLLAPSE_ANIMATIONS.add(structure.structureId())) {
            // Collapse animation for this structure was already executed; do not replay!
            return Optional.empty();
        }
        for (StructureDamageState st : StructureDamageState.values()) {
            if (st != StructureDamageState.INTACT) {
                EXECUTED_SCENARIOS.add(new ExecutedStageKey(structure.structureId(), st.templateStage()));
            }
        }
        structure.setAnimationPlayed(true);

        ScenarioProfile profile = selectProfile(structure, hitComponentId, hitZone);
        String comp = (hitComponentId != null && !hitComponentId.isEmpty())
                ? hitComponentId
                : structure.defaultComponentId();
        DestructibleStructure.HitZone zone = (hitZone != null && hitZone != DestructibleStructure.HitZone.NONE)
                ? hitZone
                : DestructibleStructure.HitZone.HULL;
        Vec3d impact = impactPoint != null ? impactPoint : Vec3d.ofCenter(structure.originPosition());

        ScenarioEvent event = new ScenarioEvent(
                sequenceCounter++,
                structure.structureId(),
                structure.structureType().category(),
                fromStage != null ? fromStage.templateStage() : StructureDamageState.INTACT,
                StructureDamageState.DESTROYED,
                profile,
                comp,
                zone,
                structure.originPosition(),
                impact,
                structure.orientation(),
                structure.destroyedVariantIndex(),
                worldTick,
                profile.totalDurationTicks
        );

        ACTIVE_SCENARIOS.put(structure.structureId(), event);
        return Optional.of(event);
    }

    public static ScenarioProfile selectProfile(DestructibleStructure structure,
                                                String componentId,
                                                DestructibleStructure.HitZone hitZone) {
        return switch (structure.structureType().category()) {
            case BRIDGE_SECTION -> {
                if (hitZone == DestructibleStructure.HitZone.SUPPORT
                        || (componentId != null && componentId.contains("support"))) {
                    yield ScenarioProfile.BRIDGE_PIER_SHEAR;
                }
                yield ScenarioProfile.BRIDGE_SPAN_COLLAPSE;
            }
            case REFINERY_TANK -> ScenarioProfile.REFINERY_TANK_RUPTURE;
            case SUBSTATION_TRANSFORMER -> ScenarioProfile.SUBSTATION_TRANSFORMER_BLOWOUT;
            case SUBSTATION_EQUIPMENT -> ScenarioProfile.SUBSTATION_SWITCHGEAR_COLLAPSE;
            default -> ScenarioProfile.SUBSTATION_TRANSFORMER_BLOWOUT;
        };
    }

    /**
     * Marks already-damaged or destroyed structures loaded from PersistentState as having completed their scenarios
     * so chunk reloads or server restarts never replay the destruction animation (Section 2.4 & Section 11).
     */
    public static synchronized void markLoadedStateWithoutReplay(DestructibleStructure structure) {
        if (structure == null) return;
        ACTIVE_SCENARIOS.remove(structure.structureId());
        StructureDamageState current = structure.damageStage().templateStage();
        for (StructureDamageState st : StructureDamageState.values()) {
            if (st != StructureDamageState.INTACT && st.templateStage().index <= current.index) {
                EXECUTED_SCENARIOS.add(new ExecutedStageKey(structure.structureId(), st.templateStage()));
            }
        }
        if (current.isTerminalOrDestroying() || structure.destroyed()) {
            EXECUTED_COLLAPSE_ANIMATIONS.add(structure.structureId());
            structure.setAnimationPlayed(true);
        }
    }

    public static synchronized void clearForStructure(String structureId) {
        if (structureId == null) return;
        ACTIVE_SCENARIOS.remove(structureId);
        EXECUTED_COLLAPSE_ANIMATIONS.remove(structureId);
        EXECUTED_SCENARIOS.removeIf(k -> k.structureId.equals(structureId));
    }

    public static synchronized void resetAll() {
        ACTIVE_SCENARIOS.clear();
        EXECUTED_COLLAPSE_ANIMATIONS.clear();
        EXECUTED_SCENARIOS.clear();
    }

    public static synchronized void tickServer(long worldTick) {
        if (ACTIVE_SCENARIOS.isEmpty()) return;
        ACTIVE_SCENARIOS.values().removeIf(ev -> worldTick - ev.startTick >= ev.durationTicks);
    }

    public static boolean hasExecutedScenario(String structureId, StructureDamageState stage) {
        if (stage == null) return false;
        return EXECUTED_SCENARIOS.contains(new ExecutedStageKey(structureId, stage.templateStage()));
    }

    public static boolean hasPlayedCollapseAnimation(String structureId) {
        return EXECUTED_COLLAPSE_ANIMATIONS.contains(structureId);
    }

    public static Optional<ScenarioEvent> activeScenario(String structureId) {
        return Optional.ofNullable(ACTIVE_SCENARIOS.get(structureId));
    }

    public static Collection<ScenarioEvent> activeScenarios() {
        return Collections.unmodifiableCollection(ACTIVE_SCENARIOS.values());
    }
}
