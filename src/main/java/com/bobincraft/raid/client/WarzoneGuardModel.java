package com.bobincraft.raid.client;

import com.bobincraft.combat.HitRegion;
import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Original humanoid geometry using the documented 64 by 64 faction skin atlas. */
public final class WarzoneGuardModel extends BipedEntityModel<WarzoneGuardEntity> {
    public final ModelPart backpack;

    public WarzoneGuardModel(ModelPart root) {
        super(root);
        backpack = body.getChild("backpack");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("head", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-4, -8, -4, 8, 8, 8), ModelTransform.NONE);
        root.addChild("hat", ModelPartBuilder.create().uv(32, 0)
                .cuboid(-4, -8, -4, 8, 8, 8, new Dilation(0.35F)), ModelTransform.NONE);
        ModelPartData body = root.addChild("body", ModelPartBuilder.create().uv(16, 16)
                .cuboid(-4, 0, -2, 8, 12, 4)
                .uv(16, 32).cuboid(-4, 1, -2, 8, 10, 4, new Dilation(0.30F)), ModelTransform.NONE);
        // This small 4 x 6 x 2 pack fits below the shoulder pad in the atlas.
        body.addChild("backpack", ModelPartBuilder.create().uv(40, 40)
                .cuboid(-2, 2, 2.15F, 4, 6, 2), ModelTransform.NONE);
        root.addChild("right_arm", ModelPartBuilder.create().uv(40, 16)
                .cuboid(-3, -2, -2, 4, 12, 4)
                .uv(40, 32).cuboid(-3, -2, -2, 4, 4, 4, new Dilation(0.20F)),
                ModelTransform.pivot(-5, 2, 0));
        root.addChild("left_arm", ModelPartBuilder.create().uv(32, 48)
                .cuboid(-1, -2, -2, 4, 12, 4)
                .uv(48, 48).cuboid(-1, -2, -2, 4, 4, 4, new Dilation(0.20F)),
                ModelTransform.pivot(5, 2, 0));
        root.addChild("right_leg", ModelPartBuilder.create().uv(0, 16)
                .cuboid(-2, 0, -2, 4, 12, 4)
                .uv(0, 32).cuboid(-2, 7, -2, 4, 5, 4, new Dilation(0.12F)),
                ModelTransform.pivot(-1.9F, 12, 0));
        root.addChild("left_leg", ModelPartBuilder.create().uv(16, 48)
                .cuboid(-2, 0, -2, 4, 12, 4)
                .uv(0, 48).cuboid(-2, 7, -2, 4, 5, 4, new Dilation(0.12F)),
                ModelTransform.pivot(1.9F, 12, 0));
        return TexturedModelData.of(data, 64, 64);
    }

    public void renderCorpse(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float alpha) {
        renderCorpse(matrices, vertices, light, overlay, 1.0F, 1.0F, 1.0F, alpha);
    }

    /** Tinted variant: scorched bodies after a blast are rendered darker. */
    public void renderCorpse(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                             float r, float g, float b, float alpha) {
        head.render(matrices, vertices, light, overlay, r, g, b, alpha);
        hat.render(matrices, vertices, light, overlay, r, g, b, alpha);
        body.render(matrices, vertices, light, overlay, r, g, b, alpha);
        leftArm.render(matrices, vertices, light, overlay, r, g, b, alpha);
        rightArm.render(matrices, vertices, light, overlay, r, g, b, alpha);
        leftLeg.render(matrices, vertices, light, overlay, r, g, b, alpha);
        rightLeg.render(matrices, vertices, light, overlay, r, g, b, alpha);
    }

    @Override
    public void setAngles(WarzoneGuardEntity guard, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        leftArm.pivotY=rightArm.pivotY=2;leftArm.pivotZ=rightArm.pivotZ=0;body.roll=0;
        rightArmPose = ArmPose.EMPTY;
        leftArmPose = ArmPose.EMPTY;
        // Native biped idle breathing and weapon poses otherwise keep "animating" a dead amputee.
        if(!guard.isAlive()){limbAngle=limbDistance=animationProgress=headYaw=headPitch=0;}
        super.setAngles(guard, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        int missing = guard.missingLimbs();
        boolean hasHead = (missing & HitRegion.HEAD_SEVERED) == 0;
        head.visible = hasHead;
        hat.visible = hasHead;
        leftArm.visible = (missing & HitRegion.LEFT_ARM.limbMask) == 0;
        rightArm.visible = (missing & HitRegion.RIGHT_ARM.limbMask) == 0;
        leftLeg.visible = (missing & HitRegion.LEFT_LEG.limbMask) == 0;
        rightLeg.visible = (missing & HitRegion.RIGHT_LEG.limbMask) == 0;
        backpack.visible = Math.floorMod(guard.appearanceVariant(), 3) == 2;
        if (guard.isAlive() && !guard.getMainHandStack().isEmpty()) {
            ModelPart main = guard.isLeftHanded() ? leftArm : rightArm;
            ModelPart support = guard.isLeftHanded() ? rightArm : leftArm;
            float side = guard.isLeftHanded() ? -1 : 1;
            // MobEntity's attacking flag is synchronized; target references are server-only.
            if (guard.isAttacking() || guard.getTarget() != null) {
                main.pitch = -1.5708F + head.pitch;
                main.yaw = head.yaw - side * 0.20F;
                support.pitch = -1.43F + head.pitch;
                support.yaw = head.yaw + side * 0.48F;
            } else {
                main.pitch = -0.68F + main.pitch * 0.14F;
                support.pitch = -0.88F + support.pitch * 0.14F;
                main.yaw = -side * 0.10F;
                support.yaw = side * 0.42F;
            }
            main.roll = 0;
            support.roll = 0;
        }
        applyInjuryPose(missing, !guard.isAlive() ? 1F : guard.injuryProneAmount(animationProgress - guard.age),
                guard.isAlive() ? animationProgress * .30f : 0, limbDistance);
    }

    /** Public geometry seam also used by the headless model regression checks. */
    public void applyInjuryPose(int missing, float prone, float phase, float movement) {
        boolean hasHead = (missing & HitRegion.HEAD_SEVERED) == 0;
        head.visible = hasHead;
        hat.visible = hasHead;
        leftArm.visible = (missing & 1) == 0;
        rightArm.visible = (missing & 2) == 0;
        leftLeg.visible = (missing & 4) == 0;
        rightLeg.visible = (missing & 8) == 0;
        if (!hasHead) { head.pitch = head.yaw = head.roll = 0; hat.copyTransform(head); }
        if (!leftLeg.visible) leftLeg.pitch = 0;
        if (!rightLeg.visible) rightLeg.pitch = 0;
        if (!leftArm.visible) { leftArm.pitch = leftArm.yaw = leftArm.roll = 0; }
        if (!rightArm.visible) { rightArm.pitch = rightArm.yaw = rightArm.roll = 0; }
        com.bobincraft.combat.client.InjuryCrawlPose.lockMissing(this,missing);
        if (prone <= 0) return;
        com.bobincraft.combat.client.InjuryCrawlPose.apply(this,missing,prone,phase,movement);
        if (!hasHead) { head.pitch = head.yaw = head.roll = 0; hat.copyTransform(head); }
        com.bobincraft.combat.client.InjuryCrawlPose.lockMissing(this,missing);
    }
}
