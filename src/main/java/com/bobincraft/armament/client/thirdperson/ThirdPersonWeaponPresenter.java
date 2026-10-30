package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.client.animation.gltf.RigFrame;
import static com.bobincraft.armament.client.thirdperson.ThirdPersonPoseMath.*;

/** Separate external-view poses and rigid-arm constraints, anchored to the current pelvis. */
public final class ThirdPersonWeaponPresenter {
    public record Pose(RigFrame geometry,Part body,Part right,Part left,double[] placement,
                       double[] rightMatrix,double[] leftMatrix,double contactError,double correctionMetres,
                       Part headAssist,double[] bodyMatrix,double[] pelvis,double waistInset) {}
    private static final double[] HIP={0,-.75,0};
    private final ThirdPersonAsset asset;
    private final ThirdPersonAnimationController controller;
    public ThirdPersonWeaponPresenter(ThirdPersonAsset asset){this.asset=asset;controller=new ThirdPersonAnimationController(asset);}
    public void update(PresentationAction action,double now,boolean moving,boolean running,boolean crouched){controller.update(action,now,moving,running,crouched);}
    public Pose sample(double now,double yaw,double pitch){return sample(now,yaw,pitch,1);}
    public Pose sample(double now,double yaw,double pitch,double units){return evaluate(now,yaw,pitch,units,null);}
    public Pose sample(double now,double yaw,double pitch,double units,ThirdPersonBodyFrame lower){return evaluate(now,yaw,pitch,units,lower);}
    private Pose evaluate(double now,double yaw,double pitch,double units,ThirdPersonBodyFrame lower){
        if(!Double.isFinite(units)||units<.5||units>2)throw new IllegalArgumentException("unsupported player scale");
        RigFrame frame=lower==null?controller.sample(now):controller.sample(now,lower.stridePhase());
        if(lower==null)lower=controller.crouched()?ThirdPersonBodyFrame.crouching(0):ThirdPersonBodyFrame.standing(0);
        double[] authoredBody=matrix(frame,"tp_body");
        // TRS blending/interpolation does not exactly preserve a remote pivot.
        // Reanchor analytically, including every interrupted transition.
        double[] hipFix=subtract(HIP,point(authoredBody,HIP[0],HIP[1],HIP[2]));
        double[] offset=subtract(lower.gltfPelvis(),HIP);
        double follow=controller.lookWeight(now);
        double[] bodyLook=around(HIP,aim(yaw*follow*.40,Math.max(-8,Math.min(8,pitch*follow*.18))));
        double[] weaponLook=around(new double[]{0,-.125,0},aim(yaw*follow,pitch*follow));
        translate(authoredBody,hipFix);double[] body=multiply(bodyLook,authoredBody);
        // A rigid tilted cuboid needs volume overlap, not only a centre-point
        // joint. Keep its entire lower face inside the vanilla leg tops.
        double waistInset=Math.abs(body[1])*.25+Math.abs(body[9])*.125+1.0/64;
        offset[1]-=waistInset;translate(body,offset);
        double[] right=matrix(frame,"tp_arm_right"),left=matrix(frame,"tp_arm_left");
        translate(right,hipFix);translate(left,hipFix);right=multiply(bodyLook,right);left=multiply(bodyLook,left);translate(right,offset);translate(left,offset);
        double[] placement=weaponLook.clone();
        for(int c=0;c<3;c++)for(int r=0;r<3;r++)placement[c*4+r]*=units;
        double[] scaledHip={hipFix[0]*units,hipFix[1]*units,hipFix[2]*units};
        double[] worldHip=point(weaponLook,scaledHip[0],scaledHip[1],scaledHip[2]);
        // Rotate the correction as a vector (not a point).
        for(int i=0;i<3;i++)placement[12+i]+=worldHip[i]-weaponLook[12+i]+offset[i];
        double[] gp=position(matrix(frame,"tp_grip_right")),gs=position(matrix(frame,"tp_grip_left"));
        double[] primary=point(placement,gp[0],gp[1],gp[2]),support=point(placement,gs[0],gs[1],gs[2]);
        double[] re=asset.armContact(true),le=asset.armContact(false);
        double[] correction=gripCorrection(position(right),position(left),primary,support,length(re),length(le));
        translate(placement,correction);for(int i=0;i<3;i++){primary[i]+=correction[i];support[i]+=correction[i];}
        right=attach(right,re,primary);left=attach(left,le,support);
        double error=Math.max(length(subtract(point(right,re[0],re[1],re[2]),primary)),length(subtract(point(left,le[0],le[1],le[2]),support)));
        if(!Double.isFinite(error)||error>.0001)throw new IllegalStateException("3P hand contact rejected: "+error);
        var torso=ThirdPersonTorsoOrientation.resolve(body,lower.gltfPelvis(),waistInset,frame,placement);
        body=torso.matrix();
        return new Pose(frame,toPart(body),toPart(right),toPart(left),placement,right,left,error,length(correction),toPart(matrix(frame,"tp_head_assist")),body,lower.gltfPelvis(),torso.waistInset());
    }
    public ThirdPersonAnimationController controller(){return controller;}
    public ThirdPersonAsset asset(){return asset;}
}
