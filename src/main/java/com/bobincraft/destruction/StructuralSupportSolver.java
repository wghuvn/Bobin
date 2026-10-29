package com.bobincraft.destruction;

import com.bobincraft.warzone.Blueprint;
import com.bobincraft.warzone.BpSpec;

import java.util.*;

/**
 * Pure topological support solver, localized impact breach calculator, and structural rubble settler.
 *
 * <p>Handles:
 * <ul>
 *   <li><b>Localized Projectile/Explosion Breaches</b>: Computes realistic directional holes in refinery tanks,
 *       transformers, switching equipment, and bridge piers/decks at the exact impact coordinates, plus
 *       outward breach emission vectors for black hydrocarbon smoke and fire jets.</li>
 *   <li><b>Balanced Organic Bridge Collapse</b>:
 *       <ul>
 *         <li>Destroying ONE pile ({@code v = -3} or {@code v = +3}) collapses a realistic rounded, jagged
 *             semi-elliptical crater (~5.5–6.5 blocks radius at the outer edge, curving inward toward the centerline
 *             with stepped girder lips) around that broken pile instead of a straight rectangular trench.</li>
 *         <li>Destroying BOTH piles at a pier collapses a jagged, V-notched span (~6.5–8.0 blocks radius) with
 *             irregular stepped edges across the roadway width.</li>
 *         <li>Runs a 2D Graph Reachability BFS from grounded anchors (riverbanks and intact piles) so any
 *             disconnected floating island between broken piers collapses completely.</li>
 *       </ul>
 *   </li>
 * </ul>
 */
public final class StructuralSupportSolver {

    /**
     * Represents a localized breach (hole) punched into a structure by an RPG, shell, drone, or demolition charge.
     */
    public record BreachOpening(
            int centerX,
            int centerY,
            int centerZ,
            double normalX,
            double normalY,
            double normalZ,
            float radius,
            int detachedCells
    ) {}

    private StructuralSupportSolver() {}

    public static long packBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    /**
     * Computes the exact set of structure cells punctured by a localized hit (e.g., RPG rocket, tank shell, FPV drone)
     * on a cylindrical refinery storage tank.
     */
    public static Map<Long, BpSpec> computeTankBreach(Blueprint currentBp,
                                                      int originX, int originY, int originZ,
                                                      int tankRadius, int tankHeight,
                                                      double impactX, double impactY, double impactZ,
                                                      double normalX, double normalY, double normalZ,
                                                      float effectiveDamage) {
        Map<Long, BpSpec> changes = new LinkedHashMap<>();
        if (currentBp == null) return changes;

        double holeRadius = Math.max(1.45, Math.min(3.35, 1.35 + effectiveDamage / 240.0));
        double rimRadius = holeRadius + 0.95;

        double dx0 = impactX - (originX + 0.5);
        double dz0 = impactZ - (originZ + 0.5);
        double radial0 = Math.hypot(dx0, dz0);
        double effX = impactX;
        double effY = Math.max(originY + 1.5, Math.min(originY + tankHeight + 0.5, impactY));
        double effZ = impactZ;
        if (radial0 > tankRadius + 0.5 && radial0 > 1e-4) {
            double scale = tankRadius / radial0;
            effX = originX + 0.5 + dx0 * scale;
            effZ = originZ + 0.5 + dz0 * scale;
        }

        int minX = (int) Math.floor(effX - rimRadius - 1);
        int maxX = (int) Math.ceil(effX + rimRadius + 1);
        int minY = Math.max(originY + 1, (int) Math.floor(effY - rimRadius - 1));
        int maxY = Math.min(originY + tankHeight + 1, (int) Math.ceil(effY + rimRadius + 1));
        int minZ = (int) Math.floor(effZ - rimRadius - 1);
        int maxZ = (int) Math.ceil(effZ + rimRadius + 1);

        for (int y = maxY; y >= minY; y--) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!currentBp.inBounds(x, y, z)) continue;
                    BpSpec existing = currentBp.get(x, y, z);
                    if (existing == null || existing.isAir()) continue;
                    if (y <= originY) continue;
                    if ("bobin_craft:crude_oil".equals(existing.id)) continue;

                    double cx = x + 0.5 - effX;
                    double cy = (y + 0.5 - effY) * 0.92;
                    double cz = z + 0.5 - effZ;
                    double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);

                    if (dist <= holeRadius) {
                        changes.put(packBlockPos(x, y, z), BpSpec.AIR);
                    } else if (dist <= rimRadius) {
                        if (!existing.id.contains("ladder") && !existing.id.contains("hopper")) {
                            changes.put(packBlockPos(x, y, z), BpSpec.of("bobin_craft:rusted_sheet_metal"));
                        }
                    }
                }
            }
        }
        return changes;
    }

    /**
     * Computes localized breach cells for a substation transformer or switchgear bay.
     */
    public static Map<Long, BpSpec> computeSubstationBreach(Blueprint currentBp,
                                                            int originX, int originY, int originZ,
                                                            double impactX, double impactY, double impactZ,
                                                            float effectiveDamage) {
        Map<Long, BpSpec> changes = new LinkedHashMap<>();
        if (currentBp == null) return changes;

        double holeRadius = Math.max(1.45, Math.min(3.1, 1.25 + effectiveDamage / 220.0));
        double insulatorShatterRadius = holeRadius + 2.2;

        int minX = (int) Math.floor(impactX - insulatorShatterRadius - 1);
        int maxX = (int) Math.ceil(impactX + insulatorShatterRadius + 1);
        int minY = Math.max(originY + 1, (int) Math.floor(impactY - insulatorShatterRadius - 1));
        int maxY = (int) Math.ceil(impactY + insulatorShatterRadius + 2);
        int minZ = (int) Math.floor(impactZ - insulatorShatterRadius - 1);
        int maxZ = (int) Math.ceil(impactZ + insulatorShatterRadius + 1);

        for (int y = maxY; y >= minY; y--) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!currentBp.inBounds(x, y, z)) continue;
                    BpSpec existing = currentBp.get(x, y, z);
                    if (existing == null || existing.isAir() || y <= originY) continue;

                    double dist = Math.sqrt(
                            (x + 0.5 - impactX) * (x + 0.5 - impactX)
                            + (y + 0.5 - impactY) * (y + 0.5 - impactY)
                            + (z + 0.5 - impactZ) * (z + 0.5 - impactZ));

                    if (dist <= holeRadius) {
                        boolean isCoreColumn = (x == originX) && (Math.abs(z - originZ) <= 2) && (y <= originY + 3);
                        if (isCoreColumn) {
                            changes.put(packBlockPos(x, y, z), BpSpec.of("waxed_oxidized_copper"));
                        } else {
                            changes.put(packBlockPos(x, y, z), BpSpec.AIR);
                        }
                    } else if (dist <= insulatorShatterRadius) {
                        if (existing.id.contains("insulator") || existing.id.contains("busbar") || existing.id.contains("hopper")) {
                            changes.put(packBlockPos(x, y, z), BpSpec.AIR);
                        } else if (dist <= holeRadius + 0.85 && existing.id.contains("terracotta")) {
                            changes.put(packBlockPos(x, y, z), BpSpec.of("bobin_craft:rusted_sheet_metal"));
                        }
                    }
                }
            }
        }
        return changes;
    }

    public static Map<Long, BpSpec> computeBridgeBreach(Blueprint currentBp,
                                                        boolean alongX,
                                                        int minU,
                                                        int maxU,
                                                        int centerX,
                                                        int centerZ,
                                                        double impactX,
                                                        double impactY,
                                                        double impactZ,
                                                        boolean supportHit,
                                                        boolean pierDestroyed,
                                                        float effectiveDamage,
                                                        Blueprint.Ground ground) {
        return computeBridgeBreach(currentBp, Map.of(), alongX, minU, maxU, centerX, centerZ,
                impactX, impactY, impactZ, supportHit, pierDestroyed, effectiveDamage, ground);
    }

    /**
     * Computes a balanced, organic 3D breach in a bridge deck, girder, railing, or individual pile (`v = -3` vs `v = +3`):
     * <ul>
     *   <li>When ONE pile (`v = -3` or `v = +3`) breaks: carves a rounded, jagged semi-elliptical collapse crater
     *       around that pile (~5.8 blocks radius at the outer edge, curving smoothly inward toward `v = 0` with
     *       stepped lower girder lips), never cutting a flat rectangular trench across the whole bridge.</li>
     *   <li>When BOTH piles (`v = -3` and `v = +3`) at a pier break: collapses a ~6.8–8.0 block radius span around
     *       the pier with uneven, jagged V-stepped fracture edges across `v = -4..4`.</li>
     *   <li>Runs a 2D Graph Reachability BFS from grounded anchors (riverbanks and intact piles) so any chunk
     *       completely cut off between two gaps collapses, while intact bank approaches remain standing!</li>
     * </ul>
     */
    public static Map<Long, BpSpec> computeBridgeBreach(Blueprint currentBp,
                                                        Map<Long, BpSpec> existingBreaches,
                                                        boolean alongX,
                                                        int minU,
                                                        int maxU,
                                                        int centerX,
                                                        int centerZ,
                                                        double impactX,
                                                        double impactY,
                                                        double impactZ,
                                                        boolean supportHit,
                                                        boolean pierDestroyed,
                                                        float effectiveDamage,
                                                        Blueprint.Ground ground) {
        Map<Long, BpSpec> changes = new LinkedHashMap<>();
        if (currentBp == null) return changes;
        if (existingBreaches != null && !existingBreaches.isEmpty()) {
            changes.putAll(existingBreaches);
        }

        double effX = impactX;
        double effY = impactY;
        double effZ = impactZ;
        if (ground != null) {
            double bestPileDist = 2.65;
            for (int u = minU; u <= maxU; u++) {
                for (int sideV : new int[]{-3, 3}) {
                    int wx = centerX + (alongX ? u : sideV);
                    int wz = centerZ + (alongX ? sideV : u);
                    int gy = ground.top(wx, wz);
                    int pileBlocks = 0;
                    int topPileY = -1;
                    for (int y = gy + 1; y <= currentBp.maxY(); y++) {
                        if (!currentBp.inBounds(wx, y, wz)) continue;
                        BpSpec s = currentBp.get(wx, y, wz);
                        if (s != null && !s.isAir() && isPileMaterial(s.id)) {
                            pileBlocks++;
                            topPileY = y;
                        }
                    }
                    if (pileBlocks >= 2 && impactY <= topPileY + 1.8) {
                        double d = Math.hypot(impactX - (wx + 0.5), impactZ - (wz + 0.5));
                        if (d < bestPileDist) {
                            bestPileDist = d;
                            effX = wx + 0.5;
                            effZ = wz + 0.5;
                            effY = Math.max(gy + 1.5, Math.min(topPileY - 0.5, impactY));
                            supportHit = true;
                        }
                    }
                }
            }
        }

        double holeRadius = supportHit
                ? Math.max(1.85, Math.min(2.25, 1.60 + effectiveDamage / 380.0))
                : Math.max(1.85, Math.min(2.85, 1.50 + effectiveDamage / 280.0));

        int minX = (int) Math.floor(effX - holeRadius - 1);
        int maxX = (int) Math.ceil(effX + holeRadius + 1);
        int minY = Math.max(currentBp.minY, (int) Math.floor(effY - holeRadius - 1));
        int maxY = Math.min(currentBp.maxY(), (int) Math.ceil(effY + holeRadius + 2));
        int minZ = (int) Math.floor(effZ - holeRadius - 1);
        int maxZ = (int) Math.ceil(effZ + holeRadius + 1);

        for (int y = maxY; y >= minY; y--) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!currentBp.inBounds(x, y, z)) continue;
                    BpSpec existing = currentBp.get(x, y, z);
                    if (existing == null || existing.isAir()) continue;
                    if (ground != null && y <= ground.top(x, z)) continue;

                    double dx = x + 0.5 - effX;
                    double dy = (y + 0.5 - effY) * (supportHit ? 0.72 : 0.88);
                    double dz = z + 0.5 - effZ;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist <= holeRadius) {
                        changes.put(packBlockPos(x, y, z), BpSpec.AIR);
                    }
                }
            }
        }

        if (ground == null) return changes;

        java.util.function.BiFunction<Long, BpSpec, BpSpec> stateAfter = (packed, orig) ->
                changes.containsKey(packed) ? changes.get(packed) : orig;

        // Step 1: Identify all piers in [minU..maxU] and determine whether each pile (v = -3 and v = +3) is intact or broken
        List<Integer> pierUs = new ArrayList<>();
        Set<Integer> intactLeftPiers = new HashSet<>();
        Set<Integer> intactRightPiers = new HashSet<>();
        Set<Integer> brokenLeftPiers = new HashSet<>();
        Set<Integer> brokenRightPiers = new HashSet<>();

        for (int u = minU; u <= maxU; u++) {
            boolean hadLeft = false, hadRight = false;
            boolean leftBrk = false, rightBrk = false;

            for (int sideV : new int[]{-3, 3}) {
                int wx = centerX + (alongX ? u : sideV);
                int wz = centerZ + (alongX ? sideV : u);
                int gy = ground.top(wx, wz);

                int origPileBlocks = 0;
                int highestPierY = -1;
                for (int y = gy + 1; y <= currentBp.maxY(); y++) {
                    if (!currentBp.inBounds(wx, y, wz)) continue;
                    BpSpec orig = currentBp.get(wx, y, wz);
                    if (orig != null && !orig.isAir() && isPileMaterial(orig.id)) {
                        origPileBlocks++;
                        highestPierY = y;
                    }
                }
                if (origPileBlocks < 2) continue;
                if (sideV < 0) hadLeft = true; else hadRight = true;

                int gap = 0;
                int firstGapY = -1;
                for (int y = gy + 1; y <= highestPierY; y++) {
                    long pk = packBlockPos(wx, y, wz);
                    BpSpec cur = stateAfter.apply(pk, currentBp.get(wx, y, wz));
                    if (cur == null || cur.isAir() || cur.id.contains("iron_bars")) {
                        gap++;
                        if (firstGapY < 0) firstGapY = y;
                    }
                }

                if (gap >= 1) {
                    if (sideV < 0) leftBrk = true; else rightBrk = true;
                    int stumpTop = Math.min(gy + 1, firstGapY);
                    for (int y = Math.max(gy + 1, stumpTop); y <= highestPierY + 1; y++) {
                        if (!currentBp.inBounds(wx, y, wz)) continue;
                        BpSpec cur = currentBp.get(wx, y, wz);
                        if (cur != null && !cur.isAir()) {
                            changes.put(packBlockPos(wx, y, wz), BpSpec.AIR);
                        }
                    }
                } else {
                    if (sideV < 0) intactLeftPiers.add(u); else intactRightPiers.add(u);
                }
            }

            if (hadLeft || hadRight) {
                pierUs.add(u);
                if (leftBrk) brokenLeftPiers.add(u);
                if (rightBrk) brokenRightPiers.add(u);
            }
        }

        // Step 2: Apply organic, rounded/jagged collapse around each broken pile (or full-width jagged span when both piles break)
        for (int pierU : pierUs) {
            boolean leftBrk = brokenLeftPiers.contains(pierU);
            boolean rightBrk = brokenRightPiers.contains(pierU);
            if (!leftBrk && !rightBrk) continue;

            if (leftBrk && !rightBrk) {
                // Single left pile (v = -3) broken: organic rounded semi-elliptical crater around (pierU, v = -3.2)
                applyRoundedSinglePileCollapse(currentBp, changes, alongX, minU, maxU, centerX, centerZ, pierU, -1, ground);
            } else if (rightBrk && !leftBrk) {
                // Single right pile (v = +3) broken: organic rounded semi-elliptical crater around (pierU, v = +3.2)
                applyRoundedSinglePileCollapse(currentBp, changes, alongX, minU, maxU, centerX, centerZ, pierU, 1, ground);
            } else {
                // Both piles at pierU broken: jagged, uneven full-width span collapse (~6.5–8.0 blocks radius, stepped edges)
                applyJaggedFullPierCollapse(currentBp, changes, alongX, minU, maxU, centerX, centerZ, pierU, ground);
            }
        }

        // Step 3: Run 2D Graph Reachability BFS from grounded anchors so any disconnected island between gaps collapses,
        // while connected bank approaches and rounded crater edges stay intact!
        sweepFloatingBridgeAttachments(currentBp, changes, alongX, minU, maxU, centerX, centerZ, ground,
                intactLeftPiers, intactRightPiers, brokenLeftPiers.isEmpty(), brokenRightPiers.isEmpty());
        return changes;
    }

    /**
     * Carves an organic, rounded semi-elliptical collapse crater around a single broken pile (`sideSign = -1` for `v = -3`,
     * `sideSign = +1` for `v = +3`), with jagged per-lane variation and a 1-block stepped lower girder rim so the fracture
     * looks curved and natural instead of a straight rectangular cut.
     */
    public static double singlePileCollapseRadiusU(int pierU, int v, int sideSign, boolean positiveDir) {
        int outerV = sideSign < 0 ? -4 : 4;
        int distFromOuter = Math.abs(v - outerV); // 0 at v=±4, 1 at v=±3 (pile), 2 at v=±2, 3 at v=±1, 4 at v=0
        if (distFromOuter > 4) return -1.0; // Opposite half of the roadway stays supported by the intact pile!
        double normV = distFromOuter / 4.45;
        double ellipse = Math.sqrt(Math.max(0.0, 1.0 - normV * normV));
        int hash = Math.floorMod((pierU * 37 + v * 73 + (positiveDir ? 19 : 53)) * 31, 5) - 2; // -2..+2
        double jitter = hash * 0.45;
        return 5.8 * ellipse + jitter;
    }

    /**
     * Computes the jagged longitudinal collapse radius along `u` for lane `v ∈ [-4..4]` when BOTH piles at `pierU` are broken.
     */
    public static double dualPileCollapseRadiusU(int pierU, int v, boolean positiveDir) {
        // Curved V-notch / diagonal shear profile + deterministic per-lane teeth so every lane breaks at a different u!
        double curve = 6.8 - 0.09 * (v * v);
        int tooth = switch (Math.floorMod(pierU * 17 + v * 41 + (positiveDir ? 7 : 23), 4)) {
            case 0 -> -1;
            case 1 -> 0;
            case 2 -> 1;
            default -> 2;
        };
        return curve + tooth * 0.75;
    }

    private static void applyRoundedSinglePileCollapse(Blueprint bp,
                                                       Map<Long, BpSpec> changes,
                                                       boolean alongX,
                                                       int minU,
                                                       int maxU,
                                                       int centerX,
                                                       int centerZ,
                                                       int pierU,
                                                       int sideSign,
                                                       Blueprint.Ground ground) {
        for (int v = -4; v <= 4; v++) {
            double radNeg = singlePileCollapseRadiusU(pierU, v, sideSign, false);
            double radPos = singlePileCollapseRadiusU(pierU, v, sideSign, true);
            if (radNeg < 0 && radPos < 0) continue;

            int uStart = Math.max(minU, (int) Math.floor(pierU - Math.max(0.0, radNeg) - 1));
            int uEnd = Math.min(maxU, (int) Math.ceil(pierU + Math.max(0.0, radPos) + 1));
            for (int u = uStart; u <= uEnd; u++) {
                int du = u - pierU;
                double limit = du < 0 ? radNeg : radPos;
                if (limit <= 0.0) continue;
                double absDu = Math.abs(du);
                if (absDu > limit + 0.85) continue;

                int wx = centerX + (alongX ? u : v);
                int wz = centerZ + (alongX ? v : u);
                int gy = ground.top(wx, wz);
                int deckY = findDeckY(bp, wx, wz, gy);
                if (deckY > 0 && deckY <= gy + 2) continue; // Keep natural riverbank touchdown intact

                boolean isRimLip = absDu > limit && ((u + v) & 1) == 0;
                for (int y = bp.maxY(); y >= gy + 2; y--) {
                    if (!bp.inBounds(wx, y, wz)) continue;
                    BpSpec cur = bp.get(wx, y, wz);
                    if (cur == null || cur.isAir() || isPileMaterial(cur.id)) continue;
                    // On the outer 1-block fracture lip, leave the lower girder jutting out on alternating lanes for a stepped look!
                    if (isRimLip && y == deckY - 1) continue;
                    changes.put(packBlockPos(wx, y, wz), BpSpec.AIR);
                }
            }
        }
    }

    private static void applyJaggedFullPierCollapse(Blueprint bp,
                                                    Map<Long, BpSpec> changes,
                                                    boolean alongX,
                                                    int minU,
                                                    int maxU,
                                                    int centerX,
                                                    int centerZ,
                                                    int pierU,
                                                    Blueprint.Ground ground) {
        for (int v = -4; v <= 4; v++) {
            double radNeg = dualPileCollapseRadiusU(pierU, v, false);
            double radPos = dualPileCollapseRadiusU(pierU, v, true);
            int uStart = Math.max(minU, (int) Math.floor(pierU - radNeg - 1));
            int uEnd = Math.min(maxU, (int) Math.ceil(pierU + radPos + 1));
            for (int u = uStart; u <= uEnd; u++) {
                int du = u - pierU;
                double limit = du < 0 ? radNeg : radPos;
                double absDu = Math.abs(du);
                if (absDu > limit + 0.85) continue;

                int wx = centerX + (alongX ? u : v);
                int wz = centerZ + (alongX ? v : u);
                int gy = ground.top(wx, wz);
                int deckY = findDeckY(bp, wx, wz, gy);
                if (deckY > 0 && deckY <= gy + 2) continue;

                boolean isRimLip = absDu > limit && ((u + v) & 1) == 0;
                for (int y = bp.maxY(); y >= gy + 2; y--) {
                    if (!bp.inBounds(wx, y, wz)) continue;
                    BpSpec cur = bp.get(wx, y, wz);
                    if (cur == null || cur.isAir() || isPileMaterial(cur.id)) continue;
                    if (isRimLip && y == deckY - 1) continue;
                    changes.put(packBlockPos(wx, y, wz), BpSpec.AIR);
                }
            }
        }
    }

    private static int findDeckY(Blueprint bp, int wx, int wz, int gy) {
        for (int y = bp.maxY(); y >= gy + 1; y--) {
            if (!bp.inBounds(wx, y, wz)) continue;
            BpSpec s = bp.get(wx, y, wz);
            if (s != null && !s.isAir() && isDeckOrGirderMaterial(s.id)) return y;
        }
        return -1;
    }

    private static boolean isPileMaterial(String id) {
        return id != null && (id.contains("andesite") || id.contains("dark_oak_log"));
    }

    private static boolean isDeckOrGirderMaterial(String id) {
        return id != null && (id.contains("concrete") || id.contains("asphalt") || id.contains("sheet_metal")
                || id.contains("dark_oak_log") || id.contains("slab") || id.contains("stairs") || id.contains("gravel"));
    }

    /**
     * Runs a 2D Graph Reachability BFS from grounded anchors (intact piers, ground banks, and unsevered ends)
     * across `(u, v)` deck columns, and sweeps all `iron_bars` and `waxed_oxidized_copper` trusses above.
     */
    private static void sweepFloatingBridgeAttachments(Blueprint bp,
                                                       Map<Long, BpSpec> changes,
                                                       boolean alongX,
                                                       int minU,
                                                       int maxU,
                                                       int centerX,
                                                       int centerZ,
                                                       Blueprint.Ground ground,
                                                       Set<Integer> intactLeftPiers,
                                                       Set<Integer> intactRightPiers,
                                                       boolean noLeftPileBroken,
                                                       boolean noRightPileBroken) {
        int uCount = maxU - minU + 1;
        if (uCount <= 0) return;
        boolean[][] hasDeck = new boolean[uCount][9];
        int[][] deckTopYs = new int[uCount][9];
        boolean[][] anchored = new boolean[uCount][9];

        for (int u = minU; u <= maxU; u++) {
            int ui = u - minU;
            for (int v = -4; v <= 4; v++) {
                int vi = v + 4;
                int wx = centerX + (alongX ? u : v);
                int wz = centerZ + (alongX ? v : u);
                int gy = ground.top(wx, wz);
                int topY = -1;
                for (int y = gy + 1; y <= bp.maxY(); y++) {
                    if (!bp.inBounds(wx, y, wz)) continue;
                    long pk = packBlockPos(wx, y, wz);
                    BpSpec s = changes.containsKey(pk) ? changes.get(pk) : bp.get(wx, y, wz);
                    if (s == null || s.isAir()) continue;
                    if (!s.id.contains("iron_bars") && !s.id.contains("copper") && (!isPileMaterial(s.id) || (s.id.contains("dark_oak_log") && Math.abs(v) != 3))) {
                        hasDeck[ui][vi] = true;
                        topY = Math.max(topY, y);
                    }
                }
                deckTopYs[ui][vi] = topY;
                if (!hasDeck[ui][vi]) continue;

                boolean onGround = (topY <= gy + 2);
                boolean atIntactPier = (v <= 0 && intactLeftPiers.contains(u)) || (v >= 0 && intactRightPiers.contains(u));
                boolean atBoundaryEnd = (u == minU || u == maxU);
                if (onGround || atIntactPier || atBoundaryEnd) {
                    anchored[ui][vi] = true;
                }
            }
        }

        boolean[][] reachable = new boolean[uCount][9];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int ui = 0; ui < uCount; ui++) {
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
                if (nui < 0 || nui >= uCount || nvi < 0 || nvi >= 9) continue;
                if (!hasDeck[nui][nvi] || reachable[nui][nvi]) continue;
                reachable[nui][nvi] = true;
                queue.add(new int[]{nui, nvi});
            }
        }

        for (int u = minU; u <= maxU; u++) {
            int ui = u - minU;
            for (int v = -4; v <= 4; v++) {
                int vi = v + 4;
                int wx = centerX + (alongX ? u : v);
                int wz = centerZ + (alongX ? v : u);
                int gy = ground.top(wx, wz);

                if (!reachable[ui][vi]) {
                    for (int y = gy + 2; y <= bp.maxY(); y++) {
                        if (!bp.inBounds(wx, y, wz)) continue;
                        long pk = packBlockPos(wx, y, wz);
                        BpSpec s = changes.containsKey(pk) ? changes.get(pk) : bp.get(wx, y, wz);
                        if (s != null && !s.isAir() && !isPileMaterial(s.id)) {
                            changes.put(pk, BpSpec.AIR);
                        }
                    }
                } else if (Math.abs(v) == 4) {
                    int deckTopY = deckTopYs[ui][vi];
                    BpSpec below = changes.containsKey(packBlockPos(wx, deckTopY, wz))
                            ? changes.get(packBlockPos(wx, deckTopY, wz))
                            : bp.get(wx, deckTopY, wz);
                    boolean deckSolid = below != null && !below.isAir() && !below.id.contains("slab");
                    for (int y = deckTopY + 1; y <= bp.maxY(); y++) {
                        if (!bp.inBounds(wx, y, wz)) continue;
                        long pk = packBlockPos(wx, y, wz);
                        BpSpec s = changes.containsKey(pk) ? changes.get(pk) : bp.get(wx, y, wz);
                        if (s == null || s.isAir()) continue;
                        if (!deckSolid) {
                            changes.put(pk, BpSpec.AIR);
                        } else if (y >= deckTopY + 2) {
                            int postU = Math.round(u / 8.0f) * 8;
                            int pui = postU - minU;
                            boolean postIntact = (pui >= 0 && pui < uCount && reachable[pui][vi]);
                            if (postIntact) {
                                int pwx = centerX + (alongX ? postU : v);
                                int pwz = centerZ + (alongX ? v : postU);
                                for (int py = deckTopY; py <= deckTopY + 5; py++) {
                                    if (!bp.inBounds(pwx, py, pwz)) { postIntact = false; break; }
                                    long ppk = packBlockPos(pwx, py, pwz);
                                    BpSpec ps = changes.containsKey(ppk) ? changes.get(ppk) : bp.get(pwx, py, pwz);
                                    if (ps == null || ps.isAir()) { postIntact = false; break; }
                                }
                            }
                            if (!postIntact) {
                                changes.put(pk, BpSpec.AIR);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Evaluates logical support across a bridge blueprint after a local hit or support failure.
     */
    public static List<Long> collapseUnsupportedBridgeSpans(Blueprint bp,
                                                            boolean alongX,
                                                            int minU,
                                                            int maxU,
                                                            int centerX,
                                                            int centerZ,
                                                            int maxCantileverSpan,
                                                            Blueprint.Ground ground,
                                                            boolean abutmentLeftIntact,
                                                            boolean abutmentRightIntact) {
        List<Long> collapsedCells = new ArrayList<>();
        if (bp == null) return collapsedCells;

        Set<Integer> supportedUs = new HashSet<>();
        if (abutmentLeftIntact) {
            supportedUs.add(minU);
            supportedUs.add(minU + 1);
        }
        if (abutmentRightIntact) {
            supportedUs.add(maxU);
            supportedUs.add(maxU - 1);
        }

        for (int u = minU; u <= maxU; u++) {
            int intactPierColumns = 0;
            for (int v : new int[]{-3, 3}) {
                int wx = centerX + (alongX ? u : v);
                int wz = centerZ + (alongX ? v : u);
                int gy = ground.top(wx, wz);
                int solidCount = 0;
                int highestPierY = -1;
                for (int y = gy + 1; y <= bp.maxY(); y++) {
                    BpSpec s = bp.get(wx, y, wz);
                    if (s != null && !s.isAir() && (s.id.contains("andesite") || s.id.contains("dark_oak_log") || s.id.contains("mossy_concrete"))) {
                        solidCount++;
                        highestPierY = y;
                    }
                }
                if (solidCount >= 3) {
                    int gap = 0;
                    for (int y = gy + 1; y <= highestPierY; y++) {
                        BpSpec s = bp.get(wx, y, wz);
                        if (s == null || s.isAir() || s.id.contains("iron_bars")) {
                            gap++;
                        }
                    }
                    if (gap <= 1) {
                        intactPierColumns++;
                    } else {
                        boolean seenGap = false;
                        for (int y = gy + 1; y <= highestPierY; y++) {
                            BpSpec s = bp.get(wx, y, wz);
                            if (s == null || s.isAir() || s.id.contains("iron_bars")) {
                                seenGap = true;
                            } else if (seenGap) {
                                bp.air(wx, y, wz);
                                collapsedCells.add(packBlockPos(wx, y, wz));
                            }
                        }
                    }
                }
            }
            if (intactPierColumns >= 2) {
                supportedUs.add(u);
            }
        }

        Set<Integer> deckIntactU = new HashSet<>();
        for (int u = minU; u <= maxU; u++) {
            int girderBlocks = 0;
            for (int v = -4; v <= 4; v++) {
                int wx = centerX + (alongX ? u : v);
                int wz = centerZ + (alongX ? v : u);
                for (int y = bp.minY; y <= bp.maxY(); y++) {
                    BpSpec s = bp.get(wx, y, wz);
                    if (s != null && !s.isAir() && (s.id.contains("concrete") || s.id.contains("asphalt") || s.id.contains("sheet_metal") || s.id.contains("log"))) {
                        if (y > ground.top(wx, wz) + 2) {
                            girderBlocks++;
                            break;
                        }
                    }
                }
            }
            if (girderBlocks >= 4) {
                deckIntactU.add(u);
            }
        }

        Map<Integer, Integer> distToSupport = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int supU : supportedUs) {
            if (deckIntactU.contains(supU)) {
                distToSupport.put(supU, 0);
                queue.add(supU);
            }
        }
        while (!queue.isEmpty()) {
            int curU = queue.poll();
            int curDist = distToSupport.get(curU);
            if (curDist >= maxCantileverSpan) continue;
            for (int dir : new int[]{-1, 1}) {
                int nextU = curU + dir;
                if (nextU >= minU && nextU <= maxU && deckIntactU.contains(nextU) && !distToSupport.containsKey(nextU)) {
                    distToSupport.put(nextU, curDist + 1);
                    queue.add(nextU);
                }
            }
        }

        for (int u = minU; u <= maxU; u++) {
            Integer dist = distToSupport.get(u);
            boolean unsupportedSpan = (dist == null || dist > maxCantileverSpan);
            boolean fractureLip = (dist != null && dist == maxCantileverSpan);

            for (int v = -4; v <= 4; v++) {
                int wx = centerX + (alongX ? u : v);
                int wz = centerZ + (alongX ? v : u);
                int gy = ground.top(wx, wz);

                if (unsupportedSpan) {
                    BpSpec fallingDeckMat = null;
                    for (int y = bp.maxY(); y >= gy + 2; y--) {
                        BpSpec s = bp.get(wx, y, wz);
                        if (s != null && !s.isAir()) {
                            if ((s.id.contains("andesite") || s.id.contains("dark_oak_log")) && y <= gy + 3) {
                                continue;
                            }
                            if (fallingDeckMat == null && (s.id.contains("concrete") || s.id.contains("asphalt") || s.id.contains("copper"))) {
                                fallingDeckMat = s;
                            }
                            bp.air(wx, y, wz);
                            collapsedCells.add(packBlockPos(wx, y, wz));
                        }
                    }
                    if (fallingDeckMat != null && ((u * 7 + v * 13) & 1) == 0) {
                        if (bp.inBounds(wx, gy + 1, wz)) {
                            bp.set(wx, gy + 1, wz, BpSpec.of("bobin_craft:mossy_concrete"));
                        }
                        if (bp.inBounds(wx, gy + 2, wz) && ((u + v) & 1) == 0) {
                            BpSpec topDebris = Math.abs(v) == 4
                                    ? BpSpec.of("iron_bars")
                                    : (((u * 3 + v) % 3 == 0) ? BpSpec.of("cobblestone_slab").with("type", "bottom") : fallingDeckMat);
                            bp.set(wx, gy + 2, wz, topDebris);
                        }
                    }
                } else if (fractureLip) {
                    for (int y = bp.maxY(); y >= gy + 3; y--) {
                        BpSpec s = bp.get(wx, y, wz);
                        if (s == null || s.isAir()) continue;
                        if (s.id.contains("iron_bars") || s.id.contains("copper")) {
                            bp.air(wx, y, wz);
                            collapsedCells.add(packBlockPos(wx, y, wz));
                        } else if (s.id.contains("asphalt") || (s.id.contains("concrete") && Math.abs(v) == 4)) {
                            bp.set(wx, y, wz, ((u + v) & 1) == 0
                                    ? BpSpec.of("cobblestone_slab").with("type", "bottom")
                                    : BpSpec.of("iron_bars"));
                            break;
                        }
                    }
                }
            }
        }

        Blueprint.Ground anchorGround = (gx, gz) -> {
            int u = alongX ? gx - centerX : gz - centerZ;
            if ((abutmentLeftIntact && u <= minU + 1) || (abutmentRightIntact && u >= maxU - 1)) {
                return bp.maxY();
            }
            return ground.top(gx, gz);
        };
        int floatingRemoved = bp.pruneFloating(anchorGround);
        if (floatingRemoved > 0) {
            bp.connect(ground);
        }
        return collapsedCells;
    }
}
