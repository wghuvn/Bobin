package com.bobincraft.drone.client;

/** Momentary actions survive an incidental screen overlay until the flight tick sends them. */
final class DroneActionQueue {
    private int pending;

    void add(int flag) { pending |= flag; }

    int drain() {
        int result = pending;
        pending = 0;
        return result;
    }
}
