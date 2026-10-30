package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.presentation.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real exported rigs, rather than a synthetic translation-only crossfade.
 * Existing Blender golden tests independently check every clip's skinned positions.
 */
final class FirstPersonAnimationStabilityTest {
    private static final List<String> CLIPS=List.of("idle","inspect","raise","lower","move");
    private static final Map<String,FirstPersonAsset> ASSETS=new LinkedHashMap<>();

    @BeforeAll static void load()throws Exception{
        for(String variant:List.of("default","slim")){
            String stem="first_person_fixture"+(variant.equals("slim")?"_slim":"");
            ASSETS.put(variant,FirstPersonAsset.load(WeaponId.parse("bobin_craft:armament/debug/"+stem+".profile.json"),
                id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path()))));
        }
    }

    @TestFactory Stream<DynamicTest> everyAuthoredClipTransitionIsContinuousAndKeepsBothGrips(){
        return ASSETS.entrySet().stream().flatMap(entry->CLIPS.stream().flatMap(from->CLIPS.stream().map(to->
            DynamicTest.dynamicTest(entry.getKey()+" "+from+" -> "+to,()->{
                var asset=entry.getValue();var controller=new GltfAnimationController(asset.model().rig());
                var contacts=new GripAttachments(asset.profile(),asset.sockets());
                controller.play(from,GltfAnimationController.Mode.HOLD,0,0);
                double now=asset.model().rig().clip(from).duration()*.375;
                double[] before=positions(controller.sampleSeconds(now));
                controller.play(to,GltfAnimationController.Mode.HOLD,now,asset.profile().transitionSeconds());
                assertArrayEquals(before,positions(controller.sampleSeconds(now)),1e-12,"switch must not snap a single mesh vertex");
                for(int sample=0;sample<=16;sample++){
                    var pose=controller.sampleSeconds(now+asset.profile().transitionSeconds()*sample/16.0);
                    assertTrue(contacts.matches(pose),"blend detaches: "+contacts.positionError()+" m / "+contacts.angleError()+" degrees");
                    assertHealthyShadingAndAtlas(pose);
                }
                var destination=new GltfAnimationController(asset.model().rig());
                destination.play(to,GltfAnimationController.Mode.HOLD,now,0);
                double completed=now+asset.profile().transitionSeconds()+1e-9;
                assertArrayEquals(positions(destination.sampleSeconds(completed)),positions(controller.sampleSeconds(completed)),1e-12,
                    "completed blend must converge to the advancing destination clip");
            }))));
    }

    @TestFactory Stream<DynamicTest> intermediateRenderSamplesNeverChangeAnimationOrInterruptedCrossfadeResult(){
        return ASSETS.entrySet().stream().flatMap(entry->Stream.of(30,60,144).map(fps->
            DynamicTest.dynamicTest(entry.getKey()+" at "+fps+" FPS",()->{
                var rig=entry.getValue().model().rig();var dense=new GltfAnimationController(rig);var sparse=new GltfAnimationController(rig);
                dense.play("idle",GltfAnimationController.Mode.LOOP,0,0);sparse.play("idle",GltfAnimationController.Mode.LOOP,0,0);
                // Exact command times belong to the simulation clock, independent of render rate.
                double[] times={.20,.28,.34,.80,1.10,1.80};
                String[] actions={"inspect","move","inspect","lower","raise","idle"};
                double previous=0;
                for(int step=0;step<times.length;step++){
                    double now=times[step];
                    for(int frame=(int)Math.floor(previous*fps)+1;frame<Math.ceil(now*fps);frame++)dense.sampleSeconds(frame/(double)fps);
                    assertArrayEquals(positions(sparse.sampleSeconds(now)),positions(dense.sampleSeconds(now)),1e-12,"render history changed outgoing pose");
                    dense.play(actions[step],GltfAnimationController.Mode.HOLD,now,.18);
                    sparse.play(actions[step],GltfAnimationController.Mode.HOLD,now,.18);
                    assertArrayEquals(positions(sparse.sampleSeconds(now)),positions(dense.sampleSeconds(now)),1e-12,"interrupted blend depends on FPS");
                    previous=now;
                }
                for(double end:new double[]{1.83,1.91,2.0,2.5}){
                    for(int frame=(int)Math.floor(previous*fps)+1;frame<Math.ceil(end*fps);frame++)dense.sampleSeconds(frame/(double)fps);
                    var expected=sparse.sampleSeconds(end);var actual=dense.sampleSeconds(end);
                    assertArrayEquals(positions(expected),positions(actual),1e-12,"final geometry depends on FPS");
                    assertEquals(sparse.state(end),dense.state(end));
                    previous=end;
                }
            })));
    }

    @TestFactory Stream<DynamicTest> sampledClipsKeepNormalizedNormalsAndOriginalSkinUvs(){
        return ASSETS.entrySet().stream().flatMap(entry->CLIPS.stream().map(clip->
            DynamicTest.dynamicTest(entry.getKey()+" shading/UV "+clip,()->{
                var rig=entry.getValue().model().rig();var controller=new GltfAnimationController(rig);
                controller.play(clip,GltfAnimationController.Mode.HOLD,0,0);
                for(int i=0;i<=16;i++)assertHealthyShadingAndAtlas(controller.sampleSeconds(rig.clip(clip).duration()*i/16));
            })));
    }

    private static void assertHealthyShadingAndAtlas(RigFrame pose){
        double worstNormalError=0,maxUvError=0;
        for(int m=0;m<pose.model().meshes().size();m++){
            var mesh=pose.model().meshes().get(m);
            assertFalse(pose.reflected(m),"holding clips must not invert mesh winding");
            for(int v=0;v<mesh.vertexCount();v++){
                double length=0;for(int k=3;k<6;k++){double value=pose.vertex(m,v,k);assertTrue(Double.isFinite(value));length+=value*value;}
                worstNormalError=Math.max(worstNormalError,Math.abs(length-1));
                for(int k=6;k<8;k++)maxUvError=Math.max(maxUvError,Math.abs(pose.vertex(m,v,k)-mesh.vertex(v,k)));
            }
        }
        assertEquals(0,worstNormalError,2e-12,"skinning must keep normals normalized for lighting");
        assertEquals(0,maxUvError,0,"animation must not alter the authored skin/sleeve atlas coordinates");
    }

    private static double[] positions(RigFrame pose){
        int size=0;for(var mesh:pose.model().meshes())size+=mesh.vertexCount()*3;
        double[] result=new double[size];int at=0;
        for(int m=0;m<pose.model().meshes().size();m++)for(int v=0;v<pose.model().meshes().get(m).vertexCount();v++)for(int k=0;k<3;k++)result[at++]=pose.vertex(m,v,k);
        return result;
    }
}
