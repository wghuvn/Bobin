package com.bobincraft.armament.client.mixin;

import com.bobincraft.armament.client.animation.gltf.GltfAnimationController;
import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController;
import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController.Mode;
import com.bobincraft.armament.client.firstpersonmotion.fabric.*;
import com.bobincraft.armament.client.presentation.FirstPersonWeaponPresenter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Independent FPS rest/aim/sprint; the legacy walking clip and camera remain unchanged. */
@Mixin(value=FirstPersonWeaponPresenter.class,remap=false)
public abstract class FirstPersonMotionPresenterMixin implements FirstPersonMotionPort {
    @Shadow @Final private GltfAnimationController controller;
    @Unique private FirstPersonMotionController bobin$motion;
    @Unique private Mode bobin$manual;
    @Unique private FirstPersonMotionController bobin$driver(){
        if(bobin$motion==null&&FirstPersonMotionController.supported(controller.model()))bobin$motion=new FirstPersonMotionController(controller);
        return bobin$motion;
    }
    @Inject(method="calibration",at=@At("RETURN"))
    private void bobin$calibrate(CallbackInfo ci){
        bobin$manual=null;var driver=bobin$driver();if(driver!=null)driver.calibrated(FirstPersonMotionClient.enabled());
    }
    @Inject(method="animation",at=@At("HEAD"))
    private void bobin$legacyAction(String action,double now,CallbackInfo ci){
        bobin$manual=null;if(bobin$motion!=null)bobin$motion.select(Mode.BASE,now);
    }
    @Inject(method="tick",at=@At("RETURN"))
    private void bobin$visualInput(long tick,double yaw,double pitch,boolean moving,CallbackInfo ci){
        var p=(FirstPersonWeaponPresenter)(Object)this;
        var driver=bobin$driver();if(driver==null)return;
        var input=FirstPersonMotionClient.input(p,moving);
        if(input.enabled()&&p.staticIdle()&&(moving||input.aim()||input.sprint()))p.animation("idle",tick/20.0);
        if(p.staticIdle())return; // static rest was evaluated at time zero by calibration
        driver.select(FirstPersonMotionController.desired(input.enabled(),false,p.action(),bobin$manual,input.aim(),input.sprint(),moving),tick/20.0);
    }
    @Override public void bobin$motion(Mode manual,double now){
        bobin$manual=manual;var driver=bobin$driver();
        if(driver!=null){
            if(((FirstPersonWeaponPresenter)(Object)this).staticIdle())driver.calibrated(FirstPersonMotionClient.enabled());
            else driver.select(manual==null?Mode.BASE:manual,now);
        }
    }
    @Override public String bobin$motionStatus(){return "fpMotion="+(bobin$motion==null?"BASE":bobin$motion.mode())+", selection="+(bobin$manual==null?"auto":bobin$manual);}
}
