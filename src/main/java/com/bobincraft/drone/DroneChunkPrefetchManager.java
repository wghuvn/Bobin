package com.bobincraft.drone;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * System A (Directional Prefetch) — calculates forward chunk prefetch coordinates along the drone's
 * velocity vector so high-speed FPV and fixed-wing drones never fly into unloaded walls (Section 5.4).
 */
public final class DroneChunkPrefetchManager {
    public static final int DEFAULT_LEAD_TICKS = 55;
    public static final int MAX_LEAD_CHUNKS = 8;

    private DroneChunkPrefetchManager() {}

    /**
     * Computes the lead chunk center ahead of {@code position} given {@code velocity} (blocks/tick).
     */
    public static ChunkPos computeLeadCenter(Vec3d position, Vec3d velocity, int leadTicks, int maxLeadChunks) {
        int cx = MathHelper.floor(position.x) >> 4;
        int cz = MathHelper.floor(position.z) >> 4;
        if (velocity == null || velocity.horizontalLengthSquared() < 1e-4) {
            return new ChunkPos(cx, cz);
        }
        int clampedTicks = Math.max(10, Math.min(120, leadTicks));
        int clampedMax = Math.max(1, Math.min(12, maxLeadChunks));
        int tx = MathHelper.clamp(MathHelper.floor(position.x + velocity.x * clampedTicks) >> 4, cx - clampedMax, cx + clampedMax);
        int tz = MathHelper.clamp(MathHelper.floor(position.z + velocity.z * clampedTicks) >> 4, cz - clampedMax, cz + clampedMax);
        return new ChunkPos(tx, tz);
    }

    /**
     * Computes the ordered corridor of chunks from the drone's current chunk toward the lead center,
     * expanding by {@code corridorRadius} around the flight vector.
     */
    public static LongArrayList computePrefetchCorridor(Vec3d position, Vec3d velocity, int corridorRadius) {
        int cx = MathHelper.floor(position.x) >> 4;
        int cz = MathHelper.floor(position.z) >> 4;
        ChunkPos lead = computeLeadCenter(position, velocity, DEFAULT_LEAD_TICKS, MAX_LEAD_CHUNKS);
        LongArrayList result = new LongArrayList();
        int r = MathHelper.clamp(corridorRadius, 1, 5);
        int steps = Math.max(1, Math.max(Math.abs(lead.x - cx), Math.abs(lead.z - cz)));
        for (int s = 1; s <= steps; s++) {
            int sx = Math.round(cx + (lead.x - cx) * (s / (float) steps));
            int sz = Math.round(cz + (lead.z - cz) * (s / (float) steps));
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    long key = ChunkPos.toLong(sx + dx, sz + dz);
                    if (!result.contains(key)) {
                        result.add(key);
                    }
                }
            }
        }
        return result;
    }
}
