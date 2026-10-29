package com.bobincraft.weapon.heavy;

import com.bobincraft.BobinCraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.*;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Low-poly renderer for the in-flight RPG projectile entity (Section 11).
 * Uses a single compact ModelPart (4 cuboids = 48 triangles) and a shared 32x32 texture.
 */
@Environment(EnvType.CLIENT)
public final class RpgProjectileRenderer extends EntityRenderer<RpgProjectileEntity> {
    public static final Identifier TEXTURE = new Identifier(BobinCraft.MOD_ID, "textures/entity/drone.png");
    private final ModelPart body;

    public RpgProjectileRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        this.body = modelData().createModel().getChild("body");
    }

    public static TexturedModelData modelData() {
        ModelData data = new ModelData();
        ModelPartBuilder builder = ModelPartBuilder.create()
                // Warhead cone and body
                .uv(0, 0).cuboid(-1.2f, -1.2f, -4.5f, 2.4f, 2.4f, 4.2f)
                .uv(0, 0).cuboid(-0.7f, -0.7f, -6.2f, 1.4f, 1.4f, 1.7f)
                // Motor tube
                .uv(0, 0).cuboid(-0.8f, -0.8f, -0.3f, 1.6f, 1.6f, 4.8f)
                // Cruciform stabilizer fins
                .uv(0, 0).cuboid(-2.4f, -0.2f, 3.0f, 4.8f, 0.4f, 1.5f)
                .uv(0, 0).cuboid(-0.2f, -2.4f, 3.0f, 0.4f, 4.8f, 1.5f);
        data.getRoot().addChild("body", builder, ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public void render(RpgProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.translate(0.0, 0.12, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerpAngleDegrees(tickDelta, entity.prevPitch, entity.getPitch())));
        matrices.scale(0.85f, -0.85f, 0.85f);
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        body.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.42f, 0.47f, 0.34f, 1.0f);
        matrices.pop();
    }

    @Override
    public Identifier getTexture(RpgProjectileEntity entity) {
        return TEXTURE;
    }
}
