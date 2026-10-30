package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.client.animation.gltf.*;
import java.util.List;

/** Independent 3P action/recovery, gait and stance layers on a shared semantic clock. */
public final class ThirdPersonAnimationController {
    private final GltfAnimationController controller;
    private final NodeMask actionMask,motionMask,stanceMask;
    private PresentationAction action;
    private int gait=-1;
    private boolean crouched,movementObserved;
    private String clip="idle",pending;
    private double pendingAt,recoveryEnd;
    private double lookFrom,lookTo,lookChanged,lookFade;
    public ThirdPersonAnimationController(ThirdPersonAsset asset){
        controller=new GltfAnimationController(asset.model().rig());
        double[] mask=new double[controller.model().nodes().size()];var body=NodeMask.subtree(controller.model(),"tp_body");
        for(int n=0;n<mask.length;n++)mask[n]=body.weight(n);
        mask[controller.model().node("tp_head_assist")]=1;actionMask=new NodeMask(mask);
        double[] motion=new double[controller.model().nodes().size()];
        motion[controller.model().node("tp_motion")]=motion[controller.model().node("tp_weapon_motion")]=1;
        motionMask=new NodeMask(motion);stanceMask=single("tp_stance");
    }
    private NodeMask single(String name){double[] w=new double[controller.model().nodes().size()];w[controller.model().node(name)]=1;return new NodeMask(w);}
    public void update(PresentationAction next,double now,boolean moving,boolean running,boolean crouching){
        advance(now);movementObserved=moving;
        if(!next.equals(action)){
            double oldLook=lookWeight(now);
            var previous=action;action=next;pending=null;
            lookFrom=previous==null?look(next.action()):oldLook;lookTo=look(next.action());lookChanged=now;
            lookFade=previous==null?0:next.action()==PresentationAction.Action.AIM?.42:previous.action()==PresentationAction.Action.AIM?.65:.38;
            boolean automaticIdle=previous!=null&&!next.calibration()&&next.action()==PresentationAction.Action.IDLE&&
                    (previous.action()==PresentationAction.Action.INSPECT||previous.action()==PresentationAction.Action.RAISE)&&
                    next.started()>=previous.started()+controller.model().clip(previous.action().key()).duration()-1e-5;
            if(automaticIdle){
                pending=previous.action()==PresentationAction.Action.INSPECT?"inspect_recover":"idle";
                pendingAt=next.started()+.18;recoveryEnd=pendingAt+(pending.equals("inspect_recover")?.9:.55);
            }else{
                String selected=next.action()==PresentationAction.Action.MOVE?"idle":next.action().key();
                double fade=previous==null?0:next.action()==PresentationAction.Action.AIM?.42:previous.action()==PresentationAction.Action.AIM?.65:.38;
                play(selected,next.started(),now,fade,next.calibration());recoveryEnd=now+fade;
            }
        }
        int desired=next.calibration()?0:(moving||next.action()==PresentationAction.Action.MOVE)?(running?2:1):0;
        if(desired!=gait){
            controller.layer(1,desired==0?List.of():List.of(new GltfAnimationController.ClipRequest(desired==2?"run":"move",GltfAnimationController.Mode.LOOP,1)),motionMask,true,1,now,gait==-1?0:.30,GltfAnimationController.BlendCurve.SMOOTHSTEP);
            if(desired!=0)controller.seek(1,next.action()==PresentationAction.Action.MOVE?Math.max(0,now-next.started()):now,now);
            gait=desired;
        }
        if(crouching!=crouched){
            controller.layer(2,crouching?List.of(new GltfAnimationController.ClipRequest("crouch",GltfAnimationController.Mode.HOLD,1)):List.of(),stanceMask,true,1,now,.30,GltfAnimationController.BlendCurve.SMOOTHSTEP);
            crouched=crouching;
        }
    }
    private void play(String selected,double started,double now,double fade,boolean calibration){
        clip=selected;
        var mode=selected.equals("idle")?GltfAnimationController.Mode.LOOP:GltfAnimationController.Mode.HOLD;
        controller.layer(0,List.of(new GltfAnimationController.ClipRequest(selected,mode,1)),actionMask,false,1,now,fade,GltfAnimationController.BlendCurve.SMOOTHSTEP);
        controller.seek(0,calibration?0:Math.max(0,now-started),now);
    }
    private void advance(double now){
        if(pending==null||now<pendingAt)return;
        String next=pending;double at=pendingAt;pending=null;
        play(next,next.equals("idle")?action.started():at,at,next.equals("idle")?.55:.16,false);
        if(next.equals("inspect_recover")){pending="idle";pendingAt=at+.9;}
        if(pending!=null&&now>=pendingAt)advance(now);
    }
    public RigFrame sample(double now){return sample(now,Double.NaN);}
    public RigFrame sample(double now,double stridePhase){
        if(action==null)throw new IllegalStateException("semantic state required");
        advance(now);
        if(action.calibration())controller.seek(0,0,now);
        if(gait>0&&Double.isFinite(stridePhase)){
            double phase=!movementObserved&&action.action()==PresentationAction.Action.MOVE?now-action.started():stridePhase;
            controller.seek(1,((phase%1)+1)%1,now);
        }
        return controller.sampleSeconds(now);
    }
    private static double look(PresentationAction.Action action){return action==PresentationAction.Action.AIM?1:0;}
    public double lookWeight(double now){
        double u=lookFade==0?1:Math.max(0,Math.min(1,(now-lookChanged)/lookFade));u=u*u*u*(10+u*(-15+6*u));
        return lookFrom+(lookTo-lookFrom)*u;
    }
    public String phase(double now){
        if(action==null)return "inactive";
        if(pending!=null&& !clip.equals("inspect_recover"))return "end_hold";
        if(clip.equals("inspect_recover")||now<recoveryEnd)return "recovery_or_blend";
        return action.calibration()?"calibration":action.action()==PresentationAction.Action.AIM?"aim_hold":"playing";
    }
    public PresentationAction action(){return action;}
    public String clip(){return clip;}
    public int gait(){return gait;}
    public boolean crouched(){return crouched;}
}
