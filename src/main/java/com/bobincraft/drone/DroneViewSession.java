package com.bobincraft.drone;

import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

/**
 * System B (Client Remote View Streaming) — represents a player's active remote drone camera session,
 * decoupling the client's chunk/entity view center from the operator's physical character body (Section 5.5).
 */
public record DroneViewSession(
        UUID playerUuid,
        UUID droneUuid,
        int droneEntityId,
        ChunkPos playerChunk,
        ChunkPos droneChunk,
        ChunkPos clientViewCenter,
        int viewRadius,
        int serverLoadedChunks,
        int clientStreamedChunks,
        int pendingChunkRequests,
        int activeDroneTickets,
        double lastChunkServiceMs
) {
    public boolean isRemoteFromPlayer() {
        return !playerChunk.equals(droneChunk);
    }
}
