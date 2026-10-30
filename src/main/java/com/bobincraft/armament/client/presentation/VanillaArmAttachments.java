package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import java.util.Map;
import java.util.Objects;

/** Rigid anchors for Minecraft's existing ModelParts; does not create hand geometry. */
public final class VanillaArmAttachments {
    public static final Map<String, WeaponId> PROFILES = Map.of(
            "default", WeaponId.parse("bobin_craft:armament/debug/vanilla_first_person_fixture.profile.json"),
            "slim", WeaponId.parse("bobin_craft:armament/debug/vanilla_first_person_fixture_slim.profile.json"));
    public static final double PIXEL = 1.0 / 16;
    public static final double LENGTH = 12 * PIXEL;
    public static final double DEPTH = 4 * PIXEL;
    public static final double END_Y = 10 * PIXEL;
    public static final double SLEEVE_DILATION = PIXEL / 4;
    public enum Side { RIGHT, LEFT }

    private final RigModel model;
    private final boolean slim;
    private final int right, left;

    public VanillaArmAttachments(FirstPersonAsset asset, String variant) {
        Objects.requireNonNull(asset);
        if (!PROFILES.containsKey(variant)) throw new IllegalArgumentException("unknown vanilla arm variant: " + variant);
        slim = variant.equals("slim");
        model = asset.model().rig();
        if (!model.skins().isEmpty()) throw new IllegalArgumentException("vanilla arm pass cannot contain GLB skins/custom hands");
        for (var mesh : model.meshes())
            if (!model.nodes().get(mesh.node()).name().startsWith("gauge_") || mesh.skin() >= 0)
                throw new IllegalArgumentException("vanilla first-person GLB must contain only the held gauge");
        right = model.node("vanilla_arm_right");
        left = model.node("vanilla_arm_left");
        var bind = new RigFrame(model);
        double[] matrix = new double[16];
        for (Side side : Side.values()) {
            anchor(bind, side, matrix);
            GripFrameConvention.validate(matrix, "vanilla arm anchor");
        }
    }

    public boolean slim() { return slim; }
    public double width() { return (slim ? 3 : 4) * PIXEL; }
    /** Local centre of the ordinary ModelPart's end cap after neutralising its pivot. */
    public double endX(Side side) { return (side == Side.RIGHT ? -1 : 1) * (slim ? .5 : 1) * PIXEL; }

    public void anchor(RigFrame pose, Side side, double[] output) {
        if (pose.model() != model) throw new IllegalArgumentException("vanilla arm pose belongs to another asset");
        if (side == null || output == null || output.length != 16) throw new IllegalArgumentException("arm side and 16-value output required");
        int node = side == Side.RIGHT ? right : left;
        for (int i = 0; i < 16; i++) output[i] = pose.global(node, i);
    }
    /** glTF model-space matrix for the unmodified vanilla arm/sleeve cuboid. */
    public void modelPartTransform(RigFrame pose, Side side, double[] output) {
        anchor(pose, side, output);
        double x = endX(side);
        output[12] -= output[0] * x + output[4] * END_Y;
        output[13] -= output[1] * x + output[5] * END_Y;
        output[14] -= output[2] * x + output[6] * END_Y;
    }
}
