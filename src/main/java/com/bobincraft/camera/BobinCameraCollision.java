package com.bobincraft.camera;

import com.bobincraft.vehicle.ArmorGeometry;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.EntityView;

import java.util.List;
import java.util.Optional;

/**
 * Tank and third-person camera collision helpers against terrain and armored vehicle colliders.
 */
public final class BobinCameraCollision {

    private static final double HALF = 0.30;
    private static final double SKIN = 0.05;

    private BobinCameraCollision() {}

    /**
     * War Thunder third-person tank camera collision:
     * Clips [origin -> desired] against solid terrain blocks and OTHER vehicles, while completely ignoring
     * the player's own ridden tank so the camera is never pulled inside the turret or hull.
     */
    public static Vec3d clipTankThirdPerson(BlockView world, net.minecraft.entity.Entity riddenTank, Vec3d origin, Vec3d desired) {
        Vec3d delta = desired.subtract(origin);
        double len = delta.length();
        if (len < 1.0e-4) return desired;
        double best = len;

        BlockPos startBlock = BlockPos.ofFloored(origin);
        BlockHitResult blockHit = BlockView.raycast(origin, desired, Boolean.TRUE, (ctx, p) -> {
            if (p.equals(startBlock)) return null;
            BlockState st = world.getBlockState(p);
            if (!blocksCamera(st, world, p)) return null;
            VoxelShape shape = st.getCollisionShape(world, p);
            if (shape.isEmpty()) return null;
            return shape.raycast(origin, desired, p);
        }, ctx -> null);
        if (blockHit != null) {
            double d = origin.distanceTo(blockHit.getPos());
            if (d < best) best = d;
        }

        if (world instanceof EntityView ev) {
            List<VoxelShape> otherArmor = ArmorGeometry.entityCollisions(ev, riddenTank, new Box(origin, desired).expand(HALF + 0.1));
            if (otherArmor != null) {
                for (VoxelShape s : otherArmor) {
                    for (Box b : s.getBoundingBoxes()) {
                        if (b.expand(0.02).contains(origin)) continue;
                        Optional<Vec3d> hit = b.expand(0.12).raycast(origin, desired);
                        if (hit.isPresent()) {
                            double d = origin.distanceTo(hit.get());
                            if (d < best) best = d;
                        }
                    }
                }
            }
        }

        if (best >= len) return desired;
        double allowed = Math.max(0.35, best - SKIN);
        return origin.add(delta.multiply(allowed / len));
    }

    /** Clips a third-person camera segment [origin -> desired] against any tank colliders that do not already contain origin. */
    public static Vec3d clipToArmor(BlockView world, Vec3d origin, Vec3d desired) {
        if (!(world instanceof EntityView ev)) return desired;
        Vec3d delta = desired.subtract(origin);
        double len = delta.length();
        if (len < 1.0e-4) return desired;
        List<VoxelShape> armor = ArmorGeometry.entityCollisions(ev, null, new Box(origin, desired).expand(HALF + 0.1));
        if (armor == null || armor.isEmpty()) return desired;
        double best = len;
        for (VoxelShape s : armor) {
            for (Box b : s.getBoundingBoxes()) {
                if (b.expand(0.02).contains(origin)) continue;
                Optional<Vec3d> hit = b.expand(0.12).raycast(origin, desired);
                if (hit.isPresent()) {
                    double d = origin.distanceTo(hit.get());
                    if (d < best) best = d;
                }
            }
        }
        if (best >= len) return desired;
        double allowed = Math.max(0.25, best - SKIN);
        return origin.add(delta.multiply(allowed / len));
    }

    private static boolean blocksCamera(BlockState state, BlockView world, BlockPos p) {
        if (state.isAir() || state.isIn(BlockTags.LEAVES)) return false;
        VoxelShape shape = state.getCollisionShape(world, p);
        if (shape.isEmpty()) return false;
        if (state.isOpaque()) return true;
        Box b = shape.getBoundingBox();
        return b.getXLength() * b.getYLength() * b.getZLength() >= 0.4;
    }
}
