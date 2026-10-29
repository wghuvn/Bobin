package com.bobincraft.drone;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** A bounded Minecraft link model, not real radio engineering units. It never loads chunks. */
public final class DroneSignal {
    public static final int LOSS_GRACE_TICKS = 40;
    private DroneSignal() { }

    public static float strength(double distance, double range, double obstruction) {
        if (!Double.isFinite(distance) || !Double.isFinite(range) || !Double.isFinite(obstruction)
                || distance < 0 || range <= 0 || distance >= range) return 0;
        double t = Math.max(0, Math.min(1, (distance / range - .35) / .65));
        double clear = 1 - t * t * (3 - 2 * t);
        // Nearby craft tolerate a tree or a thin wall; deep terrain still obstructs the link.
        double attenuation = Math.max(0, Math.min(12, obstruction) - .35) * (.55 + .45 * t);
        return (float) (clear * Math.exp(-attenuation));
    }

    public static double obstruction(ServerWorld world, Vec3d from, Vec3d to) {
        int samples = Math.min(512, Math.max(1, (int) Math.ceil(from.distanceTo(to) * 1.5)));
        double loss = 0;
        BlockPos previous = null;
        for (int i = 1; i <= samples; i++) {
            BlockPos pos = BlockPos.ofFloored(from.lerp(to, i / (double) samples));
            if (pos.equals(previous)) continue;
            previous = pos;
            if (!world.isInBuildLimit(pos)) return 12;
            // For long-range 2x/5x flights, skip unloaded intermediate chunks along the ray rather than cutting link
            if (!world.isChunkLoaded(pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.isOf(Blocks.WATER)) loss += .08;
            else if (state.isIn(BlockTags.LEAVES)) loss += .025;
            else if (state.getSoundGroup() == BlockSoundGroup.GLASS) loss += .035;
            else if (state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.LOGS)) loss += .075;
            else if (!state.getCollisionShape(world, pos).isEmpty()) loss += state.getSoundGroup() == BlockSoundGroup.METAL ? .3 : .16;
            if (loss >= 12) return 12;
        }
        return loss;
    }

    public static int advanceLoss(int previous, float signal) { return signal <= .005f ? Math.min(LOSS_GRACE_TICKS, previous + 1) : 0; }
    public static float sessionSignal(float signal, int lostTicks) {
        return lostTicks >= LOSS_GRACE_TICKS ? 0 : Math.max(.001f, signal);
    }
}
