package com.bobincraft.armament.client.mixin;

import com.bobincraft.armament.client.firstpersonmotion.fabric.FirstPersonMotionResources;
import com.bobincraft.armament.client.presentation.fabric.FirstPersonResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(value=FirstPersonResources.class,remap=false)
public abstract class FirstPersonMotionResourcesMixin {
    @Inject(method="current(Ljava/lang/String;)Ljava/util/Optional;",at=@At("RETURN"),cancellable=true)
    private void bobin$optionalClips(String variant,CallbackInfoReturnable<Optional<FirstPersonResources.Bundle>> cir){
        if(cir.getReturnValue().isPresent())FirstPersonMotionResources.current(variant).ifPresent(b->cir.setReturnValue(Optional.of(b)));
    }
}
