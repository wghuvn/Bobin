package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.client.animation.gltf.*;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;

/** Visual torso only. Vanilla has already interpolated entity bodyYaw before
 * this model-local offset is evaluated. Arms/item retain their accepted rig.
 */
public final class ThirdPersonTorsoOrientation {
    public static final double MAX_YAW=Math.toRadians(10),AUTHORED_WEIGHT=.18;
    private ThirdPersonTorsoOrientation() {}
    public record Result(double[] matrix,double waistInset) {}
    public static double yaw(double authored){
        if(!Double.isFinite(authored))throw new IllegalArgumentException("non-finite torso yaw");
        return MAX_YAW*Math.tanh(authored*AUTHORED_WEIGHT/MAX_YAW);
    }
    public static Result resolve(double[] original,double[] pelvis,double oldInset,RigFrame item,double[] placement){
        Part p=toPart(original);double pitch=p.pitch(),roll=p.roll(),turn=yaw(p.yaw());
        Result neutral=orient(pitch,turn,roll,pelvis,oldInset);
        if(clear(neutral.matrix(),item,placement))return neutral;
        // The accepted lowered hold formerly relied on a 38-degree bladed chest.
        // Find the smallest hip-centred backward lean that clears that same item.
        // No item/arm displacement, whole-entity rotation or arbitrary translation.
        double blocked=pitch,free=pitch;
        for(int step=1;step<=15;step++){
            free=pitch-Math.toRadians(step);
            if(clear(orient(free,turn,roll,pelvis,oldInset).matrix(),item,placement)){
                for(int i=0;i<9;i++){
                    double middle=(blocked+free)/2;
                    if(clear(orient(middle,turn,roll,pelvis,oldInset).matrix(),item,placement))free=middle;else blocked=middle;
                }
                return orient(free,turn,roll,pelvis,oldInset);
            }
            blocked=free;
        }
        return neutral;
    }
    private static Result orient(double pitch,double turn,double roll,double[] pelvis,double oldInset){
        double cx=Math.cos(pitch),sx=Math.sin(pitch),cy=Math.cos(turn),sy=Math.sin(turn),cz=Math.cos(roll),sz=Math.sin(roll);
        double[] m={cz*cy,sz*cy,-sy,0,cz*sy*sx-sz*cx,sz*sy*sx+cz*cx,cy*sx,0,cz*sy*cx+sz*sx,sz*sy*cx-cz*sx,cy*cx,0,0,0,0,1};
        // Conjugate model axes into glTF; preserve pitch/roll, not the weapon twist.
        for(int c=0;c<3;c++)for(int r=0;r<3;r++)m[c*4+r]*=(r<2?-1:1)*(c<2?-1:1);
        double inset=Math.max(oldInset,Math.abs(m[1])*.25+Math.abs(m[9])*.125+1.0/64);
        double[] hip=point(m,0,-.75,0);
        m[12]=pelvis[0]-hip[0];m[13]=pelvis[1]-inset-hip[1];m[14]=pelvis[2]-hip[2];
        return new Result(m,inset);
    }
    private static boolean clear(double[] body,RigFrame item,double[] placement){
        double[] inverse=new double[16];RigMath.inverseAffine(body,0,inverse,0);
        double[] into=multiply(inverse,placement);
        for(int mesh=0;mesh<item.model().meshes().size();mesh++){
            var geometry=item.model().meshes().get(mesh);
            for(int i=0;i<geometry.indexCount();i+=3){
                double[][] t=new double[3][];
                for(int j=0;j<3;j++){
                    int v=geometry.index(i+j);t[j]=point(into,item.vertex(mesh,v,0),item.vertex(mesh,v,1),item.vertex(mesh,v,2));t[j][1]+=.375;
                }
                if(intersects(t))return false;
            }
        }
        return true;
    }
    private static boolean intersects(double[][] t){
        double[][] basis={{1,0,0},{0,1,0},{0,0,1}};
        for(double[] a:basis)if(separates(t,a))return false;
        double[][] edges={subtract(t[1],t[0]),subtract(t[2],t[1]),subtract(t[0],t[2])};
        if(separates(t,cross(edges[0],edges[1])))return false;
        for(double[] e:edges)for(double[] a:basis)if(separates(t,cross(e,a)))return false;
        return true;
    }
    private static boolean separates(double[][] t,double[] axis){
        if(dot(axis,axis)<1e-24)return false;
        double min=Double.POSITIVE_INFINITY,max=-min;
        for(double[] v:t){double d=dot(v,axis);min=Math.min(min,d);max=Math.max(max,d);}
        // 0.25 mm clearance prevents a contact boundary from flickering.
        double radius=.25025*Math.abs(axis[0])+.37525*Math.abs(axis[1])+.12525*Math.abs(axis[2]);
        return min>radius||max< -radius;
    }
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
}
