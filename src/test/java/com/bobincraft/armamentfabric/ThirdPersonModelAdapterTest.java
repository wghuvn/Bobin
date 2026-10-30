package com.bobincraft.armamentfabric;

import com.bobincraft.armament.client.thirdperson.*;
import com.bobincraft.armament.client.thirdperson.fabric.ThirdPersonModelAdapter;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;

/** Actual Minecraft ModelParts, without a window or GL context. */
final class ThirdPersonModelAdapterTest {
    @Test void neckAndShouldersUseOneBodyFrameAndBorrowedVanillaStateIsRestored()throws Exception{
        for(boolean slim:new boolean[]{false,true}){
            var asset=ThirdPersonAsset.load(id->Files.newInputStream(Path.of("src/main/resources/assets",id.namespace(),id.path())),slim);
            var root=TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE,slim),64,64).createModel();
            var model=new PlayerEntityModel<LivingEntity>(root,slim);
            for(var action:PresentationAction.Action.values())for(boolean crouch:new boolean[]{false,true})for(float yaw:new float[]{-1.4f,0,1.4f}){
                model.head.setTransform(ModelTransform.of(2,crouch?4.2f:0,-3,.5f,yaw,.03f));
                model.body.setTransform(ModelTransform.of(1,crouch?3.2f:0,2,.5f,0,.02f));
                var parts=List.of(model.head,model.hat,model.body,model.jacket,model.rightArm,model.leftArm,model.rightSleeve,model.leftSleeve,model.rightLeg,model.leftLeg);
                float[] before=snapshot(parts);
                var p=new ThirdPersonWeaponPresenter(asset);p.update(new PresentationAction(action,0,false),0,true,false,crouch);
                var pose=p.sample(1.3,-Math.toDegrees(yaw),-30,1,crouch?ThirdPersonBodyFrame.crouching(.3):ThirdPersonBodyFrame.standing(.3));
                try(var adapter=new ThirdPersonModelAdapter(model,pose)){
                    assertEquals(model.body.pivotX,model.head.pivotX,0);
                    assertEquals(model.body.pivotY,model.head.pivotY,0,"Head's neck must not use a delta from vanilla's different crouch pivot");
                    assertEquals(model.body.pivotZ,model.head.pivotZ,0);
                    assertEquals(yaw,model.head.yaw,1e-6);assertEquals(.5f,model.head.pitch,1e-6);
                    assertEquals(model.head.pivotY,model.hat.pivotY,0);
                    for(boolean right:new boolean[]{false,true}){
                        var local=asset.shoulder(right);double[] shoulder=point(pose.bodyMatrix(),local[0],local[1],local[2]);
                        var arm=right?model.rightArm:model.leftArm;
                        assertEquals(-shoulder[0]*16,arm.pivotX,2e-6);assertEquals(-shoulder[1]*16,arm.pivotY,2e-6);assertEquals(shoulder[2]*16,arm.pivotZ,2e-6);
                    }
                }
                assertArrayEquals(before,snapshot(parts),0,"Rendering must restore every borrowed part, including head/hat, and leave legs alone");
            }
        }
    }
    private static float[] snapshot(List<ModelPart> parts){
        float[] result=new float[parts.size()*6];int i=0;
        for(var p:parts){result[i++]=p.pivotX;result[i++]=p.pivotY;result[i++]=p.pivotZ;result[i++]=p.pitch;result[i++]=p.yaw;result[i++]=p.roll;}
        return result;
    }
}
