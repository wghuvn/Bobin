package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.client.animation.gltf.*;
import java.util.*;

/** On-demand measurements of the last accepted pose. No mesh correction or per-frame allocation. */
public final class FirstPersonPoseDiagnostics {
    private FirstPersonPoseDiagnostics() {}
    /** OpenGL view coordinates in metres; positive depth is in front of the camera. */
    public record Point(double x,double y,double z) { public double depth(){return -z;} }
    /** Unclipped normalized device coordinates, not a silhouette/occlusion or screen-coverage estimate. */
    public record Bounds(double minX,double minY,double maxX,double maxY,double minDepth,double maxDepth) {}
    public record Contact(String name,double metres,double degrees,Point palmNormal) {}
    /** Elbow bend is zero for a straight arm, in degrees. */
    public record Snapshot(Map<String,Point> joints,Map<String,Double> elbowDegrees,Map<String,Bounds> meshes,List<Contact> contacts) {}

    public static Snapshot capture(FirstPersonWeaponPresenter presenter){
        double[] view=new double[16],projection=new double[16];
        presenter.copyModelToView(view);presenter.copyViewProjection(projection);
        var pose=presenter.pose();Map<String,Point> joints=new LinkedHashMap<>();Map<String,Bounds> meshes=new LinkedHashMap<>();
        for(String side:List.of("right","left"))for(String part:List.of("upper","forearm","hand")){
            String name=part+"_"+side;
            for(int n=0;n<pose.model().nodes().size();n++)if(pose.model().nodes().get(n).name().equals(name))
                joints.put(name,point(view,pose.global(n,12),pose.global(n,13),pose.global(n,14)));
        }
        for(int m=0;m<pose.model().meshes().size();m++){
            var mesh=pose.model().meshes().get(m);String name=pose.model().nodes().get(mesh.node()).name();
            if(!name.startsWith("view_arm_"))continue;
            double minX=Double.POSITIVE_INFINITY,minY=minX,minDepth=minX,maxX=-minX,maxY=-minX,maxDepth=-minX;
            for(int v=0;v<mesh.vertexCount();v++){
                double x=pose.vertex(m,v,0),y=pose.vertex(m,v,1),z=pose.vertex(m,v,2);
                var p=point(view,x,y,z);double w=projection[3]*x+projection[7]*y+projection[11]*z+projection[15];
                if(w<=0||!Double.isFinite(w))throw new IllegalStateException("diagnostic vertex is behind the projection camera");
                double px=(projection[0]*x+projection[4]*y+projection[8]*z+projection[12])/w;
                double py=(projection[1]*x+projection[5]*y+projection[9]*z+projection[13])/w;
                minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
                minDepth=Math.min(minDepth,p.depth());maxDepth=Math.max(maxDepth,p.depth());
            }
            meshes.put(name,new Bounds(minX,minY,maxX,maxY,minDepth,maxDepth));
        }
        List<Contact> contacts=new ArrayList<>();double[] target=new double[16],hand=new double[16],inverse=new double[16],relative=new double[16],normal=new double[3];
        for(var grip:presenter.asset().profile().grips()){
            presenter.asset().sockets().sample(grip.target(),pose,target);presenter.asset().sockets().sample(grip.hand(),pose,hand);
            double x=target[12]-hand[12],y=target[13]-hand[13],z=target[14]-hand[14];
            RigMath.inverseAffine(target,0,inverse,0);RigMath.multiply(inverse,0,hand,0,relative,0);
            double trace=0;
            for(int c=0;c<3;c++){
                double length=Math.sqrt(relative[c*4]*relative[c*4]+relative[c*4+1]*relative[c*4+1]+relative[c*4+2]*relative[c*4+2]);
                trace+=relative[c*4+c]/length;
            }
            GripFrameConvention.palmNormal(hand,normal);
            Point palm=new Point(view[0]*normal[0]+view[4]*normal[1]+view[8]*normal[2],view[1]*normal[0]+view[5]*normal[1]+view[9]*normal[2],view[2]*normal[0]+view[6]*normal[1]+view[10]*normal[2]);
            contacts.add(new Contact(grip.name(),Math.sqrt(x*x+y*y+z*z),Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,(trace-1)/2)))),palm));
        }
        Map<String,Double> elbows=new LinkedHashMap<>();
        for(String side:List.of("right","left")){
            var shoulder=joints.get("upper_"+side);var elbow=joints.get("forearm_"+side);var wrist=joints.get("hand_"+side);
            if(shoulder!=null&&elbow!=null&&wrist!=null){
                double ax=elbow.x()-shoulder.x(),ay=elbow.y()-shoulder.y(),az=elbow.z()-shoulder.z(),bx=wrist.x()-elbow.x(),by=wrist.y()-elbow.y(),bz=wrist.z()-elbow.z();
                double cosine=(ax*bx+ay*by+az*bz)/Math.sqrt((ax*ax+ay*ay+az*az)*(bx*bx+by*by+bz*bz));
                elbows.put(side,Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cosine)))));
            }
        }
        return new Snapshot(Collections.unmodifiableMap(joints),Collections.unmodifiableMap(elbows),Collections.unmodifiableMap(meshes),List.copyOf(contacts));
    }
    private static Point point(double[] m,double x,double y,double z){return new Point(m[0]*x+m[4]*y+m[8]*z+m[12],m[1]*x+m[5]*y+m[9]*z+m[13],m[2]*x+m[6]*y+m[10]*z+m[14]);}
}
