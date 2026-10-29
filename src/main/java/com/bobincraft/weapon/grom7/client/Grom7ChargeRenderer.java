package com.bobincraft.weapon.grom7.client;

import com.bobincraft.BobinCraft;
import com.bobincraft.combat.client.ImpactDebrisClient;
import com.bobincraft.weapon.grom7.Grom7ChargeEntity;
import com.bobincraft.weapon.grom7.Grom7State;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.*;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Low-poly 3D renderer for placed and thrown {@link Grom7ChargeEntity} instances (Section 10.2 & 10.5).
 * Displays a rapid bright green confirmation blink when bound to a remote via LMB, pulsing green/amber
 * when bound and ready, and rapid red when activated.
 */
@Environment(EnvType.CLIENT)
public final class Grom7ChargeRenderer extends EntityRenderer<Grom7ChargeEntity> {
    public static final Identifier TEXTURE = new Identifier(BobinCraft.MOD_ID, "textures/entity/grom7_charge.png");
    private final ModelPart casing;
    private final ModelPart straps;
    private final ModelPart receiver;
    private final ModelPart led;

    public Grom7ChargeRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        ModelPart root = modelData().createModel();
        this.casing = root.getChild("casing");
        this.straps = root.getChild("straps");
        this.receiver = root.getChild("receiver");
        this.led = root.getChild("led");
    }

    public static TexturedModelData modelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("casing", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-1.4f, 0.0f, -2.0f, 2.8f, 1.3f, 4.0f),
                ModelTransform.NONE);
        root.addChild("straps", ModelPartBuilder.create()
                        .uv(0, 8).cuboid(-1.5f, -0.05f, -1.4f, 3.0f, 1.4f, 0.55f)
                        .uv(0, 8).cuboid(-1.5f, -0.05f, 0.85f, 3.0f, 1.4f, 0.55f),
                ModelTransform.NONE);
        root.addChild("receiver", ModelPartBuilder.create()
                        .uv(12, 0).cuboid(-0.75f, 1.25f, -0.8f, 1.5f, 0.35f, 1.6f),
                ModelTransform.NONE);
        root.addChild("led", ModelPartBuilder.create()
                        .uv(20, 0).cuboid(-0.25f, 1.55f, -0.25f, 0.5f, 0.15f, 0.5f),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public void render(Grom7ChargeEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        int safeLight = light;
        if (entity.getWorld() instanceof ClientWorld clientWorld) {
            safeLight = ImpactDebrisClient.sampleFragmentLight(clientWorld, entity.getX(), entity.getY() + 0.06, entity.getZ(), light);
        }

        matrices.push();
        Grom7State state = entity.getChargeState();
        if (state == Grom7State.ATTACHED) {
            Direction face = entity.getAttachmentFace();
            float roll = entity.getAttachmentRoll();
            switch (face) {
                case UP -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
                case DOWN -> {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
                }
                case NORTH -> {
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(roll));
                }
                case SOUTH -> {
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(roll));
                }
                case WEST -> {
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(roll));
                }
                case EAST -> {
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0f));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(roll));
                }
            }
        } else {
            float interpYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
            float interpPitch = MathHelper.lerpAngleDegrees(tickDelta, entity.prevPitch, entity.getPitch());
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-interpYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(interpPitch));
        }

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        casing.render(matrices, buffer, safeLight, OverlayTexture.DEFAULT_UV, 0.44f, 0.49f, 0.36f, 1.0f);
        straps.render(matrices, buffer, safeLight, OverlayTexture.DEFAULT_UV, 0.24f, 0.26f, 0.25f, 1.0f);
        receiver.render(matrices, buffer, safeLight, OverlayTexture.DEFAULT_UV, 0.32f, 0.35f, 0.33f, 1.0f);

        // Status LED indicator:
        // - Rapid bright green flash during bind confirmation (bindBlinkTicks > 0)
        // - Bright red when ACTIVATED
        // - Steady/pulsing green when bound to remote, subtle amber when unbound
        int bindBlink = entity.getBindBlinkTicks();
        boolean activated = state == Grom7State.ACTIVATED;
        boolean bound = entity.isBoundToRemote();
        float lr, lg, lb;
        int ledLight;
        if (bindBlink > 0) {
            boolean flash = (bindBlink / 2) % 2 == 0;
            ledLight = flash ? LightmapTextureManager.MAX_LIGHT_COORDINATE : safeLight;
            lr = flash ? 0.20f : 0.10f;
            lg = flash ? 1.00f : 0.35f;
            lb = flash ? 0.38f : 0.14f;
        } else if (activated) {
            ledLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            lr = 1.0f;
            lg = 0.15f;
            lb = 0.12f;
        } else if (bound) {
            boolean pulse = ((entity.age / 8) % 2 == 0);
            ledLight = pulse ? LightmapTextureManager.MAX_LIGHT_COORDINATE : safeLight;
            lr = pulse ? 0.25f : 0.14f;
            lg = pulse ? 0.95f : 0.45f;
            lb = 0.22f;
        } else {
            boolean blinkOn = ((entity.age / 12) % 2 == 0);
            ledLight = blinkOn ? LightmapTextureManager.MAX_LIGHT_COORDINATE : safeLight;
            lr = blinkOn ? 0.92f : 0.35f;
            lg = blinkOn ? 0.58f : 0.22f;
            lb = 0.12f;
        }
        led.render(matrices, buffer, ledLight, OverlayTexture.DEFAULT_UV, lr, lg, lb, 1.0f);

        matrices.pop();
    }

    @Override
    public Identifier getTexture(Grom7ChargeEntity entity) {
        return TEXTURE;
    }
}
