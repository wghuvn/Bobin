package com.bobincraft.drone;

import com.bobincraft.combat.CombatContent;
import com.bobincraft.destruction.DestructibleStructureRegistry;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BlastTerrain {
    public static final TagKey<Block> SHATTERABLE_GLASS = tag("drone_shatterable_glass");
    public static final TagKey<Block> SCORCHABLE_SOIL = tag("drone_scorchable_soil");
    public static final TagKey<Block> SCORCHABLE_PLANTS = tag("drone_scorchable_plants");
    public static final TagKey<Block> BREACHABLE = tag("drone_breachable");
    public static final GameRules.Key<GameRules.BooleanRule> TERRAIN_DAMAGE = initTerrainDamageRule();

    private BlastTerrain() { }

    private static GameRules.Key<GameRules.BooleanRule> initTerrainDamageRule() {
        try {
            return GameRuleRegistry.register(
                    "bobinDroneTerrainDamage", GameRules.Category.UPDATES, GameRuleFactory.createBooleanRule(true));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Force gamerule registration during mod initialization, before worlds create their rule sets. */
    public static void register() { }

    private static TagKey<Block> tag(String name) {
        return TagKey.of(RegistryKeys.BLOCK, new Identifier("bobin_craft", name));
    }

    /** Capture visibility before any edits, so breaking one window cannot expose a hidden soil layer. */
    public static Plan collect(ServerWorld world, Entity source, ServerPlayerEntity owner,
                               Vec3d center, DroneBlastType type, boolean underwater) {
        String sourceKind = com.bobincraft.destruction.StructureDamageSource
                .fromDroneBlast(source, owner != null ? owner.getUuid() : null, type, center).kind().id;
        List<BlockPos> windows = new ArrayList<>();
        List<Scorch> soil = new ArrayList<>();
        List<BlockPos> breach = new ArrayList<>();
        List<BlockPos> fires = new ArrayList<>();
        if (TERRAIN_DAMAGE != null && world.getGameRules() != null && !world.getGameRules().getBoolean(TERRAIN_DAMAGE)) {
            return new Plan(windows, soil, breach, fires, sourceKind);
        }
        boolean megaStrike = type == DroneBlastType.FP1_DEEP_STRIKE;
        double maxBreachDist = megaStrike ? 5.8 : 1.8;
        int maxBreachCount = megaStrike ? 32 : 6;
        int radius = (int) Math.ceil(Math.max(type.windowRadius, type.scorchRadius));
        BlockPos origin = BlockPos.ofFloored(center);
        Vec3d pressureOrigin = center.add(0, 0.12, 0);
        for (BlockPos mutable : BlockPos.iterate(origin.add(-radius, -radius, -radius), origin.add(radius, radius, radius))) {
            BlockPos pos = mutable.toImmutable();
            if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)
                    || owner != null && !world.canPlayerModifyAt(owner, pos)) continue;
            if (!terrainAllowed(sourceKind, pos)) continue;
            BlockState state = world.getBlockState(pos);
            double distSq = Vec3d.ofCenter(pos).squaredDistanceTo(center);

            // Never punch instant block holes inside a registered industrial structure (refinery tank, bridge, substation);
            // industrial structures are governed by StructureDamageManager's staged progressive degradation.
            boolean insideManagedStructure = DestructibleStructureRegistry.spatialIndex().findPrimaryAt(Vec3d.ofCenter(pos)) != null;

            if (!insideManagedStructure && !underwater && distSq <= maxBreachDist * maxBreachDist
                    && !state.hasBlockEntity() && state.getHardness(world, pos) >= 0) {
                boolean canBreach = (type == DroneBlastType.KAMIKAZE_BREACH && state.isIn(BREACHABLE))
                        || (megaStrike && (state.isIn(BREACHABLE) || (state.getHardness(world, pos) <= 2.5f && state.getBlock().getBlastResistance() <= 6.0f && distSq <= 3.6 * 3.6)));
                if (canBreach) {
                    var shape = state.getCollisionShape(world, pos);
                    if (!shape.isEmpty()) {
                        Vec3d face = shape.getBoundingBox().offset(pos).raycast(center, Vec3d.ofCenter(pos)).orElse(Vec3d.ofCenter(pos));
                        face = face.add(center.subtract(face).normalize().multiply(.02));
                        if (BlastRaycast.transmission(world, source, pressureOrigin, face) > 0) breach.add(pos);
                    }
                }
            }
            if (!insideManagedStructure && state.isIn(SHATTERABLE_GLASS) && distSq <= type.windowRadius * type.windowRadius
                    && BlastRaycast.transmission(world, source, pressureOrigin, Vec3d.ofCenter(pos)) > 0) windows.add(pos);
            if (underwater || !state.isIn(SCORCHABLE_SOIL)) continue;
            BlockPos above = pos.up();
            if (!world.isInBuildLimit(above) || !world.isChunkLoaded(above)) continue;
            BlockState cover = world.getBlockState(above);
            if (!world.getFluidState(above).isEmpty() || !cover.isAir() && !cover.isIn(SCORCHABLE_PLANTS)) continue;
            var plantShape = cover.getCollisionShape(world, above);
            double coverHeight = plantShape.isEmpty() ? 0 : plantShape.getMax(net.minecraft.util.math.Direction.Axis.Y);
            Vec3d top = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.04 + coverHeight, pos.getZ() + 0.5);
            double distance = top.distanceTo(center);
            if (distance > type.scorchRadius || BlastRaycast.transmission(world, source, pressureOrigin, top) <= 0) continue;
            boolean fully = distance <= type.scorchRadius * 0.52;
            if (megaStrike && fully && distance >= 2.2 && distance <= 9.5 && ((pos.getX() * 31 + pos.getZ() * 17) & 7) == 0) {
                fires.add(above);
            }
            // Further blasts can deepen an old mark, never turn charcoal back into green grass.
            if (state.isOf(CombatContent.SCORCHED_GRASS) || !fully && state.isOf(CombatContent.PARTIALLY_SCORCHED_GRASS)) continue;
            soil.add(new Scorch(pos, fully));
        }
        breach.sort(Comparator.comparingDouble(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(center)));
        return new Plan(windows, soil,
                List.copyOf(breach.subList(0, Math.min(maxBreachCount, breach.size()))),
                List.copyOf(fires.subList(0, Math.min(12, fires.size()))), sourceKind);
    }

    public record Scorch(BlockPos pos, boolean fully) { }

    private static boolean terrainAllowed(String sourceKind, BlockPos pos) {
        var target = DestructibleStructureRegistry.spatialIndex().findPrimaryAt(Vec3d.ofCenter(pos));
        return target == null || com.bobincraft.destruction.StructureDamagePolicy
                .allows(sourceKind, target.structureType().category().name());
    }

    public record Plan(List<BlockPos> windows, List<Scorch> soil, List<BlockPos> breach, List<BlockPos> fires, String sourceKind) {
        public Plan(List<BlockPos> windows, List<Scorch> soil, List<BlockPos> breach, List<BlockPos> fires) {
            this(windows, soil, breach, fires, "generic_explosion");
        }
        public Plan(List<BlockPos> windows, List<Scorch> soil, List<BlockPos> breach) {
            this(windows, soil, breach, List.of(), "generic_explosion");
        }

        public void apply(ServerWorld world, Entity source) {
            for (BlockPos pos : windows) if (world.isChunkLoaded(pos) && terrainAllowed(sourceKind, pos) && world.getBlockState(pos).isIn(SHATTERABLE_GLASS)) world.breakBlock(pos, false, source);
            int debrisSent = 0;
            for (BlockPos pos : breach) {
                if (!world.isChunkLoaded(pos) || !terrainAllowed(sourceKind, pos)) continue;
                BlockState st = world.getBlockState(pos);
                if (!st.isAir() && !st.hasBlockEntity()) {
                    if (debrisSent++ < 14) {
                        com.bobincraft.combat.ImpactDebris.send(world, st, Vec3d.ofCenter(pos).add(0, 0.3, 0),
                                new Vec3d((world.random.nextDouble() - 0.5) * 0.8, 0.85, (world.random.nextDouble() - 0.5) * 0.8).normalize(),
                                2.4f, null);
                    }
                    world.breakBlock(pos, false, source);
                }
            }
            for (Scorch mark : soil) {
                if (!world.isChunkLoaded(mark.pos()) || !world.isChunkLoaded(mark.pos().up())
                        || !terrainAllowed(sourceKind, mark.pos()) || !terrainAllowed(sourceKind, mark.pos().up())) continue;
                if (!world.getBlockState(mark.pos()).isIn(SCORCHABLE_SOIL)) continue;
                BlockPos above = mark.pos().up();
                if (world.getBlockState(above).isIn(SCORCHABLE_PLANTS)) world.breakBlock(above, false, source);
                world.setBlockState(mark.pos(), (mark.fully() ? CombatContent.SCORCHED_GRASS
                        : CombatContent.PARTIALLY_SCORCHED_GRASS).getDefaultState(), Block.NOTIFY_ALL);
            }
            for (BlockPos firePos : fires) {
                if (!world.isChunkLoaded(firePos) || !terrainAllowed(sourceKind, firePos)) continue;
                if (world.getBlockState(firePos).isAir() && world.getBlockState(firePos.down()).isSolidBlock(world, firePos.down())) {
                    world.setBlockState(firePos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }
}
