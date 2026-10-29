package com.bobincraft.armament.client.animation.gltf;

import com.bobincraft.armament.api.WeaponStateSnapshot;
import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.AnimationController;
import com.bobincraft.armament.client.resource.AnimationEventsDefinition;
import java.util.*;
import java.util.function.Consumer;

/** Per-instance visual controller. Sampling is side-effect free with respect to markers/gameplay.
 * Pose storage is borrowed until next sample; calls belong to one simulation/render thread.
 */
public final class GltfAnimationController implements AnimationController<RigFrame> {
    public enum State { IDLE, PLAYING, TRANSITION }
    public enum Mode { LOOP, ONCE, HOLD }
    public record ClipRequest(String clip,Mode mode,double weight){
        public ClipRequest{Objects.requireNonNull(clip);Objects.requireNonNull(mode);unit(weight,"clip weight");}
    }
    /** Cosmetic metadata only. No server, item, damage, ammunition or sound API. */
    public record CosmeticEvent(UUID instance,long playback,int layer,String clip,String marker,WeaponId event,long loop,double clipTime){}
    private static final double[] IDENTITY_Q={0,0,0,1};
    private static final int MAX_LAYERS=8,MAX_EVENTS=256,MAX_EVENT_LOOPS=32;
    private final RigModel model;
    private final UUID instance;
    private final RigFrame frame;
    private final Layer[] layers=new Layer[MAX_LAYERS];
    private final double[] quaternionScratch=new double[12];
    private Map<String,List<AnimationEventsDefinition.Marker>> markers=Map.of();
    private long serial,mutation,sampledMutation=-1,droppedEvents;
    private double sampledTime=Double.NaN;
    private WeaponStateSnapshot snapshot;

    public GltfAnimationController(RigModel model){this(model,UUID.randomUUID());}
    public GltfAnimationController(RigModel model,UUID instance){this.model=Objects.requireNonNull(model);this.instance=Objects.requireNonNull(instance);frame=new RigFrame(model);}
    public RigModel model(){return model;} public UUID instance(){return instance;}public long droppedEvents(){return droppedEvents;}
    public void markers(AnimationEventsDefinition definition){
        Map<String,List<AnimationEventsDefinition.Marker>> bound=new HashMap<>();
        for(var clip:definition.clips()){
            AnimationClip imported=model.clip(clip.name());
            if(Math.abs(imported.duration()-clip.durationSeconds())>1e-5)throw new IllegalArgumentException("markers: duration differs from GLB clip "+clip.name());
            bound.put(clip.name(),clip.markers());
        }
        for(Layer layer:layers)if(layer!=null&&layer.players.length>0)throw new IllegalStateException("bind markers before starting clips");
        markers=Map.copyOf(bound);
    }
    public void play(String clip,Mode mode,double simulationSeconds,double fadeSeconds){
        layer(0,List.of(new ClipRequest(clip,mode,1)),NodeMask.all(model),false,1,simulationSeconds,fadeSeconds);
    }
    /** Ordered layers: override uses coverage, additive uses bind-relative local TRS deltas. */
    public void layer(int index,List<ClipRequest> requests,NodeMask mask,boolean additive,double weight,double now,double fadeSeconds){
        checkIndex(index);time(now);duration(fadeSeconds);unit(weight,"layer weight");Objects.requireNonNull(mask);
        if(mask.size()!=model.nodes().size()||requests.size()>8)throw new IllegalArgumentException("layer: mask/count mismatch");
        Layer current=layers[index];
        if(current!=null&&current.additive!=additive)throw new IllegalArgumentException("layer: additive mode is fixed until reset");
        Playback[] players=new Playback[requests.size()];
        for(int i=0;i<players.length;i++){
            ClipRequest request=requests.get(i);AnimationClip clip=model.clip(request.clip());
            if(request.mode()==Mode.LOOP&&clip.duration()<1e-6)throw new IllegalArgumentException("loop clip must be at least one microsecond long");
            players[i]=new Playback(clip,request.mode(),request.weight(),now,++serial,markers.getOrDefault(clip.name(),List.of()));
        }
        if(current==null){current=new Layer(additive);layers[index]=current;}
        current.prepareOutgoing(now);current.players=players;current.mask=mask;current.weight=weight;
        current.changed=now;current.fade=fadeSeconds;current.release=Math.max(0.05,fadeSeconds);mutation++;
    }
    public void stop(double now,double fadeSeconds){
        time(now);duration(fadeSeconds);
        for(int i=0;i<layers.length;i++)if(layers[i]!=null){Layer l=layers[i];l.prepareOutgoing(now);l.players=new Playback[0];l.changed=now;l.fade=fadeSeconds;}
        mutation++;
    }
    /** Seek never replays traversed/skipped cosmetic markers, even after seeking backwards. */
    public void seek(int index,double clipSeconds,double now){
        checkIndex(index);time(now);time(clipSeconds);Layer l=layers[index];if(l==null)return;
        for(int i=0;i<l.players.length;i++){Playback p=l.players[i];p.offset=clipSeconds;p.started=now;p.cursor=Math.max(p.cursor,clipSeconds);}
        mutation++;
    }
    @Override public void accept(WeaponStateSnapshot state){
        Objects.requireNonNull(state);
        if(snapshot!=null&&(!snapshot.ownerId().equals(state.ownerId())||!snapshot.instanceId().equals(state.instanceId())||snapshot.hand()!=state.hand()))reset();
        snapshot=state; // Action IDs are deliberately not interpreted as GLB clip names.
    }
    @Override public RigFrame sample(long serverTick,float tickDelta){
        if(serverTick<0||!Float.isFinite(tickDelta)||tickDelta<0||tickDelta>=1)throw new IllegalArgumentException("clock: expected non-negative tick and tickDelta in [0,1)");
        return sampleSeconds((serverTick+(double)tickDelta)/20.0);
    }
    public RigFrame sampleSeconds(double now){
        time(now);
        if(now==sampledTime&&mutation==sampledMutation)return frame;
        model.copyBind(frame.localTrs);
        for(int l=0;l<layers.length;l++)if(layers[l]!=null){
            Layer layer=layers[l];layer.evaluate(now);
            for(int n=0;n<model.nodes().size();n++){
                double coverage=layer.coverage[n];if(coverage==0||model.nodes().get(n).fixedMatrix())continue;int o=n*10;
                if(!layer.additive)RigMath.mixTrs(frame.localTrs,o,layer.target,o,coverage,frame.localTrs,o);
                else{
                    for(int k=0;k<3;k++){
                        frame.localTrs[o+k]+=(layer.target[o+k]-model.bind(o+k))*coverage;
                        frame.localTrs[o+7+k]*=1+(layer.target[o+7+k]/model.bind(o+7+k)-1)*coverage;
                    }
                    for(int k=0;k<4;k++)quaternionScratch[k]=model.bind(o+3+k)*(k<3?-1:1);
                    RigMath.quaternionMultiply(quaternionScratch,0,layer.target,o+3,quaternionScratch,4);
                    RigMath.slerp(IDENTITY_Q,0,quaternionScratch,4,coverage,quaternionScratch,8);
                    RigMath.quaternionMultiply(frame.localTrs,o+3,quaternionScratch,8,frame.localTrs,o+3);
                    RigMath.normalizeQuaternion(frame.localTrs,o+3);
                }
            }
        }
        frame.update();sampledTime=now;sampledMutation=mutation;return frame;
    }
    /** Called by simulation ticks, never by renderer; repeated/render-only samples emit nothing. */
    public void advance(long simulationTick,Consumer<CosmeticEvent> sink){
        if(simulationTick<0)throw new IllegalArgumentException("negative tick");Objects.requireNonNull(sink);
        double now=simulationTick/20.0;time(now);int remaining=MAX_EVENTS;
        for(int l=0;l<layers.length;l++)if(layers[l]!=null){
            Layer layer=layers[l];boolean visible=layer.weight>0&&enabled(layer.mask);
            for(int p=0;p<layer.players.length;p++){
                Playback player=layer.players[p];double elapsed=player.elapsed(now);
                if(now<player.started||elapsed<=player.cursor)continue;
                double previous=player.cursor;player.cursor=elapsed; // Consume before callbacks, including exceptional/reentrant paths.
                double end=player.mode==Mode.LOOP?elapsed:Math.min(elapsed,player.clip.duration());
                if(visible&&player.weight>0&&!player.markers.isEmpty()){
                    double span=player.clip.duration();long first=0,last=0;
                    if(player.mode==Mode.LOOP){first=Math.max(0,(long)Math.floor(Math.max(0,previous)/span));last=(long)Math.floor(end/span);}
                    if(last-first>=MAX_EVENT_LOOPS){droppedEvents+=last-first-MAX_EVENT_LOOPS+1;first=last-MAX_EVENT_LOOPS+1;}
                    for(long loop=first;loop<=last;loop++)for(int m=0;m<player.markers.size();m++){
                        var marker=player.markers.get(m);double at=loop*span+marker.timeSeconds();
                        if(at>previous&&at<=end){
                            if(remaining>0){remaining--;sink.accept(new CosmeticEvent(instance,player.id,l,player.clip.name(),marker.name(),marker.event(),loop,marker.timeSeconds()));}
                            else droppedEvents++;
                        }
                    }
                }
            }
        }
    }
    public State state(double now){
        time(now);boolean playing=false;
        for(int i=0;i<layers.length;i++)if(layers[i]!=null){
            Layer layer=layers[i];
            if(now<layer.changed+layer.fade)return State.TRANSITION;
            for(int p=0;p<layer.players.length;p++){
                Playback player=layer.players[p];if(player.weight==0||layer.weight==0||!enabled(layer.mask))continue;
                double elapsed=player.elapsed(now);
                if(player.mode!=Mode.ONCE||elapsed<=player.clip.duration())playing=true;
                else if(elapsed<player.clip.duration()+layer.release)return State.TRANSITION;
            }
        }
        return playing?State.PLAYING:State.IDLE;
    }
    @Override public void reset(){Arrays.fill(layers,null);snapshot=null;sampledTime=Double.NaN;mutation++;model.copyBind(frame.localTrs);frame.update();}
    private static void checkIndex(int i){if(i<0||i>=MAX_LAYERS)throw new IllegalArgumentException("layer index must be 0..7");}
    private static boolean enabled(NodeMask mask){for(int n=0;n<mask.size();n++)if(mask.weight(n)>0)return true;return false;}
    private static void time(double time){if(!Double.isFinite(time)||time<0||time>1e9)throw new IllegalArgumentException("simulation time must be finite in 0..1e9 seconds");}
    private static void duration(double d){if(!Double.isFinite(d)||d<0||d>60)throw new IllegalArgumentException("fade must be in 0..60 seconds");}
    private static void unit(double w,String name){if(!Double.isFinite(w)||w<0||w>1)throw new IllegalArgumentException(name+" must be in 0..1");}

    private static final class Playback {
        final AnimationClip clip;final Mode mode;final double weight;final long id;
        final List<AnimationEventsDefinition.Marker> markers;
        double started,offset,cursor=-Double.MIN_VALUE;
        Playback(AnimationClip clip,Mode mode,double weight,double now,long id,List<AnimationEventsDefinition.Marker> markers){this.clip=clip;this.mode=mode;this.weight=weight;started=now;this.id=id;this.markers=markers;}
        double elapsed(double now){return Math.max(0,now-started+offset);}
        double sampleTime(double now){double t=elapsed(now);return mode==Mode.LOOP?t%clip.duration():Math.min(t,clip.duration());}
        double activeWeight(double now,double release){double t=elapsed(now);return weight*(mode==Mode.ONCE?Math.max(0,1-Math.max(0,t-clip.duration())/release):1);}
    }
    private final class Layer {
        final boolean additive;
        final double[] target=new double[frame.localTrs.length],scratch=new double[target.length],from=new double[target.length];
        final double[] coverage=new double[model.nodes().size()],fromCoverage=new double[coverage.length];
        Playback[] players=new Playback[0];NodeMask mask=NodeMask.all(model);
        Playback[] outgoing;
        NodeMask outgoingMask;
        double outgoingWeight,outgoingRelease;
        double weight,changed,fade,release=0.15;
        Layer(boolean additive){this.additive=additive;model.copyBind(target);model.copyBind(from);}
        void capture(){System.arraycopy(target,0,from,0,target.length);System.arraycopy(coverage,0,fromCoverage,0,coverage.length);}
        void prepareOutgoing(double now){
            boolean interrupted=now<changed+fade;evaluate(now);capture();
            // Ordinary crossfades advance both clip groups. An interrupted blend captures
            // its currently mixed pose, bounding history and preserving C0 continuity.
            outgoing=interrupted||players.length==0?null:players;
            outgoingMask=mask;outgoingWeight=weight;outgoingRelease=release;
        }
        void group(Playback[] group,NodeMask mask,double weight,double release,double now,double[] result,double[] cover){
            model.copyBind(result);double sum=0;
            for(int p=0;p<group.length;p++){
                Playback player=group[p];double w=player.activeWeight(now,release);if(w==0)continue;
                model.copyBind(scratch);player.clip.sample(player.sampleTime(now),scratch);
                if(sum==0)System.arraycopy(scratch,0,result,0,result.length);
                else for(int n=0;n<model.nodes().size();n++)RigMath.mixTrs(result,n*10,scratch,n*10,w/(sum+w),result,n*10);
                sum+=w;
            }
            for(int n=0;n<cover.length;n++)cover[n]=Math.min(1,sum)*weight*mask.weight(n);
        }
        void evaluate(double now){
            group(players,mask,weight,release,now,target,coverage);
            double progress=fade==0?1:Math.max(0,Math.min(1,(now-changed)/fade));
            if(progress<1&&outgoing!=null)group(outgoing,outgoingMask,outgoingWeight,outgoingRelease,now,from,fromCoverage);
            for(int n=0;n<coverage.length;n++){
                double a=fromCoverage[n]*(1-progress),b=coverage[n]*progress;
                coverage[n]=a+b;
                if(a>0)RigMath.mixTrs(from,n*10,target,n*10,b/(a+b),target,n*10);
            }
        }
    }
}
