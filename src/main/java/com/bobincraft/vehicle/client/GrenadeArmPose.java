package com.bobincraft.vehicle.client;

import com.bobincraft.vehicle.GrenadeTiming;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

/** Native player model pose, shared by the live animator and headless geometry inspection. */
public final class GrenadeArmPose {
    private GrenadeArmPose() {}

    public static void apply(BipedEntityModel<?> model, Arm arm, float age) {
        apply(model, arm, GrenadeTiming.pose(age), false);
    }

    public static void apply(BipedEntityModel<?> model, Arm arm, GrenadeTiming.Pose p, boolean lowArc) {
        float weight = 1.0f - p.recovery();
        int sign = arm == Arm.RIGHT ? 1 : -1;
        ModelPart main = arm == Arm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart off = arm == Arm.RIGHT ? model.leftArm : model.rightArm;
        if (lowArc) {
            // Underhand throw pose: arm draws down and back, then swings upward-forward
            main.pitch = MathHelper.lerp(weight, main.pitch, -0.75f + 0.85f * p.windup() - 1.45f * p.release());
            main.yaw = MathHelper.lerp(weight, main.yaw, -sign * (0.24f - 0.12f * p.windup()));
            main.roll = MathHelper.lerp(weight, main.roll, sign * 0.08f);
        } else {
            // Overhand throw pose: arm cocks high behind shoulder, torso rotates, then whips forward
            main.pitch = MathHelper.lerp(weight, main.pitch, -1.1f - 1.5f * p.windup() + 2.1f * p.release());
            main.yaw = MathHelper.lerp(weight, main.yaw, -sign * (0.32f - 0.17f * p.windup()));
            main.roll = MathHelper.lerp(weight, main.roll, sign * 0.13f);
        }
        off.pitch = MathHelper.lerp(weight, off.pitch, -1.35f + 0.5f * p.pin() + 0.6f * p.windup());
        off.yaw = MathHelper.lerp(weight, off.yaw, sign * (0.8f - 0.65f * p.pin()));
        model.body.yaw += sign * 0.14f * p.windup() * (1.0f - p.release()) * weight;
    }
}
