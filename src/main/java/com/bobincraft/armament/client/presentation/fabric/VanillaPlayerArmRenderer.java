package com.bobincraft.armament.client.presentation.fabric;

import com.bobincraft.armament.client.animation.gltf.RigMath;
import com.bobincraft.armament.client.presentation.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws the actual player's vanilla arm ModelParts. No custom mesh, fingers or skinning. */
public final class VanillaPlayerArmRenderer {
    private final double[] placement = new double[16], part = new double[16], combined = new double[16];
    private final float[] transfer = new float[16];
    private final Matrix4f transform = new Matrix4f();
    private final Matrix3f rotation = new Matrix3f();

    public boolean available(MinecraftClient client, ClientPlayerEntity player) {
        return client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer;
    }

    public void render(MinecraftClient client, ClientPlayerEntity player, MatrixStack matrices,
                       VertexConsumerProvider.Immediate consumers, int light,
                       FirstPersonWeaponPresenter presenter, VanillaArmAttachments attachments) {
        var renderer = client.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerEntityRenderer playerRenderer))
            throw new IllegalStateException("player does not have a vanilla-compatible renderer");
        var model = playerRenderer.getModel();
        presenter.copyModelPlacement(placement);
        for (var side : VanillaArmAttachments.Side.values()) {
            attachments.modelPartTransform(presenter.pose(), side, part);
            RigMath.multiply(placement, 0, part, 0, combined, 0);
            GripFrameConvention.validate(combined, "vanilla arm placement");
            for (int i = 0; i < 16; i++) transfer[i] = (float) combined[i];
            transform.set(transfer);
            transform.get3x3(rotation);
            matrices.push();
            try {
                matrices.multiplyPositionMatrix(transform);
                // All anchors are rigid; inverse-transpose of their rotation is the rotation itself.
                matrices.peek().getNormalMatrix().mul(rotation);
                boolean right = side == VanillaArmAttachments.Side.RIGHT;
                drawPart(right ? model.rightArm : model.leftArm, matrices, consumers,
                        RenderLayer.getEntitySolid(player.getSkinTexture()), light);
                if (player.isPartVisible(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE))
                    drawPart(right ? model.rightSleeve : model.leftSleeve, matrices, consumers,
                            RenderLayer.getEntityTranslucent(player.getSkinTexture()), light);
            } finally {
                matrices.pop();
            }
        }
    }

    private static void drawPart(ModelPart part, MatrixStack matrices, VertexConsumerProvider consumers,
                                 RenderLayer layer, int light) {
        ModelTransform before = part.getTransform();
        boolean visible = part.visible;
        try {
            part.setTransform(ModelTransform.NONE);
            part.visible = true;
            part.render(matrices, consumers.getBuffer(layer), light, OverlayTexture.DEFAULT_UV);
        } finally {
            part.setTransform(before);
            part.visible = visible;
        }
    }
}
