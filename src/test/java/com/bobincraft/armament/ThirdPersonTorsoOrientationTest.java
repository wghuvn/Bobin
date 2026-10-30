package com.bobincraft.armament;

import com.bobincraft.armament.client.thirdperson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;
import static org.junit.jupiter.api.Assertions.*;

/** The rejected 5.2.1 hold is historical data, no longer a pose to preserve. */
final class ThirdPersonTorsoOrientationTest {
    private static ThirdPersonAsset asset(boolean slim)throws Exception{
        return ThirdPersonAsset.load(id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())),slim);
    }
    @Test void shouldersFollowTheFinalTorsoThroughActionsLooksGaitsAndCrouch()throws Exception{
        for(boolean slim:new boolean[]{false,true}){
            var a=asset(slim);
            for(var action:PresentationAction.Action.values())for(boolean crouch:new boolean[]{false,true})for(boolean run:new boolean[]{false,true}){
                var p=new ThirdPersonWeaponPresenter(a);p.update(new PresentationAction(action,0,false),0,true,run,crouch);
                for(double time:new double[]{0,.4,1.3,2.2})for(double yaw:new double[]{-85,0,85})for(double pitch:new double[]{-50,0,50}){
                    var lower=crouch?ThirdPersonBodyFrame.crouching(time):ThirdPersonBodyFrame.standing(time);
                    var pose=p.sample(time,yaw,pitch,1/.9375,lower);
                    assertTrue(Math.abs(pose.body().yaw())<=ThirdPersonTorsoOrientation.MAX_YAW+1e-7);
                    for(boolean right:new boolean[]{false,true}){
                        var local=a.shoulder(right);double[] shoulder=point(pose.bodyMatrix(),local[0],local[1],local[2]);
                        double[] arm=right?pose.rightMatrix():pose.leftMatrix();
                        assertArrayEquals(shoulder,position(arm),1e-10,"Shoulder detached from final torso: "+action);
                        var end=a.armContact(right);assertEquals(-.625,end[1],1e-6,"Contacts remain on the full-size vanilla hand ends");
                        double[] hand=point(arm,end[0],end[1],end[2]);
                        assertTrue(hand[1]<pose.bodyMatrix()[13]-.07,"Hand raised into the head: "+action+" yaw="+yaw+" pitch="+pitch);
                        double half=(slim?3:4)/32.0;
                        for(double x:new double[]{end[0]-half,end[0]+half})for(double y:new double[]{-.625,-.375})for(double z:new double[]{-.125,.125})
                            assertTrue(point(arm,x,y,z)[1]<pose.bodyMatrix()[13]+.02,"Actual vanilla hand cuboid enters the face: "+action);
                    }
                    double[] hip=point(pose.bodyMatrix(),0,-.75,0);double[] support=lower.gltfPelvis();support[1]-=pose.waistInset();
                    assertArrayEquals(support,hip,1e-9);
                    for(double x:new double[]{-.25,.25})for(double z:new double[]{-.125,.125})
                        assertTrue(point(pose.bodyMatrix(),x,-.75,z)[1]<=lower.gltfPelvis()[1]-1.0/64+1e-8);
                    assertTrue(pose.contactError()<1e-8);
                }
            }
        }
    }
    @Test void restIsAcrossTheChestAndHeadLookDoesNotMoveTheHold()throws Exception{
        for(boolean slim:new boolean[]{false,true}){
            var a=asset(slim);var p=new ThirdPersonWeaponPresenter(a);
            p.update(new PresentationAction(PresentationAction.Action.IDLE,0,false),0,false,false,false);
            var rest=p.sample(1,0,0);double[] hold=multiply(rest.placement(),matrix(rest.geometry(),"tp_weapon"));
            assertTrue(Math.abs(hold[12])<.15,"Hold shifted outside the chest");
            assertTrue(hold[13]<-.25&&hold[13]>-.60);
            double[] forward=subtract(point(hold,0,0,-1),position(hold));
            double yaw=Math.toDegrees(Math.atan2(-forward[0],-forward[2]));
            assertTrue(yaw>30&&yaw<55,"Rest should carry across the body, rather than aim along the camera");
            for(double look:new double[]{-180,-85,45,180}){
                var turned=p.sample(1,look,50);
                assertArrayEquals(rest.placement(),turned.placement(),1e-12,"Idle head look must not orbit the gun about the neck");
                assertArrayEquals(rest.rightMatrix(),turned.rightMatrix(),1e-12);
                assertArrayEquals(rest.leftMatrix(),turned.leftMatrix(),1e-12);
            }
        }
    }
    @Test void yawLimitIsSmoothSymmetricAndBounded(){
        for(int i=-3600;i<=3600;i++){
            double a=Math.toRadians(i/10.0),v=ThirdPersonTorsoOrientation.yaw(a);
            assertEquals(-v,ThirdPersonTorsoOrientation.yaw(-a),1e-14);
            assertTrue(Math.abs(v)<=ThirdPersonTorsoOrientation.MAX_YAW);
            assertTrue(Math.abs(ThirdPersonTorsoOrientation.yaw(a+1e-5)-v)<=1e-5+1e-12);
        }
        assertEquals(0,ThirdPersonTorsoOrientation.yaw(0));
    }
    @Test void cameraTurnsDoNotJumpTheWeaponOrNeck()throws Exception{
        for(boolean slim:new boolean[]{false,true})for(boolean crouch:new boolean[]{false,true}){
            var a=asset(slim);var p=new ThirdPersonWeaponPresenter(a);
            p.update(new PresentationAction(PresentationAction.Action.AIM,0,false),0,false,false,crouch);
            double[] previous=null,neck=null;
            for(int degree=-85;degree<=85;degree++){
                var pose=p.sample(1.3,degree,35*Math.sin(Math.toRadians(degree*2)));
                double[] weapon=position(multiply(pose.placement(),matrix(pose.geometry(),"tp_weapon"))),next=position(pose.bodyMatrix());
                if(previous!=null){
                    assertTrue(length(subtract(weapon,previous))<.04,"Hold jumped during a one-degree camera turn: "+degree);
                    assertTrue(length(subtract(next,neck))<.015,"Neck flew away during camera turn");
                }
                previous=weapon;neck=next;
            }
        }
    }
}
