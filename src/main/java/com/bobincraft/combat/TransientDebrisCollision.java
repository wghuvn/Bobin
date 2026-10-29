package com.bobincraft.combat;

import java.util.List;

/** Small cosmetic fragments only. Axis clipping uses the entire conservative rotating volume. */
public final class TransientDebrisCollision {
    public record Bounds(double x0,double y0,double z0,double x1,double y1,double z1) {
        public Bounds offset(double x,double y,double z){return new Bounds(x0+x,y0+y,z0+z,x1+x,y1+y,z1+z);}
        public boolean overlaps(Bounds b){return x1>b.x0&&x0<b.x1&&y1>b.y0&&y0<b.y1&&z1>b.z0&&z0<b.z1;}
    }
    public record Motion(double x,double y,double z,boolean supported) {}
    private TransientDebrisCollision(){}
    public static Motion move(Bounds box,double x,double y,double z,List<Bounds> obstacles) {
        double wantedY=y;
        for(var b:obstacles)if(box.x1>b.x0&&box.x0<b.x1&&box.z1>b.z0&&box.z0<b.z1) {
            if(y>0&&box.y1<=b.y0)y=Math.min(y,b.y0-box.y1);
            if(y<0&&box.y0>=b.y1)y=Math.max(y,b.y1-box.y0);
        }
        box=box.offset(0,y,0);
        for(var b:obstacles)if(box.y1>b.y0&&box.y0<b.y1&&box.z1>b.z0&&box.z0<b.z1) {
            if(x>0&&box.x1<=b.x0)x=Math.min(x,b.x0-box.x1);
            if(x<0&&box.x0>=b.x1)x=Math.max(x,b.x1-box.x0);
        }
        box=box.offset(x,0,0);
        for(var b:obstacles)if(box.x1>b.x0&&box.x0<b.x1&&box.y1>b.y0&&box.y0<b.y1) {
            if(z>0&&box.z1<=b.z0)z=Math.min(z,b.z0-box.z1);
            if(z<0&&box.z0>=b.z1)z=Math.max(z,b.z1-box.z0);
        }
        return new Motion(x,y,z,wantedY<0&&y>wantedY+1e-8);
    }
}
