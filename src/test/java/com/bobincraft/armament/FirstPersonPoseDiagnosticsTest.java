package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.presentation.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

final class FirstPersonPoseDiagnosticsTest {
    private record Fixture(FirstPersonAsset asset,JsonObject reference) {}
    private static final Map<String,Fixture> fixtures=new LinkedHashMap<>();
    @BeforeAll static void load()throws Exception{
        for(String variant:List.of("","_slim")){
            String name="first_person_fixture"+variant;
            var asset=FirstPersonAsset.load(WeaponId.parse("bobin_craft:armament/debug/"+name+".profile.json"),id->Files.newInputStream(Path.of("src/main/resources/assets").resolve(id.namespace()).resolve(id.path())));
            var reference=JsonParser.parseString(Files.readString(Path.of("art/armament/"+name+".reference.json"))).getAsJsonObject();
            fixtures.put(name,new Fixture(asset,reference));
        }
    }
    private static FirstPersonWeaponPresenter presenter(Fixture f,double fov,double aspect){
        var p=new FirstPersonWeaponPresenter(f.asset());p.calibration();double[] projection=new double[16];PresentationMath.perspective(fov,aspect,.05,256,projection);
        double[] identity=FirstPersonMathTest.identity();assertTrue(p.sample(0,0,identity,projection,identity),p.rejection());return p;
    }
    @TestFactory Stream<DynamicTest> projectedArmBoundsMatchIndependentBlenderVerticesAtThreeFovsAndAspects(){
        return fixtures.entrySet().stream().flatMap(f->Stream.of(70.0,90.0,110.0).flatMap(fov->Stream.of(4.0/3,16.0/9,21.0/9).map(aspect->DynamicTest.dynamicTest(f.getKey()+" "+fov+"deg @"+aspect,()->{
            var fixture=f.getValue();var snapshot=FirstPersonPoseDiagnostics.capture(presenter(fixture,fov,aspect));
            JsonObject goldenProjection=null;
            for(var entry:fixture.reference().getAsJsonObject("camera_diagnostics").getAsJsonArray("projections")){
                var candidate=entry.getAsJsonObject();
                if(Math.abs(candidate.get("fov").getAsDouble()-fov)<1e-8&&Math.abs(candidate.get("aspect").getAsDouble()-aspect)<1e-8)goldenProjection=candidate;
            }
            assertNotNull(goldenProjection,"Blender camera must contain the selected FOV/aspect");
            var vertices=fixture.reference().getAsJsonObject("clips").getAsJsonArray("idle").get(0).getAsJsonObject().getAsJsonObject("vertices");
            assertEquals(Set.of("view_arm_left","view_arm_right"),snapshot.meshes().keySet());
            for(var entry:snapshot.meshes().entrySet()){
                double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
                for(var vertex:vertices.getAsJsonArray(entry.getKey())){
                    var v=vertex.getAsJsonArray();double[] view=view(fixture.asset().profile(),v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble());
                    double depth=-view[2],factor=1/Math.tan(Math.toRadians(fov)/2),x=view[0]*factor/aspect/depth,y=view[1]*factor/depth;
                    bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);bounds[4]=Math.min(bounds[4],depth);bounds[5]=Math.max(bounds[5],depth);
                }
                var b=entry.getValue();assertArrayEquals(bounds,new double[]{b.minX(),b.minY(),b.maxX(),b.maxY(),b.minDepth(),b.maxDepth()},2e-4,entry.getKey());
                double[] blender=values(goldenProjection.getAsJsonObject("bounds").getAsJsonArray(entry.getKey()));
                assertArrayEquals(blender,new double[]{b.minX(),b.minY(),b.maxX(),b.maxY(),b.minDepth(),b.maxDepth()},2e-4,"Actual Blender camera projection: "+entry.getKey());
                assertTrue(b.minDepth()>=fixture.asset().profile().cameraClearance(),"No arm vertex may cross the metric camera guard");
            }
        }))));
    }
    @TestFactory Stream<DynamicTest> cameraJointsAndPalmNormalsAreFiniteAndContactsRemainRigid(){
        return fixtures.entrySet().stream().map(f->DynamicTest.dynamicTest(f.getKey(),()->{
            var fixture=f.getValue();var presenter=presenter(fixture,90,16.0/9);var snapshot=FirstPersonPoseDiagnostics.capture(presenter);
            assertEquals(Set.of("upper_right","forearm_right","hand_right","upper_left","forearm_left","hand_left"),snapshot.joints().keySet());
            for(var entry:snapshot.joints().entrySet()){
                var p=entry.getValue();assertTrue(Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()));
                assertTrue(p.depth()>.075&&p.depth()<2,"Camera joint depth: "+entry);
                var golden=fixture.reference().getAsJsonObject("camera_diagnostics").getAsJsonObject("joints").getAsJsonArray(entry.getKey());
                assertArrayEquals(values(golden),new double[]{p.x(),p.y(),p.z()},8e-5,"Actual Blender camera joint: "+entry.getKey());
            }
            var sockets=fixture.reference().getAsJsonObject("clips").getAsJsonArray("idle").get(0).getAsJsonObject().getAsJsonObject("sockets");
            for(var contact:snapshot.contacts()){
                assertTrue(contact.metres()<1e-5);assertTrue(contact.degrees()<.001);
                String name=contact.name().equals("primary")?"hand_primary":"hand_secondary";var matrix=sockets.getAsJsonArray(name);
                double[] expected=view(fixture.asset().profile(),-matrix.get(8).getAsDouble(),-matrix.get(9).getAsDouble(),-matrix.get(10).getAsDouble());
                for(int i=0;i<3;i++)expected[i]-=fixture.asset().profile().cameraOffset().get(i);
                var n=contact.palmNormal();assertArrayEquals(expected,new double[]{n.x(),n.y(),n.z()},2e-5);
            }
            // Capturing diagnostics is an observation and cannot resample or mutate the pose.
            long evaluations=presenter.pose().evaluations();FirstPersonPoseDiagnostics.capture(presenter);assertEquals(evaluations,presenter.pose().evaluations());
        }));
    }
    @TestFactory Stream<DynamicTest> shoulderElbowAndWristOriginsMatchEveryBlenderClipSample(){
        return fixtures.entrySet().stream().flatMap(f->f.getValue().reference().getAsJsonObject("clips").entrySet().stream().flatMap(clip->StreamSupport.stream(clip.getValue().getAsJsonArray().spliterator(),false).map(value->DynamicTest.dynamicTest(f.getKey()+" "+clip.getKey()+" @"+value.getAsJsonObject().get("seconds"),()->{
            var expected=value.getAsJsonObject();var fixture=f.getValue();var controller=new GltfAnimationController(fixture.asset().model().rig());controller.play(clip.getKey(),GltfAnimationController.Mode.HOLD,0,0);
            var frame=controller.sampleSeconds(expected.get("seconds").getAsDouble());
            var joints=expected.getAsJsonObject("joints");assertEquals(6,joints.size());
            for(var entry:joints.entrySet()){
                int node=frame.model().node(entry.getKey());
                assertArrayEquals(values(entry.getValue().getAsJsonArray()),new double[]{frame.global(node,12),frame.global(node,13),frame.global(node,14)},8e-5,entry.getKey());
            }
        }))));
    }
    @TestFactory Stream<DynamicTest> evaluatedTriangleSurfaceContactsMatchBlenderBvhDistances(){
        return fixtures.entrySet().stream().flatMap(f->f.getValue().reference().getAsJsonObject("clips").entrySet().stream().flatMap(clip->StreamSupport.stream(clip.getValue().getAsJsonArray().spliterator(),false).map(value->DynamicTest.dynamicTest(f.getKey()+" surface "+clip.getKey()+" @"+value.getAsJsonObject().get("seconds"),()->{
            var expected=value.getAsJsonObject();var fixture=f.getValue();var controller=new GltfAnimationController(fixture.asset().model().rig());controller.play(clip.getKey(),GltfAnimationController.Mode.HOLD,0,0);
            var frame=controller.sampleSeconds(expected.get("seconds").getAsDouble());
            for(String side:List.of("right","left")){
                String slot=side.equals("right")?"primary":"secondary";double[] socket=new double[16];fixture.asset().sockets().sample("grip_"+slot,frame,socket);
                var target=new V(socket[12],socket[13],socket[14]);var palm=nearest(frame,"view_arm_"+side,target);
                var panel=nearest(frame,side.equals("right")?"gauge_primary_handle":"gauge_forward_support",palm.point());
                var golden=expected.getAsJsonObject("surface_contacts").getAsJsonObject(side);
                assertEquals(golden.get("target_to_skin_metres").getAsDouble(),palm.distance(),8e-5,"Independent triangle-to-target distance: "+side);
                assertEquals(golden.get("skin_to_panel_metres").getAsDouble(),panel.distance(),8e-5,"Independent triangle-to-panel distance: "+side);
                assertTrue(palm.distance()<.0002,"Grip must sit on the evaluated hand surface");
                assertEquals(.001,panel.distance(),.0002,"Authored one-millimetre surface clearance must remain throughout the clip");
            }
        }))));
    }
    @Test void diagnosticsRequireAnAcceptedPoseAndRejectStaleUnsafeSamples(){
        var fixture=fixtures.values().iterator().next();var p=new FirstPersonWeaponPresenter(fixture.asset());p.calibration();
        assertThrows(IllegalStateException.class,()->FirstPersonPoseDiagnostics.capture(p));
        double[] unsafe=FirstPersonMathTest.identity(),projection=new double[16];unsafe[14]=2;PresentationMath.perspective(90,16.0/9,.05,256,projection);
        assertFalse(p.sample(0,0,unsafe,projection,FirstPersonMathTest.identity()));
        assertThrows(IllegalStateException.class,()->FirstPersonPoseDiagnostics.capture(p));
    }
    /** Independent quaternion vector rotation, using the authored profile and Blender golden geometry. */
    private static double[] view(FirstPersonProfile p,double x,double y,double z){
        var q=p.cameraRotation();double qx=q.get(0),qy=q.get(1),qz=q.get(2),qw=q.get(3);
        double tx=2*(qy*z-qz*y),ty=2*(qz*x-qx*z),tz=2*(qx*y-qy*x);
        return new double[]{x+qw*tx+qy*tz-qz*ty+p.cameraOffset().get(0),y+qw*ty+qz*tx-qx*tz+p.cameraOffset().get(1),z+qw*tz+qx*ty-qy*tx+p.cameraOffset().get(2)};
    }
    private static double[] values(JsonArray array){double[] result=new double[array.size()];for(int i=0;i<result.length;i++)result[i]=array.get(i).getAsDouble();return result;}
    private record V(double x,double y,double z){
        V add(V b){return new V(x+b.x,y+b.y,z+b.z);}V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}V scale(double s){return new V(x*s,y*s,z*s);}
        double dot(V b){return x*b.x+y*b.y+z*b.z;}V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
    }
    private record Nearest(V point,double distance){}
    private static Nearest nearest(RigFrame frame,String name,V point){
        V best=null;double squared=Double.POSITIVE_INFINITY;
        for(int m=0;m<frame.model().meshes().size();m++){
            var mesh=frame.model().meshes().get(m);if(!frame.model().nodes().get(mesh.node()).name().equals(name))continue;
            for(int i=0;i<mesh.indexCount();i+=3){
                V candidate=triangle(point,vertex(frame,m,mesh.index(i)),vertex(frame,m,mesh.index(i+1)),vertex(frame,m,mesh.index(i+2)));
                V difference=point.sub(candidate);double distance=difference.dot(difference);
                if(distance<squared){squared=distance;best=candidate;}
            }
        }
        assertNotNull(best,"Expected triangle mesh "+name);return new Nearest(best,Math.sqrt(squared));
    }
    private static V vertex(RigFrame frame,int mesh,int vertex){return new V(frame.vertex(mesh,vertex,0),frame.vertex(mesh,vertex,1),frame.vertex(mesh,vertex,2));}
    /** Independent closest-point geometry, rather than imported socket equality or vertex sampling. */
    private static V triangle(V p,V a,V b,V c){
        V ab=b.sub(a),ac=c.sub(a),normal=ab.cross(ac);double normalSquared=normal.dot(normal);
        if(normalSquared>1e-20){
            V projected=p.sub(normal.scale(p.sub(a).dot(normal)/normalSquared)),ap=projected.sub(a);
            double aa=ab.dot(ab),bb=ac.dot(ac),cross=ab.dot(ac),denom=aa*bb-cross*cross;
            double u=(bb*ap.dot(ab)-cross*ap.dot(ac))/denom,v=(aa*ap.dot(ac)-cross*ap.dot(ab))/denom;
            if(u>=0&&v>=0&&u+v<=1)return projected;
        }
        V best=segment(p,a,b);double squared=p.sub(best).dot(p.sub(best));
        for(V q:List.of(segment(p,b,c),segment(p,c,a))){double distance=p.sub(q).dot(p.sub(q));if(distance<squared){best=q;squared=distance;}}
        return best;
    }
    private static V segment(V p,V a,V b){V delta=b.sub(a);double squared=delta.dot(delta);return squared<1e-20?a:a.add(delta.scale(Math.max(0,Math.min(1,p.sub(a).dot(delta)/squared))));}
}
