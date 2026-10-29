package com.bobincraft.destruction.debris;

import java.util.*;
import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Oriented source VoxelShape sub-box. Bounds are broad phase only; SAT supplies actual contacts. */
public final class DebrisShape {
    private static final double EPS=1e-7;
    public final B local,bounds;
    public final Q rotation;
    public final V position,center;
    public final V[] axes,vertices;
    private final double[] half;
    public DebrisShape(B local,Q rotation,V position) {
        this.local=local;this.rotation=rotation;this.position=position;
        center=rotation.rotate(new V((local.x0()+local.x1())/2,(local.y0()+local.y1())/2,(local.z0()+local.z1())/2)).add(position);
        axes=new V[]{rotation.rotate(new V(1,0,0)),rotation.rotate(new V(0,1,0)),rotation.rotate(new V(0,0,1))};
        half=new double[]{(local.x1()-local.x0())/2,(local.y1()-local.y0())/2,(local.z1()-local.z0())/2};
        vertices=new V[8];for(int i=0;i<8;i++)vertices[i]=rotation.rotate(new V((i&1)==0?local.x0():local.x1(),(i&2)==0?local.y0():local.y1(),(i&4)==0?local.z0():local.z1())).add(position);
        bounds=local.transformed(rotation,position);
    }
    public static double dot(V a,V b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    public static V cross(V a,V b){return new V(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
    private double radius(V axis){double r=0;for(int i=0;i<3;i++)r+=half[i]*Math.abs(dot(axes[i],axis));return r;}
    private List<V> separatingAxes(DebrisShape b) {
        var out=new ArrayList<V>(15);Collections.addAll(out,axes);Collections.addAll(out,b.axes);
        for(var a:axes)for(var c:b.axes){V n=cross(a,c);double length=n.length();if(length>1e-8)out.add(n.mul(1/length));}return out;
    }
    /** Null means separated/touching. Otherwise the shortest collision-driven separation vector. */
    public V penetration(DebrisShape b) {
        if(!bounds.intersects(b.bounds))return null;
        double depth=Double.POSITIVE_INFINITY;V normal=V.ZERO,delta=center.add(b.center.mul(-1));
        for(V axis:separatingAxes(b)) {
            double d=dot(delta,axis),overlap=radius(axis)+b.radius(axis)-Math.abs(d);
            if(overlap<=EPS)return null;
            if(overlap<depth){depth=overlap;normal=axis.mul(d<0?-1:1);}
        }
        return normal.mul(depth+EPS);
    }
    public record Sweep(double time,V normal) {}
    /** Continuous translation of an oriented box against another oriented box, with fixed orientations. */
    public Sweep sweep(DebrisShape b,V motion) {
        double enter=0,exit=1;V normal=V.ZERO,delta=center.add(b.center.mul(-1));
        for(V axis:separatingAxes(b)) {
            double d=dot(delta,axis),r=radius(axis)+b.radius(axis),speed=dot(motion,axis);
            if(Math.abs(speed)<1e-12){if(Math.abs(d)>=r-EPS)return null;continue;}
            double t0=(-r-d)/speed,t1=(r-d)/speed;
            V n=axis.mul(speed>0?-1:1);if(t0>t1){double t=t0;t0=t1;t1=t;}
            if(t0>=enter-EPS){enter=Math.max(enter,t0);normal=n;}
            exit=Math.min(exit,t1);if(enter>exit+EPS)return null;
        }
        if(exit<=EPS||enter>1||normal.length()<.5)return null;
        return new Sweep(Math.max(0,enter),normal);
    }
    public DebrisShape offset(V amount){return new DebrisShape(local,rotation,position.add(amount));}
    private boolean contains(V point,double tolerance) {
        V d=point.add(center.mul(-1));for(int i=0;i<3;i++)if(Math.abs(dot(d,axes[i]))>half[i]+tolerance)return false;return true;
    }
    /** Vertices of the true contact/intersection polytope, including edge/face intersections. */
    public List<V> contactPoints(DebrisShape b) {
        var out=new ArrayList<V>();for(var p:vertices)if(b.contains(p,1e-5))out.add(p);for(var p:b.vertices)if(contains(p,1e-5))out.add(p);
        edgesAgainst(this,b,out);edgesAgainst(b,this,out);return out;
    }
    private static void edgesAgainst(DebrisShape a,DebrisShape b,List<V> out) {
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0) {
            V start=a.vertices[i],direction=a.vertices[i|bit].add(start.mul(-1)),relative=start.add(b.center.mul(-1));
            for(int k=0;k<3;k++)for(int sign:new int[]{-1,1}) {
                double speed=dot(direction,b.axes[k]);if(Math.abs(speed)<1e-12)continue;
                double t=(sign*b.half[k]-dot(relative,b.axes[k]))/speed;
                if(t>=-1e-7&&t<=1+1e-7){V point=start.add(direction.mul(Math.max(0,Math.min(1,t))));if(b.contains(point,1e-5))out.add(point);}
            }
        }
    }
}
