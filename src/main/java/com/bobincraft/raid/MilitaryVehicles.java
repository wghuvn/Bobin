package com.bobincraft.raid;

import com.bobincraft.BobinCraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Detailed immobile wrecks; their bounded cell models and colliders share one geometry source. */
public final class MilitaryVehicles {
    /** Width, height, length in blocks, including the tank barrel and all exterior fittings. */
    public enum Kind {
        TRUCK("truck", 3, 4, 7), HUMVEE("humvee", 3, 3, 5), TANK("tank", 4, 3, 9);
        public final String id;
        public final int width, height, length;
        Kind(String id, int width, int height, int length) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.length = length;
        }
    }

    private static final Map<Kind, CellBlock[][][]> CELLS = new EnumMap<>(Kind.class);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    private MilitaryVehicles() { }

    /** Called once by HubContent. Cells intentionally have no items: a partial vehicle is invalid. */
    public static void registerVehicleBlocks() {
        if (!CELLS.isEmpty()) return;
        try (InputStream stream = MilitaryVehicles.class.getResourceAsStream(
                "/assets/bobin_craft/military_geometry.json")) {
            if (stream == null) throw new IllegalStateException("Missing military vehicle collision geometry");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("format").getAsInt() != 1) throw new IllegalStateException("Unsupported vehicle geometry format");
            for (Kind kind : Kind.values()) {
                JsonArray dimensions = root.getAsJsonObject("dimensions").getAsJsonArray(kind.id);
                if (dimensions.get(0).getAsInt() != kind.width || dimensions.get(1).getAsInt() != kind.height
                        || dimensions.get(2).getAsInt() != kind.length) {
                    throw new IllegalStateException("Vehicle model footprint changed without updating placement: " + kind.id);
                }
                CellBlock[][][] cells = new CellBlock[kind.width][kind.height][kind.length];
                for (JsonElement element : root.getAsJsonObject("vehicles").getAsJsonArray(kind.id)) {
                    JsonObject cell = element.getAsJsonObject();
                    JsonArray position = cell.getAsJsonArray("cell");
                    int x = position.get(0).getAsInt(), y = position.get(1).getAsInt(), z = position.get(2).getAsInt();
                    List<double[]> geometry = new ArrayList<>();
                    for (JsonElement shapeElement : cell.getAsJsonArray("boxes")) {
                        JsonArray box = shapeElement.getAsJsonArray();
                        double[] v = new double[6];
                        for (int i = 0; i < v.length; i++) {
                            v[i] = box.get(i).getAsDouble();
                            if (v[i] < 0 || v[i] > 16) throw new IllegalStateException("Vehicle geometry leaves its reserved cell");
                        }
                        geometry.add(v);
                    }
                    CellBlock block = new CellBlock(kind, x, y, z, geometry);
                    if (cells[x][y][z] != null) throw new IllegalStateException("Duplicate vehicle cell");
                    cells[x][y][z] = block;
                    Registry.register(Registries.BLOCK, new Identifier(BobinCraft.MOD_ID,
                            "military_" + kind.id + "_" + x + "_" + y + "_" + z), block);
                }
                for (int x = 0; x < kind.width; x++) for (int y = 0; y < kind.height; y++) {
                    for (int z = 0; z < kind.length; z++) {
                        if (cells[x][y][z] == null) throw new IllegalStateException("Unreserved vehicle cell");
                    }
                }
                CELLS.put(kind, cells);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load military vehicle geometry", error);
        }
    }

    public static int width(Kind kind, Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? kind.length : kind.width;
    }

    public static int length(Kind kind, Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? kind.width : kind.length;
    }

    /** Origin is always the minimum world-space corner; NORTH points toward negative Z. */
    public static boolean place(Map<BlockPos, BlockState> plan, BlockPos origin, Kind kind, Direction facing) {
        if (!facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Vehicle facing must be horizontal");
        CellBlock[][][] cells = CELLS.get(kind);
        if (cells == null) throw new IllegalStateException("Register military vehicles before generating the hub");
        for (int x = 0; x < width(kind, facing); x++) for (int y = 0; y < kind.height; y++) {
            for (int z = 0; z < length(kind, facing); z++) {
                BlockState previous = plan.get(origin.add(x, y, z));
                if (previous != null && !previous.isAir() && !previous.isReplaceable()) return false;
            }
        }
        for (int x = 0; x < kind.width; x++) for (int y = 0; y < kind.height; y++) {
            for (int z = 0; z < kind.length; z++) {
                BlockPos offset = offset(kind, x, y, z, facing);
                plan.put(origin.add(offset), cells[x][y][z].getDefaultState().with(HorizontalFacingBlock.FACING, facing));
            }
        }
        return true;
    }

    public static boolean isVehicle(BlockState state) {
        return state.getBlock() instanceof CellBlock;
    }

    /** Null for ordinary camp blocks. Useful for full hub-plan integrity checks. */
    public static Kind kind(BlockState state) {
        return state.getBlock() instanceof CellBlock cell ? cell.kind : null;
    }

    public static BlockPos originOf(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CellBlock cell)) throw new IllegalArgumentException("Not a vehicle cell");
        return pos.subtract(offset(cell.kind, cell.x, cell.y, cell.z, state.get(HorizontalFacingBlock.FACING)));
    }

    private static BlockPos offset(Kind kind, int x, int y, int z, Direction facing) {
        return switch (facing) {
            case NORTH -> new BlockPos(x, y, z);
            case EAST -> new BlockPos(kind.length - 1 - z, y, x);
            case SOUTH -> new BlockPos(kind.width - 1 - x, y, kind.length - 1 - z);
            case WEST -> new BlockPos(z, y, kind.width - 1 - x);
            default -> throw new IllegalArgumentException("Vehicle facing must be horizontal");
        };
    }

    /** Merge per-cell micro-cuboids into a single tight cell bounding box so nearby entity collision queries stay O(1). */
    private static List<double[]> simplifyCellCollider(List<double[]> geometry) {
        if (geometry.size() <= 2) return geometry;
        double minX = 16, minY = 16, minZ = 16, maxX = 0, maxY = 0, maxZ = 0;
        for (double[] b : geometry) {
            minX = Math.min(minX, b[0]);
            minY = Math.min(minY, b[1]);
            minZ = Math.min(minZ, b[2]);
            maxX = Math.max(maxX, b[3]);
            maxY = Math.max(maxY, b[4]);
            maxZ = Math.max(maxZ, b[5]);
        }
        return List.of(new double[]{minX, minY, minZ, maxX, maxY, maxZ});
    }

    private static final class CellBlock extends HorizontalFacingBlock {
        private final Kind kind;
        private final int x, y, z;
        private final VoxelShape[] shapes = new VoxelShape[4];

        private CellBlock(Kind kind, int x, int y, int z, List<double[]> geometry) {
            super(FabricBlockSettings.create().mapColor(MapColor.DARK_GREEN).strength(-1.0f, 3_600_000.0f)
                    .sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.BLOCK));
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
            Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            List<double[]> simplified = simplifyCellCollider(geometry);
            for (int rotation = 0; rotation < directions.length; rotation++) {
                VoxelShape shape = VoxelShapes.empty();
                for (double[] original : simplified) {
                    double[] box = original;
                    for (int turn = 0; turn < rotation; turn++) {
                        box = new double[]{16 - box[5], box[1], box[0], 16 - box[2], box[4], box[3]};
                    }
                    shape = VoxelShapes.combine(shape, Block.createCuboidShape(
                            box[0], box[1], box[2], box[3], box[4], box[5]), BooleanBiFunction.OR);
                }
                shapes[directions[rotation].getHorizontal()] = shape.simplify();
            }
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return shapes[state.get(FACING).getHorizontal()];
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return shapes[state.get(FACING).getHorizontal()];
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
        public String getTranslationKey() {
            return "block.bobin_craft.military_" + kind.id;
        }

        @Override
        public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
            return ItemStack.EMPTY;
        }

        @Override
        public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.isOf(newState.getBlock()) && !world.isClient && !REMOVING.get()) {
                Direction facing = state.get(FACING);
                BlockPos origin = originOf(pos, state);
                REMOVING.set(true);
                try {
                    CellBlock[][][] cells = CELLS.get(kind);
                    for (int a = 0; a < kind.width; a++) for (int b = 0; b < kind.height; b++) {
                        for (int c = 0; c < kind.length; c++) {
                            BlockPos cellPos = origin.add(offset(kind, a, b, c, facing));
                            if (cellPos.equals(pos)) continue;
                            BlockState current = world.getBlockState(cellPos);
                            if (current.isOf(cells[a][b][c]) && current.get(FACING) == facing) {
                                world.setBlockState(cellPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                            }
                        }
                    }
                } finally {
                    REMOVING.set(false);
                }
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
