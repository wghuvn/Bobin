package com.bobincraft.armament.client.mixin;

import com.bobincraft.armament.client.animation.gltf.GltfAnimationController;
import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController;
import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController.Mode;
import com.bobincraft.armament.client.firstpersonmotion.fabric.*;
import com.bobincraft.armament.client.presentation.FirstPersonWeaponPresenter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Add two explicit visuals without editing the approved presenter, camera or legacy clips. */
@Mixin(value=FirstPersonWeaponPresenter.class,remap=false)
public abstract class FirstPersonMotionPresenterMixin implements FirstPersonMotionPort {
    @Shadow @Final private GltfAnimationController controller;
    @Unique private FirstPersonMotionController bobin$motion;
    @Unique private Mode bobin$manual;
    @Unique private FirstPersonMotionController bobin$driver(){
        if(bobin$motion==null&&FirstPersonMotionController.supported(controller.model()))bobin$motion=new FirstPersonMotionController(controller);
        return bobin$motion;
    }
    @Inject(method="calibration",at=@At("HEAD"))
    private void bobin$calibrate(CallbackInfo ci){bobin$manual=null;if(bobin$motion!=null)bobin$motion.calibrated();}
    @Inject(method="animation",at=@At("HEAD"))
    private void bobin$legacyAction(String action,double now,CallbackInfo ci){
        bobin$manual=null;if(bobin$motion!=null)bobin$motion.select(Mode.BASE,now);
    }
    @Inject(method="tick",at=@At("TAIL"))
    private void bobin$visualInput(long tick,double yaw,double pitch,boolean moving,CallbackInfo ci){
        var p=(FirstPersonWeaponPresenter)(Object)this;
        var driver=bobin$driver();if(driver==null)return;
        var input=FirstPersonMotionClient.input(p,moving);
        driver.select(FirstPersonMotionController.desired(input.enabled(),p.staticIdle(),p.action(),bobin$manual,input.aim(),input.sprint()),tick/20.0);
    }
    @Override public void bobin$motion(Mode manual,double now){
        bobin$manual=manual;var driver=bobin$driver();
        if(driver!=null)driver.select(manual==null?Mode.BASE:manual,now);
    }
    @Override public String bobin$motionStatus(){return "fpMotion="+(bobin$motion==null?"BASE":bobin$motion.mode())+", selection="+(bobin$manual==null?"auto":bobin$manual);}
}
