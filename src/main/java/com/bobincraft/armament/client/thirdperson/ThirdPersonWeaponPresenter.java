package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.client.animation.gltf.*;
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
        double follow=controller.lookWeight(now);
        double[] bodyLook=around(HIP,aim(yaw*follow*.10,Math.max(-3,Math.min(3,pitch*follow*.06))));
        var torso=ThirdPersonTorsoOrientation.resolve(multiply(bodyLook,authoredBody),lower.gltfPelvis());
        double[] body=torso.matrix(),inverseBody=new double[16];
        RigMath.inverseAffine(authoredBody,0,inverseBody,0);
        // One anatomical frame: all pivots inherit the final torso, not its old authored pose.
        double[] rebase=multiply(body,inverseBody);
        double[] right=multiply(rebase,matrix(frame,"tp_arm_right")),left=multiply(rebase,matrix(frame,"tp_arm_left"));
        for(boolean side:new boolean[]{true,false}){
            double[] arm=side?right:left,local=asset.shoulder(side);
            double[] shoulder=point(body,local[0],local[1],local[2]);
            System.arraycopy(shoulder,0,arm,12,3);
        }
        // Preserve physical item size, scaling around the held object rather than the player's origin.
        double[] placement=rebase.clone(),origin=position(matrix(frame,"tp_weapon"));
        double[] placedOrigin=point(placement,origin[0],origin[1],origin[2]);
        for(int c=0;c<3;c++)for(int r=0;r<3;r++)placement[c*4+r]*=units;
        double[] scaledOrigin=point(placement,origin[0],origin[1],origin[2]);
        for(int i=0;i<3;i++)placement[12+i]+=placedOrigin[i]-scaledOrigin[i];
        double[] gp=position(matrix(frame,"tp_grip_right")),gs=position(matrix(frame,"tp_grip_left"));
        double[] centre=point(placement,(gp[0]+gs[0])/2,(gp[1]+gs[1])/2,(gp[2]+gs[2])/2);
        // At rest the head may look around without dragging the gun around the neck.
        // Aim rotates about the actual hold centre and stays inside a rigid-arm envelope.
        double[] re=asset.armContact(true),le=asset.armContact(false);
        double[] bodyInverse=new double[16];RigMath.inverseAffine(body,0,bodyInverse,0);
        double lookYaw=Math.max(-35,Math.min(35,yaw*follow)),lookPitch=Math.max(-30,Math.min(30,pitch*follow));
        var held=hold(frame,placement,centre,lookYaw,lookPitch,bodyInverse,body[13],right,left,gp,gs,length(re),length(le));
        if(held==null){
            var neutral=hold(frame,placement,centre,0,0,bodyInverse,body[13],right,left,gp,gs,length(re),length(le));
            if(neutral==null)throw new IllegalStateException("3P pose exceeds the vanilla hand/torso envelope");
            double lo=0,hi=1;held=neutral;
            for(int i=0;i<9;i++){
                double amount=(lo+hi)/2;var candidate=hold(frame,placement,centre,lookYaw*amount,lookPitch*amount,bodyInverse,body[13],right,left,gp,gs,length(re),length(le));
                if(candidate==null)hi=amount;else{lo=amount;held=candidate;}
            }
        }
        placement=held.placement();double[] primary=held.primary(),support=held.support(),correction=held.correction();
        translate(placement,correction);for(int i=0;i<3;i++){primary[i]+=correction[i];support[i]+=correction[i];}
        right=attach(right,re,primary);left=attach(left,le,support);
        double error=Math.max(length(subtract(point(right,re[0],re[1],re[2]),primary)),length(subtract(point(left,le[0],le[1],le[2]),support)));
        if(!Double.isFinite(error)||error>.0001)throw new IllegalStateException("3P hand contact rejected: "+error);
        return new Pose(frame,toPart(body),toPart(right),toPart(left),placement,right,left,error,length(correction),toPart(matrix(frame,"tp_head_assist")),body,lower.gltfPelvis(),torso.waistInset());
    }
    private record Hold(double[] placement,double[] primary,double[] support,double[] correction){}
    private static Hold hold(RigFrame frame,double[] base,double[] centre,double yaw,double pitch,double[] bodyInverse,double neckY,
            double[] right,double[] left,double[] gp,double[] gs,double rr,double lr){
        double[] placement=multiply(around(centre,aim(yaw,pitch)),base);
        double[] primary=point(placement,gp[0],gp[1],gp[2]),support=point(placement,gs[0],gs[1],gs[2]);
        double[] intoBody=multiply(bodyInverse,placement);double rear=-Double.MAX_VALUE;
        for(int m=0;m<frame.model().meshes().size();m++)for(int v=0;v<frame.model().meshes().get(m).vertexCount();v++){
            double z=intoBody[2]*frame.vertex(m,v,0)+intoBody[6]*frame.vertex(m,v,1)+intoBody[10]*frame.vertex(m,v,2)+intoBody[14];rear=Math.max(rear,z);
        }
        var correction=ThirdPersonHoldConstraint.correction(position(right),position(left),primary,support,rr,lr,
            neckY-.16-Math.max(primary[1],support[1]),new double[]{bodyInverse[2],bodyInverse[6],bodyInverse[10]},-.127-rear);
        return correction.map(c->new Hold(placement,primary,support,c)).orElse(null);
    }
    public ThirdPersonAnimationController controller(){return controller;}
    public ThirdPersonAsset asset(){return asset;}
}
