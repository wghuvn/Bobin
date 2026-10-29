package com.bobincraft.destruction;

import com.bobincraft.warzone.WarzoneDimension;
import com.bobincraft.warzone.WarzoneTerrain;
import com.bobincraft.warzone.ZonePlan;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all destructible industrial structures in the zone (Section 3 & Section 5).
 * Connects fixed plan IDs (refinery tanks, substation transformers/equipment, bridge sections)
 * to their volumetric Box hit zones, spatial index, and thread-safe state snapshots for chunk generation.
 */
public final class DestructibleStructureRegistry {
    public static final Identifier WARZONE_DIM = WarzoneDimension.WORLD.getValue();

    private static final Map<String, DestructibleStructure> STRUCTURES = new ConcurrentHashMap<>();
    private static final StructureSpatialIndex SPATIAL_INDEX = new StructureSpatialIndex();
    /** Thread-safe snapshot of current structure stages for background chunk generation threads. */
    private static final ConcurrentHashMap<String, StructureDamageState> STAGE_SNAPSHOT = new ConcurrentHashMap<>();
    /** Thread-safe set of offline substation IDs for PowerGrid queries. */
    private static final Set<String> OFFLINE_SUBSTATIONS = ConcurrentHashMap.newKeySet();
    /** Thread-safe set of destroyed bridge section IDs for road/navigation queries. */
    private static final Set<String> DESTROYED_BRIDGE_SECTIONS = ConcurrentHashMap.newKeySet();
    private static volatile WarzoneTerrain lastTerrain;

    private static volatile boolean initializedForZone = false;
    private static volatile long initializedSeed = Long.MIN_VALUE;

    private DestructibleStructureRegistry() {}

    public static synchronized void ensureInitialized(WarzoneTerrain terrain) {
        if (terrain == null || !terrain.isAuthoredZone()) return;
        lastTerrain = terrain;
        if (initializedForZone && initializedSeed == terrain.seed && !STRUCTURES.isEmpty()) return;
        initializeFromPlan(terrain, terrain.zonePlan());
    }

    public static WarzoneTerrain lastTerrain() {
        return lastTerrain;
    }

    public static synchronized void initializeFromPlan(WarzoneTerrain terrain, ZonePlan plan) {
        if (terrain != null) lastTerrain = terrain;
        STRUCTURES.clear();
        SPATIAL_INDEX.clear();
        STAGE_SNAPSHOT.clear();
        OFFLINE_SUBSTATIONS.clear();
        DESTROYED_BRIDGE_SECTIONS.clear();
        DestructionScenarioDirector.resetAll();

        // 1. Refinery storage tanks: refinery_tank_01 .. refinery_tank_04
        for (ZonePlan.Site site : plan.sites()) {
            if (site.theme() == ZonePlan.Theme.REFINERY) {
                int[] uCoords = {22, 45};
                int[] vCoords = {-39, -15};
                int[] radii = {8, 6, 7, 5};
                int[] heights = {10, 8, 11, 9};
                int idx = 0;
                for (int u : uCoords) {
                    for (int v : vCoords) {
                        int r = radii[idx];
                        int h = heights[idx];
                        String id = String.format(Locale.ROOT, "refinery_tank_%02d", idx + 1);
                        BlockPos origin = new BlockPos(site.x() + u, site.y(), site.z() + v);
                        DestructibleStructure tank = new DestructibleStructure(
                                id,
                                DestructibleStructureType.get("refinery_storage_tank"),
                                WARZONE_DIM,
                                origin,
                                0,
                                DestructibleStructure.createTankRegions(origin, r, h),
                                r,
                                h,
                                idx
                        );
                        register(tank);
                        idx++;
                    }
                }
            } else if (site.theme() == ZonePlan.Theme.SUBSTATION) {
                // 2. Main substation transformers & switching equipment
                int[] tU = {-13, 13};
                for (int i = 0; i < tU.length; i++) {
                    String id = String.format(Locale.ROOT, "substation_transformer_%02d", i + 1);
                    BlockPos origin = new BlockPos(site.x() + tU[i], site.y(), site.z() + 5);
                    DestructibleStructure tr = new DestructibleStructure(
                            id,
                            DestructibleStructureType.get("substation_transformer"),
                            WARZONE_DIM,
                            origin,
                            0,
                            DestructibleStructure.createBoxRegions(origin, 5, 6, 0, 8),
                            5,
                            8,
                            i
                    );
                    register(tr);
                }
                // Technical switching section at the substation
                BlockPos eqOrigin = new BlockPos(site.x() + 12, site.y(), site.z() - 17);
                DestructibleStructure eq = new DestructibleStructure(
                        "substation_equipment_01",
                        DestructibleStructureType.get("substation_equipment"),
                        WARZONE_DIM,
                        eqOrigin,
                        0,
                        DestructibleStructure.createSwitchyardRegions(site.x(), site.y(), site.z()),
                        8,
                        9,
                        0
                );
                register(eq);
            }
        }

        // 3. Bridges divided into logical components with dedicated support (pier), deck, and parapet Box zones
        String[] bridgePrefixes = {"bridge_central", "bridge_north", "bridge_east"};
        List<ZonePlan.Crossing> crossings = plan.crossings();
        for (int bIdx = 0; bIdx < crossings.size(); bIdx++) {
            ZonePlan.Crossing crossing = crossings.get(bIdx);
            String prefix = bIdx < bridgePrefixes.length ? bridgePrefixes[bIdx] : "bridge_" + crossing.id();
            int totalMinU = -crossing.negative();
            int totalMaxU = crossing.positive();
            // Cover the full bridge between bank abutments across 3 logical span components
            int spanStart = totalMinU + 3;
            int spanEnd = totalMaxU - 3;
            int sectionCount = 3;
            double sectionLen = (spanEnd - spanStart) / (double) sectionCount;
            for (int s = 0; s < sectionCount; s++) {
                int u0 = (int) Math.round(spanStart + s * sectionLen);
                int u1 = (s == sectionCount - 1) ? spanEnd : (int) Math.round(spanStart + (s + 1) * sectionLen) - 1;
                int midU = (u0 + u1) / 2;
                int halfU = Math.max(4, (u1 - u0) / 2);
                int deckY = crossing.floor(midU);
                int wx = crossing.wx(midU, 0);
                int wz = crossing.wz(midU, 0);
                BlockPos origin = new BlockPos(wx, deckY, wz);
                int hw = crossing.alongX() ? halfU : 5;
                int hd = crossing.alongX() ? 5 : halfU;
                String id = String.format(Locale.ROOT, "%s_section_%02d", prefix, s + 1);
                DestructibleStructure section = new DestructibleStructure(
                        id,
                        DestructibleStructureType.get("bridge_section"),
                        WARZONE_DIM,
                        origin,
                        crossing.alongX() ? 1 : 0,
                        DestructibleStructure.createGeneratedBridgeRegions(terrain, crossing, u0, u1, s),
                        Math.max(hw, hd),
                        7,
                        crossing.id() * 10 + s,
                        crossing.id(),
                        u0,
                        u1
                );
                register(section);
            }
        }

        if (terrain != null && (!initializedForZone || initializedSeed != terrain.seed)) {
            StructureCollapseManager.prewarm(terrain);
        }
        if (terrain != null) {
            initializedSeed = terrain.seed;
            initializedForZone = true;
        }
    }

    public static synchronized void register(DestructibleStructure structure) {
        if (structure == null) return;
        STRUCTURES.put(structure.structureId(), structure);
        SPATIAL_INDEX.register(structure);
        STAGE_SNAPSHOT.put(structure.structureId(), structure.damageStage().templateStage());
        updateDerivedSets(structure);
    }

    public static void updateSnapshot(DestructibleStructure structure) {
        if (structure == null) return;
        STAGE_SNAPSHOT.put(structure.structureId(), structure.damageStage().templateStage());
        updateDerivedSets(structure);
    }

    private static void updateDerivedSets(DestructibleStructure structure) {
        String id = structure.structureId();
        boolean isDestroyed = structure.destroyed() || structure.damageStage().isTerminalOrDestroying();
        if (structure.structureType().category() == DestructibleStructureType.Category.SUBSTATION_TRANSFORMER
                || structure.structureType().category() == DestructibleStructureType.Category.SUBSTATION_EQUIPMENT) {
            if (isDestroyed) OFFLINE_SUBSTATIONS.add(id);
            else OFFLINE_SUBSTATIONS.remove(id);
        } else if (structure.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION) {
            if (isDestroyed) DESTROYED_BRIDGE_SECTIONS.add(id);
            else DESTROYED_BRIDGE_SECTIONS.remove(id);
        }
    }

    public static DestructibleStructure get(String structureId) {
        if (structureId == null) return null;
        DestructibleStructure direct = STRUCTURES.get(structureId);
        if (direct != null) return direct;
        String normalized = structureId.replace('/', '_');
        if (normalized.startsWith("bridge_west_")) {
            normalized = normalized.replace("bridge_west_", "bridge_central_");
        }
        return STRUCTURES.get(normalized);
    }

    public static List<DestructibleStructure> bridgeSectionsForCrossing(int crossingId) {
        List<DestructibleStructure> list = new ArrayList<>(3);
        for (DestructibleStructure s : STRUCTURES.values()) {
            if (s.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION
                    && s.crossingId() == crossingId) {
                list.add(s);
            }
        }
        list.sort(Comparator.comparingInt(DestructibleStructure::spanU0));
        return list;
    }

    public static Collection<DestructibleStructure> all() {
        return Collections.unmodifiableCollection(STRUCTURES.values());
    }

    public static List<DestructibleStructure> inChunk(int chunkX, int chunkZ) {
        return SPATIAL_INDEX.getInChunk(chunkX, chunkZ);
    }

    public static List<DestructibleStructure> inChunk(ChunkPos chunkPos) {
        return SPATIAL_INDEX.getInChunk(chunkPos);
    }

    public static StructureSpatialIndex spatialIndex() {
        return SPATIAL_INDEX;
    }

    public static StructureDamageState stageSnapshot(String structureId) {
        return STAGE_SNAPSHOT.getOrDefault(structureId, StructureDamageState.INTACT);
    }

    public static Map<String, StructureDamageState> allStageSnapshots() {
        return Collections.unmodifiableMap(STAGE_SNAPSHOT);
    }

    public static boolean isSubstationOffline() {
        return !OFFLINE_SUBSTATIONS.isEmpty();
    }

    public static Set<String> offlineSubstations() {
        return Collections.unmodifiableSet(OFFLINE_SUBSTATIONS);
    }

    public static Set<String> destroyedBridgeSections() {
        return Collections.unmodifiableSet(DESTROYED_BRIDGE_SECTIONS);
    }

    /**
     * Fast check for NPC and vehicle navigation whether a world coordinate lies on a destroyed bridge section.
     */
    public static boolean isBridgeSectionDestroyedAt(int x, int z) {
        if (DESTROYED_BRIDGE_SECTIONS.isEmpty()) return false;
        for (String id : DESTROYED_BRIDGE_SECTIONS) {
            DestructibleStructure s = STRUCTURES.get(id);
            if (s != null) {
                var box = s.overallBounds();
                if (x >= box.minX - 1 && x <= box.maxX + 1 && z >= box.minZ - 1 && z <= box.maxZ + 1) {
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized void resetAll(long worldTick) {
        for (DestructibleStructure s : STRUCTURES.values()) {
            s.reset(worldTick);
            updateSnapshot(s);
        }
        OFFLINE_SUBSTATIONS.clear();
        DESTROYED_BRIDGE_SECTIONS.clear();
        DestructionScenarioDirector.resetAll();
    }
}
