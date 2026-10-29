package com.bobincraft.armament.client.animation;

import com.bobincraft.armament.api.WeaponStateSnapshot;

/** Per-owner visual controller. P is visual pose data; implementations document storage lifetime. */
public interface AnimationController<P> {
    void accept(WeaponStateSnapshot state);
    /** tickDelta must be finite in [0,1); sampling must not commit gameplay or sound. */
    P sample(long serverTick, float tickDelta);
    void reset();
}
