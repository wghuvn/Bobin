package com.bobincraft.raid;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallSignBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.List;

/** Historical locations exist only to remove authored labels from existing saves. */
public final class HubSigns {
    public record Board(BlockPos center, Direction facing) {
        public BlockPos sign(int offset) {
            Direction across = facing.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
            return center.offset(facing).offset(across, offset);
        }
    }
    public static final List<Board> LEGACY_BOARDS = List.of(
            new Board(new BlockPos(-31, 73, -32), Direction.SOUTH),
            new Board(new BlockPos(46, 73, -29), Direction.WEST),
            new Board(new BlockPos(-38, 73, 13), Direction.EAST),
            new Board(new BlockPos(30, 73, 18), Direction.SOUTH),
            new Board(new BlockPos(0, 74, 46), Direction.NORTH));

    private HubSigns() { }

    public static void registerCleanup() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (RaidManager.isAvailable(server)
                    && RaidPersistentState.get(server).hubDesign == HubLayout.DESIGN_VERSION)
                removeGenerated(server.getOverworld());
        });
    }

    public static int removeGenerated(ServerWorld world) {
        var plan = HubBuilder.plan();
        int removed = 0;
        for (Board board : LEGACY_BOARDS) for (int offset = -1; offset <= 1; offset++) {
            BlockPos pos = board.sign(offset);
            var current = world.getBlockState(pos);
            if (current.isOf(Blocks.SPRUCE_WALL_SIGN) && current.get(WallSignBlock.FACING) == board.facing()) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                removed++;
            }
            // Restore an absent authored backing cell, retaining player replacements and storage.
            BlockPos backing = pos.offset(board.facing().getOpposite());
            var expected = plan.get(backing);
            if (world.isAir(backing) && expected != null && !expected.isAir())
                world.setBlockState(backing, expected, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
        return removed;
    }
}
