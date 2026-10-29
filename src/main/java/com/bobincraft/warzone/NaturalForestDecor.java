package com.bobincraft.warzone;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Direction;

/** Chunk-local material adapter for the pure natural forest and rock plans. */
final class NaturalForestDecor {
    private NaturalForestDecor() { }

    static void vegetation(WarzoneTerrain terrain, ChunkWriter writer, WarzoneTerrain.Sample[][] samples) {
        NaturalForest forest = terrain.naturalForest();
        for (NaturalForest.Plant plant : forest.plantsForChunk(writer.minX, writer.minZ)) {
            forest.shape(plant).forEach((dx, dy, dz, code) -> {
                int x = plant.x() + dx, y = plant.y() + dy, z = plant.z() + dz;
                if (!writer.contains(x, y, z) || !terrain.isAuthoredZone() && terrain.isReserved(x, z)) return;
                BlockState old = writer.get(x, y, z);
                boolean soft = old.isAir() || old.getBlock() instanceof PlantBlock || old.isOf(Blocks.MOSS_CARPET);
                switch (NaturalForest.type(code)) {
                    case NaturalForest.LOG -> {
                        if (soft || old.isIn(BlockTags.LEAVES)) writer.set(x, y, z, log(code));
                    }
                    case NaturalForest.LEAF -> {
                        if (soft) writer.set(x, y, z, leaf(code));
                    }
                    case NaturalForest.ROCK -> {
                        if (soft) writer.set(x, y, z, rock(code));
                    }
                    default -> throw new IllegalArgumentException("Unknown natural forest block: " + code);
                }
            });
        }
        floor(terrain, forest, writer, samples);
    }

    private static BlockState log(int code) {
        Block block = switch (NaturalForest.material(code)) {
            case NaturalForest.PINE -> ForestBlocks.DARK_PINE_WOOD;
            case NaturalForest.ROTTEN -> ForestBlocks.ROTTEN_WOOD;
            case NaturalForest.BIRCH -> Blocks.BIRCH_LOG;
            default -> ForestBlocks.WEATHERED_BARK;
        };
        Direction.Axis axis = switch (NaturalForest.axis(code)) {
            case NaturalForest.X -> Direction.Axis.X;
            case NaturalForest.Z -> Direction.Axis.Z;
            default -> Direction.Axis.Y;
        };
        return block.getDefaultState().with(PillarBlock.AXIS, axis);
    }

    private static BlockState leaf(int code) {
        Block block = NaturalForest.material(code) == NaturalForest.BIRCH ? Blocks.BIRCH_LEAVES : Blocks.SPRUCE_LEAVES;
        return block.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState rock(int code) {
        Block block = switch (NaturalForest.material(code)) {
            case NaturalForest.ROCK_CRAG -> ForestBlocks.WEATHERED_CRAG;
            case NaturalForest.ROCK_LICHEN -> ForestBlocks.LICHEN_BOULDER;
            case NaturalForest.ROCK_SHOAL -> ForestBlocks.PEBBLE_SHOAL;
            default -> ForestBlocks.MOSS_COVERED_ROCK;
        };
        return block.getDefaultState();
    }

    /** Cluster fields keep short vegetation, reeds, and field stones rich and varied without obstructing movement. */
    private static void floor(WarzoneTerrain terrain, NaturalForest forest, ChunkWriter writer, WarzoneTerrain.Sample[][] samples) {
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = writer.minX + lx, z = writer.minZ + lz;
            WarzoneTerrain.Sample sample = samples[lx + 1][lz + 1];
            if (!terrain.inside(x, z) || sample.edge < 8) continue;
            if (Math.hypot(x, z - terrain.landingZ()) <= 25) continue;
            int y = sample.height + 1;
            long hash = WarzoneNoise.hash(terrain.seed + 521, x, z);
            double choice = WarzoneNoise.unit(hash);
            double secondary = WarzoneNoise.unit(WarzoneNoise.mix(hash + 1));
            if (sample.height < WarzoneTerrain.SEA_LEVEL) {
                if (terrain.usesRevisedLandscape()) waterPlants(terrain, writer, sample, x, z, choice, secondary);
                else {
                    if (sample.bog > .3 && sample.height >= WarzoneTerrain.SEA_LEVEL - 2
                            && choice < .03 && writer.get(x, WarzoneTerrain.SEA_LEVEL, z).isOf(Blocks.WATER))
                        writer.setIfAir(x, WarzoneTerrain.SEA_LEVEL + 1, z, Blocks.LILY_PAD.getDefaultState());
                    if (sample.height >= WarzoneTerrain.SEA_LEVEL - 3 && choice < .09
                            && writer.get(x, y, z).isOf(Blocks.WATER)) writer.set(x, y, z, Blocks.SEAGRASS.getDefaultState());
                }
                continue;
            }
            if (sample.mountain > .82) continue;
            BlockState soil = writer.get(x, sample.height, z);
            boolean rockySoil = soil.isOf(ForestBlocks.MOSS_COVERED_ROCK)
                    || soil.isOf(ForestBlocks.WEATHERED_CRAG)
                    || soil.isOf(ForestBlocks.LICHEN_BOULDER)
                    || soil.isOf(Blocks.ANDESITE) || soil.isOf(Blocks.STONE);
            if (rockySoil) {
                if (choice < 0.08 && !terrain.isReserved(x, z)) {
                    writer.setIfAir(x, y, z, (secondary < 0.65 ? Blocks.MOSS_CARPET : ForestBlocks.DRY_UNDERGROWTH).getDefaultState());
                }
                continue;
            }
            boolean plantable = soil.isIn(BlockTags.DIRT) || soil.isOf(Blocks.GRASS_BLOCK) || soil.isOf(Blocks.MOSS_BLOCK)
                    || soil.isOf(ForestBlocks.DARK_FOREST_SOIL) || soil.isOf(ForestBlocks.PINE_NEEDLE_GROUND)
                    || soil.isOf(ForestBlocks.MOSSY_FOREST_SOIL) || soil.isOf(ForestBlocks.FOREST_LITTER)
                    || soil.isOf(ForestBlocks.TRAMPLED_MUD) || soil.isOf(ForestBlocks.RUTTED_TRACK)
                    || soil.isOf(ForestBlocks.RIVER_SILT) || soil.isOf(ForestBlocks.PEBBLE_SHOAL)
                    || soil.isOf(ForestBlocks.WEATHERED_GRAVEL);
            if (!plantable) continue;

            double routeStrength = Math.max(sample.road, sample.path);
            if (terrain.usesRevisedLandscape() && routeStrength > 0.03) {
                decorateRouteVerge(terrain, writer, sample, x, y, z, routeStrength, choice, secondary);
                continue;
            }
            if (routeStrength > .01) continue;

            if (terrain.usesRevisedLandscape() && (sample.channel > .02 || sample.valley > .38)
                    && sample.height <= WarzoneTerrain.SEA_LEVEL + 3) {
                decorateRiverbank(terrain, writer, sample, x, y, z, choice, secondary);
                continue;
            }
            if (sample.channel > .04) continue;

            double cover = forest.undergrowth(x, z);
            double bushCluster = patch(terrain.seed + 611, x, z, 11);
            double fernCluster = patch(terrain.seed + 617, x, z, 14);
            double dryCluster = patch(terrain.seed + 623, x, z, 13);
            double stoneCluster = patch(terrain.seed + 653, x, z, 9);
            double tallCluster = patch(terrain.seed + 659, x, z, 10);

            // Scattered field stones and mossy rock outcrops on slopes, glades, and forest floors.
            if (!forest.movementCorridor(x, z) && !terrain.isReserved(x, z)
                    && stoneCluster > 0.72 && choice < (sample.mountain > 0.20 ? 0.075 : 0.028) * 0.4) {
                Block stone = secondary < 0.45 ? ForestBlocks.MOSS_COVERED_ROCK
                        : secondary < 0.78 ? ForestBlocks.LICHEN_BOULDER : ForestBlocks.WEATHERED_CRAG;
                writer.setIfAir(x, y, z, stone.getDefaultState());
                if (secondary > 0.82 && writer.contains(x, y + 1, z)) {
                    writer.setIfAir(x, y + 1, z, ForestBlocks.MOSS_COVERED_ROCK.getDefaultState());
                }
                continue;
            }

            if (forest.movementCorridor(x, z) || cover < .10) {
                decorateOpenClearing(terrain, forest, writer, samples, lx, lz, x, y, z, sample, choice, secondary);
                continue;
            }

            if (sample.bog > .22 && choice < .28 * cover) {
                if (secondary < .34) writer.setIfAir(x, y, z, ForestBlocks.RIVER_REED.getDefaultState());
                else if (secondary < .64) writer.setIfAir(x, y, z, ForestBlocks.PALE_SEDGE.getDefaultState());
                else if (secondary < .84) writer.setIfAir(x, y, z, Blocks.MOSS_CARPET.getDefaultState());
                else writer.setIfAir(x, y, z, ForestBlocks.LOW_FOREST_BUSH.getDefaultState());
            } else if (bushCluster > .62 && choice < .18 * cover) {
                if (secondary < .48) writer.setIfAir(x, y, z, ForestBlocks.LOW_FOREST_BUSH.getDefaultState());
                else writer.setIfAir(x, y, z, ForestBlocks.NEEDLE_UNDERGROWTH.getDefaultState());
            } else if (tallCluster > .60 && choice < .30 + .40 * cover) {
                // Dense 2-block-tall thickets: a place to crouch and hide.
                tall(writer, x, y, z, ForestBlocks.TALL_FOREST_GRASS);
            } else if (fernCluster > .54 && choice < .26 + .28 * cover) {
                if (secondary < .14 * cover) tall(writer, x, y, z, Blocks.LARGE_FERN);
                else if (secondary < .62) writer.setIfAir(x, y, z, Blocks.FERN.getDefaultState());
                else writer.setIfAir(x, y, z, ForestBlocks.NEEDLE_UNDERGROWTH.getDefaultState());
            } else if (dryCluster > .58 && choice < .24 + .26 * cover) {
                if (secondary < .42) writer.setIfAir(x, y, z, ForestBlocks.PALE_SEDGE.getDefaultState());
                else if (secondary < .82) writer.setIfAir(x, y, z, ForestBlocks.DRY_UNDERGROWTH.getDefaultState());
                else writer.setIfAir(x, y, z, Blocks.DEAD_BUSH.getDefaultState());
            } else if (choice < .22 + .36 * cover) {
                if (secondary < .14) tall(writer, x, y, z, Blocks.TALL_GRASS);
                else if (secondary < .52) writer.setIfAir(x, y, z, ForestBlocks.FOREST_GRASS.getDefaultState());
                else if (secondary < .78) writer.setIfAir(x, y, z, ForestBlocks.NEEDLE_UNDERGROWTH.getDefaultState());
                else writer.setIfAir(x, y, z, Blocks.GRASS.getDefaultState());
            } else if (choice > .982 && cover > .38) {
                writer.setIfAir(x, y, z, secondary < .7 ? Blocks.BROWN_MUSHROOM.getDefaultState() : Blocks.RED_MUSHROOM.getDefaultState());
            } else if (choice > .93 && cover > .50) {
                writer.setIfAir(x, y, z, Blocks.MOSS_CARPET.getDefaultState());
            }
        }
    }

    /**
     * Keeps open clearings and fields atmospheric and quiet while adding sparse, meaningful traces
     * of past human presence or landscape wear (old boundary posts, broken fence fragments,
     * overgrown foundation remnants, extinguished campfires, shallow craters, and dry grass patches).
     */
    private static void decorateOpenClearing(WarzoneTerrain terrain, NaturalForest forest, ChunkWriter writer,
                                             WarzoneTerrain.Sample[][] samples, int lx, int lz,
                                             int x, int y, int z, WarzoneTerrain.Sample sample,
                                             double choice, double secondary) {
        double meadowPatch = patch(terrain.seed + 677, x, z, 12);
        double trackPatch = patch(terrain.seed + 683, x, z, 15);
        if (!terrain.isReserved(x, z) && trackPatch > 0.80 && choice < 0.14) {
            writer.set(x, sample.height, z, (secondary < 0.60 ? ForestBlocks.RUTTED_TRACK : ForestBlocks.WEATHERED_GRAVEL).getDefaultState());
            return;
        }
        if (terrain.isAuthoredZone() && lx == 8 && lz == 8 && !forest.movementCorridor(x, z)
                && sample.height >= WarzoneTerrain.SEA_LEVEL + 2 && sample.mountain < 0.32 && sample.bog < 0.28) {
            long cellHash = WarzoneNoise.hash(terrain.seed + 743, writer.minX >> 4, writer.minZ >> 4);
            if (Math.floorMod(cellHash, 7) == 0 && canPlaceOpenTrace(terrain, writer, samples, lx, lz, x, z, sample.height)) {
                placeOpenTrace(writer, x, y, z, sample.height, Math.floorMod(cellHash >> 5, 6));
                return;
            }
        }
        if (meadowPatch > 0.62 && choice < 0.22 && !terrain.isReserved(x, z)) {
            if (secondary < 0.12) {
                tall(writer, x, y, z, ForestBlocks.TALL_FOREST_GRASS);
            } else if (secondary < 0.44) {
                writer.setIfAir(x, y, z, ForestBlocks.PALE_SEDGE.getDefaultState());
            } else if (secondary < 0.74) {
                writer.setIfAir(x, y, z, ForestBlocks.DRY_UNDERGROWTH.getDefaultState());
            } else if (secondary < 0.90) {
                writer.setIfAir(x, y, z, Blocks.DEAD_BUSH.getDefaultState());
            } else {
                writer.setIfAir(x, y, z, ForestBlocks.LOW_FOREST_BUSH.getDefaultState());
            }
        } else if (choice < .13) {
            writer.setIfAir(x, y, z, (secondary < .52 ? ForestBlocks.FOREST_GRASS
                    : secondary < .78 ? ForestBlocks.PALE_SEDGE : ForestBlocks.DRY_UNDERGROWTH).getDefaultState());
        }
    }

    private static boolean canPlaceOpenTrace(WarzoneTerrain terrain, ChunkWriter writer,
                                             WarzoneTerrain.Sample[][] samples, int lx, int lz,
                                             int x, int z, int baseH) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (terrain.isReserved(x + dx, z + dz)) return false;
                WarzoneTerrain.Sample s = samples[lx + 1 + dx][lz + 1 + dz];
                if (s.height != baseH || s.road > 0.01 || s.path > 0.01 || s.channel > 0.02) return false;
                if (!writer.get(x + dx, baseH + 1, z + dz).isAir()) return false;
            }
        }
        return true;
    }

    private static void placeOpenTrace(ChunkWriter writer, int x, int y, int z, int groundY, int kind) {
        switch (kind) {
            case 0 -> {
                // Lone leaning/broken utility or boundary post.
                writer.set(x, groundY, z, ForestBlocks.WEATHERED_GRAVEL.getDefaultState());
                writer.setIfAir(x, y, z, ForestBlocks.WEATHERED_BARK.getDefaultState());
                writer.setIfAir(x, y + 1, z, ForestBlocks.WEATHERED_BARK.getDefaultState());
                writer.setIfAir(x, y + 2, z, Blocks.COBBLESTONE_WALL.getDefaultState());
                writer.setIfAir(x + 1, y, z, ForestBlocks.DRY_UNDERGROWTH.getDefaultState());
            }
            case 1 -> {
                // Broken fence fragment along an old forgotten boundary.
                writer.setIfAir(x - 1, y, z, Blocks.SPRUCE_FENCE.getDefaultState());
                writer.setIfAir(x, y, z, Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState());
                writer.setIfAir(x + 1, y, z, Blocks.SPRUCE_FENCE.getDefaultState());
                writer.setIfAir(x, y, z + 1, ForestBlocks.PALE_SEDGE.getDefaultState());
            }
            case 2 -> {
                // Overgrown foundation corner flush with the ground.
                writer.set(x - 1, groundY, z - 1, Blocks.MOSSY_STONE_BRICKS.getDefaultState());
                writer.set(x, groundY, z - 1, CombatZoneBlocks.CRACKED_CONCRETE.getDefaultState());
                writer.set(x + 1, groundY, z - 1, Blocks.MOSSY_STONE_BRICKS.getDefaultState());
                writer.set(x - 1, groundY, z, CombatZoneBlocks.CRACKED_CONCRETE.getDefaultState());
                writer.setIfAir(x - 1, y, z - 1, Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState());
                writer.setIfAir(x + 1, y, z - 1, Blocks.MOSS_CARPET.getDefaultState());
            }
            case 3 -> {
                // Old extinguished campfire with a rotten log seat.
                writer.set(x, groundY, z, Blocks.COARSE_DIRT.getDefaultState());
                writer.setIfAir(x, y, z, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, false));
                writer.setIfAir(x + 1, y, z, ForestBlocks.ROTTEN_WOOD.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z));
            }
            case 4 -> {
                // Old shallow shell crater / overgrown drainage depression.
                writer.set(x, groundY, z, ForestBlocks.TRAMPLED_MUD.getDefaultState());
                writer.set(x - 1, groundY, z, Blocks.COARSE_DIRT.getDefaultState());
                writer.set(x + 1, groundY, z, Blocks.COARSE_DIRT.getDefaultState());
                writer.set(x, groundY, z - 1, Blocks.COARSE_DIRT.getDefaultState());
                writer.set(x, groundY, z + 1, Blocks.COARSE_DIRT.getDefaultState());
                writer.setIfAir(x - 1, y, z, ForestBlocks.MOSS_COVERED_ROCK.getDefaultState());
            }
            default -> {
                // Fallen weathered trunk in the clearing.
                writer.setIfAir(x - 1, y, z, ForestBlocks.ROTTEN_WOOD.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
                writer.setIfAir(x, y, z, ForestBlocks.ROTTEN_WOOD.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
                writer.setIfAir(x + 1, y, z, ForestBlocks.ROTTEN_WOOD.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
                writer.setIfAir(x, y + 1, z, Blocks.MOSS_CARPET.getDefaultState());
            }
        }
    }

    /** Blends path edges and median strips with tufts of grass, sedge, and low undergrowth without blocking travel. */
    private static void decorateRouteVerge(WarzoneTerrain terrain, ChunkWriter writer, WarzoneTerrain.Sample sample,
                                           int x, int y, int z, double strength, double choice, double secondary) {
        double cluster = patch(terrain.seed + 631, x, z, 7);
        // Center of wagon tracks or lightly worn trails gets occasional low grass/undergrowth tufts.
        if (strength >= 0.52) {
            if (sample.routeStyle == 0 && sample.routeWear < 0.36 && choice < 0.24 * cluster) {
                writer.setIfAir(x, y, z, (secondary < 0.65 ? ForestBlocks.FOREST_GRASS : ForestBlocks.PALE_SEDGE).getDefaultState());
            } else if (sample.trail && sample.routeWear < 0.42 && choice < 0.16 * cluster) {
                writer.setIfAir(x, y, z, (secondary < 0.55 ? ForestBlocks.NEEDLE_UNDERGROWTH : ForestBlocks.DRY_UNDERGROWTH).getDefaultState());
            }
            return;
        }
        // Irregular outer verges receive clustered shrubs, ferns, sedges, and dark forest grass.
        double vergeDensity = (1.0 - strength) * (0.45 + 0.55 * cluster);
        if (choice < 0.36 * vergeDensity) {
            Block plant = secondary < 0.30 ? ForestBlocks.FOREST_GRASS
                    : secondary < 0.54 ? ForestBlocks.NEEDLE_UNDERGROWTH
                    : secondary < 0.76 ? ForestBlocks.PALE_SEDGE
                    : secondary < 0.90 ? ForestBlocks.DRY_UNDERGROWTH : ForestBlocks.LOW_FOREST_BUSH;
            writer.setIfAir(x, y, z, plant.getDefaultState());
        }
    }

    /** Riparian reeds, sedges, ferns, and riverbank boulders along damp riverbanks. */
    private static void decorateRiverbank(WarzoneTerrain terrain, ChunkWriter writer, WarzoneTerrain.Sample sample,
                                          int x, int y, int z, double choice, double secondary) {
        double reedPatch = patch(terrain.seed + 641, x, z, 8);
        double rockPatch = patch(terrain.seed + 647, x, z, 7);
        int aboveWater = sample.height - WarzoneTerrain.SEA_LEVEL;
        // Natural riverbank boulder clusters & wet rocks along shorelines and rapids.
        if (!terrain.isReserved(x, z) && rockPatch > 0.68
                && choice < (sample.waterState == WarzoneTerrain.WaterState.RAPIDS ? 0.11 : 0.045) * 0.4) {
            Block rock = secondary < 0.48 ? ForestBlocks.MOSS_COVERED_ROCK
                    : secondary < 0.80 ? ForestBlocks.WEATHERED_CRAG : ForestBlocks.LICHEN_BOULDER;
            writer.setIfAir(x, y, z, rock.getDefaultState());
            if (secondary > 0.78 && writer.contains(x, y + 1, z)) {
                writer.setIfAir(x, y + 1, z, ForestBlocks.MOSS_COVERED_ROCK.getDefaultState());
            }
            return;
        }
        if (choice > (0.34 + 0.38 * reedPatch)) return;
        if (aboveWater <= 1 && reedPatch > 0.38 && secondary < 0.46) {
            writer.setIfAir(x, y, z, ForestBlocks.RIVER_REED.getDefaultState());
        } else if (secondary < 0.64) {
            writer.setIfAir(x, y, z, ForestBlocks.PALE_SEDGE.getDefaultState());
        } else if (secondary < 0.80) {
            writer.setIfAir(x, y, z, Blocks.FERN.getDefaultState());
        } else if (secondary < 0.92) {
            writer.setIfAir(x, y, z, ForestBlocks.LOW_FOREST_BUSH.getDefaultState());
        } else {
            tall(writer, x, y, z, Blocks.LARGE_FERN);
        }
    }

    private static void waterPlants(WarzoneTerrain terrain, ChunkWriter writer, WarzoneTerrain.Sample sample,
                                    int x, int z, double choice, double secondary) {
        int depth = WarzoneTerrain.SEA_LEVEL - sample.height;
        boolean still = sample.waterState == WarzoneTerrain.WaterState.STILL;
        if (depth > 2 || (!still && sample.waterState != WarzoneTerrain.WaterState.SLOW)) return;
        double patch = WarzoneNoise.unit(WarzoneNoise.hash(terrain.seed + 533,
                Math.floorDiv(x, 7), Math.floorDiv(z, 7)));
        int bottom = sample.height + 1;
        BlockState water = writer.get(x, bottom, z);
        if (choice < (still ? .26 : .19) * (.35 + patch) && sourceWater(water))
            writer.set(x, bottom, z, Blocks.SEAGRASS.getDefaultState());
        if (secondary < (still ? .065 : .028) * patch
                && sourceWater(writer.get(x, WarzoneTerrain.SEA_LEVEL, z)))
            writer.setIfAir(x, WarzoneTerrain.SEA_LEVEL + 1, z, Blocks.LILY_PAD.getDefaultState());
    }

    private static boolean sourceWater(BlockState state) {
        return state.isOf(Blocks.WATER) && state.getFluidState().isStill();
    }

    private static void tall(ChunkWriter writer, int x, int y, int z, Block plant) {
        if (!writer.contains(x, y + 1, z) || !writer.get(x, y, z).isAir() || !writer.get(x, y + 1, z).isAir()) return;
        writer.set(x, y, z, plant.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER));
        writer.set(x, y + 1, z, plant.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static double patch(long seed, int x, int z, int scale) {
        int cellX = Math.floorDiv(x, scale), cellZ = Math.floorDiv(z, scale);
        double fx = WarzoneNoise.smoothstep(0, 1, Math.floorMod(x, scale) / (double) scale);
        double fz = WarzoneNoise.smoothstep(0, 1, Math.floorMod(z, scale) / (double) scale);
        double north = WarzoneNoise.lerp(WarzoneNoise.unit(WarzoneNoise.hash(seed, cellX, cellZ)),
                WarzoneNoise.unit(WarzoneNoise.hash(seed, cellX + 1, cellZ)), fx);
        double south = WarzoneNoise.lerp(WarzoneNoise.unit(WarzoneNoise.hash(seed, cellX, cellZ + 1)),
                WarzoneNoise.unit(WarzoneNoise.hash(seed, cellX + 1, cellZ + 1)), fx);
        return WarzoneNoise.lerp(north, south, fz);
    }
}