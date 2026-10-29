package com.bobincraft.armament.client.model;

import com.bobincraft.armament.api.WeaponId;

/** Future immutable asset handle. Not a loaded mesh or a renderer implementation. */
public interface WeaponModelAsset {
    WeaponId id();
}
