package com.bobincraft.destruction.debris;

import java.util.*;

/** Server-owned rigid fragment. Units: blocks and ticks. Geometry is unscaled source block geometry. */
public final class DebrisFragment {
    public static final int MAX_CELLS=16, MAX_SHAPES_PER_CELL=8;
    public enum Phase { ANIMATING, FALLING, SLEEPING, PAUSED_UNLOADED }
    public enum Commit { RESERVED, ARMED, REMOVED, CONFLICT }
    public record V(double x,double y,double z) {
        public static final V ZERO=new V(0,0,0);
        public V { if(!Double.isFinite(x+y+z))throw new IllegalArgumentException("Non-finite vector"); }
        public V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        public V mul(double s){return new V(x*s,y*s,z*s);}
        public double length(){return Math.sqrt(x*x+y*y+z*z);}
        public V limit(double maximum){double n=length();return n>maximum?mul(maximum/n):this;}
    }
    public record Q(double x,double y,double z,double w) {
        public static final Q IDENTITY=new Q(0,0,0,1);
        public Q {if(!Double.isFinite(x+y+z+w))throw new IllegalArgumentException("Non-finite rotation");}
        public Q normalized(){double n=Math.sqrt(x*x+y*y+z*z+w*w);if(!Double.isFinite(n)||n<1e-9)throw new IllegalArgumentException("Zero quaternion");return new Q(x/n,y/n,z/n,w/n);}
        public Q mul(Q b){return new Q(w*b.x+x*b.w+y*b.z-z*b.y,w*b.y-x*b.z+y*b.w+z*b.x,w*b.z+x*b.y-y*b.x+z*b.w,w*b.w-x*b.x-y*b.y-z*b.z).normalized();}
        public V rotate(V p){double tx=2*(y*p.z-z*p.y),ty=2*(z*p.x-x*p.z),tz=2*(x*p.y-y*p.x);return new V(p.x+w*tx+y*tz-z*ty,p.y+w*ty+z*tx-x*tz,p.z+w*tz+x*ty-y*tx);}
        public static Q turn(V angular,double dt){double n=angular.length();if(n<1e-10)return IDENTITY;double s=Math.sin(n*dt/2)/n;return new Q(angular.x*s,angular.y*s,angular.z*s,Math.cos(n*dt/2));}
    }
    public record B(double x0,double y0,double z0,double x1,double y1,double z1) {
        public B {if(!Double.isFinite(x0+y0+z0+x1+y1+z1)||x1<x0||y1<y0||z1<z0)throw new IllegalArgumentException("Invalid bounds");}
        public B offset(V p){return new B(x0+p.x,y0+p.y,z0+p.z,x1+p.x,y1+p.y,z1+p.z);}
        public B expand(double d){return new B(x0-d,y0-d,z0-d,x1+d,y1+d,z1+d);}
        public B union(B b){return new B(Math.min(x0,b.x0),Math.min(y0,b.y0),Math.min(z0,b.z0),Math.max(x1,b.x1),Math.max(y1,b.y1),Math.max(z1,b.z1));}
        public boolean intersects(B b){return x1>b.x0+1e-7&&x0<b.x1-1e-7&&y1>b.y0+1e-7&&y0<b.y1-1e-7&&z1>b.z0+1e-7&&z0<b.z1-1e-7;}
        public B transformed(Q q,V p){
            double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
            double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
                V v=q.rotate(new V(x==0?x0:x1,y==0?y0:y1,z==0?z0:z1));
                double vx=v.x+p.x,vy=v.y+p.y,vz=v.z+p.z;
                if(vx<minX)minX=vx;if(vx>maxX)maxX=vx;
                if(vy<minY)minY=vy;if(vy>maxY)maxY=vy;
                if(vz<minZ)minZ=vz;if(vz>maxZ)maxZ=vz;
            }
            return new B(minX,minY,minZ,maxX,maxY,maxZ);
        }
    }
    public static final class Cell {
        public final long source;
        public final V local;
        public final String state;
        public List<B> shapes=List.of();
        public Commit commit=Commit.RESERVED;
        public boolean durable,protectedSource;
        public Cell(long source,V local,String state){if(state==null||state.length()>512)throw new IllegalArgumentException("Material state");this.source=source;this.local=local;this.state=state;}
    }
    public final UUID id,operation;
    public final String dimension,structure;
    public final int index;
    public final List<Cell> cells;
    public final V sourcePose;
    public V position,velocity=V.ZERO,angularVelocity=V.ZERO,launchVelocity=V.ZERO,launchAngular=V.ZERO;
    public Q rotation=Q.IDENTITY;
    public Phase phase=Phase.ANIMATING,resume=Phase.ANIMATING;
    public long revision=1,geometryRevision=1,supportToken;
    public int stableTicks,age,retryCount;
    public long successfulSteps,lastSimulationTick=-1;
    public boolean reconciled;
    public String pauseReason="";
    public Set<Long> supports=new LinkedHashSet<>(),dependencies=new LinkedHashSet<>();

    // Cached geometry & bounds to avoid redundant O(cells * shapes) allocations on every substep and render frame
    private long cachedGeoRev = -1;
    private List<B> cachedLocalVolumes = List.of();
    private V cachedLocalMassCenter = V.ZERO;
    private double cachedLocalRadius = 0.0;
    private long cachedBoundsGeoRev = -1;
    private V cachedBoundsPos = null;
    private Q cachedBoundsRot = null;
    private B cachedBounds = null;

    public DebrisFragment(UUID id,UUID operation,String dimension,String structure,int index,List<Cell> cells,V origin) {
        if(cells.isEmpty()||cells.size()>MAX_CELLS||dimension.length()>128||structure.length()>128||index<0)throw new IllegalArgumentException("Fragment bounds");
        this.id=id;this.operation=operation;this.dimension=dimension;this.structure=structure;this.index=index;this.cells=List.copyOf(cells);this.sourcePose=origin;position=origin;
    }
    public boolean visible(){for(int i=0,n=cells.size();i<n;i++)if(cells.get(i).commit==Commit.REMOVED)return true;return false;}
    public boolean complete(){for(int i=0,n=cells.size();i<n;i++){var c=cells.get(i).commit;if(c!=Commit.REMOVED&&c!=Commit.CONFLICT)return false;}return true;}

    private void ensureLocalCache() {
        if (cachedGeoRev == geometryRevision && !cachedLocalVolumes.isEmpty()) return;
        var boxes = new ArrayList<B>();
        V sum = V.ZERO;
        double totalMass = 0.0;
        double maxRad = 0.0;
        for (int i = 0, n = cells.size(); i < n; i++) {
            Cell c = cells.get(i);
            if (c.commit == Commit.REMOVED) {
                double matMass = DebrisPlan.materialMass(c.state);
                for (int j = 0, m = c.shapes.size(); j < m; j++) {
                    B box = c.shapes.get(j);
                    boxes.add(box);
                    double volMass = matMass * (box.x1() - box.x0()) * (box.y1() - box.y0()) * (box.z1() - box.z0());
                    sum = sum.add(new V((box.x0() + box.x1()) * 0.5, (box.y0() + box.y1()) * 0.5, (box.z0() + box.z1()) * 0.5).mul(volMass));
                    totalMass += volMass;
                    for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                        double rx = x == 0 ? box.x0() : box.x1();
                        double ry = y == 0 ? box.y0() : box.y1();
                        double rz = z == 0 ? box.z0() : box.z1();
                        double r = Math.sqrt(rx * rx + ry * ry + rz * rz);
                        if (r > maxRad) maxRad = r;
                    }
                }
            }
        }
        cachedLocalVolumes = List.copyOf(boxes);
        cachedLocalMassCenter = totalMass > 1e-9 ? sum.mul(1.0 / totalMass) : V.ZERO;
        cachedLocalRadius = maxRad;
        cachedGeoRev = geometryRevision;
    }

    public List<B> localVolumes(){
        ensureLocalCache();
        return cachedLocalVolumes;
    }
    public double localRadius(){
        ensureLocalCache();
        return cachedLocalRadius;
    }
    public V worldMassCenter(){
        ensureLocalCache();
        return position.add(rotation.rotate(cachedLocalMassCenter));
    }
    public List<B> volumes(Q q,V p){
        List<B> locals = localVolumes();
        if (locals.isEmpty()) return List.of();
        var res = new ArrayList<B>(locals.size());
        for (int i = 0, n = locals.size(); i < n; i++) res.add(locals.get(i).transformed(q, p));
        return res;
    }
    public B bounds(){
        if (cachedBoundsGeoRev == geometryRevision && Objects.equals(cachedBoundsPos, position) && Objects.equals(cachedBoundsRot, rotation)) {
            return cachedBounds;
        }
        List<B> locals = localVolumes();
        if (locals.isEmpty()) return null;
        B b = null;
        for (int i = 0, n = locals.size(); i < n; i++) {
            B a = locals.get(i).transformed(rotation, position);
            b = b == null ? a : b.union(a);
        }
        cachedBoundsGeoRev = geometryRevision;
        cachedBoundsPos = position;
        cachedBoundsRot = rotation;
        cachedBounds = b;
        return b;
    }
    public void changed(boolean geometry){revision++;if(geometry){geometryRevision++;cachedGeoRev=-1;cachedBoundsGeoRev=-1;}}
    public void pause(){if(phase!=Phase.PAUSED_UNLOADED)pause("COLLISION_CONTEXT_UNAVAILABLE");}
    public void pause(String reason){if(phase!=Phase.PAUSED_UNLOADED){resume=phase;phase=Phase.PAUSED_UNLOADED;changed(false);}if(!pauseReason.equals(reason)){pauseReason=reason;changed(false);}}
    public void wake(){pauseReason="";phase=Phase.FALLING;stableTicks=0;supports.clear();changed(false);}
}
