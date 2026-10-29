package com.bobincraft.raid.client;

import com.bobincraft.combat.HitRegion;
import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Small pixel-red caps at missing limbs and neck stump. They inherit the prone/corpse body's exact transforms. */
public final class GuardWoundFeature extends FeatureRenderer<WarzoneGuardEntity, WarzoneGuardModel> {
    public static final Identifier TEXTURE = new Identifier("bobin_craft", "textures/entity/wound_caps.png");
    /** Blast stumps: scorched, charred rim. */
    public static final Identifier BURNT = new Identifier("bobin_craft", "textures/entity/wound/caps_burnt.png");
    /** Torn stumps: ragged flesh with bone. Alternates with TEXTURE per body so clean cuts are not all identical. */
    public static final Identifier GORE = new Identifier("bobin_craft", "textures/entity/wound/caps_gore.png");

    public static Identifier textureFor(java.util.UUID id, boolean blast) {
        if (blast) return BURNT;
        return (id.hashCode() & 1) == 0 ? TEXTURE : GORE;
    }
    private final ModelPart[] caps = new ModelPart[5];

    public GuardWoundFeature(FeatureRendererContext<WarzoneGuardEntity, WarzoneGuardModel> context) {
        super(context);
        ModelPart root = modelData().createModel();
        for (int i = 0; i < 5; i++) caps[i] = root.getChild("cap" + i);
    }

    public static TexturedModelData modelData() {
        ModelData data = new ModelData();
        // Arms detach at the torso's sides: vertical shoulder cuts, not horizontal shelves.
        for (int i = 0; i < 2; i++) data.getRoot().addChild("cap" + i,
                ModelPartBuilder.create().uv(0, 0).cuboid(i == 0 ? -.70f : 0,
                        -1.75f, -1.95f, .70f, 3.5f, 3.9f), ModelTransform.NONE);
        for (int i = 2; i < 4; i++) data.getRoot().addChild("cap" + i,
                ModelPartBuilder.create().uv(0, 0).cuboid(-2, 0, -2, 4, 1.1f, 4), ModelTransform.NONE);
        // Neck stump cap when the head is severed (bit 64)
        data.getRoot().addChild("cap4",
                ModelPartBuilder.create().uv(0, 0).cuboid(-2.0f, -0.55f, -2.0f, 4.0f, 1.1f, 4.0f), ModelTransform.NONE);
        return TexturedModelData.of(data, 16, 16);
    }

    @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, WarzoneGuardEntity guard,
                                 float limbAngle, float limbDistance, float delta, float age, float yaw, float pitch) {
        if (guard.isInvisible()) return;
        WarzoneGuardModel model = getContextModel();
        ModelPart[] limbs = {model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
        int mask = guard.missingLimbs();
        boolean blast = com.bobincraft.combat.client.InjuryClient.blastLike(guard.getUuid(), mask);
        var buffer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(textureFor(guard.getUuid(), blast)));
        for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) {
            caps[i].copyTransform(limbs[i]);
            caps[i].visible = true;
            caps[i].render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1.0F);
        }
        if ((mask & HitRegion.HEAD_SEVERED) != 0) {
            caps[4].copyTransform(model.body);
            caps[4].visible = true;
            caps[4].render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1.0F);
        }
    }
}
