package com.bobincraft.drone;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System B (Client Remote View Streaming) — tracks active {@link DroneViewSession} instances and telemetry
 * for independent client chunk/entity streaming around distant drones without moving the operator's body (Section 5.5).
 */
public final class DroneViewStreamingManager {
    private static final Map<UUID, DroneViewSession> SESSIONS = new ConcurrentHashMap<>();

    private DroneViewStreamingManager() {}

    public static void updateSession(DroneViewSession session) {
        if (session == null || session.playerUuid() == null) return;
        SESSIONS.put(session.playerUuid(), session);
    }

    public static void removeSession(UUID playerUuid) {
        if (playerUuid == null) return;
        SESSIONS.remove(playerUuid);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    public static Optional<DroneViewSession> getSession(UUID playerUuid) {
        if (playerUuid == null) return Optional.empty();
        return Optional.ofNullable(SESSIONS.get(playerUuid));
    }

    public static Collection<DroneViewSession> activeSessions() {
        return Collections.unmodifiableCollection(SESSIONS.values());
    }

    public static int activeSessionCount() {
        return SESSIONS.size();
    }
}
