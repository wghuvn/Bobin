package com.bobincraft.armament;

import com.bobincraft.armament.client.thirdperson.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;
import static org.junit.jupiter.api.Assertions.*;

final class ThirdPersonTorsoOrientationTest {
    @Test void acceptedHandsAndItemRemainExactlyThe521Transforms()throws Exception{
        var root=JsonParser.parseString(Files.readString(Path.of("src/test/resources/armament/third-person-hold-5.2.1.json"))).getAsJsonObject();
        assertEquals("12c8ba4",root.get("base_commit").getAsString());int count=0;
        ThirdPersonAsset[] assets={ThirdPersonAsset.load(id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())),false),ThirdPersonAsset.load(id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())),true)};
        for(var element:root.getAsJsonArray("samples")){
            var s=element.getAsJsonObject();boolean crouch=s.get("crouch").getAsBoolean();
            var p=new ThirdPersonWeaponPresenter(assets[s.get("slim").getAsBoolean()?1:0]);
            p.update(new PresentationAction(PresentationAction.Action.parse(s.get("action").getAsString()),0,false),0,true,true,crouch);
            var pose=p.sample(s.get("time").getAsDouble(),s.get("yaw").getAsDouble(),12,s.get("units").getAsDouble(),crouch?ThirdPersonBodyFrame.crouching(.28):ThirdPersonBodyFrame.standing(.28));
            for(var e:java.util.Map.of("right",pose.rightMatrix(),"left",pose.leftMatrix(),"placement",pose.placement()).entrySet())
                for(int i=0;i<16;i++)assertEquals(s.getAsJsonArray(e.getKey()).get(i).getAsDouble(),e.getValue()[i],0,"Existing hold "+e.getKey()+" sample "+count);
            assertTrue(Math.abs(pose.body().yaw())<=ThirdPersonTorsoOrientation.MAX_YAW+1e-7);
            var hip=point(pose.bodyMatrix(),0,-.75,0);assertEquals(pose.pelvis()[0],hip[0],1e-9);assertEquals(pose.pelvis()[2],hip[2],1e-9);
            for(double x:new double[]{-.25,.25})for(double z:new double[]{-.125,.125})assertTrue(point(pose.bodyMatrix(),x,-.75,z)[1]<=pose.pelvis()[1]-1.0/64+1e-8);
            count++;
        }
        assertEquals(432,count);
    }
    @Test void straightForwardPoseHasOnlySmallTorsoDeviation()throws Exception{
        for(boolean slim:new boolean[]{false,true}){
            var asset=ThirdPersonAsset.load(id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())),slim);
            for(var action:PresentationAction.Action.values()){
                var p=new ThirdPersonWeaponPresenter(asset);p.update(new PresentationAction(action,0,false),0,false,false,false);
                var pose=p.sample(1.3,0,0);assertTrue(Math.abs(Math.toDegrees(pose.body().yaw()))<8,action.key());
            }
        }
    }
    @Test void yawLimitIsSmoothSymmetricAndBounded(){
        for(int i=-3600;i<=3600;i++){
            double a=Math.toRadians(i/10.0),v=ThirdPersonTorsoOrientation.yaw(a);
            assertEquals(-v,ThirdPersonTorsoOrientation.yaw(-a),1e-14);
            assertTrue(Math.abs(v)<=Math.toRadians(10));
            assertTrue(Math.abs(ThirdPersonTorsoOrientation.yaw(a+1e-5)-v)<=.18e-5+1e-12);
        }
        assertEquals(0,ThirdPersonTorsoOrientation.yaw(0));
    }
}
