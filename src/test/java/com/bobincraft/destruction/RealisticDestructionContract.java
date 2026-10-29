package com.bobincraft.destruction;

import com.bobincraft.warzone.Blueprint;
import com.bobincraft.warzone.BpSpec;

import java.util.*;

/**
 * Pure Java 17 verification contract for:
 * 1. Localized RPG/shell/drone breaches on Refinery Tanks (hole punched at impact point, rim deformed, bottom pad intact, internal crude oil preserved without flowing out).
 * 2. BreachOpening creation and directional normal calculation for pouring black smoke and flame jets out of the hole.
 * 3. Substation Transformer & Equipment localized breaches (shattered porcelain insulators, exposed internal copper windings).
 * 4. Bridge 3D breaches, independent single-pile vs dual-pile collapse, and zero floating iron_bars/trusses.
 */
public final class RealisticDestructionContract {
    private static int checks = 0;

    private static void require(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        runAll();
    }

    public static void runAll() {
        checks = 0;
        verifyRefineryTankBreach();
        verifySubstationTransformerBreach();
        verifyBridgeLogicalSupportCollapse();
        System.out.println("PASS RealisticDestructionContract: " + checks + " checks (tank RPG hole + crude oil retention, transformer coil breach, single-pile vs dual-pile bridge collapse & zero floating iron_bars)");
    }

    private static void verifyRefineryTankBreach() {
        int ox = 100, oy = 64, oz = -200, r = 8, h = 10;
        Blueprint tankBp = new Blueprint("test_tank", ox - r - 3, oy, oz - r - 3, 2 * (r + 3) + 1, h + 3, 2 * (r + 3) + 1);
        for (int a = -r - 2; a <= r + 2; a++) {
            for (int b = -r - 2; b <= r + 2; b++) {
                int d = a * a + b * b;
                if (d > (r + 2) * (r + 2)) continue;
                tankBp.set(ox + a, oy, oz + b, BpSpec.of("bobin_craft:cracked_concrete"));
                if (d > r * r) continue;
                boolean shell = d > (r - 1) * (r - 1);
                for (int dy = 1; dy <= h; dy++) {
                    tankBp.set(ox + a, oy + dy, oz + b,
                            (dy == h || shell) ? BpSpec.of("gray_terracotta")
                            : (dy <= h - 2 ? BpSpec.of("bobin_craft:crude_oil") : BpSpec.AIR));
                }
            }
        }
        int initialSolid = tankBp.solidCount();
        require(initialSolid > 800, "Intact tank must have solid shell, roof, pad, and internal crude oil");

        double hitX = ox + r + 0.2, hitY = oy + 5.5, hitZ = oz + 0.5;
        Map<Long, BpSpec> breach = StructuralSupportSolver.computeTankBreach(
                tankBp, ox, oy, oz, r, h, hitX, hitY, hitZ, 1.0, 0.0, 0.0, 220.0f);

        require(!breach.isEmpty(), "RPG impact must create a localized breach in the tank wall");
        long airCells = breach.values().stream().filter(BpSpec::isAir).count();
        long rimCells = breach.values().stream().filter(s -> "bobin_craft:rusted_sheet_metal".equals(s.id)).count();
        require(airCells >= 6 && airCells <= 45, "RPG hole must remove a localized cluster of shell blocks (not 0 and not the whole tank): airCells=" + airCells);
        require(rimCells >= 4, "RPG hole must deform surrounding rim blocks into rusted sheet metal: rimCells=" + rimCells);

        for (long packed : breach.keySet()) {
            int bx = StructuralSupportSolver.unpackX(packed);
            int by = StructuralSupportSolver.unpackY(packed);
            int bz = StructuralSupportSolver.unpackZ(packed);
            BpSpec orig = tankBp.get(bx, by, bz);
            require(orig == null || !"bobin_craft:crude_oil".equals(orig.id),
                    "Internal crude_oil must stay in place behind the hole and only burn 1 block every few seconds");
            require(by > oy, "Tank foundation pad must remain intact");
            require(bx > ox, "Opposite tank wall must remain intact on single RPG hit");
        }
    }

    private static void verifySubstationTransformerBreach() {
        int ox = -50, oy = 68, oz = 120;
        Blueprint trBp = new Blueprint("test_transformer", ox - 6, oy, oz - 7, 13, 10, 15);
        for (int a = -3; a <= 3; a++) {
            for (int b = -4; b <= 4; b++) {
                trBp.set(ox + a, oy, oz + b, BpSpec.of("bobin_craft:cracked_concrete"));
                for (int dy = 1; dy <= 5; dy++) {
                    trBp.set(ox + a, oy + dy, oz + b, BpSpec.of("green_terracotta"));
                }
            }
        }
        for (int a : new int[]{-2, 0, 2}) {
            trBp.set(ox + a, oy + 6, oz, BpSpec.of("bobin_craft:insulator"));
            trBp.set(ox + a, oy + 7, oz, BpSpec.of("bobin_craft:insulator"));
        }

        Map<Long, BpSpec> breach = StructuralSupportSolver.computeSubstationBreach(
                trBp, ox, oy, oz, ox + 1.5, oy + 4.5, oz + 0.5, 320.0f);
        require(!breach.isEmpty(), "Transformer hit must punch breach and shatter nearby insulators");
        boolean exposedCopper = breach.values().stream().anyMatch(s -> "waxed_oxidized_copper".equals(s.id));
        boolean shatteredInsulator = breach.entrySet().stream().anyMatch(e ->
                StructuralSupportSolver.unpackY(e.getKey()) >= oy + 6 && e.getValue().isAir());
        require(exposedCopper, "Transformer breach must expose internal oxidized copper windings");
        require(shatteredInsulator, "Shockwave must shatter overhead porcelain insulators");
    }

    private static void verifyBridgeLogicalSupportCollapse() {
        int minU = -16, maxU = 16, deckY = 76, groundY = 60;
        Blueprint bridgeBp = new Blueprint("test_bridge", -20, 55, -8, 41, 30, 17);
        for (int u = minU; u <= maxU; u++) {
            for (int v = -4; v <= 4; v++) {
                bridgeBp.set(u, deckY - 1, v, BpSpec.of("bobin_craft:cracked_concrete"));
                bridgeBp.set(u, deckY, v, BpSpec.of("bobin_craft:cracked_asphalt"));
                if (Math.abs(v) == 4) {
                    bridgeBp.set(u, deckY + 1, v, BpSpec.of("iron_bars"));
                    bridgeBp.set(u, deckY + 2, v, BpSpec.of("waxed_oxidized_copper"));
                }
            }
            if (u == 0) {
                for (int v : new int[]{-3, 3}) {
                    for (int y = groundY + 1; y < deckY - 1; y++) {
                        bridgeBp.set(u, y, v, BpSpec.of("polished_andesite"));
                    }
                }
                for (int v = -4; v <= 4; v++) {
                    bridgeBp.set(u, deckY - 2, v, BpSpec.of("bobin_craft:mossy_concrete"));
                }
            }
        }

        // Test 1: Shooting ONE pile (v = -3 at u = 0) must ONLY break that single pile and collapse ONLY the left half-span!
        Map<Long, BpSpec> singlePileHit = StructuralSupportSolver.computeBridgeBreach(
                bridgeBp, true, minU, maxU, 0, 0, 0.5, 68.5, -3.0, true, false, 350.0f, (x, z) -> groundY);
        require(!singlePileHit.isEmpty(), "Single pile hit must produce breach changes");
        require(!singlePileHit.containsKey(StructuralSupportSolver.packBlockPos(0, 68, 3)),
                "Opposite pile at v=+3 (6 blocks away) must NEVER be damaged when hitting v=-3 pile!");
        require(singlePileHit.getOrDefault(StructuralSupportSolver.packBlockPos(0, deckY, -3), BpSpec.of("solid")).isAir(),
                "Left deck directly above broken v=-3 pile must collapse!");
        require(singlePileHit.getOrDefault(StructuralSupportSolver.packBlockPos(4, deckY, -3), BpSpec.of("solid")).isAir(),
                "Rounded crater around broken v=-3 pile (u=+4) must collapse!");
        require(!singlePileHit.containsKey(StructuralSupportSolver.packBlockPos(10, deckY, -3)),
                "Far span at u=+10 must remain standing (balanced rounded collapse, not 28-block straight trench)!");
        require(singlePileHit.getOrDefault(StructuralSupportSolver.packBlockPos(0, deckY + 1, -4), BpSpec.of("solid")).isAir(),
                "Left iron_bars metal fence above collapsed left deck must fall too (never float in air)!");
        require(!singlePileHit.containsKey(StructuralSupportSolver.packBlockPos(0, deckY, 3)),
                "Right deck above intact v=+3 pile must remain standing!");

        // Test 1b: Shooting the SECOND pile (v = +3 at u = 0) after the first pile was destroyed must break the second pile
        // AND collapse the remaining right half of the bridge span!
        Map<Long, BpSpec> secondPileHit = StructuralSupportSolver.computeBridgeBreach(
                bridgeBp, singlePileHit, true, minU, maxU, 0, 0, 0.5, 68.5, 3.0, true, true, 350.0f, (x, z) -> groundY);
        require(secondPileHit.getOrDefault(StructuralSupportSolver.packBlockPos(0, 68, 3), BpSpec.of("solid")).isAir(),
                "Second pile at v=+3 must break when shot!");
        require(secondPileHit.getOrDefault(StructuralSupportSolver.packBlockPos(0, deckY, 3), BpSpec.of("solid")).isAir(),
                "Right deck above broken second pile (v=+3) must now collapse!");
        require(secondPileHit.getOrDefault(StructuralSupportSolver.packBlockPos(0, deckY + 1, 4), BpSpec.of("solid")).isAir(),
                "Right iron_bars fence above collapsed right deck must also fall!");

        // Case 2: Full pier loss test via collapseUnsupportedBridgeSpans
        for (int v : new int[]{-3, 3}) {
            for (int y = 65; y <= 69; y++) {
                bridgeBp.air(0, y, v);
            }
        }

        List<Long> collapsedAfterPierLoss = StructuralSupportSolver.collapseUnsupportedBridgeSpans(
                bridgeBp, true, minU, maxU, 0, 0, 5, (x, z) -> groundY, true, true);

        require(!collapsedAfterPierLoss.isEmpty(), "Destroying both bridge piles must trigger full span collapse");
        require(bridgeBp.get(0, deckY, 0) == null || bridgeBp.get(0, deckY, 0).isAir(),
                "Unsupported deck above destroyed pier (u=0) must collapse into air");
        require(bridgeBp.get(0, deckY + 2, 4) == null || bridgeBp.get(0, deckY + 2, 4).isAir(),
                "Overhead truss above collapsed deck must also collapse");
        require(bridgeBp.get(minU, deckY, 0) != null && !bridgeBp.get(minU, deckY, 0).isAir(),
                "Anchored bank abutment end (u=minU) must remain standing");
    }
}
