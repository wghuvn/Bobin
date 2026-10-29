package com.bobincraft.raid.guard;

/** Square courtyards and legacy circular territories share a strict finite movement boundary. */
public final class GuardTerritory {
    private GuardTerritory() {}
    public static boolean contains(int centerX, int centerZ, int radius, boolean square, int x, int z) {
        double dx = (double) x - centerX, dz = (double) z - centerZ;
        return square ? Math.abs(dx) <= radius && Math.abs(dz) <= radius : dx * dx + dz * dz <= (double) radius * radius;
    }
}
