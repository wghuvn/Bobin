package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.client.animation.gltf.RigMath;

/** Camera-space math, column-major matrices. Never modifies a Minecraft camera or projection. */
public final class PresentationMath {
    private PresentationMath(){}
    public static void perspective(double verticalDegrees,double aspect,double near,double far,double[] out){
        if(!Double.isFinite(verticalDegrees)||verticalDegrees<=0||verticalDegrees>=179||!Double.isFinite(aspect)||aspect<=0||!Double.isFinite(near)||!Double.isFinite(far)||near<=0||far<=near||out.length!=16)throw new IllegalArgumentException("invalid perspective");
        java.util.Arrays.fill(out,0);double f=1/Math.tan(Math.toRadians(verticalDegrees)*0.5);
        out[0]=f/aspect;out[5]=f;out[10]=(far+near)/(near-far);out[11]=-1;out[14]=2*far*near/(near-far);
    }
    public static double nearPlane(double[] p){
        double near=p[14]/(p[10]-1);if(!Double.isFinite(near)||near<=0)throw new IllegalArgumentException("invalid projection near plane");return near;
    }
    public static void normalMatrix(double[] transform,double[] inverseScratch,double[] out){
        RigMath.inverseAffine(transform,0,inverseScratch,0);RigMath.identity(out,0);
        for(int c=0;c<3;c++)for(int r=0;r<3;r++)out[c*4+r]=inverseScratch[r*4+c];
    }
    /** Rigid alignment for attaching a child asset/socket to a parent's sampled target socket. */
    public static void attach(double[] parentTarget,double[] childSocket,double[] inverseScratch,double[] out){
        RigMath.inverseAffine(childSocket,0,inverseScratch,0);RigMath.multiply(parentTarget,0,inverseScratch,0,out,0);
    }
    public static double clamp(double value,double bound){return Math.max(-bound,Math.min(bound,value));}
    public static double wrapDegrees(double value){return value-360*Math.floor((value+180)/360);}
}
