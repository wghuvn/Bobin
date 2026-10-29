package com.bobincraft.warzone.client;

import com.bobincraft.BobinCraft;
import com.bobincraft.warzone.SkyBirdEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
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

/** Renders high-altitude Exclusion-Zone crows/ravens with smooth sub-tick wing-flap and thermal-glide cycles. */
@Environment(EnvType.CLIENT)
public final class SkyBirdRenderer extends EntityRenderer<SkyBirdEntity> {
    private static final Identifier TEXTURE = new Identifier(
            BobinCraft.MOD_ID, "textures/entity/ambient/sky_bird.png");

    private final ModelPart root;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart tail;

    public SkyBirdRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        this.root = createModelData().createModel();
        this.leftWing = this.root.getChild("left_wing");
        this.rightWing = this.root.getChild("right_wing");
        this.tail = this.root.getChild("tail");
    }

    private static TexturedModelData createModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("body", ModelPartBuilder.create()
                .uv(0, 4).cuboid(-1.5f, -1.5f, -3.5f, 3.0f, 2.5f, 7.0f)
                .uv(0, 0).cuboid(-1.2f, -2.0f, -5.5f, 2.4f, 2.0f, 2.4f)
                .uv(0, 0).cuboid(-0.6f, -1.4f, -7.2f, 1.2f, 1.0f, 2.0f),
                ModelTransform.NONE);
        root.addChild("tail", ModelPartBuilder.create()
                .uv(8, 0).cuboid(-2.2f, -0.8f, 0.0f, 4.4f, 0.8f, 4.0f),
                ModelTransform.pivot(0.0f, 0.0f, 3.2f));
        root.addChild("left_wing", ModelPartBuilder.create()
                .uv(0, 14).cuboid(0.0f, -0.5f, -2.5f, 7.5f, 0.8f, 4.5f),
                ModelTransform.pivot(1.4f, -0.8f, -0.5f));
        root.addChild("right_wing", ModelPartBuilder.create()
                .uv(0, 19).cuboid(-7.5f, -0.5f, -2.5f, 7.5f, 0.8f, 4.5f),
                ModelTransform.pivot(-1.4f, -0.8f, -0.5f));
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public void render(SkyBirdEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        float lerpedYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
        // With scale(1, -1, -1) and beak at -Z, -lerpedYaw aligns the beak with forward flight direction
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-lerpedYaw));
        float bankAngle = entity.isCircling()
                ? 15.0f + MathHelper.sin((entity.age + tickDelta) * 0.04f) * 3.0f
                : MathHelper.sin((entity.age + tickDelta) * 0.05f) * 5.0f;
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bankAngle));
        matrices.scale(0.95f, -0.95f, -0.95f);

        float t = entity.age + tickDelta + entity.flapOffset();
        // Smoothly modulate between active wingbeats and outstretched thermal gliding using a continuous envelope
        float glideEnvelope = MathHelper.clamp(0.55f + 0.65f * MathHelper.sin(t * 0.075f), 0.0f, 1.0f);
        float activeFlap = MathHelper.sin(t * 0.56f) * 0.54f;
        float thermalGlide = -0.07f + MathHelper.sin(t * 0.09f) * 0.04f;
        float flap = MathHelper.lerp(glideEnvelope, thermalGlide, activeFlap);

        this.leftWing.roll = flap;
        this.rightWing.roll = -flap;
        this.tail.pitch = 0.12f + MathHelper.abs(flap) * 0.10f;

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        this.root.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);
        matrices.pop();
    }

    @Override
    public Identifier getTexture(SkyBirdEntity entity) {
        return TEXTURE;
    }
}
