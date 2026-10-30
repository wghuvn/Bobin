package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.WeaponModelLoader.ResourceSource;
import com.bobincraft.armament.client.model.glb.*;
import java.io.IOException;

/** A separate external-view asset. The only meshes are the held diagnostic gauge. */
public record ThirdPersonAsset(GlbModelAsset model, boolean slim) {
    public static final WeaponId DEFAULT = new WeaponId("bobin_craft", "armament/debug/third_person_fixture.glb");
    public static final WeaponId SLIM = new WeaponId("bobin_craft", "armament/debug/third_person_fixture_slim.glb");
    public static ThirdPersonAsset load(ResourceSource source, boolean slim) throws IOException {
        return new ThirdPersonAsset(new JgltfModelLoader().load(slim ? SLIM : DEFAULT, source), slim);
    }
    public ThirdPersonAsset {
        var rig = model.rig();
        if (rig == null || !rig.skins().isEmpty() || rig.meshes().size() != 7)
            throw new IllegalArgumentException("third-person fixture must contain gauge geometry only");
        for (String node : new String[]{"tp_motion", "tp_stance", "tp_body", "tp_weapon", "tp_arm_right", "tp_arm_left", "tp_grip_right", "tp_grip_left"}) rig.node(node);
        for (var action : PresentationAction.Action.values()) rig.clip(action.key());
        rig.clip("crouch"); rig.clip("run");
        for (var mesh : rig.meshes()) {
            int node = mesh.node();
            while (node >= 0 && node != rig.node("tp_weapon")) node = rig.nodes().get(node).parent();
            if (node < 0) throw new IllegalArgumentException("player geometry must not be exported to runtime GLB");
        }
    }
}
