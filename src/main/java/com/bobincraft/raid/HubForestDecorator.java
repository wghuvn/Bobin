package com.bobincraft.raid;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.Direction;

/** Chunk-local, coordinate-addressed spruce forest, undergrowth and rock outcrops. */
public final class HubForestDecorator {
    public static final int FOREST_START = 48;
    private static final int TREE_GRID = 7;
    private static final int TREE_REACH = 5;
    private static final BlockState NEEDLES = Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    private static final BlockState SHRUB = Blocks.DARK_OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);

    @FunctionalInterface
    public interface BlockPlacer {
        void place(int x, int y, int z, BlockState state);
    }

    private HubForestDecorator() { }

    public static boolean reserved(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) < FOREST_START
                || HubChunkGenerator.isApproach(x, z)
                || HubChunkGenerator.isRiverMargin(x, z);
    }

    private static boolean allowed(int x, int z) {
        return HubChunkGenerator.inside(x, z) && !reserved(x, z);
    }

    public static void decorate(int minX, int minZ, int maxX, int maxZ, BlockPlacer placer) {
        if (maxX < -HubChunkGenerator.RADIUS || minX > HubChunkGenerator.RADIUS
                || maxZ < -HubChunkGenerator.RADIUS || minZ > HubChunkGenerator.RADIUS) return;
        if (minX > -FOREST_START && maxX < FOREST_START && minZ > -FOREST_START && maxZ < FOREST_START) return;
        // The same (x,z) is checked many times by overhanging canopies.
        // Cache only for this decorate() call so parallel chunk generation remains independent.
        int[][] groundCache = new int[maxX - minX + 1][maxZ - minZ + 1];
        for (int[] column : groundCache) java.util.Arrays.fill(column, Integer.MIN_VALUE);
        BlockPlacer clipped = (x, y, z, state) -> {
            if (x < minX || x > maxX || z < minZ || z > maxZ || !allowed(x, z)) return;
            int ix = x - minX, iz = z - minZ;
            int height = groundCache[ix][iz];
            if (height == Integer.MIN_VALUE) {
                height = HubChunkGenerator.terrainHeight(x, z);
                groundCache[ix][iz] = height;
            }
            if (y <= height) return;
            placer.place(x, y, z, state);
        };

        // Neighbor anchors make cross-chunk canopies independent of generation order.
        for (int gx = Math.floorDiv(minX - TREE_REACH, TREE_GRID); gx <= Math.floorDiv(maxX + TREE_REACH, TREE_GRID); gx++) {
            for (int gz = Math.floorDiv(minZ - TREE_REACH, TREE_GRID); gz <= Math.floorDiv(maxZ + TREE_REACH, TREE_GRID); gz++) {
                long seed = HubChunkGenerator.hash(gx, gz, 31013);
                int x = gx * TREE_GRID + (int) (seed % TREE_GRID);
                int z = gz * TREE_GRID + (int) ((seed >>> 8) % TREE_GRID);
                if (!allowed(x, z) || Math.max(Math.abs(x), Math.abs(z)) < FOREST_START + 6) continue;
                if (HubChunkGenerator.isApproach(x - 5, z) || HubChunkGenerator.isApproach(x + 5, z)) continue;
                double glade = Math.sin(x * .055 + .7) + Math.cos(z * .063 - .4);
                if ((seed >>> 16) % 13 == 0 || (z > 47 && glade > .75 && seed % 3 != 0)) continue;
                if ((seed >>> 18) % 5 < 2) {
                    broadleaf(clipped, x, z, 8 + (int)((seed >>> 25) % 5), (seed & 256) == 0);
                    continue;
                }
                boolean oldGrowth = (seed >>> 20) % 5 == 0;
                int height = (oldGrowth ? 21 : 15) + (int) ((seed >>> 25) % 6);
                spruce(clipped, x, z, height, oldGrowth);
            }
        }

        for (int gx = Math.floorDiv(minX - 3, 9); gx <= Math.floorDiv(maxX + 3, 9); gx++) {
            for (int gz = Math.floorDiv(minZ - 3, 9); gz <= Math.floorDiv(maxZ + 3, 9); gz++) {
                long seed = HubChunkGenerator.hash(gx, gz, 32003);
                int x = gx * 9 + (int) (seed % 9);
                int z = gz * 9 + (int) ((seed >>> 8) % 9);
                if (!allowed(x, z)) continue;
                int style = (int) ((seed >>> 16) % 9);
                if (style < 3) outcrop(clipped, x, z, 1 + style, seed);
                else if (style == 3) fallenLog(clipped, x, z, (seed & 128) == 0);
            }
        }

        for (int gx = Math.floorDiv(minX - 1, 5); gx <= Math.floorDiv(maxX + 1, 5); gx++) {
            for (int gz = Math.floorDiv(minZ - 1, 5); gz <= Math.floorDiv(maxZ + 1, 5); gz++) {
                long seed = HubChunkGenerator.hash(gx, gz, 33013);
                int x = gx * 5 + (int) (seed % 5);
                int z = gz * 5 + (int) ((seed >>> 8) % 5);
                if (allowed(x, z) && (seed >>> 16) % 3 != 0) shrub(clipped, x, z);
            }
        }

        for (int x = Math.max(minX, -HubChunkGenerator.RADIUS); x <= Math.min(maxX, HubChunkGenerator.RADIUS); x++) {
            for (int z = Math.max(minZ, -HubChunkGenerator.RADIUS); z <= Math.min(maxZ, HubChunkGenerator.RADIUS); z++) {
                if (!allowed(x, z)) continue;
                int ground = HubChunkGenerator.terrainHeight(x, z);
                BlockState floor = HubChunkGenerator.terrainState(x, ground, z);
                if (floor.isOf(Blocks.STONE) || floor.isOf(Blocks.ANDESITE)) continue;
                int variant = (int) (HubChunkGenerator.hash(x, z, 34019) % 24);
                BlockState plant = variant < 7 ? Blocks.FERN.getDefaultState()
                        : variant < 14 ? Blocks.GRASS.getDefaultState()
                        : variant == 14 ? Blocks.BROWN_MUSHROOM.getDefaultState()
                        : variant == 15 ? Blocks.DANDELION.getDefaultState()
                        : variant == 16 ? Blocks.AZURE_BLUET.getDefaultState()
                        : variant < 20 ? Blocks.MOSS_CARPET.getDefaultState() : null;
                if (plant != null) clipped.place(x, ground + 1, z, plant);
            }
        }
    }

    private static void spruce(BlockPlacer placer, int x, int z, int height, boolean oldGrowth) {
        int ground = HubChunkGenerator.terrainHeight(x, z);
        int crownStart = oldGrowth ? 7 : 4;
        for (int level = crownStart; level <= height; level++) {
            int fromTop = height - level;
            int radius = Math.min(oldGrowth ? 4 : 3, 1 + fromTop / 4);
            if (fromTop == 0) radius = 0;
            else if (fromTop % 4 == 0 && radius > 1) radius--;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius * 2 - 1 && radius > 0) continue;
                    placer.place(x + dx, ground + level, z + dz, NEEDLES);
                }
            }
        }
        int thickness = oldGrowth ? 2 : 1;
        for (int dx = 0; dx < thickness; dx++) {
            for (int dz = 0; dz < thickness; dz++) {
                for (int y = HubChunkGenerator.terrainHeight(x + dx, z + dz) + 1; y < ground + height - 1; y++) {
                    placer.place(x + dx, y, z + dz, Blocks.SPRUCE_LOG.getDefaultState());
                }
            }
        }
        if (oldGrowth) {
            BlockState branch = Blocks.SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X);
            placer.place(x - 1, ground + 9, z, branch);
            placer.place(x + 2, ground + 13, z + 1, branch);
        }
    }

    private static void broadleaf(BlockPlacer placer, int x, int z, int height, boolean birch) {
        int ground = HubChunkGenerator.terrainHeight(x, z);
        BlockState crown = (birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES).getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int level = height - 4; level <= height + 1; level++) {
            int radius = level >= height ? 2 : level == height - 4 ? 2 : 4;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius + 1) continue;
                if ((HubChunkGenerator.hash(x + dx, z + dz, level) & 15) == 0) continue;
                placer.place(x + dx, ground + level, z + dz, crown);
            }
        }
        for (int y = 1; y < height; y++) placer.place(x, ground + y, z,
                (birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).getDefaultState());
    }

    private static void shrub(BlockPlacer placer, int x, int z) {
        int ground = HubChunkGenerator.terrainHeight(x, z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 && dz != 0) continue;
                placer.place(x + dx, ground + 1, z + dz, SHRUB);
            }
        }
        placer.place(x, ground + 2, z, SHRUB);
    }

    private static void fallenLog(BlockPlacer placer, int x, int z, boolean alongX) {
        BlockState log = Blocks.SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
        int ground = HubChunkGenerator.terrainHeight(x, z);
        for (int offset = -3; offset <= 3; offset++) {
            int px = x + (alongX ? offset : 0);
            int pz = z + (alongX ? 0 : offset);
            placer.place(px, Math.max(ground, HubChunkGenerator.terrainHeight(px, pz)) + 1, pz, log);
        }
    }

    private static void outcrop(BlockPlacer placer, int x, int z, int radius, long seed) {
        int ground = HubChunkGenerator.terrainHeight(x, z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius + 1) continue;
                int height = Math.max(1, radius + 1 - (Math.abs(dx) + Math.abs(dz)) / 2);
                int localGround = HubChunkGenerator.terrainHeight(x + dx, z + dz);
                for (int y = localGround + 1; y <= Math.max(localGround + 1, ground + height); y++) {
                    BlockState rock = ((seed + dx * 7L + dz * 13L + y) & 3) == 0
                            ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                            : ((dx + dz + y) & 1) == 0 ? Blocks.ANDESITE.getDefaultState() : Blocks.STONE.getDefaultState();
                    placer.place(x + dx, y, z + dz, rock);
                }
            }
        }
    }

    /** The same precedence is used in real chunks and synthetic column samples. */
    public static boolean shouldReplace(BlockState oldState, BlockState newState) {
        return priority(newState) > priority(oldState);
    }

    private static int priority(BlockState state) {
        if (state.isAir()) return 0;
        if (state.isOf(Blocks.SPRUCE_LOG) || state.isOf(Blocks.BIRCH_LOG) || state.isOf(Blocks.OAK_LOG)) return 60;
        if (state.isOf(Blocks.STONE) || state.isOf(Blocks.MOSSY_COBBLESTONE) || state.isOf(Blocks.ANDESITE)) return 45;
        if (state.getBlock() instanceof LeavesBlock) return 30;
        if (state.isOf(Blocks.FERN) || state.isOf(Blocks.GRASS) || state.isOf(Blocks.BROWN_MUSHROOM)
                || state.isOf(Blocks.MOSS_CARPET) || state.isOf(Blocks.DANDELION) || state.isOf(Blocks.AZURE_BLUET)) return 10;
        return 100;
    }
}

