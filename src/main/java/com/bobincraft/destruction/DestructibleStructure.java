package com.bobincraft.destruction;

import com.bobincraft.warzone.BpSpec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Layer A (Server Damage Logic) representation and mutable runtime state of a single destructible
 * industrial structure or bridge component (Section 4 Level A & Section 5).
 *
 * <p>Uses volumetric {@link Box} hit zones mapped to named logical components
 * (e.g. {@code Support_01} / {@code left_support}, {@code Support_02} / {@code center_support},
 * {@code Support_03} / {@code right_support}, {@code deck_section_01..03}, {@code upper_roof},
 * {@code side_hull}, {@code lower_base}, {@code tech_manifold}) plus localized breach openings
 * for realistic point-of-impact holes, directional smoke/flame jets, and support-aware collapse.
 */
public final class DestructibleStructure {

    public enum HitZone {
        NONE(0.0f),
        HULL(1.0f),
        ROOF(1.15f),
        BASE(0.75f),
        INTERIOR(1.30f),
        SUPPORT(1.25f),
        DECK(1.05f),
        RAILING(0.80f),
        TECH_SECTION(1.20f);

        public final float damageMultiplier;
        HitZone(float damageMultiplier) { this.damageMultiplier = damageMultiplier; }
    }

    public record BoundingRegion(Box box, HitZone zone, String componentId) {
        public BoundingRegion(Box box, HitZone zone) {
            this(box, zone, defaultComponentName(zone));
        }

        private static String defaultComponentName(HitZone zone) {
            return switch (zone) {
                case ROOF -> "upper_roof";
                case BASE -> "lower_base";
                case INTERIOR -> "inner_core";
                case SUPPORT -> "center_support";
                case DECK -> "deck_section";
                case RAILING -> "parapet_railing";
                case TECH_SECTION -> "tech_manifold";
                default -> "side_hull";
            };
        }

        public boolean contains(Vec3d pos) {
            return box.expand(0.25).contains(pos);
        }

        public boolean intersects(Box other) {
            return box.intersects(other);
        }

        public double distanceToSurface(Vec3d point) {
            double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - box.maxX));
            double dy = Math.max(0.0, Math.max(box.minY - point.y, point.y - box.maxY));
            double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - box.maxZ));
            double outsideDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (outsideDist > 0.0) return outsideDist;
            // Inside the box: distance to nearest face
            double inX = Math.min(point.x - box.minX, box.maxX - point.x);
            double inY = Math.min(point.y - box.minY, box.maxY - point.y);
            double inZ = Math.min(point.z - box.minZ, box.maxZ - point.z);
            return -Math.min(inX, Math.min(inY, inZ));
        }
    }

    public record HitMatch(HitZone zone, String componentId, BoundingRegion region) {
        public static final HitMatch NONE = new HitMatch(HitZone.NONE, "none", null);
        public boolean isHit() {
            return zone != HitZone.NONE;
        }
    }

    private final String structureId;
    private final DestructibleStructureType structureType;
    private final Identifier dimensionId;
    private final BlockPos originPosition;
    private final int orientation;
    private final List<BoundingRegion> boundingRegions;
    private final Box overallBounds;
    private final int radius;
    private final int height;
    private final int variantIndex;
    private final int crossingId;
    private final int spanU0;
    private final int spanU1;

    private final float maxHealth;
    private float currentHealth;
    private boolean componentHealthKnown = true;
    private final Map<String, Float> componentHealth = new LinkedHashMap<>();
    private final Map<String, Float> componentMaxHealth = new LinkedHashMap<>();
    private final Set<String> destroyedComponents = new LinkedHashSet<>();
    private final Map<Long, BpSpec> localBreachCells = new LinkedHashMap<>();
    private final List<StructuralSupportSolver.BreachOpening> breachOpenings = new ArrayList<>();
    private int localBreachRevision = 0;
    private String lastHitComponentId;
    private HitZone lastHitZone;
    private Vec3d lastImpactPoint;
    private Vec3d lastImpactNormal;
    private int destroyedVariantOverride = -1;

    private StructureDamageState damageStage;
    private StructureDamageState targetStage;
    private int transitionProgress;
    private boolean isBurning;
    private float fireIntensity;
    private int remainingBurnTime;
    private boolean destroyed;
    private boolean animationPlayed;
    private long lastUpdateTick;

    public DestructibleStructure(String structureId,
                                 DestructibleStructureType structureType,
                                 Identifier dimensionId,
                                 BlockPos originPosition,
                                 int orientation,
                                 List<BoundingRegion> boundingRegions,
                                 int radius,
                                 int height,
                                 int variantIndex) {
        this(structureId, structureType, dimensionId, originPosition, orientation,
                boundingRegions, radius, height, variantIndex, -1, -radius, radius);
    }

    public DestructibleStructure(String structureId,
                                 DestructibleStructureType structureType,
                                 Identifier dimensionId,
                                 BlockPos originPosition,
                                 int orientation,
                                 List<BoundingRegion> boundingRegions,
                                 int radius,
                                 int height,
                                 int variantIndex,
                                 int crossingId,
                                 int spanU0,
                                 int spanU1) {
        this.structureId = Objects.requireNonNull(structureId);
        this.structureType = Objects.requireNonNull(structureType);
        this.dimensionId = Objects.requireNonNull(dimensionId);
        this.originPosition = originPosition.toImmutable();
        this.orientation = orientation & 3;
        this.boundingRegions = List.copyOf(boundingRegions);
        this.radius = radius;
        this.height = height;
        this.variantIndex = variantIndex;
        this.crossingId = crossingId;
        this.spanU0 = Math.min(spanU0, spanU1);
        this.spanU1 = Math.max(spanU0, spanU1);
        this.maxHealth = structureType.maxHealth();
        this.currentHealth = this.maxHealth;
        this.damageStage = StructureDamageState.INTACT;
        this.targetStage = StructureDamageState.INTACT;
        this.transitionProgress = 0;
        this.isBurning = false;
        this.fireIntensity = 0.0f;
        this.remainingBurnTime = 0;
        this.destroyed = false;
        this.animationPlayed = false;
        this.lastUpdateTick = 0L;
        this.lastHitComponentId = defaultComponentId();
        this.lastHitZone = HitZone.HULL;
        initComponentHealth();

        double minX = originPosition.getX() - Math.max(2, radius);
        double minY = originPosition.getY() - 2;
        double minZ = originPosition.getZ() - Math.max(2, radius);
        double maxX = originPosition.getX() + Math.max(2, radius) + 1;
        double maxY = originPosition.getY() + Math.max(2, height) + 2;
        double maxZ = originPosition.getZ() + Math.max(2, radius) + 1;
        if (!boundingRegions.isEmpty()) {
            minX = Double.POSITIVE_INFINITY;
            minY = Double.POSITIVE_INFINITY;
            minZ = Double.POSITIVE_INFINITY;
            maxX = Double.NEGATIVE_INFINITY;
            maxY = Double.NEGATIVE_INFINITY;
            maxZ = Double.NEGATIVE_INFINITY;
            for (BoundingRegion region : boundingRegions) {
                minX = Math.min(minX, region.box().minX);
                minY = Math.min(minY, region.box().minY);
                minZ = Math.min(minZ, region.box().minZ);
                maxX = Math.max(maxX, region.box().maxX);
                maxY = Math.max(maxY, region.box().maxY);
                maxZ = Math.max(maxZ, region.box().maxZ);
            }
        }
        this.overallBounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void initComponentHealth() {
        componentHealthKnown = true;
        componentHealth.clear();
        componentMaxHealth.clear();
        destroyedComponents.clear();
        localBreachCells.clear();
        breachOpenings.clear();
        localBreachRevision = 0;
        lastImpactPoint = null;
        lastImpactNormal = null;
        for (BoundingRegion region : boundingRegions) {
            float compMax = switch (region.zone()) {
                case SUPPORT -> maxHealth * 0.65f; // Realistic pier vulnerability: direct RPG/GROM7 hit to a pier can shear it!
                case DECK -> maxHealth;
                case RAILING -> maxHealth * 0.45f;
                default -> maxHealth * 0.55f;
            };
            componentMaxHealth.putIfAbsent(region.componentId(), compMax);
            componentHealth.putIfAbsent(region.componentId(), compMax);
        }
    }

    public synchronized void recordImpactVector(Vec3d impactPoint, Vec3d normal) {
        this.lastImpactPoint = impactPoint;
        if (normal != null && normal.lengthSquared() > 1e-6) {
            this.lastImpactNormal = normal.normalize();
        } else if (impactPoint != null) {
            Vec3d fromCenter = impactPoint.subtract(
                    originPosition.getX() + 0.5,
                    originPosition.getY() + height * 0.5,
                    originPosition.getZ() + 0.5);
            this.lastImpactNormal = fromCenter.lengthSquared() > 1e-6 ? fromCenter.normalize() : new Vec3d(0, 1, 0);
        }
    }

    public synchronized void recordLocalBreachCells(Map<Long, BpSpec> cells) {
        if (cells == null || cells.isEmpty()) return;
        int sumX = 0, sumY = 0, sumZ = 0, airCount = 0;
        for (Map.Entry<Long, BpSpec> e : cells.entrySet()) {
            localBreachCells.put(e.getKey(), e.getValue());
            if (e.getValue() != null && e.getValue().isAir()) {
                BlockPos p = BlockPos.fromLong(e.getKey());
                sumX += p.getX();
                sumY += p.getY();
                sumZ += p.getZ();
                airCount++;
            }
        }
        localBreachRevision++;
        if (airCount > 0) {
            int cx = Math.round((float) sumX / airCount);
            int cy = Math.round((float) sumY / airCount);
            int cz = Math.round((float) sumZ / airCount);
            Vec3d norm = lastImpactNormal;
            if (norm == null || norm.lengthSquared() < 1e-6) {
                Vec3d out = new Vec3d(cx - originPosition.getX(), Math.max(0.25, (cy - originPosition.getY()) * 0.25), cz - originPosition.getZ());
                norm = out.lengthSquared() > 1e-6 ? out.normalize() : new Vec3d(0, 1, 0);
            }
            float radiusEst = (float) Math.max(1.2, Math.min(3.5, Math.sqrt(airCount) * 0.65));
            // Coalesce with existing opening if within 3.5 blocks
            for (int i = 0; i < breachOpenings.size(); i++) {
                StructuralSupportSolver.BreachOpening old = breachOpenings.get(i);
                double d2 = (old.centerX() - cx) * (old.centerX() - cx)
                        + (old.centerY() - cy) * (old.centerY() - cy)
                        + (old.centerZ() - cz) * (old.centerZ() - cz);
                if (d2 <= 12.25) {
                    breachOpenings.set(i, new StructuralSupportSolver.BreachOpening(
                            (old.centerX() + cx) / 2,
                            (old.centerY() + cy) / 2,
                            (old.centerZ() + cz) / 2,
                            norm.x, norm.y, norm.z,
                            Math.min(4.0f, Math.max(old.radius(), radiusEst) + 0.35f),
                            old.detachedCells() + airCount
                    ));
                    return;
                }
            }
            if (breachOpenings.size() < 8) {
                breachOpenings.add(new StructuralSupportSolver.BreachOpening(
                        cx, cy, cz, norm.x, norm.y, norm.z, radiusEst, airCount
                ));
            }
        }
    }

    public synchronized Map<Long, BpSpec> localBreachCells() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(localBreachCells));
    }

    public synchronized List<StructuralSupportSolver.BreachOpening> breachOpenings() {
        return List.copyOf(breachOpenings);
    }

    public synchronized int localBreachRevision() {
        return localBreachRevision;
    }

    public synchronized Vec3d lastImpactPoint() {
        return lastImpactPoint;
    }

    public synchronized Vec3d lastImpactNormal() {
        return lastImpactNormal;
    }

    public static String normalizeComponentAlias(String componentId, int sectionIndex) {
        if (componentId == null || componentId.isEmpty()) return "";
        String lower = componentId.trim().toLowerCase(Locale.ROOT);
        if ("support_01".equals(lower) || "support_1".equals(lower)) return "left_support";
        if ("support_02".equals(lower) || "support_2".equals(lower)) return "center_support";
        if ("support_03".equals(lower) || "support_3".equals(lower)) return "right_support";
        if ("deck_01".equals(lower) || "span_01".equals(lower)) return "deck_section_01";
        if ("deck_02".equals(lower) || "span_02".equals(lower)) return "deck_section_02";
        if ("deck_03".equals(lower) || "span_03".equals(lower)) return "deck_section_03";
        return componentId;
    }

    public String defaultComponentId() {
        if (!boundingRegions.isEmpty()) {
            return boundingRegions.get(0).componentId();
        }
        return switch (structureType.category()) {
            case BRIDGE_SECTION -> "deck_section_01";
            case REFINERY_TANK -> "side_hull";
            default -> "transformer_core";
        };
    }

    /**
     * Volumetric hit zones for a refinery storage tank (Section 5 & Section 7):
     * - lower_base (BASE)
     * - upper_roof (ROOF)
     * - tech_manifold (INTERIOR / technological manifold section)
     * - side_hull (HULL)
     */
    public static List<BoundingRegion> createTankRegions(BlockPos origin, int radius, int height) {
        double cx = origin.getX() + 0.5;
        double cy = origin.getY();
        double cz = origin.getZ() + 0.5;
        double r = radius + 0.75;
        double innerR = Math.max(1.5, radius - 1.25);
        return List.of(
                new BoundingRegion(new Box(cx - r - 1.5, cy - 1.0, cz - r - 1.5, cx + r + 1.5, cy + 2.0, cz + r + 1.5),
                        HitZone.BASE, "lower_base"),
                new BoundingRegion(new Box(cx - r, cy + height - 0.5, cz - r, cx + r, cy + height + 2.5, cz + r),
                        HitZone.ROOF, "upper_roof"),
                new BoundingRegion(new Box(cx - innerR, cy + 1.5, cz - innerR, cx + innerR, cy + height - 0.5, cz + innerR),
                        HitZone.INTERIOR, "tech_manifold"),
                new BoundingRegion(new Box(cx - r, cy + 1.0, cz - r, cx + r, cy + height + 0.5, cz + r),
                        HitZone.HULL, "side_hull")
        );
    }

    /**
     * Volumetric hit zones for a bridge section (Section 1.2 & Section 5):
     * Non-overlapping vertical bands for pier support ({@code left_support}, {@code center_support}, {@code right_support}),
     * carriageway deck ({@code deck_section_01..03}), and parapet/truss ({@code parapet_railing}).
     * Also keeps horizontal margin tight (`+0.15` instead of `+0.85`) so adjacent bridge sections never overlap.
     */
    public static List<BoundingRegion> createBridgeSectionRegions(BlockPos origin,
                                                                  boolean alongX,
                                                                  int halfSpan,
                                                                  int sectionIndex) {
        double cx = origin.getX() + 0.5;
        double cy = origin.getY();
        double cz = origin.getZ() + 0.5;
        double hw = (alongX ? halfSpan : 5) + 0.15;
        double hd = (alongX ? 5 : halfSpan) + 0.15;
        String supportName = switch (sectionIndex) {
            case 0 -> "left_support";
            case 1 -> "center_support";
            default -> "right_support";
        };
        String deckName = String.format(Locale.ROOT, "deck_section_%02d", sectionIndex + 1);
        return List.of(
                // Pier & under-deck support girders (y - 14.0 .. y - 0.45)
                new BoundingRegion(new Box(cx - hw, cy - 14.0, cz - hd, cx + hw, cy - 0.45, cz + hd),
                        HitZone.SUPPORT, supportName),
                // Main carriageway deck slab (y - 0.45 .. y + 1.35)
                new BoundingRegion(new Box(cx - hw, cy - 0.45, cz - hd, cx + hw, cy + 1.35, cz + hd),
                        HitZone.DECK, deckName),
                // Upper parapet / overhead truss envelope (y + 1.35 .. y + 7.0)
                new BoundingRegion(new Box(cx - hw, cy + 1.35, cz - hd, cx + hw, cy + 7.0, cz + hd),
                        HitZone.RAILING, "parapet_railing")
        );
    }

    /** Hit bands follow every actual generated bridge column, including slope, deep piers and truss height. */
    public static List<BoundingRegion> createGeneratedBridgeRegions(com.bobincraft.warzone.WarzoneTerrain terrain,
            com.bobincraft.warzone.ZonePlan.Crossing crossing, int u0, int u1, int sectionIndex) {
        var regions = new ArrayList<BoundingRegion>();
        String support = switch (sectionIndex) {
            case 0 -> "left_support";
            case 1 -> "center_support";
            default -> "right_support";
        };
        String deck = String.format(Locale.ROOT, "deck_section_%02d", sectionIndex + 1);
        for (int u = u0; u <= u1; u++) {
            int y = crossing.floor(u), x0 = Math.min(crossing.wx(u, -4), crossing.wx(u, 4)), x1 = Math.max(crossing.wx(u, -4), crossing.wx(u, 4)) + 1;
            int z0 = Math.min(crossing.wz(u, -4), crossing.wz(u, 4)), z1 = Math.max(crossing.wz(u, -4), crossing.wz(u, 4)) + 1;
            regions.add(new BoundingRegion(new Box(x0, y - 1, z0, x1, y, z1), HitZone.SUPPORT, support));
            regions.add(new BoundingRegion(new Box(x0, y, z0, x1, y + 1, z1), HitZone.DECK, deck));
            for (int v : new int[]{-4, 4}) {
                int x = crossing.wx(u, v), z = crossing.wz(u, v);
                regions.add(new BoundingRegion(new Box(x, y + 1, z, x + 1, y + 6, z + 1), HitZone.RAILING, "parapet_railing"));
            }
            if (Math.floorMod(u, 16) == 0 && u > -crossing.negative() + 12 && u < crossing.positive() - 12) {
                regions.add(new BoundingRegion(new Box(x0, y - 2, z0, x1, y - 1, z1), HitZone.SUPPORT, support));
                for (int v : new int[]{-3, 3}) {
                    int x = crossing.wx(u, v), z = crossing.wz(u, v);
                    var sample = new com.bobincraft.warzone.WarzoneTerrain.Sample();
                    terrain.naturalLandscape().sample(x, z, sample);
                    if (sample.height < y - 1) regions.add(new BoundingRegion(new Box(x, sample.height, z, x + 1, y - 1, z + 1), HitZone.SUPPORT, support));
                }
            }
        }
        return List.copyOf(regions);
    }

    /** Actual IndustrialFacilities gantries, switching banks and high jumpers; transformer bays stay separate. */
    public static List<BoundingRegion> createSwitchyardRegions(int x, int y, int z) {
        return List.of(
                new BoundingRegion(new Box(x - 20, y, z - 31, x + 21, y + 12, z - 4), HitZone.TECH_SECTION, "equipment_hull"),
                new BoundingRegion(new Box(x - 20, y, z + 15, x + 21, y + 15, z + 18), HitZone.TECH_SECTION, "equipment_hull"),
                new BoundingRegion(new Box(x - 20, y + 8, z - 4, x + 21, y + 15, z + 15), HitZone.TECH_SECTION, "equipment_hull"));
    }

    public static List<BoundingRegion> createBoxRegions(BlockPos origin, int halfWidth, int halfDepth, int minYOffset, int maxYOffset) {
        double cx = origin.getX() + 0.5;
        double cy = origin.getY();
        double cz = origin.getZ() + 0.5;
        double hw = halfWidth + 0.75;
        double hd = halfDepth + 0.75;
        return List.of(
                new BoundingRegion(new Box(cx - hw, cy + minYOffset, cz - hd, cx + hw, cy + minYOffset + 1.5, cz + hd),
                        HitZone.BASE, "foundation_base"),
                new BoundingRegion(new Box(cx - hw, cy + maxYOffset - 1.0, cz - hd, cx + hw, cy + maxYOffset + 1.5, cz + hd),
                        HitZone.ROOF, "high_voltage_bushings"),
                new BoundingRegion(new Box(cx - hw, cy + minYOffset, cz - hd, cx + hw, cy + maxYOffset + 1.0, cz + hd),
                        HitZone.HULL, "equipment_hull")
        );
    }

    /**
     * Resolves both the {@link HitZone} and logical component ID at an impact position using exact
     * surface priority and minimum surface distance when regions touch or overlap (Section 1.2).
     */
    public HitMatch matchAt(Vec3d pos, double tolerance) {
        if (!overallBounds.expand(tolerance).contains(pos)) return HitMatch.NONE;
        if (structureType.category() == DestructibleStructureType.Category.REFINERY_TANK) {
            double dx = pos.x - (originPosition.getX() + 0.5);
            double dz = pos.z - (originPosition.getZ() + 0.5);
            double maxR = radius + 2.0 + tolerance;
            if (dx * dx + dz * dz > maxR * maxR) return HitMatch.NONE;
        }
        // First pass: exact containment without tolerance expansion
        for (BoundingRegion region : boundingRegions) {
            if (region.box().contains(pos)) {
                return new HitMatch(region.zone(), region.componentId(), region);
            }
        }
        // Second pass: pick the region with the smallest surface distance within tolerance
        BoundingRegion best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (BoundingRegion region : boundingRegions) {
            if (region.box().expand(tolerance).contains(pos)) {
                double d = region.distanceToSurface(pos);
                // For bridge sections, respect vertical plane separating support (< y - 0.45) and deck (>= y - 0.45)
                if (structureType.category() == DestructibleStructureType.Category.BRIDGE_SECTION) {
                    double deckThreshold = originPosition.getY() - 0.45;
                    if (pos.y >= deckThreshold && region.zone() == HitZone.SUPPORT) {
                        d += 1.5;
                    } else if (pos.y < deckThreshold && region.zone() == HitZone.DECK) {
                        d += 1.5;
                    }
                }
                if (d < bestDist) {
                    bestDist = d;
                    best = region;
                }
            }
        }
        if (best != null) {
            return new HitMatch(best.zone(), best.componentId(), best);
        }
        return new HitMatch(HitZone.HULL, defaultComponentId(), null);
    }

    /**
     * Checks whether a point is inside this structure and returns the most specific hit zone.
     * For cylindrical tanks, also verifies radial distance in the horizontal plane.
     */
    public HitZone hitZoneAt(Vec3d pos, double tolerance) {
        return matchAt(pos, tolerance).zone();
    }

    /**
     * Resolves the best matching component and zone for an impact center within {@code radiusSphere}.
     */
    public HitMatch matchSphere(Vec3d center, double radiusSphere) {
        HitMatch direct = matchAt(center, 0.85);
        if (direct.isHit()) return direct;
        if (!intersectsSphere(center, radiusSphere)) return HitMatch.NONE;
        BoundingRegion bestRegion = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (BoundingRegion region : boundingRegions) {
            Vec3d clamped = new Vec3d(
                    MathHelper.clamp(center.x, region.box().minX, region.box().maxX),
                    MathHelper.clamp(center.y, region.box().minY, region.box().maxY),
                    MathHelper.clamp(center.z, region.box().minZ, region.box().maxZ)
            );
            double d2 = center.squaredDistanceTo(clamped);
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestRegion = region;
            }
        }
        if (bestRegion != null) {
            return new HitMatch(bestRegion.zone(), bestRegion.componentId(), bestRegion);
        }
        return new HitMatch(HitZone.HULL, defaultComponentId(), null);
    }

    public boolean intersectsSphere(Vec3d center, double radiusSphere) {
        if (!overallBounds.expand(radiusSphere).contains(center)) return false;
        if (structureType.category() == DestructibleStructureType.Category.REFINERY_TANK) {
            double dx = center.x - (originPosition.getX() + 0.5);
            double dz = center.z - (originPosition.getZ() + 0.5);
            double r = this.radius + 1.5 + radiusSphere;
            if (dx * dx + dz * dz > r * r) return false;
        }
        for (BoundingRegion region : boundingRegions) {
            if (region.box().expand(radiusSphere).contains(center)) return true;
        }
        return true;
    }

    public double distanceToSurface(Vec3d point) {
        if (structureType.category() == DestructibleStructureType.Category.REFINERY_TANK) {
            double cx = originPosition.getX() + 0.5;
            double cz = originPosition.getZ() + 0.5;
            double radial = Math.hypot(point.x - cx, point.z - cz);
            double dr = Math.max(0.0, radial - (radius + 1.0));
            double dy = Math.max(0.0, Math.max(originPosition.getY() - point.y, point.y - (originPosition.getY() + height + 1.0)));
            return Math.hypot(dr, dy);
        }
        double dx = Math.max(0.0, Math.max(overallBounds.minX - point.x, point.x - overallBounds.maxX));
        double dy = Math.max(0.0, Math.max(overallBounds.minY - point.y, point.y - overallBounds.maxY));
        double dz = Math.max(0.0, Math.max(overallBounds.minZ - point.z, point.z - overallBounds.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public synchronized boolean applyComponentDamage(String componentId,
                                                     HitZone zone,
                                                     float damageAmount,
                                                     long worldTick) {
        if (!Float.isFinite(damageAmount) || damageAmount <= 0.0f) {
            return false;
        }
        if ((destroyed || damageStage.isTerminalOrDestroying())
                && structureType.category() != DestructibleStructureType.Category.BRIDGE_SECTION) {
            return false;
        }
        if (!componentHealthKnown) {
            this.lastHitComponentId = componentId;
            this.lastHitZone = zone;
            return applyHealthDelta(damageAmount, worldTick);
        }
        String canonicalComp = normalizeComponentAlias(componentId, variantIndex % 10);
        if (canonicalComp != null && !canonicalComp.isEmpty()) {
            this.lastHitComponentId = canonicalComp;
            float compMax = componentMaxHealth.getOrDefault(canonicalComp, maxHealth);
            float prevCompHp = componentHealth.getOrDefault(canonicalComp, compMax);
            float nextCompHp = Math.max(0.0f, prevCompHp - damageAmount);
            componentHealth.put(canonicalComp, nextCompHp);
            if (nextCompHp <= 0.0f) {
                destroyedComponents.add(canonicalComp);
                // Do not zero out the entire bridge section HP when only one pile is hit so the second pile remains destructible!
            }
        }
        if (zone != null && zone != HitZone.NONE) {
            this.lastHitZone = zone;
            // Select deterministic ruin variant based on where the structure was struck
            if (destroyedVariantOverride < 0) {
                int zoneSalt = switch (zone) {
                    case SUPPORT, BASE -> 1;
                    case ROOF, RAILING -> 2;
                    default -> 0;
                };
                this.destroyedVariantOverride = Math.floorMod(variantIndex + zoneSalt, 4);
            }
        }
        return applyHealthDelta(damageAmount, worldTick);
    }

    public synchronized boolean applyHealthDelta(float damageAmount, long worldTick) {
        if (!Float.isFinite(damageAmount) || damageAmount <= 0.0f) {
            return false;
        }
        if (destroyed || damageStage.isTerminalOrDestroying()) {
            if (structureType.category() == DestructibleStructureType.Category.BRIDGE_SECTION) {
                this.lastUpdateTick = worldTick;
                return true;
            }
            return false;
        }
        float prevHealth = this.currentHealth;
        this.currentHealth = Math.max(0.0f, this.currentHealth - damageAmount);
        this.lastUpdateTick = worldTick;
        StructureDamageState nextStage = structureType.resolveStage(this.currentHealth);
        if (this.currentHealth <= 0.0f) {
            nextStage = StructureDamageState.DESTROYED;
            this.destroyed = true;
            if (lastHitComponentId != null && !lastHitComponentId.isEmpty()) {
                destroyedComponents.add(lastHitComponentId);
                componentHealth.put(lastHitComponentId, 0.0f);
            }
            for (String compKey : componentHealth.keySet()) {
                if (structureType.category() == DestructibleStructureType.Category.BRIDGE_SECTION) {
                    if (lastHitZone == HitZone.SUPPORT || (lastHitComponentId != null && lastHitComponentId.contains("support"))) {
                        componentHealth.put(compKey, 0.0f);
                        destroyedComponents.add(compKey);
                    } else if (compKey.contains("deck")) {
                        componentHealth.put(compKey, 0.0f);
                        destroyedComponents.add(compKey);
                    }
                } else {
                    componentHealth.put(compKey, 0.0f);
                    destroyedComponents.add(compKey);
                }
            }
        }
        boolean stageChanged = nextStage.index > this.damageStage.index;
        if (stageChanged) {
            this.damageStage = nextStage;
            this.targetStage = nextStage;
        }
        return this.currentHealth != prevHealth;
    }

    /**
     * Marks the structure as entering the active collapse animation ({@link StructureDamageState#DESTROYING})
     * before finalizing to {@link StructureDamageState#DESTROYED} (Section 2.3).
     */
    public synchronized boolean beginDestroyingAnimation(long worldTick) {
        if (this.animationPlayed || this.currentHealth > 0.0f) return false;
        this.animationPlayed = true;
        this.currentHealth = 0.0f;
        this.destroyed = true;
        this.damageStage = StructureDamageState.DESTROYING;
        this.targetStage = StructureDamageState.DESTROYED;
        this.lastUpdateTick = worldTick;
        return true;
    }

    public synchronized void completeDestroyingAnimation(long worldTick) {
        this.currentHealth = 0.0f;
        this.destroyed = true;
        this.animationPlayed = true;
        this.damageStage = StructureDamageState.DESTROYED;
        this.targetStage = StructureDamageState.DESTROYED;
        this.lastUpdateTick = worldTick;
    }

    public synchronized void forceStage(StructureDamageState stage, long worldTick) {
        Objects.requireNonNull(stage);
        this.damageStage = stage;
        this.targetStage = stage.templateStage();
        this.transitionProgress = 0;
        this.lastUpdateTick = worldTick;
        this.destroyed = stage.isTerminalOrDestroying();
        this.currentHealth = switch (stage) {
            case INTACT -> maxHealth;
            case DAMAGED -> maxHealth * 0.65f;
            case CRITICAL -> maxHealth * 0.25f;
            case DESTROYING, DESTROYED -> 0.0f;
        };
        if (stage == StructureDamageState.INTACT) {
            this.isBurning = false;
            this.fireIntensity = 0.0f;
            this.remainingBurnTime = 0;
            this.destroyedVariantOverride = -1;
            this.animationPlayed = false;
            initComponentHealth();
        } else if (stage.isTerminalOrDestroying()) {
            destroyedComponents.add(defaultComponentId());
            for (String key : new ArrayList<>(componentHealth.keySet())) {
                componentHealth.put(key, 0.0f);
                destroyedComponents.add(key);
            }
        }
    }

    public synchronized void reset(long worldTick) {
        forceStage(StructureDamageState.INTACT, worldTick);
    }

    public synchronized void setFireState(boolean burning, float intensity, int remainingTicks, long worldTick) {
        this.isBurning = burning && remainingTicks > 0;
        this.fireIntensity = this.isBurning ? MathHelper.clamp(intensity, 0.0f, 1.0f) : 0.0f;
        this.remainingBurnTime = this.isBurning ? Math.max(0, remainingTicks) : 0;
        this.lastUpdateTick = worldTick;
    }

    public synchronized void setTransitionProgress(StructureDamageState target, int progress) {
        this.targetStage = target == null ? this.damageStage.templateStage() : target.templateStage();
        this.transitionProgress = Math.max(0, progress);
        if (progress == 0 && this.damageStage == StructureDamageState.DESTROYING) {
            this.damageStage = StructureDamageState.DESTROYED;
        }
    }

    public synchronized NbtCompound toCompactNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Id", structureId);
        nbt.putFloat("Hp", currentHealth);
        nbt.putBoolean("ComponentHealthKnown", componentHealthKnown);
        if (componentHealthKnown) {
            var hp = new NbtCompound();
            for (var e : componentHealth.entrySet()) hp.putFloat(e.getKey(), e.getValue());
            nbt.put("ComponentHp", hp);
        }
        nbt.putByte("St", (byte) damageStage.templateStage().index);
        nbt.putByte("Tgt", (byte) targetStage.templateStage().index);
        if (transitionProgress > 0) nbt.putInt("Prg", transitionProgress);
        if (isBurning) {
            nbt.putBoolean("Burn", true);
            nbt.putFloat("FInt", fireIntensity);
            nbt.putInt("FBurn", remainingBurnTime);
        }
        if (destroyed) nbt.putBoolean("Dead", true);
        if (animationPlayed) nbt.putBoolean("Anim", true);
        if (lastHitComponentId != null && !lastHitComponentId.isEmpty()) {
            nbt.putString("Comp", lastHitComponentId);
        }
        if (destroyedVariantOverride >= 0) {
            nbt.putByte("Var", (byte) destroyedVariantOverride);
        }
        if (!destroyedComponents.isEmpty()) {
            nbt.putString("DComps", String.join(",", destroyedComponents));
        }
        if (!breachOpenings.isEmpty()) {
            NbtList bList = new NbtList();
            for (var b : breachOpenings) {
                NbtCompound bTag = new NbtCompound();
                bTag.putInt("X", b.centerX());
                bTag.putInt("Y", b.centerY());
                bTag.putInt("Z", b.centerZ());
                bTag.putDouble("Nx", b.normalX());
                bTag.putDouble("Ny", b.normalY());
                bTag.putDouble("Nz", b.normalZ());
                bTag.putFloat("R", b.radius());
                bTag.putInt("C", b.detachedCells());
                bList.add(bTag);
            }
            nbt.put("Breaches", bList);
        }
        if (!localBreachCells.isEmpty()) {
            NbtList cList = new NbtList();
            for (var e : localBreachCells.entrySet()) {
                NbtCompound cTag = new NbtCompound();
                cTag.putLong("P", e.getKey());
                cTag.putString("S", e.getValue().toString());
                cList.add(cTag);
            }
            nbt.put("BreachCells", cList);
            nbt.putInt("BreachRev", localBreachRevision);
        }
        nbt.putLong("Tick", lastUpdateTick);
        return nbt;
    }

    public synchronized void fromCompactNbt(NbtCompound nbt) {
        if (nbt == null) return;
        float hp = nbt.contains("Hp") ? nbt.getFloat("Hp") : maxHealth;
        this.currentHealth = Float.isFinite(hp) ? MathHelper.clamp(hp, 0.0f, maxHealth) : maxHealth;
        this.damageStage = StructureDamageState.byIndex(nbt.getByte("St")).templateStage();
        this.targetStage = nbt.contains("Tgt") ? StructureDamageState.byIndex(nbt.getByte("Tgt")).templateStage() : this.damageStage;
        this.transitionProgress = Math.max(0, nbt.getInt("Prg"));
        this.destroyed = nbt.getBoolean("Dead") || this.currentHealth <= 0.0f || this.damageStage.isTerminalOrDestroying();
        this.animationPlayed = nbt.getBoolean("Anim") || this.destroyed;
        if (this.destroyed) {
            this.currentHealth = 0.0f;
            this.damageStage = StructureDamageState.DESTROYED;
            this.targetStage = StructureDamageState.DESTROYED;
        }
        if (nbt.contains("Comp")) {
            this.lastHitComponentId = nbt.getString("Comp");
        }
        if (nbt.contains("Var")) {
            this.destroyedVariantOverride = nbt.getByte("Var");
        }
        componentHealthKnown = nbt.contains("ComponentHp") && nbt.getBoolean("ComponentHealthKnown");
        if (componentHealthKnown) {
            var hpTag = nbt.getCompound("ComponentHp");
            for (String key : hpTag.getKeys()) {
                float value = hpTag.getFloat(key);
                if (Float.isFinite(value)) componentHealth.put(key, Math.max(0, Math.min(componentMaxHealth.getOrDefault(key, maxHealth), value)));
            }
        }
        destroyedComponents.clear();
        if (nbt.contains("DComps")) {
            String raw = nbt.getString("DComps");
            if (!raw.isEmpty()) {
                for (String part : raw.split(",")) {
                    if (!part.isBlank()) {
                        String trimmed = part.trim();
                        destroyedComponents.add(trimmed);
                        componentHealth.put(trimmed, 0.0f);
                    }
                }
            }
        }
        breachOpenings.clear();
        if (nbt.contains("Breaches", NbtElement.LIST_TYPE)) {
            NbtList bList = nbt.getList("Breaches", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < bList.size(); i++) {
                NbtCompound bTag = bList.getCompound(i);
                breachOpenings.add(new StructuralSupportSolver.BreachOpening(
                        bTag.getInt("X"), bTag.getInt("Y"), bTag.getInt("Z"),
                        bTag.getDouble("Nx"), bTag.getDouble("Ny"), bTag.getDouble("Nz"),
                        bTag.getFloat("R"), bTag.getInt("C")
                ));
            }
        }
        localBreachCells.clear();
        if (nbt.contains("BreachCells", NbtElement.LIST_TYPE)) {
            NbtList cList = nbt.getList("BreachCells", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < cList.size(); i++) {
                NbtCompound cTag = cList.getCompound(i);
                String specStr = cTag.getString("S");
                localBreachCells.put(cTag.getLong("P"), "air".equals(specStr) ? BpSpec.AIR : BpSpec.of(specStr));
            }
            localBreachRevision = nbt.getInt("BreachRev");
        }
        this.isBurning = nbt.getBoolean("Burn");
        this.fireIntensity = this.isBurning ? MathHelper.clamp(nbt.getFloat("FInt"), 0.0f, 1.0f) : 0.0f;
        this.remainingBurnTime = this.isBurning ? Math.max(0, nbt.getInt("FBurn")) : 0;
        if (this.remainingBurnTime <= 0) {
            this.isBurning = false;
            this.fireIntensity = 0.0f;
        }
        this.lastUpdateTick = nbt.getLong("Tick");
    }

    public boolean isModified() {
        return currentHealth < maxHealth || damageStage != StructureDamageState.INTACT || isBurning || destroyed
                || transitionProgress > 0 || !localBreachCells.isEmpty();
    }

    public String structureId() { return structureId; }
    public DestructibleStructureType structureType() { return structureType; }
    public Identifier dimensionId() { return dimensionId; }
    public BlockPos originPosition() { return originPosition; }
    public int orientation() { return orientation; }
    public List<BoundingRegion> boundingRegions() { return boundingRegions; }
    public Box overallBounds() { return overallBounds; }
    public int radius() { return radius; }
    public int height() { return height; }
    public int variantIndex() { return variantIndex; }
    public synchronized int destroyedVariantIndex() {
        return destroyedVariantOverride >= 0 ? destroyedVariantOverride : variantIndex;
    }
    public int crossingId() { return crossingId; }
    public int spanU0() { return spanU0; }
    public int spanU1() { return spanU1; }
    public float maxHealth() { return maxHealth; }
    public synchronized float currentHealth() { return currentHealth; }
    public synchronized float healthFraction() { return maxHealth <= 0 ? 0 : currentHealth / maxHealth; }
    public synchronized float componentHealth(String componentId) {
        String key = normalizeComponentAlias(componentId, variantIndex % 10);
        return componentHealth.getOrDefault(key, componentMaxHealth.getOrDefault(key, maxHealth));
    }
    public synchronized float componentHealthFraction(String componentId) {
        String key = normalizeComponentAlias(componentId, variantIndex % 10);
        float max = componentMaxHealth.getOrDefault(key, maxHealth);
        return max <= 0 ? 0.0f : componentHealth(key) / max;
    }
    public synchronized Set<String> destroyedComponents() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(destroyedComponents));
    }
    public synchronized String lastHitComponentId() { return lastHitComponentId; }
    public synchronized HitZone lastHitZone() { return lastHitZone; }
    public synchronized StructureDamageState damageStage() { return damageStage; }
    public synchronized StructureDamageState targetStage() { return targetStage; }
    public synchronized int transitionProgress() { return transitionProgress; }
    public synchronized boolean isBurning() { return isBurning; }
    public synchronized float fireIntensity() { return fireIntensity; }
    public synchronized int remainingBurnTime() { return remainingBurnTime; }
    public synchronized boolean destroyed() { return destroyed; }
    public synchronized boolean animationPlayed() { return animationPlayed; }
    public synchronized void setAnimationPlayed(boolean played) { this.animationPlayed = played; }
    public synchronized long lastUpdateTick() { return lastUpdateTick; }
}
