package com.bobincraft.armament.client.thirdperson;

import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;

/** Resolve the torso before shoulders, neck and weapon are evaluated against it. */
public final class ThirdPersonTorsoOrientation {
    public static final double MAX_YAW=Math.toRadians(8),AUTHORED_WEIGHT=1;
    private ThirdPersonTorsoOrientation() {}
    public record Result(double[] matrix,double waistInset) {}
    public static double yaw(double authored){
        if(!Double.isFinite(authored))throw new IllegalArgumentException("non-finite torso yaw");
        return MAX_YAW*Math.tanh(authored/MAX_YAW);
    }
    public static Result resolve(double[] original,double[] pelvis){
        Part p=toPart(original);double pitch=p.pitch(),roll=p.roll(),turn=yaw(p.yaw());
        double cx=Math.cos(pitch),sx=Math.sin(pitch),cy=Math.cos(turn),sy=Math.sin(turn),cz=Math.cos(roll),sz=Math.sin(roll);
        double[] m={cz*cy,sz*cy,-sy,0,cz*sy*sx-sz*cx,sz*sy*sx+cz*cx,cy*sx,0,cz*sy*cx+sz*sx,sz*sy*cx-cz*sx,cy*cx,0,0,0,0,1};
        for(int c=0;c<3;c++)for(int r=0;r<3;r++)m[c*4+r]*=(r<2?-1:1)*(c<2?-1:1);
        double inset=Math.abs(m[1])*.25+Math.abs(m[9])*.125+1.0/64;
        double[] hip=point(m,0,-.75,0);
        m[12]=pelvis[0]-hip[0];m[13]=pelvis[1]-inset-hip[1];m[14]=pelvis[2]-hip[2];
        return new Result(m,inset);
    }
}
