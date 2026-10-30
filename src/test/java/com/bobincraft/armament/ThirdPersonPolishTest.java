package com.bobincraft.armament;

import com.bobincraft.armament.client.thirdperson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;
import static org.junit.jupiter.api.Assertions.*;

final class ThirdPersonPolishTest {
    private static ThirdPersonAsset normal,slim;
    @BeforeAll static void load()throws Exception{
        normal=ThirdPersonAsset.load(id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())),false);
        slim=ThirdPersonAsset.load(id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())),true);
    }
    private static PresentationAction action(PresentationAction.Action a,double start){return new PresentationAction(a,start,false);}
    private static double[] vertex(double[] matrix,double[] position){return point(matrix,position[0],position[1],position[2]);}
    @ParameterizedTest @ValueSource(booleans={false,true}) void aimingRaisesTheHoldWithoutBringingHandsToTheFace(boolean alex){
        var p=new ThirdPersonWeaponPresenter(alex?slim:normal);
        p.update(action(PresentationAction.Action.IDLE,0),0,false,false,false);var carry=p.sample(0,0,0);
        double[] carryWeapon=multiply(carry.placement(),matrix(carry.geometry(),"tp_weapon"));
        p.update(action(PresentationAction.Action.AIM,1),1,false,false,false);var aim=p.sample(2.4,0,0);
        double[] weapon=multiply(aim.placement(),matrix(aim.geometry(),"tp_weapon"));
        double[] forward=subtract(point(weapon,0,0,-1),position(weapon));
        assertTrue(Math.abs(Math.toDegrees(Math.atan2(forward[0],-forward[2])))<5,"Muzzle is forward, not 60 degrees sideways");
        assertTrue(forward[2]<-.98);assertTrue(forward[1]>-.05&&forward[1]<.15);
        assertTrue(weapon[13]-carryWeapon[13]>.07,"Aiming actually raises the item");
        double[] butt=point(weapon,0,.065,.31);
        assertTrue(length(subtract(butt,position(aim.rightMatrix())))<.45,"Technical rear support remains at the upper chest/shoulder region");
        assertTrue(butt[1]<aim.bodyMatrix()[13],"Do not force the stock into the head to emulate a bent human elbow");
        for(boolean right:new boolean[]{true,false}){
            double[] arm=right?aim.rightMatrix():aim.leftMatrix();double[] contact=(alex?slim:normal).armContact(right);
            double[] direction=subtract(vertex(arm,contact),position(arm));
            assertTrue(Math.abs(Math.toDegrees(Math.atan2(direction[1],Math.hypot(direction[0],direction[2]))))<40,"Avoid the vertical arm slabs");
        }
        assertEquals(0,aim.headAssist().roll(),1e-6,"Head look stays vanilla; the neck follows the torso");
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void pelvisStaysJoinedThroughEveryActionLookGaitAndCrouch(boolean alex){
        for(var a:PresentationAction.Action.values())for(boolean crouched:new boolean[]{false,true}){
            var p=new ThirdPersonWeaponPresenter(alex?slim:normal);p.update(action(a,0),0,true,true,crouched);
            for(int step=0;step<90;step++){
                double t=step/30.0;
                var lower=new ThirdPersonBodyFrame(.015, .75+(crouched?.0125:0),crouched?.25:0,t*.8);
                var pose=p.sample(t,45*Math.sin(t),35*Math.cos(t),1/.9375,lower);
                double[] support=lower.gltfPelvis();double[] centre=support.clone();centre[1]-=pose.waistInset();
                assertArrayEquals(centre,point(pose.bodyMatrix(),0,-.75,0),2e-9,"Waist overlap follows the actual leg pivots");
                for(double x:new double[]{-.25,.25})for(double z:new double[]{-.125,.125})
                    assertTrue(point(pose.bodyMatrix(),x,-.75,z)[1]<=support[1]-1.0/64+1e-8,"No raised corner may open the torso/leg seam");
                assertTrue(pose.contactError()<1e-8);
            }
        }
    }
    @Test void inspectHasAnEndHoldAndSeparateRecoveryWithoutChangingSemanticClock(){
        var p=new ThirdPersonWeaponPresenter(normal);p.update(action(PresentationAction.Action.INSPECT,0),0,false,false,false);
        var before=p.sample(2.75,22,-13);
        p.update(action(PresentationAction.Action.IDLE,2.75),2.75,false,false,false);
        var at=p.sample(2.75,22,-13);
        assertArrayEquals(before.rightMatrix(),at.rightMatrix(),1e-7);assertArrayEquals(before.placement(),at.placement(),1e-7);
        p.sample(2.85,22,-13);assertEquals("inspect",p.controller().clip());assertEquals("end_hold",p.controller().phase(2.85));
        p.sample(3.0,22,-13);assertEquals("inspect_recover",p.controller().clip());
        p.sample(4.5,22,-13);assertEquals("idle",p.controller().clip());
        assertEquals(2.75,p.controller().action().started());
    }
    @Test void allActionInterruptionsIncludingAimKeepTheCurrentPoseAtTheBoundary(){
        for(var from:PresentationAction.Action.values())for(var to:PresentationAction.Action.values()){
            var p=new ThirdPersonWeaponPresenter(normal);p.update(action(from,0),0,false,false,false);
            var before=p.sample(.73,38,22);p.update(action(to,.73),.73,false,false,false);
            var after=p.sample(.73,38,22);
            assertArrayEquals(before.rightMatrix(),after.rightMatrix(),1e-7,from+" -> "+to);
            assertArrayEquals(before.placement(),after.placement(),1e-7,from+" -> "+to);
        }
    }
    @Test void idleAfterInspectHasBoundedFrameToFrameMotionAtDifferentRenderRates(){
        for(int rate:new int[]{30,60,144}){
            var p=new ThirdPersonWeaponPresenter(normal);p.update(action(PresentationAction.Action.INSPECT,0),0,false,false,false);
            ThirdPersonWeaponPresenter.Pose prior=null;
            for(int f=0;f<=rate*5;f++){
                double now=f/(double)rate;
                if(now>=2.75&&p.controller().action().action()!=PresentationAction.Action.IDLE)p.update(action(PresentationAction.Action.IDLE,2.75),now,false,false,false);
                var pose=p.sample(now,25,8);
                if(prior!=null){
                    assertTrue(length(subtract(position(pose.rightMatrix()),position(prior.rightMatrix())))<.10/rate+.003);
                    double delta=0;for(int i=0;i<12;i++)delta=Math.max(delta,Math.abs(pose.placement()[i]-prior.placement()[i]));
                    assertTrue(delta<5.0/rate,"No abrupt orientation reset, rate="+rate+", t="+now+", delta="+delta);
                }
                prior=pose;
            }
        }
    }
    @Test void locomotionUsesVanillaStridePhaseRatherThanAnIndependentBobClock(){
        var p=new ThirdPersonWeaponPresenter(normal);p.update(action(PresentationAction.Action.LOWER,0),0,true,false,false);
        var a=p.sample(5,0,0,1,ThirdPersonBodyFrame.standing(.25));
        var b=p.sample(8,0,0,1,ThirdPersonBodyFrame.standing(.25));
        assertArrayEquals(a.bodyMatrix(),b.bodyMatrix(),1e-8,"Same leg phase yields same upper-body phase");
        var c=p.sample(8,0,0,1,ThirdPersonBodyFrame.standing(.75));
        assertNotEquals(a.body().roll(),c.body().roll());
        assertArrayEquals(a.pelvis(),c.pelvis(),0);
    }
    @Test void sharedAimSurvivesF5WithoutAddingOrChangingAnyFpsClip(){
        var semantic=new SharedPresentationActions();var fallback=action(PresentationAction.Action.IDLE,12);
        semantic.aim(fallback,12);
        for(int i=0;i<100;i++){
            var snapshot=semantic.observe(fallback);assertEquals(PresentationAction.Action.AIM,snapshot.action());assertEquals(12,snapshot.started());
            assertTrue(PairedPresentationPolicy.simulates(i%3==0,true,true,true));
        }
        var inspect=action(PresentationAction.Action.INSPECT,18);assertEquals(inspect,semantic.observe(inspect));
        semantic.aim(fallback,20);assertNull(semantic.observe(null));assertEquals(fallback,semantic.observe(fallback));
    }
    @Test void explicitMoveShowsTheCycleEvenWhileThePlayerIsStationary(){
        var p=new ThirdPersonWeaponPresenter(normal);p.update(action(PresentationAction.Action.MOVE,0),0,false,false,false);
        var first=p.sample(.25,0,0,1,ThirdPersonBodyFrame.standing(0));
        var second=p.sample(.75,0,0,1,ThirdPersonBodyFrame.standing(0));
        assertNotEquals(first.body().roll(),second.body().roll());
        assertTrue(first.contactError()<1e-8&&second.contactError()<1e-8);
        assertEquals(PresentationAction.Action.MOVE,p.controller().action().action());
    }

}
