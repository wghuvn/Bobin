package com.bobincraft.raid;

/** Pure countdown policy. Epoch deadlines survive reconnects and server restarts. */
final class HubDesertionPolicy {
    static final long COUNTDOWN_MILLIS = 10_000;
    static final long BOMB_FALL_MILLIS = 1_500;
    private long deadline;
    private boolean struck;

    HubDesertionPolicy() { }

    HubDesertionPolicy(long deadline, boolean struck) {
        this.deadline = Math.max(0, deadline);
        this.struck = struck;
    }

    static boolean shouldWarn(boolean exempt, boolean insideHub,
                              boolean activeRaid, boolean inWarzone) {
        // Holding or opening a map never authorizes departure; only a successful deployment does.
        // Raid onboarding is independent of perimeter enforcement. A new or
        // restored player must not become immune because no raid profile exists.
        return !exempt && !insideHub && !(activeRaid && inWarzone);
    }

    static boolean inside(double x, double y, double z, int radius, int floorY) {
        // Block columns -radius..radius occupy [-radius, radius + 1).
        return x >= -radius && x < radius + 1 && z >= -radius && z < radius + 1 && y >= floorY - 2;
    }

    boolean begin(long now) {
        if (deadline != 0 || struck) return false;
        deadline = now + COUNTDOWN_MILLIS;
        return true;
    }

    boolean cancel() {
        boolean changed = deadline != 0 || struck;
        deadline = 0;
        struck = false;
        return changed;
    }

    boolean strike(long now) {
        if (struck || deadline == 0 || now < deadline) return false;
        struck = true;
        return true;
    }

    long remaining(long now) { return deadline == 0 ? 0 : Math.max(0, deadline - now); }
    int seconds(long now) { return (int) ((remaining(now) + 999) / 1_000); }
    long deadline() { return deadline; }
    boolean struck() { return struck; }
    boolean active() { return deadline != 0 && !struck; }
}
