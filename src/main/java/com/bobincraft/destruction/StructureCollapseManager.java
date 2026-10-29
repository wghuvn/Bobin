package com.bobincraft.destruction;

import com.bobincraft.warzone.Blueprint;
import com.bobincraft.warzone.BpSpec;
import com.bobincraft.warzone.WarzoneTerrain;
import com.bobincraft.warzone.ZonePlan;
import com.bobincraft.warzone.ZoneWeathering;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates and caches immutable stage templates (INTACT, DAMAGED, CRITICAL, DESTROYED)
 * for refinery storage tanks, substation transformers/equipment, and bridge sections.
 * Computes exact block-state diffs between stages so only changed blocks are queued.
 */
public final class StructureCollapseManager {
    public record BlockChange(BlockPos pos, BpSpec spec, BpSpec prevSpec) {
        public BlockChange(BlockPos pos, BpSpec spec) {
            this(pos, spec, BpSpec.AIR);
        }
    }

    private record TemplateKey(String structureId, StructureDamageState stage, long seed, int variant, int revision) {}
    private record DiffKey(String structureId, StructureDamageState fromStage, StructureDamageState toStage, long seed, int variant) {}
    private static final ConcurrentHashMap<TemplateKey, Blueprint> TEMPLATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<DiffKey, List<BlockChange>> DIFF_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BpSpec, BlockState> STATE_CACHE = new ConcurrentHashMap<>();

    private static final Map<String,Blueprint> DEBRIS_SOURCE_BRIDGES=new ConcurrentHashMap<>();
    private StructureCollapseManager() {}

    public static void clearCache() {
        TEMPLATES.clear();
        DIFF_CACHE.clear();DEBRIS_SOURCE_BRIDGES.clear();
    }

    public static void prewarm(WarzoneTerrain terrain) {
        for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
            for (StructureDamageState st : StructureDamageState.values()) {
                getStageBlueprint(terrain, s, st);
            }
            computeDiff(terrain, s, StructureDamageState.INTACT, StructureDamageState.DAMAGED);
            computeDiff(terrain, s, StructureDamageState.DAMAGED, StructureDamageState.CRITICAL);
            computeDiff(terrain, s, StructureDamageState.CRITICAL, StructureDamageState.DESTROYED);
            computeDiff(terrain, s, StructureDamageState.INTACT, StructureDamageState.DESTROYED);
        }
    }

    public static Blueprint getStageBlueprint(WarzoneTerrain terrain, DestructibleStructure structure, StructureDamageState stage) {
        if (structure == null || stage == null) return null;
        StructureDamageState normStage = stage.templateStage();
        WarzoneTerrain effectiveTerrain = terrain != null ? terrain : DestructibleStructureRegistry.lastTerrain();
        return TEMPLATES.computeIfAbsent(new TemplateKey(structure.structureId(), normStage, effectiveTerrain==null?0:effectiveTerrain.seed, structure.destroyedVariantIndex(), StructureGeometrySnapshot.REVISION),
                k -> buildStageBlueprint(effectiveTerrain, structure, normStage));
    }

    /**
     * Computes only the block differences needed to transition a structure from fromStage to toStage.
     * Pre-sorted by ChunkPos and top-down Y so chunk rebuilds are coalesced and upper blocks clear before lower rubble.
     */
    public static List<BlockChange> computeDiff(WarzoneTerrain terrain,
                                                DestructibleStructure structure,
                                                StructureDamageState fromStage,
                                                StructureDamageState toStage) {
        if (structure == null || toStage == null) return List.of();
        fromStage = fromStage == null ? StructureDamageState.INTACT : fromStage.templateStage();
        toStage = toStage.templateStage();
        DiffKey key = new DiffKey(structure.structureId(), fromStage, toStage, terrain==null?0:terrain.seed, structure.destroyedVariantIndex());
        List<BlockChange> cached = DIFF_CACHE.get(key);
        if (cached != null) return cached;
        Blueprint fromBp = getStageBlueprint(terrain, structure, fromStage);
        Blueprint toBp = getStageBlueprint(terrain, structure, toStage);
        if (toBp == null) return List.of();
        List<BlockChange> diff = new ArrayList<>();
        int minX = fromBp==null?toBp.minX:Math.min(fromBp.minX,toBp.minX), maxX = fromBp==null?toBp.maxX():Math.max(fromBp.maxX(),toBp.maxX());
        int minY = fromBp==null?toBp.minY:Math.min(fromBp.minY,toBp.minY), maxY = fromBp==null?toBp.maxY():Math.max(fromBp.maxY(),toBp.maxY());
        int minZ = fromBp==null?toBp.minZ:Math.min(fromBp.minZ,toBp.minZ), maxZ = fromBp==null?toBp.maxZ():Math.max(fromBp.maxZ(),toBp.maxZ());
        for (int y = maxY; y >= minY; y--) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BpSpec nextSpec = toBp.get(x, y, z);
                    BpSpec prevSpec = fromBp != null ? fromBp.get(x, y, z) : null;
                    if (nextSpec == null && prevSpec == null) continue;
                    BpSpec effectiveNext = nextSpec == null ? BpSpec.AIR : nextSpec;
                    BpSpec effectivePrev = prevSpec == null ? BpSpec.AIR : prevSpec;
                    if (!effectiveNext.equals(effectivePrev)) {
                        diff.add(new BlockChange(new BlockPos(x, y, z), effectiveNext, effectivePrev));
                    }
                }
            }
        }
        diff.sort(Comparator
                .comparingLong((BlockChange c) -> ChunkPos.toLong(c.pos().getX() >> 4, c.pos().getZ() >> 4))
                .thenComparingInt(c -> -c.pos().getY()));
        List<BlockChange> immutable = Collections.unmodifiableList(diff);
        DIFF_CACHE.put(key, immutable);
        return immutable;
    }

    private static Blueprint buildStageBlueprint(WarzoneTerrain terrain, DestructibleStructure structure, StructureDamageState stage) {
        long seed = terrain != null ? terrain.seed : 19860426L;
        return switch (structure.structureType().category()) {
            case REFINERY_TANK -> StructureGeometrySnapshot.stage(terrain, structure, stage);
            case SUBSTATION_TRANSFORMER -> sourceStage(terrain,structure,stage,buildTransformerStage(seed, structure, stage));
            case SUBSTATION_EQUIPMENT -> sourceStage(terrain,structure,stage,null);
            case BRIDGE_SECTION -> sourceStage(terrain,structure,stage,buildBridgeSectionStage(terrain, structure, stage));
            default -> buildTransformerStage(seed, structure, stage);
        };
    }

    /** Project stage masks onto real generator cells. A stage can remove an owned cell, never invent rubble. */
    private static Blueprint sourceStage(WarzoneTerrain terrain,DestructibleStructure s,StructureDamageState stage,Blueprint mask) {
        Blueprint reference;
        if(s.structureType().category()==DestructibleStructureType.Category.BRIDGE_SECTION) {
            var crossing=findCrossing(terrain,s);
            if(crossing==null||terrain==null)return mask;
            reference=DEBRIS_SOURCE_BRIDGES.computeIfAbsent(terrain.seed+"/"+terrain.generationVersion()+"/"+crossing.id(),
                    key->com.bobincraft.warzone.ZoneInfrastructure.bridgeBlueprint(terrain,crossing));
        } else reference=com.bobincraft.warzone.StructureGeometryAccess.substation(terrain,s.originPosition().getX(),s.originPosition().getZ());
        if(reference==null)throw new IllegalStateException("Missing actual source blueprint: "+s.structureId());
        var bounds=s.overallBounds();
        int minX=Math.max(reference.minX,(int)Math.floor(bounds.minX)),minY=Math.max(reference.minY,(int)Math.floor(bounds.minY)),minZ=Math.max(reference.minZ,(int)Math.floor(bounds.minZ));
        int maxX=Math.min(reference.maxX(),(int)Math.ceil(bounds.maxX)),maxY=Math.min(reference.maxY(),(int)Math.ceil(bounds.maxY)),maxZ=Math.min(reference.maxZ(),(int)Math.ceil(bounds.maxZ));
        var out=new Blueprint(s.structureId()+"_"+stage.modelName,minX,minY,minZ,maxX-minX+1,maxY-minY+1,maxZ-minZ+1);
        var crossing = s.structureType().category()==DestructibleStructureType.Category.BRIDGE_SECTION ? findCrossing(terrain,s) : null;
        reference.forEachCell((x,y,z,spec,mode)->{
            if(mode!=Blueprint.Mode.SET||!out.inBounds(x,y,z))return;
            boolean included;
            if(s.structureType().category()==DestructibleStructureType.Category.BRIDGE_SECTION && crossing != null) {
                int u=crossing.alongX()?x-crossing.wx(0,0):z-crossing.wz(0,0);
                included=u>=s.spanU0()&&u<=s.spanU1();
            } else included=s.boundingRegions().stream().anyMatch(region->region.box().contains(new net.minecraft.util.math.Vec3d(x+.5,y+.5,z+.5)));
            if(!included)return;
            boolean remove=false;
            if(stage!=StructureDamageState.INTACT&&!spec.isAir()) {
                if(mask!=null){var target=mask.get(x,y,z);remove=target==null||target.isAir();}
                else {
                    int h=y-s.originPosition().getY();int level=stage.templateStage().index;
                    int top=level==1?9:level==2?5:1;
                    remove=h>top&&(level>1||x>=s.originPosition().getX());
                }
            }
            out.set(x,y,z,remove?BpSpec.AIR:spec,mode);
        });
        // For bridge sections in CRITICAL or DESTROYED stages, enforce logical support (BFS from intact piers & abutments)
        // so any unsupported bridge span, girder, railing or truss without pier support collapses cleanly!
        if (s.structureType().category() == DestructibleStructureType.Category.BRIDGE_SECTION
                && crossing != null
                && (stage == StructureDamageState.CRITICAL || stage == StructureDamageState.DESTROYED)) {
            int maxCantilever = (stage == StructureDamageState.DESTROYED) ? 2 : 5;
            Blueprint.Ground groundFn = (gx, gz) -> rawHeight(terrain, gx, gz);
            boolean[] vis = computeBridgeGrounded(out, crossing, s.spanU0(), s.spanU1(), maxCantilever, groundFn);
            out.forEachCell((x, y, z, spec, mode) -> {
                if (spec == null || spec.isAir()) return;
                int idx = ((y - out.minY) * out.sizeZ + (z - out.minZ)) * out.sizeX + (x - out.minX);
                if (idx >= 0 && idx < vis.length && !vis[idx]) {
                    out.air(x, y, z);
                }
            });
        }
        return out;
    }

    /**
     * Evaluates 3D structural support for a bridge section blueprint:
     * 1. Pier columns anchored to ground/riverbed transfer vertical load upward only if unbroken.
     * 2. Boundary edges (u == spanU0 or u == spanU1) can cantilever at most {@code maxCantilever} blocks
     *    into the section when the internal pier is destroyed.
     * 3. Any deck, girder, railing, or overhead truss block not connected to a supported deck column or pier is ungrounded.
     */
    private static boolean[] computeBridgeGrounded(Blueprint bp,
                                                   ZonePlan.Crossing crossing,
                                                   int u0,
                                                   int u1,
                                                   int maxCantilever,
                                                   Blueprint.Ground ground) {
        int n = bp.sizeX * bp.sizeY * bp.sizeZ;
        boolean[] vis = new boolean[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;

        // First: mark blocks directly touching the riverbed/ground or at the supported boundary cantilever lip
        for (int y = bp.minY; y <= bp.maxY(); y++) {
            for (int z = bp.minZ; z <= bp.maxZ(); z++) {
                for (int x = bp.minX; x <= bp.maxX(); x++) {
                    BpSpec s = bp.get(x, y, z);
                    if (s == null || s.isAir()) continue;
                    int u = crossing.alongX() ? x - crossing.wx(0, 0) : z - crossing.wz(0, 0);
                    int gy = ground.top(x, z);
                    boolean onGround = (y <= gy + 1);
                    boolean atBoundaryAnchor = (u <= u0 + 1 || u >= u1 - 1) && (y <= crossing.floor(u));
                    if (onGround || atBoundaryAnchor) {
                        int i = ((y - bp.minY) * bp.sizeZ + (z - bp.minZ)) * bp.sizeX + (x - bp.minX);
                        vis[i] = true;
                        queue[tail++] = i;
                    }
                }
            }
        }

        // Propagate vertical support up intact piers first so we know which u coordinates have a working pier
        int layer = bp.sizeX * bp.sizeZ;
        while (head < tail) {
            int i = queue[head++];
            int ix = i % bp.sizeX;
            int iz = (i / bp.sizeX) % bp.sizeZ;
            int iy = i / layer;
            int x = bp.minX + ix, y = bp.minY + iy, z = bp.minZ + iz;
            int u = crossing.alongX() ? x - crossing.wx(0, 0) : z - crossing.wz(0, 0);

            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int nonZero = (ox != 0 ? 1 : 0) + (oy != 0 ? 1 : 0) + (oz != 0 ? 1 : 0);
                        if (nonZero == 0 || nonZero == 3) continue;
                        int nx = x + ox, ny = y + oy, nz = z + oz;
                        if (!bp.inBounds(nx, ny, nz)) continue;
                        int nu = crossing.alongX() ? nx - crossing.wx(0, 0) : nz - crossing.wz(0, 0);
                        // Prevent horizontal cantilevering past maxCantilever from a broken pier or boundary
                        if (nu != u) {
                            int distLeft = Math.abs(nu - u0);
                            int distRight = Math.abs(u1 - nu);
                            int minDistToAnchor = Math.min(distLeft, distRight);
                            if (minDistToAnchor > maxCantilever) continue;
                        }
                        int j = ((ny - bp.minY) * bp.sizeZ + (nz - bp.minZ)) * bp.sizeX + (nx - bp.minX);
                        if (vis[j]) continue;
                        BpSpec ns = bp.get(nx, ny, nz);
                        if (ns == null || ns.isAir()) continue;
                        vis[j] = true;
                        queue[tail++] = j;
                    }
                }
            }
        }
        return vis;
    }

    /**
     * Cylindrical refinery storage tank with 4 distinct stages and 4 deterministic ruin variants (Task 2.3).
     */
    public static Blueprint buildTankStage(long seed, DestructibleStructure s, StructureDamageState stage) {
        WarzoneTerrain terrain=DestructibleStructureRegistry.lastTerrain();
        if(terrain==null || terrain.seed!=seed)throw new IllegalStateException("Tank reference terrain unavailable");
        return StructureGeometrySnapshot.stage(terrain,s,stage);
    }

    /**
     * Substation transformer unit with 4 damage stages and recognizable internal coil/radiator ruins (Task 2.3).
     */
    public static Blueprint buildTransformerStage(long seed, DestructibleStructure s, StructureDamageState stage) {
        BlockPos o = s.originPosition();
        int cx = o.getX(), cy = o.getY(), cz = o.getZ();
        int vIdx = s.destroyedVariantIndex() & 1;
        int breachSign = vIdx == 0 ? 1 : -1;
        Blueprint bp = new Blueprint(s.structureId() + "_" + stage.modelName,
                cx - 6, cy, cz - 7, 13, 10, 15);

        for (int a = -6; a <= 6; a++) {
            for (int b = -7; b <= 7; b++) {
                for (int dy = 0; dy <= 8; dy++) bp.air(cx + a, cy + dy, cz + b);
                bp.set(cx + a, cy, cz + b, BpSpec.of("bobin_craft:cracked_concrete"));
            }
        }

        for (int a = -3; a <= 3; a++) {
            for (int b = -4; b <= 4; b++) {
                bp.set(cx + a, cy + 1, cz + b, BpSpec.of("bobin_craft:cracked_concrete"));
                boolean isShell = Math.abs(a) == 3 || Math.abs(b) == 4;
                boolean isCoreCoil = a == 0 && (b == -2 || b == 0 || b == 2);

                if (stage == StructureDamageState.INTACT || stage == StructureDamageState.DAMAGED) {
                    for (int dy = 2; dy <= 5; dy++) {
                        String mat;
                        if (stage == StructureDamageState.DAMAGED && dy >= 4 && a * breachSign > 0 && (a + b + dy) % 2 == 0) {
                            mat = "bobin_craft:rusted_sheet_metal";
                        } else {
                            mat = dy == 5 ? "bobin_craft:rusted_sheet_metal"
                                    : ZoneWeathering.patch(seed, cx + a, cz + b, 3) > 0.72 ? "brown_terracotta" : "green_terracotta";
                        }
                        bp.set(cx + a, cy + dy, cz + b, BpSpec.of(mat));
                    }
                } else if (stage == StructureDamageState.CRITICAL) {
                    boolean breachedCorner = (a * breachSign >= 1) && (b >= 0);
                    int wallMax = breachedCorner ? 3 : 5;
                    for (int dy = 2; dy <= wallMax; dy++) {
                        String mat = isCoreCoil ? "waxed_oxidized_copper"
                                : (dy >= wallMax || breachedCorner ? "bobin_craft:rusted_sheet_metal" : "brown_terracotta");
                        bp.set(cx + a, cy + dy, cz + b, BpSpec.of(mat));
                    }
                    if (isCoreCoil) {
                        bp.set(cx + a, cy + 4, cz + b, BpSpec.of("waxed_oxidized_copper"));
                    }
                } else if (stage == StructureDamageState.DESTROYED) {
                    if (isCoreCoil) {
                        int coilTop = (b == 0) ? 4 : 3;
                        for (int dy = 2; dy <= coilTop; dy++) {
                            bp.set(cx + a, cy + dy, cz + b, BpSpec.of(dy == coilTop ? "waxed_oxidized_copper" : "brown_terracotta"));
                        }
                        bp.set(cx + a, cy + coilTop + 1, cz + b, BpSpec.of("iron_bars"));
                    } else if (isShell) {
                        int wallTop = (a * breachSign > 0)
                                ? (Math.abs(b) <= 1 ? 1 : 2)
                                : (b < 0 ? 4 : 3);
                        for (int dy = 2; dy <= wallTop; dy++) {
                            String mat = (dy == wallTop && (a + b) % 2 == 0)
                                    ? "bobin_craft:rusted_sheet_metal"
                                    : (dy == 2 ? "green_terracotta" : "brown_terracotta");
                            bp.set(cx + a, cy + dy, cz + b, BpSpec.of(mat));
                        }
                    } else {
                        bp.set(cx + a, cy + 2, cz + b,
                                (a + b) % 2 == 0 ? BpSpec.of("bobin_craft:rusted_sheet_metal") : BpSpec.of("cobblestone_slab"));
                    }
                }
            }
        }

        for (int side : new int[]{-4, 4}) {
            boolean blownSide = (side * breachSign > 0);
            for (int b = -4; b <= 4; b += 2) {
                bp.set(cx + side, cy + 1, cz + b, BpSpec.of("bobin_craft:cracked_concrete"));
                int finTop = switch (stage) {
                    case INTACT -> 4;
                    case DAMAGED -> (blownSide && b > 0) ? 3 : 4;
                    case CRITICAL -> blownSide ? 2 : 4;
                    default -> blownSide ? (b == -4 ? 2 : 1) : (b <= 0 ? 3 : 2);
                };
                for (int dy = 2; dy <= finTop; dy++) {
                    bp.set(cx + side, cy + dy, cz + b, BpSpec.of("iron_bars"));
                }
            }
        }

        if (stage != StructureDamageState.DESTROYED) {
            int topY = stage == StructureDamageState.CRITICAL ? 4 : 5;
            for (int a : new int[]{-2, 0, 2}) {
                if (stage == StructureDamageState.CRITICAL && a * breachSign > 0) continue;
                bp.set(cx + a, cy + topY + 1, cz, BpSpec.of("bobin_craft:insulator"));
                if (stage == StructureDamageState.INTACT || a * breachSign < 0) {
                    bp.set(cx + a, cy + topY + 2, cz, BpSpec.of("bobin_craft:insulator"));
                }
                bp.set(cx + a, cy + topY + 1, cz + 2, BpSpec.of("bobin_craft:insulator"));
                if (stage == StructureDamageState.INTACT) {
                    bp.set(cx + a, cy + topY + 2, cz + 2, BpSpec.of("bobin_craft:insulator"));
                }
            }
            bp.set(cx, cy + topY + 1, cz - 4, BpSpec.of("hopper"));
        } else {
            bp.set(cx - 2 * breachSign, cy + 5, cz - 2, BpSpec.of("bobin_craft:insulator"));
            bp.set(cx + 4 * breachSign, cy + 1, cz + 1, BpSpec.of("bobin_craft:rusted_sheet_metal"));
            bp.set(cx + 5 * breachSign, cy + 1, cz - 1, BpSpec.of("cobblestone_slab"));
            bp.set(cx, cy + 2, cz - 5, BpSpec.of("hopper"));
        }

        bp.set(cx - 4, cy + 1, cz - 5, BpSpec.of(stage == StructureDamageState.DESTROYED ? "bobin_craft:rusted_sheet_metal" : "bobin_craft:equipment_panel"));
        for (int a = -5; a <= 5; a++) {
            for (int b = -6; b <= 6; b++) {
                if (Math.abs(a) == 5 || Math.abs(b) == 6) {
                    if (stage != StructureDamageState.DESTROYED || (a * breachSign <= 0 || Math.abs(b) > 2)) {
                        bp.set(cx + a, cy + 1, cz + b, BpSpec.of("cobblestone_slab"));
                    }
                }
            }
        }

        bp.connect((x, z) -> cy);
        return bp;
    }

    /**
     * Substation high-voltage switching equipment bay with recognizable portal & busbar ruins (Task 2.3).
     */
    public static Blueprint buildSubstationEquipmentStage(long seed, DestructibleStructure s, StructureDamageState stage) {
        BlockPos o = s.originPosition();
        int cx = o.getX(), cy = o.getY(), cz = o.getZ();
        Blueprint bp = new Blueprint(s.structureId() + "_" + stage.modelName,
                cx - 8, cy, cz - 8, 17, 11, 17);

        for (int a = -8; a <= 8; a++) {
            for (int b = -8; b <= 8; b++) {
                for (int dy = 0; dy <= 10; dy++) bp.air(cx + a, cy + dy, cz + b);
                bp.set(cx + a, cy, cz + b, BpSpec.of("bobin_craft:cracked_concrete"));
            }
        }

        int[] phaseOffsets = {-6, 0, 6};
        int[] vOffsets = {-6, 0, 6};
        for (int pa : phaseOffsets) {
            for (int vb : vOffsets) {
                bp.set(cx + pa, cy + 1, cz + vb, BpSpec.of("bobin_craft:cracked_concrete"));
                if (stage == StructureDamageState.DESTROYED) {
                    if (pa == -6) {
                        bp.set(cx + pa, cy + 2, cz + vb, BpSpec.of("gray_terracotta"));
                        int top = vb <= 0 ? 4 : 3;
                        for (int dy = 3; dy <= top; dy++) {
                            bp.set(cx + pa, cy + dy, cz + vb, BpSpec.of("bobin_craft:insulator"));
                        }
                    } else if (pa == 0) {
                        bp.set(cx + pa, cy + 2, cz + vb, BpSpec.of("bobin_craft:rusted_sheet_metal"));
                        if (vb != 0) {
                            bp.set(cx + pa, cy + 3, cz + vb, BpSpec.of("iron_bars"));
                        }
                    } else {
                        bp.set(cx + pa, cy + 2, cz + vb, BpSpec.of("cobblestone_slab"));
                        if (vb == 0) {
                            bp.set(cx + pa - 1, cy + 1, cz + vb, BpSpec.of("bobin_craft:rusted_sheet_metal"));
                        }
                    }
                    continue;
                }

                bp.set(cx + pa, cy + 2, cz + vb, BpSpec.of(stage == StructureDamageState.INTACT ? "gray_terracotta" : "bobin_craft:rusted_sheet_metal"));
                int insTop = stage == StructureDamageState.INTACT ? 5
                        : (stage == StructureDamageState.DAMAGED ? (pa == 6 && vb == 0 ? 4 : 5) : (pa >= 0 && vb >= 0 ? 3 : 4));
                for (int dy = 3; dy <= insTop; dy++) {
                    bp.set(cx + pa, cy + dy, cz + vb, BpSpec.of("bobin_craft:insulator"));
                }
                if (vb == 6) {
                    bp.set(cx + pa - 1, cy + 1, cz + vb, BpSpec.of("bobin_craft:equipment_panel"));
                }
            }

            if (stage == StructureDamageState.INTACT || stage == StructureDamageState.DAMAGED) {
                for (int vb = -6; vb <= 6; vb++) {
                    if (stage == StructureDamageState.DAMAGED && pa == 6 && vb > 2) continue;
                    bp.set(cx + pa, cy + 6, cz + vb, BpSpec.of("bobin_craft:busbar"));
                }
            } else if (stage == StructureDamageState.CRITICAL) {
                for (int vb = -6; vb <= 0; vb++) {
                    if (pa <= 0) bp.set(cx + pa, cy + 5, cz + vb, BpSpec.of("bobin_craft:busbar"));
                }
            } else if (stage == StructureDamageState.DESTROYED) {
                for (int vb = -6; vb <= -2; vb++) {
                    bp.set(cx - 6, cy + 5, cz + vb, BpSpec.of("bobin_craft:busbar"));
                }
                for (int vb = -1; vb <= 3; vb++) {
                    bp.set(cx + 1, cy + 1, cz + vb, BpSpec.of("bobin_craft:busbar"));
                }
            }
        }
        bp.connect((x, z) -> cy);
        return bp;
    }

    /**
     * Support-aware multi-component engineering bridge section stage template (Task 3):
     * - When a bridge section collapses (`DESTROYED`), its unsupported span over the destroyed pier fails across
     *   `spanHalf - 2` so no impossible long cantilevers hang in mid-air past a shattered support.
     * - Remaining ends step/slope down logically toward the abutment or adjacent supported section, with zero
     *   floating railings or unsupported overhead truss beams.
     * - Physical falling fragments (`DebrisFragment`) created from the detached source blocks settle onto the riverbed/ground below!
     */
    public static Blueprint buildBridgeSectionStage(WarzoneTerrain terrain, DestructibleStructure s, StructureDamageState stage) {
        ZonePlan.Crossing crossing = findCrossing(terrain, s);
        if (crossing == null) {
            return buildFallbackBridgeSectionStage(s, stage);
        }

        int u0 = s.spanU0();
        int u1 = s.spanU1();
        int x0 = Math.min(crossing.wx(u0, -6), crossing.wx(u1, 6));
        int z0 = Math.min(crossing.wz(u0, -6), crossing.wz(u1, 6));
        int x1 = Math.max(crossing.wx(u0, -6), crossing.wx(u1, 6));
        int z1 = Math.max(crossing.wz(u0, -6), crossing.wz(u1, 6));
        int minY = 40;
        int maxY = crossing.deckY() + 18;

        Blueprint bp = new Blueprint(s.structureId() + "_" + stage.modelName,
                x0, minY, z0, x1 - x0 + 1, maxY - minY + 1, z1 - z0 + 1);

        int style = crossing.style();
        String axis = crossing.alongX() ? "x" : "z";
        String posStepFacing = crossing.alongX() ? "east" : "south";
        String negStepFacing = crossing.alongX() ? "west" : "north";

        int dmgVariant = Math.floorMod(s.structureId().hashCode() + s.destroyedVariantIndex() * 13, 4);
        int midU = (u0 + u1) / 2;
        int spanHalf = Math.max(4, (u1 - u0) / 2);
        // Support-aware span failure: collapse the entire unsupported span except the 1-2 block stepped transition lip
        // anchored at the adjacent section/abutment boundary, eliminating impossible mid-air cantilevers.
        int baseGapHalf = Math.max(4, spanHalf - 2);
        int shoulderSide = (dmgVariant % 2 == 0) ? 1 : -1;

        for (int u = u0; u <= u1; u++) {
            int y = crossing.floor(u);
            int relU = u - midU;

            boolean hasPierHere = Math.floorMod(u, 16) == 0 && u > -crossing.negative() + 12 && u < crossing.positive() - 12;

            for (int v = -4; v <= 4; v++) {
                int wx = crossing.wx(u, v);
                int wz = crossing.wz(u, v);
                int groundY = rawHeight(terrain, wx, wz);

                for (int h = Math.max(groundY + 1, y - 4); h <= Math.min(maxY, Math.max(y + 10, 94)); h++) {
                    bp.air(wx, h, wz);
                }

                int leftBreakU = Math.max(-spanHalf + 1, -baseGapHalf + fractureOffsetLeft(v, dmgVariant));
                int rightBreakU = Math.min(spanHalf - 1, baseGapHalf + fractureOffsetRight(v, dmgVariant));

                if (stage == StructureDamageState.INTACT) {
                    placeIntactBridgeColumn(bp, crossing, u, v, wx, wz, y, groundY, style, axis, hasPierHere);
                } else if (stage == StructureDamageState.DAMAGED) {
                    placeDamagedBridgeColumn(bp, crossing, u, v, relU, wx, wz, y, groundY, style, axis, hasPierHere, dmgVariant, shoulderSide);
                } else if (stage == StructureDamageState.CRITICAL) {
                    placeCriticalBridgeColumn(bp, crossing, u, v, relU, wx, wz, y, groundY, style, axis, hasPierHere, dmgVariant, shoulderSide);
                } else if (stage == StructureDamageState.DESTROYED) {
                    placeDestroyedBridgeColumn(bp, crossing, u, v, relU, leftBreakU, rightBreakU,
                            wx, wz, y, groundY, style, axis, posStepFacing, negStepFacing, hasPierHere, dmgVariant);
                }
            }
        }

        bp.connect((x, z) -> rawHeight(terrain, x, z));
        if(stage.templateStage()!=StructureDamageState.INTACT) {
            Blueprint reference=DEBRIS_SOURCE_BRIDGES.computeIfAbsent(terrain.seed+"/"+terrain.generationVersion()+"/"+crossing.id(),
                    key->com.bobincraft.warzone.ZoneInfrastructure.bridgeBlueprint(terrain,crossing));
            // Preserve surviving authored blocks; source-less decorative riverbed piles cannot duplicate physical fragments.
            bp.forEachCell((x,y,z,spec,mode)->{var original=reference.get(x,y,z);
                if(!spec.isAir()&&(original==null||original.isAir()))bp.clear(x,y,z);
            });
        }
        return bp;
    }

    private static void placeIntactBridgeColumn(Blueprint bp, ZonePlan.Crossing b, int u, int v,
                                                int wx, int wz, int y, int groundY,
                                                int style, String axis, boolean hasPierHere) {
        if (hasPierHere) {
            if (Math.abs(v) == 3) {
                for (int h = groundY; h < y - 1; h++) {
                    bp.set(wx, h, wz, BpSpec.of(style == 2 ? "dark_oak_log" : "polished_andesite"));
                }
            }
            bp.set(wx, y - 2, wz, BpSpec.of("bobin_craft:mossy_concrete"));
        }

        bp.set(wx, y - 1, wz, style == 2 ? BpSpec.of("dark_oak_log").with("axis", axis) : BpSpec.of("bobin_craft:cracked_concrete"));

        String deck = Math.abs(v) >= 3 ? "bobin_craft:cracked_concrete"
                : style == 2 ? "bobin_craft:rusted_sheet_metal" : "bobin_craft:cracked_asphalt";
        bp.set(wx, y, wz, BpSpec.of(deck));

        if (Math.abs(v) == 4) {
            bp.set(wx, y + 1, wz, BpSpec.of(style == 0 ? "bobin_craft:cracked_concrete" : "iron_bars"));
            if (style == 1 && Math.floorMod(u, 8) == 0) {
                for (int h = 2; h <= 5; h++) {
                    bp.set(wx, y + h, wz, BpSpec.of("waxed_oxidized_copper"));
                }
            }
            if (style == 1 && u >= -b.negative() + 16 && u <= b.positive() - 16) {
                bp.set(wx, y + 5, wz, BpSpec.of("waxed_oxidized_copper"));
                int diagonal = 1 + Math.floorMod(u, 8) / 2;
                bp.set(wx, y + diagonal, wz, BpSpec.of("iron_bars"));
            }
        }
    }

    private static void placeDamagedBridgeColumn(Blueprint bp, ZonePlan.Crossing b, int u, int v, int relU,
                                                 int wx, int wz, int y, int groundY,
                                                 int style, String axis, boolean hasPierHere,
                                                 int dmgVariant, int shoulderSide) {
        if (hasPierHere) {
            if (Math.abs(v) == 3) {
                for (int h = groundY; h < y - 1; h++) {
                    bp.set(wx, h, wz, BpSpec.of(style == 2 ? "dark_oak_log" : "polished_andesite"));
                }
            }
            String capMat = (v == 4 * shoulderSide) ? "cobblestone_slab" : "bobin_craft:mossy_concrete";
            bp.set(wx, y - 2, wz, BpSpec.of(capMat));
        }

        BpSpec girder = style == 2 ? BpSpec.of("dark_oak_log").with("axis", axis)
                : ((relU + v + dmgVariant) % 5 == 0 ? BpSpec.of("bobin_craft:mossy_concrete") : BpSpec.of("bobin_craft:cracked_concrete"));
        bp.set(wx, y - 1, wz, girder);

        boolean chippedOuterEdge = Math.abs(v) == 4 && v * shoulderSide > 0 && Math.abs(relU - (dmgVariant - 1)) <= 2;
        boolean deckSolid = false;
        if (chippedOuterEdge) {
            bp.set(wx, y, wz, BpSpec.of("cobblestone_slab").with("type", "bottom"));
        } else {
            deckSolid = true;
            String deck;
            if (Math.abs(relU) <= 2 && v * shoulderSide >= 1) {
                deck = (relU + v) % 2 == 0 ? "bobin_craft:weathered_gravel" : "bobin_craft:cracked_concrete";
            } else if (Math.abs(v) >= 3) {
                deck = "bobin_craft:cracked_concrete";
            } else if ((u * 3 + v * 5 + dmgVariant) % 7 == 0) {
                deck = "bobin_craft:cracked_concrete";
            } else {
                deck = style == 2 ? "bobin_craft:rusted_sheet_metal" : "bobin_craft:cracked_asphalt";
            }
            bp.set(wx, y, wz, BpSpec.of(deck));
        }

        if (Math.abs(v) == 4 && deckSolid) {
            boolean railBlown = (v * shoulderSide > 0 && Math.abs(relU) <= 3)
                    || (v * shoulderSide < 0 && Math.abs(relU + 2) <= 1);
            if (!railBlown) {
                bp.set(wx, y + 1, wz, BpSpec.of(style == 0 ? "bobin_craft:cracked_concrete" : "iron_bars"));
            } else if (style == 0 && Math.abs(relU) == 3) {
                bp.set(wx, y + 1, wz, BpSpec.of("cobblestone_slab").with("type", "bottom"));
            }
            if (style == 1 && Math.floorMod(u, 8) == 0) {
                for (int h = 2; h <= 5; h++) {
                    bp.set(wx, y + h, wz, BpSpec.of("waxed_oxidized_copper"));
                }
            }
            if (style == 1 && u >= -b.negative() + 16 && u <= b.positive() - 16) {
                bp.set(wx, y + 5, wz, BpSpec.of("waxed_oxidized_copper"));
                int diagonal = 1 + Math.floorMod(u, 8) / 2;
                if (!railBlown && (diagonal == 1 || diagonal == 4)) {
                    bp.set(wx, y + diagonal, wz, BpSpec.of("iron_bars"));
                }
            }
        }
    }

    private static void placeCriticalBridgeColumn(Blueprint bp, ZonePlan.Crossing b, int u, int v, int relU,
                                                  int wx, int wz, int y, int groundY,
                                                  int style, String axis, boolean hasPierHere,
                                                  int dmgVariant, int shoulderSide) {
        if (hasPierHere) {
            if (Math.abs(v) == 3) {
                int pierTop = (v == 3 * shoulderSide) ? y - 3 : y - 2;
                for (int h = groundY; h <= pierTop; h++) {
                    bp.set(wx, h, wz, BpSpec.of(style == 2 ? "dark_oak_log" : "polished_andesite"));
                }
                if (v == 3 * shoulderSide) {
                    bp.set(wx, y - 2, wz, BpSpec.of("iron_bars"));
                }
            }
            if (v * shoulderSide <= 2) {
                bp.set(wx, y - 2, wz, BpSpec.of("bobin_craft:mossy_concrete"));
            }
        }

        boolean shoulderCollapsed = (v * shoulderSide >= 3 && Math.abs(relU) <= 3)
                || (v * shoulderSide == 2 && Math.abs(relU) <= 1);

        if (shoulderCollapsed && Math.abs(v) == 4) {
            placeRiverbedDebris(bp, wx, groundY, wz, style, u + v + dmgVariant, false);
            return;
        }

        BpSpec girder = (shoulderCollapsed && Math.abs(relU) <= 1)
                ? BpSpec.of("bobin_craft:mossy_concrete")
                : (style == 2 ? BpSpec.of("dark_oak_log").with("axis", axis)
                : ((u + v) % 2 == 0 ? BpSpec.of("bobin_craft:mossy_concrete") : BpSpec.of("bobin_craft:cracked_concrete")));
        bp.set(wx, y - 1, wz, girder);

        boolean deckSolid = false;
        if (shoulderCollapsed) {
            if (Math.abs(relU) == 3) {
                bp.set(wx, y, wz, BpSpec.of("cobblestone_slab").with("type", "bottom"));
            }
            placeRiverbedDebris(bp, wx, groundY, wz, style, u + v + dmgVariant, false);
        } else if (Math.abs(relU) <= 2 && (relU + v + dmgVariant) % 3 == 0 && Math.abs(v) <= 2) {
            bp.set(wx, y, wz, BpSpec.of(style == 2 ? "cobblestone_slab" : "bobin_craft:weathered_asphalt_slab").with("type", "bottom"));
        } else {
            deckSolid = true;
            String deck = (Math.abs(relU) <= 3 || Math.abs(v) >= 3)
                    ? ((u + v) % 3 == 0 ? "bobin_craft:weathered_gravel" : "bobin_craft:cracked_concrete")
                    : (style == 2 ? "bobin_craft:rusted_sheet_metal" : "bobin_craft:cracked_asphalt");
            bp.set(wx, y, wz, BpSpec.of(deck));
        }

        if (Math.abs(v) == 4 && deckSolid && Math.abs(relU) > (v * shoulderSide > 0 ? 4 : 2)) {
            bp.set(wx, y + 1, wz, BpSpec.of(style == 0 ? "bobin_craft:cracked_concrete" : "iron_bars"));
            if (style == 1 && Math.floorMod(u, 8) == 0) {
                for (int h = 2; h <= 4; h++) {
                    bp.set(wx, y + h, wz, BpSpec.of("waxed_oxidized_copper"));
                }
            }
        }
    }

    private static void placeDestroyedBridgeColumn(Blueprint bp, ZonePlan.Crossing b, int u, int v, int relU,
                                                   int leftBreakU, int rightBreakU,
                                                   int wx, int wz, int y, int groundY,
                                                   int style, String axis,
                                                   String posStepFacing, String negStepFacing,
                                                   boolean hasPierHere, int dmgVariant) {
        boolean inGap = relU >= leftBreakU && relU <= rightBreakU;

        // 1. Piers: any pier inside the collapsed section is shattered down to a jagged stump with exposed rebar
        if (hasPierHere) {
            if (Math.abs(v) == 3) {
                int brokenPierTop = y - (v < 0 ? 5 + (dmgVariant & 1) : 4 + ((dmgVariant + 1) & 1));
                for (int h = groundY; h <= brokenPierTop; h++) {
                    bp.set(wx, h, wz, BpSpec.of(style == 2 ? "dark_oak_log" : "polished_andesite"));
                }
                bp.set(wx, brokenPierTop + 1, wz, BpSpec.of("iron_bars"));
                if (brokenPierTop + 2 < y - 1 && (v + dmgVariant) % 2 == 0) {
                    bp.set(wx, brokenPierTop + 2, wz, BpSpec.of("iron_bars"));
                }
            } else if (Math.abs(v) <= 2) {
                bp.set(wx, groundY + 1, wz, BpSpec.of("bobin_craft:mossy_concrete"));
                if ((u + v + dmgVariant) % 2 == 0) {
                    bp.set(wx, groundY + 2, wz, BpSpec.of("cobblestone_slab").with("type", "bottom"));
                }
            }
        }

        // 2. Inside the severed physical gap: NO floating deck, NO hanging cantilevers, only a 1-block slumped shear lip
        //    at the immediate abutment/boundary edge and rich collapsed span wreckage on the riverbed below!
        if (inGap) {
            int distFromLeft = relU - leftBreakU;
            int distFromRight = rightBreakU - relU;

            if (distFromLeft == 0 && Math.abs(v) <= 3) {
                bp.set(wx, y - 2, wz, BpSpec.of("bobin_craft:mossy_concrete"));
                if ((v + dmgVariant) % 2 == 0) {
                    bp.set(wx, y - 1, wz, BpSpec.of("stone_brick_stairs").with("facing", negStepFacing).with("half", "bottom"));
                } else {
                    bp.set(wx, y - 1, wz, BpSpec.of("cobblestone_slab").with("type", "bottom"));
                }
            } else if (distFromRight == 0 && (Math.abs(v) == 3 || v == 0)) {
                bp.set(wx, y - 2, wz, BpSpec.of("bobin_craft:mossy_concrete"));
                bp.set(wx, y - 1, wz, BpSpec.of("iron_bars"));
            }

            // Place fallen bridge span slabs, girders, and rebar in the riverbed below the collapsed span
            placeRiverbedDebris(bp, wx, groundY, wz, style, u * 7 + v * 13 + dmgVariant, true);
            return;
        }

        // 3. Surviving boundary edges outside the gap: stepped heights anchored directly to the adjacent supported span/bank
        int distToLeftEdge = leftBreakU - relU;   // > 0 on the negative-u boundary side
        int distToRightEdge = relU - rightBreakU; // > 0 on the positive-u boundary side

        if (distToLeftEdge > 0 && distToLeftEdge <= 2) {
            int slumpedY = (distToLeftEdge == 1) ? y - 1 : y;
            bp.set(wx, slumpedY - 1, wz, BpSpec.of("bobin_craft:mossy_concrete"));
            if (distToLeftEdge == 1) {
                String edgeMat = Math.abs(v) == 4 ? "cobblestone_slab"
                        : ((v + dmgVariant) % 2 == 0 ? "bobin_craft:cracked_concrete" : "bobin_craft:weathered_gravel");
                BpSpec spec = "cobblestone_slab".equals(edgeMat)
                        ? BpSpec.of("cobblestone_slab").with("type", "bottom")
                        : BpSpec.of(edgeMat);
                bp.set(wx, slumpedY, wz, spec);
            } else {
                bp.set(wx, slumpedY, wz, BpSpec.of("bobin_craft:cracked_concrete"));
                if (Math.abs(v) == 4 && (v + dmgVariant) % 2 == 0) {
                    bp.set(wx, slumpedY + 1, wz, BpSpec.of("cobblestone_slab").with("type", "bottom"));
                }
            }
            return;
        }

        if (distToRightEdge > 0 && distToRightEdge <= 2) {
            int slumpedY = (distToRightEdge == 1 && (dmgVariant & 1) == 1) ? y - 1 : y;
            bp.set(wx, slumpedY - 1, wz, style == 2 ? BpSpec.of("dark_oak_log").with("axis", axis) : BpSpec.of("bobin_craft:cracked_concrete"));
            if (distToRightEdge == 1 && Math.abs(v) == 4) {
                bp.set(wx, slumpedY, wz, BpSpec.of("cobblestone_slab").with("type", "bottom"));
            } else if (distToRightEdge == 1 && (v + dmgVariant) % 3 == 0) {
                bp.set(wx, slumpedY, wz, BpSpec.of("stone_brick_stairs").with("facing", posStepFacing).with("half", "bottom"));
            } else {
                bp.set(wx, slumpedY, wz, BpSpec.of("bobin_craft:cracked_concrete"));
                if (distToRightEdge == 2 && Math.abs(v) == 4) {
                    bp.set(wx, slumpedY + 1, wz, BpSpec.of("iron_bars"));
                }
            }
            return;
        }

        // 4. Outer boundary transition (> 2 blocks from fracture)
        bp.set(wx, y - 1, wz, style == 2 ? BpSpec.of("dark_oak_log").with("axis", axis) : BpSpec.of("bobin_craft:cracked_concrete"));
        String deck = Math.abs(v) >= 3 ? "bobin_craft:cracked_concrete"
                : style == 2 ? "bobin_craft:rusted_sheet_metal" : "bobin_craft:cracked_asphalt";
        bp.set(wx, y, wz, BpSpec.of(deck));

        if (Math.abs(v) == 4) {
            bp.set(wx, y + 1, wz, BpSpec.of(style == 0 ? "bobin_craft:cracked_concrete" : "iron_bars"));
            if (style == 1 && Math.floorMod(u, 8) == 0) {
                for (int h = 2; h <= 4; h++) {
                    bp.set(wx, y + h, wz, BpSpec.of("waxed_oxidized_copper"));
                }
            }
        }
    }

    private static int fractureOffsetLeft(int v, int dmgVariant) {
        return switch (dmgVariant & 3) {
            case 0 -> (v + 4) / 3 - 1;          // Diagonal shear across lanes
            case 1 -> Math.abs(v) >= 3 ? 2 : 0; // V-notch: outer girders survive further than center lanes
            case 2 -> (4 - v) / 3 - 1;          // Reverse diagonal shear
            default -> (v % 2 == 0 ? 1 : 0) + (v > 0 ? 1 : 0); // Stepped jagged fracture
        };
    }

    private static int fractureOffsetRight(int v, int dmgVariant) {
        return switch (dmgVariant & 3) {
            case 0 -> (v + 4) / 3 - 1;
            case 1 -> Math.abs(v) >= 3 ? -2 : 0;
            case 2 -> (4 - v) / 3 - 1;
            default -> (v < 0 ? 1 : -1) + (Math.abs(v) == 2 ? 1 : 0);
        };
    }

    private static void placeRiverbedDebris(Blueprint bp, int wx, int groundY, int wz, int style, int hashSeed, boolean fullCollapse) {
        int h = Math.floorMod(hashSeed * 31 + wx * 7 + wz * 13, fullCollapse ? 4 : 8);
        if (h == 0) {
            bp.set(wx, groundY, wz, BpSpec.of("bobin_craft:mossy_concrete"));
            bp.set(wx, groundY + 1, wz, BpSpec.of("bobin_craft:cracked_concrete"));
            if (fullCollapse && (wx + wz) % 2 == 0) {
                bp.set(wx, groundY + 2, wz, BpSpec.of("iron_bars"));
            }
        } else if (h == 1) {
            bp.set(wx, groundY, wz, BpSpec.of("bobin_craft:mossy_concrete"));
            String mat = style == 1 ? "waxed_oxidized_copper"
                    : (style == 2 ? "bobin_craft:rusted_sheet_metal" : "bobin_craft:cracked_concrete");
            bp.set(wx, groundY + 1, wz, BpSpec.of(mat));
        } else if (h == 2 && fullCollapse) {
            bp.set(wx, groundY, wz, BpSpec.of("bobin_craft:mossy_concrete"));
            bp.set(wx, groundY + 1, wz, BpSpec.of("cobblestone_slab").with("type", "bottom"));
        }
    }

    public static Map<Long, BpSpec> computeBridgeImpactBreach(WarzoneTerrain terrain,
                                                              DestructibleStructure s,
                                                              Blueprint currentBp,
                                                              double impactX,
                                                              double impactY,
                                                              double impactZ,
                                                              boolean supportHit,
                                                              boolean pierDestroyed,
                                                              float effectiveDamage) {
        if (s == null) return Map.of();
        Blueprint intactBp = getStageBlueprint(terrain, s, StructureDamageState.INTACT);
        if (intactBp == null) intactBp = currentBp;
        if (intactBp == null) return Map.of();
        ZonePlan.Crossing crossing = findCrossing(terrain, s);
        boolean alongX = crossing != null ? crossing.alongX() : (s.orientation() & 1) == 1;
        int centerX = crossing != null ? crossing.wx(0, 0) : s.originPosition().getX();
        int centerZ = crossing != null ? crossing.wz(0, 0) : s.originPosition().getZ();
        int minU = s.spanU0();
        int maxU = s.spanU1();
        Blueprint.Ground groundFn = (gx, gz) -> rawHeight(terrain, gx, gz);
        return StructuralSupportSolver.computeBridgeBreach(
                intactBp, s.localBreachCells(), alongX, minU, maxU, centerX, centerZ,
                impactX, impactY, impactZ, supportHit, pierDestroyed, effectiveDamage, groundFn);
    }

    static ZonePlan.Crossing findCrossing(WarzoneTerrain terrain, DestructibleStructure s) {
        if (terrain == null || !terrain.isAuthoredZone()) return null;
        List<ZonePlan.Crossing> crossings = terrain.zonePlan().crossings();
        int cid = s.crossingId();
        for (ZonePlan.Crossing c : crossings) {
            if (c.id() == cid) return c;
        }
        int fallbackIdx = (s.variantIndex() / 10);
        if (fallbackIdx >= 0 && fallbackIdx < crossings.size()) {
            return crossings.get(fallbackIdx);
        }
        return crossings.isEmpty() ? null : crossings.get(0);
    }

    static int rawHeight(WarzoneTerrain t, int x, int z) {
        if (t == null) return 58;
        var s = new WarzoneTerrain.Sample();
        t.naturalLandscape().sample(x, z, s);
        return s.height;
    }

    private static Blueprint buildFallbackBridgeSectionStage(DestructibleStructure s, StructureDamageState stage) {
        BlockPos o = s.originPosition();
        int cx = o.getX(), deckY = o.getY(), cz = o.getZ();
        boolean alongX = (s.orientation() & 1) == 1;
        int halfSpan = s.radius();
        int hw = alongX ? halfSpan : 5;
        int hd = alongX ? 5 : halfSpan;
        Blueprint bp = new Blueprint(s.structureId() + "_" + stage.modelName,
                cx - hw, deckY - 6, cz - hd, hw * 2 + 1, 14, hd * 2 + 1);
        for (int u = -halfSpan; u <= halfSpan; u++) {
            for (int v = -4; v <= 4; v++) {
                int wx = cx + (alongX ? u : v);
                int wz = cz + (alongX ? v : u);
                for (int dy = -5; dy <= 7; dy++) bp.air(wx, deckY + dy, wz);
                if (stage == StructureDamageState.DESTROYED && Math.abs(u) <= Math.max(4, halfSpan - 1)) continue;
                bp.set(wx, deckY - 1, wz, BpSpec.of("bobin_craft:cracked_concrete"));
                bp.set(wx, deckY, wz, BpSpec.of("bobin_craft:cracked_asphalt"));
                if (Math.abs(v) == 4 && stage == StructureDamageState.INTACT) {
                    bp.set(wx, deckY + 1, wz, BpSpec.of("iron_bars"));
                }
            }
        }
        return bp;
    }

    public static BlockState resolveBlockState(BpSpec spec) {
        if (spec == null || spec.isAir()) return Blocks.AIR.getDefaultState();
        BlockState cached = STATE_CACHE.get(spec);
        if (cached != null) return cached;
        Block block = Registries.BLOCK.get(new Identifier(spec.id.contains(":") ? spec.id : "minecraft:" + spec.id));
        if (block == Blocks.AIR) return Blocks.AIR.getDefaultState();
        BlockState st = block.getDefaultState();
        for (int i = 0; i < spec.propCount(); i++) {
            Property<?> prop = block.getStateManager().getProperty(spec.key(i));
            if (prop != null) {
                st = withProperty(st, prop, spec.val(i));
            }
        }
        STATE_CACHE.put(spec, st);
        return st;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState st, Property<T> prop, String value) {
        Optional<T> parsed = prop.parse(value);
        return parsed.map(t -> st.with(prop, t)).orElse(st);
    }
}
