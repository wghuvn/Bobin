package com.bobincraft.armament.client.firstpersonmotion;

import com.bobincraft.armament.client.animation.gltf.*;
import java.util.List;

/** New optional FPS visuals. The approved action/movement layers remain their owners. */
public final class FirstPersonMotionController {
    public enum Mode { BASE, AIM, SPRINT }
    private final GltfAnimationController controller;
    private final NodeMask root;
    private Mode mode=Mode.BASE;
    public FirstPersonMotionController(GltfAnimationController controller){
        this.controller=controller;
        controller.model().clip("aim");controller.model().clip("sprint");
        double[] weights=new double[controller.model().nodes().size()];
        weights[controller.model().node("presentation_root")]=1;root=new NodeMask(weights);
    }
    public static boolean supported(RigModel model){
        return model.clips().stream().anyMatch(c->c.name().equals("aim"))&&model.clips().stream().anyMatch(c->c.name().equals("sprint"));
    }
    public void select(Mode next,double now){
        if(next==mode)return;
        controller.layer(2,next==Mode.BASE?List.of():List.of(new GltfAnimationController.ClipRequest(
            next==Mode.AIM?"aim":"sprint",GltfAnimationController.Mode.LOOP,1)),
            root,false,1,now,next==Mode.AIM?.24:.22,GltfAnimationController.BlendCurve.SMOOTHSTEP);
        mode=next;
    }
    /** The approved presenter's calibration resets the actual controller itself. */
    public void calibrated(){mode=Mode.BASE;}
    public Mode mode(){return mode;}
    public static Mode desired(boolean enabled,boolean calibration,String legacyAction,Mode manual,boolean aim,boolean sprint){
        if(!enabled||calibration||!(legacyAction.equals("idle")||legacyAction.equals("move")))return Mode.BASE;
        return manual!=null?manual:aim?Mode.AIM:sprint?Mode.SPRINT:Mode.BASE;
    }
}
