package com.bobincraft.drone.client;

import com.bobincraft.drone.DronePayloadEntity;
import com.bobincraft.drone.DronePayloadProfile;
import net.minecraft.client.model.*;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.EnumMap;

/** Three Minecraft silhouettes: grenade, vertical finned HE bomb and segmented fragmentation charge. */
public final class DronePayloadRenderer extends EntityRenderer<DronePayloadEntity> {
    private final EnumMap<DronePayloadProfile, ModelPart> bodies = new EnumMap<>(DronePayloadProfile.class);

    public DronePayloadRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = .12f;
        for (DronePayloadProfile profile : DronePayloadProfile.values()) bodies.put(profile, modelData(profile).createModel().getChild("body"));
    }

    static float floorOffset(DronePayloadProfile profile) {
        return (profile == DronePayloadProfile.HIGH_EXPLOSIVE || profile == DronePayloadProfile.HEAVY_STRUCTURAL ? 3.6f : profile == DronePayloadProfile.FRAGMENTATION ? 2.5f : 2.75f) / 16;
    }

    static TexturedModelData modelData(DronePayloadProfile profile) {
        ModelData data = new ModelData();
        ModelPartBuilder shape = ModelPartBuilder.create();
        if (profile == DronePayloadProfile.HIGH_EXPLOSIVE || profile == DronePayloadProfile.HEAVY_STRUCTURAL) {
            shape.uv(0, 0).cuboid(-1.3f, -2.4f, -1.3f, 2.6f, 5, 2.6f)
                    .uv(0, 0).cuboid(-.8f, 2.6f, -.8f, 1.6f, 1, 1.6f)
                    .uv(0, 0).cuboid(-2.7f, -3.6f, -.3f, 5.4f, 2, .6f)
                    .uv(0, 0).cuboid(-.3f, -3.6f, -2.7f, .6f, 2, 5.4f);
        } else if (profile == DronePayloadProfile.FRAGMENTATION) {
            shape.uv(0, 0).cuboid(-1.7f, -2, -1.7f, 3.4f, 4, 3.4f)
                    .uv(0, 0).cuboid(-.7f, -2.5f, -.7f, 1.4f, .5f, 1.4f)
                    .uv(0, 0).cuboid(-.7f, 2, -.7f, 1.4f, .5f, 1.4f);
            for (float y : new float[]{-1.4f, -.2f, 1}) shape.uv(0, 0).cuboid(-1.85f, y, -1.85f, 3.7f, .35f, 3.7f);
        } else {
            shape.uv(0, 0).cuboid(-1.35f, -2, -1.35f, 2.7f, 4.5f, 2.7f)
                    .uv(0, 0).cuboid(-.65f, 2.5f, -.65f, 1.3f, 1, 1.3f)
                    .uv(0, 0).cuboid(-.9f, -2.8f, -.9f, 1.8f, .8f, 1.8f)
                    .uv(0, 0).cuboid(.9f, -2.6f, -.4f, .6f, 3.4f, .8f);
        }
        data.getRoot().addChild("body", shape, ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32);
    }

    @Override public void render(DronePayloadEntity entity, float yaw, float delta, MatrixStack matrices,
                                 VertexConsumerProvider vertices, int light) {
        var profile = DronePayloadProfile.of(entity.getBlastType());
        matrices.push();
        matrices.translate(0, floorOffset(profile) * (entity.handThrown() ? 0.56f : 1.0f), 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerpAngleDegrees(delta, entity.prevYaw, entity.getYaw())));
        if (entity.handThrown()) {
            float interpPitch = MathHelper.lerpAngleDegrees(delta, entity.prevPitch, entity.getPitch());
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(interpPitch));
        }
        matrices.scale(entity.handThrown() ? 1.05f : 1, entity.handThrown() ? -.64f : -1, entity.handThrown() ? -1.05f : -1);
        boolean he = profile == DronePayloadProfile.HIGH_EXPLOSIVE || profile == DronePayloadProfile.HEAVY_STRUCTURAL, fragments = profile == DronePayloadProfile.FRAGMENTATION;
        bodies.get(profile).render(matrices, vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(DroneRenderer.TEXTURE)),
                light, OverlayTexture.DEFAULT_UV, he ? .76f : fragments ? .35f : .44f,
                he ? .58f : fragments ? .56f : .46f, he ? .22f : fragments ? .46f : .28f, 1);
        matrices.pop();
    }

    @Override public Identifier getTexture(DronePayloadEntity entity) { return DroneRenderer.TEXTURE; }
}
