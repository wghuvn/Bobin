package com.bobincraft.drone.client;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The same bank, pitch and yaw order as GameRenderer; screen coordinates use GUI pixels. */
public final class DroneHudProjection {
    private DroneHudProjection() { }
    public record Rectangle(int left, int top, int right, int bottom) { }

    public static float zoomedFov(float base, float zoom) {
        return (float) Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(base) / 2) / Math.max(1, zoom)));
    }

    public static Rectangle box(Box bounds, Vec3d camera, float yaw, float pitch, float bank,
                                float fov, int width, int height) {
        if (width <= 0 || height <= 0 || !Float.isFinite(fov) || fov <= 0 || fov >= 180) return null;
        Matrix4f view = new Matrix4f().rotateZ((float) Math.toRadians(bank))
                .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw + 180));
        double tangent = Math.tan(Math.toRadians(fov) / 2);
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (double x : new double[]{bounds.minX, bounds.maxX})
            for (double y : new double[]{bounds.minY, bounds.maxY})
                for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                    Vector3f p = view.transformPosition(new Vector3f((float) (x - camera.x),
                            (float) (y - camera.y), (float) (z - camera.z)));
                    if (p.z >= -.03f) return null;
                    double sx = width * .5 + p.x / (-p.z * tangent) * height * .5;
                    double sy = height * .5 - p.y / (-p.z * tangent) * height * .5;
                    if (!Double.isFinite(sx) || !Double.isFinite(sy)) return null;
                    minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                    minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
                }
        if (maxX < 2 || minX > width - 2 || maxY < 42 || minY > height - 24) return null;
        int left = (int) Math.max(2, Math.floor(minX)), right = (int) Math.min(width - 2, Math.ceil(maxX));
        int top = (int) Math.max(42, Math.floor(minY)), bottom = (int) Math.min(height - 24, Math.ceil(maxY));
        return right - left >= 3 && bottom - top >= 3 ? new Rectangle(left, top, right, bottom) : null;
    }
}
