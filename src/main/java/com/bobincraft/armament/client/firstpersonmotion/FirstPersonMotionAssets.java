package com.bobincraft.armament.client.firstpersonmotion;

import com.bobincraft.armament.api.WeaponId;
import java.util.Map;

/** Combined assets append two clips; their original geometry and five clips are copied byte for byte. */
public final class FirstPersonMotionAssets {
    public static final Map<String,WeaponId> PROFILES=Map.of(
        "default",WeaponId.parse("bobin_craft:armament/debug/vanilla_first_person_motion.profile.json"),
        "slim",WeaponId.parse("bobin_craft:armament/debug/vanilla_first_person_motion_slim.profile.json"));
    private FirstPersonMotionAssets(){}
}
