package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.client.animation.gltf.*;
import java.util.*;

/** Per-view presentation only. Authored clips + bounded camera inertia, no item or gameplay state. */
public final class FirstPersonWeaponPresenter {
    private final FirstPersonAsset asset;private final GltfAnimationController controller;
    private final FirstPersonActionStateMachine actions;
    private final NodeMask mask;private final GripAttachments grips;private final DampedViewInertia inertia;
    private final double[] rootTrs={0,0,0,0,0,0,1,1,1,1},lagTrs={0,0,0,0,0,0,1,1,1,1};
    private final double[] root=new double[16],lag=new double[16],offsetLag=new double[16],modelToView=new double[16],viewProjection=new double[16],worldTransform=new double[16],inverse=new double[16],normal=new double[16];
    private final double[] viewInverse=new double[16];
    private RigFrame pose;private String rejection="not_sampled";private double minDepth;
    private boolean movementInput,movementLayer,inertiaRequested;
    public FirstPersonWeaponPresenter(FirstPersonAsset asset){
        this.asset=asset;controller=new GltfAnimationController(asset.model().rig());mask=NodeMask.all(controller.model());grips=new GripAttachments(asset.profile(),asset.sockets());inertia=new DampedViewInertia(asset.profile().inertia());
        var durations=new EnumMap<FirstPersonActionStateMachine.Action,Double>(FirstPersonActionStateMachine.Action.class);
        for(var action:FirstPersonActionStateMachine.Action.values())durations.put(action,controller.model().clip(asset.profile().clips().get(action.key())).duration());
        actions=new FirstPersonActionStateMachine(durations);
        inertiaRequested=asset.profile().inertia().enabled();
        for(int i=0;i<3;i++)rootTrs[i]=asset.profile().cameraOffset().get(i);for(int i=0;i<4;i++)rootTrs[i+3]=asset.profile().cameraRotation().get(i);RigMath.compose(rootTrs,0,root,0);
        calibration();
    }
    public void show(double now){
        calibration();
    }
    /** Calibration is deliberately independent of time, motion layers and sway. */
    public void calibration(){actions.calibration();movementInput=movementLayer=false;controller.reset();controller.play(asset.profile().clips().get("idle"),GltfAnimationController.Mode.HOLD,0,0);inertia.enabled(false);rejection="not_sampled";}
    public boolean staticIdle(){return actions.staticIdle();}
    public void animation(String action,double now){
        boolean wasStatic=staticIdle();
        actions.request(FirstPersonActionStateMachine.Action.parse(action),now);
        if(wasStatic)inertia.enabled(inertiaRequested);
        playAction(now);
        movement(movementInput&&actions.allowsMovement(),now);
    }
    public void tick(long tick,double yaw,double pitch,boolean moving){
        movementInput=moving;
        if(staticIdle())return;
        double now=tick/20.0;inertia.tick(tick,yaw,pitch);
        if(actions.tick(now))playAction(now);
        movement(moving&&actions.allowsMovement(),now);
    }
    private void playAction(double now){
        var action=actions.action();
        controller.play(asset.profile().clips().get(action.key()),action.mode(),now,asset.profile().transitionSeconds(),GltfAnimationController.BlendCurve.SMOOTHSTEP);
    }
    private void movement(boolean on,double now){
        if(on==movementLayer)return;movementLayer=on;
        controller.layer(1,on?List.of(new GltfAnimationController.ClipRequest(asset.profile().clips().get("move"),GltfAnimationController.Mode.LOOP,1)):List.of(),mask,true,1,now,asset.profile().transitionSeconds(),GltfAnimationController.BlendCurve.SMOOTHSTEP);
    }
    /** Buffers are borrowed until next sample. All arguments are read-only camera-space matrices. */
    public boolean sample(long tick,float delta,double[] handView,double[] projection,double[] cameraToWorld){
        if(tick<0||!Float.isFinite(delta)||delta<0||delta>=1)throw new IllegalArgumentException("clock: expected non-negative tick and tickDelta in [0,1)");
        pose=staticIdle()?controller.sampleSeconds(0):controller.sample(tick,delta);
        if(!grips.matches(pose)){rejection="grip_contact";return false;}
        double yaw=Math.toRadians(inertia.yaw(delta)),pitch=Math.toRadians(inertia.pitch(delta));
        lagTrs[0]=inertia.yaw(delta)*asset.profile().inertia().metresPerDegree();lagTrs[1]=-inertia.pitch(delta)*asset.profile().inertia().metresPerDegree();
        lagTrs[3]=Math.sin(pitch/2)*Math.cos(yaw/2);lagTrs[4]=Math.cos(pitch/2)*Math.sin(yaw/2);lagTrs[5]=-Math.sin(pitch/2)*Math.sin(yaw/2);lagTrs[6]=Math.cos(pitch/2)*Math.cos(yaw/2);
        RigMath.compose(lagTrs,0,lag,0);RigMath.multiply(root,0,lag,0,offsetLag,0);RigMath.multiply(handView,0,offsetLag,0,modelToView,0);
        minDepth=Double.POSITIVE_INFINITY;
        for(int m=0;m<pose.model().meshes().size();m++)for(int v=0;v<pose.model().meshes().get(m).vertexCount();v++){
            double depth=-(modelToView[2]*pose.vertex(m,v,0)+modelToView[6]*pose.vertex(m,v,1)+modelToView[10]*pose.vertex(m,v,2)+modelToView[14]);minDepth=Math.min(minDepth,depth);
        }
        if(minDepth<Math.max(asset.profile().cameraClearance(),PresentationMath.nearPlane(projection)+0.01)){rejection="camera_clearance";return false;}
        RigMath.multiply(projection,0,modelToView,0,viewProjection,0);RigMath.multiply(cameraToWorld,0,modelToView,0,worldTransform,0);PresentationMath.normalMatrix(worldTransform,inverse,normal);
        RigMath.inverseAffine(modelToView,0,viewInverse,0);
        rejection="ready";return true;
    }
    public void inertia(boolean enabled){inertiaRequested=enabled;if(!staticIdle()&&inertia.enabled()!=enabled)inertia.enabled(enabled);}
    public boolean inertiaEnabled(){return inertia.enabled();}
    public boolean inertiaRequested(){return inertiaRequested;}
    /** Requested locomotion layer; its incoming/outgoing pose still crossfades. */
    public boolean movementLayerEnabled(){return movementLayer;}
    public String action(){return actions.action().key();}public FirstPersonActionStateMachine.Phase phase(){return actions.phase();}
    public String rejection(){return rejection;}public double minimumDepth(){return minDepth;}
    public double contactMetres(){return grips.positionError();}public double contactDegrees(){return grips.angleError();}
    public RigFrame pose(){return pose;}public FirstPersonAsset asset(){return asset;}
    public double cameraInModel(int axis){if(axis<0||axis>2)throw new IllegalArgumentException("camera axis");return viewInverse[12+axis];}
    /** Last accepted sample; diagnostic copies are requested explicitly, never in the render loop. */
    public void copyModelToView(double[] destination){copyDiagnosticMatrix(modelToView,destination);}
    public void copyViewProjection(double[] destination){copyDiagnosticMatrix(viewProjection,destination);}
    private void copyDiagnosticMatrix(double[] source,double[] destination){
        if(!rejection.equals("ready"))throw new IllegalStateException("first-person diagnostics require an accepted camera sample");
        if(destination.length!=16)throw new IllegalArgumentException("matrix output length");
        System.arraycopy(source,0,destination,0,16);
    }
    public void copyProjection(float[] destination){copy(viewProjection,destination);}public void copyNormals(float[] destination){copy(normal,destination);}
    private static void copy(double[] source,float[] out){if(out.length!=16)throw new IllegalArgumentException("matrix output length");for(int i=0;i<16;i++)out[i]=(float)source[i];}
}
