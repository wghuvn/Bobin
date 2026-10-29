package com.bobincraft.warzone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.bobincraft.warzone.WarzoneNoise.smoothstep;

/**
 * Pure, bounded forest and boulder plans for the natural Warzone. Every touched chunk replays the same
 * ordered plants, including roots outside that chunk, and clips only its own block writes.
 * Nothing here loads chunks or runs after generation. Legacy forests use WarzoneFlora.
 */
public final class NaturalForest {
    static final int LOG = 1, LEAF = 2, ROCK = 3;
    static final int BARK = 0, PINE = 1, ROTTEN = 2, BIRCH = 3;
    static final int ROCK_MOSS = 0, ROCK_CRAG = 1, ROCK_LICHEN = 2, ROCK_SHOAL = 3;
    static final int Y = 0, X = 1, Z = 2;
    static final int TREE_CELL = 6, FLOOR_CELL = 6, MAX_REACH = 10;
    private static final int PLAN_CACHE_LIMIT = 4096, SHAPE_CACHE_LIMIT = 128;
    private static final int[][] SIX = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] FOUR = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    public enum Kind { PINE, SPRUCE, TWISTED, DEAD, YOUNG_PINE, BIRCH, FALLEN, STUMP, BUSH, BOULDER, BRANCH }

    public record Plant(int x, int y, int z, Kind kind, long seed, int cellX, int cellZ, boolean floor) { }

    @FunctionalInterface
    public interface Visitor { void at(int dx, int dy, int dz, int code); }

    /** Coordinates relative to one root; insertion order is stable across JVMs and chunks. */
    public static final class Shape {
        private final LinkedHashMap<Long, Integer> blocks = new LinkedHashMap<>();
        public int size() { return blocks.size(); }
        public void forEach(Visitor visitor) {
            blocks.forEach((k, c) -> visitor.at(dx(k), dy(k), dz(k), c));
        }
        private void log(int x, int y, int z, int material, int axis) {
            blocks.put(key(x, y, z), LOG | material << 4 | axis << 8);
        }
        private void leaf(int x, int y, int z, int material) {
            blocks.putIfAbsent(key(x, y, z), LEAF | material << 4);
        }
        private void rock(int x, int y, int z, int material) {
            blocks.put(key(x, y, z), ROCK | material << 4);
        }
        private void line(int x0, int y0, int z0, int x1, int y1, int z1, int material) {
            int ax = Math.abs(x1 - x0), ay = Math.abs(y1 - y0), az = Math.abs(z1 - z0);
            int axis = ay >= ax && ay >= az ? Y : ax >= az ? X : Z;
            int steps = Math.max(ax, Math.max(ay, az));
            log(x0, y0, z0, material, axis);
            int px = x0, py = y0, pz = z0;
            for (int i = 1; i <= steps; i++) {
                int tx = x0 + (int) Math.round((x1 - x0) * i / (double) steps);
                int ty = y0 + (int) Math.round((y1 - y0) * i / (double) steps);
                int tz = z0 + (int) Math.round((z1 - z0) * i / (double) steps);
                while (px != tx) { px += Integer.signum(tx - px); log(px, py, pz, material, axis); }
                while (pz != tz) { pz += Integer.signum(tz - pz); log(px, py, pz, material, axis); }
                while (py != ty) { py += Integer.signum(ty - py); log(px, py, pz, material, axis); }
            }
        }
        private void crown(int cx, int cy, int cz, double rx, double ry, double rz, int material, Random random) {
            for (int x = -(int) Math.ceil(rx); x <= (int) Math.ceil(rx); x++) {
                for (int y = -(int) Math.ceil(ry); y <= (int) Math.ceil(ry); y++) {
                    for (int z = -(int) Math.ceil(rz); z <= (int) Math.ceil(rz); z++) {
                        double d = x * x / (rx * rx) + y * y / (ry * ry) + z * z / (rz * rz);
                        if (d > 1 || d > 0.60 && random.nextDouble() < 0.17) continue;
                        leaf(cx + x, cy + y, cz + z, material);
                    }
                }
            }
        }
    }

    static int type(int code) { return code & 15; }
    static int material(int code) { return code >> 4 & 15; }
    static int axis(int code) { return code >> 8 & 3; }
    static long key(int x, int y, int z) { return (long) (x + 512) << 40 | (long) (y + 512) << 20 | z + 512L; }
    static int dx(long k) { return (int) (k >> 40 & 0xfffff) - 512; }
    static int dy(long k) { return (int) (k >> 20 & 0xfffff) - 512; }
    static int dz(long k) { return (int) (k & 0xfffff) - 512; }

    private record Cell(int x, int z, boolean floor) { }
    private record Planned(Plant plant) { }
    private final WarzoneTerrain terrain;
    private final long salt;
    private final WarzoneNoise corridors, undergrowth, boulderField;
    private final Map<Cell, Planned> plans = new LinkedHashMap<>(256, .75f, true);
    private final Map<Plant, Shape> shapes = new LinkedHashMap<>(32, .75f, true);

    public NaturalForest(WarzoneTerrain terrain) {
        this.terrain = terrain;
        salt = WarzoneNoise.mix(terrain.seed + 510);
        corridors = new WarzoneNoise(WarzoneNoise.mix(terrain.seed + 511));
        undergrowth = new WarzoneNoise(WarzoneNoise.mix(terrain.seed + 512));
        boulderField = new WarzoneNoise(WarzoneNoise.mix(terrain.seed + 519));
    }

    /** Natural winding gaps supplement explicit roads without cutting regular grid lanes. */
    public boolean movementCorridor(int x, int z) {
        return Math.abs(corridors.fbm(x / 39.0, z / 39.0, 2)) < .045;
    }

    public double undergrowth(int x, int z) {
        if (movementCorridor(x, z) || terrain.isReserved(x, z)) return 0;
        return terrain.forest(x, z) * smoothstep(-.23, .37, undergrowth.fbm(x / 15.0, z / 15.0, 2));
    }

    /** Stable tree order, followed by floor obstacles and boulders. No neighboring chunks are accessed. */
    public List<Plant> plantsForChunk(int minX, int minZ) {
        List<Plant> result = new ArrayList<>();
        collect(result, minX, minZ, false);
        collect(result, minX, minZ, true);
        return result;
    }

    private void collect(List<Plant> out, int minX, int minZ, boolean floor) {
        int cell = floor ? FLOOR_CELL : TREE_CELL;
        for (int z = Math.floorDiv(minZ - MAX_REACH, cell); z <= Math.floorDiv(minZ + 15 + MAX_REACH, cell); z++) {
            for (int x = Math.floorDiv(minX - MAX_REACH, cell); x <= Math.floorDiv(minX + 15 + MAX_REACH, cell); x++) {
                Plant p = planned(x, z, floor);
                if (p != null && p.x + MAX_REACH >= minX && p.x - MAX_REACH <= minX + 15
                        && p.z + MAX_REACH >= minZ && p.z - MAX_REACH <= minZ + 15) out.add(p);
            }
        }
    }

    private Plant planned(int cx, int cz, boolean floor) {
        Cell cell = new Cell(cx, cz, floor);
        synchronized (plans) {
            Planned found = plans.get(cell);
            if (found != null) return found.plant;
        }
        Plant plan = plan(cx, cz, floor);
        synchronized (plans) {
            plans.put(cell, new Planned(plan));
            if (plans.size() > PLAN_CACHE_LIMIT) plans.remove(plans.keySet().iterator().next());
        }
        return plan;
    }

    private Plant plan(int cx, int cz, boolean floor) {
        int cell = floor ? FLOOR_CELL : TREE_CELL;
        long hash = WarzoneNoise.hash(salt + (floor ? 1 : 0), cx, cz);
        int x = cx * cell + (int) (unit(hash, 1) * cell);
        int z = cz * cell + (int) (unit(hash, 2) * cell);
        if (!terrain.inside(x, z) || terrain.edgeDistance(x, z) < 14 || terrain.isReserved(x, z)
                || movementCorridor(x, z)) return null;
        if (terrain.isAuthoredZone() && terrain.zonePlan().protectedWithin(x,z,MAX_REACH+2)) return null;
        if (Math.hypot(x, z - terrain.landingZ()) <= 30) return null;
        // Keep tree roots and floor objects clear of route corridors so paths never slice through trees.
        if (terrain.isNatural() && !terrain.isAuthoredZone() && terrain.naturalLandscape().hasRouteWithin(x, z, floor ? 3.2 : 5.2)) return null;

        WarzoneTerrain.Sample sample = terrain.sample(x, z);
        if (sample.road > .01 || sample.path > .01 || sample.mountain > .86) return null;
        double density = terrain.forest(x, z);
        int h = sample.height;

        if (floor) {
            // Boulders and rocky outcrops spawn across ridges, river valleys, clearings, and forests alike.
            double rockZone = smoothstep(-0.18, 0.36, boulderField.fbm(x / 34.0 + 7.0, z / 34.0 - 5.0, 2));
            boolean nearWater = h >= WarzoneTerrain.SEA_LEVEL && h <= WarzoneTerrain.SEA_LEVEL + 3
                    && (sample.valley > 0.30 || sample.channel > 0.01);
            boolean rockySlope = sample.mountain > 0.14 || nearWater;
            // Big rocks reduced by 60% (x0.4).
            double boulderChance = (0.08 + 0.26 * rockZone + (rockySlope ? 0.16 : 0.0) + 0.08 * density) * 0.4;
            double choice = unit(hash, 4);
            boolean wantsBoulder = choice >= 0.42 || rockySlope && choice >= 0.22 || undergrowth(x, z) < 0.22;
            if (wantsBoulder) {
                if (h < WarzoneTerrain.SEA_LEVEL || sample.channel > 0.32 || sample.pool > 0.25) return null;
                if (unit(hash, 3) > boulderChance) return null;
                for (int[] d : FOUR) {
                    if (Math.abs(terrain.height(x + d[0] * 3, z + d[1] * 3) - h) > 5) return null;
                }
                return new Plant(x, h + 1, z, Kind.BOULDER, WarzoneNoise.mix(hash + 7), cx, cz, true);
            }
            if (h <= WarzoneTerrain.SEA_LEVEL + 1 || sample.channel > .06 || sample.pool > .1) return null;
            if (undergrowth(x, z) < .20 || unit(hash, 3) > .07 + .20 * density * density) return null;
            for (int[] d : FOUR) if (Math.abs(terrain.height(x + d[0] * 3, z + d[1] * 3) - h) > 2) return null;
            Kind kind = choice < .14 ? Kind.BUSH : choice < .26 ? Kind.FALLEN : choice < .35 ? Kind.STUMP : Kind.BRANCH;
            return new Plant(x, h + 1, z, kind, WarzoneNoise.mix(hash + 7), cx, cz, true);
        }

        if (h <= WarzoneTerrain.SEA_LEVEL + 1 || sample.channel > .06 || sample.pool > .1) return null;
        if (unit(hash, 3) > .04 + .95 * density) return null;
        // A lower-priority root yields to its immediate neighbors, avoiding adjacent trunk walls.
        if (!spaced(cx, cz, x, z, hash)) return null;
        for (int[] d : FOUR) if (Math.abs(terrain.height(x + d[0] * 3, z + d[1] * 3) - h) > 3) return null;
        double choice = unit(hash, 4);
        boolean wet = sample.region == WarzoneTerrain.Region.MARSH || sample.bog > .3;
        Kind kind = choice < (wet ? .34 : .43) ? Kind.PINE : choice < .73 ? Kind.SPRUCE
                : choice < .84 ? Kind.TWISTED : choice < .90 ? Kind.DEAD
                : choice < .96 ? Kind.YOUNG_PINE : Kind.BIRCH;
        return new Plant(x, h + 1, z, kind, WarzoneNoise.mix(hash + 7), cx, cz, false);
    }

    private boolean spaced(int cx, int cz, int x, int z, long hash) {
        double priority = WarzoneNoise.unit(hash);
        for (int oz = -1; oz <= 1; oz++) for (int ox = -1; ox <= 1; ox++) {
            if (ox == 0 && oz == 0) continue;
            long other = WarzoneNoise.hash(salt, cx + ox, cz + oz);
            if (WarzoneNoise.unit(other) >= priority) continue;
            int nx = (cx + ox) * TREE_CELL + (int) (unit(other, 1) * TREE_CELL);
            int nz = (cz + oz) * TREE_CELL + (int) (unit(other, 2) * TREE_CELL);
            if ((nx - x) * (nx - x) + (nz - z) * (nz - z) < 16) return false;
        }
        return true;
    }

    private static double unit(long hash, int offset) { return WarzoneNoise.unit(WarzoneNoise.mix(hash + offset)); }
    private static int between(Random random, int low, int high) { return low + random.nextInt(high - low + 1); }

    /** Shapes are cached only near active generation, rather than retaining an entire forest. */
    public Shape shape(Plant plant) {
        synchronized (shapes) {
            Shape found = shapes.get(plant);
            if (found != null) return found;
        }
        Shape result = rawShape(plant);
        anchor(plant, result);
        synchronized (shapes) {
            shapes.put(plant, result);
            if (shapes.size() > SHAPE_CACHE_LIMIT) shapes.remove(shapes.keySet().iterator().next());
        }
        return result;
    }

    Shape rawShape(Plant plant) {
        Shape s = new Shape();
        Random r = new Random(plant.seed);
        switch (plant.kind) {
            case PINE -> pine(s, r);
            case SPRUCE -> spruce(s, r, false);
            case TWISTED -> twisted(s, r, false);
            case DEAD -> twisted(s, r, true);
            case YOUNG_PINE -> spruce(s, r, true);
            case BIRCH -> birch(s, r);
            case FALLEN, BRANCH -> fallen(s, r, plant);
            case STUMP -> {
                s.line(0, 0, 0, 0, between(r, 1, 2), 0, ROTTEN);
                int[] d = FOUR[r.nextInt(4)];
                s.log(d[0], 0, d[1], BARK, d[0] == 0 ? Z : X);
            }
            case BUSH -> {
                s.log(0, 0, 0, BARK, Y);
                s.crown(0, 1, 0, 1.7, 1.2, 1.5, PINE, r);
            }
            case BOULDER -> boulder(s, r);
        }
        return s;
    }

    /** Multi-archetype Exclusion-Zone boulders, crags, and mossy rock clusters. */
    private void boulder(Shape s, Random r) {
        int style = r.nextInt(4);
        double rx = 1.6 + r.nextDouble() * 1.4;
        double rz = 1.5 + r.nextDouble() * 1.3;
        double ry = style == 1 ? 2.6 + r.nextDouble() * 1.4 : 1.7 + r.nextDouble() * 1.1;
        int tiltX = style == 1 ? r.nextInt(3) - 1 : 0;
        int tiltZ = style == 1 ? r.nextInt(3) - 1 : 0;
        int maxR = (int) Math.ceil(Math.max(rx, rz)) + 1;
        int maxH = (int) Math.ceil(ry) + 1;
        for (int x = -maxR; x <= maxR; x++) {
            for (int z = -maxR; z <= maxR; z++) {
                for (int y = 0; y <= maxH; y++) {
                    double ox = x - y * tiltX * 0.35;
                    double oz = z - y * tiltZ * 0.35;
                    double warp = (r.nextDouble() - 0.5) * 0.16;
                    double d = (ox * ox) / (rx * rx) + (oz * oz) / (rz * rz) + (y * y) / (ry * ry) + warp;
                    if (d > 1.0) continue;
                    int mat = y >= maxH - 1 || (y > 0 && oz < -0.3 && r.nextDouble() < 0.65)
                            ? ROCK_MOSS
                            : (d < 0.55 || y == 0 && r.nextBoolean() ? ROCK_CRAG : ROCK_LICHEN);
                    s.rock(x, y, z, mat);
                }
            }
        }
        // Add a secondary satellite rock or basal talus cluster for natural asymmetry.
        int[] dir = FOUR[r.nextInt(4)];
        int sx = dir[0] * 2, sz = dir[1] * 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 1 && r.nextBoolean()) continue;
                int px = sx + dx, pz = sz + dz;
                if (Math.abs(px) <= MAX_REACH && Math.abs(pz) <= MAX_REACH) {
                    s.rock(px, 0, pz, dx == 0 && dz == 0 ? ROCK_MOSS : ROCK_LICHEN);
                    if (dx == 0 && dz == 0 && style >= 2) s.rock(px, 1, pz, ROCK_MOSS);
                }
            }
        }
    }

    /** Tall open lower trunks and asymmetrical branch clusters distinguish pine from spruce. */
    private void pine(Shape s, Random r) {
        int h = between(r, 17, 24), bend = between(r, 7, 11);
        int[] lean = FOUR[r.nextInt(4)];
        s.line(0, 0, 0, 0, bend, 0, BARK);
        s.line(0, bend, 0, lean[0], bend + 1, lean[1], BARK);
        s.line(lean[0], bend + 1, lean[1], lean[0], h, lean[1], BARK);
        double phase = r.nextDouble() * Math.PI * 2;
        for (int i = 0, count = between(r, 5, 7); i < count; i++) {
            int y = h - 2 - i * 2;
            if (y < 9) break;
            double a = phase + i * 2.4 + r.nextDouble() * .6;
            int len = between(r, 2, 4), tx = lean[0] + (int) Math.round(Math.cos(a) * len);
            int tz = lean[1] + (int) Math.round(Math.sin(a) * len);
            s.line(lean[0], y, lean[1], tx, y + 1, tz, PINE);
            s.crown(tx, y + 1, tz, 2.1 + r.nextDouble() * .5, 1.2, 2.0 + r.nextDouble() * .5, PINE, r);
        }
        s.crown(lean[0], h, lean[1], 2.5, 1.7, 2.1, PINE, r);
        if (r.nextBoolean()) s.line(0, bend - 1, 0, -lean[1] * 2, bend - 1, lean[0] * 2, ROTTEN);
    }

    /** Uneven layered whorls, small branch tips and shifted skirts avoid a repeated solid cone. */
    private void spruce(Shape s, Random r, boolean young) {
        int h = young ? between(r, 5, 8) : between(r, 14, 21);
        s.line(0, 0, 0, 0, h, 0, young ? PINE : BARK);
        int bottom = young ? 2 : between(r, 5, 7);
        double max = young ? 2.0 : 3.5 + r.nextDouble();
        double phase = r.nextDouble() * Math.PI * 2;
        for (int y = bottom; y < h; y += 2) {
            double radius = Math.max(1, max * (h - y) / (h - bottom));
            for (int branch = 0; branch < 4; branch++) {
                if (branch == 3 && r.nextDouble() < .3) continue;
                double angle = phase + branch * Math.PI / 2 + r.nextDouble() * .45;
                int tx = (int) Math.round(Math.cos(angle) * radius);
                int tz = (int) Math.round(Math.sin(angle) * radius);
                s.line(0, y, 0, tx, y - 1, tz, PINE);
                s.crown(tx, y, tz, young ? 1.15 : 1.8, 1.1, young ? 1.15 : 1.8, PINE, r);
            }
            s.crown(0, y + 1, 0, Math.max(1.0, radius * .72), 1.15, Math.max(1.0, radius * .72), PINE, r);
            phase += .8;
        }
        s.leaf(0, h + 1, 0, PINE);
        s.leaf(0, h + 2, 0, PINE);
    }

    private void twisted(Shape s, Random r, boolean dead) {
        int h = dead ? between(r, 8, 17) : between(r, 10, 16);
        int[] lean = FOUR[r.nextInt(4)];
        int bend1 = h / 3, bend2 = 2 * h / 3;
        s.line(0, 0, 0, 0, bend1, 0, BARK);
        s.line(0, bend1, 0, lean[0] * 2, bend2, lean[1] * 2, BARK);
        s.line(lean[0] * 2, bend2, lean[1] * 2, lean[0] * 3, h, lean[1] * 3, BARK);
        int[] positions = {bend1 + 1, bend2, h - 1};
        double phase = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < positions.length; i++) {
            int y = positions[i], bx = i == 0 ? lean[0] : lean[0] * 2, bz = i == 0 ? lean[1] : lean[1] * 2;
            // Explicitly connect each branch to the bent trunk at its actual height.
            s.line(bx, y, bz, lean[0] * 2, bend2, lean[1] * 2, BARK);
            double angle = phase + i * 2.25;
            int len = dead ? between(r, 2, 4) : between(r, 3, 5);
            int tx = bx + (int) Math.round(Math.cos(angle) * len), tz = bz + (int) Math.round(Math.sin(angle) * len);
            s.line(bx, y, bz, tx, y + (dead ? 0 : 2), tz, dead ? ROTTEN : PINE);
            if (!dead || i == 2 && r.nextDouble() < .35)
                s.crown(tx, y + 2, tz, dead ? 1.4 : 2.7, 1.3, dead ? 1.4 : 2.3, PINE, r);
        }
        if (!dead) s.crown(lean[0] * 3, h, lean[1] * 3, 2.2, 1.4, 2.6, PINE, r);
    }

    private void birch(Shape s, Random r) {
        int h = between(r, 10, 15);
        int[] lean = FOUR[r.nextInt(4)];
        s.line(0, 0, 0, 0, h / 2, 0, BIRCH);
        s.line(0, h / 2, 0, lean[0], h / 2 + 1, lean[1], BIRCH);
        s.line(lean[0], h / 2 + 1, lean[1], lean[0], h, lean[1], BIRCH);
        s.crown(lean[0], h - 1, lean[1], 2.3, 3.1, 2.0, BIRCH, r);
        s.line(lean[0], h - 4, lean[1], -lean[1] * 2, h - 2, lean[0] * 2, BIRCH);
        s.crown(-lean[1] * 2, h - 2, lean[0] * 2, 1.7, 1.8, 1.7, BIRCH, r);
    }

    private void fallen(Shape s, Random r, Plant plant) {
        int[] d = FOUR[r.nextInt(4)];
        int length = plant.kind == Kind.BRANCH ? between(r, 2, 4) : between(r, 5, 9);
        int lastY = 0;
        s.log(0, 0, 0, ROTTEN, d[0] == 0 ? Z : X);
        for (int i = 1; i <= length; i++) {
            int x = d[0] * i, z = d[1] * i;
            int y = terrain.height(plant.x + x, plant.z + z) + 1 - plant.y;
            if (Math.abs(y - lastY) > 1 || Math.abs(y) > 3) break;
            s.line(d[0] * (i - 1), lastY, d[1] * (i - 1), x, y, z, ROTTEN);
            if (plant.kind == Kind.FALLEN && i == length / 2) s.line(x, y, z, x - d[1], y + 1, z + d[0], ROTTEN);
            lastY = y;
        }
    }

    /** Prune terrain collisions, reserved columns and unsupported fragments before chunk clipping. */
    private void anchor(Plant plant, Shape shape) {
        Map<Long, Integer> heights = new HashMap<>();
        Set<Long> reserved = new HashSet<>();
        boolean mature = !plant.floor && plant.kind != Kind.YOUNG_PINE;
        if (plant.kind == Kind.BOULDER) {
            // Seat each column of a boulder directly on top of sloped ground so edges never float.
            Map<Long, List<int[]>> columns = new LinkedHashMap<>();
            for (var entry : shape.blocks.entrySet()) {
                long k = entry.getKey();
                int rx = dx(k), ry = dy(k), rz = dz(k);
                int x = plant.x + rx, z = plant.z + rz;
                if (Math.abs(rx) > MAX_REACH || Math.abs(rz) > MAX_REACH || !terrain.inside(x, z)) continue;
                long colKey = (long) x << 32 ^ z & 0xffffffffL;
                int h = heights.computeIfAbsent(colKey, ignored -> {
                    if (terrain.isReserved(x, z) || movementCorridor(x, z)) reserved.add(colKey);
                    return terrain.height(x, z);
                });
                if (reserved.contains(colKey) || Math.abs(h - (plant.y - 1)) > 4) continue;
                columns.computeIfAbsent( (long) (rx + 64) << 16 | (rz + 64) & 0xffffL, ignored -> new ArrayList<>())
                        .add(new int[]{rx, ry, rz, entry.getValue(), h});
            }
            shape.blocks.clear();
            for (List<int[]> col : columns.values()) {
                for (int[] cell : col) {
                    int rx = cell[0], ry = cell[1], rz = cell[2], code = cell[3], ground = cell[4];
                    int worldY = ground + 1 + ry;
                    int newDy = worldY - plant.y;
                    if (worldY > ground && worldY < WarzoneTerrain.HEIGHT) {
                        shape.blocks.put(key(rx, newDy, rz), code);
                    }
                }
            }
            return;
        }
        shape.blocks.entrySet().removeIf(e -> {
            long k = e.getKey();
            int x = plant.x + dx(k), z = plant.z + dz(k), y = plant.y + dy(k);
            if (Math.abs(dx(k)) > MAX_REACH || Math.abs(dz(k)) > MAX_REACH || !terrain.inside(x, z)) return true;
            long column = (long) x << 32 ^ z & 0xffffffffL;
            int h = heights.computeIfAbsent(column, ignored -> {
                if (terrain.isReserved(x, z)) reserved.add(column);
                return terrain.height(x, z);
            });
            if (reserved.contains(column) || y <= h || y >= WarzoneTerrain.HEIGHT) return true;
            if (y < h + 4 && movementCorridor(x, z)) return true;
            return mature && type(e.getValue()) == LEAF && y < h + 4;
        });
        // Every log must connect by faces to an actual supported root.
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> connected = new HashSet<>();
        for (var entry : shape.blocks.entrySet()) {
            long k = entry.getKey();
            if (type(entry.getValue()) != LOG) continue;
            long column = (long) (plant.x + dx(k)) << 32 ^ (plant.z + dz(k)) & 0xffffffffL;
            if (plant.y + dy(k) == heights.get(column) + 1) { queue.add(k); connected.add(k); }
        }
        while (!queue.isEmpty()) {
            long k = queue.remove();
            for (int[] d : SIX) {
                long next = key(dx(k) + d[0], dy(k) + d[1], dz(k) + d[2]);
                Integer code = shape.blocks.get(next);
                if (code != null && type(code) == LOG && connected.add(next)) queue.add(next);
            }
        }
        shape.blocks.entrySet().removeIf(e -> type(e.getValue()) == LOG && !connected.contains(e.getKey()));
        Map<Long, Integer> distance = new HashMap<>();
        for (long k : connected) { distance.put(k, 0); queue.add(k); }
        while (!queue.isEmpty()) {
            long k = queue.remove();
            int depth = distance.get(k);
            if (depth >= 6) continue;
            for (int[] d : SIX) {
                long next = key(dx(k) + d[0], dy(k) + d[1], dz(k) + d[2]);
                Integer code = shape.blocks.get(next);
                if (code != null && type(code) == LEAF && !distance.containsKey(next)) {
                    distance.put(next, depth + 1); queue.add(next);
                }
            }
        }
        shape.blocks.entrySet().removeIf(e -> type(e.getValue()) == LEAF && !distance.containsKey(e.getKey()));
    }
}
