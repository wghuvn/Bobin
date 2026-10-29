package com.bobincraft.armament.client.animation.gltf;

import java.util.*;

/** Immutable glTF channels; CUBICSPLINE values are [in tangent, value, out tangent]. */
public final class AnimationClip {
    public enum Path { TRANSLATION, ROTATION, SCALE }
    public enum Interpolation { STEP, LINEAR, CUBICSPLINE }
    public static final class Channel {
        private final int node,components;
        private final Path path;
        private final Interpolation interpolation;
        private final double[] times,values;
        public Channel(int node,Path path,Interpolation interpolation,double[] times,double[] values) {
            this.node=node;this.path=Objects.requireNonNull(path);this.interpolation=Objects.requireNonNull(interpolation);
            components=path==Path.ROTATION?4:3;this.times=times.clone();this.values=values.clone();
            if(node<0||times.length==0||times.length>100_000||values.length!=times.length*components*(interpolation==Interpolation.CUBICSPLINE?3:1))
                throw new IllegalArgumentException("channel: invalid node/keyframe count");
            double previous=-1;
            for(double time:times){if(!Double.isFinite(time)||time<0||time<=previous||time>3600)throw new IllegalArgumentException("channel: timestamps must strictly increase within 0..3600s");previous=time;}
            for(double v:values)if(!Double.isFinite(v)||Math.abs(v)>1e6)throw new IllegalArgumentException("channel: non-finite/excessive output");
            if(path==Path.ROTATION)for(int k=0;k<times.length;k++){
                int offset=interpolation==Interpolation.CUBICSPLINE?(k*3+1)*4:k*4;double norm=0;
                for(int j=0;j<4;j++)norm+=values[offset+j]*values[offset+j];
                if(Math.abs(norm-1)>1e-4)throw new IllegalArgumentException("channel: rotation keys must be unit quaternions");
            }
        }
        public int node(){return node;} public Path path(){return path;}
        public Interpolation interpolation(){return interpolation;}
        public double end(){return times[times.length-1];}
        public void sample(double seconds,double[] out,int o) {
            if(!Double.isFinite(seconds))throw new IllegalArgumentException("sample: non-finite time");
            int hi=Arrays.binarySearch(times,seconds);
            if(hi>=0){key(hi,out,o);return;}
            hi=-hi-1;
            if(hi==0){key(0,out,o);return;}
            if(hi==times.length){key(times.length-1,out,o);return;}
            int lo=hi-1;
            if(interpolation==Interpolation.STEP){key(lo,out,o);return;}
            double dt=times[hi]-times[lo],t=(seconds-times[lo])/dt;
            if(interpolation==Interpolation.LINEAR){
                if(path==Path.ROTATION)RigMath.slerp(values,lo*4,values,hi*4,t,out,o);
                else for(int c=0;c<components;c++)out[o+c]=values[lo*components+c]*(1-t)+values[hi*components+c]*t;
            }else{
                double t2=t*t,t3=t2*t,h00=2*t3-3*t2+1,h10=t3-2*t2+t,h01=-2*t3+3*t2,h11=t3-t2;
                for(int c=0;c<components;c++)out[o+c]=h00*values[(lo*3+1)*components+c]+h10*dt*values[(lo*3+2)*components+c]
                        +h01*values[(hi*3+1)*components+c]+h11*dt*values[hi*3*components+c];
                // glTF cubic tangents retain their exported signs; do NOT flip hemispheres.
                if(path==Path.ROTATION)RigMath.normalizeQuaternion(out,o);
            }
        }
        private void key(int k,double[] out,int o){System.arraycopy(values,(interpolation==Interpolation.CUBICSPLINE?k*3+1:k)*components,out,o,components);}
        int offset(){return path==Path.TRANSLATION?0:path==Path.ROTATION?3:7;}
    }
    private final String name;
    private final List<Channel> channels;
    private final double duration;
    public AnimationClip(String name,List<Channel> channels) {
        this.name=Objects.requireNonNull(name);this.channels=List.copyOf(channels);
        if(name.isBlank()||name.length()>128||channels.isEmpty()||channels.size()>768)throw new IllegalArgumentException("clip: invalid name/channel count");
        Set<String> targets=new HashSet<>();double end=0;
        for(Channel c:channels){if(!targets.add(c.node()+":"+c.path()))throw new IllegalArgumentException("clip: duplicate node/path target");end=Math.max(end,c.end());}
        duration=end;
    }
    public String name(){return name;} public List<Channel> channels(){return channels;} public double duration(){return duration;}
    public void sample(double seconds,double[] localTrs) {
        for(int i=0;i<channels.size();i++){Channel c=channels.get(i);c.sample(seconds,localTrs,c.node()*10+c.offset());}
    }
}
