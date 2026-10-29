package com.bobincraft.raid;

import com.bobincraft.BobinCraft;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/** Forest camp materials and static props. Vanilla containers hold persistent inventory. */
public final class HubContent {
    public static final IntProperty MAP_TILE = IntProperty.of("tile", 0, 8);
    public static final Block MAP_TABLE = new MapTableBlock();
    public static final Block SUPPLY_CRATE = new Block(woodSettings());
    public static final Block TENT_FABRIC = new Block(woodSettings().strength(0.8f).sounds(BlockSoundGroup.WOOL));
    public static final Block REINFORCED_WOOD = new Block(woodSettings().strength(3.5f, 8.0f));
    public static final Block PALISADE = new Block(woodSettings().strength(3.5f, 8.0f));
    public static final Block ROOF_SHINGLES = new com.bobincraft.warzone.ConnectedSurfaceBlock(FabricBlockSettings.create()
            .mapColor(MapColor.STONE_GRAY).strength(2.5f, 6.0f).sounds(BlockSoundGroup.STONE));
    public static final Block CAMOUFLAGE_NET = new StaticPropBlock(
            woodSettings().strength(0.3f).sounds(BlockSoundGroup.WOOL).nonOpaque().noCollision(),
            Block.createCuboidShape(0, 0, 0, 16, 0.2, 16));
    public static final Block SAND_BAGS = new StaticPropBlock(
            woodSettings().strength(1.5f, 5.0f).sounds(BlockSoundGroup.GRAVEL).nonOpaque(),
            Block.createCuboidShape(0, 0, 0, 16, 16, 16));
    public static final Block AMMO_CRATE = new StaticPropBlock(
            woodSettings().strength(2.5f).nonOpaque(), Block.createCuboidShape(1, 0, 1, 15, 12, 15));
    public static final Block EQUIPMENT_LOCKER = new EquipmentLockerBlock();
    private static final VoxelShape RACK_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(1, 0, 3, 15, 2, 16),
            Block.createCuboidShape(1, 0, 14, 3, 16, 16),
            Block.createCuboidShape(13, 0, 14, 15, 16, 16),
            Block.createCuboidShape(2, 5, 13, 14, 7, 15),
            Block.createCuboidShape(2, 12, 13, 14, 14, 15),
            Block.createCuboidShape(2, 2, 9, 14, 16, 11));
    public static final Block WEAPON_RACK = new FacingPropBlock(woodSettings().nonOpaque(), RACK_SHAPE);
    public static final Block SNIPER_WEAPON_RACK = new FacingPropBlock(woodSettings().nonOpaque(), RACK_SHAPE);
    public static final Block SMG_WEAPON_RACK = new FacingPropBlock(woodSettings().nonOpaque(), RACK_SHAPE);
    public static final Block FIELD_LIGHT = new StaticPropBlock(propMetal().luminance(15), VoxelShapes.union(
            Block.createCuboidShape(4, 0, 4, 12, 2, 12),
            Block.createCuboidShape(5, 2, 5, 11, 11, 11)));
    public static final Block WALL_LIGHT = new FacingPropBlock(propMetal().luminance(15), VoxelShapes.union(
            Block.createCuboidShape(5, 3, 14, 11, 13, 16),
            Block.createCuboidShape(4.5, 4.5, 6.5, 10.5, 14, 14)));
    public static final Block FIELD_TABLE = new FacingPropBlock(woodSettings().nonOpaque(), VoxelShapes.union(
            Block.createCuboidShape(0, 14, 0, 16, 16, 16),
            Block.createCuboidShape(1, 0, 1, 3, 14, 3),
            Block.createCuboidShape(13, 0, 1, 15, 14, 3),
            Block.createCuboidShape(1, 0, 13, 3, 14, 15),
            Block.createCuboidShape(13, 0, 13, 15, 14, 15)));
    public static final Block FIELD_BENCH = new FacingPropBlock(woodSettings().nonOpaque(), VoxelShapes.union(
            Block.createCuboidShape(0, 7, 4, 16, 9, 12),
            Block.createCuboidShape(2, 0, 5, 4, 7, 11),
            Block.createCuboidShape(12, 0, 5, 14, 7, 11)));
    public static final Block RADIO = new FacingPropBlock(propMetal(), VoxelShapes.union(
            Block.createCuboidShape(2, 0, 3, 14, 8, 13),
            Block.createCuboidShape(11, 8, 10, 12, 16, 11)));
    public static final Block WARNING_SIGN = new FacingPropBlock(woodSettings().nonOpaque(), VoxelShapes.union(
            Block.createCuboidShape(1, 8, 6, 15, 15, 8),
            Block.createCuboidShape(7, 0, 6, 9, 8, 8)));
    /** Mount at the top of the hanging cloth; the lower half extends into the block below. */
    public static final Block OUTPOST_BANNER = new FacingPropBlock(
            woodSettings().strength(0.5f).sounds(BlockSoundGroup.WOOL).nonOpaque().noCollision(),
            Block.createCuboidShape(0, -16, 13, 16, 16, 16));
    public static final Block HUB_BARBED_WIRE = new FacingPropBlock(
            propMetal().strength(1.0f).noCollision(), Block.createCuboidShape(0, 0, 3, 16, 10, 13));

    private HubContent() { }

    private static FabricBlockSettings woodSettings() {
        return FabricBlockSettings.create().mapColor(MapColor.SPRUCE_BROWN).strength(2.5f, 5.0f)
                .sounds(BlockSoundGroup.WOOD);
    }

    private static FabricBlockSettings propMetal() {
        return FabricBlockSettings.create().mapColor(MapColor.DARK_GREEN).strength(3.5f, 8.0f)
                .sounds(BlockSoundGroup.METAL).nonOpaque();
    }

    public static void register() {
        register("map_table", MAP_TABLE);
        register("supply_crate", SUPPLY_CRATE);
        register("tent_fabric", TENT_FABRIC);
        register("reinforced_wood", REINFORCED_WOOD);
        register("palisade", PALISADE);
        register("roof_shingles", ROOF_SHINGLES);
        register("camouflage_net", CAMOUFLAGE_NET);
        register("sand_bags", SAND_BAGS);
        register("ammo_crate", AMMO_CRATE);
        register("equipment_locker", EQUIPMENT_LOCKER);
        register("weapon_rack", WEAPON_RACK);
        register("sniper_weapon_rack", SNIPER_WEAPON_RACK);
        register("smg_weapon_rack", SMG_WEAPON_RACK);
        register("field_light", FIELD_LIGHT);
        register("wall_light", WALL_LIGHT);
        register("field_table", FIELD_TABLE);
        register("field_bench", FIELD_BENCH);
        register("radio", RADIO);
        register("warning_sign", WARNING_SIGN);
        register("outpost_banner", OUTPOST_BANNER);
        register("hub_barbed_wire", HUB_BARBED_WIRE);
        MilitaryVehicles.registerVehicleBlocks();
        Registry.register(Registries.CHUNK_GENERATOR, new Identifier(BobinCraft.MOD_ID, "hub"), HubChunkGenerator.CODEC);
    }

    private static class StaticPropBlock extends Block {
        private final VoxelShape shape;

        private StaticPropBlock(Settings settings, VoxelShape shape) {
            super(settings);
            this.shape = shape;
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return shape;
        }
    }

    /** Facing is stored in the blockstate; baked models and collisions rotate together. */
    private static class FacingPropBlock extends HorizontalFacingBlock {
        public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
        private final VoxelShape[] shapes = new VoxelShape[4];

        private FacingPropBlock(Settings settings, VoxelShape shape) {
            super(settings);
            setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
            shapes[Direction.NORTH.getHorizontal()] = shape;
            VoxelShape current = shape;
            for (Direction direction : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                VoxelShape[] rotated = {VoxelShapes.empty()};
                current.forEachBox((x0, y0, z0, x1, y1, z1) -> rotated[0] = VoxelShapes.union(rotated[0],
                        VoxelShapes.cuboid(1 - z1, y0, x0, 1 - z0, y1, x1)));
                current = rotated[0].simplify();
                shapes[direction.getHorizontal()] = current;
            }
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext context) {
            return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
        }

        @Override
        public BlockState rotate(BlockState state, BlockRotation rotation) {
            return state.with(FACING, rotation.rotate(state.get(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, BlockMirror mirror) {
            return rotate(state, mirror.getRotation(state.get(FACING)));
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return shapes[state.get(FACING).getHorizontal()];
        }
    }

    /** Each half owns its own collision cell; normal door-style cleanup gives one lower-half drop. */
    private static final class EquipmentLockerBlock extends FacingPropBlock {
        private EquipmentLockerBlock() {
            super(propMetal(), Block.createCuboidShape(1, 0, 3, 15, 16, 15));
            setDefaultState(getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            super.appendProperties(builder);
            builder.add(Properties.DOUBLE_BLOCK_HALF);
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext context) {
            BlockPos pos = context.getBlockPos();
            if (pos.getY() >= context.getWorld().getTopY() - 1
                    || !context.getWorld().getBlockState(pos.up()).canReplace(context)) return null;
            return super.getPlacementState(context).with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            world.setBlockState(pos.up(), state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
        }

        @Override
        public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
            BlockState below = world.getBlockState(pos.down());
            if (state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                return below.isOf(this) && below.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                        && below.get(FACING) == state.get(FACING);
            }
            return below.isSideSolidFullSquare(world, pos.down(), Direction.UP);
        }

        @Override
        public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
                                                    WorldAccess world, BlockPos pos, BlockPos neighborPos) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
            if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
                return neighbor.isOf(this) && neighbor.get(Properties.DOUBLE_BLOCK_HALF) != half
                        && neighbor.get(FACING) == state.get(FACING) ? state : Blocks.AIR.getDefaultState();
            }
            if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !canPlaceAt(state, world, pos)) {
                return Blocks.AIR.getDefaultState();
            }
            return super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
        }

        @Override
        public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
            if (!world.isClient && player.isCreative()
                    && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                BlockPos lowerPos = pos.down();
                BlockState lower = world.getBlockState(lowerPos);
                if (lower.isOf(this) && lower.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                    world.setBlockState(lowerPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                    world.syncWorldEvent(player, 2001, lowerPos, Block.getRawIdFromState(lower));
                }
            }
            super.onBreak(world, pos, state, player);
        }
    }

    private static void register(String name, Block block) {
        Identifier id = new Identifier(BobinCraft.MOD_ID, name);
        Registry.register(Registries.BLOCK, id, block);
        BlockItem item = Registry.register(Registries.ITEM, id, new BlockItem(block, new FabricItemSettings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(item));
    }

    private static final class MapTableBlock extends Block {
        private static final VoxelShape TOP = Block.createCuboidShape(0, 13, 0, 16, 16, 16);

        private MapTableBlock() {
            super(woodSettings().nonOpaque());
            setDefaultState(getStateManager().getDefaultState().with(MAP_TILE, 4));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(MAP_TILE); }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            int col = state.get(MAP_TILE) % 3, row = state.get(MAP_TILE) / 3;
            if (col == 1 || row == 1) return TOP;
            int x = col == 0 ? 1 : 12, z = row == 0 ? 1 : 12;
            return VoxelShapes.union(TOP, Block.createCuboidShape(x, 0, z, x + 3, 13, z + 3));
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                  Hand hand, BlockHitResult hit) {
            if (player instanceof ServerPlayerEntity serverPlayer) RaidManager.openMap(serverPlayer, HubBuilder.TABLE);
            return ActionResult.success(world.isClient);
        }
    }
}
