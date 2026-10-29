package com.bobincraft.weapon.grom7.client;

import com.bobincraft.weapon.grom7.Grom7Timing;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

/**
 * Third-person BipedEntityModel poses for GROM-7 throw, two-handed surface attachment,
 * remote binding, two-handed remote activation, and the 6-stage failed remote activation sequence.
 */
public final class Grom7ArmPose {
    private Grom7ArmPose() {}

    public static void apply(BipedEntityModel<?> model, Arm arm, Grom7Timing.Mode mode, float age) {
        int sign = arm == Arm.RIGHT ? 1 : -1;
        ModelPart main = arm == Arm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart off = arm == Arm.RIGHT ? model.leftArm : model.rightArm;

        switch (mode) {
            case THROW -> {
                var p = Grom7Timing.throwPose(age);
                float weight = 1.0f - p.recovery();
                main.pitch = MathHelper.lerp(weight, main.pitch, -1.05f - 1.35f * p.windup() + 1.95f * p.release());
                main.yaw = MathHelper.lerp(weight, main.yaw, -sign * (0.28f - 0.14f * p.windup()));
                main.roll = MathHelper.lerp(weight, main.roll, sign * 0.12f);
                off.pitch = MathHelper.lerp(weight, off.pitch, -0.95f + 0.45f * p.windup());
                off.yaw = MathHelper.lerp(weight, off.yaw, sign * 0.45f);
                model.body.yaw += sign * 0.10f * p.windup() * (1.0f - p.release()) * weight;
            }
            case ATTACH -> {
                var p = Grom7Timing.attachPose(age);
                float weight = 1.0f - p.recovery();
                float forwardPush = 0.65f * p.reach() + 0.35f * p.press();
                main.pitch = MathHelper.lerp(weight, main.pitch, -1.35f - 0.28f * forwardPush);
                main.yaw = MathHelper.lerp(weight, main.yaw, -sign * 0.26f);
                off.pitch = MathHelper.lerp(weight, off.pitch, -1.30f - 0.25f * forwardPush);
                off.yaw = MathHelper.lerp(weight, off.yaw, sign * 0.28f);
                model.body.pitch += 0.08f * forwardPush * weight;
            }
            case REMOTE_BIND -> {
                float u = MathHelper.clamp(age / Grom7Timing.Mode.REMOTE_BIND.durationTicks, 0f, 1f);
                float w = (float) Math.sin(u * Math.PI);
                main.pitch = MathHelper.lerp(w, main.pitch, -1.18f);
                main.yaw = MathHelper.lerp(w, main.yaw, -sign * 0.22f);
            }
            case REMOTE_SUCCESS -> {
                var p = Grom7Timing.successPose(age);
                float weight = p.raise() * (1.0f - p.lower());
                main.pitch = MathHelper.lerp(weight, main.pitch, -1.22f + 0.06f * p.anticipation() - 0.14f * p.clickRecoil());
                main.yaw = MathHelper.lerp(weight, main.yaw, -sign * 0.28f);
                main.roll = MathHelper.lerp(weight, main.roll, sign * 0.10f);

                off.pitch = MathHelper.lerp(weight, off.pitch, -1.34f - 0.12f * p.anticipation() + 0.22f * p.press());
                off.yaw = MathHelper.lerp(weight, off.yaw, sign * (0.48f - 0.08f * p.press()));
                off.roll = MathHelper.lerp(weight, off.roll, -sign * (0.18f - 0.08f * p.press()));
            }
            case REMOTE_FAIL -> {
                var p = Grom7Timing.failPose(age);
                float weight = p.raise();
                float targetPitch = -1.15f
                        - 0.14f * p.thumbPress()
                        - 0.34f * p.inspect()
                        + 0.16f * p.slapWave()
                        + 0.42f * p.disappoint();
                float targetYaw = -sign * (0.20f + 0.26f * p.inspect() - 0.14f * p.slapWave());
                float targetRoll = sign * (0.08f + 0.38f * p.inspect() + 0.28f * p.slapWave());
                main.pitch = MathHelper.lerp(weight, main.pitch, targetPitch);
                main.yaw = MathHelper.lerp(weight, main.yaw, targetYaw);
                main.roll = MathHelper.lerp(weight, main.roll, targetRoll);

                off.pitch = MathHelper.lerp(weight, off.pitch, -1.28f + 0.18f * p.thumbPress() + 0.30f * p.disappoint());
                off.yaw = MathHelper.lerp(weight, off.yaw, sign * 0.44f);

                model.head.pitch += (-0.18f * p.inspect() + 0.26f * p.disappoint()) * weight;
                model.head.roll += (sign * 0.16f * p.inspect()) * weight;
            }
        }
    }
}
