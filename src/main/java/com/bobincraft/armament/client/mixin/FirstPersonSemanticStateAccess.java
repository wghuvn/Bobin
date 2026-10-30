package com.bobincraft.armament.client.mixin;

import com.bobincraft.armament.client.presentation.FirstPersonActionStateMachine;
import com.bobincraft.armament.client.presentation.FirstPersonWeaponPresenter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Semantic clock accessor only. No bone, camera, grip or rendering data is exposed. */
@Mixin(value=FirstPersonWeaponPresenter.class, remap=false)
public interface FirstPersonSemanticStateAccess {
    @Accessor("actions") FirstPersonActionStateMachine bobin$semanticState();
}
