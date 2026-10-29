package com.bobincraft.raid;

import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.Comparator;
import java.util.Collections;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the authored forest camp once. The blueprint contains no world or inventory state. */
public final class HubBuilder {
    public static final BlockPos SPAWN = new BlockPos(0, 71, 33);
    public static final BlockPos TABLE = new BlockPos(-25, 71, 21);
    public static final BlockPos STORAGE = new BlockPos(-30, 71, 29);
    public static final BlockPos STARTER_SUPPLIES = new BlockPos(-28, 71, 29);
    public static final BlockPos SUPPLY_TRUCK = new BlockPos(30, 71, 35);
    public static final BlockPos WRECKED_TANK = new BlockPos(-36, 71, -7);
    private static final int GROUND = HubLayout.FLOOR_Y;
    private static final int FEET = HubLayout.PLAYER_Y;
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private HubBuilder() { }

    /** The same final block plan is used for placement and offline structural validation. */
    public static Map<BlockPos, BlockState> plan() {
        HubLayout.validate();
        Camp camp = new Camp();
        camp.courtyard();
        camp.enclosure();
        for (BlockPos tower : HubLayout.TOWERS) camp.watchtower(tower.getX(), tower.getZ());
        camp.mainGate();
        camp.sleepingQuarter();
        camp.fieldKitchen();
        camp.deployment();
        camp.tradingPost();
        camp.campfire();
        camp.vehicles();
        camp.landscaping();
        return Collections.unmodifiableMap(new LinkedHashMap<>(camp.blocks));
    }

    public static void build(ServerWorld world) {
        // RaidManager calls this only for a new, uninitialized managed world.
        plan().entrySet().stream().sorted(Comparator
                .comparingInt((Map.Entry<BlockPos, BlockState> e) -> e.getKey().getY())
                .thenComparingInt(e -> e.getKey().getZ()).thenComparingInt(e -> e.getKey().getX()))
                .forEach(e -> world.setBlockState(e.getKey(), e.getValue(), FLAGS));
        stock(world, STARTER_SUPPLIES,
                new ItemStack(com.bobincraft.vehicle.ArmorContent.GRENADE, 4), new ItemStack(com.bobincraft.combat.CombatContent.TOURNIQUET, 2), new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_LEGGINGS),
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.COOKED_BEEF, 16),
                new ItemStack(Items.EMERALD, 16));
        stock(world, new BlockPos(34, FEET, -32), new ItemStack(Items.BREAD, 32),
                new ItemStack(Items.COOKED_BEEF, 32), new ItemStack(Items.BAKED_POTATO, 32));
        verify(world);
        HubNpcs.spawn(world);
        world.setSpawnPos(SPAWN, 180.0f);
    }

    private static final class Camp {
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        private void set(int x, int y, int z, BlockState state) { blocks.put(new BlockPos(x, y, z), state); }
        private void put(int x, int y, int z, Block block) { set(x, y, z, block.getDefaultState()); }
        private void column(int x, int z, int bottom, int top, Block block) {
            for (int y = bottom; y <= top; y++) put(x, y, z, block);
        }
        private void area(int x1, int z1, int x2, int z2, int y, Block block) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) put(x, y, z, block);
        }
        private void horizontal(int x, int y, int z, Block block, Direction direction) {
            set(x, y, z, block.getDefaultState().with(Properties.HORIZONTAL_FACING, direction));
        }
        private void timber(int x, int y, int z, Direction.Axis axis) {
            set(x, y, z, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, axis));
        }
        private void wallLight(int x, int y, int z, Direction outward) {
            horizontal(x, y, z, HubContent.WALL_LIGHT, outward);
        }
        private void fieldLight(int x, int z) {
            put(x, FEET, z, HubContent.FIELD_LIGHT);
        }
        private void locker(int x, int z, Direction facing) {
            BlockState state = HubContent.EQUIPMENT_LOCKER.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
            set(x, FEET, z, state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
            set(x, FEET + 1, z, state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        }
        private void crateStack(int x, int z, int height) {
            for (int y = FEET; y < FEET + height; y++) put(x, y, z, HubContent.SUPPLY_CRATE);
            put(x + 1, FEET, z, HubContent.AMMO_CRATE);
        }
        private void chest(BlockPos pos, Direction direction) {
            horizontal(pos.getX(), pos.getY(), pos.getZ(), Blocks.CHEST, direction);
        }
        private void sandbags(int x, int z, int length, boolean alongX) {
            for (int n = 0; n < length; n++) {
                int px = x + (alongX ? n : 0), pz = z + (alongX ? 0 : n);
                put(px, FEET, pz, HubContent.SAND_BAGS);
                if (n > 0 && n < length - 1) put(px, FEET + 1, pz, HubContent.SAND_BAGS);
            }
        }

        private void courtyard() {
            for (int x = -45; x <= 45; x++) for (int z = -45; z <= 45; z++) {
                boolean route = HubLayout.isPath(x, z);
                double soil = Math.sin(x * .17 + Math.cos(z * .12)) * .6 + Math.cos(z * .21 + x * .07) * .4;
                boolean plaza = x * x / 100.0 + (z - 5) * (z - 5) / 144.0 < 1.0;
                boolean entrance = Math.abs(x) <= 6 && z >= 28;
                Block surface = route || plaza || entrance
                        ? (Math.sin(x * .31) + Math.cos(z * .29) > 1.75 ? Blocks.GRAVEL
                            : soil > .55 ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT)
                        : soil > .45 ? Blocks.MOSS_BLOCK : soil < -.55 ? Blocks.PODZOL
                        : soil < -.25 ? Blocks.ROOTED_DIRT : Blocks.GRASS_BLOCK;
                put(x, GROUND, z, surface);
            }
        }

        private void enclosure() {
            int r = HubLayout.HUB_RADIUS;
            for (int n = -r; n <= r; n++) {
                wallColumn(n, -r, Direction.NORTH);
                wallColumn(n, r, Direction.SOUTH);
                if (Math.abs(n) != r) {
                    wallColumn(-r, n, Direction.WEST);
                    wallColumn(r, n, Direction.EAST);
                }
            }
            for (int n = -36; n <= 36; n += 9) {
                for (int side : new int[]{-1, 1}) {
                    column(n, side * 47, FEET, 73, Blocks.STRIPPED_SPRUCE_LOG);
                    column(side * 47, n, FEET, 73, Blocks.STRIPPED_SPRUCE_LOG);
                }
            }
            for (int n = -30; n <= 30; n += 15) {
                wallLight(n, 74, -44, Direction.SOUTH);
                wallLight(-44, 74, n, Direction.EAST);
                wallLight(44, 74, n, Direction.WEST);
            }
        }

        private void wallColumn(int x, int z, Direction outward) {
            int n = outward.getAxis() == Direction.Axis.Z ? x : z;
            int top = 75 + (int)(HubChunkGenerator.hash(x, z, 4101) % 2);
            column(x, z, FEET, top, Math.floorMod(n, 5) == 0 ? HubContent.REINFORCED_WOOD : HubContent.PALISADE);
            // A continuous wire line sits on the uneven log tips.
            horizontal(x, top + 1, z, HubContent.HUB_BARBED_WIRE, outward);
            int ix = x - outward.getOffsetX(), iz = z - outward.getOffsetZ();
            timber(ix, 72, iz, outward.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
            timber(ix, 74, iz, outward.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
        }

        private void watchtower(int cx, int cz) {
            int deck = HubLayout.TOWER_DECK_Y;
            for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3})
                column(cx + dx, cz + dz, FEET, deck + 5, Blocks.STRIPPED_SPRUCE_LOG);
            area(cx - 3, cz - 3, cx + 3, cz + 3, deck, Blocks.SPRUCE_PLANKS);
            for (int n = -3; n <= 3; n++) {
                timber(cx + n, deck - 1, cz - 3, Direction.Axis.X);
                timber(cx + n, deck - 1, cz + 3, Direction.Axis.X);
                timber(cx - 3, deck - 1, cz + n, Direction.Axis.Z);
                timber(cx + 3, deck - 1, cz + n, Direction.Axis.Z);
                rail(cx + n, deck + 1, cz - 3, true);
                rail(cx + n, deck + 1, cz + 3, true);
                rail(cx - 3, deck + 1, cz + n, false);
                rail(cx + 3, deck + 1, cz + n, false);
            }
            // A ladder reaches a genuine deck opening, with a solid backing log.
            column(cx - 3, cz, FEET, deck, Blocks.STRIPPED_SPRUCE_LOG);
            for (int y = FEET; y <= deck; y++)
                set(cx - 2, y, cz, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.EAST));
            // Structural posts remain continuous through the rail instead of balancing on a fence.
            for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3})
                column(cx + dx, cz + dz, FEET, deck + 5, Blocks.STRIPPED_SPRUCE_LOG);
            // Fill below each raised roof course: diagonal contacts alone leave sky gaps.
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                int roof = deck + 5 + Math.min(2, (4 - Math.abs(dx)) / 2);
                for (int y = deck + 5; y <= roof; y++) put(cx + dx, y, cz + dz, HubContent.ROOF_SHINGLES);
            }
            wallLight(cx + 2, deck + 3, cz + 3, Direction.WEST);
            wallLight(cx - 2, deck + 3, cz - 3, Direction.EAST);
            horizontal(cx + 2, deck + 1, cz - 1, HubContent.RADIO, Direction.SOUTH);
            Direction outward = cz == 42 ? Direction.SOUTH : cz == -42 ? Direction.NORTH : Direction.EAST;
            int bx = cx + outward.getOffsetX() * 4, bz = cz + outward.getOffsetZ() * 4;
            horizontal(bx, deck, bz, HubContent.OUTPOST_BANNER, outward);
            column(cx, cz, deck + 7, deck + 10, Blocks.SPRUCE_FENCE);
            horizontal(cx, deck + 10, cz + 1, HubContent.OUTPOST_BANNER, Direction.SOUTH);
        }
        private void rail(int x, int y, int z, boolean alongX) {
            set(x, y, z, Blocks.SPRUCE_FENCE.getDefaultState()
                    .with(Properties.EAST, alongX).with(Properties.WEST, alongX)
                    .with(Properties.NORTH, !alongX).with(Properties.SOUTH, !alongX));
        }

        private void mainGate() {
            int z = HubLayout.HUB_RADIUS;
            for (int x = -7; x <= 7; x++) {
                column(x, z, FEET, 76, HubContent.REINFORCED_WOOD);
                timber(x, 77, z, Direction.Axis.X);
                if (Math.abs(x) <= 5) {
                    // Cross braces are raised on the outer face of the closed double gate.
                    int y = FEET + Math.abs(x);
                    set(x, y, z + 1, Blocks.SPRUCE_TRAPDOOR.getDefaultState()
                            .with(Properties.OPEN, true).with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
                }
            }
            for (int x : new int[]{-8, 8}) {
                column(x, z, FEET, 78, Blocks.STRIPPED_SPRUCE_LOG);
                horizontal(x, 76, z + 1, HubContent.OUTPOST_BANNER, Direction.SOUTH);
            }
            for (int x : new int[]{-6, 6}) {
                wallLight(x, 76, 45, Direction.NORTH);
                wallLight(x, 76, 47, Direction.SOUTH);
            }
            sandbags(-15, 40, 7, true);
            sandbags(9, 40, 7, true);
            sandbags(-15, 49, 7, true);
            sandbags(9, 49, 7, true);
            for (int x : new int[]{-15, 15}) {
                column(x, 54, FEET, FEET + 2, Blocks.SPRUCE_FENCE);
                timber(x, FEET + 1, 54, Direction.Axis.X);
                timber(x - 1, FEET, 54, Direction.Axis.X);
                timber(x + 1, FEET + 2, 54, Direction.Axis.X);
            }
            fieldLight(-5, 54);
            fieldLight(6, 55);
        }

        private void tent(int cx, int cz, int halfWidth, int halfDepth) {
            area(cx - halfWidth, cz - halfDepth, cx + halfWidth, cz + halfDepth, GROUND, Blocks.COARSE_DIRT);
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                int roof = FEET + 3 + (halfWidth - Math.abs(dx)) / 2;
                for (int dz = -halfDepth; dz <= halfDepth; dz++) {
                    for (int y = FEET + 3; y <= roof; y++) put(cx + dx, y, cz + dz, HubContent.TENT_FABRIC);
                    if (Math.abs(dx) == halfWidth) column(cx + dx, cz + dz, FEET, roof - 1, HubContent.TENT_FABRIC);
                    if (dz == -halfDepth) column(cx + dx, cz + dz, FEET, roof - 1, HubContent.TENT_FABRIC);
                }
            }
            for (int side : new int[]{-1, 1}) {
                column(cx + side * halfWidth, cz + halfDepth, FEET, FEET + 3, Blocks.STRIPPED_SPRUCE_LOG);
                column(cx + side * (halfWidth + 1), cz + halfDepth + 1, FEET, FEET + 1, Blocks.SPRUCE_FENCE);
                wallLight(cx + side * (halfWidth - 1), FEET + 2, cz + halfDepth,
                        side < 0 ? Direction.EAST : Direction.WEST);
            }
        }
        private void sleepingQuarter() {
            tent(-31, -26, 5, 6);
            tent(-18, -30, 5, 6);
            // Shift only the shelter shell: its old rear corner cut through the central tower leg.
            // Beds and storage keep their existing coordinates and inventory identities.
            tent(-7, -32, 4, 5);
            for (int[] center : new int[][]{{-31, -26}, {-18, -30}, {-7, -34}}) {
                bed(center[0] - 2, center[1], Direction.NORTH);
                bed(center[0] + 2, center[1], Direction.NORTH);
                crateStack(center[0] - 2, center[1] + 3, 1);
                chest(new BlockPos(center[0] + 2, FEET, center[1] + 3), Direction.SOUTH);
            }
            // Small medical station on the open edge of the sleeping quarter.
            horizontal(-11, FEET, -26, HubContent.FIELD_TABLE, Direction.NORTH);
            put(-11, FEET + 1, -26, Blocks.BREWING_STAND);
            chest(new BlockPos(-10, FEET, -26), Direction.SOUTH);
            crateStack(-36, -16, 2);
            sandbags(-33, -16, 6, true);
            fieldLight(-25, -18);
        }
        private void bed(int x, int z, Direction facing) {
            BlockState state = Blocks.GREEN_BED.getDefaultState().with(BedBlock.FACING, facing);
            set(x, FEET, z, state.with(BedBlock.PART, BedPart.FOOT));
            set(x + facing.getOffsetX(), FEET, z + facing.getOffsetZ(), state.with(BedBlock.PART, BedPart.HEAD));
        }

        /** Roof base is five blocks above the floor; beams and lamps stay above head height. */
        private void awning(int x1, int z1, int x2, int z2, int y, boolean net) {
            if (y < FEET + 5) throw new IllegalArgumentException("Service shelter needs clear standing headroom");
            int middle = (x1 + x2) / 2;
            for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2})
                column(x, z, FEET, y - 1, Blocks.STRIPPED_SPRUCE_LOG);
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                boolean frame = x == x1 || x == x2 || z == z1 || z == z2 || x == middle;
                if (frame) timber(x, y - 1, z, x == x1 || x == x2 || x == middle ? Direction.Axis.Z : Direction.Axis.X);
                if (net) put(x, y, z, HubContent.CAMOUFLAGE_NET);
                else {
                    int rise = Math.abs(x - middle) <= (x2 - x1) / 4 ? 1 : 0;
                    for (int roof = y; roof <= y + rise; roof++) put(x, roof, z, HubContent.TENT_FABRIC);
                }
            }
            for (int x : new int[]{x1 + 1, x2 - 1}) {
                wallLight(x, y - 2, z2, x == x1 + 1 ? Direction.EAST : Direction.WEST);
            }
        }
        private void fieldKitchen() {
            awning(10, -35, 22, -25, 76, false);
            awning(28, -32, 38, -23, 76, false);
            for (int z = -32; z <= -27; z++) {
                put(12, FEET, z, Blocks.STONE_BRICKS);
                horizontal(13, FEET, z, HubContent.FIELD_TABLE, Direction.NORTH);
            }
            horizontal(12, FEET, -30, Blocks.SMOKER, Direction.EAST);
            horizontal(12, FEET, -28, Blocks.FURNACE, Direction.EAST);
            put(13, FEET + 1, -30, Blocks.CAULDRON);
            put(13, FEET + 1, -28, Blocks.CAULDRON);
            put(13, GROUND, -25, Blocks.HAY_BLOCK);
            put(13, FEET, -25, Blocks.CAMPFIRE);
            column(12, -26, FEET + 1, 78, Blocks.COBBLESTONE);
            for (int x = 27; x <= 34; x++) horizontal(x, FEET, -26, HubContent.FIELD_TABLE, Direction.SOUTH);
            horizontal(28, FEET + 1, -26, HubContent.RADIO, Direction.SOUTH);
            chest(new BlockPos(34, FEET, -32), Direction.SOUTH);
            put(36, FEET, -31, Blocks.BARREL);
            put(36, FEET + 1, -31, Blocks.BARREL);
            put(37, FEET, -31, Blocks.BARREL);
            diningTable(29, -20, 7);
            diningTable(29, -16, 7);
            for (int x : new int[]{25, 35}) for (int z : new int[]{-20, -9}) put(x, FEET, z, Blocks.BARREL);
            crateStack(20, -33, 2);
            fieldLight(8, -20);
        }
        private void diningTable(int cx, int z, int length) {
            for (int n = -length / 2; n <= length / 2; n++)
                horizontal(cx + n, FEET, z, HubContent.FIELD_TABLE, Direction.SOUTH);
            bench(cx, z - 1, length, Direction.SOUTH);
            bench(cx, z + 1, length, Direction.NORTH);
            put(cx, FEET + 1, z, HubContent.FIELD_LIGHT);
        }
        private void bench(int cx, int cz, int length, Direction face) {
            for (int n = -length / 2; n <= length / 2; n++)
                horizontal(cx + (face.getAxis() == Direction.Axis.Z ? n : 0), FEET,
                        cz + (face.getAxis() == Direction.Axis.X ? n : 0), HubContent.FIELD_BENCH, face);
        }

        private void deployment() {
            awning(-37, -8, -27, 1, 76, true);
            crateStack(-30, -6, 2);
            crateStack(-30, -2, 1);
            sandbags(-35, 3, 7, true);
            awning(-38, 7, -28, 16, 76, true);
            for (int z = 8; z <= 23; z++) {
                column(-38, z, FEET, 73, HubContent.REINFORCED_WOOD);
                if ((z & 1) == 0) horizontal(-37, FEET, z,
                        z % 6 == 0 ? HubContent.SNIPER_WEAPON_RACK : z % 6 == 2 ? HubContent.SMG_WEAPON_RACK : HubContent.WEAPON_RACK,
                        Direction.EAST);
            }
            for (int x = -35; x <= -29; x += 2) {
                locker(x, 9, Direction.SOUTH);
            }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                set(TABLE.getX() + dx, FEET, TABLE.getZ() + dz,
                        HubContent.MAP_TABLE.getDefaultState().with(HubContent.MAP_TILE, (dz + 1) * 3 + dx + 1));
            horizontal(-27, FEET, 17, HubContent.RADIO, Direction.SOUTH);
            crateStack(-34, 25, 2);
            crateStack(-20, 24, 1);
            chest(STORAGE, Direction.SOUTH);
            chest(STARTER_SUPPLIES, Direction.SOUTH);
            for (int x : new int[]{-34, -32}) put(x, FEET, 30, HubContent.AMMO_CRATE);
            sandbags(-36, 33, 9, true);
            fieldLight(-16, 26);
        }

        private void tradingPost() {
            awning(29, -5, 38, 5, 76, true);
            crateStack(31, -2, 2);
            crateStack(35, 2, 3);
            put(36, FEET, -2, Blocks.BARREL);
            sandbags(29, 7, 6, true);
            awning(23, 18, 37, 30, 76, false);
            for (int x = 24; x <= 36; x++) {
                column(x, 18, FEET, 73, HubContent.REINFORCED_WOOD);
                if ((x & 1) == 0) horizontal(x, FEET + 1, 19,
                        x % 6 == 0 ? HubContent.SNIPER_WEAPON_RACK : x % 6 == 2 ? HubContent.SMG_WEAPON_RACK : HubContent.WEAPON_RACK,
                        Direction.SOUTH);
            }
            for (int x = 25; x <= 33; x++) {
                horizontal(x, FEET, 27, HubContent.FIELD_TABLE, Direction.SOUTH);
            }
            horizontal(26, FEET + 1, 27, HubContent.RADIO, Direction.SOUTH);
            for (int x : new int[]{24, 36}) locker(x, 22, Direction.SOUTH);
            put(35, FEET, 25, Blocks.ANVIL);
            put(35, FEET, 23, Blocks.CRAFTING_TABLE);
            crateStack(22, 24, 2);
            crateStack(38, 27, 2);
            sandbags(23, 33, 7, true);
            awning(32, 7, 39, 14, 76, true);
            crateStack(33, 10, 2);
            crateStack(36, 11, 2);
            fieldLight(17, 31);
        }
        private void vehicles() {
            if (!MilitaryVehicles.place(blocks, SUPPLY_TRUCK, MilitaryVehicles.Kind.TRUCK, Direction.EAST)
                    || !MilitaryVehicles.place(blocks, WRECKED_TANK, MilitaryVehicles.Kind.TANK, Direction.NORTH))
                throw new IllegalStateException("Authored vehicle bay intersects another camp structure");
        }

        private void campfire() {
            for (int x = -8; x <= 8; x++) for (int z = -3; z <= 13; z++) {
                int d = x * x + (z - 5) * (z - 5);
                if (d <= 64) put(x, GROUND, z, d < 10 ? Blocks.GRAVEL : Blocks.COARSE_DIRT);
            }
            put(0, FEET, 5, Blocks.CAMPFIRE);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0) set(dx, FEET, 5 + dz,
                        Blocks.COBBLESTONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
            bench(0, -1, 7, Direction.SOUTH);
            bench(0, 11, 7, Direction.NORTH);
            bench(-6, 5, 5, Direction.EAST);
            bench(6, 5, 5, Direction.WEST);
            crateStack(-8, 11, 1);
            put(8, FEET, 2, HubContent.AMMO_CRATE);
            fieldLight(-9, -3);
            fieldLight(8, 14);
        }

        private void landscaping() {
            for (int[] patch : new int[][]{
                    {-10,-7,6,7},{10,-2,6,7},{-10,23,5,7},{10,29,5,8},
                    {-24,-1,4,5},{25,9,5,5},{-26,38,9,4},{30,-39,8,3},
                    {-41,-12,3,7},{-41,22,3,9},{40,-22,3,10},{-14,41,6,3},{16,42,6,3}})
                plantedIsland(patch[0], patch[1], patch[2], patch[3]);
            courtyardTree(-10, -7);
            courtyardTree(10, -2);
            courtyardTree(-25, -17);
            courtyardTree(8, -19);
            courtyardTree(-34, 35);
            livingFloor();
            sandbags(-43, -31, 7, false);
            sandbags(42, -31, 7, false);
            crateStack(-39, 31, 2);
            crateStack(38, -36, 2);
            for (int[] p : new int[][]{{-12,34},{12,36},{-37,-5},{37,-5},{-6,-16},{6,-26}})
                fieldLight(p[0], p[1]);
        }
        private void livingFloor() {
            for (int x = -44; x <= 44; x++) for (int z = -44; z <= 44; z++) {
                BlockPos ground = new BlockPos(x, GROUND, z);
                BlockState soil = blocks.get(ground);
                if (soil == null || (!soil.isOf(Blocks.GRASS_BLOCK) && !soil.isOf(Blocks.MOSS_BLOCK))) continue;
                if (HubLayout.isPath(x, z) || blocks.containsKey(new BlockPos(x, FEET, z)) || standingAnchor(x, z)) continue;
                long growth = HubChunkGenerator.hash(x, z, 70139);
                if (growth % 13 < 4) put(x, FEET, z, Blocks.GRASS);
                else if (growth % 13 < 7) put(x, FEET, z, Blocks.FERN);
                else if (growth % 13 == 7) put(x, FEET, z, Blocks.AZURE_BLUET);
                else if (growth % 13 == 8) put(x, FEET, z, Blocks.MOSS_CARPET);
            }
        }
        private boolean standingAnchor(int x, int z) {
            for (BlockPos p : new BlockPos[]{SPAWN, HubLayout.MERCHANT, HubLayout.COOK, HubLayout.MEDIC, HubLayout.COMMANDER})
                if (Math.abs(p.getX() - x) <= 1 && Math.abs(p.getZ() - z) <= 1) return true;
            for (BlockPos p : HubLayout.GUARDS) if (Math.abs(p.getX() - x) <= 1 && Math.abs(p.getZ() - z) <= 1) return true;
            return false;
        }

        private void plantedIsland(int cx, int cz, int rx, int rz) {
            for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) {
                double edge = .86 + Math.sin(dx * 1.7 + dz * .9) * .14;
                if (dx * dx / (double)(rx * rx) + dz * dz / (double)(rz * rz) > edge) continue;
                int x = cx + dx, z = cz + dz;
                if (Math.abs(x) >= 44 || Math.abs(z) >= 44 || HubLayout.isPath(x, z)
                        || blocks.containsKey(new BlockPos(x, FEET, z))) continue;
                long growth = HubChunkGenerator.hash(x, z, 59173);
                put(x, GROUND, z, Blocks.GRASS_BLOCK);
                if (growth % 100 < 53) {
                    set(x, FEET, z, Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
                    if (growth % 19 == 0) set(x, FEET + 1, z,
                            Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
                } else if (growth % 100 < 75) put(x, FEET, z, Blocks.FERN);
                else if (growth % 100 < 87) put(x, FEET, z, Blocks.DANDELION);
                else if (growth % 100 < 96) put(x, FEET, z, Blocks.AZURE_BLUET);
                else put(x, FEET, z, Blocks.MOSS_CARPET);
            }
        }
        private void courtyardTree(int x, int z) {
            BlockState leaves = Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
            for (int level = 4; level <= 9; level++) {
                int radius = Math.min(3, (10 - level) / 2);
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius > 0) continue;
                    set(x + dx, GROUND + level, z + dz, leaves);
                }
            }
            column(x, z, FEET, GROUND + 8, Blocks.SPRUCE_LOG);
        }
    }

    private static void stock(ServerWorld world, BlockPos pos, ItemStack... stacks) {
        if (!(world.getBlockEntity(pos) instanceof Inventory inventory))
            throw new IllegalStateException("Missing camp inventory at " + pos);
        for (int i = 0; i < Math.min(stacks.length, inventory.size()); i++) inventory.setStack(i, stacks[i]);
        inventory.markDirty();
    }
    private static void verify(ServerWorld world) {
        int r = HubLayout.HUB_RADIUS;
        for (int n = -r; n <= r; n++) for (int y = FEET; y <= 75; y++)
            for (BlockPos pos : new BlockPos[]{new BlockPos(n, y, -r), new BlockPos(n, y, r),
                    new BlockPos(-r, y, n), new BlockPos(r, y, n)})
                if (!world.getBlockState(pos).isFullCube(world, pos))
                    throw new IllegalStateException("Open palisade at " + pos);
        if (!world.getBlockState(TABLE).isOf(HubContent.MAP_TABLE)) throw new IllegalStateException("Missing map table");
        if (!(world.getBlockEntity(STORAGE) instanceof Inventory) || !(world.getBlockEntity(STARTER_SUPPLIES) instanceof Inventory))
            throw new IllegalStateException("Missing persistent camp storage");
        verifyAnchor(world, SPAWN);
        for (BlockPos pos : new BlockPos[]{HubLayout.MERCHANT, HubLayout.COOK, HubLayout.MEDIC, HubLayout.COMMANDER}) verifyAnchor(world, pos);
        for (BlockPos pos : HubLayout.GUARDS) verifyAnchor(world, pos);
    }
    private static void verifyAnchor(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                || !world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
                || !world.getBlockState(pos.down()).isFullCube(world, pos.down()))
            throw new IllegalStateException("Obstructed camp arrival or NPC at " + pos);
    }
}
