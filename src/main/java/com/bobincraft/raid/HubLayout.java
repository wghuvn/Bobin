package com.bobincraft.raid;

import net.minecraft.util.math.BlockPos;
import java.util.List;

/** Authored forest outpost plan: north is the rear, south is the gate approach. */
public final class HubLayout {
    public static final int DESIGN_VERSION = 4;
    public static final int FLOOR_Y = 70;
    public static final int PLAYER_Y = FLOOR_Y + 1;
    public static final int HUB_RADIUS = 46;
    public static final int WALL_TOP = 76;
    public static final int TOWER_DECK_Y = 77;
    public static final BlockPos MERCHANT = new BlockPos(28, PLAYER_Y, 25);
    public static final BlockPos COOK = new BlockPos(25, PLAYER_Y, -24);
    public static final BlockPos MEDIC = new BlockPos(-12, PLAYER_Y, -24);
    public static final BlockPos COMMANDER = new BlockPos(-24, PLAYER_Y, 15);
    public static final List<BlockPos> TOWERS = List.of(
            new BlockPos(-42, TOWER_DECK_Y, -42), new BlockPos(0, TOWER_DECK_Y, -42),
            new BlockPos(42, TOWER_DECK_Y, 0), new BlockPos(42, TOWER_DECK_Y, 42),
            new BlockPos(-42, TOWER_DECK_Y, 42));
    public static final List<BlockPos> GUARDS = List.of(
            new BlockPos(-5, PLAYER_Y, 40), new BlockPos(5, PLAYER_Y, 40),
            new BlockPos(-42, TOWER_DECK_Y + 1, -42), new BlockPos(0, TOWER_DECK_Y + 1, -42),
            new BlockPos(42, TOWER_DECK_Y + 1, 0), new BlockPos(42, TOWER_DECK_Y + 1, 42),
            new BlockPos(-42, TOWER_DECK_Y + 1, 42),
            new BlockPos(-17, PLAYER_Y, 16), new BlockPos(19, PLAYER_Y, 18),
            new BlockPos(-3, PLAYER_Y, 2), new BlockPos(3, PLAYER_Y, 2),
            new BlockPos(-3, PLAYER_Y, 8), new BlockPos(3, PLAYER_Y, 8));
    public enum Zone {
        CENTRAL(new Rect(-12, -9, 12, 23)),
        REST(new Rect(-38, -39, -6, -15)),
        KITCHEN(new Rect(8, -38, 38, -10)),
        DEPLOYMENT(new Rect(-39, 7, -15, 33)),
        TRADING(new Rect(15, 8, 39, 34)),
        SECURITY(new Rect(-13, 30, 13, 44));
        public final Rect bounds;
        Zone(Rect bounds) { this.bounds = bounds; }
    }
    public record Rect(int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }
    public record Path(int x1, int z1, int x2, int z2, int halfWidth) {
        public boolean contains(int x, int z) {
            return x >= Math.min(x1, x2) - halfWidth && x <= Math.max(x1, x2) + halfWidth
                    && z >= Math.min(z1, z2) - halfWidth && z <= Math.max(z1, z2) + halfWidth;
        }
    }
    public static final List<Path> PATHS = List.of(
            new Path(0, -35, 0, 39, 4),
            new Path(-34, -12, 33, -12, 2),
            new Path(-34, 18, 34, 18, 2),
            new Path(-17, -29, -17, 29, 1),
            new Path(18, -31, 18, 30, 1),
            new Path(-32, -19, -5, -19, 1),
            new Path(8, -25, 33, -25, 1),
            new Path(-33, 29, -16, 29, 1),
            new Path(20, 30, 36, 30, 1));
    public static boolean isPath(int x, int z) {
        return PATHS.stream().anyMatch(path -> path.contains(x, z));
    }
    public static boolean isPerimeter(int x, int z) {
        return Math.abs(x) <= HUB_RADIUS && Math.abs(z) <= HUB_RADIUS
                && (Math.abs(x) == HUB_RADIUS || Math.abs(z) == HUB_RADIUS);
    }
    public static boolean isReserved(int x, int z) {
        if (Math.abs(x) >= HUB_RADIUS - 7 || Math.abs(z) >= HUB_RADIUS - 7 || isPath(x, z)) return true;
        for (Zone zone : Zone.values()) if (zone.bounds.contains(x, z)) return true;
        return false;
    }
    public static void validate() {
        for (Zone zone : Zone.values()) {
            Rect r = zone.bounds;
            if (r.minX <= -HUB_RADIUS || r.maxX >= HUB_RADIUS || r.minZ <= -HUB_RADIUS || r.maxZ >= HUB_RADIUS)
                throw new IllegalStateException("Zone crosses the enclosure: " + zone);
            if (PATHS.stream().noneMatch(p -> Math.max(p.x1, p.x2) + p.halfWidth >= r.minX
                    && Math.min(p.x1, p.x2) - p.halfWidth <= r.maxX
                    && Math.max(p.z1, p.z2) + p.halfWidth >= r.minZ
                    && Math.min(p.z1, p.z2) - p.halfWidth <= r.maxZ))
                throw new IllegalStateException("Zone has no approach: " + zone);
        }
        for (BlockPos p : GUARDS) if (Math.abs(p.getX()) >= HUB_RADIUS || Math.abs(p.getZ()) >= HUB_RADIUS)
            throw new IllegalStateException("Guard outside palisade: " + p);
    }
    private HubLayout() { }
}
