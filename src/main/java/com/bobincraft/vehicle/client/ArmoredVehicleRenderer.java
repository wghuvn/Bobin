package com.bobincraft.vehicle.client;

import com.bobincraft.vehicle.ArmorControls;
import com.bobincraft.vehicle.ArmoredVehicleEntity;
import com.bobincraft.vehicle.ArmorGeometry;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.util.*;

/** Pre-batched Merkava Mk 4 Barak polygon mesh renderer: SoA vertex/normal tables per pass with pre-cached no-cull RenderLayers. */
public final class ArmoredVehicleRenderer extends EntityRenderer<ArmoredVehicleEntity> {
    public static boolean WHEEL_ROTATION_ENABLED = true;

    private static final int PASS_STATIC = 0;
    private static final int PASS_LOWER_TRACK = 1;
    private static final int PASS_UPPER_TRACK = 2;
    private static final int PASS_WHEEL = 3;
    private static final int PASS_BREECH = 4;

    private static final float[] TX = new float[4096], TY = new float[4096], TZ = new float[4096];
    private static final float[] TNX = new float[256], TNY = new float[256], TNZ = new float[256];

    private static final class MaterialSlice {
        final Identifier textureId;
        final short[] posIdx;
        final byte[] normIdx;
        final float[] u;
        final float[] v;
        RenderLayer layer;
        boolean layerResolved;

        MaterialSlice(String material, short[] posIdx, byte[] normIdx, float[] u, float[] v) {
            this.textureId = new Identifier("bobin_craft", "textures/block/armor_" + material + ".png");
            this.posIdx = posIdx;
            this.normIdx = normIdx;
            this.u = u;
            this.v = v;
        }

        RenderLayer layer() {
            if (!layerResolved) {
                layer = RenderLayer.getEntityCutoutNoCull(textureId);
                layerResolved = true;
            }
            return layer;
        }
    }

    private record MeshPass(
        int passType,
        float invRadius,
        float[] px, float[] py, float[] pz,
        float[] wdy, float[] wdz, float[] wcy, float[] wcz,
        float[] nx, float[] ny, float[] nz,
        MaterialSlice[] slices
    ) { }

    private static final Map<String, MeshPass[]> GROUP_PASSES = buildGroupPasses();
    private static final MeshPass[] HULL_PASSES = GROUP_PASSES.get("hull");
    private static final MeshPass[] TURRET_PASSES = GROUP_PASSES.get("turret");
    private static final MeshPass[] GUN_PASSES = GROUP_PASSES.get("gun");
    private static final MeshPass[] HATCH_PASSES = GROUP_PASSES.get("hatch");
    private static final MeshPass[] GUNNER_HATCH_PASSES = GROUP_PASSES.get("gunner_hatch");

    private record PosKey(int partId, int x, int y, int z) { }
    private record NormKey(int nx, int ny, int nz) { }

    private static int q(float v) { return Math.round(v * 100000f); }

    /** Pre-resolves and caches RenderLayer instances for all material slices once registries are bootstrapped. */
    public static void preCacheLayers() {
        for (var passes : GROUP_PASSES.values()) {
            for (var pass : passes) {
                for (var s : pass.slices) s.layer();
            }
        }
    }

    private static Map<String, MeshPass[]> buildGroupPasses() {
        Map<String, List<ArmorGeometry.Part>> byGroup = new LinkedHashMap<>();
        for (var p : ArmorGeometry.PARTS) {
            byGroup.computeIfAbsent(p.group(), g -> new ArrayList<>()).add(p);
        }
        Map<String, MeshPass[]> result = new HashMap<>();
        for (var gEntry : byGroup.entrySet()) {
            List<ArmorGeometry.Part> parts = gEntry.getValue();
            List<ArmorGeometry.Part> statics = new ArrayList<>();
            List<ArmorGeometry.Part> lowerTracks = new ArrayList<>();
            List<ArmorGeometry.Part> upperTracks = new ArrayList<>();
            List<ArmorGeometry.Part> breeches = new ArrayList<>();
            Map<Integer, List<ArmorGeometry.Part>> wheelsByR = new LinkedHashMap<>();

            for (var p : parts) {
                if (p.faces().isEmpty()) continue;
                if (p.motion().isEmpty()) statics.add(p);
                else if (p.track()) {
                    if (p.box().minY < .3) lowerTracks.add(p);
                    else upperTracks.add(p);
                } else if (p.wheel()) {
                    float r = p.pivot() != null && p.pivot().length >= 4 && p.pivot()[3] > 1e-4f ? p.pivot()[3] : 0.39f;
                    wheelsByR.computeIfAbsent(Math.round(r * 1000f), k -> new ArrayList<>()).add(p);
                } else {
                    breeches.add(p);
                }
            }

            List<MeshPass> passes = new ArrayList<>();
            if (!statics.isEmpty()) passes.add(compilePass(PASS_STATIC, 0f, statics));
            if (!lowerTracks.isEmpty()) passes.add(compilePass(PASS_LOWER_TRACK, 0f, lowerTracks));
            if (!upperTracks.isEmpty()) passes.add(compilePass(PASS_UPPER_TRACK, 0f, upperTracks));
            for (var wEntry : wheelsByR.entrySet()) {
                passes.add(compilePass(PASS_WHEEL, 1000f / wEntry.getKey(), wEntry.getValue()));
            }
            if (!breeches.isEmpty()) passes.add(compilePass(PASS_BREECH, 0f, breeches));
            result.put(gEntry.getKey(), passes.toArray(MeshPass[]::new));
        }
        return Map.copyOf(result);
    }

    private static MeshPass compilePass(int passType, float invRadius, List<ArmorGeometry.Part> parts) {
        List<Float> pxList = new ArrayList<>(), pyList = new ArrayList<>(), pzList = new ArrayList<>();
        List<Float> wdyList = new ArrayList<>(), wdzList = new ArrayList<>(), wcyList = new ArrayList<>(), wczList = new ArrayList<>();
        List<Float> nxList = new ArrayList<>(), nyList = new ArrayList<>(), nzList = new ArrayList<>();
        Map<PosKey, Integer> posMap = new LinkedHashMap<>();
        Map<NormKey, Integer> normMap = new LinkedHashMap<>();

        Map<String, List<Short>> matPosIdx = new LinkedHashMap<>();
        Map<String, List<Byte>> matNormIdx = new LinkedHashMap<>();
        Map<String, List<Float>> matU = new LinkedHashMap<>();
        Map<String, List<Float>> matV = new LinkedHashMap<>();

        int partSeq = 0;
        for (var p : parts) {
            partSeq++;
            int keyPart = passType == PASS_WHEEL ? partSeq : 0;
            float cy = 0f, cz = 0f;
            if (passType == PASS_WHEEL) {
                cy = p.pivot() != null && p.pivot().length >= 4 ? p.pivot()[1] : (float) ((p.box().minY + p.box().maxY) * 0.5);
                cz = p.pivot() != null && p.pivot().length >= 4 ? p.pivot()[2] : (float) ((p.box().minZ + p.box().maxZ) * 0.5);
            }
            var pIdxList = matPosIdx.computeIfAbsent(p.material(), k -> new ArrayList<>());
            var nIdxList = matNormIdx.computeIfAbsent(p.material(), k -> new ArrayList<>());
            var uList = matU.computeIfAbsent(p.material(), k -> new ArrayList<>());
            var vList = matV.computeIfAbsent(p.material(), k -> new ArrayList<>());

            for (var f : p.faces()) {
                float fnx = f.n()[0], fny = f.n()[1], fnz = f.n()[2];
                NormKey nk = new NormKey(q(fnx), q(fny), q(fnz));
                Integer ni = normMap.get(nk);
                if (ni == null) {
                    ni = normMap.size();
                    normMap.put(nk, ni);
                    nxList.add(fnx); nyList.add(fny); nzList.add(fnz);
                }
                int nVerts = f.v().length;
                for (int i = 0; i < 4; i++) {
                    int vi = Math.min(i, nVerts - 1);
                    float vx = f.v()[vi][0], vy = f.v()[vi][1], vz = f.v()[vi][2];
                    PosKey pk = new PosKey(keyPart, q(vx), q(vy), q(vz));
                    Integer pi = posMap.get(pk);
                    if (pi == null) {
                        pi = posMap.size();
                        posMap.put(pk, pi);
                        pxList.add(vx); pyList.add(vy); pzList.add(vz);
                        if (passType == PASS_WHEEL) {
                            wdyList.add(vy - cy); wdzList.add(vz - cz);
                            wcyList.add(cy); wczList.add(cz);
                        }
                    }
                    pIdxList.add((short) (int) pi);
                    nIdxList.add((byte) (int) ni);
                    uList.add(f.uv()[vi][0]);
                    vList.add(f.uv()[vi][1]);
                }
            }
        }

        List<MaterialSlice> slices = new ArrayList<>();
        for (var mat : matPosIdx.keySet()) {
            var sp = matPosIdx.get(mat);
            var sn = matNormIdx.get(mat);
            var su = matU.get(mat);
            var sv = matV.get(mat);
            slices.add(new MaterialSlice(mat, toShortArray(sp), toByteArray(sn), toFloatArray(su), toFloatArray(sv)));
        }

        return new MeshPass(
            passType, invRadius,
            toFloatArray(pxList), toFloatArray(pyList), toFloatArray(pzList),
            toFloatArray(wdyList), toFloatArray(wdzList), toFloatArray(wcyList), toFloatArray(wczList),
            toFloatArray(nxList), toFloatArray(nyList), toFloatArray(nzList),
            slices.toArray(MaterialSlice[]::new)
        );
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    private static short[] toShortArray(List<Short> list) {
        short[] a = new short[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    private static byte[] toByteArray(List<Byte> list) {
        byte[] a = new byte[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    public ArmoredVehicleRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 2.2f;
        preCacheLayers();
    }

    @Override
    public Identifier getTexture(ArmoredVehicleEntity e) {
        return new Identifier("bobin_craft", "textures/block/armor_tan.png");
    }

    @Override
    public void render(ArmoredVehicleEntity e, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider out, int light) {
        if ((ArmorClient.optics() || ArmorClient.periscope()) && ArmorClient.station() == e) return;
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - MathHelper.lerpAngleDegrees(delta, e.prevYaw, e.getYaw())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(delta, e.previousSuspension, e.suspension())));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-MathHelper.lerp(delta, e.previousBank, e.bank())));
        drawHull(e, delta, matrices, out, light);

        matrices.translate(ArmorGeometry.TURRET_PIVOT.x, ArmorGeometry.TURRET_PIVOT.y, ArmorGeometry.TURRET_PIVOT.z);
        float gun = MathHelper.lerpAngleDegrees(delta, e.previousTurret, e.turretYaw());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerpAngleDegrees(delta, e.prevYaw, e.getYaw()) - gun));
        draw("turret", e, matrices, out, light);

        for (var hatch : ArmorGeometry.HATCHES) {
            matrices.push();
            var h = hatch.hinge();
            matrices.translate(h.x, h.y, h.z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(delta, e.previousHatch, e.hatchAngle()) * hatch.sign()));
            matrices.translate(-h.x, -h.y, -h.z);
            draw(hatch.group(), e, matrices, out, light);
            matrices.pop();
        }

        matrices.translate(ArmorGeometry.GUN_PIVOT.x, ArmorGeometry.GUN_PIVOT.y, ArmorGeometry.GUN_PIVOT.z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-MathHelper.lerp(delta, e.previousElevation, e.elevation())));
        int rt = e.reloadTicks();
        if (rt > ArmorControls.RELOAD_TICKS - 18) {
            float shotAge = (ArmorControls.RELOAD_TICKS - rt) + delta;
            float recoil = shotAge < 3.5f ? (shotAge / 3.5f) * 0.36f : Math.max(0f, (1f - (shotAge - 3.5f) / 14.5f) * 0.36f);
            matrices.translate(0, 0, recoil);
        }
        draw("gun", e, matrices, out, light);
        matrices.pop();
        super.render(e, yaw, delta, matrices, out, light);
    }

    private static void transformPosSoA(Matrix4f p, float[] px, float[] py, float[] pz, float dz) {
        float m00 = p.m00(), m01 = p.m01(), m02 = p.m02(), m10 = p.m10(), m11 = p.m11(), m12 = p.m12(), m20 = p.m20(), m21 = p.m21(), m22 = p.m22();
        float m30 = p.m30() + m20 * dz, m31 = p.m31() + m21 * dz, m32 = p.m32() + m22 * dz;
        for (int i = 0, len = px.length; i < len; i++) {
            float x = px[i], y = py[i], z = pz[i];
            TX[i] = m00 * x + m10 * y + m20 * z + m30;
            TY[i] = m01 * x + m11 * y + m21 * z + m31;
            TZ[i] = m02 * x + m12 * y + m22 * z + m32;
        }
    }

    private static void transformWheelPosSoA(Matrix4f p, float[] px, float[] wdy, float[] wdz, float[] wcy, float[] wcz, float cosA, float sinA) {
        float m00 = p.m00(), m01 = p.m01(), m02 = p.m02(), m10 = p.m10(), m11 = p.m11(), m12 = p.m12(), m20 = p.m20(), m21 = p.m21(), m22 = p.m22(), m30 = p.m30(), m31 = p.m31(), m32 = p.m32();
        for (int i = 0, len = px.length; i < len; i++) {
            float x = px[i], dy = wdy[i], dz = wdz[i];
            float ry = wcy[i] + dy * cosA - dz * sinA;
            float rz = wcz[i] + dy * sinA + dz * cosA;
            TX[i] = m00 * x + m10 * ry + m20 * rz + m30;
            TY[i] = m01 * x + m11 * ry + m21 * rz + m31;
            TZ[i] = m02 * x + m12 * ry + m22 * rz + m32;
        }
    }

    private static void transformNormsSoA(Matrix3f n, float[] nxArr, float[] nyArr, float[] nzArr) {
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02(), n10 = n.m10(), n11 = n.m11(), n12 = n.m12(), n20 = n.m20(), n21 = n.m21(), n22 = n.m22();
        for (int i = 0, len = nxArr.length; i < len; i++) {
            float nx = nxArr[i], ny = nyArr[i], nz = nzArr[i];
            TNX[i] = n00 * nx + n10 * ny + n20 * nz;
            TNY[i] = n01 * nx + n11 * ny + n21 * nz;
            TNZ[i] = n02 * nx + n12 * ny + n22 * nz;
        }
    }

    private static void transformWheelNormsSoA(Matrix3f n, float[] nxArr, float[] nyArr, float[] nzArr, float cosA, float sinA) {
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02(), n10 = n.m10(), n11 = n.m11(), n12 = n.m12(), n20 = n.m20(), n21 = n.m21(), n22 = n.m22();
        for (int i = 0, len = nxArr.length; i < len; i++) {
            float nx = nxArr[i], ny = nyArr[i], nz = nzArr[i];
            float rny = ny * cosA - nz * sinA;
            float rnz = ny * sinA + nz * cosA;
            TNX[i] = n00 * nx + n10 * rny + n20 * rnz;
            TNY[i] = n01 * nx + n11 * rny + n21 * rnz;
            TNZ[i] = n02 * nx + n12 * rny + n22 * rnz;
        }
    }

    private static void emitAll(VertexConsumer vc, short[] posIdx, byte[] normIdx, float[] u, float[] v, int light) {
        int ov = OverlayTexture.DEFAULT_UV;
        for (int i = 0, len = posIdx.length; i < len; i++) {
            int pi = posIdx[i] & 4095;
            int ni = normIdx[i] & 255;
            vc.vertex(TX[pi], TY[pi], TZ[pi], 1f, 1f, 1f, 1f, u[i], v[i], ov, light, TNX[ni], TNY[ni], TNZ[ni]);
        }
    }

    /** Emit each material slice to its own RenderLayer without probing slices[1] first (which would flush Immediate's shared fallbackBuffer). */
    private static void emitPass(MeshPass pass, VertexConsumerProvider out, int light) {
        MaterialSlice[] slices = pass.slices;
        for (int i = 0, ns = slices.length; i < ns; i++) {
            MaterialSlice s = slices[i];
            emitAll(out.getBuffer(s.layer()), s.posIdx, s.normIdx, s.u, s.v, light);
        }
    }

    static void drawHull(ArmoredVehicleEntity e, float delta, MatrixStack m, VertexConsumerProvider out, int light) {
        MeshPass[] passes = HULL_PASSES;
        if (passes == null) return;
        double travel = MathHelper.lerp(delta, e.previousTread, e.treadDistance);
        float phase = (float) (travel % .22);
        MatrixStack.Entry entry = m.peek();
        Matrix4f p = entry.getPositionMatrix();
        Matrix3f n = entry.getNormalMatrix();
        boolean rotWheels = WHEEL_ROTATION_ENABLED;
        for (int i = 0, len = passes.length; i < len; i++) {
            MeshPass pass = passes[i];
            int type = pass.passType;
            if (type == PASS_STATIC) {
                transformPosSoA(p, pass.px, pass.py, pass.pz, 0f);
                transformNormsSoA(n, pass.nx, pass.ny, pass.nz);
            } else if (type == PASS_LOWER_TRACK) {
                transformPosSoA(p, pass.px, pass.py, pass.pz, phase);
                transformNormsSoA(n, pass.nx, pass.ny, pass.nz);
            } else if (type == PASS_UPPER_TRACK) {
                transformPosSoA(p, pass.px, pass.py, pass.pz, -phase);
                transformNormsSoA(n, pass.nx, pass.ny, pass.nz);
            } else if (type == PASS_WHEEL) {
                if (rotWheels) {
                    double ang = -travel * pass.invRadius;
                    float cosA = (float) Math.cos(ang), sinA = (float) Math.sin(ang);
                    transformWheelPosSoA(p, pass.px, pass.wdy, pass.wdz, pass.wcy, pass.wcz, cosA, sinA);
                    transformWheelNormsSoA(n, pass.nx, pass.ny, pass.nz, cosA, sinA);
                } else {
                    transformPosSoA(p, pass.px, pass.py, pass.pz, 0f);
                    transformNormsSoA(n, pass.nx, pass.ny, pass.nz);
                }
            }
            emitPass(pass, out, light);
        }
    }

    static void draw(String group, ArmoredVehicleEntity e, MatrixStack matrices, VertexConsumerProvider out, int light) {
        MeshPass[] passes = switch (group) {
            case "turret" -> TURRET_PASSES;
            case "gun" -> GUN_PASSES;
            case "hatch" -> HATCH_PASSES;
            case "gunner_hatch" -> GUNNER_HATCH_PASSES;
            default -> GROUP_PASSES.get(group);
        };
        if (passes == null) return;
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f p = entry.getPositionMatrix();
        Matrix3f n = entry.getNormalMatrix();
        for (int i = 0, len = passes.length; i < len; i++) {
            MeshPass pass = passes[i];
            if (pass.passType == PASS_BREECH) {
                int rt = e != null ? e.reloadTicks() : 0;
                float breechShift = rt > 0 ? (float) (Math.sin(Math.PI * rt / ArmorControls.RELOAD_TICKS) * .22) : 0f;
                transformPosSoA(p, pass.px, pass.py, pass.pz, breechShift);
            } else {
                transformPosSoA(p, pass.px, pass.py, pass.pz, 0f);
            }
            transformNormsSoA(n, pass.nx, pass.ny, pass.nz);
            emitPass(pass, out, light);
        }
    }

    private static void box(VertexConsumer out, MatrixStack.Entry m, double[] b, int light) {
        double x = b[0], y = b[1], z = b[2], X = b[3], Y = b[4], Z = b[5];
        quad(out, m, new double[][]{{x, y, z}, {x, Y, z}, {X, Y, z}, {X, y, z}}, 0, 0, -1, light);
        quad(out, m, new double[][]{{X, y, Z}, {X, Y, Z}, {x, Y, Z}, {x, y, Z}}, 0, 0, 1, light);
        quad(out, m, new double[][]{{x, y, Z}, {x, Y, Z}, {x, Y, z}, {x, y, z}}, -1, 0, 0, light);
        quad(out, m, new double[][]{{X, y, z}, {X, Y, z}, {X, Y, Z}, {X, y, Z}}, 1, 0, 0, light);
        quad(out, m, new double[][]{{x, Y, z}, {x, Y, Z}, {X, Y, Z}, {X, Y, z}}, 0, 1, 0, light);
        quad(out, m, new double[][]{{x, y, Z}, {x, y, z}, {X, y, z}, {X, y, Z}}, 0, -1, 0, light);
    }

    private static void quad(VertexConsumer out, MatrixStack.Entry m, double[][] points, float nx, float ny, float nz, int light) {
        for (int i = 0; i < 4; i++) {
            var p = points[i];
            float u = (float) (nx == 0 ? .5 + p[0] / 7 : .6 + p[2] / 12), v = (float) (ny == 0 ? .92 - p[1] / 4 : .6 + p[2] / 12);
            out.vertex(m.getPositionMatrix(), (float) p[0], (float) p[1], (float) p[2]).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.getNormalMatrix(), nx, ny, nz).next();
        }
    }
}
