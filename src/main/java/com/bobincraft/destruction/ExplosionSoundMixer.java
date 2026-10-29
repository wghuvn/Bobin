package com.bobincraft.destruction;

import java.util.*;

/** Auditory-only bounded grouping. No damage/event IDs are discarded by this class. */
public final class ExplosionSoundMixer<T> {
    public record Emission<T>(double x, double y, double z, T sound, float volume, float pitch) {}
    private static final class Group<T> {
        String dimension; T sound; double x,y,z; long created,due; float volume,pitch; int count=1;
    }
    private record Heard<T>(String dimension,T sound,double x,double y,double z,long created,long due,long emitted) {}
    private final List<Group<T>> groups=new ArrayList<>();
    private final ArrayDeque<Heard<T>> recent=new ArrayDeque<>();
    private final int capacity;
    public ExplosionSoundMixer(int capacity) {this.capacity=Math.max(1,capacity);}

    public boolean offer(String dimension,double x,double y,double z,T sound,float volume,float pitch,long tick,int delay) {
        if(dimension==null||sound==null||!Double.isFinite(x+y+z)||!Float.isFinite(volume)||!Float.isFinite(pitch)
                ||volume<=0||pitch<=0||delay<0||delay>200)return false;
        long due=tick+Math.max(1,delay); // Collect a complete server tick; never truncate a voice already playing.
        pruneRecent(dimension,tick);
        for(var g:groups) if(g.dimension.equals(dimension)&&g.sound.equals(sound)
                &&tick>=g.created&&tick-g.created<=2&&Math.abs(g.due-due)<=3
                &&(x-g.x)*(x-g.x)+(y-g.y)*(y-g.y)+(z-g.z)*(z-g.z)<=64) {
            g.count++; if(volume>g.volume){g.volume=volume;g.pitch=pitch;}
            return true;
        }
        // Sympathetic charges arrive two ticks later, after initial voices have left the queue.
        // Suppress only the matching short-window onset; never stop/replay or retroactively amplify that voice.
        for(var h:recent) if(h.dimension().equals(dimension)&&h.sound().equals(sound)
                &&tick>=h.created()&&tick-h.created()<=2&&Math.abs(h.due()-due)<=3
                &&(x-h.x())*(x-h.x())+(y-h.y())*(y-h.y())+(z-h.z())*(z-h.z())<=64)return true;
        if(groups.size()>=capacity)return false;
        var g=new Group<T>();g.dimension=dimension;g.sound=sound;g.x=x;g.y=y;g.z=z;
        g.volume=volume;g.pitch=pitch;g.created=tick;g.due=due;groups.add(g);return true;
    }
    public List<Emission<T>> drain(String dimension,long tick) {
        pruneRecent(dimension,tick);
        var out=new ArrayList<Emission<T>>();
        var it=groups.iterator();while(it.hasNext()) {
            var g=it.next();if(!g.dimension.equals(dimension)||tick<g.due)continue;
            float gain=(float)Math.min(1.35,Math.sqrt(1+.12*(g.count-1)));
            out.add(new Emission<>(g.x,g.y,g.z,g.sound,g.volume*gain,g.pitch));
            while(recent.size()>=capacity)recent.removeFirst();
            recent.addLast(new Heard<>(g.dimension,g.sound,g.x,g.y,g.z,g.created,g.due,tick));
            it.remove();
        }
        return out;
    }
    public int size(){return groups.size();}
    int recentSize(){return recent.size();}
    private void pruneRecent(String dimension,long tick) {
        recent.removeIf(h->h.dimension().equals(dimension)&&(tick<h.emitted()||tick-h.emitted()>3));
    }
    public void clear(){groups.clear();recent.clear();}
}
