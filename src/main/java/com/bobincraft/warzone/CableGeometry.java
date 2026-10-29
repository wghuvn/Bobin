package com.bobincraft.warzone;

import java.util.ArrayList;
import java.util.List;

/** Continuous, independent conductors. No voxel adjacency is used to connect phases. */
public final class CableGeometry {
    public record Vertex(double x,double y,double z) { }
    public record Segment(Vertex a,Vertex b) { }
    private CableGeometry() { }
    public static Vertex point(int ax0, int ay0, int az0, boolean aAlongX, int aHeight,
                               int bx0, int by0, int bz0, boolean bAlongX, int bHeight,
                               int phase, double f) {
        double ax=ax0+.5+(aAlongX?0:phase),az=az0+.5+(aAlongX?phase:0);
        double bx=bx0+.5+(bAlongX?0:phase),bz=bz0+.5+(bAlongX?phase:0);
        return new Vertex(ax+(bx-ax)*f,(ay0+aHeight-.05)*(1-f)+(by0+bHeight-.05)*f-8.8*f*(1-f),az+(bz-az)*f);
    }
    public static Vertex point(PowerGrid.Span span,int phase,double f) {
        var a=span.a();var b=span.b();
        return point(a.x(), a.y(), a.z(), a.alongX(), a.height(), b.x(), b.y(), b.z(), b.alongX(), b.height(), phase, f);
    }
    public static List<Segment> conductor(int ax0, int ay0, int az0, boolean aAlongX, int aHeight,
                                          int bx0, int by0, int bz0, boolean bAlongX, int bHeight,
                                          int phase) {
        double length=Math.hypot(bx0-ax0,bz0-az0);
        int steps=Math.max(2,(int)Math.ceil(length*2));List<Segment> result=new ArrayList<>();
        Vertex a=point(ax0, ay0, az0, aAlongX, aHeight, bx0, by0, bz0, bAlongX, bHeight, phase, 0);
        for(int i=1;i<=steps;i++){
            Vertex b=point(ax0, ay0, az0, aAlongX, aHeight, bx0, by0, bz0, bAlongX, bHeight, phase, i/(double)steps);
            result.add(new Segment(a,b));a=b;
        }
        return List.copyOf(result);
    }
    public static List<Segment> conductor(PowerGrid.Span span,int phase) {
        var a=span.a();var b=span.b();
        return conductor(a.x(), a.y(), a.z(), a.alongX(), a.height(), b.x(), b.y(), b.z(), b.alongX(), b.height(), phase);
    }
}
