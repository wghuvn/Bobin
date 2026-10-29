package com.bobincraft.destruction;

import com.bobincraft.warzone.Blueprint;
import com.bobincraft.warzone.ExclusionZonePreset;
import com.bobincraft.warzone.WarzoneTerrain;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.UUID;

/**
 * Fabric GameTest suite verifying all core Scripted Structural Destruction scenarios (Sections 1, 2, 3.7):
 * <ul>
 *   <li>Single-component structural hit isolation on bridges (hitting {@code Support_02} only damages {@code Support_02},
 *       leaving {@code Support_01} and {@code Support_03} at 100% HP).</li>
 *   <li>Strict separation of {@code STRUCTURE_HIT} (HP > 0: wear/cracks, no collapse animation) vs
 *       {@code STRUCTURE_DESTROYED} (HP reaches 0: full collapse animation once).</li>
 *   <li>Destroyed bridge deck becomes physically impassable (true gap in deck blocks & navigation query).</li>
 *   <li>Destruction state persists across world save/reload without replaying the animation scenario.</li>
 *   <li>Repeated hits never trigger the collapse animation more than once.</li>
 * </ul>
 */
public final class DestructionGameTestSuite implements FabricGameTest {

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void stabilizationPayloadPolicy(TestContext context) {
        ensureTerrain();
        for (var target : DestructibleStructureRegistry.all()) {
            var cat = target.structureType().category();
            if (cat != DestructibleStructureType.Category.REFINERY_TANK && cat != DestructibleStructureType.Category.BRIDGE_SECTION) continue;
            for (var kind : new StructureDamageSource.Kind[]{StructureDamageSource.Kind.HAND_GRENADE,
                    StructureDamageSource.Kind.DRONE_PAYLOAD, StructureDamageSource.Kind.HEAVY_DRONE_PAYLOAD}) {
                var before = target.toCompactNbt().copy();
                var point = Vec3d.ofCenter(target.originPosition());
                var source = StructureDamageSource.explosion(kind, null, point, 20);
                if (StructureDamageManager.applyDirectStructureDamage(null, target, source, 10000))
                    throw new AssertionError("Forbidden direct payload damage: " + kind + target.structureId());
                StructureDamageManager.applyDamageInternal(null, point, source, 10000);
                StructureDamageManager.tickDelayedCollapses(context.getWorld(), context.getWorld().getTime() + 600);
                if (!before.equals(target.toCompactNbt()))
                    throw new AssertionError("Forbidden payload mutated HP/stage/component/fire/transition state");
            }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void stabilizationJournalRoundTrip(TestContext context) {
        var state=new RefineryTransitionState();var ledger=new TransitionLedger();
        long p=BlockPos.asLong(-17,77,-33);ledger.noteOwned(p,"minecraft:stone");
        ledger.retarget(java.util.Map.of(p,"minecraft:air"));ledger.protect(p);
        state.ledgers.put("test-tank",ledger);
        var saved=state.writeNbt(new NbtCompound());
        var restored=RefineryTransitionState.read(saved);
        if(!saved.equals(restored.writeNbt(new NbtCompound())))throw new AssertionError("Journal NBT round trip changed data");
        var l=restored.ledgers.get("test-tank");l.rebuildScheduling();
        if(l.hasReady()||l.canApply(p,"minecraft:stone"))throw new AssertionError("Reload unprotected a player replacement");
        var future=saved.copy();future.putInt("Version",99);future.putString("Unrecognized","preserve");
        var frozen=RefineryTransitionState.read(future);
        if(frozen.writable()||!future.equals(frozen.writeNbt(new NbtCompound())))throw new AssertionError("Future journal not preserved read-only");
        context.complete();
    }

    /** Actual generated cells and registry index, not replacement geometry. Requires the Fabric runtime. */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void stabilizationManagedGeometryTerrainPolicy(TestContext context) throws Exception {
        var terrain = ensureTerrain();
        var allowed = com.bobincraft.drone.BlastTerrain.class.getDeclaredMethod("terrainAllowed", String.class, BlockPos.class);
        allowed.setAccessible(true);
        int[] cells = {0}, outsideHitZones = {0};
        java.util.function.Consumer<Blueprint> audit = blueprint -> blueprint.forEachCell((x,y,z,spec,mode) -> {
            if (spec.isAir()) return;
            cells[0]++;
            var pos = new BlockPos(x,y,z);
            var primary = DestructibleStructureRegistry.spatialIndex().findPrimaryAt(Vec3d.ofCenter(pos));
            if (primary == null) outsideHitZones[0]++;
            var state = com.bobincraft.warzone.BlueprintBlitter.state(spec);
            // Forbidden profiles never enter the KAMIKAZE_BREACH / FP1-only breach or fire branches.
            // Their remaining possible structural edits are tagged glass/soil/plant writes.
            boolean mutable = state.isIn(com.bobincraft.drone.BlastTerrain.SHATTERABLE_GLASS)
                    || state.isIn(com.bobincraft.drone.BlastTerrain.SCORCHABLE_SOIL)
                    || state.isIn(com.bobincraft.drone.BlastTerrain.SCORCHABLE_PLANTS);
            for (String source : new String[]{"hand_grenade","drone_payload","heavy_drone_payload"}) {
                try {
                    if ((boolean) allowed.invoke(null, source, pos) && mutable)
                        throw new AssertionError("Unprotected mutable structural cell " + blueprint.name + " " + pos + " " + spec);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        });
        for (var target : DestructibleStructureRegistry.all()) {
            if (target.structureType().category() == DestructibleStructureType.Category.REFINERY_TANK) {
                audit.accept(StructureGeometrySnapshot.capture(terrain, target));
                for (var stage : new StructureDamageState[]{StructureDamageState.DAMAGED, StructureDamageState.CRITICAL, StructureDamageState.DESTROYED})
                    audit.accept(StructureGeometrySnapshot.stage(terrain, target, stage));
            } else if (target.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION) {
                for (var stage : new StructureDamageState[]{StructureDamageState.INTACT, StructureDamageState.DAMAGED, StructureDamageState.CRITICAL, StructureDamageState.DESTROYED})
                    audit.accept(StructureCollapseManager.buildBridgeSectionStage(terrain, target, stage));
            }
        }
        for (var crossing : terrain.zonePlan().crossings())
            audit.accept(com.bobincraft.warzone.ZoneInfrastructure.bridgeBlueprint(terrain, crossing));
        if (cells[0] == 0) throw new AssertionError("No actual managed geometry audited");
        // Outside all registered geometry: retain ordinary terrain, even for forbidden structural sources.
        var ordinary = new BlockPos(1600, 80, 1600);
        if (DestructibleStructureRegistry.spatialIndex().findPrimaryAt(Vec3d.ofCenter(ordinary)) != null)
            throw new AssertionError("Ordinary-terrain fixture overlaps a managed structure");
        for (String source : new String[]{"hand_grenade","drone_payload","heavy_drone_payload"})
            if (!(boolean) allowed.invoke(null, source, ordinary)) throw new AssertionError("Ordinary terrain blocked");
        System.out.println("Managed geometry terrain audit: " + cells[0] + " occupied cells; "
                + outsideHitZones[0] + " outside primary hit zones; all forbidden-source edit gates safe");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void debrisNbtPhases(TestContext context) throws Exception {
        com.bobincraft.destruction.debris.DebrisGameTests.roundTrip(context);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void debrisDurableDiskAndFutureVersion(TestContext context) throws Exception {
        com.bobincraft.destruction.debris.DebrisGameTests.disk(context);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void debrisCommitCrashRecovery(TestContext context) throws Exception {
        com.bobincraft.destruction.debris.DebrisGameTests.commitAndRecovery(context);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void debrisProtectedPartialCommit(TestContext context) throws Exception {
        com.bobincraft.destruction.debris.DebrisGameTests.protectedPartial(context);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
    public void debrisActualSlabSupportLoss(TestContext context) throws Exception {
        com.bobincraft.destruction.debris.DebrisGameTests.actualSlabSupport(context);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void debrisBridgeNoDuplicateStaticPiles(TestContext context) {
        var terrain=ensureTerrain();int[] surviving={0};
        for(var crossing:terrain.zonePlan().crossings()) {
            var source=com.bobincraft.warzone.ZoneInfrastructure.bridgeBlueprint(terrain,crossing);
            for(var section:DestructibleStructureRegistry.bridgeSectionsForCrossing(crossing.id()))
                for(var stage:new StructureDamageState[]{StructureDamageState.DAMAGED,StructureDamageState.CRITICAL,StructureDamageState.DESTROYED}) {
                    var bp=StructureCollapseManager.getStageBlueprint(terrain,section,stage);
                    bp.forEachCell((x,y,z,spec,mode)->{if(spec.isAir())return;var original=source.get(x,y,z);
                        if(original==null||original.isAir())throw new AssertionError("Source-less decorative pile duplicates physical debris: "+x+","+y+","+z);
                        surviving[0]++;
                    });
                }
        }
        if(surviving[0]==0)throw new AssertionError("Legitimate surviving structure was hidden");
        context.complete();
    }

    private static WarzoneTerrain cachedTerrain;

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 120)
    public void correctionCollisionBudgetFairness(TestContext context) {
        com.bobincraft.destruction.debris.DebrisGameTests.budgetFairness(context);
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void correctionGeneratedGeometryAndContactCoverage(TestContext context) {
        var terrain=ensureTerrain();int[] checked={0};
        for(var target:DestructibleStructureRegistry.all()) {
            var category=target.structureType().category();
            if(category!=DestructibleStructureType.Category.BRIDGE_SECTION&&category!=DestructibleStructureType.Category.SUBSTATION_TRANSFORMER&&category!=DestructibleStructureType.Category.SUBSTATION_EQUIPMENT)continue;
            var intact=StructureCollapseManager.getStageBlueprint(terrain,target,StructureDamageState.INTACT);
            intact.forEachCell((x,y,z,spec,mode)->{
                if(spec.isAir())return;
                var point=new Vec3d(x+.5,y+.5,z+.5);
                if(!target.matchAt(point,0).isHit())throw new AssertionError("Unregistered actual geometry: "+target.structureId()+" "+point);
                var source=StructureDamageSource.direct(StructureDamageSource.Kind.RPG_PROJECTILE,null,point);
                if(StructureDamageManager.selectPrimaryTarget(point,source).isEmpty())throw new AssertionError("Actual RPG contact lost in spatial index");
                checked[0]++;
            });
            for(var stage:new StructureDamageState[]{StructureDamageState.DAMAGED,StructureDamageState.CRITICAL,StructureDamageState.DESTROYED}) {
                var damaged=StructureCollapseManager.getStageBlueprint(terrain,target,stage);
                damaged.forEachCell((x,y,z,spec,mode)->{
                    if(spec.isAir())return;var original=intact.get(x,y,z);
                    if(original==null||!original.equals(spec))throw new AssertionError("Invented/mutated surviving source material: "+target.structureId()+" "+stage);
                });
            }
        }
        if(checked[0]<100)throw new AssertionError("Missing geometry fixture");context.complete();
    }

    private static synchronized WarzoneTerrain ensureTerrain() {
        if (cachedTerrain == null) {
            cachedTerrain = new WarzoneTerrain(42L, 2048, ExclusionZonePreset.VERSION);
        }
        DestructibleStructureRegistry.initializeFromPlan(cachedTerrain, cachedTerrain.zonePlan());
        StructureUpdateQueue.clear();
        StructureFireManager.clear();
        DestructionScenarioDirector.resetAll();
        return cachedTerrain;
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void bridgeSectionTakesDamageAndChangesStage(TestContext context) {
        runBridgeSectionTakesDamageAndChangesStage();
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void destroyedBridgeDeckIsPhysicallyImpassable(TestContext context) {
        runDestroyedBridgeDeckIsPhysicallyImpassable();
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void destructionPersistsAcrossReloadWithoutReplayingScenario(TestContext context) {
        runDestructionPersistsAcrossReloadWithoutReplayingScenario();
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 100)
    public void repeatedHitsNeverDuplicateSameScenario(TestContext context) {
        runRepeatedHitsNeverDuplicateSameScenario();
        context.complete();
    }

    public static void runBridgeSectionTakesDamageAndChangesStage() {
        ensureTerrain();
        DestructibleStructure support1 = DestructibleStructureRegistry.get("bridge_central_section_01");
        DestructibleStructure support2 = DestructibleStructureRegistry.get("bridge_central_section_02");
        DestructibleStructure support3 = DestructibleStructureRegistry.get("bridge_central_section_03");
        if (support1 == null || support2 == null || support3 == null) {
            throw new AssertionError("Missing bridge_central sections");
        }

        BlockPos origin = support2.originPosition();
        Vec3d supportHitPos = new Vec3d(origin.getX() + 0.5, origin.getY() - 4.0, origin.getZ() + 0.5);
        DestructibleStructure.HitMatch match = support2.matchAt(supportHitPos, 0.5);
        if (match.zone() != DestructibleStructure.HitZone.SUPPORT || !"center_support".equals(match.componentId())) {
            throw new AssertionError("Expected center_support SUPPORT hit zone, got " + match);
        }

        // Section 1 check: explosion at Support_02 with large blast radius (12.0) must ONLY damage Support_02!
        StructureDamageSource blastSource = new StructureDamageSource(
                UUID.randomUUID(), StructureDamageSource.Kind.ARMOR_SHELL, UUID.randomUUID(), supportHitPos, 12.0);
        int affected = StructureDamageManager.applyDamageInternal(null, supportHitPos, blastSource, 280.0f);
        if (affected != 1) {
            throw new AssertionError("Explosion at Support_02 must affect exactly 1 structure, got " + affected);
        }
        if (support1.currentHealth() < support1.maxHealth() || support3.currentHealth() < support3.maxHealth()) {
            throw new AssertionError("Neighboring supports Support_01 and Support_03 must stay at 100% HP");
        }
        if (support2.damageStage() != StructureDamageState.DAMAGED) {
            throw new AssertionError("Support_02 should transition to DAMAGED, stage=" + support2.damageStage());
        }
        // Section 2 check: non-terminal hit (DAMAGED) must dispatch STRUCTURE_HIT and MUST NOT start collapse animation!
        if (StructureDamageManager.lastDispatchedEventType() != StructureDamageManager.DamageEventType.STRUCTURE_HIT) {
            throw new AssertionError("Expected STRUCTURE_HIT on non-terminal damage");
        }
        if (DestructionScenarioDirector.activeScenario(support2.structureId()).isPresent()) {
            throw new AssertionError("Collapse animation must NOT play on non-terminal DAMAGED hit!");
        }
    }

    public static void runDestroyedBridgeDeckIsPhysicallyImpassable() {
        WarzoneTerrain terrain = ensureTerrain();
        DestructibleStructure bridge = DestructibleStructureRegistry.get("bridge_central_section_02");
        BlockPos origin = bridge.originPosition();

        StructureDamageManager.forceStage(null, bridge, StructureDamageState.DESTROYED);
        if (!DestructibleStructureRegistry.isBridgeSectionDestroyedAt(origin.getX(), origin.getZ())) {
            throw new AssertionError("Navigation query must report destroyed bridge section impassable");
        }

        Blueprint intactBp = StructureCollapseManager.getStageBlueprint(terrain, bridge, StructureDamageState.INTACT);
        Blueprint destroyedBp = StructureCollapseManager.getStageBlueprint(terrain, bridge, StructureDamageState.DESTROYED);
        if (intactBp.get(origin.getX(), origin.getY(), origin.getZ()).isAir()) {
            throw new AssertionError("INTACT bridge must have solid deck at center");
        }
        if (!destroyedBp.get(origin.getX(), origin.getY(), origin.getZ()).isAir()
                || !destroyedBp.get(origin.getX(), origin.getY() - 1, origin.getZ()).isAir()) {
            throw new AssertionError("DESTROYED bridge must have physical gap in deck and girder at center");
        }
    }

    public static void runDestructionPersistsAcrossReloadWithoutReplayingScenario() {
        ensureTerrain();
        DestructibleStructure bridge = DestructibleStructureRegistry.get("bridge_central_section_01");
        DestructibleStructure tank = DestructibleStructureRegistry.get("refinery_tank_01");

        StructureDamageManager.forceStage(null, bridge, StructureDamageState.DESTROYED);
        tank.applyComponentDamage("upper_roof", DestructibleStructure.HitZone.ROOF, 750.0f, 50L);
        tank.setFireState(true, 0.90f, 1000, 50L);
        DestructibleStructureRegistry.updateSnapshot(tank);

        StructurePersistentState saved = new StructurePersistentState();
        saved.captureFromRegistry();
        NbtCompound nbt = saved.writeNbt(new NbtCompound());

        // Simulate full server restart
        DestructibleStructureRegistry.resetAll(100L);
        StructurePersistentState reloaded = StructurePersistentState.read(nbt);
        reloaded.applyToRegistry(150L);

        if (bridge.damageStage() != StructureDamageState.DESTROYED || !bridge.destroyed()) {
            throw new AssertionError("Bridge DESTROYED state must survive reload");
        }
        if (tank.damageStage() != StructureDamageState.CRITICAL || !tank.isBurning()) {
            throw new AssertionError("Tank CRITICAL burning state must survive reload");
        }
        // Attempting to trigger the DESTROYED scenario on the reloaded bridge must return empty (no replay!)
        Optional<DestructionScenarioDirector.ScenarioEvent> replayAttempt =
                DestructionScenarioDirector.triggerScenario(
                        null, bridge, StructureDamageState.CRITICAL, StructureDamageState.DESTROYED,
                        "left_support", DestructibleStructure.HitZone.SUPPORT, Vec3d.ofCenter(bridge.originPosition()), 160L);
        if (replayAttempt.isPresent()) {
            throw new AssertionError("Reloaded destroyed structure must never replay its destruction scenario");
        }
    }

    public static void runRepeatedHitsNeverDuplicateSameScenario() {
        ensureTerrain();
        DestructibleStructure tank = DestructibleStructureRegistry.get("refinery_tank_02");
        Vec3d hitPos = Vec3d.ofCenter(tank.originPosition()).add(0, 4, 0);
        StructureDamageSource src1 = StructureDamageSource.direct(StructureDamageSource.Kind.RPG_PROJECTILE, null, hitPos);

        // Hit 1 (350 dmg -> 650 HP -> DAMAGED): must dispatch STRUCTURE_HIT and MUST NOT play collapse animation
        StructureDamageManager.applyDirectStructureDamage(null, tank, src1, 350.0f, "side_hull", DestructibleStructure.HitZone.HULL);
        if (StructureDamageManager.lastDispatchedEventType() != StructureDamageManager.DamageEventType.STRUCTURE_HIT) {
            throw new AssertionError("Hit 1 (HP > 0) must be STRUCTURE_HIT");
        }
        if (DestructionScenarioDirector.activeScenario(tank.structureId()).isPresent()) {
            throw new AssertionError("Collapse animation must NOT start while HP > 0 (DAMAGED)");
        }

        // Hit 2 (400 dmg -> 250 HP -> CRITICAL): must dispatch STRUCTURE_HIT and MUST NOT play collapse animation
        StructureDamageManager.applyDirectStructureDamage(null, tank, src1, 400.0f, "side_hull", DestructibleStructure.HitZone.HULL);
        if (StructureDamageManager.lastDispatchedEventType() != StructureDamageManager.DamageEventType.STRUCTURE_HIT) {
            throw new AssertionError("Hit 2 (HP > 0) must be STRUCTURE_HIT");
        }
        if (DestructionScenarioDirector.activeScenario(tank.structureId()).isPresent()) {
            throw new AssertionError("Collapse animation must NOT start while HP > 0 (CRITICAL)");
        }

        // Hit 3 (300 dmg -> 0 HP -> DESTROYED): must dispatch STRUCTURE_DESTROYED and start collapse animation ONCE!
        StructureDamageManager.applyDirectStructureDamage(null, tank, src1, 300.0f, "side_hull", DestructibleStructure.HitZone.HULL);
        if (StructureDamageManager.lastDispatchedEventType() != StructureDamageManager.DamageEventType.STRUCTURE_DESTROYED) {
            throw new AssertionError("Hit 3 (0 HP) must be STRUCTURE_DESTROYED");
        }
        Optional<DestructionScenarioDirector.ScenarioEvent> collapseAnim = DestructionScenarioDirector.activeScenario(tank.structureId());
        if (collapseAnim.isEmpty() || collapseAnim.get().toStage() != StructureDamageState.DESTROYED) {
            throw new AssertionError("Transition to 0 HP must start the DESTROYED collapse scenario once");
        }
        long collapseSeqId = collapseAnim.get().sequenceId();

        // Hit 4 on already-destroyed structure must be ignored and never duplicate the scenario
        boolean appliedAfterDead = StructureDamageManager.applyDirectStructureDamage(
                null, tank, src1, 300.0f, "side_hull", DestructibleStructure.HitZone.HULL);
        if (appliedAfterDead) {
            throw new AssertionError("Already-destroyed structure must ignore further hits");
        }
        Optional<DestructionScenarioDirector.ScenarioEvent> dupAttempt =
                DestructionScenarioDirector.triggerScenario(
                        null, tank, StructureDamageState.CRITICAL, StructureDamageState.DESTROYED,
                        "side_hull", DestructibleStructure.HitZone.HULL, hitPos, 50L);
        if (dupAttempt.isPresent() || DestructionScenarioDirector.activeScenario(tank.structureId()).get().sequenceId() != collapseSeqId) {
            throw new AssertionError("Duplicate collapse scenario trigger must be rejected");
        }
    }
}
