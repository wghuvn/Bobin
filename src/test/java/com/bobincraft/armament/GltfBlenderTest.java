package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.model.glb.*;
import com.bobincraft.armament.client.resource.WeaponVisualJson;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

final class GltfBlenderTest {
    static final Path ASSETS=Path.of("src/main/resources/assets/bobin_craft/armament/debug");
    static GlbModelAsset asset;static JsonObject reference;
    @BeforeAll static void load()throws Exception{
        asset=new JgltfModelLoader().load(WeaponId.parse("test:animated.glb"),id->Files.newInputStream(ASSETS.resolve("animation_fixture.glb")));
        reference=JsonParser.parseString(Files.readString(Path.of("art/armament/animation_fixture.reference.json"))).getAsJsonObject();
    }
    @Test void realRigAndAllInterpolationModesAreImported(){
        assertNotNull(asset.rig());assertEquals(5,asset.rig().clips().size());assertEquals(3,asset.rig().skins().get(0).size());assertEquals(4,asset.rig().meshes().size());
        var modes=asset.rig().clips().stream().flatMap(c->c.channels().stream()).map(AnimationClip.Channel::interpolation).collect(Collectors.toSet());
        assertEquals(Set.of(AnimationClip.Interpolation.LINEAR,AnimationClip.Interpolation.STEP,AnimationClip.Interpolation.CUBICSPLINE),modes);
    }
    @TestFactory Stream<DynamicTest> everyExportedPoseMatchesIndependentBlenderEvaluation(){
        return reference.getAsJsonObject("clips").entrySet().stream().flatMap(entry->StreamSupport.stream(entry.getValue().getAsJsonArray().spliterator(),false)
            .map(value->DynamicTest.dynamicTest(entry.getKey()+"@"+value.getAsJsonObject().get("seconds"),()->{
                var expected=value.getAsJsonObject();double seconds=expected.get("seconds").getAsDouble();var rig=asset.rig();
                var c=new GltfAnimationController(rig);c.play(entry.getKey(),GltfAnimationController.Mode.HOLD,0,0);var frame=c.sampleSeconds(seconds);
                for(int m=0;m<rig.meshes().size();m++){
                    var mesh=rig.meshes().get(m);String name=rig.nodes().get(mesh.node()).name();JsonArray points=expected.getAsJsonObject("vertices").getAsJsonArray(name);
                    for(int v=0;v<mesh.vertexCount();v++){
                        double best=Double.POSITIVE_INFINITY;
                        for(JsonElement point:points){double error=0;for(int k=0;k<3;k++)error=Math.max(error,Math.abs(point.getAsJsonArray().get(k).getAsDouble()-frame.vertex(m,v,k)));best=Math.min(best,error);}
                        assertTrue(best<6e-5,name+" vertex "+v+" error="+best);
                        double norm=0;for(int k=3;k<6;k++)norm+=frame.vertex(m,v,k)*frame.vertex(m,v,k);assertEquals(1,norm,1e-9);
                    }
                    if(mesh.skin()>=0){var skin=rig.skins().get(mesh.skin());for(int j=0;j<skin.size();j++){
                        String joint=rig.nodes().get(skin.joint(j)).name();JsonArray matrix=expected.getAsJsonObject("joint_matrices").getAsJsonArray(joint);
                        for(int k=0;k<16;k++)assertEquals(matrix.get(k).getAsDouble(),frame.jointWorldMatrix(m,j,k),6e-5,name+" joint "+joint+" component "+k);
                    }}
                }
                for(String name:List.of("fixture_root","rotor","rotor_pointer","slider","socket_debug")){
                    int node=rig.node(name);JsonArray matrix=expected.getAsJsonObject("nodes").getAsJsonArray(name);
                    for(int k=0;k<16;k++)assertEquals(matrix.get(k).getAsDouble(),frame.global(node,k),6e-5,name+" matrix "+k);
                }
                var sockets=new RigSockets(rig,WeaponVisualJson.readSockets(Files.readString(ASSETS.resolve("animation_fixture.sockets.json"))));
                double[] socket=new double[16];sockets.sample("debug",frame,socket);
                JsonArray matrix=expected.getAsJsonObject("nodes").getAsJsonArray("socket_debug");for(int k=0;k<16;k++)assertEquals(matrix.get(k).getAsDouble(),socket[k],6e-5);
            })));
    }
    @Test void sidecarsValidateAgainstActualRigAndClipDurations()throws Exception{
        var rig=WeaponVisualJson.readRig(Files.readString(ASSETS.resolve("animation_fixture.rig.json")));
        for(var node:rig.nodes())assertEquals(node.name(),asset.rig().nodes().get(node.nodeIndex()).name());
        var controller=new GltfAnimationController(asset.rig());controller.markers(WeaponVisualJson.readEvents(Files.readString(ASSETS.resolve("animation_fixture.events.json"))));
    }
    @Test void steadyStateSamplingHasNoPerFrameHeapAllocation(){
        var controller=new GltfAnimationController(asset.rig());controller.play("combo",GltfAnimationController.Mode.LOOP,0,0);
        for(int i=0;i<5000;i++)controller.sampleSeconds(i/120.0);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
        for(int i=0;i<2000;i++)controller.sampleSeconds(100+i/120.0);
        long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-before;
        System.out.println("animation_runtime_benchmark samples=2000 bytes="+bytes+" ns_per_sample="+(nanos/2000));
        assertEquals(0,bytes,"pose sampling and skinning must reuse their buffers after warmup");
    }
}
