package com.bobincraft.drone;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

import java.util.*;

/**
 * System A (Server Drone Chunk Loading) — manages active server chunk tickets around each flying drone
 * and guarantees complete cleanup on session end, drone destruction, player disconnect, or server stop (Section 5.4 & 5.6).
 */
public final class DroneChunkTicketManager {
    public static int activeRadius = 2; // 5x5 active entity/physics area around the drone
    public static int maxTicketsPerDrone = 256;

    public record TicketAllocation(
            UUID ownerUuid,
            ChunkPos droneChunk,
            ChunkPos leadChunk,
            Set<Long> activeTicketKeys,
            Set<Long> viewTicketKeys,
            long updatedAtTick
    ) {
        public int totalTicketCount() {
            return activeTicketKeys.size() + viewTicketKeys.size();
        }
    }

    private static final Map<UUID, TicketAllocation> ALLOCATIONS = new HashMap<>();

    private DroneChunkTicketManager() {}

    /**
     * Computes the 5x5 (radius 2) active simulation chunk grid around the drone's current chunk.
     */
    public static LongArrayList computeActiveWindow(int centerChunkX, int centerChunkZ, int radius) {
        int r = MathHelper.clamp(radius, 1, 4);
        LongArrayList list = new LongArrayList((2 * r + 1) * (2 * r + 1));
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                list.add(ChunkPos.toLong(centerChunkX + dx, centerChunkZ + dz));
            }
        }
        return list;
    }

    /**
     * Computes the 5x5 (radius 2) active simulation chunk set around the drone's current chunk.
     */
    public static LongOpenHashSet computeActiveWindowSet(int centerChunkX, int centerChunkZ, int radius) {
        return new LongOpenHashSet(computeActiveWindow(centerChunkX, centerChunkZ, radius));
    }

    public static synchronized void recordAllocation(UUID ownerUuid,
                                                     ChunkPos droneChunk,
                                                     ChunkPos leadChunk,
                                                     LongOpenHashSet activeTickets,
                                                     LongOpenHashSet viewTickets,
                                                     long tick) {
        if (ownerUuid == null) return;
        Set<Long> act = new LinkedHashSet<>(activeTickets.size());
        LongIterator actIt = activeTickets.iterator();
        while (actIt.hasNext()) {
            act.add(actIt.nextLong());
        }
        Set<Long> vw = new LinkedHashSet<>(viewTickets.size());
        LongIterator vwIt = viewTickets.iterator();
        while (vwIt.hasNext()) {
            vw.add(vwIt.nextLong());
        }
        ALLOCATIONS.put(ownerUuid, new TicketAllocation(ownerUuid, droneChunk, leadChunk,
                Collections.unmodifiableSet(act), Collections.unmodifiableSet(vw), tick));
    }

    public static synchronized void releaseAllocation(UUID ownerUuid) {
        if (ownerUuid == null) return;
        ALLOCATIONS.remove(ownerUuid);
    }

    public static synchronized void clearAll() {
        ALLOCATIONS.clear();
    }

    public static synchronized Optional<TicketAllocation> allocationFor(UUID ownerUuid) {
        return Optional.ofNullable(ALLOCATIONS.get(ownerUuid));
    }

    public static synchronized int activeSessionCount() {
        return ALLOCATIONS.size();
    }

    public static synchronized int totalActiveTickets() {
        int sum = 0;
        for (TicketAllocation alloc : ALLOCATIONS.values()) {
            sum += alloc.totalTicketCount();
        }
        return sum;
    }
}
