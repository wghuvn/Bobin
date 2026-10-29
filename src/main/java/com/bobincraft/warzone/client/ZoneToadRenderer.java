package com.bobincraft.warzone.client;

import com.bobincraft.BobinCraft;
import com.bobincraft.warzone.ZoneToadEntity;
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

/** Custom 3D renderer for the amphibious Zone toad, facing head-first along its movement/leap direction. */
@Environment(EnvType.CLIENT)
public final class ZoneToadRenderer extends EntityRenderer<ZoneToadEntity> {
    private static final Identifier TEXTURE = new Identifier(
            BobinCraft.MOD_ID, "textures/entity/ambient/zone_toad.png");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart throat;
    private final ModelPart leftHindLeg;
    private final ModelPart rightHindLeg;
    private final ModelPart leftForeLeg;
    private final ModelPart rightForeLeg;

    public ZoneToadRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.16f;
        this.root = createModelData().createModel();
        this.body = this.root.getChild("body");
        this.throat = this.body.getChild("throat");
        this.leftHindLeg = this.root.getChild("left_hind_leg");
        this.rightHindLeg = this.root.getChild("right_hind_leg");
        this.leftForeLeg = this.root.getChild("left_fore_leg");
        this.rightForeLeg = this.root.getChild("right_fore_leg");
    }

    private static TexturedModelData createModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData body = root.addChild("body", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-2.5f, -3.2f, -3.0f, 5.0f, 3.0f, 6.0f)
                .uv(0, 9).cuboid(-2.0f, -3.7f, -4.5f, 4.0f, 2.0f, 2.0f)
                .uv(0, 0).cuboid(-2.1f, -4.4f, -3.8f, 1.2f, 1.0f, 1.4f)
                .uv(4, 0).cuboid(0.9f, -4.4f, -3.8f, 1.2f, 1.0f, 1.4f),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        body.addChild("throat", ModelPartBuilder.create()
                .uv(6, 4).cuboid(-1.5f, -2.0f, -4.2f, 3.0f, 1.4f, 2.2f),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        root.addChild("left_hind_leg", ModelPartBuilder.create()
                .uv(0, 13).cuboid(-0.5f, -1.5f, -1.5f, 1.6f, 2.0f, 3.4f),
                ModelTransform.pivot(2.4f, -0.5f, 1.8f));
        root.addChild("right_hind_leg", ModelPartBuilder.create()
                .uv(8, 13).cuboid(-1.1f, -1.5f, -1.5f, 1.6f, 2.0f, 3.4f),
                ModelTransform.pivot(-2.4f, -0.5f, 1.8f));
        root.addChild("left_fore_leg", ModelPartBuilder.create()
                .uv(0, 15).cuboid(-0.4f, -2.0f, -0.8f, 1.2f, 2.2f, 1.4f),
                ModelTransform.pivot(2.1f, -0.2f, -2.0f));
        root.addChild("right_fore_leg", ModelPartBuilder.create()
                .uv(6, 15).cuboid(-0.8f, -2.0f, -0.8f, 1.2f, 2.2f, 1.4f),
                ModelTransform.pivot(-2.1f, -0.2f, -2.0f));
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public void render(ZoneToadEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        float lerpedYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
        // With scale(1, -1, -1) and model snout at -Z, -lerpedYaw aligns the snout with entity forward (+Z at yaw=0)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-lerpedYaw));
        float scale = 0.88f + (entity.variant() * 0.08f);
        matrices.scale(scale, scale, scale);
        matrices.scale(1.0f, -1.0f, -1.0f);

        float age = entity.age + tickDelta;
        if (entity.isLeaping()) {
            this.body.pitch = -0.32f;
            this.leftHindLeg.pitch = -0.85f;
            this.rightHindLeg.pitch = -0.85f;
            this.leftForeLeg.pitch = 0.45f;
            this.rightForeLeg.pitch = 0.45f;
            this.throat.xScale = this.throat.yScale = this.throat.zScale = 1.0f;
        } else {
            this.body.pitch = -0.08f;
            this.leftHindLeg.pitch = 0.08f;
            this.rightHindLeg.pitch = 0.08f;
            this.leftForeLeg.pitch = 0.0f;
            this.rightForeLeg.pitch = 0.0f;
            float pulse = 1.0f + MathHelper.sin(age * 0.22f) * 0.12f;
            this.throat.xScale = pulse;
            this.throat.yScale = pulse;
            this.throat.zScale = pulse;
        }

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        this.root.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);
        matrices.pop();
    }

    @Override
    public Identifier getTexture(ZoneToadEntity entity) {
        return TEXTURE;
    }
}
