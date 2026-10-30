package com.bobincraft.armament.client.thirdperson.fabric;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** One entity-attached GLB draw; no world-overlay or held-item replacement. */
public final class ThirdPersonWeaponFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    public ThirdPersonWeaponFeature(PlayerEntityRenderer renderer){super(renderer);}
    @Override public void render(MatrixStack matrices,VertexConsumerProvider consumers,int light,AbstractClientPlayerEntity player,
                                 float limbAngle,float limbDistance,float delta,float age,float headYaw,float headPitch){
        ThirdPersonDiagnostic.render(player,matrices,consumers,light);
    }
}
