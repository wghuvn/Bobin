package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.client.animation.gltf.RigFrame;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;

/** Independent external-view presentation, one instance per player. No camera-space inputs. */
public final class ThirdPersonWeaponPresenter {
    public record Pose(RigFrame geometry, Part body, Part right, Part left, double[] placement,
                       double[] rightMatrix, double[] leftMatrix, double contactError, double correctionMetres) {}
    private final ThirdPersonAsset asset;
    private final ThirdPersonAnimationController controller;
    public ThirdPersonWeaponPresenter(ThirdPersonAsset asset) { this.asset=asset;controller=new ThirdPersonAnimationController(asset); }
    public void update(PresentationAction action,double now,boolean moving,boolean running,boolean crouched) { controller.update(action,now,moving,running,crouched); }
    public Pose sample(double now,double headYaw,double headPitch) { return sample(now,headYaw,headPitch,1); }
    /** Cancel the entity renderer's measured uniform scale for physical GLB metres.
     * Arms retain the renderer's native size; their attachments are solved in model units.
     */
    public Pose sample(double now,double headYaw,double headPitch,double modelUnitsPerMetre) {
        if(!Double.isFinite(modelUnitsPerMetre)||modelUnitsPerMetre<.5||modelUnitsPerMetre>2)throw new IllegalArgumentException("unsupported player model scale");
        RigFrame frame=controller.sample(now);
        double[] aim=aim(headYaw,headPitch);
        double[] body=multiply(aim,matrix(frame,"tp_body"));
        double[] right=multiply(aim,matrix(frame,"tp_arm_right")),left=multiply(aim,matrix(frame,"tp_arm_left"));
        double[] primary=position(multiply(aim,matrix(frame,"tp_grip_right"))),support=position(multiply(aim,matrix(frame,"tp_grip_left")));
        for(int i=0;i<3;i++){primary[i]*=modelUnitsPerMetre;support[i]*=modelUnitsPerMetre;}
        double x=asset.slim()?.5/16:1.0/16;
        // glTF local coordinates of the actual vanilla distal rectangle centres.
        double[] re={x,-10.0/16,0},le={-x,-10.0/16,0};
        double[] correction=gripCorrection(position(right),position(left),primary,support,length(re));
        for(int i=0;i<3;i++){primary[i]+=correction[i];support[i]+=correction[i];}
        right=attach(right,re,primary);left=attach(left,le,support);
        double[] placement=aim.clone();
        for(int c=0;c<3;c++)for(int r=0;r<3;r++)placement[c*4+r]*=modelUnitsPerMetre;
        for(int i=0;i<3;i++)placement[12+i]+=correction[i];
        double error=Math.max(length(subtract(point(right,re[0],re[1],re[2]),primary)),length(subtract(point(left,le[0],le[1],le[2]),support)));
        if(!Double.isFinite(error)||error>.0001)throw new IllegalStateException("3P rigid hand contact rejected: "+error);
        return new Pose(frame,toPart(body),toPart(right),toPart(left),placement,right,left,error,length(correction));
    }
    public ThirdPersonAnimationController controller(){return controller;}
    public ThirdPersonAsset asset(){return asset;}
}
