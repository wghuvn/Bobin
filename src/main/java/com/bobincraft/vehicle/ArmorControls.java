package com.bobincraft.vehicle;

/** Bounded game-vehicle controls, shared by server validation and deterministic regression tests. */
public final class ArmorControls {
    public static final double SHELL_SPEED=8, SHELL_GRAVITY=.018;
    public static final double BURIAL_DEPTH=1.05;
    public static final double CLIMB_PER_TICK=.055, MAX_STEP=1.05;
    public static final int MAGAZINE=24,RELOAD_TICKS=60,INPUT_TIMEOUT=10;
    private ArmorControls() { }
    public record Input(float forward,float steer,float yaw,float pitch,boolean fire) {
        public static final Input STOP=new Input(0,0,0,0,false);
        public boolean valid(){return Float.isFinite(forward)&&Float.isFinite(steer)&&Float.isFinite(yaw)&&Float.isFinite(pitch)&&Math.abs(forward)<=1&&Math.abs(steer)<=1&&Math.abs(yaw)<=360000&&Math.abs(pitch)<=90;}
    }
    public static double speed(double current,float throttle,boolean buried){
        if(buried)return 0;double target=throttle*(throttle<0?.095:.19);return current+(target-current)*.14;
    }
    public static float slew(float current,float target,float step){
        float difference=(target-current)%360;if(difference>180)difference-=360;if(difference< -180)difference+=360;
        return (current+Math.max(-step,Math.min(step,difference)))%360;
    }
    public static boolean fireReady(boolean allowed,int ammo,int cooldown,float yawError,float pitchError){return allowed&&ammo>0&&cooldown==0&&Math.abs(yawError)<4&&Math.abs(pitchError)<3;}
}
