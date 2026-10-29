package com.bobincraft.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Coordinate-addressed forest basin: earth, rock escarpment, southern approach and
 * eastern stream. The expedition camp is built separately inside the level clearing.
 */
public final class HubChunkGenerator extends ChunkGenerator {
    public static final int FLOOR_Y = 70;
    public static final int RADIUS = 160;
    public static final int BEDROCK_Y = 38;
    public static final int CLEARING_RADIUS = 49;
    public static final int STREAM_LEVEL = 68;
    public static final Codec<HubChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource)
    ).apply(instance, instance.stable(HubChunkGenerator::new)));

    public HubChunkGenerator(BiomeSource biomeSource) { super(biomeSource); }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() { return CODEC; }

    /** Rounded, uneven forest boundary instead of an exposed square slab. */
    public static boolean inside(int x, int z) {
        if (Math.abs((long) x) > RADIUS || Math.abs((long) z) > RADIUS) return false;
        double radius = 152.0 + smoothNoise(x, z, 23, 20011) * 5.0;
        double nx = x / radius;
        double nz = z / radius;
        return nx * nx * nx * nx + nz * nz * nz * nz <= 1.0;
    }

    public static boolean isApproach(int x, int z) {
        return z >= 47 && Math.abs(x - Math.sin((z - 47) / 23.0) * 3.0) <= 5.0;
    }

    public static double streamCenter(int z) {
        double turn = Math.max(0.0, Math.min(1.0, (z - 52) / 55.0));
        turn = turn * turn * (3.0 - 2.0 * turn);
        return 63.0 + Math.sin((z + 12) / 21.0) * 5.0 + Math.sin(z / 9.0) * 1.4 - turn * 27.0;
    }

    private static double streamWidth(int z) {
        double width = 3.3 + Math.sin(z / 13.0) * 0.65;
        // Rounded plunge pools interrupt the narrow, meandering stream.
        for (int pool : new int[]{-99, -88, -81, -55, 15, 52, 82}) {
            double distance = (z - pool) / 6.0;
            width += Math.exp(-distance * distance) * 1.3;
        }
        double ends = Math.min(1.0, Math.min((z + 113) / 6.0, (105 - z) / 6.0));
        return width * Math.max(0.0, ends);
    }

    public static boolean isStream(int x, int z) {
        return inside(x, z) && z >= -111 && z <= 103
                && Math.abs(x - streamCenter(z)) <= streamWidth(z);
    }

    public static boolean isRiverMargin(int x, int z) {
        return z >= -114 && z <= 106 && Math.abs(x - streamCenter(z)) < streamWidth(z) + 2.0;
    }

    /** Source-filled pools descend through three rock-lined falls north of camp. */
    public static int waterSurface(int x, int z) {
        if (!isStream(x, z)) return BEDROCK_Y - 1;
        // A full vertical sheet joins each upper pool to the lower pool beneath it.
        return Math.max(streamLevel(z), streamLevel(z - 1));
    }

    private static int streamLevel(int z) {
        return z < -92 ? 86 : z < -85 ? 80 : z < -78 ? 74 : z < 72 ? STREAM_LEVEL : z < 91 ? 66 : 64;
    }

    public static int terrainHeight(int x, int z) {
        if (!inside(x, z)) return BEDROCK_Y - 1;
        int distance = Math.max(Math.abs(x), Math.abs(z));
        if (distance <= CLEARING_RADIUS) return FLOOR_Y;
        if (isApproach(x, z)) return FLOOR_Y + Math.max(0, (z - 102) / 5);
        double blend = Math.min(1.0, (distance - CLEARING_RADIUS) / 12.0);
        double hills = smoothNoise(x, z, 19, 23003) * 4.5 + smoothNoise(x, z, 8, 23009) * 2.2;
        hills += mound(x, z, -65, -54, 22, 19, 18)
                + mound(x, z, -69, 18, 20, 26, 13)
                + mound(x, z, 12, -73, 29, 19, 16)
                + mound(x, z, 85, 27, 24, 27, 13)
                + mound(x, z, -31, 72, 25, 22, 11);
        double outerRidge = Math.max(0.0, Math.min(1.0, (distance - 88) / 25.0)) * 10.0;
        double northRidge = Math.max(0.0, 1.0 - Math.abs(z + 94) / 27.0) * 10.0;
        double westRidge = Math.max(0.0, 1.0 - Math.abs(x + 103) / 19.0) * 6.0;
        int height = FLOOR_Y + (int) Math.round((hills + outerRidge + northRidge + westRidge) * blend);
        if (z >= 47 && z <= 111) {
            double roadDistance = Math.abs(x - Math.sin((z - 47) / 23.0) * 3.0);
            double shoulder = Math.max(0.0, Math.min(1.0, (roadDistance - 5.0) / 10.0));
            shoulder = shoulder * shoulder * (3.0 - 2.0 * shoulder);
            height = FLOOR_Y + (int)Math.round((height - FLOOR_Y) * shoulder);
        }
        if (z >= -126 && z <= 118) {
            int channelZ = Math.max(-111, Math.min(103, z));
            double crossStream = Math.abs(x - streamCenter(channelZ));
            double width = streamWidth(channelZ);
            if (z == channelZ && crossStream <= width) return streamLevel(z) - 2;
            // Continue around the ends as rounded rock basins, rather than flat caps.
            double riverDistance = Math.hypot(crossStream, z - channelZ);
            double bankDistance = Math.max(0.0, riverDistance - width);
            double transition = 12.0 + smoothNoise(x, z, 13, 23021) * 3.0;
            if (bankDistance < transition) {
                int water = streamLevel(channelZ);
                // High shoulders beside a fall contain any downstream fluid updates.
                int required = Math.max(water, streamLevel(channelZ - 7)) + 1;
                int edge = Math.max(required, Math.min(height, water + 3));
                double t = Math.min(1.0, bankDistance / transition);
                t = t * t * (3.0 - 2.0 * t);
                int shoulder = (int) Math.round(edge + (height - edge) * t);
                // The first solid bank column must always meet the highest water sheet.
                if (bankDistance <= 1.25) shoulder = Math.max(required, shoulder);
                return shoulder;
            }
        }
        return height;
    }

    /** Water is part of the terrain model, shared by chunks and column queries. */
    public static BlockState terrainState(int x, int y, int z) {
        if (!inside(x, z) || y < BEDROCK_Y) return Blocks.AIR.getDefaultState();
        int top = terrainHeight(x, z);
        return terrainStateForColumn(x, y, z, top, waterSurface(x, z));
    }

    // The terrain profile is constant for a column; do not recalculate expensive
    // height/noise during each individual block placement in spawn chunks.
    private static BlockState terrainStateForColumn(int x, int y, int z, int top, int waterLevel) {
        if (y > top) return y <= waterLevel ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
        if (y == BEDROCK_Y) return Blocks.BEDROCK.getDefaultState();
        if (y < top - 3) return Blocks.STONE.getDefaultState();
        boolean riverBank = z >= -115 && z <= 107
                && Math.abs(x - streamCenter(z)) < streamWidth(z) + 1.5;
        long patch = hash(x, z, 24001);
        if (y < top) return (riverBank ? Blocks.STONE : Blocks.DIRT).getDefaultState();
        int slope = Math.max(Math.abs(top - terrainHeight(x + 1, z)), Math.abs(top - terrainHeight(x, z + 1)));
        if ((riverBank && slope > 1) || slope >= 3 || (top >= FLOOR_Y + 25 && patch % 5 < 2)) {
            return (patch % 5 == 0 ? Blocks.MOSSY_COBBLESTONE
                    : patch % 3 == 0 ? Blocks.ANDESITE : Blocks.STONE).getDefaultState();
        }
        if (isStream(x, z)) return (patch % 3 == 0 ? Blocks.SAND : Blocks.GRAVEL).getDefaultState();
        if (isApproach(x, z)) return (patch % 23 == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT).getDefaultState();
        if (Math.max(Math.abs(x), Math.abs(z)) <= CLEARING_RADIUS) return Blocks.GRASS_BLOCK.getDefaultState();
        double soil = smoothNoise(x, z, 8, 24007);
        if (riverBank) return (soil > 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).getDefaultState();
        if (soil > 0.28) return Blocks.MOSS_BLOCK.getDefaultState();
        if (soil < -0.38) return Blocks.PODZOL.getDefaultState();
        return Blocks.GRASS_BLOCK.getDefaultState();
    }

    private static double mound(int x, int z, int cx, int cz, double rx, double rz, double height) {
        double dx = (x - cx) / rx, dz = (z - cz) / rz;
        return Math.exp(-(dx * dx + dz * dz) * 1.3) * height;
    }

    public static long hash(int x, int z, long salt) {
        long value = x * 341873128712L + z * 132897987541L + salt * 42317861L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return (value ^ (value >>> 31)) & Long.MAX_VALUE;
    }

    private static double smoothNoise(int x, int z, int spacing, long salt) {
        int gridX = Math.floorDiv(x, spacing);
        int gridZ = Math.floorDiv(z, spacing);
        double dx = Math.floorMod(x, spacing) / (double) spacing;
        double dz = Math.floorMod(z, spacing) / (double) spacing;
        dx = dx * dx * (3.0 - 2.0 * dx);
        dz = dz * dz * (3.0 - 2.0 * dz);
        double a = noiseValue(gridX, gridZ, salt);
        double b = noiseValue(gridX + 1, gridZ, salt);
        double c = noiseValue(gridX, gridZ + 1, salt);
        double d = noiseValue(gridX + 1, gridZ + 1, salt);
        return (a + (b - a) * dx) * (1.0 - dz) + (c + (d - c) * dx) * dz;
    }

    private static double noiseValue(int x, int z, long salt) {
        return (hash(x, z, salt) & 65535L) / 32767.5 - 1.0;
    }

    @Override
    public StructurePlacementCalculator createStructurePlacementCalculator(RegistryWrapper<StructureSet> registry,
                                                                          NoiseConfig noiseConfig, long seed) {
        return StructurePlacementCalculator.create(noiseConfig, seed, biomeSource, Stream.empty());
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig,
                                                 StructureAccessor structures, Chunk chunk) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        Heightmap floor = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        int x0 = chunk.getPos().getStartX();
        int z0 = chunk.getPos().getStartZ();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                if (!inside(x, z)) continue;
                int ground = terrainHeight(x, z);
                int water = waterSurface(x, z);
                int top = Math.max(ground, water);
                for (int y = BEDROCK_Y; y <= top; y++) {
                    BlockState state = terrainStateForColumn(x, y, z, ground, water);
                    chunk.setBlockState(pos.set(x, y, z), state, false);
                    floor.trackUpdate(x & 15, y, z & 15, state);
                    surface.trackUpdate(x & 15, y, z & 15, state);
                }
            }
        }
        HubForestDecorator.decorate(x0, z0, x0 + 15, z0 + 15, (x, y, z, state) -> {
            pos.set(x, y, z);
            if (!HubForestDecorator.shouldReplace(chunk.getBlockState(pos), state)) return;
            chunk.setBlockState(pos, state, false);
            floor.trackUpdate(x & 15, y, z & 15, state);
            surface.trackUpdate(x & 15, y, z & 15, state);
        });
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void carve(ChunkRegion region, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structures, Chunk chunk, GenerationStep.Carver step) { }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) { }

    @Override
    public void populateEntities(ChunkRegion region) { }

    @Override
    public int getWorldHeight() { return 384; }

    @Override
    public int getMinimumY() { return -64; }

    @Override
    public int getSeaLevel() { return STREAM_LEVEL; }

    @Override
    public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] states = columnStates(x, z, world);
        for (int i = states.length - 1; i >= 0; i--) {
            if (type.getBlockPredicate().test(states[i])) return world.getBottomY() + i + 1;
        }
        return world.getBottomY();
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        return new VerticalBlockSample(world.getBottomY(), columnStates(x, z, world));
    }

    private static BlockState[] columnStates(int x, int z, HeightLimitView world) {
        BlockState[] states = new BlockState[world.getHeight()];
        Arrays.fill(states, Blocks.AIR.getDefaultState());
        if (!inside(x, z)) return states;
        int ground = terrainHeight(x, z);
        int water = waterSurface(x, z);
        int top = Math.max(ground, water);
        for (int y = BEDROCK_Y; y <= top; y++) {
            int index = y - world.getBottomY();
            if (index >= 0 && index < states.length) states[index] = terrainStateForColumn(x, y, z, ground, water);
        }
        HubForestDecorator.decorate(x, z, x, z, (px, y, pz, state) -> {
            int index = y - world.getBottomY();
            if (index >= 0 && index < states.length && HubForestDecorator.shouldReplace(states[index], state)) {
                states[index] = state;
            }
        });
        return states;
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        text.add("Bobin Craft: expedition camp / spruce basin and eastern cascades");
    }
}

