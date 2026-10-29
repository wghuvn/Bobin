package com.bobincraft.weapon.grom7;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

/**
 * Exact VoxelShape-aware placement calculator for the GROM-7 remote demolition charge (Section 10.5).
 *
 * <p>Instead of assuming every block is a full {@code 1x1x1} cube, this calculator inspects the actual
 * {@link VoxelShape} sub-boxes of the target block (such as thin iron bars, wooden fence posts, walls,
 * slabs, stair steps, trapdoors, ceilings, and custom Bobin Craft blocks), snaps the charge's backplane
 * directly to the hit sub-box face with a tiny {@code 0.0025} anti-Z-fighting offset (zero air gap and
 * zero penetration into the block), centers on thin posts/bars when needed, and verifies that the charge
 * volume does not clip into adjacent solid blocks.
 */
public final class Grom7Placement {
    /** Maximum reach distance for manual Shift + RMB placement (blocks). */
    public static final double MAX_ATTACH_DISTANCE = 3.75;
    /** Tiny offset along the surface normal so the charge sits flush without Z-fighting. */
    public static final double SURFACE_EPSILON = 0.0025;
    /** Charge body dimensions in blocks: width (2.8/16), thickness/height (1.4/16), length (4.0/16). */
    public static final double CHARGE_HALF_WIDTH = 0.0875;     // 0.175 wide
    public static final double CHARGE_THICKNESS = 0.0875;      // 0.0875 thick off the surface
    public static final double CHARGE_HALF_LENGTH = 0.125;     // 0.250 long

    public record PlacementResult(
            boolean valid,
            String reason,
            BlockPos attachmentBlockPos,
            Direction attachmentFace,
            Vec3d attachmentPoint,
            Box chargeBoundingBox,
            float yaw,
            float pitch,
            float roll,
            double surfaceGap
    ) {
        public static PlacementResult invalid(String reason) {
            return new PlacementResult(false, reason, BlockPos.ORIGIN, Direction.UP, Vec3d.ZERO,
                    new Box(0, 0, 0, 0, 0, 0), 0.0f, 0.0f, 0.0f, Double.NaN);
        }
    }

    private Grom7Placement() {}

    /**
     * Convenience overload that validates {@code player} and {@code world} before raycasting from the player's eyes.
     */
    public static PlacementResult raycastAndEvaluate(World world, PlayerEntity player) {
        if (world == null || player == null || player.isRemoved() || !player.isAlive()
                || player.isSpectator() || player.getWorld() != world) {
            return PlacementResult.invalid("invalid_player_or_world");
        }
        return raycastAndEvaluate(world, player, player.getEyePos(), player.getRotationVec(1.0f), player.getYaw());
    }

    /**
     * Raycasts from {@code caster}'s eye along {@code lookDir} up to {@link #MAX_ATTACH_DISTANCE} and evaluates
     * whether a GROM-7 charge can be attached to the hit surface.
     *
     * <p>Requires a non-null, active {@code caster} entity because {@link RaycastContext} delegates to
     * {@link ShapeContext#of(Entity)}, which throws {@link NullPointerException} if given {@code null}.
     */
    public static PlacementResult raycastAndEvaluate(World world,
                                                     Entity caster,
                                                     Vec3d eyePos,
                                                     Vec3d lookDir,
                                                     float playerYaw) {
        if (world == null || caster == null || caster.isRemoved() || !caster.isAlive() || caster.getWorld() != world) {
            return PlacementResult.invalid("invalid_caster_or_world");
        }
        if (eyePos == null || lookDir == null
                || !Double.isFinite(eyePos.x) || !Double.isFinite(eyePos.y) || !Double.isFinite(eyePos.z)
                || !Double.isFinite(lookDir.x) || !Double.isFinite(lookDir.y) || !Double.isFinite(lookDir.z)
                || lookDir.lengthSquared() <= 1.0e-6) {
            return PlacementResult.invalid("invalid_ray_vectors");
        }

        BlockPos startBlockPos = BlockPos.ofFloored(eyePos);
        if (!world.isInBuildLimit(startBlockPos)
                || !world.isChunkLoaded(startBlockPos.getX() >> 4, startBlockPos.getZ() >> 4)) {
            return PlacementResult.invalid("unloaded_chunk");
        }

        Vec3d normLook = lookDir.normalize();
        Vec3d endPos = eyePos.add(normLook.multiply(MAX_ATTACH_DISTANCE));
        BlockPos endBlockPos = BlockPos.ofFloored(endPos);
        if (!world.isChunkLoaded(endBlockPos.getX() >> 4, endBlockPos.getZ() >> 4)) {
            return PlacementResult.invalid("unloaded_chunk");
        }

        // First raycast against OUTLINE shapes (captures exact visible geometry of fences, iron bars, slabs, stairs)
        BlockHitResult hit = world.raycast(new RaycastContext(
                eyePos, endPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, caster));

        // If OUTLINE hit non-solid foliage/grass in front of a real surface, fall back to COLLIDER raycast
        if (hit == null || hit.getType() != HitResult.Type.BLOCK
                || !isAttachableBlock(world.getBlockState(hit.getBlockPos()))
                || resolveAttachableShape(world, hit.getBlockPos(), world.getBlockState(hit.getBlockPos())).isEmpty()) {
            hit = world.raycast(new RaycastContext(
                    eyePos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
        }

        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return PlacementResult.invalid("out_of_range");
        }

        BlockPos hitBlockPos = hit.getBlockPos();
        if (!world.isInBuildLimit(hitBlockPos)
                || !world.isChunkLoaded(hitBlockPos.getX() >> 4, hitBlockPos.getZ() >> 4)) {
            return PlacementResult.invalid("unloaded_chunk");
        }

        BlockState state = world.getBlockState(hitBlockPos);
        VoxelShape attachShape = resolveAttachableShape(world, hitBlockPos, state);
        if (attachShape.isEmpty()) {
            return PlacementResult.invalid("unsuitable_block");
        }

        // Refine hit against the exact attachable VoxelShape (so fence 1.5m collider barrier never causes mid-air hits above 1.0m)
        BlockHitResult refinedHit = attachShape.raycast(eyePos, endPos, hitBlockPos);
        Direction hitSide = refinedHit != null && refinedHit.getType() == HitResult.Type.BLOCK
                ? refinedHit.getSide()
                : hit.getSide();
        Vec3d hitPoint = refinedHit != null && refinedHit.getType() == HitResult.Type.BLOCK
                ? refinedHit.getPos()
                : hit.getPos();

        if (eyePos.squaredDistanceTo(hitPoint) > MAX_ATTACH_DISTANCE * MAX_ATTACH_DISTANCE + 0.05) {
            return PlacementResult.invalid("out_of_range");
        }

        return evaluateBlockHit(world, hitBlockPos, hitSide, hitPoint, playerYaw);
    }

    /**
     * Authoritatively re-validates a placement on the server right before spawning the attached charge,
     * ensuring the player is still within reach, has line of sight, and the target block's VoxelShape is still valid.
     */
    public static PlacementResult revalidateServerPlacement(World world,
                                                            PlayerEntity player,
                                                            PlacementResult candidate) {
        if (world == null || player == null || player.isRemoved() || !player.isAlive()
                || player.isSpectator() || player.getWorld() != world) {
            return PlacementResult.invalid("invalid_player_or_world");
        }
        if (candidate == null || !candidate.valid()) {
            return PlacementResult.invalid("invalid_candidate");
        }
        BlockPos targetPos = candidate.attachmentBlockPos();
        if (targetPos == null || !world.isInBuildLimit(targetPos)
                || !world.isChunkLoaded(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
            return PlacementResult.invalid("unloaded_chunk");
        }
        Vec3d eyePos = player.getEyePos();
        double maxReach = MAX_ATTACH_DISTANCE + 0.35;
        if (eyePos.squaredDistanceTo(candidate.attachmentPoint()) > maxReach * maxReach) {
            return PlacementResult.invalid("out_of_range");
        }
        // Check unobstructed line of sight from player's eyes to the attachment surface point
        Vec3i normal = candidate.attachmentFace().getVector();
        Vec3d probeEnd = candidate.attachmentPoint().add(normal.getX() * 0.05, normal.getY() * 0.05, normal.getZ() * 0.05);
        BlockHitResult losHit = world.raycast(new RaycastContext(
                eyePos, probeEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (losHit != null && losHit.getType() == HitResult.Type.BLOCK && !losHit.getBlockPos().equals(targetPos)) {
            return PlacementResult.invalid("line_of_sight_blocked");
        }
        return evaluateBlockHit(world, targetPos, candidate.attachmentFace(), candidate.attachmentPoint(), player.getYaw());
    }

    /**
     * Evaluates attachment on a specific block, face, and world-space hit position using the block's actual VoxelShape.
     */
    public static PlacementResult evaluateBlockHit(BlockView world,
                                                   BlockPos pos,
                                                   Direction face,
                                                   Vec3d hitPos,
                                                   float playerYaw) {
        if (world == null || pos == null || face == null || hitPos == null) {
            return PlacementResult.invalid("null_args");
        }
        BlockState state = world.getBlockState(pos);
        if (!isAttachableBlock(state)) {
            return PlacementResult.invalid("unsuitable_block");
        }

        VoxelShape shape = resolveAttachableShape(world, pos, state);
        if (shape.isEmpty()) {
            return PlacementResult.invalid("empty_shape");
        }

        List<Box> localBoxes = shape.getBoundingBoxes();
        if (localBoxes.isEmpty()) {
            return PlacementResult.invalid("empty_shape");
        }

        double localHitX = MathHelper.clamp(hitPos.x - pos.getX(), 0.0, 1.0);
        double localHitY = MathHelper.clamp(hitPos.y - pos.getY(), 0.0, 1.0);
        double localHitZ = MathHelper.clamp(hitPos.z - pos.getZ(), 0.0, 1.0);

        // Select the exact VoxelShape sub-box whose face is closest to the hit point
        Box bestSubBox = selectTargetSubBox(localBoxes, face, localHitX, localHitY, localHitZ);
        if (bestSubBox == null) {
            return PlacementResult.invalid("no_subbox");
        }

        // Verify that the selected sub-box has enough support area (reject ultra-thin wires < 1.5 pixels wide in both axes)
        double spanU = faceSpanU(bestSubBox, face);
        double spanV = faceSpanV(bestSubBox, face);
        if (spanU < 0.08 && spanV < 0.08) {
            return PlacementResult.invalid("surface_too_small");
        }

        // Compute exact flush surface coordinate on the sub-box (zero air gap even on thin bars/fences/slabs/stairs!)
        Vec3d exactLocalPoint = computeFlushLocalPoint(bestSubBox, face, localHitX, localHitY, localHitZ);
        Vec3d worldAttachPoint = new Vec3d(
                pos.getX() + exactLocalPoint.x,
                pos.getY() + exactLocalPoint.y,
                pos.getZ() + exactLocalPoint.z
        );

        // Compute charge AABB extending outward from worldAttachPoint along the face normal
        Box chargeBox = buildChargeBox(worldAttachPoint, face);

        // Check that the charge does not penetrate the supporting sub-box itself
        Box worldSubBox = bestSubBox.offset(pos);
        if (chargeBox.shrink(1.0e-4, 1.0e-4, 1.0e-4).intersects(worldSubBox)) {
            return PlacementResult.invalid("self_intersection");
        }

        // Check that the charge does not clip into adjacent solid blocks
        if (intersectsSurroundingGeometry(world, pos, face, chargeBox)) {
            return PlacementResult.invalid("obstructed");
        }

        // Compute natural orientation angles (yaw, pitch, roll) for rendering on walls, floors, ceilings, and bars
        float[] angles = computeOrientationAngles(face, playerYaw, spanU, spanV);
        double gap = computeDistanceToSubBoxFace(worldAttachPoint, worldSubBox, face);

        return new PlacementResult(
                true,
                "ok",
                pos.toImmutable(),
                face,
                worldAttachPoint,
                chargeBox,
                angles[0],
                angles[1],
                angles[2],
                gap
        );
    }

    /**
     * Checks whether an already-attached charge at {@code attachmentPoint} on {@code attachmentBlockPos}
     * still has its supporting VoxelShape surface intact (Section 10.6).
     */
    public static boolean hasValidSupport(BlockView world,
                                          BlockPos attachmentBlockPos,
                                          Direction attachmentFace,
                                          Vec3d attachmentPoint) {
        if (world == null || attachmentBlockPos == null || attachmentFace == null || attachmentPoint == null) {
            return false;
        }
        BlockState state = world.getBlockState(attachmentBlockPos);
        if (!isAttachableBlock(state)) {
            return false;
        }
        if (state.isOpaqueFullCube(world, attachmentBlockPos)) {
            return true;
        }
        VoxelShape shape = resolveAttachableShape(world, attachmentBlockPos, state);
        if (shape.isEmpty()) {
            return false;
        }
        // Check that a sub-box face still lies within 0.03 blocks behind the attachment point
        Vec3i normal = attachmentFace.getVector();
        double probeX = attachmentPoint.x - attachmentBlockPos.getX() - normal.getX() * (SURFACE_EPSILON + 0.015);
        double probeY = attachmentPoint.y - attachmentBlockPos.getY() - normal.getY() * (SURFACE_EPSILON + 0.015);
        double probeZ = attachmentPoint.z - attachmentBlockPos.getZ() - normal.getZ() * (SURFACE_EPSILON + 0.015);
        for (Box box : shape.getBoundingBoxes()) {
            if (box.expand(0.025).contains(probeX, probeY, probeZ)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAttachableBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        Block block = state.getBlock();
        if (block instanceof FluidBlock || block instanceof FireBlock || block instanceof AbstractPlantPartBlock
                || block instanceof PlantBlock || block instanceof TorchBlock || block instanceof CobwebBlock) {
            return false;
        }
        return true;
    }

    /**
     * Resolves the exact attachable VoxelShape of a block without empty catch blocks.
     *
     * <p>Requires non-empty collision shape so non-collidable decorative blocks (grass, cables, signs) are rejected,
     * and returns the visible outline shape (so fences and walls use their true 1.0-block visual height rather than
     * their 1.5-block invisible jump-prevention collider).
     */
    public static VoxelShape resolveAttachableShape(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir() || !isAttachableBlock(state)) {
            return VoxelShapes.empty();
        }
        VoxelShape collision = state.getCollisionShape(world, pos, ShapeContext.absent());
        if (collision == null || collision.isEmpty()) {
            return VoxelShapes.empty();
        }
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        if (outline != null && !outline.isEmpty()) {
            return outline;
        }
        return collision;
    }

    private static Box selectTargetSubBox(List<Box> boxes, Direction face, double hx, double hy, double hz) {
        Box best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Box box : boxes) {
            double facePlane = switch (face) {
                case DOWN -> box.minY;
                case UP -> box.maxY;
                case NORTH -> box.minZ;
                case SOUTH -> box.maxZ;
                case WEST -> box.minX;
                case EAST -> box.maxX;
            };
            double planeDist = switch (face.getAxis()) {
                case X -> Math.abs(hx - facePlane);
                case Y -> Math.abs(hy - facePlane);
                case Z -> Math.abs(hz - facePlane);
            };
            double dx = hx < box.minX ? box.minX - hx : (hx > box.maxX ? hx - box.maxX : 0.0);
            double dy = hy < box.minY ? box.minY - hy : (hy > box.maxY ? hy - box.maxY : 0.0);
            double dz = hz < box.minZ ? box.minZ - hz : (hz > box.maxZ ? hz - box.maxZ : 0.0);
            double distSq = dx * dx + dy * dy + dz * dz + planeDist * planeDist * 2.5;
            if (distSq < bestScore) {
                bestScore = distSq;
                best = box;
            }
        }
        return best;
    }

    private static Vec3d computeFlushLocalPoint(Box subBox, Direction face, double hx, double hy, double hz) {
        double x = clampOrCenter(hx, subBox.minX, subBox.maxX, CHARGE_HALF_WIDTH);
        double y = clampOrCenter(hy, subBox.minY, subBox.maxY, CHARGE_HALF_WIDTH);
        double z = clampOrCenter(hz, subBox.minZ, subBox.maxZ, CHARGE_HALF_WIDTH);

        return switch (face) {
            case DOWN -> new Vec3d(x, subBox.minY - SURFACE_EPSILON, z);
            case UP -> new Vec3d(x, subBox.maxY + SURFACE_EPSILON, z);
            case NORTH -> new Vec3d(x, y, subBox.minZ - SURFACE_EPSILON);
            case SOUTH -> new Vec3d(x, y, subBox.maxZ + SURFACE_EPSILON);
            case WEST -> new Vec3d(subBox.minX - SURFACE_EPSILON, y, z);
            case EAST -> new Vec3d(subBox.maxX + SURFACE_EPSILON, y, z);
        };
    }

    private static double clampOrCenter(double coord, double min, double max, double margin) {
        double span = max - min;
        if (span <= margin * 2.1) {
            // Narrow bar, fence post, or thin beam: center directly on the element so it looks cleanly mounted
            return 0.5 * (min + max);
        }
        return MathHelper.clamp(coord, min + margin, max - margin);
    }

    public static Box buildChargeBox(Vec3d attachPoint, Direction face) {
        double hw = CHARGE_HALF_WIDTH;
        double hl = CHARGE_HALF_LENGTH;
        double t = CHARGE_THICKNESS;
        return switch (face) {
            case UP -> new Box(attachPoint.x - hw, attachPoint.y, attachPoint.z - hl,
                    attachPoint.x + hw, attachPoint.y + t, attachPoint.z + hl);
            case DOWN -> new Box(attachPoint.x - hw, attachPoint.y - t, attachPoint.z - hl,
                    attachPoint.x + hw, attachPoint.y, attachPoint.z + hl);
            case NORTH -> new Box(attachPoint.x - hw, attachPoint.y - hl, attachPoint.z - t,
                    attachPoint.x + hw, attachPoint.y + hl, attachPoint.z);
            case SOUTH -> new Box(attachPoint.x - hw, attachPoint.y - hl, attachPoint.z,
                    attachPoint.x + hw, attachPoint.y + hl, attachPoint.z + t);
            case WEST -> new Box(attachPoint.x - t, attachPoint.y - hl, attachPoint.z - hw,
                    attachPoint.x, attachPoint.y + hl, attachPoint.z + hw);
            case EAST -> new Box(attachPoint.x, attachPoint.y - hl, attachPoint.z - hw,
                    attachPoint.x + t, attachPoint.y + hl, attachPoint.z + hw);
        };
    }

    private static boolean intersectsSurroundingGeometry(BlockView world, BlockPos supportPos, Direction face, Box chargeBox) {
        Box testBox = chargeBox.shrink(0.008, 0.008, 0.008);
        BlockPos neighborPos = supportPos.offset(face);
        BlockState neighborState = world.getBlockState(neighborPos);
        if (!neighborState.isAir() && !(neighborState.getBlock() instanceof FluidBlock)) {
            VoxelShape neighborShape = resolveAttachableShape(world, neighborPos, neighborState);
            for (Box b : neighborShape.getBoundingBoxes()) {
                if (b.offset(neighborPos).intersects(testBox)) {
                    return true;
                }
            }
        }
        // Also check other sub-boxes of the same block (e.g., L-shaped stair riser vs tread)
        BlockState supportState = world.getBlockState(supportPos);
        VoxelShape supportShape = resolveAttachableShape(world, supportPos, supportState);
        for (Box b : supportShape.getBoundingBoxes()) {
            if (b.offset(supportPos).intersects(testBox)) {
                return true;
            }
        }
        return false;
    }

    private static float[] computeOrientationAngles(Direction face, float playerYaw, double spanU, double spanV) {
        return switch (face) {
            case UP -> new float[]{MathHelper.wrapDegrees(playerYaw), 0.0f, 0.0f};
            case DOWN -> new float[]{MathHelper.wrapDegrees(playerYaw), 180.0f, 0.0f};
            case NORTH -> new float[]{180.0f, 90.0f, spanU > spanV * 1.5 ? 90.0f : 0.0f};
            case SOUTH -> new float[]{0.0f, 90.0f, spanU > spanV * 1.5 ? 90.0f : 0.0f};
            case WEST -> new float[]{90.0f, 90.0f, spanU > spanV * 1.5 ? 90.0f : 0.0f};
            case EAST -> new float[]{-90.0f, 90.0f, spanU > spanV * 1.5 ? 90.0f : 0.0f};
        };
    }

    private static double faceSpanU(Box box, Direction face) {
        return switch (face.getAxis()) {
            case X -> box.maxZ - box.minZ;
            case Y -> box.maxX - box.minX;
            case Z -> box.maxX - box.minX;
        };
    }

    private static double faceSpanV(Box box, Direction face) {
        return switch (face.getAxis()) {
            case X -> box.maxY - box.minY;
            case Y -> box.maxZ - box.minZ;
            case Z -> box.maxY - box.minY;
        };
    }

    private static double computeDistanceToSubBoxFace(Vec3d attachPoint, Box worldSubBox, Direction face) {
        return switch (face) {
            case UP -> Math.abs(attachPoint.y - worldSubBox.maxY);
            case DOWN -> Math.abs(worldSubBox.minY - attachPoint.y);
            case NORTH -> Math.abs(worldSubBox.minZ - attachPoint.z);
            case SOUTH -> Math.abs(attachPoint.z - worldSubBox.maxZ);
            case WEST -> Math.abs(worldSubBox.minX - attachPoint.x);
            case EAST -> Math.abs(attachPoint.x - worldSubBox.maxX);
        };
    }
}
