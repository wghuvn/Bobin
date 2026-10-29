package com.bobincraft.combat;

import com.bobincraft.drone.DroneBlastType;
import java.io.IOException;
import java.util.*;

/** Runs both through JUnit and directly with Java 17 when Gradle dependencies are unavailable. */
public final class ExplosionVisualContract {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(byte[] bytes) {
        try { ExplosionVisualEvent.decode(bytes); throw new AssertionError("Accepted malformed packet"); }
        catch (IOException expected) { assertions++; }
    }
    public static void runAll() throws Exception {
        assertions = 0;
        var window = new ExplosionVisualEventWindow(512);
        var first = new UUID(0, 1);
        check(window.accept(first), "First event accepted");
        check(!window.accept(first), "Duplicate suppressed");
        for (int i = 2; i <= 1024; i++) window.accept(new UUID(0, i));
        check(window.size() == 512, "Replay cache bounded");
        window.clear();
        check(window.accept(first), "World reset clears replay cache");
        var ids = new HashSet<String>();
        check(ExplosionVisualProfile.values().length == 12, "Twelve weapon categories");
        for (var p : ExplosionVisualProfile.values()) {
            check(ids.add(p.id), "Unique stable profile ID");
            check(ExplosionVisualProfile.find(p.id) == p, "ID lookup");
            var event = new ExplosionVisualEvent(new UUID(12, 34), "bobin_craft:warzone", p.id,
                    12.5, 64, -98.1, 42, false, 1, 12.5, 63, -98.1);
            byte[] wire = event.encode();
            check(wire.length <= ExplosionVisualEvent.MAX_BYTES, "Packet limit");
            check(event.equals(ExplosionVisualEvent.decode(wire)), "Lossless wire roundtrip");
            for (int n = 0; n < wire.length; n++) rejects(Arrays.copyOf(wire, n));
            byte[] trailing = Arrays.copyOf(wire, wire.length + 1); rejects(trailing);
            byte[] wrongVersion = wire.clone(); wrongVersion[0] = 127; rejects(wrongVersion);
            for (int budget : new int[]{0, 1, 10, 64, 1000}) {
                var a = VoxelExplosionShape.createWeapon(p, 42, budget);
                check(a.equals(VoxelExplosionShape.createWeapon(p, 42, budget)), "Deterministic seeded shape");
                check(a.size() <= budget && a.size() <= p.smokeCount, "Honours budget, including zero");
                for (var puff : a) {
                    check(puff.mode() == VoxelExplosionShape.MODE_STANDARD, "No industrial mode");
                    check(puff.life() > 0 && puff.life() <= 101, "Transient lifetime <= 5.05 seconds");
                    check(Double.isFinite(puff.vx() + puff.vy() + puff.vz()), "Finite motion");
                }
            }
        }
        var expected = new ExplosionVisualProfile[]{ExplosionVisualProfile.TANK_HE, ExplosionVisualProfile.DROPPED_GRENADE,
                ExplosionVisualProfile.FPV_STANDARD, ExplosionVisualProfile.FPV_FRAGMENTATION, ExplosionVisualProfile.FPV_BREACHING,
                ExplosionVisualProfile.BOMBER_HE, ExplosionVisualProfile.BOMBER_FRAGMENTATION,
                ExplosionVisualProfile.BOMBER_HEAVY, ExplosionVisualProfile.FP1_STRIKE};
        for (int i = 0; i < expected.length; i++) check(ExplosionVisualProfile.select(DroneBlastType.values()[i], false, false) == expected[i], "Gameplay mapping");
        check(ExplosionVisualProfile.select(DroneBlastType.KAMIKAZE_BREACH, true, false) == ExplosionVisualProfile.RPG_IMPACT, "RPG identity");
        check(ExplosionVisualProfile.select(DroneBlastType.DROPPED_GRENADE, false, true) == ExplosionVisualProfile.HAND_GRENADE, "Hand grenade identity");
        check(ExplosionVisualProfile.find("other:future") == null, "Unknown profile uses neutral fallback");
        var unknown = new ExplosionVisualEvent(UUID.randomUUID(), "minecraft:overworld", "other:future", 0, 0, 0, 1, true, -1, 0, 0, 0);
        check(unknown.equals(ExplosionVisualEvent.decode(unknown.encode())), "Forward profile preserved for safe handling");
        rejects(new byte[513]);
        try {
            new ExplosionVisualEvent(UUID.randomUUID(), "minecraft:overworld", "invalid profile", 0, 0, 0, 0, false, -1, 0, 0, 0);
            throw new AssertionError("Invalid ID accepted");
        } catch (IllegalArgumentException expectedFailure) { assertions++; }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 30_000_001}) {
            try {
                new ExplosionVisualEvent(UUID.randomUUID(), "minecraft:overworld", "other:future", invalid, 0, 0, 0, false, -1, 0, 0, 0);
                throw new AssertionError("Invalid coordinate accepted");
            } catch (IllegalArgumentException expectedFailure) { assertions++; }
        }
        System.out.println("PASS: " + assertions + " assertions; all 12 categories, protocol rejection, seeded geometry and budgets");
    }
    public static void main(String[] args) throws Exception { runAll(); }
}
