package com.bobincraft.warzone.client;

import com.bobincraft.BobinCraft;
import com.bobincraft.warzone.FlySwarmEntity;
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

/** Renders a chaotic 3D cloud of 22 darting midges/flies with rapid wing flutter. */
@Environment(EnvType.CLIENT)
public final class FlySwarmRenderer extends EntityRenderer<FlySwarmEntity> {
    private static final Identifier TEXTURE = new Identifier(
            BobinCraft.MOD_ID, "textures/entity/ambient/fly_swarm.png");
    private static final int FLY_COUNT = 22;

    private final ModelPart flyModel;
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public FlySwarmRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        this.flyModel = createFlyModel().createModel();
        this.leftWing = this.flyModel.getChild("left_wing");
        this.rightWing = this.flyModel.getChild("right_wing");
    }

    private static TexturedModelData createFlyModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("body", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-0.5f, -0.5f, -1.0f, 1.0f, 1.0f, 2.0f),
                ModelTransform.NONE);
        root.addChild("left_wing", ModelPartBuilder.create()
                .uv(16, 0).cuboid(0.0f, -0.2f, -0.6f, 1.4f, 0.2f, 1.2f),
                ModelTransform.pivot(0.4f, -0.4f, 0.0f));
        root.addChild("right_wing", ModelPartBuilder.create()
                .uv(16, 4).cuboid(-1.4f, -0.2f, -0.6f, 1.4f, 0.2f, 1.2f),
                ModelTransform.pivot(-0.4f, -0.4f, 0.0f));
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public void render(FlySwarmEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float disperse = entity.disperse();
        if (disperse >= 0.99f) return;

        float time = (entity.age + tickDelta) * 0.19f;
        int seed = entity.swarmSeed();
        float spread = 1.0f + disperse * 2.4f;
        float alpha = MathHelper.clamp(1.0f - disperse, 0.1f, 1.0f);
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));

        for (int i = 0; i < FLY_COUNT; i++) {
            float p1 = ((seed + i * 73) & 255) * 0.0245f;
            float p2 = ((seed + i * 151) & 255) * 0.0245f;
            float p3 = ((seed + i * 211) & 255) * 0.0245f;
            float fx = MathHelper.sin(time * (1.1f + (i % 5) * 0.17f) + p1) * 0.58f * spread
                    + MathHelper.cos(time * 2.3f + p2) * 0.18f;
            float fy = MathHelper.sin(time * (1.4f + (i % 4) * 0.21f) + p2) * 0.42f * spread + 0.35f;
            float fiz = MathHelper.cos(time * (1.2f + (i % 6) * 0.15f) + p3) * 0.58f * spread
                    + MathHelper.sin(time * 2.1f + p1) * 0.18f;

            matrices.push();
            matrices.translate(fx, fy, fiz);
            float flyYaw = (time * 85.0f + i * 47.0f) % 360.0f;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(flyYaw));
            matrices.scale(0.32f, 0.32f, 0.32f);

            float flutter = MathHelper.sin(time * 9.5f + i * 1.7f) * 0.65f;
            this.leftWing.roll = flutter;
            this.rightWing.roll = -flutter;

            this.flyModel.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, alpha);
            matrices.pop();
        }
    }

    @Override
    public Identifier getTexture(FlySwarmEntity entity) {
        return TEXTURE;
    }
}
