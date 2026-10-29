package com.bobincraft.vehicle;

import com.google.gson.*;
import net.minecraft.entity.*;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.EntityView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

/** Shared model-space geometry: the polygon mesh used by rendering is also the ballistic armor surface. */
public final class ArmorGeometry {
    public record Face(float[][] v, float[] n, float[][] uv) { }
    public record Part(String group, String material, String motion, Box box, boolean armor, float[] pivot, List<Face> faces) {
        public Part(String group, String material, String motion, Box box) {
            this(group, material, motion, box, true, null, List.of());
        }
        public boolean track() { return motion.equals("track"); }
        public boolean wheel() { return motion.equals("wheel"); }
        public boolean breech() { return motion.equals("breech"); }
    }
    public record Hatch(String group, Vec3d center, Vec3d hinge, float sign) { }
    public record Surface(String group, Vec3d point) { }
    public record WorldCollider(String group, Box box) { }
    public record SweptTankHit(ArmoredVehicleEntity vehicle, String group, double t, Vec3d contactPos, Vec3d blastCenter, boolean interior) { }

    private record GroupArmorMesh(
        double envMinX, double envMinY, double envMinZ,
        double envMaxX, double envMaxY, double envMaxZ,
        float[] partBounds,
        int[] triOffsets,
        float[] tris
    ) { }

    private static final List<String> GROUPS = List.of("hull", "turret", "gun", "hatch", "gunner_hatch");
    public static final Vec3d TURRET_PIVOT, GUN_PIVOT, OPERATOR_SEAT, GUNNER_SEAT = new Vec3d(-.68, -.52, -.10), DRIVER_VIEW;
    public static final double BARREL_LENGTH, HALF_WIDTH, HALF_LENGTH, DECK_HEIGHT;
    public static final Box CABIN;
    /** Raised commander hatch coaming rim boxes in turret local space (around opening x in [0.14, 1.22], z in [-0.38, 0.70]). */
    public static final List<Box> HATCH_RIM_BOXES = List.of(
        new Box(0.06, 1.23, -0.44, 0.14, 1.30, 0.76),
        new Box(1.22, 1.23, -0.44, 1.30, 1.30, 0.76),
        new Box(0.06, 1.23, -0.46, 1.30, 1.30, -0.38),
        new Box(0.06, 1.23, 0.70, 1.30, 1.30, 0.78)
    );
    public static final List<Hatch> HATCHES;
    public static final List<Part> PARTS;
    private static final List<Part> COLLISION;
    private static final Map<String, List<Part>> GROUP_PARTS;
    private static final Map<String, Box> GROUP_BOUNDS;
    private static final GroupArmorMesh[] GROUP_MESHES;

    static {
        try (var stream = ArmorGeometry.class.getResourceAsStream("/assets/bobin_craft/armored_geometry.json")) {
            if (stream == null) throw new IllegalStateException("Missing armor geometry");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            int format = root.has("format") ? root.get("format").getAsInt() : 2;
            if (format < 3) throw new IllegalStateException("Expected armored_geometry.json format >= 3, got " + format);
            PARTS = read(root.getAsJsonArray("parts"), true);
            COLLISION = read(root.getAsJsonArray("collision"), false);
            var layout = root.getAsJsonObject("layout");
            TURRET_PIVOT = vector(layout.getAsJsonArray("turretPivot"));
            GUN_PIVOT = vector(layout.getAsJsonArray("gunPivot"));
            OPERATOR_SEAT = vector(layout.getAsJsonArray("operatorSeat"));
            DRIVER_VIEW = vector(layout.getAsJsonArray("driverView"));
            BARREL_LENGTH = layout.get("barrelLength").getAsDouble();
            HALF_WIDTH = layout.get("hullHalfWidth").getAsDouble();
            HALF_LENGTH = layout.get("hullHalfLength").getAsDouble();
            DECK_HEIGHT = layout.get("deckHeight").getAsDouble();
            var c = layout.getAsJsonArray("cabin");
            CABIN = new Box(c.get(0).getAsDouble(), c.get(1).getAsDouble(), c.get(2).getAsDouble(), c.get(3).getAsDouble(), c.get(4).getAsDouble(), c.get(5).getAsDouble());
            List<Hatch> hatches = new ArrayList<>();
            for (var value : root.getAsJsonArray("hatches")) {
                var h = value.getAsJsonObject();
                hatches.add(new Hatch(h.get("group").getAsString(), vector(h.getAsJsonArray("center")), vector(h.getAsJsonArray("hinge")), h.get("sign").getAsFloat()));
            }
            HATCHES = List.copyOf(hatches);

            Map<String, List<Part>> byGroup = new HashMap<>();
            Map<String, Box> bounds = new HashMap<>();
            for (var p : PARTS) {
                byGroup.computeIfAbsent(p.group, k -> new ArrayList<>()).add(p);
                Box expanded = p.box.expand(p.motion.isEmpty() ? .02 : .30);
                bounds.merge(p.group, expanded, Box::union);
            }
            byGroup.replaceAll((k, v) -> List.copyOf(v));
            GROUP_PARTS = Map.copyOf(byGroup);
            GROUP_BOUNDS = Map.copyOf(bounds);

            GROUP_MESHES = new GroupArmorMesh[GROUPS.size()];
            for (int gIdx = 0; gIdx < GROUPS.size(); gIdx++) {
                String g = GROUPS.get(gIdx);
                List<Part> gParts = byGroup.getOrDefault(g, List.of());
                List<Part> armorParts = new ArrayList<>();
                int totalTris = 0;
                for (var p : gParts) {
                    if (p.armor && !p.faces.isEmpty()) {
                        armorParts.add(p);
                        for (var f : p.faces) totalTris += (f.v.length >= 4 ? 2 : 1);
                    }
                }
                float[] partBounds = new float[armorParts.size() * 6];
                int[] triOffsets = new int[armorParts.size() + 1];
                float[] tris = new float[totalTris * 9];
                int bIdx = 0, tIdx = 0;
                for (int pIdx = 0; pIdx < armorParts.size(); pIdx++) {
                    Part p = armorParts.get(pIdx);
                    Box b = p.box;
                    partBounds[bIdx++] = (float) (b.minX - 1e-3);
                    partBounds[bIdx++] = (float) (b.minY - 1e-3);
                    partBounds[bIdx++] = (float) (b.minZ - 1e-3);
                    partBounds[bIdx++] = (float) (b.maxX + 1e-3);
                    partBounds[bIdx++] = (float) (b.maxY + 1e-3);
                    partBounds[bIdx++] = (float) (b.maxZ + 1e-3);
                    triOffsets[pIdx] = tIdx;
                    for (var f : p.faces) {
                        tIdx = packTri(tris, tIdx, f.v[0], f.v[1], f.v[2]);
                        if (f.v.length >= 4) tIdx = packTri(tris, tIdx, f.v[0], f.v[2], f.v[3]);
                    }
                }
                triOffsets[armorParts.size()] = tIdx;
                Box env = bounds.get(g);
                GROUP_MESHES[gIdx] = new GroupArmorMesh(
                    env.minX, env.minY, env.minZ, env.maxX, env.maxY, env.maxZ,
                    partBounds, triOffsets, tris
                );
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ArmorGeometry() { }

    private static int packTri(float[] out, int idx, float[] v0, float[] v1, float[] v2) {
        out[idx]     = v0[0]; out[idx + 1] = v0[1]; out[idx + 2] = v0[2];
        out[idx + 3] = v1[0] - v0[0]; out[idx + 4] = v1[1] - v0[1]; out[idx + 5] = v1[2] - v0[2];
        out[idx + 6] = v2[0] - v0[0]; out[idx + 7] = v2[1] - v0[1]; out[idx + 8] = v2[2] - v0[2];
        return idx + 9;
    }

    private static Vec3d vector(JsonArray a) {
        return new Vec3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static List<Part> read(JsonArray values, boolean readFaces) {
        List<Part> result = new ArrayList<>(values.size());
        for (var value : values) {
            var p = value.getAsJsonObject();
            var b = p.getAsJsonArray("box");
            Box box = new Box(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble());
            String group = p.get("part").getAsString();
            String material = p.has("material") ? p.get("material").getAsString() : "steel";
            String motion = p.has("motion") ? p.get("motion").getAsString() : "";
            boolean armor = !p.has("armor") || p.get("armor").getAsBoolean();
            float[] pivot = null;
            if (p.has("pivot")) {
                var pv = p.getAsJsonArray("pivot");
                pivot = new float[pv.size()];
                for (int i = 0; i < pv.size(); i++) pivot[i] = pv.get(i).getAsFloat();
            }
            List<Face> faces = List.of();
            if (readFaces && p.has("faces")) {
                var fa = p.getAsJsonArray("faces");
                List<Face> fList = new ArrayList<>(fa.size());
                for (var fv : fa) {
                    var fo = fv.getAsJsonObject();
                    var va = fo.getAsJsonArray("v");
                    var na = fo.getAsJsonArray("n");
                    var uva = fo.getAsJsonArray("uv");
                    float[][] verts = new float[va.size()][3];
                    float[][] uvs = new float[uva.size()][2];
                    for (int i = 0; i < va.size(); i++) {
                        var pt = va.get(i).getAsJsonArray();
                        verts[i][0] = pt.get(0).getAsFloat();
                        verts[i][1] = pt.get(1).getAsFloat();
                        verts[i][2] = pt.get(2).getAsFloat();
                        var uv = uva.get(i).getAsJsonArray();
                        uvs[i][0] = uv.get(0).getAsFloat();
                        uvs[i][1] = uv.get(1).getAsFloat();
                    }
                    float[] norm = new float[]{na.get(0).getAsFloat(), na.get(1).getAsFloat(), na.get(2).getAsFloat()};
                    fList.add(new Face(verts, norm, uvs));
                }
                faces = List.copyOf(fList);
            }
            result.add(new Part(group, material, motion, box, armor, pivot, faces));
        }
        return List.copyOf(result);
    }

    private static Vec3d rx(Vec3d v, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(v.x, v.y * c + v.z * s, v.z * c - v.y * s);
    }

    private static Vec3d ry(Vec3d v, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(v.x * c + v.z * s, v.y, v.z * c - v.x * s);
    }

    private static Vec3d rz(Vec3d v, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(v.x * c + v.y * s, v.y * c - v.x * s, v.z);
    }

    public static Vec3d toWorld(ArmoredVehicleEntity v, String group, Vec3d p) {
        for (var h : HATCHES) if (group.equals(h.group)) p = rx(p.subtract(h.hinge), -v.hatchAngle() * h.sign).add(h.hinge);
        if (group.equals("gun")) p = rx(p, v.elevation()).add(GUN_PIVOT);
        if (!group.equals("hull")) p = ry(p, v.getYaw() - v.turretYaw()).add(TURRET_PIVOT);
        return ry(rx(rz(p, v.bank()), -v.suspension()), 180 - v.getYaw()).add(v.getPos());
    }

    public static Vec3d toLocal(ArmoredVehicleEntity v, String group, Vec3d p) {
        p = rz(rx(ry(p.subtract(v.getPos()), v.getYaw() - 180), v.suspension()), -v.bank());
        if (group.equals("hull")) return p;
        p = ry(p.subtract(TURRET_PIVOT), v.turretYaw() - v.getYaw());
        if (group.equals("gun")) return rx(p.subtract(GUN_PIVOT), -v.elevation());
        for (var h : HATCHES) if (group.equals(h.group)) p = rx(p.subtract(h.hinge), v.hatchAngle() * h.sign).add(h.hinge);
        return p;
    }

    private static boolean boxHitsSegment(
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        double ox, double oy, double oz, double invDx, double invDy, double invDz, double maxT
    ) {
        double tx1 = (minX - ox) * invDx, tx2 = (maxX - ox) * invDx;
        double tmin = Math.min(tx1, tx2), tmax = Math.max(tx1, tx2);
        double ty1 = (minY - oy) * invDy, ty2 = (maxY - oy) * invDy;
        tmin = Math.max(tmin, Math.min(ty1, ty2));
        tmax = Math.min(tmax, Math.max(ty1, ty2));
        double tz1 = (minZ - oz) * invDz, tz2 = (maxZ - oz) * invDz;
        tmin = Math.max(tmin, Math.min(tz1, tz2));
        tmax = Math.min(tmax, Math.max(tz1, tz2));
        return tmax >= Math.max(0.0, tmin) && tmin <= maxT;
    }

    private static double raycastGroup(
        GroupArmorMesh mesh,
        double ox, double oy, double oz,
        double dx, double dy, double dz,
        double bestT
    ) {
        double invDx = Math.abs(dx) > 1e-12 ? 1.0 / dx : (dx >= 0 ? 1e12 : -1e12);
        double invDy = Math.abs(dy) > 1e-12 ? 1.0 / dy : (dy >= 0 ? 1e12 : -1e12);
        double invDz = Math.abs(dz) > 1e-12 ? 1.0 / dz : (dz >= 0 ? 1e12 : -1e12);
        if (!boxHitsSegment(mesh.envMinX, mesh.envMinY, mesh.envMinZ, mesh.envMaxX, mesh.envMaxY, mesh.envMaxZ, ox, oy, oz, invDx, invDy, invDz, bestT)) {
            return bestT;
        }
        float[] pBounds = mesh.partBounds;
        int[] offsets = mesh.triOffsets;
        float[] tris = mesh.tris;
        for (int pIdx = 0, bIdx = 0, nParts = offsets.length - 1; pIdx < nParts; pIdx++, bIdx += 6) {
            if (!boxHitsSegment(pBounds[bIdx], pBounds[bIdx + 1], pBounds[bIdx + 2], pBounds[bIdx + 3], pBounds[bIdx + 4], pBounds[bIdx + 5], ox, oy, oz, invDx, invDy, invDz, bestT)) {
                continue;
            }
            for (int i = offsets[pIdx], end = offsets[pIdx + 1]; i < end; i += 9) {
                double e1x = tris[i + 3], e1y = tris[i + 4], e1z = tris[i + 5];
                double e2x = tris[i + 6], e2y = tris[i + 7], e2z = tris[i + 8];
                double hx = dy * e2z - dz * e2y;
                double hy = dz * e2x - dx * e2z;
                double hz = dx * e2y - dy * e2x;
                double det = e1x * hx + e1y * hy + e1z * hz;
                if (det > -1e-8 && det < 1e-8) continue;
                double f = 1.0 / det;
                double sx = ox - tris[i], sy = oy - tris[i + 1], sz = oz - tris[i + 2];
                double u = f * (sx * hx + sy * hy + sz * hz);
                if (u < -1e-5 || u > 1.00001) continue;
                double qx = sy * e1z - sz * e1y;
                double qy = sz * e1x - sx * e1z;
                double qz = sx * e1y - sy * e1x;
                double w = f * (dx * qx + dy * qy + dz * qz);
                if (w < -1e-5 || u + w > 1.00002) continue;
                double t = f * (e2x * qx + e2y * qy + e2z * qz);
                if (t >= 0.0 && t < bestT) bestT = t;
            }
        }
        return bestT;
    }

    /** Server ray hit detection against the actual polygon armor surfaces of the mesh. */
    public static Optional<Surface> raycast(ArmoredVehicleEntity v, Vec3d start, Vec3d end) {
        double vx = v.getX(), vy = v.getY(), vz = v.getZ();
        double hw = HALF_WIDTH + HALF_LENGTH + 2.5;
        double sdx = end.x - start.x, sdy = end.y - start.y, sdz = end.z - start.z;
        double sInvX = Math.abs(sdx) > 1e-12 ? 1.0 / sdx : (sdx >= 0 ? 1e12 : -1e12);
        double sInvY = Math.abs(sdy) > 1e-12 ? 1.0 / sdy : (sdy >= 0 ? 1e12 : -1e12);
        double sInvZ = Math.abs(sdz) > 1e-12 ? 1.0 / sdz : (sdz >= 0 ? 1e12 : -1e12);
        if (!boxHitsSegment(vx - hw, vy - 2.5, vz - hw, vx + hw, vy + 7.0, vz + hw, start.x, start.y, start.z, sInvX, sInvY, sInvZ, 1.0)) {
            return Optional.empty();
        }

        // Transform start & end to hull local space using shared trig values
        double yawRad = Math.toRadians(v.getYaw() - 180.0);
        double cy = Math.cos(yawRad), sy = Math.sin(yawRad);
        double ax0 = start.x - vx, ay0 = start.y - vy, az0 = start.z - vz;
        double bx0 = end.x - vx, by0 = end.y - vy, bz0 = end.z - vz;
        double hxA = ax0 * cy + az0 * sy, hyA = ay0, hzA = az0 * cy - ax0 * sy;
        double hxB = bx0 * cy + bz0 * sy, hyB = by0, hzB = bz0 * cy - bx0 * sy;

        float susp = v.suspension();
        if (susp != 0f) {
            double sr = Math.toRadians(susp), cs = Math.cos(sr), ss = Math.sin(sr);
            double nyA = hyA * cs + hzA * ss, nzA = hzA * cs - hyA * ss;
            double nyB = hyB * cs + hzB * ss, nzB = hzB * cs - hyB * ss;
            hyA = nyA; hzA = nzA; hyB = nyB; hzB = nzB;
        }
        float bnk = v.bank();
        if (bnk != 0f) {
            double br = Math.toRadians(-bnk), cb = Math.cos(br), sb = Math.sin(br);
            double nxA = hxA * cb + hyA * sb, nyA = hyA * cb - hxA * sb;
            double nxB = hxB * cb + hyB * sb, nyB = hyB * cb - hxB * sb;
            hxA = nxA; hyA = nyA; hxB = nxB; hyB = nyB;
        }

        int bestGroupIdx = -1;
        double bestT = 1.0 + 1e-6;

        // Group 0: hull
        double tHull = raycastGroup(GROUP_MESHES[0], hxA, hyA, hzA, hxB - hxA, hyB - hyA, hzB - hzA, bestT);
        if (tHull < bestT) { bestT = tHull; bestGroupIdx = 0; }

        // Transform to turret local space
        double tx0 = hxA - TURRET_PIVOT.x, tyA = hyA - TURRET_PIVOT.y, tz0 = hzA - TURRET_PIVOT.z;
        double tx1 = hxB - TURRET_PIVOT.x, tyB = hyB - TURRET_PIVOT.y, tz1 = hzB - TURRET_PIVOT.z;
        double relRad = Math.toRadians(v.turretYaw() - v.getYaw());
        double cr = Math.cos(relRad), sr = Math.sin(relRad);
        double txA = tx0 * cr + tz0 * sr, tzA = tz0 * cr - tx0 * sr;
        double txB = tx1 * cr + tz1 * sr, tzB = tz1 * cr - tx1 * sr;

        // Group 1: turret
        double tTurret = raycastGroup(GROUP_MESHES[1], txA, tyA, tzA, txB - txA, tyB - tyA, tzB - tzA, bestT);
        if (tTurret < bestT) { bestT = tTurret; bestGroupIdx = 1; }

        // Group 2: gun
        double gxA = txA - GUN_PIVOT.x, gyA = tyA - GUN_PIVOT.y, gzA = tzA - GUN_PIVOT.z;
        double gxB = txB - GUN_PIVOT.x, gyB = tyB - GUN_PIVOT.y, gzB = tzB - GUN_PIVOT.z;
        float elev = v.elevation();
        if (elev != 0f) {
            double er = Math.toRadians(-elev), ce = Math.cos(er), se = Math.sin(er);
            double ngyA = gyA * ce + gzA * se, ngzA = gzA * ce - gyA * se;
            double ngyB = gyB * ce + gzB * se, ngzB = gzB * ce - gyB * se;
            gyA = ngyA; gzA = ngzA; gyB = ngyB; gzB = ngzB;
        }
        double tGun = raycastGroup(GROUP_MESHES[2], gxA, gyA, gzA, gxB - gxA, gyB - gyA, gzB - gzA, bestT);
        if (tGun < bestT) { bestT = tGun; bestGroupIdx = 2; }

        // Groups 3 & 4: hatch & gunner_hatch (gunner_hatch has sign=0 and stays permanently closed)
        float hAng = v.hatchAngle();
        for (int hIdx = 0; hIdx < 2; hIdx++) {
            Hatch h = HATCHES.get(hIdx);
            double hr = Math.toRadians(hAng * h.sign), ch = Math.cos(hr), sh = Math.sin(hr);
            double hgx = h.hinge.x, hgy = h.hinge.y, hgz = h.hinge.z;
            double dyA = tyA - hgy, dzA = tzA - hgz;
            double dyB = tyB - hgy, dzB = tzB - hgz;
            double ryA = hgy + dyA * ch + dzA * sh, rzA = hgz + dzA * ch - dyA * sh;
            double ryB = hgy + dyB * ch + dzB * sh, rzB = hgz + dzB * ch - dyB * sh;
            int gIdx = 3 + hIdx;
            double tHatch = raycastGroup(GROUP_MESHES[gIdx], txA, ryA, rzA, txB - txA, ryB - ryA, rzB - rzA, bestT);
            if (tHatch < bestT) { bestT = tHatch; bestGroupIdx = gIdx; }
        }

        if (bestGroupIdx < 0) return Optional.empty();
        Vec3d worldHit = new Vec3d(start.x + sdx * bestT, start.y + sdy * bestT, start.z + sdz * bestT);
        return Optional.of(new Surface(GROUPS.get(bestGroupIdx), worldHit));
    }

    public static Box partBox(Part p, ArmoredVehicleEntity v) {
        return p.motion.equals("breech") ? p.box.offset(0, 0, v.reloadTicks() > 0 ? Math.sin(Math.PI * v.reloadTicks() / ArmorControls.RELOAD_TICKS) * .22 : 0) : p.box;
    }

    public static Vec3d hatchCenter(ArmoredVehicleEntity v) { return toWorld(v, "turret", HATCHES.get(0).center); }

    public static Vec3d nearestHatchCenter(ArmoredVehicleEntity v, Vec3d from) {
        Vec3d best = hatchCenter(v);
        double dist = from.squaredDistanceTo(best);
        for (var h : HATCHES) {
            if (h.sign == 0) continue;
            Vec3d c = toWorld(v, "turret", h.center);
            double d = from.squaredDistanceTo(c);
            if (d < dist) { dist = d; best = c; }
        }
        return best;
    }

    /**
     * Checks whether a world-space point lies inside the turret fighting compartment.
     * Points on or above the turret roof/rim/closed hatch (local.y >= 1.225) are strictly outside.
     */
    public static boolean insideCabin(ArmoredVehicleEntity v, Vec3d p) {
        Vec3d local = toLocal(v, "turret", p);
        return local.x >= -1.285 && local.x <= 1.285
                && local.y >= -1.14 && local.y < 1.225
                && local.z >= -0.92 && local.z <= 0.96;
    }

    /** True if the point is inside the cabin or directly in the open commander hatch throat descending into the cabin. */
    public static boolean insideOrEnteringOpenHatch(ArmoredVehicleEntity v, Vec3d p) {
        if (insideCabin(v, p)) return true;
        if (!v.hatchOpen()) return false;
        Vec3d local = toLocal(v, "turret", p);
        return local.x >= 0.14 && local.x <= 1.22
                && local.z >= -0.38 && local.z <= 0.70
                && local.y >= -1.14 && local.y <= 2.15;
    }

    /** Distance from a blast point to the tank's physical structure (0 when inside the cabin). */
    public static double distanceToVehicle(ArmoredVehicleEntity v, Vec3d point) {
        if (insideCabin(v, point)) return 0.0;
        double bestSq = v.getPos().squaredDistanceTo(point);
        for (Box b : v.walkingBoxes()) {
            double dx = Math.max(Math.max(b.minX - point.x, 0.0), point.x - b.maxX);
            double dy = Math.max(Math.max(b.minY - point.y, 0.0), point.y - b.maxY);
            double dz = Math.max(Math.max(b.minZ - point.z, 0.0), point.z - b.maxZ);
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < bestSq) bestSq = dSq;
        }
        return Math.sqrt(bestSq);
    }

    /** Closed armor shields the seated or cabin-occupying crew; a charge already in the basket is never shielded. */
    public static double crewExposure(ArmoredVehicleEntity v, Vec3d center, Entity crew) {
        if (insideCabin(v, center)) return 1.0;
        Vec3d aim = crew.getEyePos();
        return raycast(v, center, aim).isEmpty() ? 1.0 : 0.15;
    }

    /** Finds the tank that a crew member is riding or standing inside, if any. */
    public static ArmoredVehicleEntity enclosingVehicle(Entity target) {
        if (target == null || target.getWorld() == null) return null;
        if (target.getVehicle() instanceof ArmoredVehicleEntity v && v.armorHealth() > 0) return v;
        for (Entity e : target.getWorld().getOtherEntities(target, target.getBoundingBox().expand(3.5),
                cand -> cand instanceof ArmoredVehicleEntity tank && tank.isAlive() && tank.armorHealth() > 0)) {
            ArmoredVehicleEntity tank = (ArmoredVehicleEntity) e;
            if (tank.isInCabin(target)) return tank;
        }
        return null;
    }

    public static boolean blocksCrewHit(Entity target, Vec3d start, Vec3d hit) {
        ArmoredVehicleEntity v = enclosingVehicle(target);
        if (v == null || v.armorHealth() <= 0) return false;
        if (insideCabin(v, start)) return false;
        var armor = raycast(v, start, hit);
        return armor.isPresent() && start.squaredDistanceTo(armor.get().point()) + 1e-5 < start.squaredDistanceTo(hit);
    }

    private static double sweptBoxEntryT(Vec3d startCenter, Vec3d delta, double rx, double ry, double rz, Box target) {
        Box expanded = new Box(
            target.minX - rx, target.minY - ry, target.minZ - rz,
            target.maxX + rx, target.maxY + ry, target.maxZ + rz
        );
        if (expanded.contains(startCenter)) return 0.0;
        double lenSq = delta.lengthSquared();
        if (lenSq < 1e-14) return Double.POSITIVE_INFINITY;
        double[] s = {startCenter.x, startCenter.y, startCenter.z};
        double[] d = {delta.x, delta.y, delta.z};
        double[] min = {expanded.minX, expanded.minY, expanded.minZ};
        double[] max = {expanded.maxX, expanded.maxY, expanded.maxZ};
        double entry = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-9) {
                if (s[axis] < min[axis] || s[axis] > max[axis]) return Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (min[axis] - s[axis]) / d[axis];
            double b = (max[axis] - s[axis]) / d[axis];
            entry = Math.max(entry, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
            if (entry > exit + 1e-7) return Double.POSITIVE_INFINITY;
        }
        if (entry >= -1e-7 && entry <= 1.0 + 1e-7 && exit > 1e-7) {
            return Math.max(0.0, Math.min(1.0, entry));
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Exact 3D local-frame swept collision for an armed FPV drone against an ArmoredVehicleEntity.
     * Evaluates hull, turret (including the open hatch window, raised coaming rim, and interior walls/floor/breech),
     * gun barrel, and hatches in their exact local coordinate spaces, plus any crew inside the cabin.
     */
    public static Optional<SweptTankHit> sweepFpvDrone(
        ArmoredVehicleEntity v,
        Entity drone,
        Entity owner,
        Box oldBody,
        Vec3d velocity,
        boolean previouslyAirborne,
        boolean flightContactReady
    ) {
        if (v == null || !v.isAlive() || v.armorHealth() <= 0) return Optional.empty();
        Vec3d startFoot = new Vec3d((oldBody.minX + oldBody.maxX) * 0.5, oldBody.minY, (oldBody.minZ + oldBody.maxZ) * 0.5);
        double halfW = (oldBody.maxX - oldBody.minX) * 0.5;
        double height = oldBody.maxY - oldBody.minY;
        double halfH = height * 0.5;
        Vec3d startCenter = startFoot.add(0, halfH, 0);
        Vec3d endCenter = startCenter.add(velocity);

        // Use a slightly compact horizontal radius (0.18) in local space so the 0.48-wide FPV passes cleanly through the 1.08x1.08 hatch coaming at all diagonal angles while still catching the 0.08-wide hatch rim.
        double localR = Math.min(halfW, 0.18);
        double bestT = Double.POSITIVE_INFINITY;
        String bestGroup = null;
        boolean bestInterior = false;
        boolean fastForward = v.forwardSpeed() > 0.06;

        for (String group : GROUPS) {
            Vec3d localStart = toLocal(v, group, startCenter);
            Vec3d localEnd = toLocal(v, group, endCenter);
            Vec3d localDelta = localEnd.subtract(localStart);

            List<Box> groupBoxes = new ArrayList<>();
            for (Part part : COLLISION) {
                if (!part.group.equals(group)) continue;
                if (fastForward && part.motion.equals("front_step")) continue;
                groupBoxes.add(part.box);
            }
            if (group.equals("turret")) {
                groupBoxes.addAll(HATCH_RIM_BOXES);
                // Interior cannon breech & seats so a drone inside the cabin also collides with internal equipment
                groupBoxes.add(new Box(-0.20, 0.20, -0.90, 0.20, 0.64, -0.46));
            } else if (group.equals("gun")) {
                groupBoxes.add(new Box(-0.14, -0.14, -BARREL_LENGTH, 0.14, 0.14, 0.0));
            }

            for (Box box : groupBoxes) {
                if (!previouslyAirborne && localStart.y - halfH >= box.maxY - 0.01 && localDelta.y >= -1e-4) {
                    continue;
                }
                double r = (group.equals("turret") && box.minY >= 1.229 && box.maxY <= 1.301) ? halfW : localR;
                double ry = halfH;
                double rz = r;
                if (group.equals("hatch") && v.hatchOpen()) {
                    ry = r;
                    rz = halfH;
                }
                double t = sweptBoxEntryT(localStart, localDelta, r, ry, rz, box);
                if (t < bestT) {
                    bestT = t;
                    bestGroup = group;
                    Vec3d hitTurretLocal = group.equals("turret")
                            ? localStart.add(localDelta.multiply(t))
                            : toLocal(v, "turret", startCenter.add(velocity.multiply(t)));
                    bestInterior = hitTurretLocal.y < 1.12
                            && hitTurretLocal.x >= -1.285 && hitTurretLocal.x <= 1.285
                            && hitTurretLocal.z >= -0.92 && hitTurretLocal.z <= 0.96;
                }
            }
        }

        // Also check direct contact with crew inside the tank (if not preceded by exterior armor hit)
        for (Entity other : v.getWorld().getOtherEntities(drone, oldBody.stretch(velocity).expand(0.35),
                e -> e != v && (flightContactReady || e != owner) && e.canHit() && e.isAlive())) {
            if (other.getVehicle() == v || v.isInCabin(other)) {
                double tCrew = sweptBoxEntryT(startCenter, velocity, halfW, halfH, halfW, other.getBoundingBox());
                if (tCrew < bestT) {
                    Vec3d crewHitCenter = startCenter.add(velocity.multiply(tCrew));
                    if (insideCabin(v, crewHitCenter) || !blocksCrewHit(other, startCenter, other.getBoundingBox().getCenter())) {
                        bestT = tCrew;
                        bestGroup = "crew";
                        bestInterior = true;
                    }
                }
            }
        }

        if (bestGroup == null || bestT > 1.0) return Optional.empty();
        Vec3d contactFoot = startFoot.add(velocity.multiply(bestT));
        Vec3d blastCenter = contactFoot.add(0, 0.10, 0);
        if (bestInterior) {
            Vec3d localBlast = toLocal(v, "turret", blastCenter);
            Vec3d clampedLocal = new Vec3d(
                MathHelper.clamp(localBlast.x, -1.15, 1.15),
                MathHelper.clamp(localBlast.y, -0.95, 0.85),
                MathHelper.clamp(localBlast.z, -0.80, 0.80)
            );
            blastCenter = toWorld(v, "turret", clampedLocal);
        } else {
            Vec3d localBlast = toLocal(v, "turret", blastCenter);
            if (localBlast.y >= 1.14 && localBlast.y < 1.25) {
                blastCenter = toWorld(v, "turret", new Vec3d(localBlast.x, 1.28, localBlast.z));
            }
        }
        return Optional.of(new SweptTankHit(v, bestGroup, bestT, contactFoot, blastCenter, bestInterior));
    }

    /** Exact physical boxes for a dropped payload interacting with an ArmoredVehicleEntity. */
    public static List<Box> payloadWorldBoxes(ArmoredVehicleEntity v) {
        return v.walkingBoxes();
    }

    /** Exact tank surfaces participate in the same nearest-hit selection as living occupants. */
    public static Optional<Vec3d> intersection(Entity target, Vec3d start, Vec3d end, double margin) {
        if (target instanceof ArmoredVehicleEntity v) return raycast(v, start, end).map(Surface::point);
        Box b = target.getBoundingBox().expand(margin);
        return b.contains(start) ? Optional.of(start) : b.raycast(start, end);
    }

    public static EntityHitResult raycastEntities(Entity shooter, Vec3d start, Vec3d end, Box search, Predicate<Entity> predicate, double maxDistance) {
        EntityHitResult best = null;
        double distance = maxDistance;
        for (var target : shooter.getWorld().getOtherEntities(shooter, search, predicate)) {
            var point = intersection(target, start, end, target.getTargetingMargin());
            if (point.isPresent() && start.squaredDistanceTo(point.get()) < distance) {
                distance = start.squaredDistanceTo(point.get());
                best = new EntityHitResult(target, point.get());
            }
        }
        return best;
    }

    /** Conservative 1D slices along rotated walls and roof edges leave both hatches and the cabin open with <= 126 world boxes. */
    public static List<WorldCollider> worldColliders(ArmoredVehicleEntity v) {
        List<WorldCollider> colliders = new ArrayList<>(126);
        record Frame(Vec3d origin, Vec3d x, Vec3d y, Vec3d z) { }
        Map<String, Frame> frames = new HashMap<>();
        boolean fastForward = v.forwardSpeed() > .06;
        for (var part : COLLISION) {
            if (fastForward && part.motion.equals("front_step")) continue;
            Box b = part.box;
            var frame = frames.computeIfAbsent(part.group, group -> {
                var origin = toWorld(v, group, Vec3d.ZERO);
                return new Frame(
                    origin,
                    toWorld(v, group, new Vec3d(1, 0, 0)).subtract(origin),
                    toWorld(v, group, new Vec3d(0, 1, 0)).subtract(origin),
                    toWorld(v, group, new Vec3d(0, 0, 1)).subtract(origin)
                );
            });
            double heading = part.group.equals("hull") ? v.getYaw() : v.turretYaw();
            boolean diagonal = Math.abs(Math.sin(Math.toRadians(heading * 2))) > 1e-5;
            int nx = 1, nz = 1;
            if (diagonal) {
                double dx = b.getXLength(), dy = b.getYLength(), dz = b.getZLength();
                if (part.group.equals("hatch") || part.group.equals("gunner_hatch")) nx = 4;
                else if (part.group.equals("turret")) {
                    if (dy >= .12 || b.minY >= 0) {
                        if (b.minY >= 1.10) { if (dx < .5) nz = 8; else nx = 4; }
                        else if (dy < .25) { if (dx > dz) nx = 2; else nz = 2; }
                        else { if (dx > dz) nx = 4; else nz = 4; }
                    }
                } else {
                    if (b.minY < 1.80 && !(b.maxY <= .70 || b.maxY <= .96 && Math.abs(b.minX) > 2.0) && !(dy < 1.50 && dz < .30)) {
                        if (dx > dz) nx = b.maxY > 1.70 ? 5 : 1;
                        else nz = dz < 6.5 ? 5 : 6;
                    }
                }
            }
            for (int x = 0; x < nx; x++) for (int z = 0; z < nz; z++) {
                double ax = MathHelper.lerp((double) x / nx, b.minX, b.maxX), bx = MathHelper.lerp((double) (x + 1) / nx, b.minX, b.maxX);
                double az = MathHelper.lerp((double) z / nz, b.minZ, b.maxZ), bz = MathHelper.lerp((double) (z + 1) / nz, b.minZ, b.maxZ);
                double cx = (ax + bx) / 2, cy = (b.minY + b.maxY) / 2, cz = (az + bz) / 2, hx = (bx - ax) / 2, hy = (b.maxY - b.minY) / 2, hz = (bz - az) / 2;
                Vec3d center = frame.origin.add(frame.x.multiply(cx)).add(frame.y.multiply(cy)).add(frame.z.multiply(cz));
                double dx = Math.abs(frame.x.x) * hx + Math.abs(frame.y.x) * hy + Math.abs(frame.z.x) * hz;
                double dy = Math.abs(frame.x.y) * hx + Math.abs(frame.y.y) * hy + Math.abs(frame.z.y) * hz;
                double dz = Math.abs(frame.x.z) * hx + Math.abs(frame.y.z) * hy + Math.abs(frame.z.z) * hz;
                colliders.add(new WorldCollider(part.group, new Box(center.x - dx, center.y - dy, center.z - dz, center.x + dx, center.y + dy, center.z + dz)));
            }
        }
        if (v.hatchOpen() && (Math.abs(v.suspension()) > 0.5f || Math.abs(v.bank()) > 0.5f)) {
            Vec3d hTop = toWorld(v, "turret", new Vec3d(0.68, 1.22, 0.16));
            Vec3d hBot = toWorld(v, "turret", new Vec3d(0.68, -0.88, 0.16));
            double cx = (hTop.x + hBot.x) * 0.5, cz = (hTop.z + hBot.z) * 0.5;
            double rx = Math.abs(hTop.x - hBot.x) * 0.5 + 0.36;
            double rz = Math.abs(hTop.z - hBot.z) * 0.5 + 0.36;
            Box chute = new Box(cx - rx, hBot.y + 0.05, cz - rz, cx + rx, hTop.y + 1.85, cz + rz);
            for (int i = 0; i < colliders.size(); i++) {
                WorldCollider wc = colliders.get(i);
                Box cb = wc.box;
                if (cb.maxY <= hBot.y + 0.12 || !cb.intersects(chute)) continue;
                double pushLeft = cb.maxX - chute.minX;
                double pushRight = chute.maxX - cb.minX;
                double pushNorth = cb.maxZ - chute.minZ;
                double pushSouth = chute.maxZ - cb.minZ;
                double minPush = Math.min(Math.min(pushLeft, pushRight), Math.min(pushNorth, pushSouth));
                Box adjusted = cb;
                if (minPush == pushLeft && cb.minX < chute.minX - 0.02) {
                    adjusted = new Box(cb.minX, cb.minY, cb.minZ, chute.minX, cb.maxY, cb.maxZ);
                } else if (minPush == pushRight && cb.maxX > chute.maxX + 0.02) {
                    adjusted = new Box(chute.maxX, cb.minY, cb.minZ, cb.maxX, cb.maxY, cb.maxZ);
                } else if (minPush == pushNorth && cb.minZ < chute.minZ - 0.02) {
                    adjusted = new Box(cb.minX, cb.minY, cb.minZ, cb.maxX, cb.maxY, chute.minZ);
                } else if (minPush == pushSouth && cb.maxZ > chute.maxZ + 0.02) {
                    adjusted = new Box(cb.minX, cb.minY, chute.maxZ, cb.maxX, cb.maxY, cb.maxZ);
                }
                colliders.set(i, new WorldCollider(wc.group, adjusted));
            }
        }
        return colliders;
    }

    public static List<Box> collisionBoxes(ArmoredVehicleEntity v) {
        var colliders = worldColliders(v);
        List<Box> boxes = new ArrayList<>(colliders.size());
        for (var c : colliders) boxes.add(c.box);
        return boxes;
    }

    /** Null keeps vanilla's fast path when no tank is present. Used by the common collision mixin. */
    public static List<VoxelShape> entityCollisions(EntityView world, Entity except, Box query) {
        if (except instanceof com.bobincraft.drone.DroneEntity drone
                && drone.isArmed() && drone.getDroneType() == com.bobincraft.drone.DroneType.FPV) {
            // Armed FPV drones use exact 3D local-space swept collision in DroneEntity.tick()
            // so diagonal dives into an open hatch are never blocked by Y-first axis stair-stepping.
            return null;
        }
        if (except instanceof com.bobincraft.drone.DronePayloadEntity) {
            // Dropped payloads resolve tank & rim collisions explicitly in DronePayloadMotion.move().
            return null;
        }
        var entities = world.getOtherEntities(except, query.expand(1e-7), e -> !e.isSpectator()
                && !(except instanceof com.bobincraft.drone.DronePayloadEntity && e instanceof LivingEntity)
                && (except == null ? e.isCollidable() : except.collidesWith(e)));
        boolean hasArmor = false;
        for (var e : entities) if (e instanceof ArmoredVehicleEntity) { hasArmor = true; break; }
        if (!hasArmor) return null;
        List<VoxelShape> result = new ArrayList<>();
        for (var e : entities) {
            if (e instanceof ArmoredVehicleEntity v) {
                var boxes = v.walkingBoxes();
                var shapes = v.walkingShapes();
                for (int i = 0, n = boxes.size(); i < n; i++) if (boxes.get(i).intersects(query)) result.add(shapes.get(i));
            } else result.add(VoxelShapes.cuboid(e.getBoundingBox()));
        }
        return result;
    }
}
