package com.bobincraft.armament.client.model.glb;

import java.io.IOException;

/** glTF column-major affine math. No axis flip: Blender's exporter already produces Y-up. */
public final class StaticTransforms {
    private StaticTransforms() {}
    public static double[] identity() { return new double[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}; }
    public static double[] multiply(double[] a, double[] b) {
        double[] result = new double[16];
        for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++)
            for (int k = 0; k < 4; k++) result[c*4+r] += a[k*4+r] * b[c*4+k];
        return result;
    }
    public static double determinant(double[] m) {
        return m[0]*(m[5]*m[10]-m[9]*m[6]) - m[4]*(m[1]*m[10]-m[9]*m[2]) + m[8]*(m[1]*m[6]-m[5]*m[2]);
    }
    public static void validate(double[] m) throws IOException {
        if (m == null || m.length != 16) throw new IOException("node: expected a 4x4 affine matrix");
        for (double x : m) if (!Double.isFinite(x) || Math.abs(x) > 1e6) throw new IOException("node: non-finite or excessive transform");
        if (Math.abs(m[3])+Math.abs(m[7])+Math.abs(m[11])+Math.abs(m[15]-1) > 1e-8)
            throw new IOException("node: projective matrices are not supported");
        if (Math.abs(determinant(m)) < 1e-12) throw new IOException("node: singular transform (zero scale)");
    }
    public static float[] position(double[] m, double x, double y, double z) throws IOException {
        return finite(m[0]*x+m[4]*y+m[8]*z+m[12], m[1]*x+m[5]*y+m[9]*z+m[13], m[2]*x+m[6]*y+m[10]*z+m[14]);
    }
    public static float[] normal(double[] m, double x, double y, double z) throws IOException {
        double d = determinant(m);
        double nx = ((m[5]*m[10]-m[9]*m[6])*x + (m[9]*m[2]-m[1]*m[10])*y + (m[1]*m[6]-m[5]*m[2])*z)/d;
        double ny = ((m[8]*m[6]-m[4]*m[10])*x + (m[0]*m[10]-m[8]*m[2])*y + (m[4]*m[2]-m[0]*m[6])*z)/d;
        double nz = ((m[4]*m[9]-m[8]*m[5])*x + (m[8]*m[1]-m[0]*m[9])*y + (m[0]*m[5]-m[4]*m[1])*z)/d;
        double length = Math.sqrt(nx*nx+ny*ny+nz*nz);
        if (!Double.isFinite(length) || length < 1e-12) throw new IOException("NORMAL: zero or invalid normal");
        return finite(nx/length,ny/length,nz/length);
    }
    private static float[] finite(double x, double y, double z) throws IOException {
        if (!Double.isFinite(x+y+z) || Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z))) > 1e6)
            throw new IOException("vertex: non-finite or excessive coordinate");
        return new float[]{(float)x,(float)y,(float)z};
    }
}
