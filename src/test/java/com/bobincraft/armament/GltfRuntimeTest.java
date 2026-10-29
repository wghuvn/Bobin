package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.resource.AnimationEventsDefinition;
import com.bobincraft.armament.client.resource.WeaponRigDefinition;
import com.bobincraft.armament.client.resource.WeaponSocketsDefinition;
import com.bobincraft.armament.client.resource.TransformDefinition;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.bobincraft.armament.client.animation.gltf.GltfAnimationController.*;

final class GltfRuntimeTest {
    static GltfAnimationController controller(){return new GltfAnimationController(GltfMathTest.model());}
    static AnimationEventsDefinition events(){return new AnimationEventsDefinition(1,WeaponId.parse("test:markers"),List.of(
        new AnimationEventsDefinition.Clip("ramp",1,List.of(new AnimationEventsDefinition.Marker("start",0,WeaponId.parse("test:presentation")),
            new AnimationEventsDefinition.Marker("contact",0.5,WeaponId.parse("test:contact")),new AnimationEventsDefinition.Marker("end",1,WeaponId.parse("test:presentation"))))));}

    @Test void playStopFadeAndStates(){
        var c=controller();assertEquals(State.IDLE,c.state(0));c.play("a",Mode.LOOP,0,1);assertEquals(State.TRANSITION,c.state(0.5));
        assertEquals(1,c.sampleSeconds(0.5).local(0,0),1e-12);assertEquals(State.PLAYING,c.state(1));
        c.stop(1,1);assertEquals(2,c.sampleSeconds(1).local(0,0));assertEquals(1,c.sampleSeconds(1.5).local(0,0));
        assertEquals(0,c.sampleSeconds(2).local(0,0));assertEquals(State.IDLE,c.state(2));
    }
    @Test void interruptedCrossfadeStartsAtCurrentPose(){
        var c=controller();c.play("a",Mode.LOOP,0,1);double before=c.sampleSeconds(0.5).local(0,0);
        c.play("b",Mode.LOOP,0.5,1);assertEquals(before,c.sampleSeconds(0.5).local(0,0),1e-12);
        double middle=c.sampleSeconds(0.75).local(0,0);assertEquals(2.25,middle,1e-12);
        c.play("a",Mode.LOOP,0.75,1);assertEquals(middle,c.sampleSeconds(0.75).local(0,0),1e-12);
    }
    @Test void ordinaryCrossfadeAdvancesOutgoingAndIncomingClips(){
        var c=controller();c.play("ramp",Mode.HOLD,0,0);c.play("b",Mode.HOLD,0.25,1);
        assertEquals(2.25,c.sampleSeconds(0.5).local(0,0),1e-12,"outgoing ramp must advance to 1 before 25% blend with 6");
    }
    @Test void independentInstancesShareOnlyImmutableAsset(){
        var model=GltfMathTest.model();var a=new GltfAnimationController(model);var b=new GltfAnimationController(model);
        a.play("a",Mode.HOLD,0,0);b.play("b",Mode.HOLD,0,0);
        var af=a.sampleSeconds(1);var bf=b.sampleSeconds(1);assertNotSame(af,bf);assertEquals(2,af.local(0,0));assertEquals(6,bf.local(0,0));b.reset();assertEquals(2,af.local(0,0));
    }
    @Test void weightedClipsKeepBindRemainder(){
        var c=controller();c.layer(0,List.of(new ClipRequest("a",Mode.HOLD,0.25),new ClipRequest("b",Mode.HOLD,0.25)),NodeMask.all(c.model()),false,1,0,0);
        assertEquals(2,c.sampleSeconds(1).local(0,0),1e-12);assertEquals(3,c.sampleSeconds(1).local(1,0),1e-12);
    }
    @Test void nodeMasksAndIndependentLayers(){
        var c=controller();c.play("a",Mode.HOLD,0,0);
        c.layer(1,List.of(new ClipRequest("b",Mode.HOLD,1)),NodeMask.subtree(c.model(),"child"),false,1,0,0);
        var f=c.sampleSeconds(1);assertEquals(2,f.local(0,0));assertEquals(7,f.local(1,0));assertEquals(9,f.global(1,12));
    }
    @Test void additiveTranslationUsesBindReference(){
        var c=controller();c.play("a",Mode.HOLD,0,0);c.layer(1,List.of(new ClipRequest("b",Mode.HOLD,1)),NodeMask.all(c.model()),true,0.5,0,0);
        var f=c.sampleSeconds(1);assertEquals(5,f.local(0,0));assertEquals(6,f.local(1,0));
    }
    @Test void onceReleasesToBindAndHoldKeepsLastKey(){
        var c=controller();c.play("ramp",Mode.ONCE,0,0.2);assertEquals(2,c.sampleSeconds(1).local(0,0),1e-12);
        assertEquals(1,c.sampleSeconds(1.1).local(0,0),1e-12);assertEquals(State.TRANSITION,c.state(1.1));assertEquals(0,c.sampleSeconds(1.2).local(0,0),1e-12);
        assertEquals(State.IDLE,c.state(1.21));c.play("ramp",Mode.HOLD,2,0);assertEquals(2,c.sampleSeconds(10).local(0,0));
    }
    @Test void loopUsesSimulationTimeAndInterpolation(){
        var c=controller();c.play("ramp",Mode.LOOP,0,0);assertEquals(0.5,c.sampleSeconds(1000.25).local(0,0),1e-12);
        assertEquals(0.05,c.sample(0,0.5f).local(0,0),1e-12);assertThrows(IllegalArgumentException.class,()->c.sample(0,1));
    }
    @Test void repeatedSamplesReuseFrameAndNeverAccumulateTransforms(){
        var c=controller();c.play("ramp",Mode.LOOP,0,0);var frame=c.sampleSeconds(0.25);long count=frame.evaluations();
        assertSame(frame,c.sampleSeconds(0.25));assertEquals(count,frame.evaluations());
        for(int i=1;i<=2000;i++)assertEquals(0.5,c.sampleSeconds(i+0.25).local(0,0),1e-12);
    }
    @Test void markersAreIndependentOfFpsAndDuplicatePasses(){
        var c=controller();c.markers(events());c.play("ramp",Mode.LOOP,0,0);var occurrences=new ArrayList<CosmeticEvent>();
        for(int tick=0;tick<=20;tick++){for(int frame=0;frame<7;frame++)c.sample(tick,frame/8f);c.advance(tick,occurrences::add);c.advance(tick,occurrences::add);}
        assertEquals(List.of("start","contact","end","start"),occurrences.stream().map(CosmeticEvent::marker).toList());
        assertEquals(4,occurrences.stream().map(e->e.playback()+":"+e.marker()+":"+e.loop()).distinct().count());
    }
    @Test void seekingBackOrForwardDoesNotBurstOrRepeatMarkers(){
        var c=controller();c.markers(events());c.play("ramp",Mode.LOOP,0,0);var e=new ArrayList<CosmeticEvent>();c.advance(15,e::add);assertEquals(2,e.size());
        c.seek(0,0,0.75);c.advance(25,e::add);assertEquals(2,e.size());
        c.seek(0,5,1.25);c.advance(25,e::add);assertEquals(2,e.size());c.advance(35,e::add);assertEquals(3,e.size());assertEquals(5,e.get(2).loop());
    }
    @Test void transitionsCancelOutgoingMarkerCursors(){
        var c=controller();c.markers(events());c.play("ramp",Mode.LOOP,0,0);var e=new ArrayList<CosmeticEvent>();c.advance(0,e::add);
        c.play("a",Mode.LOOP,0.25,1);c.advance(20,e::add);assertEquals(1,e.size());
    }
    @Test void longTickGapsHaveBoundedCosmeticCatchup(){
        var c=controller();c.markers(events());c.play("ramp",Mode.LOOP,0,0);var e=new ArrayList<CosmeticEvent>();c.advance(20_000,e::add);
        assertTrue(e.size()<=96);assertTrue(c.droppedEvents()>0);int count=e.size();c.advance(20_000,e::add);assertEquals(count,e.size());
    }
    @Test void invalidBindingsAndCommandsFailClearly(){
        var c=controller();assertThrows(IllegalArgumentException.class,()->c.play("missing",Mode.HOLD,0,0));
        assertThrows(IllegalArgumentException.class,()->c.layer(8,List.of(),NodeMask.all(c.model()),false,1,0,0));
        assertThrows(IllegalArgumentException.class,()->c.play("a",Mode.HOLD,Double.NaN,0));
        c.play("a",Mode.LOOP,0,0);assertThrows(IllegalStateException.class,()->c.markers(events()));
    }
    @Test void exceptionalMarkerConsumerCannotReplayAlreadyConsumedInterval(){
        var c=controller();c.markers(events());c.play("ramp",Mode.LOOP,0,0);
        assertThrows(IllegalStateException.class,()->c.advance(10,event->{throw new IllegalStateException("consumer failure");}));
        var e=new ArrayList<CosmeticEvent>();c.advance(10,e::add);assertTrue(e.isEmpty());
    }
    @Test void changingMasksDuringCrossfadePreservesContinuity(){
        var c=controller();c.play("a",Mode.HOLD,0,0);c.layer(1,List.of(new ClipRequest("b",Mode.HOLD,1)),NodeMask.subtree(c.model(),"child"),false,1,0,0);
        double root=c.sampleSeconds(0.5).local(0,0),child=c.sampleSeconds(0.5).local(1,0);
        c.layer(1,List.of(new ClipRequest("b",Mode.HOLD,1)),NodeMask.all(c.model()),false,1,0.5,1);
        assertEquals(root,c.sampleSeconds(0.5).local(0,0));assertEquals(child,c.sampleSeconds(0.5).local(1,0));assertEquals(4,c.sampleSeconds(1).local(0,0));
    }
    @Test void additiveQuaternionAndScaleUseBindRelativeDeltas(){
        double half=Math.PI/8;double[] bind={0,0,0,0,0,Math.sin(half),Math.cos(half),2,2,2};
        var rotation=new AnimationClip.Channel(0,AnimationClip.Path.ROTATION,AnimationClip.Interpolation.LINEAR,new double[]{0,1},
            new double[]{0,0,Math.sin(2*half),Math.cos(2*half),0,0,Math.sin(2*half),Math.cos(2*half)});
        var scale=new AnimationClip.Channel(0,AnimationClip.Path.SCALE,AnimationClip.Interpolation.LINEAR,new double[]{0,1},new double[]{4,4,4,4,4,4});
        var model=new RigModel(List.of(new RigModel.Node("root",-1,bind,null)),List.of(),List.of(),List.of(new AnimationClip("delta",List.of(rotation,scale))));
        var c=new GltfAnimationController(model);c.layer(0,List.of(new ClipRequest("delta",Mode.HOLD,1)),NodeMask.all(model),true,0.5,0,0);var frame=c.sampleSeconds(1);
        assertEquals(Math.sin(1.5*half),frame.local(0,5),1e-12);assertEquals(Math.cos(1.5*half),frame.local(0,6),1e-12);assertEquals(3,frame.local(0,7),1e-12);
    }
    @Test void zeroCoverageLayerProducesNeitherPoseNorEvents(){
        var c=controller();c.markers(events());c.layer(0,List.of(new ClipRequest("ramp",Mode.LOOP,1)),new NodeMask(new double[2]),false,1,0,0);
        var events=new ArrayList<CosmeticEvent>();c.advance(20,events::add);assertTrue(events.isEmpty());assertEquals(State.IDLE,c.state(1));assertEquals(0,c.sampleSeconds(0.5).local(0,0));
    }
    @Test void socketMetadataAliasesResolveByNodeIndex(){
        var model=new RigModel(List.of(new RigModel.Node("Blender.Root",-1,GltfMathTest.bind(0),null),new RigModel.Node("Bone.001",0,GltfMathTest.bind(1),null)),List.of(),List.of(),List.of());
        var id=WeaponId.parse("test:rig");var rig=new WeaponRigDefinition(1,id,List.of(new WeaponRigDefinition.Node("root",0,Optional.empty()),new WeaponRigDefinition.Node("joint",1,Optional.of("root"))),Map.of("weapon_root","root"));
        var socket=new WeaponSocketsDefinition(1,WeaponId.parse("test:sockets"),id,List.of(new WeaponSocketsDefinition.Socket("debug","joint",new TransformDefinition(List.of(0.5,0.0,0.0),List.of(0.0,0.0,0.0,1.0),List.of(1.0,1.0,1.0)))));
        var bindings=new RigSockets(model,rig,socket);double[] matrix=new double[16];bindings.sample("debug",new RigFrame(model),matrix);assertEquals(1.5,matrix[12]);
        assertThrows(IllegalArgumentException.class,()->bindings.sample("missing",new RigFrame(model),matrix));
        var other=new WeaponSocketsDefinition(1,WeaponId.parse("test:sockets"),WeaponId.parse("test:other"),socket.sockets());assertThrows(IllegalArgumentException.class,()->new RigSockets(model,rig,other));
    }
}
