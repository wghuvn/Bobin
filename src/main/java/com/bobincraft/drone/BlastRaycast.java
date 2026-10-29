package com.bobincraft.drone;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Short, bounded traces. Glass attenuates fragments; solid cover and unloaded chunks stop them. */
public final class BlastRaycast {
    private static final int MAX_GLASS_LAYERS = 8;

    private BlastRaycast() { }

    public static double transmission(ServerWorld world, Entity source, Vec3d start, Vec3d end) {
        return transmission(start, end, pos -> world.isInBuildLimit(pos) && world.isChunkLoaded(pos),
                (from, to) -> world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, source)),
                pos -> world.getBlockState(pos).isIn(BlastTerrain.SHATTERABLE_GLASS));
    }

    /** The same trace algorithm with a collision provider, also usable in native-shape unit tests. */
    static double transmission(Vec3d start, Vec3d end, Predicate<BlockPos> loaded,
                               BiFunction<Vec3d, Vec3d, BlockHitResult> raycast, Predicate<BlockPos> glass) {
        double length = start.distanceTo(end);
        int steps = Math.max(1, (int) Math.ceil(length / 0.5));
        for (int i = 0; i <= steps; i++) {
            BlockPos sample = BlockPos.ofFloored(start.lerp(end, (double) i / steps));
            if (!loaded.test(sample)) return 0;
        }
        if (length < 1.0e-5) return 1;
        Vec3d direction = end.subtract(start).normalize();
        Vec3d cursor = start;
        double energy = 1;
        for (int layer = 0; layer <= MAX_GLASS_LAYERS; layer++) {
            var hit = raycast.apply(cursor, end);
            if (hit.getType() == HitResult.Type.MISS) return energy;
            if (!glass.test(hit.getBlockPos()) || layer == MAX_GLASS_LAYERS) return 0;
            energy *= 0.72;
            // Exit the entire glass cell, including thick glass, without skipping a wall behind it.
            BlockPos pos = hit.getBlockPos();
            Vec3d p = hit.getPos();
            double advance = Double.POSITIVE_INFINITY;
            double[] origin = {p.x, p.y, p.z}, delta = {direction.x, direction.y, direction.z};
            int[] block = {pos.getX(), pos.getY(), pos.getZ()};
            for (int axis = 0; axis < 3; axis++) if (Math.abs(delta[axis]) > 1.0e-9) {
                double boundary = block[axis] + (delta[axis] > 0 ? 1 : 0);
                advance = Math.min(advance, Math.max(0, (boundary - origin[axis]) / delta[axis]));
            }
            cursor = p.add(direction.multiply(advance + 0.001));
            if (cursor.subtract(start).dotProduct(direction) >= length) return energy;
        }
        return 0;
    }
}
