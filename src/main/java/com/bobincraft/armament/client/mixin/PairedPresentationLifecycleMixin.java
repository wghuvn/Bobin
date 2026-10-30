package com.bobincraft.armament.client.mixin;

import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.client.presentation.fabric.FirstPersonDiagnostic;
import com.bobincraft.armament.client.thirdperson.PresentationAction;
import com.bobincraft.armament.client.thirdperson.PairedPresentationPolicy;
import com.bobincraft.armament.client.thirdperson.fabric.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional diagnostic lifecycle extension; the approved FPS sources/assets stay unchanged.
 * While TP is requested, F5 hides the FPS draw, not its semantic simulation clock.
 * Every original item/injury/vehicle/camera-owner guard is retained.
 */
@Mixin(value=FirstPersonDiagnostic.class, remap=false)
public abstract class PairedPresentationLifecycleMixin implements ThirdPersonSemanticPort {
    @Unique private final com.bobincraft.armament.client.thirdperson.SharedPresentationActions bobin$sharedActions=new com.bobincraft.armament.client.thirdperson.SharedPresentationActions();
    @Shadow private boolean requested;
    @Shadow private FirstPersonWeaponPresenter presenter;
    @Shadow private long visualTick;
    @Shadow private void update() { throw new AssertionError(); }

    @Inject(method="observations",at=@At("RETURN"),cancellable=true)
    private void bobin$keepActionAcrossF5(CallbackInfoReturnable<FirstPersonEligibility.View> cir) {
        var v=cir.getReturnValue();
        if (v.firstPerson() || !PairedPresentationPolicy.simulates(false,ThirdPersonDiagnostic.paired(),v.debug(),v.localCamera())) return;
        cir.setReturnValue(new FirstPersonEligibility.View(v.debug(),v.requested(),v.playerPresent(),v.alive(),v.spectator(),
                true,v.localCamera(),v.mainEmpty(),v.offEmpty(),v.compatibleBody(),v.otherHandsOwner(),v.usingOrSwinging(),v.mounted(),v.hudVisible(),v.viewportValid()));
    }
    @Override public void bobin$enablePairedPresentation() { requested=true;update(); }
    @Override public boolean bobin$requestPresentation(String action) {
        bobin$enablePairedPresentation();
        if(presenter==null)return false;
        bobin$sharedActions.clear();
        if(action.equals("aim")) {
            // Only the existing FPS idle input is used. No FPS ADS transform or
            // state-machine/presenter change is introduced for this 3P task.
            presenter.animation("idle",visualTick/20.0);
            bobin$sharedActions.aim(bobin$legacyPresentation(),visualTick/20.0);
        } else presenter.animation(action,visualTick/20.0);
        return true;
    }
    @Override public PresentationAction bobin$presentationAction() { return bobin$sharedActions.observe(bobin$legacyPresentation()); }
    @Unique private PresentationAction bobin$legacyPresentation() {
        if(!requested||presenter==null)return null;
        var state=((FirstPersonSemanticStateAccess)(Object)presenter).bobin$semanticState();
        return new PresentationAction(PresentationAction.Action.parse(state.action().key()),state.started(),state.staticIdle());
    }
    @Override public long bobin$presentationTick() { return visualTick; }
}
