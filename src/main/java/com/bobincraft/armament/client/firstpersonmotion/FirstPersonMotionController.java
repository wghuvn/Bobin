package com.bobincraft.armament.client.firstpersonmotion;

import com.bobincraft.armament.client.animation.gltf.*;
import java.util.List;

/** New optional FPS visuals. The approved action/movement layers remain their owners. */
public final class FirstPersonMotionController {
    public enum Mode { BASE, REST, AIM, SPRINT }
    private final GltfAnimationController controller;
    private final NodeMask root;
    private Mode mode=Mode.BASE;
    public FirstPersonMotionController(GltfAnimationController controller){
        this.controller=controller;
        controller.model().clip("rest");controller.model().clip("aim");controller.model().clip("sprint");
        double[] weights=new double[controller.model().nodes().size()];
        for(String node:List.of("presentation_root","vanilla_arm_right","vanilla_arm_left"))weights[controller.model().node(node)]=1;
        root=new NodeMask(weights);
    }
    public static boolean supported(RigModel model){
        return List.of("rest","aim","sprint").stream().allMatch(name->model.clips().stream().anyMatch(c->c.name().equals(name)));
    }
    public void select(Mode next,double now){
        select(next,now,next==Mode.AIM?.24:.22);
    }
    private void select(Mode next,double now,double fade){
        if(next==mode)return;
        controller.layer(2,next==Mode.BASE?List.of():List.of(new GltfAnimationController.ClipRequest(
            next.name().toLowerCase(java.util.Locale.ROOT),GltfAnimationController.Mode.LOOP,1)),
            root,false,1,now,fade,GltfAnimationController.BlendCurve.SMOOTHSTEP);
        mode=next;
    }
    /** The approved presenter's calibration resets the actual controller itself. */
    public void calibrated(boolean rest){
        // The presenter's static sample uses time zero, including after a late reload.
        mode=rest?Mode.BASE:Mode.REST;select(rest?Mode.REST:Mode.BASE,0,0);
    }
    public Mode mode(){return mode;}
    public static Mode desired(boolean enabled,boolean calibration,String legacyAction,Mode manual,boolean aim,boolean sprint,boolean moving){
        if(!enabled||!(legacyAction.equals("idle")||legacyAction.equals("move")))return Mode.BASE;
        if(calibration)return Mode.REST;
        return manual!=null?manual:aim?Mode.AIM:sprint?Mode.SPRINT:moving||legacyAction.equals("move")?Mode.BASE:Mode.REST;
    }
}
