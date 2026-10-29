package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.client.animation.gltf.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

final class FirstPersonBlenderTest {
    static final Path RESOURCES=Path.of("src/main/resources/assets");
    static final WeaponId PROFILE=WeaponId.parse("bobin_craft:armament/debug/first_person_fixture.profile.json");
    static FirstPersonAsset asset;static JsonObject reference;static int[][] mapping;
    private record GaugeCollider(int mesh,List<double[]> planes){}
    static final List<GaugeCollider> gaugeColliders=new ArrayList<>();
    @BeforeAll static void prepare()throws Exception{
        asset=FirstPersonAsset.load(PROFILE,id->Files.newInputStream(RESOURCES.resolve(id.namespace()).resolve(id.path())));
        reference=JsonParser.parseString(Files.readString(Path.of("art/armament/first_person_fixture.reference.json"))).getAsJsonObject();
        var rig=asset.model().rig();var bind=new RigFrame(rig);mapping=new int[rig.meshes().size()][];
        var rest=reference.getAsJsonObject("clips").getAsJsonArray("idle").get(0).getAsJsonObject().getAsJsonObject("vertices");
        for(int m=0;m<rig.meshes().size();m++){
            var mesh=rig.meshes().get(m);var points=rest.getAsJsonArray(rig.nodes().get(mesh.node()).name());mapping[m]=new int[mesh.vertexCount()];
            for(int v=0;v<mesh.vertexCount();v++){
                double best=Double.POSITIVE_INFINITY;int index=-1;
                for(int p=0;p<points.size();p++){double distance=0;for(int k=0;k<3;k++)distance=Math.max(distance,Math.abs(points.get(p).getAsJsonArray().get(k).getAsDouble()-bind.vertex(m,v,k)));if(distance<best){best=distance;index=p;}}
                assertTrue(best<4e-5,"bind geometry must match Blender: "+rig.nodes().get(mesh.node()).name()+" error="+best);mapping[m][v]=index;
            }
        }
        gaugeColliders.clear();
        for(int m=0;m<rig.meshes().size();m++){
            var mesh=rig.meshes().get(m);if(mesh.skin()>=0)continue;
            assertTrue(rig.nodes().get(mesh.node()).name().startsWith("gauge_"));
            List<double[]> planes=new ArrayList<>();
            for(int v=0;v<mesh.vertexCount();v++){
                double nx=mesh.vertex(v,3),ny=mesh.vertex(v,4),nz=mesh.vertex(v,5),d=nx*mesh.vertex(v,0)+ny*mesh.vertex(v,1)+nz*mesh.vertex(v,2);
                boolean duplicate=false;for(double[] plane:planes)if(Math.abs(plane[0]-nx)+Math.abs(plane[1]-ny)+Math.abs(plane[2]-nz)<1e-6&&Math.abs(plane[3]-d)<1e-6)duplicate=true;
                if(!duplicate)planes.add(new double[]{nx,ny,nz,d});
            }
            assertTrue(planes.size()>=6,"the beveled gauge panel must be a closed convex solid");
            gaugeColliders.add(new GaugeCollider(m,List.copyOf(planes)));
        }
        assertEquals(7,gaugeColliders.size());
    }
    @Test void twoRealArmsAndNeutralErgonomicGaugeArePresent(){
        assertEquals(11,asset.model().rig().meshes().size());assertEquals(36,asset.model().rig().skins().get(0).size());assertEquals(5,asset.model().rig().clips().size());
        for(String node:List.of("upper_right","upper_left","forearm_right","forearm_left","hand_right","hand_left","thumb_1_right","little_3_left"))assertTrue(asset.model().rig().node(node)>=0);
        for(String node:List.of("gauge_long_body","gauge_rear_support","gauge_primary_handle","gauge_handle_neck","gauge_forward_support","gauge_forward_axis","gauge_side_axis"))assertTrue(asset.model().rig().node(node)>=0);
    }
    @TestFactory Stream<DynamicTest> importedPosesMatchBlenderAndBothGripContacts(){
        return reference.getAsJsonObject("clips").entrySet().stream().flatMap(entry->StreamSupport.stream(entry.getValue().getAsJsonArray().spliterator(),false).map(value->DynamicTest.dynamicTest(entry.getKey()+"@"+value.getAsJsonObject().get("seconds"),()->{
            var expected=value.getAsJsonObject();double seconds=expected.get("seconds").getAsDouble();var controller=new GltfAnimationController(asset.model().rig());controller.play(entry.getKey(),GltfAnimationController.Mode.HOLD,0,0);var frame=controller.sampleSeconds(seconds);
            for(int m=0;m<frame.model().meshes().size();m++){
                var mesh=frame.model().meshes().get(m);String name=frame.model().nodes().get(mesh.node()).name();JsonArray points=expected.getAsJsonObject("vertices").getAsJsonArray(name);
                for(int v=0;v<mesh.vertexCount();v++)for(int k=0;k<3;k++)assertEquals(points.get(mapping[m][v]).getAsJsonArray().get(k).getAsDouble(),frame.vertex(m,v,k),8e-5,name+" vertex "+v+" axis "+k);
            }
            var contacts=new GripAttachments(asset.profile(),asset.sockets());assertTrue(contacts.matches(frame),"contacts: "+contacts.positionError()+"m / "+contacts.angleError()+"deg");
            double[] matrix=new double[16];
            for(String name:asset.sockets().names()){
                asset.sockets().sample(name,frame,matrix);var values=expected.getAsJsonObject("sockets").getAsJsonArray(name);for(int k=0;k<16;k++)assertEquals(values.get(k).getAsDouble(),matrix[k],8e-5,name+" matrix "+k);
            }
        })));
    }
    @Test void blenderSurfaceChecksFindNoHandObjectIntersections(){for(JsonElement entry:reference.getAsJsonArray("intersections"))assertEquals(0,entry.getAsJsonObject().getAsJsonObject("pairs").size(),entry.toString());}
    @Test void cameraMovementAndCrossfadesDoNotDetachHands(){
        var presenter=new FirstPersonWeaponPresenter(asset);double[] identity=FirstPersonMathTest.identity(),projection=new double[16];PresentationMath.perspective(70,16.0/9,0.05,256,projection);
        presenter.show(0);
        for(int tick=0;tick<120;tick++){
            if(tick==28)presenter.animation("inspect",tick/20.0);if(tick==38)presenter.animation("idle",tick/20.0);if(tick==40)presenter.animation("lower",tick/20.0);if(tick==65)presenter.animation("raise",tick/20.0);
            presenter.tick(tick,tick<55?tick*2.5:275-tick*2.5,tick<60?tick*0.5:60-tick*0.5,tick>10&&tick<95);
            for(int frame=0;frame<4;frame++){
                assertTrue(presenter.sample(tick,frame/4f,identity,projection,identity),tick+" "+presenter.rejection()+" "+presenter.contactMetres()+"m / "+presenter.contactDegrees()+"deg, depth="+presenter.minimumDepth());
                if(frame==0)noGaugePenetration(presenter.pose());
            }
        }
    }
    private static void noGaugePenetration(RigFrame frame){
        double[] world=new double[16],inverse=new double[16];
        for(var collider:gaugeColliders){
            int node=frame.model().meshes().get(collider.mesh()).node();for(int i=0;i<16;i++)world[i]=frame.global(node,i);RigMath.inverseAffine(world,0,inverse,0);
            for(int m=0;m<frame.model().meshes().size();m++)if(frame.model().meshes().get(m).skin()>=0)for(int v=0;v<frame.model().meshes().get(m).vertexCount();v++){
                double px=frame.vertex(m,v,0),py=frame.vertex(m,v,1),pz=frame.vertex(m,v,2);
                double x=inverse[0]*px+inverse[4]*py+inverse[8]*pz+inverse[12],y=inverse[1]*px+inverse[5]*py+inverse[9]*pz+inverse[13],z=inverse[2]*px+inverse[6]*py+inverse[10]*pz+inverse[14];
                boolean inside=true;for(double[] plane:collider.planes())if(plane[0]*x+plane[1]*y+plane[2]*z-plane[3]>=-0.0002){inside=false;break;}
                assertFalse(inside,"skinned vertex penetrates the ergonomic gauge: panel="+frame.model().nodes().get(node).name()+", mesh="+m+", vertex="+v);
            }
        }
    }
    @Test void defaultShowIsAbsolutelyStaticDespiteClockMovementAndInertiaInputs(){
        var presenter=new FirstPersonWeaponPresenter(asset);presenter.show(0);presenter.inertia(true);
        double[] identity=FirstPersonMathTest.identity(),projection=new double[16];PresentationMath.perspective(70,16.0/9,.05,100,projection);
        assertTrue(presenter.sample(0,0,identity,projection,identity));var rig=asset.model().rig();double[][] expected=new double[rig.meshes().size()][];
        for(int m=0;m<expected.length;m++){expected[m]=new double[rig.meshes().get(m).vertexCount()*3];for(int v=0;v<rig.meshes().get(m).vertexCount();v++)for(int k=0;k<3;k++)expected[m][v*3+k]=presenter.pose().vertex(m,v,k);}
        float[] before=new float[16],after=new float[16];presenter.copyProjection(before);
        for(long tick:List.of(1L,20L,51L,2000L)){
            presenter.tick(tick,tick*2.5,tick%65,true);
            for(float delta:new float[]{0,.25f,.75f}){
                assertTrue(presenter.sample(tick,delta,identity,projection,identity));assertEquals("idle",presenter.action());
                for(int m=0;m<expected.length;m++)for(int v=0;v<rig.meshes().get(m).vertexCount();v++)for(int k=0;k<3;k++)assertEquals(expected[m][v*3+k],presenter.pose().vertex(m,v,k),1e-12);
                presenter.copyProjection(after);assertArrayEquals(before,after);
                noGaugePenetration(presenter.pose());
            }
        }
    }
    @Test void cameraGuardRejectsUnsafeOffsetsInsteadOfMovingIndividualHands(){
        var p=new FirstPersonWeaponPresenter(asset);p.show(0);double[] hand=FirstPersonMathTest.identity(),projection=new double[16];PresentationMath.perspective(70,16.0/9,0.05,100,projection);hand[14]=1.2;
        assertFalse(p.sample(0,0,hand,projection,FirstPersonMathTest.identity()));assertEquals("camera_clearance",p.rejection());
    }
    @Test void successfulReloadUsesIndependentPresentationStorage()throws Exception{
        var other=FirstPersonAsset.load(PROFILE,id->Files.newInputStream(RESOURCES.resolve(id.namespace()).resolve(id.path())));assertNotSame(asset.model().rig(),other.model().rig());
        var a=new FirstPersonWeaponPresenter(asset);var b=new FirstPersonWeaponPresenter(other);a.animation("inspect",0);b.animation("lower",0);
        double[] identity=FirstPersonMathTest.identity(),projection=new double[16];PresentationMath.perspective(70,1.5,0.05,100,projection);
        assertTrue(a.sample(10,0.5f,identity,projection,identity));assertTrue(b.sample(10,0.5f,identity,projection,identity));assertNotSame(a.pose(),b.pose());assertNotEquals(a.pose().global(a.pose().model().node("presentation_root"),13),b.pose().global(b.pose().model().node("presentation_root"),13));
    }
    @Test void ergonomicGaugeDimensionsAreAuthoredInAbsoluteMetres(){
        var frame=new RigFrame(asset.model().rig());
        // Dimensions are independently specified here, in glTF X/Y/Z order.
        Map<String,double[]> dimensions=Map.of("gauge_long_body",new double[]{.10,.09,.69},"gauge_rear_support",new double[]{.16,.10,.16},
            "gauge_primary_handle",new double[]{.0625,.18,.085},"gauge_handle_neck",new double[]{.04,.053,.02},"gauge_forward_support",new double[]{.10,.035,.19});
        for(var entry:dimensions.entrySet()){
            int node=frame.model().node(entry.getKey()),found=0;double[] min={100,100,100},max={-100,-100,-100};
            for(int m=0;m<frame.model().meshes().size();m++)if(frame.model().meshes().get(m).node()==node){
                found++;for(int v=0;v<frame.model().meshes().get(m).vertexCount();v++)for(int k=0;k<3;k++){double value=frame.vertex(m,v,k);min[k]=Math.min(min[k],value);max[k]=Math.max(max[k],value);}
            }
            assertEquals(1,found);for(int k=0;k<3;k++)assertEquals(entry.getValue()[k],max[k]-min[k],3e-5,entry.getKey()+" dimension "+k);
        }
    }
    @Test void changingFovAspectAndViewBobKeepsContactsAndUsesNewProjection(){
        var presenter=new FirstPersonWeaponPresenter(asset);presenter.animation("idle",0);presenter.inertia(false);
        for(double fov:List.of(30.0,70.0,110.0))for(double aspect:List.of(4.0/3,16.0/9,9.0/16)){
            double[] projection=new double[16],identity=FirstPersonMathTest.identity(),hand=FirstPersonMathTest.identity();PresentationMath.perspective(fov,aspect,0.05,256,projection);hand[13]=0.015;
            assertTrue(presenter.sample(20,0.5f,hand,projection,identity));assertTrue(presenter.contactMetres()<1e-5);
            double[] trs={0,0,0,0,0,0,1,1,1,1},root=new double[16],view=new double[16],expected=new double[16],inverse=new double[16];
            for(int i=0;i<3;i++)trs[i]=asset.profile().cameraOffset().get(i);for(int i=0;i<4;i++)trs[i+3]=asset.profile().cameraRotation().get(i);
            RigMath.compose(trs,0,root,0);RigMath.multiply(hand,0,root,0,view,0);RigMath.multiply(projection,0,view,0,expected,0);RigMath.inverseAffine(view,0,inverse,0);
            float[] actual=new float[16];presenter.copyProjection(actual);for(int i=0;i<16;i++)assertEquals(expected[i],actual[i],1e-5);
            for(int i=0;i<3;i++)assertEquals(inverse[12+i],presenter.cameraInModel(i),1e-12);
        }
    }
    @Test void presenterReusesPoseAndBuffersAfterWarmup(){
        var presenter=new FirstPersonWeaponPresenter(asset);presenter.animation("idle",0);var identity=FirstPersonMathTest.identity();double[] projection=new double[16];PresentationMath.perspective(70,1.7,0.05,100,projection);
        // The additional arm/sleeve meshes need the same fully warmed JVM as the
        // rig benchmark. Keep the zero-allocation assertion; exclude JIT startup.
        for(int i=0;i<10000;i++)assertTrue(presenter.sample(i,0.5f,identity,projection,identity));
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        long best=Long.MAX_VALUE;int stable=0;long nanos=0;long[] windows=new long[6];Arrays.fill(windows,-1);
        for(int pass=0;pass<6&&stable<2;pass++){
            long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(int i=0;i<300;i++)presenter.sample(2000+pass*300+i,0.5f,identity,projection,identity);
            nanos=System.nanoTime()-start;long bytes=bean.getThreadAllocatedBytes(thread)-before;windows[pass]=bytes;best=Math.min(best,bytes);stable=bytes==0?stable+1:0;
        }
        System.out.println("first_person_cpu_benchmark samples=300 stable_zero_windows="+stable+" best_bytes="+best+" windows="+Arrays.toString(windows)+" ns_per_sample="+(nanos/300));
        assertEquals(2,stable,"require two consecutive allocation-free steady-state windows, excluding JVM warmup/deoptimization");
    }
    @Test void attachmentRotationMetricDoesNotTreatFloatScaleNoiseAsWristRotation(){
        var p=asset.profile();var strict=new FirstPersonProfile(p.schemaVersion(),p.id(),p.model(),p.rig(),p.sockets(),p.cameraOffset(),p.cameraRotation(),p.cameraClearance(),p.transitionSeconds(),p.movementThreshold(),p.contactMetres(),0.001,p.clips(),p.grips(),p.inertia());
        var contacts=new GripAttachments(strict,asset.sockets());var controller=new GltfAnimationController(asset.model().rig());controller.play("inspect",GltfAnimationController.Mode.HOLD,0,0);
        for(int sample=0;sample<=30;sample++)assertTrue(contacts.matches(controller.sampleSeconds(sample/10.0)),"wrist angle="+contacts.angleError());
    }
}
