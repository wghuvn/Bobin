package com.bobincraft.armament;

import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.client.thirdperson.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;

final class ThirdPersonPresentationTest {
    private static final Map<Boolean,ThirdPersonAsset> ASSETS=new HashMap<>();
    @BeforeAll static void load() throws Exception {
        for(boolean slim:new boolean[]{false,true}) ASSETS.put(slim,ThirdPersonAsset.load(id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())),slim));
    }
    private static PresentationAction state(String key,double start){return new PresentationAction(PresentationAction.Action.parse(key),start,false);}
    static Stream<Arguments> poses(){
        var cases=new ArrayList<Arguments>();
        for(boolean slim:new boolean[]{false,true}) for(String action:new String[]{"idle","inspect","raise","lower","move"})
            for(boolean crouch:new boolean[]{false,true}) for(boolean run:new boolean[]{false,true})cases.add(Arguments.of(slim,action,crouch,run));
        return cases.stream();
    }
    @ParameterizedTest @MethodSource("poses") void rigidTwoHandContactsSurviveActionStanceMotionAndLook(boolean slim,String action,boolean crouch,boolean run) {
        var presenter=new ThirdPersonWeaponPresenter(ASSETS.get(slim));
        presenter.update(state(action,0),0,true,run,crouch);
        for(int i=0;i<=120;i++){
            double now=i/60.0;
            presenter.update(state(action,0),now,true,run,crouch);
            var pose=presenter.sample(now,Math.sin(now*3)*180,Math.cos(now*3)*90);
            assertTrue(pose.contactError()<1e-8);
            for(double[] m:new double[][]{pose.rightMatrix(),pose.leftMatrix(),pose.placement()}) {
                for(double v:m)assertTrue(Double.isFinite(v));
                assertEquals(1,RigMath.determinant(m,0),1e-6,"No mirrored or scaled hands");
                for(int c=0;c<3;c++)assertEquals(1,Math.sqrt(m[c*4]*m[c*4]+m[c*4+1]*m[c*4+1]+m[c*4+2]*m[c*4+2]),1e-6);
            }
            assertGripWithinCuboid(pose,true,slim);assertGripWithinCuboid(pose,false,slim);
            assertModelPartRoundTrip(pose.right(),pose.rightMatrix());assertModelPartRoundTrip(pose.left(),pose.leftMatrix());
            // Shoulder separation is authored vanilla Steve/Alex, never stretched to reach.
            assertEquals(10.0/16,length(subtract(position(pose.rightMatrix()),position(pose.leftMatrix()))),1e-6);
        }
    }
    private static void assertGripWithinCuboid(ThirdPersonWeaponPresenter.Pose pose,boolean right,boolean slim){
        double[] inverse=new double[16];RigMath.inverseAffine(right?pose.rightMatrix():pose.leftMatrix(),0,inverse,0);
        double[] transform=multiply(inverse,pose.placement());
        var frame=pose.geometry();double best=Double.POSITIVE_INFINITY;
        for(int m=0;m<frame.model().meshes().size();m++){
            var mesh=frame.model().meshes().get(m);
            String name=frame.model().nodes().get(mesh.node()).name();
            if(!name.equals(right?"gauge_primary_handle":"gauge_forward_support"))continue;
            for(int v=0;v<mesh.vertexCount();v++){
                double[] p=point(transform,frame.vertex(m,v,0),frame.vertex(m,v,1),frame.vertex(m,v,2));
                double center=(right?1:-1)*(slim?.5:1)/16;
                double dx=Math.max(0,Math.abs(p[0]-center)-(slim?3:4)/32.0);
                double dy=Math.max(0,Math.max(-.625-p[1],p[1]-.125));
                double dz=Math.max(0,Math.abs(p[2])-.125);
                best=Math.min(best,Math.sqrt(dx*dx+dy*dy+dz*dz));
            }
        }
        assertTrue(best<.006,"Real grip geometry must meet the vanilla cuboid, not only an empty marker: "+best);
    }
    private static void assertModelPartRoundTrip(Part p,double[] expected){
        double cx=Math.cos(p.pitch()),sx=Math.sin(p.pitch()),cy=Math.cos(p.yaw()),sy=Math.sin(p.yaw()),cz=Math.cos(p.roll()),sz=Math.sin(p.roll());
        double[] m={cz*cy,sz*cy,-sy,0,cz*sy*sx-sz*cx,sz*sy*sx+cz*cx,cy*sx,0,cz*sy*cx+sz*sx,sz*sy*cx-cz*sx,cy*cx,0,p.x()/16.0,p.y()/16.0,p.z()/16.0,1};
        for(int c=0;c<4;c++)for(int r=0;r<3;r++)m[c*4+r]*=(r<2?-1:1)*(c<2?-1:1);
        for(int i=0;i<16;i++)assertEquals(expected[i],m[i],2e-6,"ModelPart Z-Y-X rotation and pixels: "+i);
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void independentBlenderGoldensMatchRuntime(boolean slim) throws Exception {
        String stem="third_person_fixture"+(slim?"_slim":"");
        JsonObject ref=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/bobin_craft/armament/debug",stem+".reference.json"))).getAsJsonObject();
        for(JsonElement entry:ref.getAsJsonArray("samples")){
            var sample=entry.getAsJsonObject();String clip=sample.get("clip").getAsString();double time=sample.get("time").getAsDouble();
            var controller=new ThirdPersonAnimationController(ASSETS.get(slim));controller.update(state(clip,0),0,false,false,false);
            RigFrame frame=controller.sample(time);
            for(var node:sample.getAsJsonObject("matrices").entrySet()){
                double[] actual=matrix(frame,node.getKey());
                for(int i=0;i<16;i++)assertEquals(node.getValue().getAsJsonArray().get(i).getAsDouble(),actual[i],.0004,stem+" "+clip+" "+time+" "+node.getKey()+" ["+i+"]");
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void everyInterruptedTransitionMaintainsContactAndContinuity(boolean slim){
        for(String from:new String[]{"idle","inspect","raise","lower","move"})for(String to:new String[]{"idle","inspect","raise","lower","move"}){
            var p=new ThirdPersonWeaponPresenter(ASSETS.get(slim));
            p.update(state(from,0),0,false,false,false);
            var before=p.sample(.31,0,0);
            p.update(state(to,.31),.31,false,false,false);
            var at=p.sample(.31,0,0);
            assertArrayEquals(before.placement(),at.placement(),1e-6,"crossfade must start at current pose");
            assertArrayEquals(before.rightMatrix(),at.rightMatrix(),1e-6);
            for(int f=1;f<40;f++){
                double t=.31+f/144.0;
                if(f==9)p.update(state("inspect",t),t,true,false,true);
                var pose=p.sample(t,0,0);assertTrue(pose.contactError()<1e-8);
                assertTrue(pose.correctionMetres()<.09,"Bounded whole-object attachment correction");
            }
        }
    }
    @Test void playerInstancesAndReloadDoNotShareAnimationOrRestartTime(){
        var a=new ThirdPersonWeaponPresenter(ASSETS.get(false));var b=new ThirdPersonWeaponPresenter(ASSETS.get(false));
        var event=state("inspect",10);a.update(event,10,false,false,false);b.update(state("idle",10),10,false,false,false);
        var old=a.sample(11.2,0,0);var idle=b.sample(11.2,0,0);
        assertNotEquals(old.right(),idle.right());
        var reload=new ThirdPersonWeaponPresenter(ASSETS.get(false));reload.update(event,11.2,false,false,false);
        var resumed=reload.sample(11.2,0,0);
        assertArrayEquals(old.rightMatrix(),resumed.rightMatrix(),1e-7);
        assertEquals(10,reload.controller().action().started());
        assertNotSame(old.geometry(),idle.geometry());
    }
    @Test void f5SemanticClockMatchesApprovedFpsWithoutReadingTransforms() throws Exception {
        var first=FirstPersonAsset.load(VanillaArmAttachments.PROFILES.get("default"),id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())));
        var fps=new FirstPersonWeaponPresenter(first);fps.animation("inspect",0);
        var tp=new ThirdPersonWeaponPresenter(ASSETS.get(false));
        double started=0;String last="inspect";
        for(int tick=0;tick<90;tick++){
            // F5 repeatedly changes which pass draws; neither controller is reconstructed.
            boolean firstView=tick%3==0;
            assertTrue(PairedPresentationPolicy.simulates(firstView,true,true,true));
            fps.tick(tick,0,0,false);
            if(!fps.action().equals(last)){last=fps.action();started=tick/20.0;}
            tp.update(state(last,started),tick/20.0,false,false,false);
            tp.sample(tick/20.0,0,0);
            assertEquals(fps.action(),tp.controller().action().action().key());
            assertEquals(tick>=55?2.75:0,tp.controller().action().started());
        }
        assertFalse(PairedPresentationPolicy.simulates(false,false,true,true));
        assertFalse(PairedPresentationPolicy.simulates(false,true,true,false));
        assertFalse(PairedPresentationPolicy.simulates(false,true,false,true));
    }
    @Test void physicalGeometryDimensionsAreSharedButAnimationSetsAreIndependent() throws Exception {
        var tp=ASSETS.get(false).model().rig();
        var fp=FirstPersonAsset.load(VanillaArmAttachments.PROFILES.get("default"),id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path()))).model().rig();
        assertEquals(7,tp.meshes().size());assertTrue(tp.skins().isEmpty());
        for(var action:PresentationAction.Action.values())assertEquals(fp.clip(action.key()).duration(),tp.clip(action.key()).duration(),1e-5,"Semantic durations only");
        var tc=new ThirdPersonAnimationController(ASSETS.get(false));tc.update(state("inspect",0),0,false,false,false);
        var fc=new GltfAnimationController(fp);fc.play("inspect",GltfAnimationController.Mode.HOLD,0,0);
        double[] t0=matrix(tc.sample(0),"tp_weapon"),t1=matrix(tc.sample(1),"tp_weapon");
        double[] f0=matrix(fc.sampleSeconds(0),"presentation_root"),f1=matrix(fc.sampleSeconds(1),"presentation_root");
        double[] ti=new double[16],fi=new double[16];RigMath.inverseAffine(t0,0,ti,0);RigMath.inverseAffine(f0,0,fi,0);
        double[] td=multiply(ti,t1),fd=multiply(fi,f1);
        assertTrue(Math.abs(td[0]-fd[0])+Math.abs(td[1]-fd[1])+Math.abs(td[2]-fd[2])>.1,"External inspect is not a rescaled/rotated FPS root clip");
        for(var mesh:tp.meshes())assertTrue(tp.nodes().get(mesh.node()).name().startsWith("gauge_"));
    }
    @Test void eligibilityYieldsToEveryExistingOwnerAndRemotePlayers(){
        for(int invalid=0;invalid<10;invalid++){
            boolean[] v={true,true,true,true,true,true,true,false,false,true};
            v[invalid]=!v[invalid];
            var observation=new ThirdPersonEligibility.View(v[0],v[1],v[2],v[3],v[4],true,v[5],v[6],v[7],v[8],v[9]);
            assertNotEquals("ready",ThirdPersonEligibility.reason(observation));assertFalse(ThirdPersonEligibility.draws(observation));
        }
        for(boolean external:new boolean[]{false,true}){
            var v=new ThirdPersonEligibility.View(true,true,true,true,true,external,true,true,false,false,true);
            assertEquals("ready",ThirdPersonEligibility.reason(v));assertEquals(external,ThirdPersonEligibility.draws(v));
        }
    }
    @Test void frozenFirstPersonSourcesClipsGeometryAndCameraSettingsRemainByteIdentical() throws Exception {
        var frozen=JsonParser.parseString(Files.readString(Path.of("docs/armament/task5-2/validation/frozen-first-person.json"))).getAsJsonObject();
        for(var entry:frozen.getAsJsonObject("sha256").entrySet()){
            String actual=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(entry.getKey()))));
            assertEquals(entry.getValue().getAsString(),actual,entry.getKey());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void heldGeometryNeverCrossesTorso(boolean slim) {
        for(String clip:new String[]{"idle","inspect","raise","lower","move"}){
            var p=new ThirdPersonWeaponPresenter(ASSETS.get(slim));p.update(state(clip,0),0,false,false,false);
            for(int step=0;step<=72;step++){
                var pose=p.sample(step/24.0,0,0);var frame=pose.geometry();
                double[] inverse=new double[16];RigMath.inverseAffine(matrix(frame,"tp_body"),0,inverse,0);
                double[] intoBody=multiply(inverse,pose.placement());
                for(int m=0;m<frame.model().meshes().size();m++){
                    var mesh=frame.model().meshes().get(m);
                    for(int i=0;i<mesh.indexCount();i+=3){
                        double[][] triangle=new double[3][];
                        for(int c=0;c<3;c++){
                            int v=mesh.index(i+c);triangle[c]=point(intoBody,frame.vertex(m,v,0),frame.vertex(m,v,1),frame.vertex(m,v,2));
                            triangle[c][1]+=.375; // torso AABB centred at the origin
                        }
                        assertFalse(triangleTouchesBody(triangle),"Torso/GLB collision: "+slim+" "+clip+" step "+step+" mesh "+m);
                    }
                }
            }
        }
    }
    // Independent triangle-vs-box SAT, including edge intersections with no contained vertex.
    private static boolean triangleTouchesBody(double[][] t){
        double[][] edges={subtract(t[1],t[0]),subtract(t[2],t[1]),subtract(t[0],t[2])};
        var axes=new ArrayList<double[]>();axes.add(new double[]{1,0,0});axes.add(new double[]{0,1,0});axes.add(new double[]{0,0,1});
        axes.add(cross(edges[0],edges[1]));
        for(double[] e:edges)for(double[] a:List.of(new double[]{1,0,0},new double[]{0,1,0},new double[]{0,0,1}))axes.add(cross(e,a));
        for(double[] a:axes){
            if(length(a)<1e-12)continue;
            double min=Double.POSITIVE_INFINITY,max=-min;
            for(double[] v:t){double d=dot(a,v);min=Math.min(min,d);max=Math.max(max,d);}
            double radius=.25*Math.abs(a[0])+.375*Math.abs(a[1])+.125*Math.abs(a[2]);
            if(min>radius || max< -radius)return false;
        }
        return true;
    }
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    @Test void renderRateDoesNotAdvanceSemanticTimeOrChangePose(){
        for(int rate:new int[]{30,60,144}){
            var frequent=new ThirdPersonWeaponPresenter(ASSETS.get(false));var sparse=new ThirdPersonWeaponPresenter(ASSETS.get(false));
            frequent.update(state("inspect",0),0,true,false,true);sparse.update(state("inspect",0),0,true,false,true);
            for(int frame=0;frame<rate;frame++)frequent.sample(frame/(double)rate,12,-5);
            var a=frequent.sample(1,12,-5);var b=sparse.sample(1,12,-5);
            assertArrayEquals(a.rightMatrix(),b.rightMatrix(),1e-9);assertArrayEquals(a.placement(),b.placement(),1e-9);
            assertEquals(0,frequent.controller().action().started());
        }
    }

    @Test void measuredPlayerScaleDoesNotChangePhysicalItemSize(){
        var p=new ThirdPersonWeaponPresenter(ASSETS.get(false));p.update(state("idle",0),0,false,false,false);
        for(double entityScale:new double[]{.9375,1,1.1}){
            var pose=p.sample(0,0,0,1/entityScale);
            double[] a=point(pose.placement(),0,0,0),b=point(pose.placement(),0,.69,0);
            assertEquals(.69,length(subtract(a,b))*entityScale,1e-9,"One GLB metre remains one world metre");
            assertEquals(10.0/16*entityScale,length(subtract(position(pose.rightMatrix()),position(pose.leftMatrix())))*entityScale,1e-8,"Keep actual player shoulders");
            assertTrue(pose.contactError()<1e-8);
        }
    }
    @Test void crouchLeansTorsoForwardAndKeepsVanillaLegsIndependent(){
        var p=new ThirdPersonWeaponPresenter(ASSETS.get(false));p.update(state("idle",0),0,false,false,true);
        var pose=p.sample(1,0,0);
        assertTrue(pose.body().pitch()>Math.toRadians(20));assertTrue(pose.body().y()>2.5);
        assertTrue(pose.contactError()<1e-8);
    }

}
