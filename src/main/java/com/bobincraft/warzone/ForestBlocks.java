package com.bobincraft.warzone;

import com.bobincraft.BobinCraft;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Ordinary static blocks for the Warzone floor, routes, riverbanks, trunks, boulders and undergrowth. */
public final class ForestBlocks {
    public static final Block DARK_FOREST_SOIL = new Block(soil(MapColor.SPRUCE_BROWN));
    public static final Block PINE_NEEDLE_GROUND = new Block(soil(MapColor.BROWN));
    public static final Block MOSSY_FOREST_SOIL = new Block(soil(MapColor.DARK_GREEN));
    public static final Block FOREST_LITTER = new Block(soil(MapColor.BROWN));
    public static final Block TRAMPLED_MUD = new Block(mud(MapColor.TERRACOTTA_BROWN));
    public static final Block RUTTED_TRACK = new Block(soil(MapColor.DIRT_BROWN));
    public static final Block RIVER_SILT = new Block(mud(MapColor.TERRACOTTA_GRAY));
    public static final Block WEATHERED_GRAVEL = new Block(gravel(MapColor.DEEPSLATE_GRAY));
    public static final Block PEBBLE_SHOAL = new Block(gravel(MapColor.STONE_GRAY));
    public static final Block CRACKED_ASPHALT = new Block(rock(MapColor.DEEPSLATE_GRAY, 1.8f, 7.0f));
    public static final PillarBlock WEATHERED_BARK = new PillarBlock(wood().strength(2.0f));
    public static final PillarBlock DARK_PINE_WOOD = new PillarBlock(wood().strength(2.0f));
    public static final PillarBlock ROTTEN_WOOD = new PillarBlock(wood().strength(1.0f));
    public static final Block MOSS_COVERED_ROCK = new Block(rock(MapColor.STONE_GRAY, 1.5f, 6.0f));
    public static final Block WEATHERED_CRAG = new Block(rock(MapColor.DEEPSLATE_GRAY, 1.8f, 6.5f));
    public static final Block LICHEN_BOULDER = new Block(rock(MapColor.GRAY, 1.6f, 6.0f));
    public static final Block DRY_UNDERGROWTH = new ForestPlantBlock(MapColor.BROWN,
            Block.createCuboidShape(2, 0, 2, 14, 12, 14));
    public static final Block FOREST_GRASS = new ForestPlantBlock(MapColor.DARK_GREEN,
            Block.createCuboidShape(2, 0, 2, 14, 13, 14));
    public static final Block PALE_SEDGE = new ForestPlantBlock(MapColor.PALE_GREEN,
            Block.createCuboidShape(2, 0, 2, 14, 14, 14));
    public static final Block LOW_FOREST_BUSH = new ForestPlantBlock(MapColor.DARK_GREEN,
            Block.createCuboidShape(1, 0, 1, 15, 13, 15));
    public static final Block NEEDLE_UNDERGROWTH = new ForestPlantBlock(MapColor.DARK_GREEN,
            Block.createCuboidShape(1, 0, 1, 15, 7, 15));
    public static final Block RIVER_REED = new ForestPlantBlock(MapColor.PALE_GREEN,
            Block.createCuboidShape(2, 0, 2, 14, 15, 14));

    /** Dense two-block-tall grass: tall enough to hide a standing player. */
    public static final Block TALL_FOREST_GRASS = new ForestTallGrassBlock();

    private ForestBlocks() { }

    private static FabricBlockSettings soil(MapColor color) {
        return FabricBlockSettings.create().mapColor(color).strength(0.6f).sounds(BlockSoundGroup.ROOTED_DIRT);
    }

    private static FabricBlockSettings mud(MapColor color) {
        return FabricBlockSettings.create().mapColor(color).strength(0.55f).sounds(BlockSoundGroup.MUD);
    }

    private static FabricBlockSettings gravel(MapColor color) {
        return FabricBlockSettings.create().mapColor(color).strength(0.7f).sounds(BlockSoundGroup.GRAVEL);
    }

    private static FabricBlockSettings rock(MapColor color, float hardness, float resistance) {
        return FabricBlockSettings.create().mapColor(color).strength(hardness, resistance)
                .requiresTool().sounds(BlockSoundGroup.STONE);
    }

    private static FabricBlockSettings wood() {
        return FabricBlockSettings.create().mapColor(MapColor.SPRUCE_BROWN).sounds(BlockSoundGroup.WOOD);
    }

    public static void register() {
        register("dark_forest_soil", DARK_FOREST_SOIL);
        register("pine_needle_ground", PINE_NEEDLE_GROUND);
        register("mossy_forest_soil", MOSSY_FOREST_SOIL);
        register("forest_litter", FOREST_LITTER);
        register("trampled_mud", TRAMPLED_MUD);
        register("rutted_track", RUTTED_TRACK);
        register("river_silt", RIVER_SILT);
        register("weathered_gravel", WEATHERED_GRAVEL);
        register("pebble_shoal", PEBBLE_SHOAL);
        register("cracked_asphalt", CRACKED_ASPHALT);
        register("weathered_bark", WEATHERED_BARK);
        register("dark_pine_wood", DARK_PINE_WOOD);
        register("rotten_wood", ROTTEN_WOOD);
        register("moss_covered_rock", MOSS_COVERED_ROCK);
        register("weathered_crag", WEATHERED_CRAG);
        register("lichen_boulder", LICHEN_BOULDER);
        register("dry_undergrowth", DRY_UNDERGROWTH);
        register("forest_grass", FOREST_GRASS);
        register("pale_sedge", PALE_SEDGE);
        register("low_forest_bush", LOW_FOREST_BUSH);
        register("needle_undergrowth", NEEDLE_UNDERGROWTH);
        register("river_reed", RIVER_REED);
        Identifier tallId = new Identifier(BobinCraft.MOD_ID, "tall_forest_grass");
        Registry.register(Registries.BLOCK, tallId, TALL_FOREST_GRASS);
        net.minecraft.item.Item tallItem = Registry.register(Registries.ITEM, tallId,
                new net.minecraft.item.TallBlockItem(TALL_FOREST_GRASS, new FabricItemSettings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.add(tallItem));
    }

    private static void register(String name, Block block) {
        Identifier id = new Identifier(BobinCraft.MOD_ID, name);
        Registry.register(Registries.BLOCK, id, block);
        BlockItem item = Registry.register(Registries.ITEM, id, new BlockItem(block, new FabricItemSettings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.add(item));
    }

    private static final class ForestTallGrassBlock extends net.minecraft.block.TallPlantBlock {
        private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 16, 15);

        private ForestTallGrassBlock() {
            super(FabricBlockSettings.create().mapColor(MapColor.DARK_GREEN).noCollision().nonOpaque()
                    .breakInstantly().sounds(BlockSoundGroup.GRASS).pistonBehavior(PistonBehavior.DESTROY));
        }

        @Override
        protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
            return floor.isIn(BlockTags.DIRT) || floor.isOf(Blocks.SAND)
                    || floor.isOf(Blocks.RED_SAND) || floor.isOf(Blocks.GRAVEL)
                    || floor.isOf(Blocks.CLAY) || floor.isOf(Blocks.MUD)
                    || floor.isOf(WEATHERED_GRAVEL) || floor.isOf(PEBBLE_SHOAL)
                    || floor.isOf(RIVER_SILT) || floor.isOf(TRAMPLED_MUD)
                    || floor.isOf(RUTTED_TRACK);
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return SHAPE;
        }
    }

    /** PlantBlock only reacts to support changes: no random ticks, block entities or movement collision. */
    private static final class ForestPlantBlock extends PlantBlock {
        private final VoxelShape shape;

        private ForestPlantBlock(MapColor color, VoxelShape shape) {
            super(FabricBlockSettings.create().mapColor(color).noCollision().nonOpaque()
                    .breakInstantly().sounds(BlockSoundGroup.GRASS).pistonBehavior(PistonBehavior.DESTROY));
            this.shape = shape;
        }

        @Override
        protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
            return floor.isIn(BlockTags.DIRT) || floor.isOf(Blocks.SAND)
                    || floor.isOf(Blocks.RED_SAND) || floor.isOf(Blocks.GRAVEL)
                    || floor.isOf(Blocks.CLAY) || floor.isOf(Blocks.MUD)
                    || floor.isOf(WEATHERED_GRAVEL) || floor.isOf(PEBBLE_SHOAL)
                    || floor.isOf(RIVER_SILT) || floor.isOf(TRAMPLED_MUD)
                    || floor.isOf(RUTTED_TRACK);
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return shape;
        }
    }
}