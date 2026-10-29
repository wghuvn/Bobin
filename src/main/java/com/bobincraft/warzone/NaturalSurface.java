package com.bobincraft.warzone;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/** A muted S.T.A.L.K.E.R. / DayZ palette shared by dark woods, weathered ridges, worn tracks and murky rivers. */
final class NaturalSurface {
    private NaturalSurface() { }

    static void choose(WarzoneTerrain t, WarzoneTerrain.Sample s, double slope,
                       int x, int z, WarzoneSurface.Column out) {
        double r = WarzoneNoise.unit(WarzoneNoise.hash(t.seed + 501, x, z));
        out.strata = false;
        out.desertStrata = false;
        out.fillerDepth = 3;
        out.filler = Blocks.DIRT.getDefaultState();
        if (s.height < WarzoneTerrain.SEA_LEVEL) {
            if (t.usesRevisedLandscape()) waterbed(t, s, x, z, r, out);
            else {
                out.top = (s.channel > 0.35 ? (r < 0.65 ? Blocks.GRAVEL : Blocks.CLAY) : Blocks.MUD).getDefaultState();
                out.filler = Blocks.CLAY.getDefaultState();
            }
        } else if (s.road > 0.22 || s.path > 0.26) {
            Block chosen = t.usesRevisedLandscape() ? route(t, s, x, z, r)
                    : (r < (s.trail ? 0.08 : 0.25) ? Blocks.GRAVEL : Blocks.COARSE_DIRT);
            out.top = chosen.getDefaultState();
            out.filler = (chosen == ForestBlocks.CRACKED_ASPHALT || chosen == ForestBlocks.WEATHERED_GRAVEL
                    ? ForestBlocks.WEATHERED_GRAVEL : ForestBlocks.DARK_FOREST_SOIL).getDefaultState();
            out.fillerDepth = 2;
        } else if (t.usesRevisedLandscape() && (s.channel > 0.02 || s.valley > 0.36)
                && s.height <= WarzoneTerrain.SEA_LEVEL + 3) {
            riverbank(t, s, slope, x, z, r, out);
        } else if (slope > 1.25 || (s.mountain > 0.58 && slope > 0.55)) {
            double rockPatch = patch(t.seed + 557, x, z, 7);
            out.top = (rockPatch < 0.36 ? ForestBlocks.WEATHERED_CRAG
                    : rockPatch < 0.68 ? ForestBlocks.MOSS_COVERED_ROCK
                    : r < 0.65 ? ForestBlocks.LICHEN_BOULDER : Blocks.ANDESITE).getDefaultState();
            out.filler = Blocks.STONE.getDefaultState();
            out.fillerDepth = 2;
        } else if (s.region == WarzoneTerrain.Region.MARSH || s.height <= WarzoneTerrain.SEA_LEVEL + 1) {
            double marshPatch = patch(t.seed + 563, x, z, 9);
            out.top = (s.height <= WarzoneTerrain.SEA_LEVEL && r < 0.68
                    ? (marshPatch < 0.55 ? ForestBlocks.RIVER_SILT : ForestBlocks.TRAMPLED_MUD)
                    : marshPatch < 0.52 ? ForestBlocks.MOSSY_FOREST_SOIL
                    : marshPatch < 0.80 ? ForestBlocks.DARK_FOREST_SOIL : ForestBlocks.TRAMPLED_MUD).getDefaultState();
            out.filler = ForestBlocks.DARK_FOREST_SOIL.getDefaultState();
        } else {
            double forest = t.forest(x, z);
            double patch = patch(t.seed + 539, x, z, 10);
            // Broad forest ecology chooses the floor; small grain breaks up the material boundaries.
            out.top = (forest < 0.20 ? (patch < 0.42 ? ForestBlocks.FOREST_LITTER
                    : patch < 0.68 ? ForestBlocks.DARK_FOREST_SOIL : Blocks.GRASS_BLOCK)
                    : forest < 0.38 ? (r < 0.58 ? ForestBlocks.FOREST_LITTER
                    : patch < 0.62 ? ForestBlocks.DARK_FOREST_SOIL : ForestBlocks.MOSSY_FOREST_SOIL)
                    : s.bog > 0.26 ? ForestBlocks.MOSSY_FOREST_SOIL
                    : patch < 0.42 ? ForestBlocks.PINE_NEEDLE_GROUND
                    : patch < 0.76 ? ForestBlocks.DARK_FOREST_SOIL
                    : r < 0.65 ? ForestBlocks.FOREST_LITTER : ForestBlocks.MOSSY_FOREST_SOIL).getDefaultState();
            out.filler = ForestBlocks.DARK_FOREST_SOIL.getDefaultState();
        }
    }

    /** Dark, organic Exclusion-Zone riverbanks with silt, mossy earth, pebble shoals, and rocky bluffs. */
    private static void riverbank(WarzoneTerrain t, WarzoneTerrain.Sample s, double slope,
                                  int x, int z, double grain, WarzoneSurface.Column out) {
        double patch = patch(t.seed + 531, x, z, 13);
        double detail = patch(t.seed + 547, x, z, 5);
        int aboveWater = s.height - WarzoneTerrain.SEA_LEVEL;
        Block bank;
        if (slope > 0.95 || (s.waterState == WarzoneTerrain.WaterState.RAPIDS && patch > 0.44)) {
            bank = detail < 0.42 ? ForestBlocks.WEATHERED_CRAG
                    : detail < 0.76 ? ForestBlocks.MOSS_COVERED_ROCK : ForestBlocks.LICHEN_BOULDER;
            out.filler = Blocks.STONE.getDefaultState();
        } else if (aboveWater == 0) {
            if (s.bog > 0.28 || s.waterState == WarzoneTerrain.WaterState.STILL || patch < 0.36) {
                bank = grain < 0.62 ? ForestBlocks.RIVER_SILT : ForestBlocks.MOSSY_FOREST_SOIL;
            } else if (patch < 0.66) {
                bank = detail < 0.52 ? ForestBlocks.MOSSY_FOREST_SOIL : ForestBlocks.DARK_FOREST_SOIL;
            } else if (patch < 0.88) {
                bank = detail < 0.64 ? ForestBlocks.PEBBLE_SHOAL : ForestBlocks.MOSS_COVERED_ROCK;
            } else {
                bank = detail < 0.55 ? ForestBlocks.TRAMPLED_MUD : ForestBlocks.WEATHERED_GRAVEL;
            }
            out.filler = ForestBlocks.RIVER_SILT.getDefaultState();
        } else {
            // Upper bank terrace blends smoothly into dark forest soil, moss, litter, and scattered river stones.
            bank = patch < 0.34 ? ForestBlocks.MOSSY_FOREST_SOIL
                    : patch < 0.66 ? ForestBlocks.DARK_FOREST_SOIL
                    : patch < 0.86 ? ForestBlocks.FOREST_LITTER
                    : grain < 0.55 ? ForestBlocks.PINE_NEEDLE_GROUND : ForestBlocks.PEBBLE_SHOAL;
            out.filler = ForestBlocks.DARK_FOREST_SOIL.getDefaultState();
        }
        out.top = bank.getDefaultState();
    }

    private static void waterbed(WarzoneTerrain t, WarzoneTerrain.Sample s, int x, int z,
                                 double grain, WarzoneSurface.Column out) {
        int depth = WarzoneTerrain.SEA_LEVEL - s.height;
        double patch = patch(t.seed + 529, x, z, 11);
        double fine = patch(t.seed + 571, x, z, 4);
        Block bed = switch (s.waterState) {
            case STILL -> patch < 0.58 ? ForestBlocks.RIVER_SILT
                    : patch < 0.84 ? Blocks.MUD : Blocks.CLAY;
            case SLOW -> patch < 0.62 ? ForestBlocks.RIVER_SILT
                    : patch < 0.86 ? Blocks.MUD : ForestBlocks.PEBBLE_SHOAL;
            case FLOWING -> patch < 0.52 ? ForestBlocks.PEBBLE_SHOAL
                    : patch < 0.78 ? ForestBlocks.RIVER_SILT
                    : grain < 0.65 ? ForestBlocks.WEATHERED_GRAVEL : ForestBlocks.MOSS_COVERED_ROCK;
            case RAPIDS -> patch < 0.44 ? ForestBlocks.WEATHERED_CRAG
                    : patch < 0.76 ? ForestBlocks.MOSS_COVERED_ROCK
                    : fine < 0.6 ? ForestBlocks.LICHEN_BOULDER : ForestBlocks.PEBBLE_SHOAL;
            case NONE -> patch < 0.65 ? ForestBlocks.RIVER_SILT : Blocks.MUD;
        };
        if (depth <= 2 && s.waterState != WarzoneTerrain.WaterState.RAPIDS) {
            if (s.waterState == WarzoneTerrain.WaterState.STILL) {
                bed = patch < 0.64 ? ForestBlocks.RIVER_SILT : Blocks.MUD;
            } else if (s.waterState == WarzoneTerrain.WaterState.FLOWING) {
                bed = patch < 0.58 ? ForestBlocks.PEBBLE_SHOAL
                        : fine < 0.55 ? ForestBlocks.RIVER_SILT : ForestBlocks.WEATHERED_GRAVEL;
            }
        }
        out.top = bed.getDefaultState();
        out.filler = (s.waterState == WarzoneTerrain.WaterState.RAPIDS ? ForestBlocks.WEATHERED_CRAG
                : s.waterState == WarzoneTerrain.WaterState.FLOWING ? ForestBlocks.PEBBLE_SHOAL
                : ForestBlocks.RIVER_SILT).getDefaultState();
        out.fillerDepth = 3;
    }

    private static Block route(WarzoneTerrain t, WarzoneTerrain.Sample s, int x, int z, double grain) {
        double patch = patch(t.seed + 527, x, z, 8);
        double edgePatch = patch(t.seed + 541, x, z, 4);
        double strength = Math.max(s.road, s.path);
        double wear = s.routeWear;
        // Feather outer path edges into the surrounding dark forest floor so tracks never have ruler-sharp borders.
        if (strength < 0.52 && edgePatch * 0.75 + strength < 0.76) {
            return edgePatch < 0.34 ? ForestBlocks.FOREST_LITTER
                    : edgePatch < 0.66 ? ForestBlocks.DARK_FOREST_SOIL
                    : s.bog > 0.22 ? ForestBlocks.TRAMPLED_MUD : ForestBlocks.MOSSY_FOREST_SOIL;
        }
        return switch (s.routeStyle) {
            case 0 -> {
                // Wagon track: dark overgrown median strip between muddy, rutted wheel tracks.
                if (wear < 0.42) {
                    yield patch < 0.38 ? ForestBlocks.FOREST_LITTER
                            : patch < 0.68 ? ForestBlocks.MOSSY_FOREST_SOIL
                            : edgePatch < 0.58 ? ForestBlocks.DARK_FOREST_SOIL : ForestBlocks.RUTTED_TRACK;
                }
                yield (s.valley > 0.22 || s.bog > 0.18 || patch > 0.74) ? ForestBlocks.TRAMPLED_MUD
                        : wear > 0.72 && edgePatch < 0.30 ? ForestBlocks.WEATHERED_GRAVEL
                        : patch < 0.64 ? ForestBlocks.RUTTED_TRACK : ForestBlocks.DARK_FOREST_SOIL;
            }
            case 1 -> {
                // Old Zone roadway: cracked asphalt core broken by weathered gravel, potholes, and mud.
                if (wear > 0.58 && patch > 0.26 && edgePatch > 0.22) {
                    yield patch > 0.62 ? ForestBlocks.CRACKED_ASPHALT : ForestBlocks.WEATHERED_GRAVEL;
                }
                yield patch < 0.32 ? ForestBlocks.WEATHERED_GRAVEL
                        : edgePatch < 0.30 ? ForestBlocks.TRAMPLED_MUD
                        : grain < 0.45 ? ForestBlocks.RUTTED_TRACK : ForestBlocks.CRACKED_ASPHALT;
            }
            case 2 -> {
                // Woodland trail: subtle beaten earth winding through pine needles and humus.
                if (wear < 0.45) {
                    yield patch < 0.52 ? ForestBlocks.PINE_NEEDLE_GROUND : ForestBlocks.FOREST_LITTER;
                }
                yield patch < 0.42 ? ForestBlocks.RUTTED_TRACK
                        : patch < 0.74 ? ForestBlocks.DARK_FOREST_SOIL
                        : s.bog > 0.18 ? ForestBlocks.TRAMPLED_MUD : ForestBlocks.FOREST_LITTER;
            }
            case 3 -> {
                // Worn stalker footpath: narrow trodden earth and mud with scattered dark stones.
                if (wear < 0.46) {
                    yield patch < 0.48 ? ForestBlocks.DARK_FOREST_SOIL : ForestBlocks.FOREST_LITTER;
                }
                yield patch < 0.46 ? ForestBlocks.RUTTED_TRACK
                        : patch < 0.76 ? ForestBlocks.TRAMPLED_MUD
                        : grain < 0.28 ? ForestBlocks.WEATHERED_GRAVEL : ForestBlocks.DARK_FOREST_SOIL;
            }
            default -> ForestBlocks.RUTTED_TRACK;
        };
    }

    /** Smooth deterministic patches avoid noisy one-block confetti and chunk-aligned material changes. */
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