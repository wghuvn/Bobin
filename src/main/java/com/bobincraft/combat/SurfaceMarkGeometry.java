package com.bobincraft.combat;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Face-clipped quads with outward winding. Rotation affects the pixel texture, not the supporting face. */
public final class SurfaceMarkGeometry {
    private SurfaceMarkGeometry() { }
    public record Vertex(Vec3d point, float u, float v) { }
    public static Vertex[] quad(SurfaceMark m) {
        Direction.Axis normal = m.face().getAxis();
        double u = normal == Direction.Axis.X ? m.local().z : m.local().x;
        double v = normal == Direction.Axis.Y ? m.local().z : m.local().y;
        double u0 = Math.max(m.minU(), u - m.radius()), u1 = Math.min(m.maxU(), u + m.radius());
        double v0 = Math.max(m.minV(), v - m.radius()), v1 = Math.min(m.maxV(), v + m.radius());
        if (u0 >= u1 || v0 >= v1) return new Vertex[0];
        boolean reverse = m.face() == Direction.UP || m.face() == Direction.NORTH || m.face() == Direction.EAST;
        double[][] points = reverse ? new double[][]{{u0,v0},{u0,v1},{u1,v1},{u1,v0}}
                : new double[][]{{u0,v0},{u1,v0},{u1,v1},{u0,v1}};
        Vec3d offset = Vec3d.of(m.block()).add(Vec3d.of(m.face().getVector()).multiply(.002));
        Vertex[] result = new Vertex[4];
        for (int i = 0; i < 4; i++) {
            double pu = points[i][0], pv = points[i][1];
            Vec3d local = switch (normal) {
                case X -> new Vec3d(m.local().x, pv, pu);
                case Y -> new Vec3d(pu, m.local().y, pv);
                case Z -> new Vec3d(pu, pv, m.local().z);
            };
            float tu = (float) ((pu - u + m.radius()) / (m.radius() * 2));
            float tv = (float) ((pv - v + m.radius()) / (m.radius() * 2));
            for (int r = 0; r < m.turn(); r++) { float oldU = tu; tu = 1 - tv; tv = oldU; }
            result[i] = new Vertex(local.add(offset), tu, tv);
        }
        return result;
    }
}
