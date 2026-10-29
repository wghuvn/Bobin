package com.bobincraft.warzone;

import com.bobincraft.BobinCraft;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;

/**
 * Registers and manages non-intrusive Exclusion-Zone ambient wildlife:
 * - Zone toads sitting on damp river/pond banks that leap into water and vanish when approached
 * - Swarms of midges/flies hovering above marsh water, reeds, and muddy tracks
 * - High-altitude crows/ravens circling and gliding across the sky
 */
public final class NaturalAmbientLife {
    public static final EntityType<ZoneToadEntity> ZONE_TOAD = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(BobinCraft.MOD_ID, "zone_toad"),
            FabricEntityTypeBuilder.create(SpawnGroup.AMBIENT, ZoneToadEntity::new)
                    .dimensions(EntityDimensions.fixed(0.35f, 0.25f))
                    .trackRangeChunks(4)
                    .trackedUpdateRate(1)
                    .build());

    public static final EntityType<FlySwarmEntity> FLY_SWARM = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(BobinCraft.MOD_ID, "fly_swarm"),
            FabricEntityTypeBuilder.create(SpawnGroup.AMBIENT, FlySwarmEntity::new)
                    .dimensions(EntityDimensions.fixed(0.85f, 0.85f))
                    .trackRangeChunks(4)
                    .trackedUpdateRate(2)
                    .disableSummon()
                    .fireImmune()
                    .build());

    public static final EntityType<SkyBirdEntity> SKY_BIRD = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(BobinCraft.MOD_ID, "sky_bird"),
            FabricEntityTypeBuilder.create(SpawnGroup.AMBIENT, SkyBirdEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 0.35f))
                    .trackRangeChunks(10)
                    .trackedUpdateRate(1)
                    .fireImmune()
                    .build());

    private static final int TICK_INTERVAL = 40;
    private static final int MAX_TOADS_NEAR_PLAYER = 4;
    private static final int MAX_SWARMS_NEAR_PLAYER = 4;
    private static final int MAX_BIRDS_NEAR_PLAYER = 6;

    private NaturalAmbientLife() { }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(NaturalAmbientLife::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        if (world.getTime() % TICK_INTERVAL != 0) return;
        WarzoneChunkGenerator generator = WarzoneDimension.generatorOf(world);
        if (generator == null) return;
        WarzoneTerrain terrain = generator.terrain();
        if (!terrain.usesRevisedLandscape()) return;

        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) return;

        long frame = world.getTime() / TICK_INTERVAL;
        for (ServerPlayerEntity player : players) {
            if (!player.isAlive() || player.isSpectator()) continue;
            long seed = WarzoneNoise.mix(terrain.seed ^ player.getUuid().getLeastSignificantBits() ^ (frame * 0x9E3779B97F4A7C15L));

            Box toadBox = player.getBoundingBox().expand(34.0, 16.0, 34.0);
            if (world.getEntitiesByType(ZONE_TOAD, toadBox, e -> true).size() < MAX_TOADS_NEAR_PLAYER) {
                trySpawnToad(world, terrain, player, seed);
            }

            Box swarmBox = player.getBoundingBox().expand(32.0, 14.0, 32.0);
            if (world.getEntitiesByType(FLY_SWARM, swarmBox, e -> true).size() < MAX_SWARMS_NEAR_PLAYER) {
                trySpawnFlySwarm(world, terrain, player, WarzoneNoise.mix(seed + 0x517CC1B727220A95L));
            }

            Box birdBox = player.getBoundingBox().expand(96.0, 72.0, 96.0);
            if (world.getEntitiesByType(SKY_BIRD, birdBox, e -> true).size() < MAX_BIRDS_NEAR_PLAYER) {
                trySpawnSkyBirds(world, terrain, player, WarzoneNoise.mix(seed + 0x2545F4914F6CDD1DL));
            }
        }
    }

    private static void trySpawnToad(ServerWorld world, WarzoneTerrain terrain, ServerPlayerEntity player, long hash) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int attempt = 0; attempt < 18; attempt++) {
            hash = WarzoneNoise.mix(hash + attempt * 31L);
            double angle = WarzoneNoise.unit(hash) * (Math.PI * 2.0);
            hash = WarzoneNoise.mix(hash);
            double dist = 9.0 + WarzoneNoise.unit(hash) * 16.0;
            int x = Math.round((float) (player.getX() + Math.cos(angle) * dist));
            int z = Math.round((float) (player.getZ() + Math.sin(angle) * dist));
            if (!terrain.inside(x, z)) continue;

            WorldChunk chunk = world.getChunkManager().getWorldChunk(x >> 4, z >> 4);
            if (chunk == null) continue;
            int y = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < WarzoneTerrain.SEA_LEVEL || y > WarzoneTerrain.SEA_LEVEL + 2) continue;

            pos.set(x, y, z);
            BlockState ground = chunk.getBlockState(pos);
            if (ground.isOf(Blocks.WATER) || ground.isAir()) continue;
            if (!chunk.getBlockState(pos.up()).isAir()) continue;

            // Find an adjacent water cell within 2..4 blocks for the toad to jump into.
            int waterX = Integer.MIN_VALUE;
            int waterZ = Integer.MIN_VALUE;
            search:
            for (int r = 1; r <= 3; r++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                        int wx = x + dx;
                        int wz = z + dz;
                        WorldChunk wChunk = world.getChunkManager().getWorldChunk(wx >> 4, wz >> 4);
                        if (wChunk == null) continue;
                        pos.set(wx, WarzoneTerrain.SEA_LEVEL, wz);
                        if (wChunk.getBlockState(pos).isOf(Blocks.WATER)) {
                            waterX = wx;
                            waterZ = wz;
                            break search;
                        }
                    }
                }
            }
            if (waterX == Integer.MIN_VALUE) continue;

            ZoneToadEntity toad = ZONE_TOAD.create(world);
            if (toad == null) return;
            toad.refreshPositionAndAngles(x + 0.5, y + 1.0, z + 0.5, 0.0f, 0.0f);
            toad.configureBankSpawn(waterX + 0.5, waterZ + 0.5, (int) Math.floorMod(hash, 3));
            world.spawnEntity(toad);
            return;
        }
    }

    private static void trySpawnFlySwarm(ServerWorld world, WarzoneTerrain terrain, ServerPlayerEntity player, long hash) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        WarzoneTerrain.Sample sample = new WarzoneTerrain.Sample();
        for (int attempt = 0; attempt < 14; attempt++) {
            hash = WarzoneNoise.mix(hash + attempt * 53L);
            double angle = WarzoneNoise.unit(hash) * (Math.PI * 2.0);
            hash = WarzoneNoise.mix(hash);
            double dist = 8.0 + WarzoneNoise.unit(hash) * 18.0;
            int x = Math.round((float) (player.getX() + Math.cos(angle) * dist));
            int z = Math.round((float) (player.getZ() + Math.sin(angle) * dist));
            if (!terrain.inside(x, z)) continue;

            terrain.sample(x, z, sample);
            boolean marshOrWaterEdge = sample.channel > 0.14 || sample.pool > 0.14
                    || sample.bog > 0.25 || sample.region == WarzoneTerrain.Region.MARSH || sample.path > 0.35;
            if (!marshOrWaterEdge) continue;

            WorldChunk chunk = world.getChunkManager().getWorldChunk(x >> 4, z >> 4);
            if (chunk == null) continue;
            int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
            pos.set(x, y + 1, z);
            if (!chunk.getBlockState(pos).isAir()) continue;

            FlySwarmEntity swarm = FLY_SWARM.create(world);
            if (swarm == null) return;
            double sy = y + 1.35 + WarzoneNoise.unit(WarzoneNoise.mix(hash + 7L)) * 0.65;
            swarm.refreshPositionAndAngles(x + 0.5, sy, z + 0.5, 0.0f, 0.0f);
            swarm.configureAnchor(x + 0.5, sy, z + 0.5, (int) hash);
            world.spawnEntity(swarm);
            return;
        }
    }

    private static void trySpawnSkyBirds(ServerWorld world, WarzoneTerrain terrain, ServerPlayerEntity player, long hash) {
        double angle = WarzoneNoise.unit(hash) * (Math.PI * 2.0);
        hash = WarzoneNoise.mix(hash);
        double dist = 24.0 + WarzoneNoise.unit(hash) * 34.0;
        double cx = player.getX() + Math.cos(angle) * dist;
        double cz = player.getZ() + Math.sin(angle) * dist;
        int bx = Math.round((float) cx);
        int bz = Math.round((float) cz);
        if (!terrain.inside(bx, bz)) return;

        int groundY = terrain.height(bx, bz);
        double baseSkyY = Math.max(groundY + 26.0, WarzoneTerrain.SEA_LEVEL + 32.0)
                + WarzoneNoise.unit(WarzoneNoise.mix(hash + 3L)) * 14.0;
        boolean circling = WarzoneNoise.unit(WarzoneNoise.mix(hash + 11L)) < 0.68;
        float radius = 18.0f + (float) (WarzoneNoise.unit(WarzoneNoise.mix(hash + 19L)) * 14.0);
        float baseAngle = (float) (WarzoneNoise.unit(WarzoneNoise.mix(hash + 23L)) * Math.PI * 2.0);
        int flockSize = circling ? (2 + (int) Math.floorMod(hash, 2)) : 2;

        for (int i = 0; i < flockSize; i++) {
            SkyBirdEntity bird = SKY_BIRD.create(world);
            if (bird == null) continue;
            float a = baseAngle + i * 0.65f;
            double sx = circling ? cx + Math.cos(a) * radius : cx + i * 3.2;
            double sz = circling ? cz + Math.sin(a) * radius : cz - i * 2.6;
            double sy = baseSkyY + i * 1.4;
            bird.refreshPositionAndAngles(sx, sy, sz, 0.0f, 0.0f);
            bird.configureFlight(cx, sy, cz, circling, radius + i * 2.5f, a, i * 19.0f + (hash & 63));
            world.spawnEntity(bird);
        }
    }
}
