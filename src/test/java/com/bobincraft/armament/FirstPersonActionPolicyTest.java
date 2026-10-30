package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.presentation.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real-rig action ownership, tick completion and calibration preference regressions. */
final class FirstPersonActionPolicyTest {
    private static final Map<String,FirstPersonAsset> assets=new LinkedHashMap<>();
    @BeforeAll static void load()throws Exception{
        for(String suffix:List.of("","_slim")){
            String stem="first_person_fixture"+suffix;
            assets.put(stem,FirstPersonAsset.load(WeaponId.parse("bobin_craft:armament/debug/"+stem+".profile.json"),
                id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path()))));
        }
    }
    @TestFactory Stream<DynamicTest> inertiaPreferenceSurvivesStaticCalibrationWithoutMovingIt(){
        return assets.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var p=new FirstPersonWeaponPresenter(e.getValue());p.show(0);p.inertia(true);
            assertTrue(p.inertiaRequested());assertFalse(p.inertiaEnabled());
            sample(p,0);double[] rest=positions(p.pose());p.tick(10,80,30,true);sample(p,.5);
            assertArrayEquals(rest,positions(p.pose()),0,"static calibration must remain frozen");
            p.animation("idle",.5);assertTrue(p.inertiaEnabled(),"enable requested during calibration must apply to animation");
            p.calibration();assertTrue(p.inertiaRequested());assertFalse(p.inertiaEnabled());
            p.animation("idle",1);assertTrue(p.inertiaEnabled());
            p.calibration();p.inertia(false);p.animation("idle",2);assertFalse(p.inertiaEnabled());assertFalse(p.inertiaRequested());
        }));
    }
    @TestFactory Stream<DynamicTest> actionClipsOwnTheAssemblyAfterLocomotionCrossfadesOut(){
        return assets.entrySet().stream().flatMap(e->Stream.of("inspect","raise","lower").map(action->DynamicTest.dynamicTest(e.getKey()+" "+action,()->{
            var moving=new FirstPersonWeaponPresenter(e.getValue());var still=new FirstPersonWeaponPresenter(e.getValue());
            moving.animation("idle",0);still.animation("idle",0);moving.tick(0,0,0,true);still.tick(0,0,0,false);
            moving.tick(10,0,0,true);still.tick(10,0,0,false);assertTrue(moving.movementLayerEnabled());
            sample(moving,.5);double[] before=positions(moving.pose());
            moving.animation(action,.5);still.animation(action,.5);
            assertFalse(moving.movementLayerEnabled());sample(moving,.5);
            assertArrayEquals(before,positions(moving.pose()),1e-12,"exclusive action must fade out gait without a snap");
            double after=.5+e.getValue().profile().transitionSeconds()+.1;
            sample(moving,after);sample(still,after);
            assertArrayEquals(positions(still.pose()),positions(moving.pose()),1e-12,"walking must not keep bobbing an exclusive action");
        })));
    }
    @TestFactory Stream<DynamicTest> completionRestoresLatestMovementInputAndLowerHoldsUntilRaised(){
        return assets.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var asset=e.getValue();var p=new FirstPersonWeaponPresenter(asset);
            for(String action:List.of("inspect","raise")){
                p.calibration();p.animation(action,0);
                long end=(long)Math.ceil(asset.model().rig().clip(asset.profile().clips().get(action)).duration()*20);
                p.tick(Math.max(0,end-1),0,0,true);assertEquals(action,p.action());assertFalse(p.movementLayerEnabled());
                p.tick(end,0,0,true);assertEquals("idle",p.action());assertTrue(p.movementLayerEnabled(),"latest walking input must resume automatically");
                p.tick(end+1,0,0,false);assertFalse(p.movementLayerEnabled());
            }
            p.calibration();p.animation("lower",0);long held=(long)Math.ceil(asset.model().rig().clip(asset.profile().clips().get("lower")).duration()*20)+40;
            p.tick(held,0,0,true);assertEquals("lower",p.action());assertFalse(p.movementLayerEnabled());
            assertEquals(FirstPersonActionStateMachine.Phase.HOLDING,p.phase());
            sample(p,held/20.0);double[] lowered=positions(p.pose());p.animation("raise",held/20.0);sample(p,held/20.0);
            assertArrayEquals(lowered,positions(p.pose()),1e-12,"raising from lowered hold must not teleport the assembly");
        }));
    }
    @TestFactory Stream<DynamicTest> rememberedInertiaActuallyAffectsTheViewAndRepeatedEnableDoesNotResetIt(){
        return assets.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var p=new FirstPersonWeaponPresenter(e.getValue());var still=new FirstPersonWeaponPresenter(e.getValue());
            p.show(0);p.inertia(true);p.animation("idle",0);still.animation("idle",0);
            p.tick(0,0,0,false);still.tick(0,0,0,false);
            p.tick(1,15,3,false);still.tick(1,15,3,false);
            sample(p,.075);sample(still,.075);
            float[] withLag=new float[16],withoutLag=new float[16],afterEnable=new float[16];
            p.copyProjection(withLag);still.copyProjection(withoutLag);
            assertFalse(Arrays.equals(withLag,withoutLag),"remembered setting must change the camera transform, not just a flag");
            p.inertia(true);sample(p,.075);p.copyProjection(afterEnable);
            assertArrayEquals(withLag,afterEnable,0,"repeating the enabled preference must not reset an active spring");
        }));
    }
    @TestFactory Stream<DynamicTest> explicitMoveNeverAddsAnotherCopyOfItsGait(){
        return assets.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var asset=e.getValue();var p=new FirstPersonWeaponPresenter(asset);p.animation("move",0);p.tick(0,0,0,true);
            assertFalse(p.movementLayerEnabled());double now=Math.max(asset.profile().transitionSeconds()+.05,asset.model().rig().clip(asset.profile().clips().get("move")).duration()*.375);
            sample(p,now);var controller=new GltfAnimationController(asset.model().rig());controller.play(asset.profile().clips().get("move"),GltfAnimationController.Mode.LOOP,0,0);
            assertArrayEquals(positions(controller.sampleSeconds(now)),positions(p.pose()),1e-12,"explicit locomotion must not double its amplitude");
        }));
    }
    @TestFactory Stream<DynamicTest> renderSamplesCannotCompleteActionsOrMutateLocomotionPolicy(){
        return assets.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var asset=e.getValue();var p=new FirstPersonWeaponPresenter(asset);p.animation("inspect",0);
            double end=asset.model().rig().clip(asset.profile().clips().get("inspect")).duration();
            for(int i=0;i<60;i++)sample(p,end+i/144.0);
            assertEquals("inspect",p.action());assertFalse(p.movementLayerEnabled(),"render-only samples cannot advance action policy");
            p.tick((long)Math.ceil((end+.5)*20),0,0,true);assertEquals("idle",p.action());assertTrue(p.movementLayerEnabled());
        }));
    }
    private static void sample(FirstPersonWeaponPresenter p,double now){
        double ticks=now*20;long tick=(long)Math.floor(ticks);float delta=(float)(ticks-tick);if(delta>=1){tick++;delta=0;}
        double[] identity=new double[16],projection=new double[16];RigMath.identity(identity,0);PresentationMath.perspective(70,16.0/9,.05,256,projection);
        assertTrue(p.sample(tick,delta,identity,projection,identity),p.rejection()+" at "+now);
    }
    private static double[] positions(RigFrame pose){
        int count=0;for(var mesh:pose.model().meshes())count+=mesh.vertexCount()*3;
        double[] out=new double[count];int at=0;
        for(int m=0;m<pose.model().meshes().size();m++)for(int v=0;v<pose.model().meshes().get(m).vertexCount();v++)for(int k=0;k<3;k++)out[at++]=pose.vertex(m,v,k);
        return out;
    }
}
