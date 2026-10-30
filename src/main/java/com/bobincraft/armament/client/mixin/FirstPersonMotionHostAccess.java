package com.bobincraft.armament.client.mixin;

import com.bobincraft.armament.client.presentation.FirstPersonWeaponPresenter;
import com.bobincraft.armament.client.presentation.fabric.FirstPersonDiagnostic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=FirstPersonDiagnostic.class,remap=false)
public interface FirstPersonMotionHostAccess {
    @Accessor("presenter") FirstPersonWeaponPresenter bobin$motionPresenter();
    @Accessor("visualTick") long bobin$motionTick();
}
