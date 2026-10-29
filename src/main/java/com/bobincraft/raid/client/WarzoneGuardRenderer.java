package com.bobincraft.raid.client;

import com.bobincraft.combat.GuardInjuryPose;
import com.bobincraft.raid.guard.WarzoneGuardEntity;
import com.bobincraft.warzone.Faction;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Identifier;

import java.util.Locale;

/** Faction identity belongs to the tracked entity data rather than its display name. */
public final class WarzoneGuardRenderer extends MobEntityRenderer<WarzoneGuardEntity, WarzoneGuardModel> {
    private static final Identifier[][] TEXTURES = new Identifier[Faction.values().length][3];
    private static final Identifier HUB_SENTRY = new Identifier("bobin_craft", "textures/entity/hub/sentry.png");
    private final WarzoneGuardModel guardModel;
    private final GuardWoundFeature woundFeature;

    static {
        for (Faction faction : Faction.values()) {
            for (int variant = 0; variant < 3; variant++) {
                TEXTURES[faction.ordinal()][variant] = new Identifier("bobin_craft",
                        "textures/entity/faction/" + faction.name().toLowerCase(Locale.ROOT) + "_" + variant + ".png");
            }
        }
    }

    public static Identifier textureFor(WarzoneGuardEntity guard) {
        if (guard.isHubGuard()) return HUB_SENTRY;
        return TEXTURES[guard.faction().ordinal()][Math.floorMod(guard.appearanceVariant(), 3)];
    }

    public WarzoneGuardRenderer(EntityRendererFactory.Context context) {
        this(context, new WarzoneGuardModel(WarzoneGuardModel.getTexturedModelData().createModel()));
    }

    private WarzoneGuardRenderer(EntityRendererFactory.Context context, WarzoneGuardModel m) {
        super(context, m, 0.45F);
        this.guardModel = m;
        addFeature(new HeldItemFeatureRenderer<WarzoneGuardEntity, WarzoneGuardModel>(this, context.getHeldItemRenderer()) {
            @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, WarzoneGuardEntity guard,
                                         float angle, float distance, float delta, float age, float yaw, float pitch) {
                if (guard.isAlive() && guard.canUseHands()) {
                    super.render(matrices, vertices, light, guard, angle, distance, delta, age, yaw, pitch);
                }
            }
        });
        woundFeature = new GuardWoundFeature(this);
        addFeature(woundFeature);
    }

    @Override
    public void render(WarzoneGuardEntity guard, float yaw, float delta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light) {
        if (!guard.isAlive()) {
            if (!guard.isHubGuard()) return;
            matrices.push();
            setupTransforms(guard, matrices, 0, guard.getYaw(), delta);
            matrices.scale(-1.0F, -1.0F, 1.0F);
            matrices.scale(0.9375F, 0.9375F, 0.9375F);
            matrices.translate(0.0F, -1.501F, 0.0F);
            guardModel.setAngles(guard, 0, 0, 0, 0, 0);
            var bodyBuf = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(guard)));
            guardModel.renderCorpse(matrices, bodyBuf, light, OverlayTexture.DEFAULT_UV, 1.0F);
            woundFeature.render(matrices, vertices, light, guard, 0, 0, delta, 0, 0, 0);
            matrices.pop();
            return;
        }
        super.render(guard, yaw, delta, matrices, vertices, light);
    }

    @Override protected void setupTransforms(WarzoneGuardEntity guard, MatrixStack matrices, float age, float bodyYaw, float delta) {
        if (!guard.isAlive()) {
            int dPose = GuardInjuryPose.deathPose(guard.missingLimbs());
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - bodyYaw));
            if (dPose == GuardInjuryPose.POSE_BLAST) {
                matrices.translate(0, 0.24, -0.85);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            } else {
                matrices.translate(0, 0.26, 0.85);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
            }
            return;
        }
        float prone = guard.injuryProneAmount(delta);
        super.setupTransforms(guard, matrices, age, bodyYaw, delta);
        matrices.translate(0, 0.30 * prone, 0.85 * prone);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90 * prone));
    }

    @Override
    public Identifier getTexture(WarzoneGuardEntity guard) {
        return textureFor(guard);
    }
}
