package com.bobincraft.armament.client.render;

import com.bobincraft.armament.client.model.WeaponModelAsset;

/** Future rendering boundary. P is pose data; C explicitly supplies owner/view/hand/light. */
public interface WeaponRenderer<P, C> {
    /** Draw only: must not process input, advance actions or emit sounds. */
    void render(WeaponModelAsset asset, P pose, C context);
}
