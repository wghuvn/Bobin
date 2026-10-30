package com.bobincraft.armament.client.thirdperson.fabric;

import com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.Part;
import com.bobincraft.armament.client.thirdperson.ThirdPersonWeaponPresenter.Pose;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** Borrow the live player model for one entity pass; never change cuboids, UVs or visibility. */
public final class ThirdPersonModelAdapter implements AutoCloseable {
    private final ModelPart[] parts;
    private final ModelTransform[] saved;
    public ThirdPersonModelAdapter(PlayerEntityModel<?> model,Pose pose){
        parts=new ModelPart[]{model.body,model.rightArm,model.leftArm,model.jacket,model.rightSleeve,model.leftSleeve};
        saved=new ModelTransform[parts.length];
        for(int i=0;i<parts.length;i++)saved[i]=parts[i].getTransform();
        apply(model.body,pose.body());apply(model.rightArm,pose.right());apply(model.leftArm,pose.left());
        model.jacket.copyTransform(model.body);model.rightSleeve.copyTransform(model.rightArm);model.leftSleeve.copyTransform(model.leftArm);
    }
    private static void apply(ModelPart part,Part pose){
        part.setTransform(ModelTransform.of(pose.x(),pose.y(),pose.z(),pose.pitch(),pose.yaw(),pose.roll()));
    }
    @Override public void close(){for(int i=0;i<parts.length;i++)parts[i].setTransform(saved[i]);}
}
