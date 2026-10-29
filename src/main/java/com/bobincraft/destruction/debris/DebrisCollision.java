package com.bobincraft.destruction.debris;

import java.util.*;
import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Deterministic rigid voxel-patch solver. Oriented source-box contacts, continuous translation and bounded angular sweeps. */
public final class DebrisCollision {
    public record Obstacle(B box,long source,DebrisShape shape) {
        public Obstacle(B box,long source){this(box,source,new DebrisShape(box,Q.IDENTITY,V.ZERO));}
        public static Obstacle rigid(B local,Q rotation,V position,long source){var shape=new DebrisShape(local,rotation,position);return new Obstacle(shape.bounds,source,shape);}
    }
    public interface Environment { List<Obstacle> query(B swept); }
    public static final int MAX_SUBSTEPS=16,MAX_OBSTACLES=4096;
    private DebrisCollision(){}
    public static void tick(DebrisFragment f,Environment environment) {
        if(!f.visible()||!f.complete())return;
        B bounds=f.bounds();if(bounds==null)return;
        double radius=f.localRadius();
        V v=f.velocity.add(new V(0,-.04,0)).limit(1.5),omega=f.angularVelocity.limit(.12);
        double arc=Math.max(omega.length(),.12)*radius*3;
        B sweep=bounds.union(bounds.offset(v)).expand(arc+.08);
        List<Obstacle> obstacles=environment.query(sweep);
        if(obstacles==null||obstacles.size()>MAX_OBSTACLES){f.pause();return;}
        if(f.phase==Phase.PAUSED_UNLOADED){f.phase=f.resume;f.pauseReason="";f.changed(false);}
        if(!recoverOverlap(f,obstacles)){f.pause("OVERLAP_RETRY");f.retryCount++;return;}
        f.retryCount=0;
        if(f.phase==Phase.SLEEPING) {
            Set<Long> support=contacts(f,obstacles);
            if(!support.isEmpty()&&stable(f,obstacles)){if(!support.equals(f.supports)){f.supports=support;f.changed(false);}return;}
            f.wake();
        }
        if(f.phase==Phase.ANIMATING){if(++f.age<2)return;f.phase=Phase.FALLING;}
        V contactPivot=null;
        List<Support> currentSupports = supports(f, obstacles);
        if(!currentSupports.isEmpty()&&!stableFromSupports(f,currentSupports)) {
            var hull=supportHullFromSupports(currentSupports);V mass=massCenter(f);double nearest=Double.POSITIVE_INFINITY;
            for(int i=0;i<hull.size();i++){V a=hull.get(i),b=hull.get((i+1)%hull.size());
                double dx=b.x()-a.x(),dz=b.z()-a.z(),length=dx*dx+dz*dz;
                double t=length<1e-10?0:Math.max(0,Math.min(1,((mass.x()-a.x())*dx+(mass.z()-a.z())*dz)/length));
                V point=new V(a.x()+t*dx,a.y(),a.z()+t*dz);double d=Math.hypot(mass.x()-point.x(),mass.z()-point.z());
                if(d<nearest){nearest=d;contactPivot=point;}}
            if(contactPivot!=null){V arm=mass.add(contactPivot.mul(-1));omega=omega.add(new V(arm.z()*.008,0,-arm.x()*.008)).limit(.12);}
        }
        arc=omega.length()*(radius+(contactPivot==null?0:f.position.add(contactPivot.mul(-1)).length()));
        int steps=Math.max(1,(int)Math.ceil(Math.max(v.length()/.20,arc/.10)));
        if(steps>MAX_SUBSTEPS){f.pause("SUBSTEP_LIMIT");return;}
        boolean floor=false,blockedRotation=false;
        for(int step=0;step<steps;step++) {
            if (omega.length() > 1e-9) {
                Q delta=Q.turn(omega,1.0/steps),next=delta.mul(f.rotation);
                V pivot=contactPivot==null?massCenter(f):contactPivot;
                V nextPosition=pivot.add(delta.rotate(f.position.add(pivot.mul(-1))));
                boolean rotationClear=rotationClear(f,pivot,omega,1.0/steps,obstacles);
                if(rotationClear){f.rotation=next;f.position=nextPosition;}
                else {
                    double low=0,high=1.0/steps;
                    for(int search=0;search<10;search++){double mid=(low+high)/2;if(rotationClear(f,pivot,omega,mid,obstacles))low=mid;else high=mid;}
                    Q partial=Q.turn(omega,low);f.position=pivot.add(partial.rotate(f.position.add(pivot.mul(-1))));f.rotation=partial.mul(f.rotation);
                    omega=V.ZERO;blockedRotation=true;
                }
            }
            double wantedY=v.y()/steps,dy=clipRigid(f,obstacles,wantedY,1);
            f.position=f.position.add(new V(0,dy,0));
            double dx=clipRigid(f,obstacles,v.x()/steps,0);f.position=f.position.add(new V(dx,0,0));
            double dz=clipRigid(f,obstacles,v.z()/steps,2);f.position=f.position.add(new V(0,0,dz));
            floor|=wantedY<0&&dy>wantedY+1e-8;
            if(Math.abs(dx-v.x()/steps)>1e-8)v=new V(0,v.y(),v.z());
            if(Math.abs(dy-wantedY)>1e-8)v=new V(v.x(),0,v.z());
            if(Math.abs(dz-v.z()/steps)>1e-8)v=new V(v.x(),v.y(),0);
        }
        List<Support> endSupports = supports(f, obstacles);
        Set<Long> supportIds = new LinkedHashSet<>();
        for (int i = 0, n = endSupports.size(); i < n && supportIds.size() < 64; i++) supportIds.add(endSupports.get(i).source);
        f.supports = supportIds;
        if(floor&&!f.supports.isEmpty()) {
            v=new V(v.x()*.55,0,v.z()*.55);omega=omega.mul(.55);
            if(stableFromSupports(f,endSupports)&&v.length()<.025&&(omega.length()<.01||blockedRotation))f.stableTicks++;else f.stableTicks=0;
            if(f.stableTicks>=12){f.phase=Phase.SLEEPING;v=omega=V.ZERO;}
        } else f.stableTicks=0;
        f.velocity=v.mul(.995);f.angularVelocity=omega;f.age++;f.successfulSteps++;f.changed(false);
    }
    private static double cornerRadius(B box,V pivot) {
        double radius=0;for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)
            radius=Math.max(radius,new V((x==0?box.x0():box.x1())-pivot.x(),(y==0?box.y0():box.y1())-pivot.y(),(z==0?box.z0():box.z1())-pivot.z()).length());
        return radius;
    }
    private static List<DebrisShape> shapes(DebrisFragment f,Q rotation,V position) {
        List<B> locals = f.localVolumes();
        var list = new ArrayList<DebrisShape>(locals.size());
        for (int i = 0, n = locals.size(); i < n; i++) list.add(new DebrisShape(locals.get(i), rotation, position));
        return list;
    }
    private static boolean rotationClear(DebrisFragment f,V pivot,V angular,double dt,List<Obstacle> obstacles) {
        if (angular.length() < 1e-9 || obstacles.isEmpty()) return true;
        double radius=0;for(var b:f.volumes(f.rotation,f.position))radius=Math.max(radius,cornerRadius(b,pivot));
        int samples=Math.max(1,(int)Math.ceil(angular.length()*dt*radius/.01));if(samples>32)return false;
        for(int i=1;i<=samples;i++) {
            Q turn=Q.turn(angular,dt*i/samples);V p=pivot.add(turn.rotate(f.position.add(pivot.mul(-1))));
            for(var body:shapes(f,turn.mul(f.rotation),p))for(var o:obstacles)if(body.bounds.intersects(o.box)&&body.penetration(o.shape)!=null)return false;
        }
        return true;
    }
    private static double clipRigid(DebrisFragment f,List<Obstacle> obstacles,double amount,int axis) {
        if (Math.abs(amount) < 1e-12) return 0.0;
        if (obstacles.isEmpty()) return amount;
        V motion=axis==0?new V(amount,0,0):axis==1?new V(0,amount,0):new V(0,0,amount);
        double fraction=1;
        for(var body:shapes(f,f.rotation,f.position)) {
            B sweptBody = body.bounds.union(body.bounds.offset(motion)).expand(1e-6);
            for(int i=0,n=obstacles.size();i<n;i++) {
                var o=obstacles.get(i);
                if(!sweptBody.intersects(o.box))continue;
                var hit=body.sweep(o.shape,motion);if(hit!=null)fraction=Math.min(fraction,hit.time());
            }
        }
        return amount*fraction;
    }
    /** Incremental collision-normal depenetration. Never guesses a floor, disables collision or discards a record. */
    private static boolean recoverOverlap(DebrisFragment f,List<Obstacle> obstacles) {
        if (obstacles.isEmpty()) return true;
        for(int attempt=0;attempt<8;attempt++) {
            V deepest=null;double before=0;
            for(var body:shapes(f,f.rotation,f.position))for(var o:obstacles){
                if(!body.bounds.intersects(o.box))continue;
                V v=body.penetration(o.shape);if(v!=null){before+=v.length();if(deepest==null||v.length()>deepest.length())deepest=v;}
            }
            if(deepest==null)return true;
            V correction=deepest.limit(.0125);V candidate=f.position.add(correction);double after=0;
            for(var body:shapes(f,f.rotation,candidate))for(var o:obstacles){
                if(!body.bounds.intersects(o.box))continue;
                V v=body.penetration(o.shape);if(v!=null)after+=v.length();
            }
            if(after>=before-1e-8)return false;
            f.position=candidate;f.changed(false);
        }
        for(var body:shapes(f,f.rotation,f.position))for(var o:obstacles)if(body.bounds.intersects(o.box)&&body.penetration(o.shape)!=null)return false;
        return true;
    }
    private record Support(long source,List<V> points) {}
    private static List<Support> supports(DebrisFragment f,List<Obstacle> obstacles) {
        if (obstacles.isEmpty()) return List.of();
        var result=new ArrayList<Support>();V probe=new V(0,-.014,0);
        for(var body:shapes(f,f.rotation,f.position)) {
            B expanded = body.bounds.expand(.015);
            for(int i=0,n=obstacles.size();i<n;i++) {
                var o=obstacles.get(i);
                if(!expanded.intersects(o.box))continue;
                var hit=body.sweep(o.shape,probe);
                if(hit!=null&&hit.normal().y()>.25){var points=body.offset(probe.mul(hit.time())).contactPoints(o.shape);if(!points.isEmpty())result.add(new Support(o.source,points));}
            }
        }
        return result;
    }
    private static Set<Long> contacts(DebrisFragment f,List<Obstacle> obstacles) {
        var result=new LinkedHashSet<Long>();for(var contact:supports(f,obstacles))if(result.size()<64)result.add(contact.source);return result;
    }
    /** Exact extrema of every rotating corner, including intermediate arc extrema, not just endpoint boxes. */
    public static B sweptRotation(B local,Q orientation,V position,V pivot,V angular,double dt) {
        double speed=angular.length(),angle=speed*dt;if(speed<1e-12||angle<1e-12)return local.transformed(orientation,position);
        V axis=angular.mul(1/speed);B result=null;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++) {
            V start=orientation.rotate(new V(x==0?local.x0():local.x1(),y==0?local.y0():local.y1(),z==0?local.z0():local.z1())).add(position).add(pivot.mul(-1));
            double dot=axis.x()*start.x()+axis.y()*start.y()+axis.z()*start.z();V parallel=axis.mul(dot),perp=start.add(parallel.mul(-1));
            V cross=new V(axis.y()*start.z()-axis.z()*start.y(),axis.z()*start.x()-axis.x()*start.z(),axis.x()*start.y()-axis.y()*start.x());
            double[] base={pivot.x()+parallel.x(),pivot.y()+parallel.y(),pivot.z()+parallel.z()},b={perp.x(),perp.y(),perp.z()},c={cross.x(),cross.y(),cross.z()},lo=new double[3],hi=new double[3];
            for(int k=0;k<3;k++){double v0=base[k]+b[k],v1=base[k]+b[k]*Math.cos(angle)+c[k]*Math.sin(angle);lo[k]=Math.min(v0,v1);hi[k]=Math.max(v0,v1);
                double extremum=Math.atan2(c[k],b[k]);for(int n=-2;n<=2;n++){double t=extremum+n*Math.PI;if(t>=0&&t<=angle){double v=base[k]+b[k]*Math.cos(t)+c[k]*Math.sin(t);lo[k]=Math.min(lo[k],v);hi[k]=Math.max(hi[k],v);}}}
            var bounds=new B(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]);result=result==null?bounds:result.union(bounds);
        }return result;
    }
    public static double clip(List<B> bodies,List<Obstacle> obstacles,double amount,int axis) {
        for(var a:bodies)for(var o:obstacles) {
            B b=o.box;boolean overlap=switch(axis){case 0->a.y1()>b.y0()+1e-8&&a.y0()<b.y1()-1e-8&&a.z1()>b.z0()+1e-8&&a.z0()<b.z1()-1e-8;
                case 1->a.x1()>b.x0()+1e-8&&a.x0()<b.x1()-1e-8&&a.z1()>b.z0()+1e-8&&a.z0()<b.z1()-1e-8;
                default->a.x1()>b.x0()+1e-8&&a.x0()<b.x1()-1e-8&&a.y1()>b.y0()+1e-8&&a.y0()<b.y1()-1e-8;};
            if(!overlap)continue;
            double low=axis==0?a.x0():axis==1?a.y0():a.z0(),high=axis==0?a.x1():axis==1?a.y1():a.z1();
            double otherLow=axis==0?b.x0():axis==1?b.y0():b.z0(),otherHigh=axis==0?b.x1():axis==1?b.y1():b.z1();
            if(amount>0&&high<=otherLow+1e-8)amount=Math.min(amount,Math.max(0,otherLow-high));
            if(amount<0&&low>=otherHigh-1e-8)amount=Math.max(amount,Math.min(0,otherHigh-low));
        }return amount;
    }
    public static Set<Long> contacts(List<B> bodies,List<Obstacle> obstacles) {
        Set<Long> result=new LinkedHashSet<>();for(var a:bodies)for(var o:obstacles){var b=o.box;
            if(a.x1()>b.x0()+1e-6&&a.x0()<b.x1()-1e-6&&a.z1()>b.z0()+1e-6&&a.z0()<b.z1()-1e-6
                    &&a.y0()>=b.y1()-1e-6&&a.y0()-b.y1()<.015&&result.size()<64)result.add(o.source);}
        return result;
    }
    private static V massCenter(DebrisFragment f) {
        return f.worldMassCenter();
    }
    private static List<V> supportHullFromSupports(List<Support> suppList) {
        var points=new ArrayList<V>();
        for(int i=0,n=suppList.size();i<n;i++)points.addAll(suppList.get(i).points);
        points.sort(Comparator.comparingDouble(V::x).thenComparingDouble(V::z));
        var unique=new ArrayList<V>();for(var p:points)if(unique.isEmpty()||p.x()!=unique.get(unique.size()-1).x()||p.z()!=unique.get(unique.size()-1).z())unique.add(p);
        if(unique.size()<3)return unique;
        var hull=new ArrayList<V>();for(var p:unique){while(hull.size()>=2&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}
        int lower=hull.size();for(int i=unique.size()-2;i>=0;i--){var p=unique.get(i);while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)hull.remove(hull.size()-1);hull.add(p);}hull.remove(hull.size()-1);return hull;
    }
    private static List<V> supportHull(DebrisFragment f,List<Obstacle> obstacles) {
        return supportHullFromSupports(supports(f,obstacles));
    }
    private static double cross(V a,V b,V c){return (b.x()-a.x())*(c.z()-a.z())-(b.z()-a.z())*(c.x()-a.x());}
    private static boolean stableFromSupports(DebrisFragment f,List<Support> suppList) {
        var hull=supportHullFromSupports(suppList);if(hull.size()<3)return false;V mass=massCenter(f);
        for(int i=0;i<hull.size();i++)if(cross(hull.get(i),hull.get((i+1)%hull.size()),mass)<-1e-5)return false;
        return true;
    }
    private static boolean stable(DebrisFragment f,List<Obstacle> obstacles) {
        return stableFromSupports(f, supports(f, obstacles));
    }
}
