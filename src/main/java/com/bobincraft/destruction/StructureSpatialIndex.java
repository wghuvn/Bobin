package com.bobincraft.destruction;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial index mapping ChunkPos (packed as long) to the list of DestructibleStructures
 * intersecting that chunk (Section 4.1).
 * Avoids global O(N) iteration over all world structures on every impact or explosion.
 */
public final class StructureSpatialIndex {
    private final Map<Long, List<DestructibleStructure>> chunkBuckets = new ConcurrentHashMap<>();
    private final Map<String, DestructibleStructure> byId = new ConcurrentHashMap<>();

    public void clear() {
        chunkBuckets.clear();
        byId.clear();
    }

    public void register(DestructibleStructure structure) {
        if (structure == null) return;
        byId.put(structure.structureId(), structure);
        Box bounds = structure.overallBounds().expand(2.0);
        int minCx = MathHelper.floor(bounds.minX) >> 4;
        int maxCx = MathHelper.floor(bounds.maxX) >> 4;
        int minCz = MathHelper.floor(bounds.minZ) >> 4;
        int maxCz = MathHelper.floor(bounds.maxZ) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long key = ChunkPos.toLong(cx, cz);
                chunkBuckets.compute(key, (k, existing) -> {
                    List<DestructibleStructure> list = existing == null ? new ArrayList<>(4) : new ArrayList<>(existing);
                    list.removeIf(s -> s.structureId().equals(structure.structureId()));
                    list.add(structure);
                    return Collections.unmodifiableList(list);
                });
            }
        }
    }

    public void unregister(String structureId) {
        DestructibleStructure removed = byId.remove(structureId);
        if (removed == null) return;
        Box bounds = removed.overallBounds().expand(2.0);
        int minCx = MathHelper.floor(bounds.minX) >> 4;
        int maxCx = MathHelper.floor(bounds.maxX) >> 4;
        int minCz = MathHelper.floor(bounds.minZ) >> 4;
        int maxCz = MathHelper.floor(bounds.maxZ) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long key = ChunkPos.toLong(cx, cz);
                chunkBuckets.computeIfPresent(key, (k, existing) -> {
                    List<DestructibleStructure> list = new ArrayList<>(existing);
                    list.removeIf(s -> s.structureId().equals(structureId));
                    return list.isEmpty() ? null : Collections.unmodifiableList(list);
                });
            }
        }
    }

    public List<DestructibleStructure> getInChunk(int chunkX, int chunkZ) {
        return chunkBuckets.getOrDefault(ChunkPos.toLong(chunkX, chunkZ), List.of());
    }

    public List<DestructibleStructure> getInChunk(ChunkPos pos) {
        return getInChunk(pos.x, pos.z);
    }

    public List<DestructibleStructure> getAtPosition(Vec3d impactPos) {
        int cx = MathHelper.floor(impactPos.x) >> 4;
        int cz = MathHelper.floor(impactPos.z) >> 4;
        List<DestructibleStructure> candidates = getInChunk(cx, cz);
        if (candidates.isEmpty()) return List.of();
        List<DestructibleStructure> hits = new ArrayList<>(2);
        for (DestructibleStructure candidate : candidates) {
            if (candidate.hitZoneAt(impactPos, 0.75) != DestructibleStructure.HitZone.NONE) {
                hits.add(candidate);
            }
        }
        return hits;
    }

    public DestructibleStructure findPrimaryAt(Vec3d impactPos) {
        int cx = MathHelper.floor(impactPos.x) >> 4;
        int cz = MathHelper.floor(impactPos.z) >> 4;
        DestructibleStructure best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (DestructibleStructure candidate : getInChunk(cx + dx, cz + dz)) {
                    if (candidate.hitZoneAt(impactPos, 1.25) != DestructibleStructure.HitZone.NONE) {
                        double d = candidate.originPosition().getSquaredDistance(impactPos.x, impactPos.y, impactPos.z);
                        if (d < bestDist) {
                            bestDist = d;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    public List<DestructibleStructure> getIntersectingSphere(Vec3d center, double radius) {
        int minCx = MathHelper.floor(center.x - radius - 1.0) >> 4;
        int maxCx = MathHelper.floor(center.x + radius + 1.0) >> 4;
        int minCz = MathHelper.floor(center.z - radius - 1.0) >> 4;
        int maxCz = MathHelper.floor(center.z + radius + 1.0) >> 4;
        LinkedHashMap<String, DestructibleStructure> found = new LinkedHashMap<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                for (DestructibleStructure structure : getInChunk(cx, cz)) {
                    if (!found.containsKey(structure.structureId()) && structure.intersectsSphere(center, radius)) {
                        found.put(structure.structureId(), structure);
                    }
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    public DestructibleStructure findNearest(Vec3d pos, double maxDistance) {
        int minCx = MathHelper.floor(pos.x - maxDistance) >> 4;
        int maxCx = MathHelper.floor(pos.x + maxDistance) >> 4;
        int minCz = MathHelper.floor(pos.z - maxDistance) >> 4;
        int maxCz = MathHelper.floor(pos.z + maxDistance) >> 4;
        DestructibleStructure best = null;
        double bestDist = maxDistance;
        Set<String> seen = new HashSet<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                for (DestructibleStructure s : getInChunk(cx, cz)) {
                    if (!seen.add(s.structureId())) continue;
                    double d = s.distanceToSurface(pos);
                    if (d <= bestDist) {
                        bestDist = d;
                        best = s;
                    }
                }
            }
        }
        return best;
    }

    public int chunkBucketCount() {
        return chunkBuckets.size();
    }

    public int structureCount() {
        return byId.size();
    }

    public Collection<DestructibleStructure> allStructures() {
        return Collections.unmodifiableCollection(byId.values());
    }
}
