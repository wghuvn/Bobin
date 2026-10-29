package com.bobincraft.warzone;

import java.util.List;

/**
 * Authored Eastern European / post-Soviet settlement, roadside, forest, agricultural, and
 * industrial structures for the Black Alder exclusion zone.
 */
final class ZoneExpansionStructures {
    private final WarzoneTerrain t;
    private final ZonePlan.Site s;
    private final Blueprint bp;
    private int curCx;
    private int curCz;
    private int curRot;

    ZoneExpansionStructures(WarzoneTerrain t, ZonePlan.Site s, Blueprint bp) {
        this.t = t;
        this.s = s;
        this.bp = bp;
    }

    static List<int[]> extraPads(ZonePlan.Site s) {
        if (s.id() >= 100) {
            return List.of(new int[]{14, -24, 3, 3});
        }
        return switch (s.theme()) {
            case ALDER -> List.of(
                    new int[]{-48, -40, 9, 7},
                    new int[]{37, -45, 7, 5},
                    new int[]{46, -8, 7, 5},
                    new int[]{48, 16, 8, 7},
                    new int[]{-46, -8, 7, 5},
                    new int[]{56, -26, 3, 3}
            );
            case BRICK -> List.of(
                    new int[]{0, -48, 9, 6},
                    new int[]{-48, -50, 8, 6},
                    new int[]{50, -16, 7, 5},
                    new int[]{50, 8, 6, 5},
                    new int[]{46, -54, 10, 5},
                    new int[]{28, 50, 5, 3},
                    new int[]{58, 36, 3, 3}
            );
            case PINE -> List.of(
                    new int[]{-44, -44, 8, 6},
                    new int[]{40, -44, 6, 6},
                    new int[]{36, -6, 9, 5},
                    new int[]{20, 42, 8, 6},
                    new int[]{52, 42, 5, 3},
                    new int[]{50, 20, 3, 3}
            );
            case FARM -> List.of(
                    new int[]{-22, -56, 11, 7},
                    new int[]{8, -54, 9, 6},
                    new int[]{52, -56, 8, 7},
                    new int[]{54, 28, 6, 5},
                    new int[]{18, 50, 5, 3},
                    new int[]{-58, -14, 3, 3}
            );
            case SAWMILL -> List.of(
                    new int[]{35, -9, 8, 5},
                    new int[]{-30, 10, 7, 6},
                    new int[]{-10, 31, 4, 3},
                    new int[]{25, 49, 5, 3},
                    new int[]{-22, -46, 3, 3}
            );
            case CONCRETE -> List.of(
                    new int[]{0, -46, 8, 6},
                    new int[]{48, 12, 8, 5},
                    new int[]{-15, 39, 4, 3},
                    new int[]{49, -17, 7, 5},
                    new int[]{-20, -48, 3, 3}
            );
            case ESTATE -> List.of(
                    new int[]{28, -4, 8, 5},
                    new int[]{-36, 4, 7, 5},
                    new int[]{47, 19, 8, 5},
                    new int[]{22, 46, 4, 3},
                    new int[]{8, -44, 8, 5}
            );
            case WATERWORKS -> List.of(
                    new int[]{3, -27, 6, 5},
                    new int[]{24, 44, 4, 3},
                    new int[]{36, 11, 3, 3},
                    new int[]{44, 28, 5, 3}
            );
            case SHACKS -> List.of(
                    new int[]{-24, -28, 6, 5},
                    new int[]{24, -24, 6, 5},
                    new int[]{0, -30, 7, 5},
                    new int[]{-28, 2, 7, 5}
            );
            case REFINERY -> List.of(
                    new int[]{-18, -52, 12, 8},
                    new int[]{55, 27, 7, 5},
                    new int[]{-11, 47, 4, 3},
                    new int[]{21, 47, 8, 6},
                    new int[]{4, -58, 3, 3}
            );
            case SUBSTATION -> List.of(
                    new int[]{-42, 0, 4, 4},
                    new int[]{32, -20, 4, 4},
                    new int[]{24, 40, 4, 3},
                    new int[]{-34, 18, 7, 5}
            );
        };
    }

    private void oriented(int cx, int cz, int rot, Runnable action) {
        int prevCx = this.curCx;
        int prevCz = this.curCz;
        int prevRot = this.curRot;
        this.curCx = cx;
        this.curCz = cz;
        this.curRot = Math.floorMod(rot, 4);
        try {
            action.run();
        } finally {
            this.curCx = prevCx;
            this.curCz = prevCz;
            this.curRot = prevRot;
        }
    }

    private int wx(int x, int z) {
        int u = x - curCx;
        int v = z - curCz;
        return curCx + switch (curRot) {
            case 1 -> -v;
            case 2 -> -u;
            case 3 -> v;
            default -> u;
        };
    }

    private int wz(int x, int z) {
        int u = x - curCx;
        int v = z - curCz;
        return curCz + switch (curRot) {
            case 1 -> u;
            case 2 -> -v;
            case 3 -> -u;
            default -> v;
        };
    }

    private BpSpec rotateSpec(BpSpec spec) {
        if (curRot == 0) return spec;
        String f = spec.prop("facing");
        if (f != null) {
            List<String> dirs = List.of("north", "east", "south", "west");
            int idx = dirs.indexOf(f);
            if (idx >= 0) {
                spec = spec.with("facing", dirs.get((idx + curRot) % 4));
            }
        }
        String axis = spec.prop("axis");
        if (axis != null && curRot % 2 == 1) {
            if ("x".equals(axis)) spec = spec.with("axis", "z");
            else if ("z".equals(axis)) spec = spec.with("axis", "x");
        }
        return spec;
    }

    void build() {
        int cx = s.x();
        int cz = s.z();
        if (s.id() >= 100) {
            oriented(cx + 14, cz - 24, 2, () -> yardOutbuilding(cx + 14, cz - 24, s.id() % 7));
            return;
        }
        switch (s.theme()) {
            case ALDER -> {
                oriented(cx - 48, cz - 40, 2, () -> ruralSchool(cx - 48, cz - 40, 9, 7, 0));
                oriented(cx + 37, cz - 45, 2, () -> postOffice(cx + 37, cz - 45, 7, 5, 0));
                oriented(cx + 46, cz - 8, 0, () -> groceryStore(cx + 46, cz - 8, 7, 5, 0));
                oriented(cx + 48, cz + 16, 0, () -> ruralCemetery(cx + 48, cz + 16, 8, 7, 0));
                oriented(cx - 46, cz - 8, 2, () -> serviceStation(cx - 46, cz - 8, 7, 5, 0));
                oriented(cx + 56, cz - 26, 3, () -> transformerBooth(cx + 56, cz - 26, 0));
            }
            case BRICK -> {
                oriented(cx, cz - 48, 2, () -> militiaStation(cx, cz - 48, 9, 6));
                oriented(cx - 48, cz - 50, 2, () -> ruralSchool(cx - 48, cz - 50, 8, 6, 1));
                oriented(cx + 50, cz - 16, 2, () -> groceryStore(cx + 50, cz - 16, 7, 5, 1));
                oriented(cx + 50, cz + 8, 0, () -> postOffice(cx + 50, cz + 8, 6, 5, 1));
                oriented(cx + 46, cz - 54, 2, () -> garageCooperative(cx + 46, cz - 54, 10, 5, 0));
                oriented(cx + 28, cz + 50, 2, () -> busStopVariant(cx + 28, cz + 50, 0));
                oriented(cx + 58, cz + 36, 3, () -> transformerBooth(cx + 58, cz + 36, 1));
            }
            case PINE -> {
                oriented(cx - 44, cz - 44, 2, () -> foresterHouse(cx - 44, cz - 44, 8, 6, 0));
                oriented(cx + 40, cz - 44, 0, () -> fireWatchtower(cx + 40, cz - 44));
                oriented(cx + 36, cz - 6, 2, () -> garageCooperative(cx + 36, cz - 6, 9, 5, 1));
                oriented(cx + 20, cz + 42, 2, () -> gasStation(cx + 20, cz + 42, 8, 6, 0));
                oriented(cx + 52, cz + 42, 2, () -> busStopVariant(cx + 52, cz + 42, 1));
                oriented(cx + 50, cz + 20, 3, () -> transformerBooth(cx + 50, cz + 20, 2));
            }
            case FARM -> {
                oriented(cx - 22, cz - 56, 2, () -> machineryHangar(cx - 22, cz - 56, 11, 7));
                oriented(cx + 8, cz - 54, 2, () -> tractorWorkshop(cx + 8, cz - 54, 9, 6));
                oriented(cx + 52, cz - 56, 2, () -> abandonedFarm(cx + 52, cz - 56, 8, 7, 0));
                oriented(cx + 54, cz + 28, 2, () -> groceryStore(cx + 54, cz + 28, 6, 5, 2));
                oriented(cx + 18, cz + 50, 2, () -> busStopVariant(cx + 18, cz + 50, 2));
                oriented(cx - 58, cz - 14, 1, () -> transformerBooth(cx - 58, cz - 14, 0));
            }
            case SAWMILL -> {
                oriented(cx + 35, cz - 9, 2, () -> workerBarracks(cx + 35, cz - 9, 8, 5, 0));
                oriented(cx - 30, cz + 10, 0, () -> boilerHouse(cx - 30, cz + 10, 7, 6, 0));
                oriented(cx - 10, cz + 31, 0, () -> industrialCheckpoint(cx - 10, cz + 31, 0));
                oriented(cx + 25, cz + 49, 2, () -> busStopVariant(cx + 25, cz + 49, 3));
                oriented(cx - 22, cz - 46, 2, () -> transformerBooth(cx - 22, cz - 46, 1));
            }
            case CONCRETE -> {
                oriented(cx, cz - 46, 2, () -> boilerHouse(cx, cz - 46, 8, 6, 1));
                oriented(cx + 48, cz + 12, 2, () -> workerBarracks(cx + 48, cz + 12, 8, 5, 1));
                oriented(cx - 15, cz + 39, 0, () -> industrialCheckpoint(cx - 15, cz + 39, 1));
                oriented(cx + 49, cz - 17, 2, () -> serviceStation(cx + 49, cz - 17, 7, 5, 1));
                oriented(cx - 20, cz - 48, 2, () -> transformerBooth(cx - 20, cz - 48, 2));
            }
            case ESTATE -> {
                oriented(cx + 28, cz - 4, 2, () -> workerBarracks(cx + 28, cz - 4, 8, 5, 2));
                oriented(cx - 36, cz + 4, 2, () -> workerBarracks(cx - 36, cz + 4, 7, 5, 3));
                oriented(cx + 47, cz + 19, 2, () -> roadsideCafe(cx + 47, cz + 19, 8, 5, 0));
                oriented(cx + 22, cz + 46, 2, () -> industrialCheckpoint(cx + 22, cz + 46, 2));
                oriented(cx + 8, cz - 44, 2, () -> ruralCemetery(cx + 8, cz - 44, 8, 5, 1));
            }
            case WATERWORKS -> {
                oriented(cx + 3, cz - 27, 2, () -> boilerHouse(cx + 3, cz - 27, 6, 5, 2));
                oriented(cx + 24, cz + 44, 2, () -> industrialCheckpoint(cx + 24, cz + 44, 3));
                oriented(cx + 36, cz + 11, 3, () -> transformerBooth(cx + 36, cz + 11, 0));
                oriented(cx + 44, cz + 28, 2, () -> busStopVariant(cx + 44, cz + 28, 0));
            }
            case SHACKS -> {
                oriented(cx - 24, cz - 28, 2, () -> huntersCabin(cx - 24, cz - 28, 6, 5, 0));
                oriented(cx + 24, cz - 24, 2, () -> huntersCabin(cx + 24, cz - 24, 6, 5, 1));
                oriented(cx, cz - 30, 2, () -> foresterHouse(cx, cz - 30, 7, 5, 1));
                oriented(cx - 28, cz + 2, 0, () -> abandonedFarm(cx - 28, cz + 2, 7, 5, 1));
            }
            case REFINERY -> {
                oriented(cx - 18, cz - 52, 2, () -> fuelDepot(cx - 18, cz - 52, 12, 8));
                oriented(cx + 55, cz + 27, 2, () -> boilerHouse(cx + 55, cz + 27, 7, 5, 3));
                oriented(cx - 11, cz + 47, 0, () -> industrialCheckpoint(cx - 11, cz + 47, 0));
                oriented(cx + 21, cz + 47, 2, () -> gasStation(cx + 21, cz + 47, 8, 6, 1));
                oriented(cx + 4, cz - 58, 2, () -> transformerBooth(cx + 4, cz - 58, 1));
            }
            case SUBSTATION -> {
                oriented(cx - 42, cz, 1, () -> transformerBooth(cx - 42, cz, 0));
                oriented(cx + 32, cz - 20, 3, () -> transformerBooth(cx + 32, cz - 20, 2));
                oriented(cx + 24, cz + 40, 2, () -> industrialCheckpoint(cx + 24, cz + 40, 1));
                oriented(cx - 34, cz + 18, 2, () -> roadsideCafe(cx - 34, cz + 18, 7, 5, 1));
            }
        }
    }

    private void put(int x, int y, int z, String id) {
        int rx = wx(x, z);
        int rz = wz(x, z);
        if (bp.inBounds(rx, y, rz)) {
            bp.set(rx, y, rz, rotateSpec(BpSpec.of(id)));
        }
    }

    private void set(int x, int y, int z, BpSpec spec) {
        int rx = wx(x, z);
        int rz = wz(x, z);
        if (bp.inBounds(rx, y, rz)) {
            bp.set(rx, y, rz, rotateSpec(spec));
        }
    }

    private void loot(int x, int y, int z, String table) {
        int rx = wx(x, z);
        int rz = wz(x, z);
        if (!bp.inBounds(rx, y, rz)) return;
        bp.addBlockEntity(
                new Blueprint.BlockEntity(
                        rx, y, rz, Blueprint.Kind.BARREL, "up",
                        "bobin_craft:chests/" + table,
                        WarzoneNoise.hash(t.seed + s.id() * 31L, rx, rz),
                        false, false
                ),
                BpSpec.of("barrel").with("facing", "up")
        );
    }

    private void door(int x, int y, int z, String id, String facing) {
        set(x, y, z, BpSpec.of(id).with("facing", facing).with("half", "lower").with("hinge", "left"));
        set(x, y + 1, z, BpSpec.of(id).with("facing", facing).with("half", "upper").with("hinge", "left"));
    }

    private void bed(int x, int y, int z, String color) {
        set(x, y, z, BpSpec.of(color + "_bed").with("facing", "south").with("part", "foot"));
        set(x, y, z + 1, BpSpec.of(color + "_bed").with("facing", "south").with("part", "head"));
    }

    private String windowMat(int dx, int dy, int dz, int state) {
        int h = Math.floorMod(dx * 7 + dz * 13 + dy * 5 + state * 3 + s.id(), 10);
        return switch (h) {
            case 0 -> "bobin_craft:damaged_wood_panel";
            case 1, 2 -> "bobin_craft:old_window_frame";
            case 3, 4, 5 -> "bobin_craft:broken_window_glass";
            case 6, 7 -> "gray_stained_glass_pane";
            case 8 -> "brown_stained_glass_pane";
            default -> "tinted_glass";
        };
    }

    private int basePad(int cx, int cz, int rx, int rz, String floorId) {
        int baseY = t.height(cx, cz);
        for (int dz = -rz - 1; dz <= rz + 1; dz++) {
            for (int dx = -rx - 1; dx <= rx + 1; dx++) {
                int x = cx + dx;
                int z = cz + dz;
                int rwX = wx(x, z);
                int rwZ = wz(x, z);
                int h = t.height(rwX, rwZ);
                for (int y = Math.min(h, baseY) - 5; y < baseY; y++) {
                    put(x, y, z, y == baseY - 1 ? "bobin_craft:cracked_concrete" : "stone");
                }
                boolean inside = Math.abs(dx) <= rx && Math.abs(dz) <= rz;
                if (inside) {
                    put(x, baseY, z, floorId);
                } else {
                    put(x, h, z, "bobin_craft:dark_forest_soil");
                }
                for (int dy = 1; dy <= 16; dy++) {
                    put(x, baseY + dy, z, "air");
                }
            }
        }
        return baseY;
    }

    private void shellBox(int cx, int baseY, int cz, int rx, int rz, int wallH, String wallId, String plinthId, int state) {
        for (int dz = -rz; dz <= rz; dz++) {
            for (int dx = -rx; dx <= rx; dx++) {
                boolean border = Math.abs(dx) == rx || Math.abs(dz) == rz;
                if (!border) continue;
                boolean corner = Math.abs(dx) == rx && Math.abs(dz) == rz;
                for (int dy = 1; dy <= wallH; dy++) {
                    // Keep the top wall course (dy == wallH) intact so the roof never sits over an open wall gap.
                    boolean win = dy >= 2 && dy <= Math.min(3, wallH - 1) && !corner && (
                            (Math.abs(dz) == rz && Math.abs(dx) % 3 == 2) ||
                            (Math.abs(dx) == rx && Math.abs(dz) % 3 == 2)
                    );
                    String mat = dy == 1 ? plinthId : wallId;
                    if (corner) {
                        mat = "bobin_craft:dark_concrete_panel";
                    } else if (win) {
                        mat = windowMat(dx, dy, dz, state);
                    } else if (state >= 2 && dy == 2 && (dx + dz) % 7 == 0) {
                        mat = "bobin_craft:cracked_red_brick";
                    }
                    put(cx + dx, baseY + dy, cz + dz, mat);
                }
            }
        }
    }

    private void slateRoof(int cx, int baseY, int cz, int rx, int rz, int startH, int state) {
        for (int dz = -rz - 1; dz <= rz + 1; dz++) {
            for (int dx = -rx - 1; dx <= rx + 1; dx++) {
                int ry = startH + Math.max(0, (rz + 1 - Math.abs(dz)) / 2);
                boolean inFootprint = Math.abs(dx) <= rx && Math.abs(dz) <= rz;
                boolean onWall = inFootprint && (Math.abs(dx) == rx || Math.abs(dz) == rz);
                if (onWall) {
                    for (int y = startH; y < ry; y++) {
                        put(cx + dx, baseY + y, cz + dz, "bobin_craft:old_cinderblock");
                    }
                } else if (inFootprint && ry > startH) {
                    // Close internal roof step transitions so diagonal slopes have no air slits.
                    put(cx + dx, baseY + ry - 1, cz + dz, "dark_oak_planks");
                } else if (!inFootprint && ry > startH) {
                    // Constructive cornice support under gable overhang.
                    set(cx + dx, baseY + ry - 1, cz + dz, BpSpec.of("dark_oak_slab").with("type", "top"));
                }
                boolean interior = Math.abs(dx) < rx - 1 && Math.abs(dz) < rz - 1;
                if (interior && state == 3 && dx > 2 && dz > 1) {
                    set(cx + dx, baseY + ry, cz + dz, BpSpec.of("dark_oak_slab").with("type", "bottom"));
                    continue;
                }
                String rMat = (dx + dz + state) % 3 == 0
                        ? "bobin_craft:rusted_sheet_metal"
                        : "bobin_craft:corrugated_slate";
                put(cx + dx, baseY + ry, cz + dz, rMat);
            }
        }
    }

    private void ruralSchool(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:peeling_paint_wall");
        int state = variant == 0 ? 1 : 2;
        shellBox(cx, y, cz, rx, rz, 5, "bobin_craft:weathered_plaster", "bobin_craft:cracked_red_brick", state);
        slateRoof(cx, y, cz, rx, rz, 6, state);
        door(cx, y + 1, cz - rz, "spruce_door", "north");
        for (int dx = -2; dx <= 2; dx++) {
            put(cx + dx, y, cz - rz - 1, "bobin_craft:cracked_concrete");
        }
        for (int dx = -rx + 1; dx < rx; dx++) {
            if (Math.abs(dx) > 1) {
                for (int dy = 1; dy <= 5; dy++) {
                    put(cx + dx, y + dy, cz, "bobin_craft:peeling_paint_wall");
                }
            }
        }
        for (int dz = 1; dz < rz; dz++) {
            if (dz != 2) {
                for (int dy = 1; dy <= 5; dy++) {
                    put(cx, y + dy, cz + dz, "bobin_craft:peeling_paint_wall");
                }
            }
        }
        for (int dz = -rz + 2; dz <= -2; dz++) {
            for (int dy = 2; dy <= 3; dy++) {
                put(cx - rx + 1, y + dy, cz + dz, "black_concrete");
            }
        }
        for (int dx : new int[]{-rx + 3, -rx + 5}) {
            for (int dz : new int[]{-rz + 2, -2}) {
                put(cx + dx, y + 1, cz + dz, "spruce_fence");
                put(cx + dx, y + 2, cz + dz, "spruce_pressure_plate");
                set(cx + dx + 1, y + 1, cz + dz, BpSpec.of("spruce_stairs").with("facing", "west"));
            }
        }
        for (int dx = 2; dx <= rx - 2; dx++) {
            put(cx + dx, y + 1, cz + rz - 1, "bookshelf");
            put(cx + dx, y + 2, cz + rz - 1, "bookshelf");
        }
        put(cx + 2, y + 1, cz + 2, "lectern");
        put(cx - rx + 2, y + 1, cz + rz - 1, "bobin_craft:field_table");
        loot(cx + rx - 1, y + 1, cz + rz - 1, "warzone_civilian");
        loot(cx - rx + 1, y + 1, cz + rz - 1, "warzone_medical");
        for (int dx = -rx; dx <= rx; dx += 2) {
            if (Math.abs(dx) > 2) {
                put(cx + dx, y + 1, cz - rz - 1, "iron_bars");
            }
        }
    }

    private void militiaStation(int cx, int cz, int rx, int rz) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:old_wall_tile");
        shellBox(cx, y, cz, rx, rz, 5, "bobin_craft:cracked_red_brick", "bobin_craft:dark_concrete_panel", 1);
        for (int dz = -rz; dz <= rz; dz++) {
            for (int dx = -rx; dx <= rx; dx++) {
                put(cx + dx, y + 5, cz + dz, "bobin_craft:dark_concrete_panel");
                if (Math.abs(dx) == rx || Math.abs(dz) == rz) {
                    set(cx + dx, y + 6, cz + dz, BpSpec.of("stone_brick_slab"));
                }
            }
        }
        door(cx, y + 1, cz - rz, "iron_door", "north");
        for (int dx = -2; dx <= 2; dx++) {
            put(cx + dx, y + 4, cz - rz, dx == 0 ? "yellow_terracotta" : "blue_terracotta");
        }
        set(cx - 2, y + 3, cz - rz - 1, BpSpec.of("bobin_craft:wall_light").with("facing", "north"));
        for (int dx = -3; dx <= -1; dx++) {
            put(cx + dx, y + 1, cz - 1, "bobin_craft:peeling_paint_wall");
            put(cx + dx, y + 2, cz - 1, "iron_bars");
        }
        set(cx - 3, y + 1, cz - 3, BpSpec.of("bobin_craft:radio").with("facing", "south"));
        for (int dx = -rx + 1; dx <= -2; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                if (dx != -2 || dy > 2) {
                    put(cx + dx, y + dy, cz + 1, "iron_bars");
                }
            }
        }
        for (int dz = 1; dz < rz; dz++) {
            for (int dy = 1; dy <= 4; dy++) {
                put(cx - 2, y + dy, cz + dz, "iron_bars");
            }
        }
        bed(cx - rx + 1, y + 1, cz + rz - 3, "gray");
        for (int dx = 2; dx < rx; dx++) {
            if (dx != 3) {
                for (int dy = 1; dy <= 4; dy++) {
                    put(cx + dx, y + dy, cz, "bobin_craft:old_cinderblock");
                }
            }
        }
        put(cx + rx - 1, y + 1, cz + rz - 1, "bobin_craft:equipment_locker");
        put(cx + rx - 2, y + 1, cz + rz - 1, "bobin_craft:ammo_crate");
        put(cx + rx - 3, y + 1, cz + rz - 1, "bookshelf");
        loot(cx + 2, y + 1, cz + rz - 1, "warzone_command");
        loot(cx - rx + 1, y + 1, cz - rz + 1, "warzone_supply");
    }

    private void groceryStore(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:old_wall_tile");
        String wall = variant == 0
                ? "bobin_craft:weathered_plaster"
                : (variant == 1 ? "bobin_craft:cracked_red_brick" : "bobin_craft:old_cinderblock");
        shellBox(cx, y, cz, rx, rz, 4, wall, "bobin_craft:cracked_concrete", variant);
        slateRoof(cx, y, cz, rx, rz, 5, variant);
        door(cx, y + 1, cz - rz, "spruce_door", "north");
        for (int dx = -3; dx <= 3; dx++) {
            put(cx + dx, y + 4, cz - rz, variant == 0 ? "green_terracotta" : (variant == 1 ? "red_terracotta" : "cyan_terracotta"));
        }
        for (int dx = -rx + 1; dx < rx; dx++) {
            if (dx != 1) {
                for (int dy = 1; dy <= 4; dy++) {
                    put(cx + dx, y + dy, cz + 1, "bobin_craft:peeling_paint_wall");
                }
            }
        }
        for (int dx = -rx + 2; dx <= 0; dx++) {
            put(cx + dx, y + 1, cz - 1, "bobin_craft:damaged_wood_panel");
            put(cx + dx, y + 2, cz - 1, "stone_pressure_plate");
        }
        for (int dz = -rz + 1; dz <= 0; dz += 2) {
            put(cx + rx - 1, y + 1, cz + dz, "bookshelf");
        }
        put(cx - rx + 1, y + 1, cz + rz - 1, "bobin_craft:supply_crate");
        loot(cx + rx - 1, y + 1, cz + rz - 1, "warzone_civilian");
        loot(cx - rx + 1, y + 1, cz - rz + 1, "warzone_supply");
    }

    private void postOffice(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:peeling_paint_wall");
        shellBox(cx, y, cz, rx, rz, 4, "bobin_craft:weathered_plaster", "bobin_craft:old_cinderblock", variant + 1);
        slateRoof(cx, y, cz, rx, rz, 5, variant);
        door(cx, y + 1, cz - rz, "spruce_door", "north");
        for (int dx = -2; dx <= 2; dx++) {
            put(cx + dx, y + 4, cz - rz, dx % 2 == 0 ? "blue_terracotta" : "white_terracotta");
        }
        for (int dx = -rx + 1; dx <= 1; dx++) {
            if (dx != 0) {
                put(cx + dx, y + 1, cz, "bobin_craft:damaged_wood_panel");
                put(cx + dx, y + 2, cz, dx % 2 == 0 ? "bobin_craft:old_window_frame" : "bobin_craft:broken_window_glass");
            }
        }
        for (int dz = -rz + 1; dz <= -1; dz++) {
            put(cx - rx + 1, y + 1, cz + dz, "chiseled_bookshelf");
            put(cx - rx + 1, y + 2, cz + dz, "chiseled_bookshelf");
        }
        put(cx - 2, y + 1, cz + rz - 1, "bobin_craft:field_table");
        put(cx + 2, y + 1, cz + rz - 1, "cartography_table");
        put(cx + rx - 1, y + 1, cz + rz - 1, "bobin_craft:supply_crate");
        loot(cx, y + 1, cz + rz - 1, "warzone_civilian");
    }

    private void garageCooperative(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:cracked_concrete");
        int bays = 4;
        int bayW = (rx * 2) / bays;
        for (int b = 0; b < bays; b++) {
            int x0 = cx - rx + b * bayW;
            int x1 = (b == bays - 1) ? (cx + rx) : (x0 + bayW);
            int bState = (b + variant) % 4;
            for (int dz = -rz; dz <= rz; dz++) {
                for (int x = x0; x <= x1; x++) {
                    boolean sideOrBack = (x == x0 || x == x1 || dz == rz);
                    boolean front = (dz == -rz);
                    if (sideOrBack) {
                        for (int dy = 1; dy <= 3; dy++) {
                            put(x, y + dy, cz + dz, dy == 1 ? "bobin_craft:old_cinderblock" : "bobin_craft:cracked_red_brick");
                        }
                    } else if (front) {
                        if (x == x0 || x == x1) {
                            for (int dy = 1; dy <= 3; dy++) {
                                put(x, y + dy, cz + dz, "bobin_craft:old_cinderblock");
                            }
                        } else if (bState == 0) {
                            for (int dy = 1; dy <= 3; dy++) {
                                put(x, y + dy, cz + dz, "bobin_craft:metal_garage_door");
                            }
                        } else {
                            put(x, y + 3, cz + dz, "bobin_craft:metal_garage_door");
                        }
                    }
                    if (!(bState == 3 && dz > 0 && x > x0 + 1 && x < x1)) {
                        put(x, y + 4, cz + dz, b % 2 == 0 ? "bobin_craft:corrugated_slate" : "bobin_craft:rusted_sheet_metal");
                    } else {
                        set(x, y + 4, cz + dz, BpSpec.of("dark_oak_slab").with("type", "bottom"));
                    }
                }
            }
            int midX = (x0 + x1) / 2;
            if (bState == 1) {
                // Walkable 1-block inspection recess with stairs at the entrance so players never get stuck.
                for (int dz = -rz + 2; dz <= rz - 2; dz++) {
                    put(midX, y - 1, cz + dz, "bobin_craft:cracked_concrete");
                    if (dz == -rz + 2) {
                        set(midX, y, cz + dz, BpSpec.of("stone_brick_stairs").with("facing", "north"));
                    } else {
                        put(midX, y, cz + dz, "air");
                    }
                }
                put(x0 + 1, y + 1, cz + rz - 1, "smithing_table");
                put(x1 - 1, y + 1, cz + rz - 1, "anvil");
                loot(midX, y + 1, cz + rz - 1, "warzone_technical");
            } else if (bState == 2) {
                put(x0 + 1, y + 1, cz + rz - 1, "crafting_table");
                put(x1 - 1, y + 1, cz + rz - 1, "bobin_craft:supply_crate");
                put(midX, y + 1, cz + 1, "black_concrete");
            } else if (bState == 0) {
                put(midX, y + 2, cz + rz, "bobin_craft:rusted_vent_grille");
                loot(midX, y + 1, cz + rz - 1, "warzone_supply");
            }
        }
    }

    private void ruralCemetery(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:mossy_forest_soil");
        for (int dz = -rz; dz <= rz; dz++) {
            for (int dx = -rx; dx <= rx; dx++) {
                boolean border = Math.abs(dx) == rx || Math.abs(dz) == rz;
                if (border) {
                    if (!(dz == -rz && Math.abs(dx) <= 1)) {
                        if ((dx + dz + variant) % 7 != 0) {
                            put(cx + dx, y + 1, cz + dz, (dx + dz) % 4 == 0 ? "mossy_cobblestone_wall" : "iron_bars");
                        }
                    }
                } else if (dx == 0 || dz == 0) {
                    put(cx + dx, y, cz + dz, "bobin_craft:weathered_gravel");
                }
            }
        }
        int sx = cx + rx - 4;
        int sz = cz + rz - 4;
        for (int dz = 0; dz <= 3; dz++) {
            for (int dx = 0; dx <= 3; dx++) {
                boolean b = (dx == 0 || dx == 3 || dz == 0 || dz == 3);
                put(sx + dx, y, sz + dz, "bobin_craft:cracked_concrete");
                if (b && !(dz == 0 && dx == 1)) {
                    for (int dy = 1; dy <= 2; dy++) {
                        put(sx + dx, y + dy, sz + dz, "bobin_craft:damaged_wood_panel");
                    }
                }
                put(sx + dx, y + 3, sz + dz, "bobin_craft:corrugated_slate");
            }
        }
        loot(sx + 2, y + 1, sz + 2, "warzone_civilian");
        int[][] graves = {
                {-rx + 2, -rz + 2}, {-rx + 5, -rz + 2},
                {-rx + 2, -rz + 5}, {-rx + 5, -rz + 5},
                {rx - 5, -rz + 2}, {rx - 2, -rz + 2},
                {-rx + 2, rz - 3}, {-rx + 5, rz - 3}
        };
        for (int i = 0; i < graves.length; i++) {
            int gx = cx + graves[i][0];
            int gz = cz + graves[i][1];
            put(gx, y, gz + 1, "podzol");
            if (i % 2 == 0) {
                put(gx, y + 1, gz, "mossy_stone_brick_wall");
                put(gx, y + 2, gz, "mossy_stone_brick_wall");
            } else {
                put(gx, y + 1, gz, "cobblestone_wall");
                put(gx, y + 2, gz, "spruce_fence");
            }
        }
    }

    private void gasStation(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:cracked_asphalt");
        put(cx - 2, y, cz - 2, "black_concrete");
        put(cx + 2, y, cz - 2, "black_concrete");
        // Forecourt canopy with thin slab fascia and 4 symmetric structural columns.
        for (int dz = -rz; dz <= 0; dz++) {
            for (int dx = -rx + 1; dx <= rx - 1; dx++) {
                boolean rim = Math.abs(dx) == rx - 1 || dz == -rz;
                if (rim) {
                    set(cx + dx, y + 4, cz + dz, BpSpec.of("stone_brick_slab").with("type", "top"));
                } else {
                    put(cx + dx, y + 4, cz + dz, "bobin_craft:rusted_sheet_metal");
                }
            }
        }
        for (int px : new int[]{-rx + 2, rx - 2}) {
            for (int pz : new int[]{-rz + 1, -1}) {
                for (int dy = 1; dy <= 3; dy++) {
                    put(cx + px, y + dy, cz + pz, "bobin_craft:dark_concrete_panel");
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            set(cx + dx, y + 1, cz - 3, BpSpec.of("stone_brick_slab"));
        }
        for (int px : new int[]{-2, 2}) {
            put(cx + px, y + 1, cz - 3, "red_terracotta");
            put(cx + px, y + 2, cz - 3, "bobin_craft:equipment_panel");
        }
        for (int dz = 0; dz <= rz; dz++) {
            for (int dx = -rx + 1; dx <= rx - 1; dx++) {
                put(cx + dx, y, cz + dz, "bobin_craft:old_wall_tile");
                boolean border = (dx == -rx + 1 || dx == rx - 1 || dz == 0 || dz == rz);
                if (border && !(dz == 0 && dx == -2) && !(dz == 0 && dx >= 2 && dx <= 4)) {
                    for (int dy = 1; dy <= 3; dy++) {
                        String m = (dz == 0 && dy == 2 && Math.abs(dx) <= 1)
                                ? (dx == 0 ? "bobin_craft:broken_window_glass" : "gray_stained_glass_pane")
                                : (dy == 1 ? "bobin_craft:cracked_red_brick" : "bobin_craft:weathered_plaster");
                        put(cx + dx, y + dy, cz + dz, m);
                    }
                } else if (dz == 0 && dx >= 2 && dx <= 4) {
                    put(cx + dx, y + 3, cz + dz, "bobin_craft:metal_garage_door");
                }
                put(cx + dx, y + 4, cz + dz, "bobin_craft:dark_concrete_panel");
            }
        }
        door(cx - 2, y + 1, cz, "spruce_door", "north");
        put(cx - 4, y + 1, cz + 2, "bobin_craft:field_table");
        put(cx + 3, y + 1, cz + rz - 1, "smithing_table");
        loot(cx - 4, y + 1, cz + rz - 1, "warzone_technical");
        loot(cx, y + 1, cz + rz - 1, "warzone_supply");
    }

    private void roadsideCafe(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:old_wall_tile");
        shellBox(cx, y, cz, rx, rz, 4, "bobin_craft:weathered_plaster", "bobin_craft:cracked_red_brick", variant + 1);
        slateRoof(cx, y, cz, rx, rz, 5, variant + 1);
        door(cx, y + 1, cz - rz, "spruce_door", "north");
        // Supported entrance canopy over the front porch (no furniture placed outside on the road approach).
        for (int dx = -2; dx <= 2; dx++) {
            put(cx + dx, y, cz - rz - 1, "bobin_craft:cracked_concrete");
            set(cx + dx, y + 4, cz - rz - 1, BpSpec.of("stone_brick_slab").with("type", "bottom"));
        }
        for (int side : new int[]{-2, 2}) {
            for (int dy = 1; dy <= 3; dy++) {
                put(cx + side, y + dy, cz - rz - 1, "spruce_fence");
            }
        }
        // Dining tables placed inside the cafe hall rather than outside on the roadway.
        for (int tx : new int[]{-rx + 2, rx - 2}) {
            put(cx + tx, y + 1, cz - rz + 2, "spruce_fence");
            put(cx + tx, y + 2, cz - rz + 2, "spruce_pressure_plate");
            set(cx + tx, y + 1, cz - rz + 1, BpSpec.of("spruce_stairs").with("facing", "south"));
            set(cx + tx, y + 1, cz - rz + 3, BpSpec.of("spruce_stairs").with("facing", "north"));
        }
        for (int dx = -rx + 1; dx < rx; dx++) {
            if (dx != 1) {
                put(cx + dx, y + 1, cz + 1, "bobin_craft:peeling_paint_wall");
                if (dx <= 0) {
                    put(cx + dx, y + 2, cz + 1, "spruce_slab");
                } else {
                    put(cx + dx, y + 2, cz + 1, "bobin_craft:old_wall_tile");
                    put(cx + dx, y + 3, cz + 1, "bobin_craft:old_wall_tile");
                    put(cx + dx, y + 4, cz + 1, "bobin_craft:old_wall_tile");
                }
            }
        }
        put(cx - rx + 1, y + 1, cz + rz - 1, "smoker");
        put(cx - rx + 2, y + 1, cz + rz - 1, "water_cauldron");
        put(cx + rx - 1, y + 2, cz + rz, "bobin_craft:rusted_vent_grille");
        loot(cx + rx - 1, y + 1, cz + rz - 1, "warzone_civilian");
        put(cx + rx + 1, y + 1, cz + rz - 1, "cauldron");
        put(cx + rx + 1, y + 1, cz + rz - 2, "composter");
    }

    private void busStopVariant(int cx, int cz, int variant) {
        int y = basePad(cx, cz, 4, 2, "bobin_craft:cracked_concrete");
        String wall = variant == 0
                ? "bobin_craft:dark_concrete_panel"
                : (variant == 1 ? "bobin_craft:cracked_red_brick" : (variant == 2 ? "bobin_craft:old_wall_tile" : "bobin_craft:rusted_sheet_metal"));
        String roof = variant % 2 == 0 ? "bobin_craft:corrugated_slate" : "bobin_craft:rusted_sheet_metal";
        for (int dz = -1; dz <= 2; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (dz == 2 || (Math.abs(dx) == 4 && dz >= 0)) {
                    for (int dy = 1; dy <= 3; dy++) {
                        String wMat = (dy == 2 && Math.abs(dx) == 2 && dz == 2)
                                ? (dx < 0 ? "bobin_craft:old_window_frame" : "bobin_craft:broken_window_glass")
                                : wall;
                        put(cx + dx, y + dy, cz + dz, wMat);
                    }
                } else if (Math.abs(dx) == 4 && dz == -1) {
                    for (int dy = 1; dy <= 3; dy++) {
                        put(cx + dx, y + dy, cz + dz, "iron_bars");
                    }
                }
                if (dz == -1) {
                    set(cx + dx, y + 4, cz + dz, BpSpec.of("stone_brick_slab").with("type", "bottom"));
                } else {
                    put(cx + dx, y + 4, cz + dz, roof);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            set(cx + dx, y + 1, cz + 1, BpSpec.of("spruce_stairs").with("facing", "north"));
        }
        put(cx + 3, y + 1, cz - 1, "cauldron");
        put(cx - 4, y + 1, cz - 2, "iron_bars");
        put(cx - 4, y + 2, cz - 2, "iron_bars");
        put(cx - 4, y + 3, cz - 2, "blue_terracotta");
    }

    private void serviceStation(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:cracked_concrete");
        shellBox(cx, y, cz, rx, rz, 4, "bobin_craft:old_cinderblock", "bobin_craft:dark_concrete_panel", variant + 1);
        for (int dz = -rz; dz <= rz; dz++) {
            for (int dx = -rx; dx <= rx; dx++) {
                put(cx + dx, y + 5, cz + dz, "bobin_craft:corrugated_slate");
            }
        }
        for (int dx = -rx + 2; dx <= -1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                put(cx + dx, y + dy, cz - rz, dy == 3 ? "bobin_craft:metal_garage_door" : "air");
            }
        }
        for (int dx = 1; dx <= rx - 2; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                put(cx + dx, y + dy, cz - rz, variant == 0 ? "bobin_craft:metal_garage_door" : (dy == 3 ? "bobin_craft:metal_garage_door" : "air"));
            }
        }
        for (int dz = -rz + 2; dz <= rz - 2; dz++) {
            set(cx - 3, y, cz + dz, BpSpec.of("stone_brick_slab").with("type", "bottom"));
        }
        put(cx - rx + 1, y + 1, cz + rz - 1, "smithing_table");
        put(cx, y + 1, cz + rz - 1, "anvil");
        put(cx + rx - 1, y + 1, cz + rz - 1, "grindstone");
        put(cx + rx - 1, y + 1, cz - rz + 2, "black_concrete");
        put(cx + rx - 1, y + 2, cz - rz + 2, "black_concrete");
        loot(cx + 2, y + 1, cz + rz - 1, "warzone_technical");
    }

    private void foresterHouse(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:dark_forest_soil");
        int hx0 = cx - rx + 1;
        int hx1 = cx + 2;
        int hz0 = cz - rz + 1;
        int hz1 = cz + rz - 1;
        for (int z = hz0; z <= hz1; z++) {
            for (int x = hx0; x <= hx1; x++) {
                put(x, y, z, "spruce_planks");
                boolean b = (x == hx0 || x == hx1 || z == hz0 || z == hz1);
                boolean c = (x == hx0 || x == hx1) && (z == hz0 || z == hz1);
                int ry = 4 + Math.min(x - hx0, hx1 - x) / 2;
                if (b && !(z == hz0 && x == (hx0 + hx1) / 2)) {
                    for (int dy = 1; dy <= 3; dy++) {
                        boolean w = (dy == 2 && !c && (x == hx0 || x == hx1) && z == cz);
                        String wId = (x == hx0) ? "bobin_craft:old_window_frame" : "brown_stained_glass_pane";
                        put(x, y + dy, z, c ? "bobin_craft:weathered_bark" : (w ? wId : "bobin_craft:damaged_wood_panel"));
                    }
                }
                // Fill gable ends and internal roof step transitions so there is no gap between walls and roof.
                if (b) {
                    for (int dy = 4; dy < ry; dy++) {
                        put(x, y + dy, z, "bobin_craft:damaged_wood_panel");
                    }
                } else if (ry > 4) {
                    put(x, y + ry - 1, z, "spruce_planks");
                }
                put(x, y + ry, z, "bobin_craft:corrugated_slate");
            }
        }
        door((hx0 + hx1) / 2, y + 1, hz0, "spruce_door", "north");
        for (int dy = 1; dy <= 6; dy++) {
            put(hx0 + 1, y + dy, hz1 - 1, dy == 1 ? "smoker" : "bobin_craft:cracked_red_brick");
        }
        bed(hx1 - 1, y + 1, hz1 - 2, "brown");
        put(hx0 + 1, y + 1, hz0 + 1, "crafting_table");
        loot(hx1 - 1, y + 1, hz0 + 1, "warzone_civilian");
        for (int z = cz - 2; z <= cz + 2; z++) {
            for (int x = cx + 4; x <= cx + rx - 1; x++) {
                put(x, y + 1, z, "spruce_log");
                if (x == cx + rx - 1 || ((x == cx + 4 || x == cx + rx - 1) && Math.abs(z - cz) == 2)) {
                    put(x, y + 2, z, "bobin_craft:weathered_bark");
                }
                put(x, y + 3, z, "bobin_craft:rusted_sheet_metal");
            }
        }
        put(cx + 5, y, cz - rz + 2, "water");
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) {
                    put(cx + 5 + dx, y + 1, cz - rz + 2 + dz, "mossy_cobblestone_wall");
                }
            }
        }
    }

    private void fireWatchtower(int cx, int cz) {
        int y = basePad(cx, cz, 6, 6, "bobin_craft:weathered_gravel");
        for (int lz : new int[]{-3, 3}) {
            for (int lx : new int[]{-3, 3}) {
                for (int dy = 1; dy <= 16; dy++) {
                    put(cx + lx, y + dy, cz + lz, dy <= 2 ? "bobin_craft:dark_concrete_panel" : "iron_bars");
                }
            }
        }
        for (int py : new int[]{6, 11, 16}) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dx = -3; dx <= 3; dx++) {
                    boolean hatch = (dx >= -1 && dx <= 0 && dz >= -2 && dz <= -1);
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                        put(cx + dx, y + py, cz + dz, "bobin_craft:rusted_sheet_metal");
                        put(cx + dx, y + py + 1, cz + dz, "iron_bars");
                    } else if (!hatch) {
                        put(cx + dx, y + py, cz + dz, "spruce_planks");
                    }
                }
            }
        }
        for (int dy = 1; dy <= 16; dy++) {
            put(cx, y + dy, cz - 3, "bobin_craft:rusted_sheet_metal");
            set(cx, y + dy, cz - 2, BpSpec.of("ladder").with("facing", "south"));
        }
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean b = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (b && !(dz == -2 && dx == -1)) {
                    put(cx + dx, y + 17, cz + dz, "bobin_craft:damaged_wood_panel");
                    String win = (dx + dz) % 2 == 0 ? "bobin_craft:old_window_frame" : "bobin_craft:broken_window_glass";
                    put(cx + dx, y + 18, cz + dz, win);
                    put(cx + dx, y + 19, cz + dz, "bobin_craft:damaged_wood_panel");
                }
                put(cx + dx, y + 20, cz + dz, "bobin_craft:corrugated_slate");
            }
        }
        set(cx, y + 17, cz, BpSpec.of("bobin_craft:radio").with("facing", "north"));
        loot(cx + 1, y + 17, cz + 1, "warzone_command");
        for (int dz = 0; dz <= 4; dz++) {
            for (int dx = 0; dx <= 4; dx++) {
                boolean b = (dx == 0 || dx == 4 || dz == 0 || dz == 4);
                if (b && !(dz == 0 && dx == 2)) {
                    for (int dy = 1; dy <= 3; dy++) {
                        put(cx + dx, y + dy, cz + dz, "bobin_craft:old_cinderblock");
                    }
                }
                put(cx + dx, y + 4, cz + dz, "bobin_craft:corrugated_slate");
            }
        }
        loot(cx + 2, y + 1, cz + 2, "warzone_technical");
    }

    private void abandonedFarm(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:dark_forest_soil");
        for (int dz = -rz + 1; dz <= -1; dz++) {
            for (int dx = -rx + 1; dx <= -1; dx++) {
                put(cx + dx, y, cz + dz, "spruce_planks");
                boolean b = (dx == -rx + 1 || dx == -1 || dz == -rz + 1 || dz == -1);
                if (b && !(dz == -1 && dx == -3)) {
                    for (int dy = 1; dy <= 3; dy++) {
                        put(cx + dx, y + dy, cz + dz, (dy == 2 && dx == -rx + 1 && dz == -3) ? "bobin_craft:old_window_frame" : "bobin_craft:weathered_plaster");
                    }
                }
                put(cx + dx, y + 4, cz + dz, "bobin_craft:corrugated_slate");
            }
        }
        loot(cx - rx + 2, y + 1, cz - rz + 2, "warzone_civilian");
        for (int dz = -rz + 1; dz <= 0; dz++) {
            for (int dx = 1; dx <= rx - 1; dx++) {
                put(cx + dx, y, cz + dz, "coarse_dirt");
                boolean b = (dx == 1 || dx == rx - 1 || dz == -rz + 1 || dz == 0);
                if (b && !(dz == 0 && Math.abs(dx - 4) <= 1)) {
                    for (int dy = 1; dy <= 4; dy++) {
                        put(cx + dx, y + dy, cz + dz, "bobin_craft:damaged_wood_panel");
                    }
                } else if (dz == 0 && Math.abs(dx - 4) <= 1) {
                    put(cx + dx, y + 4, cz + dz, "bobin_craft:damaged_wood_panel");
                }
                put(cx + dx, y + 5, cz + dz, "bobin_craft:rusted_sheet_metal");
            }
        }
        put(cx + rx - 2, y + 1, cz - rz + 2, "hay_block");
        put(cx + rx - 3, y + 1, cz - rz + 2, "hay_block");
        for (int dz = 2; dz <= rz - 1; dz++) {
            for (int dx = -rx + 1; dx <= 0; dx++) {
                boolean b = (dx == -rx + 1 || dx == 0 || dz == 2 || dz == rz - 1);
                boolean corner = (dx == -rx + 1 || dx == 0) && (dz == 2 || dz == rz - 1);
                if (b && !(dz == 2 && dx == -2)) {
                    put(cx + dx, y + 1, cz + dz, corner ? "bobin_craft:weathered_bark" : "bobin_craft:old_window_frame");
                    String gMat = corner ? "bobin_craft:weathered_bark"
                            : ((dx + dz) % 3 == 0 ? "bobin_craft:broken_window_glass"
                            : ((dx + dz) % 3 == 1 ? "bobin_craft:old_window_frame" : "gray_stained_glass_pane"));
                    put(cx + dx, y + 2, cz + dz, gMat);
                } else {
                    set(cx + dx, y, cz + dz, BpSpec.of("farmland").with("moisture", "7"));
                    set(cx + dx, y + 1, cz + dz, BpSpec.of("potatoes").with("age", "5"));
                }
                String roofGlass = (dx + dz) % 3 == 0
                        ? "bobin_craft:old_window_frame"
                        : ((dx + dz) % 3 == 1 ? "bobin_craft:broken_window_glass" : "BpSpec_slab");
                if ("BpSpec_slab".equals(roofGlass)) {
                    set(cx + dx, y + 3, cz + dz, BpSpec.of("dark_oak_slab").with("type", "bottom"));
                } else {
                    put(cx + dx, y + 3, cz + dz, roofGlass);
                }
            }
        }
        for (int dx = -rx; dx <= rx; dx++) {
            if (Math.abs(dx) > 1) {
                put(cx + dx, y + 1, cz + rz, "spruce_fence");
            }
        }
    }

    private void huntersCabin(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "spruce_planks");
        for (int dz = -rz + 2; dz <= rz; dz++) {
            for (int dx = -rx; dx <= rx; dx++) {
                boolean b = (Math.abs(dx) == rx || dz == -rz + 2 || dz == rz);
                boolean c = (Math.abs(dx) == rx && (dz == -rz + 2 || dz == rz));
                int ry = 4 + Math.max(0, (rz + 1 - Math.abs(dz)) / 2);
                if (b && !(dz == -rz + 2 && dx == 0)) {
                    for (int dy = 1; dy < ry; dy++) {
                        boolean w = (dy == 2 && Math.abs(dx) == rx && dz == 0);
                        String wId = (dx < 0) ? "bobin_craft:old_window_frame" : "brown_stained_glass_pane";
                        put(cx + dx, y + dy, cz + dz, c ? "bobin_craft:weathered_bark" : (w ? wId : "stripped_spruce_wood"));
                    }
                } else if (dz == -rz + 2 && dx == 0) {
                    for (int dy = 3; dy < ry; dy++) {
                        put(cx + dx, y + dy, cz + dz, "stripped_spruce_wood");
                    }
                }
            }
        }
        // Full-height porch support posts and cross-beam so the front cabin porch roof is structurally supported.
        for (int dx = -rx; dx <= rx; dx++) {
            if (Math.abs(dx) > 1) {
                put(cx + dx, y + 1, cz - rz, "spruce_fence");
            }
            if (Math.abs(dx) == rx || Math.abs(dx) == 2) {
                for (int dy = 1; dy <= 3; dy++) {
                    put(cx + dx, y + dy, cz - rz, "bobin_craft:weathered_bark");
                }
            }
            set(cx + dx, y + 3, cz - rz, BpSpec.of("dark_oak_slab").with("type", "top"));
        }
        slateRoof(cx, y, cz, rx, rz, 4, variant);
        door(cx, y + 1, cz - rz + 2, "spruce_door", "north");
        for (int dy = 1; dy <= 6; dy++) {
            put(cx - rx + 1, y + dy, cz + rz - 1, dy == 1 ? "smoker" : "bricks");
        }
        bed(cx + rx - 1, y + 1, cz + rz - 2, "green");
        put(cx - rx + 2, y + 1, cz, "bobin_craft:field_table");
        loot(cx + rx - 1, y + 1, cz - rz + 3, "warzone_civilian");
        for (int dz = -1; dz <= 2; dz++) {
            set(cx + rx + 1, y + 1, cz + dz, BpSpec.of("spruce_log").with("axis", "z"));
        }
    }

    private void machineryHangar(int cx, int cz, int rx, int rz) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:cracked_concrete");
        for (int dz = -rz; dz <= rz; dz++) {
            for (int dx = -rx; dx <= rx; dx++) {
                boolean b = (Math.abs(dx) == rx || Math.abs(dz) == rz);
                int ry = 6 + (rx - Math.abs(dx)) / 3;
                if (b && !(dz == -rz && Math.abs(dx) <= 4)) {
                    for (int dy = 1; dy <= 5; dy++) {
                        String m = dy <= 2
                                ? "bobin_craft:old_cinderblock"
                                : ((dy == 4 && Math.abs(dx) == rx && dz % 3 == 0) ? windowMat(dx, dy, dz, 1) : "bobin_craft:rusted_sheet_metal");
                        put(cx + dx, y + dy, cz + dz, m);
                    }
                } else if (dz == -rz && Math.abs(dx) <= 4) {
                    put(cx + dx, y + 5, cz + dz, "bobin_craft:metal_garage_door");
                }
                // Close the front and back gable triangles completely from y+6 up to ry-1.
                if (Math.abs(dz) == rz) {
                    for (int dy = 6; dy < ry; dy++) {
                        put(cx + dx, y + dy, cz + dz, "bobin_craft:rusted_sheet_metal");
                    }
                } else if (ry > 6 && (rx - Math.abs(dx)) % 3 == 0) {
                    put(cx + dx, y + ry - 1, cz + dz, "iron_bars");
                }
                put(cx + dx, y + ry, cz + dz, (dx + dz) % 4 == 0 ? "bobin_craft:rusted_sheet_metal" : "bobin_craft:corrugated_slate");
            }
        }
        for (int dz = -rz + 2; dz <= rz - 2; dz += 3) {
            put(cx - rx + 1, y + 1, cz + dz, "bobin_craft:supply_crate");
            put(cx - rx + 1, y + 2, cz + dz, "bobin_craft:supply_crate");
            put(cx + rx - 1, y + 1, cz + dz, "smithing_table");
        }
        for (int dx = -2; dx <= 2; dx++) {
            put(cx + dx, y + 1, cz + 2, "hay_block");
            put(cx + dx, y + 1, cz - 1, "bobin_craft:equipment_panel");
        }
        loot(cx - rx + 2, y + 1, cz + rz - 1, "warzone_technical");
        loot(cx + rx - 2, y + 1, cz + rz - 1, "warzone_supply");
    }

    private void tractorWorkshop(int cx, int cz, int rx, int rz) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:cracked_concrete");
        shellBox(cx, y, cz, rx, rz, 5, "bobin_craft:cracked_red_brick", "bobin_craft:old_cinderblock", 1);
        slateRoof(cx, y, cz, rx, rz, 6, 1);
        for (int dx = -3; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                put(cx + dx, y + dy, cz - rz, "air");
            }
            put(cx + dx, y + 4, cz - rz, "bobin_craft:metal_garage_door");
            put(cx + dx, y + 5, cz - rz, "bobin_craft:old_cinderblock");
        }
        door(cx + rx - 2, y + 1, cz - rz, "spruce_door", "north");
        for (int dz = -rz + 1; dz < rz; dz++) {
            if (dz != 0) {
                for (int dy = 1; dy <= 5; dy++) {
                    put(cx + 3, y + dy, cz + dz, "bobin_craft:old_cinderblock");
                }
            }
        }
        for (int dx = -rx + 1; dx <= 2; dx++) {
            put(cx + dx, y + 4, cz, "iron_bars");
        }
        put(cx - 1, y + 3, cz, "chain");
        put(cx - rx + 1, y + 1, cz + rz - 1, "blast_furnace");
        put(cx - rx + 2, y + 1, cz + rz - 1, "anvil");
        put(cx + 1, y + 1, cz + rz - 1, "grindstone");
        loot(cx + rx - 1, y + 1, cz + rz - 1, "warzone_technical");
    }

    private void yardOutbuilding(int cx, int cz, int kind) {
        int y = basePad(cx, cz, 3, 3, kind == 4 ? "bobin_craft:dark_forest_soil" : "spruce_planks");
        switch (kind % 6) {
            case 0 -> {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (dz == 2 || Math.abs(dx) == 2) {
                            for (int dy = 1; dy <= 2; dy++) {
                                put(cx + dx, y + dy, cz + dz, "bobin_craft:damaged_wood_panel");
                            }
                        } else if (dz == -2 && Math.abs(dx) == 2) {
                            for (int dy = 1; dy <= 2; dy++) {
                                put(cx + dx, y + dy, cz + dz, "bobin_craft:weathered_bark");
                            }
                        } else if (dz >= 0) {
                            set(cx + dx, y + 1, cz + dz, BpSpec.of("spruce_log").with("axis", "x"));
                        }
                        put(cx + dx, y + 3, cz + dz, "bobin_craft:corrugated_slate");
                    }
                }
            }
            case 1 -> {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (dz >= 0) {
                            boolean b = (Math.abs(dx) == 2 || dz == 0 || dz == 2);
                            if (b && !(dz == 0 && dx == 0)) {
                                for (int dy = 1; dy <= 2; dy++) {
                                    put(cx + dx, y + dy, cz + dz, "bobin_craft:damaged_wood_panel");
                                }
                            } else {
                                put(cx + dx, y + 1, cz + dz, "hay_block");
                            }
                            put(cx + dx, y + 3, cz + dz, "bobin_craft:corrugated_slate");
                        } else if (Math.abs(dx) == 2 || dz == -2) {
                            put(cx + dx, y + 1, cz + dz, "spruce_fence");
                        }
                    }
                }
            }
            case 2 -> {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (!(dz == -2 && dx == 0)) {
                            put(cx + dx, y + 1, cz + dz, "bobin_craft:old_cinderblock");
                            put(cx + dx, y + 2, cz + dz, "bobin_craft:old_cinderblock");
                        }
                        put(cx + dx, y + 3, cz + dz, "bobin_craft:mossy_forest_soil");
                    }
                }
                door(cx, y + 1, cz - 2, "spruce_door", "north");
                loot(cx, y + 1, cz, "warzone_civilian");
            }
            case 3 -> {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        boolean b = (Math.abs(dx) == 2 || Math.abs(dz) == 2);
                        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                        if (b && !(dz == -2 && dx == 0)) {
                            put(cx + dx, y + 1, cz + dz, corner ? "bobin_craft:weathered_bark" : "bobin_craft:old_window_frame");
                            put(cx + dx, y + 2, cz + dz, corner ? "bobin_craft:weathered_bark"
                                    : ((dx + dz) % 2 == 0 ? "bobin_craft:old_window_frame" : "bobin_craft:broken_window_glass"));
                        }
                        set(cx + dx, y + 3, cz + dz, BpSpec.of("dark_oak_slab").with("type", "bottom"));
                    }
                }
            }
            case 4 -> {
                shellBox(cx, y, cz, 2, 2, 3, "bobin_craft:old_cinderblock", "bobin_craft:cracked_concrete", 1);
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        put(cx + dx, y + 4, cz + dz, "bobin_craft:corrugated_slate");
                    }
                }
                door(cx, y + 1, cz - 2, "spruce_door", "north");
                loot(cx, y + 1, cz + 1, "warzone_supply");
            }
            default -> {
                shellBox(cx, y, cz, 2, 2, 3, "bobin_craft:cracked_red_brick", "bobin_craft:cracked_concrete", 1);
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        put(cx + dx, y + 4, cz + dz, "bobin_craft:rusted_sheet_metal");
                    }
                }
                door(cx, y + 1, cz - 2, "spruce_door", "north");
                put(cx - 1, y + 1, cz + 1, "crafting_table");
                put(cx + 1, y + 1, cz + 1, "anvil");
            }
        }
    }

    private void boilerHouse(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:cracked_concrete");
        shellBox(cx, y, cz, rx, rz, 6, "bobin_craft:cracked_red_brick", "bobin_craft:dark_concrete_panel", variant % 3);
        for (int dz = -rz; dz <= rz; dz++) {
            for (int dx = -rx; dx <= rx; dx++) {
                put(cx + dx, y + 7, cz + dz, "bobin_craft:dark_concrete_panel");
            }
        }
        door(cx, y + 1, cz - rz, "iron_door", "north");
        put(cx - 2, y + 5, cz - rz, "bobin_craft:rusted_vent_grille");
        put(cx + 2, y + 5, cz - rz, "bobin_craft:rusted_vent_grille");
        for (int bx : new int[]{-rx + 3, 2}) {
            for (int dz = -1; dz <= 2; dz++) {
                put(cx + bx, y + 1, cz + dz, "blast_furnace");
                put(cx + bx, y + 2, cz + dz, "bobin_craft:dark_concrete_panel");
                put(cx + bx, y + 3, cz + dz, "bobin_craft:utility_pipe");
            }
        }
        put(cx - rx + 1, y + 2, cz, "bobin_craft:equipment_panel");
        for (int dy = 1; dy <= 20; dy++) {
            for (int dz = rz - 2; dz <= rz; dz++) {
                for (int dx = rx - 2; dx <= rx; dx++) {
                    boolean core = (dx == rx - 1 && dz == rz - 1);
                    put(cx + dx, y + dy, cz + dz, core ? "air" : (dy % 6 == 0 ? "bobin_craft:dark_concrete_panel" : "bobin_craft:cracked_red_brick"));
                }
            }
        }
        loot(cx - rx + 1, y + 1, cz + rz - 1, "warzone_technical");
    }

    private void fuelDepot(int cx, int cz, int rx, int rz) {
        int y = basePad(cx, cz, rx, rz, "bobin_craft:weathered_gravel");
        for (int dz = -rz; dz <= rz; dz++) {
            for (int dx = -rx; dx <= rx; dx++) {
                if ((Math.abs(dx) == rx || Math.abs(dz) == rz) && !(dz == -rz && Math.abs(dx) <= 1)) {
                    put(cx + dx, y + 1, cz + dz, "bobin_craft:dark_concrete_panel");
                }
            }
        }
        for (int tz : new int[]{-rz + 3, 0, rz - 3}) {
            for (int dx = -rx + 2; dx <= 2; dx++) {
                for (int dy = 1; dy <= 3; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dy == 2 || dz == 0) {
                            put(cx + dx, y + dy, cz + tz + dz, (dx + dy) % 3 == 0 ? "bobin_craft:rusted_sheet_metal" : "bobin_craft:container_red");
                        }
                    }
                }
            }
            put(cx + 3, y + 2, cz + tz, "bobin_craft:utility_pipe");
        }
        for (int dz = -rz + 3; dz <= rz - 3; dz++) {
            put(cx + 4, y + 2, cz + dz, "bobin_craft:utility_pipe");
        }
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = 5; dx <= rx - 1; dx++) {
                put(cx + dx, y, cz + dz, "bobin_craft:cracked_concrete");
                boolean b = (dx == 5 || dx == rx - 1 || Math.abs(dz) == 3);
                if (b && !(dx == 5 && dz == 0)) {
                    for (int dy = 1; dy <= 3; dy++) {
                        put(cx + dx, y + dy, cz + dz, "bobin_craft:old_cinderblock");
                    }
                } else if (dx == 5 && dz == 0) {
                    put(cx + dx, y + 3, cz + dz, "bobin_craft:old_cinderblock");
                }
                put(cx + dx, y + 4, cz + dz, "bobin_craft:corrugated_slate");
            }
        }
        put(cx + rx - 2, y + 2, cz, "bobin_craft:equipment_panel");
        loot(cx + rx - 2, y + 1, cz + 2, "warzone_technical");
    }

    private void industrialCheckpoint(int cx, int cz, int variant) {
        int y = basePad(cx, cz, 4, 3, "bobin_craft:cracked_concrete");
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -3; dx <= 1; dx++) {
                boolean b = (dx == -3 || dx == 1 || Math.abs(dz) == 2);
                if (b && !(dx == 1 && dz == 0)) {
                    for (int dy = 1; dy <= 3; dy++) {
                        boolean w = (dy == 2 && ((dx == 1 && Math.abs(dz) == 1) || (dz == -2 && dx == -1)));
                        String wId = (dz == -2) ? "bobin_craft:old_window_frame" : "bobin_craft:broken_window_glass";
                        put(cx + dx, y + dy, cz + dz, w ? wId : "bobin_craft:cracked_red_brick");
                    }
                } else if (dx == 1 && dz == 0) {
                    put(cx + dx, y + 3, cz + dz, "bobin_craft:cracked_red_brick");
                }
                put(cx + dx, y + 4, cz + dz, "bobin_craft:dark_concrete_panel");
            }
        }
        door(cx + 1, y + 1, cz, "spruce_door", "east");
        set(cx - 2, y + 1, cz - 1, BpSpec.of("bobin_craft:radio").with("facing", "south"));
        put(cx - 2, y + 1, cz + 1, "bobin_craft:equipment_panel");
        loot(cx - 1, y + 1, cz + 1, "warzone_command");
        put(cx + 3, y + 1, cz - 2, "bobin_craft:dark_concrete_panel");
        put(cx + 3, y + 2, cz - 2, "red_terracotta");
        for (int dz = -1; dz <= 2; dz++) {
            put(cx + 3, y + 2, cz + dz, "iron_bars");
        }
        for (int dy = 1; dy <= 5; dy++) {
            put(cx - 3, y + dy, cz - 2, "iron_bars");
        }
        set(cx - 3, y + 6, cz - 2, BpSpec.of("bobin_craft:field_light"));
    }

    private void workerBarracks(int cx, int cz, int rx, int rz, int variant) {
        int y = basePad(cx, cz, rx, rz, "spruce_planks");
        String wall = variant % 2 == 0 ? "bobin_craft:damaged_wood_panel" : "bobin_craft:old_cinderblock";
        shellBox(cx, y, cz, rx, rz, 3, wall, "bobin_craft:cracked_concrete", variant % 3);
        slateRoof(cx, y, cz, rx, rz, 4, variant % 3);
        door(cx, y + 1, cz - rz, "spruce_door", "north");
        for (int dz = -rz + 1; dz < rz; dz++) {
            if (dz != 0) {
                for (int dy = 1; dy <= 3; dy++) {
                    put(cx + 1, y + dy, cz + dz, "bobin_craft:peeling_paint_wall");
                }
            }
        }
        for (int bx : new int[]{-rx + 1, -rx + 4}) {
            bed(cx + bx, y + 1, cz + rz - 3, "gray");
        }
        put(cx + rx - 1, y + 1, cz + rz - 1, "bobin_craft:equipment_locker");
        put(cx + rx - 2, y + 1, cz + rz - 1, "smoker");
        put(cx + 3, y + 1, cz, "bobin_craft:field_table");
        loot(cx + rx - 1, y + 1, cz - rz + 1, "warzone_civilian");
    }

    private void transformerBooth(int cx, int cz, int variant) {
        int y = basePad(cx, cz, 3, 3, "bobin_craft:cracked_concrete");
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean b = (Math.abs(dx) == 2 || Math.abs(dz) == 2);
                if (b) {
                    for (int dy = 1; dy <= 3; dy++) {
                        String m;
                        if (dz == -2 && dx == 0 && dy <= 2) {
                            m = "iron_door";
                        } else if (dy == 2 && Math.abs(dx) == 2 && dz == 0) {
                            m = "bobin_craft:rusted_vent_grille";
                        } else if (dy == 2 && dz == 2 && dx == 0) {
                            m = "bobin_craft:transformer_cabinet";
                        } else {
                            m = "bobin_craft:dark_concrete_panel";
                        }
                        if ("iron_door".equals(m)) {
                            if (dy == 1) {
                                door(cx + dx, y + 1, cz + dz, "iron_door", "north");
                            }
                        } else {
                            put(cx + dx, y + dy, cz + dz, m);
                        }
                    }
                }
                put(cx + dx, y + 4, cz + dz, "bobin_craft:rusted_sheet_metal");
            }
        }
        put(cx, y + 1, cz + 1, "bobin_craft:transformer_cabinet");
        put(cx - 1, y + 1, cz + 1, "bobin_craft:equipment_panel");
        for (int dx : new int[]{-1, 1}) {
            put(cx + dx, y + 5, cz, "bobin_craft:insulator");
            put(cx + dx, y + 6, cz, "bobin_craft:busbar");
        }
        put(cx, y + 6, cz, "bobin_craft:busbar");
    }
}
