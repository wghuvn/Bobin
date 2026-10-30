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
            assertEquals(5,a.getAsJsonArray("animations").size());assertEquals(7,b.getAsJsonArray("animations").size());
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
            for(Mode mode:new Mode[]{Mode.AIM,Mode.SPRINT,Mode.AIM,Mode.BASE,Mode.SPRINT,Mode.BASE}){
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
        assertEquals(Mode.BASE,FirstPersonMotionController.desired(true,true,"idle",Mode.AIM,true,true));
        assertEquals(Mode.BASE,FirstPersonMotionController.desired(false,false,"idle",Mode.AIM,true,true));
        for(String exclusive:List.of("inspect","raise","lower"))assertEquals(Mode.BASE,FirstPersonMotionController.desired(true,false,exclusive,Mode.SPRINT,true,true));
        assertEquals(Mode.AIM,FirstPersonMotionController.desired(true,false,"idle",null,true,true));
        assertEquals(Mode.SPRINT,FirstPersonMotionController.desired(true,false,"move",null,false,true));
        assertEquals(Mode.BASE,FirstPersonMotionController.desired(true,false,"idle",null,false,false));
        assertEquals(Mode.SPRINT,FirstPersonMotionController.desired(true,false,"move",Mode.SPRINT,true,false));
    }
    @Test void allApprovedFirstPersonAndThirdPersonAssetsRemainByteIdentical()throws Exception{
        var baseline=JsonParser.parseString(Files.readString(Path.of("docs/armament/task5-2-2/validation/protected-baseline.json"))).getAsJsonObject();
        for(var e:baseline.getAsJsonObject("sha256").entrySet()){
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
}
