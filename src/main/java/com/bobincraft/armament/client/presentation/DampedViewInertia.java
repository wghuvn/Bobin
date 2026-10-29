package com.bobincraft.armament.client.presentation;

/** Analytic critically damped spring, advanced only by simulation ticks. No frame dt accumulation. */
public final class DampedViewInertia {
    private final FirstPersonProfile.Inertia settings;
    private long tick=-1;
    private double yaw,pitch,yawLag,pitchLag,yawVelocity,pitchVelocity,previousYawLag,previousPitchLag;
    private boolean enabled;
    public DampedViewInertia(FirstPersonProfile.Inertia settings){this.settings=settings;enabled=settings.enabled();}
    public void enabled(boolean enabled){this.enabled=enabled;reset();}
    public boolean enabled(){return enabled;}
    public void reset(){tick=-1;yawLag=pitchLag=yawVelocity=pitchVelocity=previousYawLag=previousPitchLag=0;}
    public void tick(long tick,double cameraYaw,double cameraPitch){
        if(tick<0||!Double.isFinite(cameraYaw)||!Double.isFinite(cameraPitch))throw new IllegalArgumentException("inertia: invalid clock/angles");
        if(tick==this.tick)return;
        if(this.tick<0||tick<this.tick||tick-this.tick>20){this.tick=tick;yaw=cameraYaw;pitch=cameraPitch;previousYawLag=yawLag=previousPitchLag=pitchLag=0;yawVelocity=pitchVelocity=0;return;}
        double dt=(tick-this.tick)/20.0;
        previousYawLag=yawLag;previousPitchLag=pitchLag;
        double yawTarget=enabled?PresentationMath.clamp(-PresentationMath.wrapDegrees(cameraYaw-yaw)/dt*settings.lagSeconds(),settings.maxYawDegrees()):0;
        double pitchTarget=enabled?PresentationMath.clamp(-(cameraPitch-pitch)/dt*settings.lagSeconds(),settings.maxPitchDegrees()):0;
        double omega=2*Math.PI*settings.frequencyHz(),decay=Math.exp(-omega*dt);
        double a=yawLag-yawTarget,b=yawVelocity+omega*a;yawLag=yawTarget+(a+b*dt)*decay;yawVelocity=(yawVelocity-omega*b*dt)*decay;
        a=pitchLag-pitchTarget;b=pitchVelocity+omega*a;pitchLag=pitchTarget+(a+b*dt)*decay;pitchVelocity=(pitchVelocity-omega*b*dt)*decay;
        yawLag=PresentationMath.clamp(yawLag,settings.maxYawDegrees());pitchLag=PresentationMath.clamp(pitchLag,settings.maxPitchDegrees());
        yaw=cameraYaw;pitch=cameraPitch;this.tick=tick;
    }
    public double yaw(float delta){return enabled?previousYawLag+(yawLag-previousYawLag)*delta:0;}
    public double pitch(float delta){return enabled?previousPitchLag+(pitchLag-previousPitchLag)*delta:0;}
}
