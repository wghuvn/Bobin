package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.client.animation.gltf.*;

/** Rigid vanilla ModelPart adapter. Metres -> pixels; no limb scaling or elbows. */
public final class ThirdPersonPoseMath {
    private ThirdPersonPoseMath() {}
    public record Part(float x, float y, float z, float pitch, float yaw, float roll) {}
    public static double[] matrix(RigFrame frame, String node) {
        int n = frame.model().node(node); double[] m = new double[16];
        for (int i=0;i<16;i++) m[i]=frame.global(n,i);
        return m;
    }
    /** Conjugate glTF by diag(-1,-1,1), matching vanilla's entity/model axes. */
    public static Part toPart(double[] gltf) {
        double[] m = gltf.clone();
        for (int c=0;c<4;c++) for(int r=0;r<3;r++) m[c*4+r] *= (r<2?-1:1) * (c<2?-1:1);
        double yaw = Math.asin(Math.max(-1, Math.min(1, -m[2])));
        double pitch, roll;
        if (Math.abs(Math.cos(yaw))>1e-7) { pitch=Math.atan2(m[6],m[10]); roll=Math.atan2(m[1],m[0]); }
        else { pitch=Math.atan2(-m[9],m[5]); roll=0; }
        return new Part((float)(-gltf[12]*16),(float)(-gltf[13]*16),(float)(gltf[14]*16),(float)pitch,(float)yaw,(float)roll);
    }
    public static double[] point(double[] m, double x, double y, double z) {
        return new double[]{m[0]*x+m[4]*y+m[8]*z+m[12],m[1]*x+m[5]*y+m[9]*z+m[13],m[2]*x+m[6]*y+m[10]*z+m[14]};
    }
    public static double[] subtract(double[] a,double[] b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    public static double dot(double[] a,double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    public static double length(double[] a) { return Math.sqrt(dot(a,a)); }
    public static double[] position(double[] m) { return new double[]{m[12],m[13],m[14]}; }
    /** Closest correction keeping both rigid arm endpoints on their sockets through crossfades. */
    public static double[] gripCorrection(double[] right, double[] left, double[] primary, double[] support, double radius) {
        return gripCorrection(right,left,primary,support,radius,radius);
    }
    public static double[] gripCorrection(double[] right,double[] left,double[] primary,double[] support,double rightRadius,double leftRadius) {
        double[] a=subtract(right,primary), b=subtract(left,support), axis=subtract(b,a);
        double distance=length(axis);
        if(distance<1e-7 || distance>=rightRadius+leftRadius||distance<=Math.abs(rightRadius-leftRadius)) throw new IllegalArgumentException("unreachable rigid-arm attachments");
        for(int i=0;i<3;i++) axis[i]/=distance;
        double[] mid=new double[3],direction=new double[3];
        for(int i=0;i<3;i++) { mid[i]=a[i]+axis[i]*(rightRadius*rightRadius-leftRadius*leftRadius+distance*distance)/(2*distance); direction[i]=-mid[i]; }
        double projected=dot(direction,axis);
        for(int i=0;i<3;i++) direction[i]-=axis[i]*projected;
        double along=(rightRadius*rightRadius-leftRadius*leftRadius+distance*distance)/(2*distance);
        double norm=length(direction),circle=Math.sqrt(rightRadius*rightRadius-along*along);
        if(norm<1e-8) throw new IllegalArgumentException("ambiguous rigid-arm attachment circle");
        double[] correction=new double[3];
        for(int i=0;i<3;i++) correction[i]=mid[i]+direction[i]*circle/norm;
        return correction;
    }
    /** Rotate the single rigid cuboid as a whole; retain its independently authored roll. */
    public static double[] attach(double[] authored,double[] localEnd,double[] target) {
        double[] from=subtract(point(authored,localEnd[0],localEnd[1],localEnd[2]),position(authored));
        double[] to=subtract(target,position(authored));
        double fn=length(from),tn=length(to);
        for(int i=0;i<3;i++){from[i]/=fn;to[i]/=tn;}
        double[] axis={from[1]*to[2]-from[2]*to[1],from[2]*to[0]-from[0]*to[2],from[0]*to[1]-from[1]*to[0]};
        double w=1+dot(from,to);
        if(w<1e-8) throw new IllegalArgumentException("attachment would invert the arm");
        double norm=Math.sqrt(dot(axis,axis)+w*w);
        double[] trs={0,0,0,axis[0]/norm,axis[1]/norm,axis[2]/norm,w/norm,1,1,1};
        double[] rotate=new double[16],result=new double[16];
        RigMath.compose(trs,0,rotate,0);RigMath.multiply(rotate,0,authored,0,result,0);
        System.arraycopy(authored,12,result,12,3);
        return result;
    }
    /** Player-relative look, bounded independently of camera perspective. */
    public static double[] aim(double yawDegrees,double pitchDegrees) {
        double yaw=Math.toRadians(Math.max(-65,Math.min(65,yawDegrees)));
        double pitch=Math.toRadians(Math.max(-50,Math.min(50,pitchDegrees)));
        double[] trs={0,0,0,Math.sin(pitch/2)*Math.cos(yaw/2),Math.cos(pitch/2)*Math.sin(yaw/2),-Math.sin(pitch/2)*Math.sin(yaw/2),Math.cos(pitch/2)*Math.cos(yaw/2),1,1,1};
        double[] out=new double[16];RigMath.compose(trs,0,out,0);return out;
    }
    public static double[] multiply(double[] a,double[] b) { double[] out=new double[16];RigMath.multiply(a,0,b,0,out,0);return out; }
    public static double[] around(double[] pivot,double[] rotation){
        double[] m=rotation.clone();double[] rotated=point(rotation,pivot[0],pivot[1],pivot[2]);
        for(int i=0;i<3;i++)m[12+i]+=pivot[i]-rotated[i];return m;
    }
    public static void translate(double[] m,double[] offset){for(int i=0;i<3;i++)m[12+i]+=offset[i];}

}
