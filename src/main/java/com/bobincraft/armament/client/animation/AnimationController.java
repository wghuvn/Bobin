package com.bobincraft.armament.client.animation;

import com.bobincraft.armament.api.WeaponStateSnapshot;

/** Future per-owner visual controller. P is an immutable pose, not gameplay state. */
public interface AnimationController<P> {
    void accept(WeaponStateSnapshot state);
    /** tickDelta must be finite in [0,1); sampling must not commit gameplay or sound. */
    P sample(long serverTick, float tickDelta);
    void reset();
}
