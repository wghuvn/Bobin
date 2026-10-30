package com.bobincraft.armament;

import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.firstpersonmotion.*;
import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController.Mode;
import com.bobincraft.armament.client.presentation.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

final class FirstPersonMotionTest {
    private record Fixture(String variant,FirstPersonAsset approved,FirstPersonAsset extended){}
    private static final List<Fixture> FIXTURES=new ArrayList<>();
    private static final Path ASSETS=Path.of("src/main/resources/assets");
    @BeforeAll static void load()throws Exception{
        for(String variant:List.of("default","slim"))FIXTURES.add(new Fixture(variant,
            FirstPersonAsset.load(VanillaArmAttachments.PROFILES.get(variant),id->Files.newInputStream(ASSETS.resolve(id.namespace()).resolve(id.path()))),
            FirstPersonAsset.load(FirstPersonMotionAssets.PROFILES.get(variant),id->Files.newInputStream(ASSETS.resolve(id.namespace()).resolve(id.path())))));
    }
    @TestFactory Stream<DynamicTest> onlyNewAnimationDataIsAppendedToApprovedGlb(){
        return FIXTURES.stream().map(f->DynamicTest.dynamicTest(f.variant(),()->{
            byte[] old=Files.readAllBytes(path(f.approved())),combined=Files.readAllBytes(path(f.extended()));
            JsonObject a=json(old),b=json(combined);
            for(String key:List.of("asset","nodes","meshes","materials","images","textures","scenes","scene"))assertEquals(a.get(key),b.get(key),key);
            for(String key:List.of("animations","accessors","bufferViews")){
                var before=a.getAsJsonArray(key);var after=b.getAsJsonArray(key);
                for(int i=0;i<before.size();i++)assertEquals(before.get(i),after.get(i),key+" "+i);
            }
            assertEquals(5,a.getAsJsonArray("animations").size());assertEquals(8,b.getAsJsonArray("animations").size());
            byte[] bin=binary(old);assertArrayEquals(bin,Arrays.copyOf(binary(combined),bin.length));
            var p=f.approved().profile();var q=f.extended().profile();
            assertEquals(p.cameraOffset(),q.cameraOffset());assertEquals(p.cameraRotation(),q.cameraRotation());
            assertEquals(p.clips(),q.clips());assertEquals(p.rig(),q.rig());assertEquals(p.sockets(),q.sockets());
            assertTrue(f.extended().model().rig().skins().isEmpty());new VanillaArmAttachments(f.extended(),f.variant());
        }));
    }
    @TestFactory Stream<DynamicTest> approvedFiveClipsHaveExactlyTheSamePoseWithExtensionInstalled(){
        return FIXTURES.stream().flatMap(f->f.approved().profile().clips().values().stream().map(clip->DynamicTest.dynamicTest(f.variant()+" "+clip,()->{
            var a=new GltfAnimationController(f.approved().model().rig());var b=new GltfAnimationController(f.extended().model().rig());
            var extension=new FirstPersonMotionController(b);
            a.play(clip,GltfAnimationController.Mode.HOLD,0,0);b.play(clip,GltfAnimationController.Mode.HOLD,0,0);
            extension.select(Mode.BASE,0);
            for(int i=0;i<=32;i++){
                double now=f.approved().model().rig().clip(clip).duration()*i/32;
                assertArrayEquals(snapshot(a.sampleSeconds(now)),snapshot(b.sampleSeconds(now)),0);
            }
        })));
    }
    @TestFactory Stream<DynamicTest> newClipsAndInterruptedBlendsKeepBothVanillaHandsAndCameraClearance(){
        return FIXTURES.stream().map(f->DynamicTest.dynamicTest(f.variant(),()->{
            var asset=f.extended();var c=new GltfAnimationController(asset.model().rig());var ext=new FirstPersonMotionController(c);
            var grips=new GripAttachments(asset.profile(),asset.sockets());c.play("idle",GltfAnimationController.Mode.LOOP,0,0);
            double now=0;
            for(Mode mode:new Mode[]{Mode.REST,Mode.AIM,Mode.SPRINT,Mode.REST,Mode.BASE,Mode.SPRINT,Mode.BASE}){
                double[] before=snapshot(c.sampleSeconds(now));ext.select(mode,now);
                assertArrayEquals(before,snapshot(c.sampleSeconds(now)),1e-12,"action switch continuity");
                for(int i=0;i<=20;i++){
                    var pose=c.sampleSeconds(now+i*.013);assertTrue(grips.matches(pose));
                    for(int m=0;m<pose.model().meshes().size();m++)for(int v=0;v<pose.model().meshes().get(m).vertexCount();v++)
                        assertTrue(-(pose.vertex(m,v,2)+asset.profile().cameraOffset().get(2))>.075,"camera clearance");
                }
                now+=.10; // Intentionally interrupt an unfinished blend.
            }
            ext.select(Mode.BASE,1);var original=new GltfAnimationController(asset.model().rig());original.play("idle",GltfAnimationController.Mode.LOOP,0,0);
            assertArrayEquals(snapshot(original.sampleSeconds(2)),snapshot(c.sampleSeconds(2)),1e-12);
        }));
    }
    @TestFactory Stream<DynamicTest> aimHasCentredSightlineAndSprintHasSeparateLoweredRhythm(){
        return FIXTURES.stream().map(f->DynamicTest.dynamicTest(f.variant(),()->{
            var asset=f.extended();var c=new GltfAnimationController(asset.model().rig());
            c.play("aim",GltfAnimationController.Mode.HOLD,0,0);
            var aim=c.sampleSeconds(0);int axis=aim.model().node("presentation_root");
            assertEquals(0,aim.global(axis,12)+asset.profile().cameraOffset().get(0),1e-6);
            double aimHeight=aim.global(axis,13);double[] a=snapshot(aim);
            int sight=aim.model().node("gauge_forward_axis");
            for(int i=0;i<=32;i++){
                var held=c.sampleSeconds(i/8.0);
                assertEquals(0,held.global(sight,12)+asset.profile().cameraOffset().get(0),.002,"sight horizontal centre");
                assertEquals(0,held.global(sight,13)+asset.profile().cameraOffset().get(1),.002,"sight vertical centre");
            }
            c.play("sprint",GltfAnimationController.Mode.HOLD,0,0);double[] start=snapshot(c.sampleSeconds(0));
            assertFalse(Arrays.equals(a,start));assertFalse(Arrays.equals(start,snapshot(c.sampleSeconds(.17))));
            // HOLD checks the real exported seam, rather than modulo returning time zero.
            assertArrayEquals(start,snapshot(c.sampleSeconds(asset.model().rig().clip("sprint").duration())),1e-4);
            assertTrue(c.sampleSeconds(0).global(axis,13)<aimHeight-.10);
        }));
    }
    @Test void automaticPolicyLeavesCalibrationAndExclusiveApprovedActionsAlone(){
        assertEquals(Mode.REST,FirstPersonMotionController.desired(true,true,"idle",Mode.AIM,true,true,false));
        assertEquals(Mode.BASE,FirstPersonMotionController.desired(false,false,"idle",Mode.AIM,true,true,false));
        for(String exclusive:List.of("inspect","raise","lower"))assertEquals(Mode.BASE,FirstPersonMotionController.desired(true,false,exclusive,Mode.SPRINT,true,true,false));
        assertEquals(Mode.AIM,FirstPersonMotionController.desired(true,false,"idle",null,true,true,false));
        assertEquals(Mode.SPRINT,FirstPersonMotionController.desired(true,false,"move",null,false,true,true));
        assertEquals(Mode.REST,FirstPersonMotionController.desired(true,false,"idle",null,false,false,false));
        assertEquals(Mode.BASE,FirstPersonMotionController.desired(true,false,"idle",null,false,false,true));
        assertEquals(Mode.SPRINT,FirstPersonMotionController.desired(true,false,"move",Mode.SPRINT,true,false,false));
    }
    @TestFactory Stream<DynamicTest> nearArmUsesDepthAndDirectionWithoutScalingVanillaGeometry(){
        return FIXTURES.stream().map(f->DynamicTest.dynamicTest(f.variant(),()->{
            var a=f.extended();var arms=new VanillaArmAttachments(a,f.variant());var c=new GltfAnimationController(a.model().rig());
            for(String clip:List.of("rest","aim")){
                c.play(clip,GltfAnimationController.Mode.HOLD,0,0);
                for(int i=0;i<=16;i++){
                    var frame=c.sampleSeconds(i/4.0);double[] part=new double[16];
                    arms.modelPartTransform(frame,VanillaArmAttachments.Side.RIGHT,part);
                    assertTrue(part[5]>0&&Math.abs(part[6])/part[5]<.9,"Route the proximal arm below view, not towards the lens");
                    double minDepth=Double.POSITIVE_INFINITY;var projected=new ArrayList<double[]>();
                    double centre=arms.endX(VanillaArmAttachments.Side.RIGHT),d=VanillaArmAttachments.SLEEVE_DILATION,half=arms.width()/2+d;
                    for(double x:new double[]{centre-half,centre+half})for(double y:new double[]{-.125-d,.625+d})for(double z:new double[]{-.125-d,.125+d}){
                        double px=part[0]*x+part[4]*y+part[8]*z+part[12]+a.profile().cameraOffset().get(0);
                        double py=part[1]*x+part[5]*y+part[9]*z+part[13]+a.profile().cameraOffset().get(1);
                        double pz=part[2]*x+part[6]*y+part[10]*z+part[14]+a.profile().cameraOffset().get(2);
                        double depth=-pz;minDepth=Math.min(minDepth,depth);assertTrue(depth>.075);
                        double ny=py/(depth*Math.tan(Math.toRadians(35))),nx=px/(depth*Math.tan(Math.toRadians(35))*16/9);
                        projected.add(new double[]{nx,ny});
                    }
                    double area=viewportCoverage(projected);
                    assertTrue(area<.14,"Right arm/sleeve takes too much of the viewport in "+clip+": "+area);
                    if(clip.equals("aim"))assertTrue(minDepth>.24,"ADS must not put the proximal skin against the lens: "+minDepth);
                    for(int col=0;col<3;col++)assertEquals(1,Math.sqrt(part[col*4]*part[col*4]+part[col*4+1]*part[col*4+1]+part[col*4+2]*part[col*4+2]),2e-6,"No arm scaling");
                    for(var side:VanillaArmAttachments.Side.values()){
                        double[] target=new double[16],nativePart=new double[16],inverse=new double[16];
                        a.sockets().sample(side==VanillaArmAttachments.Side.RIGHT?"grip_primary":"grip_secondary",frame,target);
                        arms.modelPartTransform(frame,side,nativePart);RigMath.inverseAffine(nativePart,0,inverse,0);
                        double[] contact=new double[3];
                        for(int k=0;k<3;k++)contact[k]=inverse[k]*target[12]+inverse[4+k]*target[13]+inverse[8+k]*target[14]+inverse[12+k];
                        assertEquals(.625,contact[1],3e-5,"Real native hand end stays on its contact plane");
                        assertTrue(Math.abs(contact[0]-arms.endX(side))<=arms.width()/2+3e-5);
                        assertTrue(Math.abs(contact[2])<=.125+3e-5);
                    }
                }
            }
        }));
    }
    @TestFactory Stream<DynamicTest> staticRestWorksAtTimeZeroAndWalkingReturnsToTheExactOriginalLayer(){
        return FIXTURES.stream().map(f->DynamicTest.dynamicTest(f.variant(),()->{
            var rig=f.extended().model().rig();var c=new GltfAnimationController(rig);var ext=new FirstPersonMotionController(c);
            c.play("idle",GltfAnimationController.Mode.HOLD,0,0);ext.calibrated(true);
            var rest=new GltfAnimationController(rig);rest.play("rest",GltfAnimationController.Mode.HOLD,0,0);
            assertArrayEquals(snapshot(rest.sampleSeconds(0)),snapshot(c.sampleSeconds(0)),1e-12);
            ext.select(FirstPersonMotionController.desired(true,false,"idle",null,false,false,true),10);
            c.layer(1,List.of(new GltfAnimationController.ClipRequest("move",GltfAnimationController.Mode.LOOP,1)),NodeMask.all(rig),true,1,10,0);
            var walk=new GltfAnimationController(rig);walk.play("idle",GltfAnimationController.Mode.HOLD,0,0);
            walk.layer(1,List.of(new GltfAnimationController.ClipRequest("move",GltfAnimationController.Mode.LOOP,1)),NodeMask.all(rig),true,1,10,0);
            assertArrayEquals(snapshot(walk.sampleSeconds(11)),snapshot(c.sampleSeconds(11)),1e-12,"The walking clip and native hand poses are the existing ones");
            c.reset();c.play("idle",GltfAnimationController.Mode.HOLD,0,0);ext.calibrated(false);
            var original=new GltfAnimationController(rig);original.play("idle",GltfAnimationController.Mode.HOLD,0,0);
            assertArrayEquals(snapshot(original.sampleSeconds(0)),snapshot(c.sampleSeconds(0)),1e-12);
        }));
    }
    @Test void approvedFirstPersonAssetsAndSourcesRemainByteIdentical()throws Exception{
        var baseline=JsonParser.parseString(Files.readString(Path.of("docs/armament/task5-2-2/validation/protected-baseline.json"))).getAsJsonObject();
        for(var e:baseline.getAsJsonObject("sha256").entrySet()){
            // The user explicitly rejected the old 3P poses and requested reauthoring.
            if(e.getKey().contains("/debug/third_person_fixture"))continue;
            String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(e.getKey()))));
            assertEquals(e.getValue().getAsString(),hash,e.getKey());
        }
    }
    private static Path path(FirstPersonAsset a){var id=a.profile().model();return ASSETS.resolve(id.namespace()).resolve(id.path());}
    private static JsonObject json(byte[] b){int n=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(12);return JsonParser.parseString(new String(b,20,n,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static byte[] binary(byte[] b){int json=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(12);return Arrays.copyOfRange(b,28+json,b.length);}
    private static double[] snapshot(RigFrame p){
        var out=new ArrayList<Double>();for(int n=0;n<p.model().nodes().size();n++)for(int k=0;k<16;k++)out.add(p.global(n,k));
        for(int m=0;m<p.model().meshes().size();m++)for(int v=0;v<p.model().meshes().get(m).vertexCount();v++)for(int k=0;k<3;k++)out.add(p.vertex(m,v,k));
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }
    /** Projected convex cuboid silhouette, clipped to the viewport; a bounding
     * rectangle would count large empty triangles near the bottom edge. */
    private static double viewportCoverage(List<double[]> points){
        points.sort(Comparator.<double[]>comparingDouble(p->p[0]).thenComparingDouble(p->p[1]));
        var lower=new ArrayList<double[]>();var upper=new ArrayList<double[]>();
        for(var p:points){while(lower.size()>1&&cross2(lower.get(lower.size()-2),lower.get(lower.size()-1),p)<=0)lower.remove(lower.size()-1);lower.add(p);}
        for(int i=points.size()-1;i>=0;i--){var p=points.get(i);while(upper.size()>1&&cross2(upper.get(upper.size()-2),upper.get(upper.size()-1),p)<=0)upper.remove(upper.size()-1);upper.add(p);}
        lower.remove(lower.size()-1);upper.remove(upper.size()-1);lower.addAll(upper);List<double[]> polygon=lower;
        for(int axis=0;axis<2;axis++)for(int sign:new int[]{1,-1}){
            double bound=-sign;var clipped=new ArrayList<double[]>();
            for(int i=0;i<polygon.size();i++){
                var a=polygon.get((i+polygon.size()-1)%polygon.size());var b=polygon.get(i);
                boolean ai=sign*(a[axis]-bound)>=0,bi=sign*(b[axis]-bound)>=0;
                if(ai!=bi){double t=(bound-a[axis])/(b[axis]-a[axis]);clipped.add(new double[]{a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])});}
                if(bi)clipped.add(b);
            }
            polygon=clipped;
        }
        double twice=0;for(int i=0;i<polygon.size();i++){var a=polygon.get(i);var b=polygon.get((i+1)%polygon.size());twice+=a[0]*b[1]-a[1]*b[0];}
        return Math.abs(twice)/8;
    }
    private static double cross2(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
}
