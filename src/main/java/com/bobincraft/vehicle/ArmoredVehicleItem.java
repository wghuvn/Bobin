package com.bobincraft.vehicle;

import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public final class ArmoredVehicleItem extends Item {
    /** Extra forward spawn offset (+3 blocks) so the player never gets trapped inside the 3.8m tank hull (Task 5). */
    public static final double SPAWN_FORWARD_OFFSET = 3.0;

    private final boolean fixed;
    public ArmoredVehicleItem(Settings settings, boolean fixed) { super(settings); this.fixed = fixed; }

    public static Vec3d computeSpawnCenter(BlockPos clickedBase, float playerYaw) {
        Vec3d forward = Vec3d.fromPolar(0, playerYaw);
        return new Vec3d(
                clickedBase.getX() + 0.5 + forward.x * SPAWN_FORWARD_OFFSET,
                clickedBase.getY(),
                clickedBase.getZ() + 0.5 + forward.z * SPAWN_FORWARD_OFFSET
        );
    }

    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        var world = context.getWorld();
        var player = context.getPlayer();
        if (player == null || ArmorContent.inHub(world) || !player.canModifyBlocks()) return ActionResult.FAIL;
        BlockPos clickedBase = context.getBlockPos().offset(context.getSide());
        Vec3d shifted = computeSpawnCenter(clickedBase, player.getYaw());
        BlockPos base = findSurfacePosition(world, BlockPos.ofFloored(shifted.x, clickedBase.getY(), shifted.z));

        var vehicle = ArmorContent.VEHICLE.create(world);
        if (vehicle == null) return ActionResult.FAIL;
        vehicle.refreshPositionAndAngles(
                shifted.x,
                base.getY() - (fixed ? ArmorControls.BURIAL_DEPTH : 0),
                shifted.z,
                player.getYaw(),
                0
        );
        vehicle.configure(fixed, -1, -1, player.getYaw());
        Box clear = vehicle.getBoundingBox();
        if (fixed) clear = new Box(clear.minX, base.getY() + .1, clear.minZ, clear.maxX, clear.maxY, clear.maxZ);
        if (!world.getWorldBorder().contains(clear) || !world.isSpaceEmpty(vehicle, clear)) {
            vehicle.discard();
            return ActionResult.FAIL;
        }
        if (!world.isClient) {
            if (!world.spawnEntity(vehicle)) return ActionResult.FAIL;
            if (!player.getAbilities().creativeMode) context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }

    private static BlockPos findSurfacePosition(World world, BlockPos candidate) {
        if (world == null) return candidate;
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos check = candidate.up(dy);
            if (world.getBlockState(check).isAir() && !world.getBlockState(check.down()).isAir()) {
                return check;
            }
        }
        return candidate;
    }
}
