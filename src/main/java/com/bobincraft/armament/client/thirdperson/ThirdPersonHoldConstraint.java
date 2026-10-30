package com.bobincraft.armament.client.thirdperson;

import java.util.Optional;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;

/** Closest point on the two rigid-arm reach circle, clipped by anatomical
 * half-spaces. Moves the complete hold; never moves a shoulder or scales an arm. */
public final class ThirdPersonHoldConstraint {
    private ThirdPersonHoldConstraint(){}
    public static Optional<double[]> correction(double[] right,double[] left,double[] primary,double[] support,
            double rightRadius,double leftRadius,double handLimit,double[] frontNormal,double frontLimit){
        double[] a=subtract(right,primary),b=subtract(left,support),axis=subtract(b,a);double distance=length(axis);
        if(distance<1e-8||distance>=rightRadius+leftRadius||distance<=Math.abs(rightRadius-leftRadius))return Optional.empty();
        for(int i=0;i<3;i++)axis[i]/=distance;
        double along=(rightRadius*rightRadius-leftRadius*leftRadius+distance*distance)/(2*distance);
        double radius=Math.sqrt(rightRadius*rightRadius-along*along);double[] mid=new double[3],u=new double[3];
        for(int i=0;i<3;i++){mid[i]=a[i]+axis[i]*along;u[i]=-mid[i];}
        double projected=dot(u,axis);for(int i=0;i<3;i++)u[i]-=axis[i]*projected;
        double norm=length(u);if(norm<1e-8)return Optional.empty();for(int i=0;i<3;i++)u[i]/=norm;
        double[] v={axis[1]*u[2]-axis[2]*u[1],axis[2]*u[0]-axis[0]*u[2],axis[0]*u[1]-axis[1]*u[0]};
        double[][] normals={{0,1,0},frontNormal};double[] limits={handLimit,frontLimit},candidates=new double[5];int count=1;
        for(int k=0;k<normals.length;k++){
            double ac=radius*dot(normals[k],u),bc=radius*dot(normals[k],v),amplitude=Math.hypot(ac,bc),q=limits[k]-dot(normals[k],mid);
            if(q< -amplitude-1e-10)return Optional.empty();
            if(amplitude>1e-10&&q<amplitude){
                double phi=Math.atan2(bc,ac),delta=Math.acos(Math.max(-1,Math.min(1,q/amplitude)));
                candidates[count++]=phi-delta;candidates[count++]=phi+delta;
            }
        }
        double[] best=null;double cost=Double.POSITIVE_INFINITY;
        for(int k=0;k<count;k++){
            double angle=Math.IEEEremainder(candidates[k],2*Math.PI);double[] p=new double[3];
            for(int i=0;i<3;i++)p[i]=mid[i]+radius*(u[i]*Math.cos(angle)+v[i]*Math.sin(angle));
            if(dot(normals[0],p)>limits[0]+1e-9||dot(normals[1],p)>limits[1]+1e-9)continue;
            if(Math.abs(angle)<cost){cost=Math.abs(angle);best=p;}
        }
        return Optional.ofNullable(best);
    }
}
