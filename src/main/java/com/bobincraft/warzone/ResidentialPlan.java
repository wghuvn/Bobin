package com.bobincraft.warzone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Profile-five forest hamlets. Only individual plots, footpaths and lanes reserve the landscape. */
public final class ResidentialPlan {
    public enum Kind { HAMLET, HOMESTEAD, ROADSIDE, DERELICT }
    public enum Group { MARAUDER, BANDIT, UNIDENTIFIED }
    public enum Duty { SENTRY, PATROL, QUARTERMASTER, MECHANIC, RADIO, REST }
    public record Point(int x,int y,int z) { }
    public record Post(int index,Duty duty,Point spawn,Point focus,List<Point> route) {
        public Post { route=List.copyOf(route); }
    }
    public record Plot(int index,int x,int z,int groundY,int halfWidth,int halfDepth,String facing,int variant) {
        public int localX(int wx,int wz) {
            int dx=wx-x,dz=wz-z;
            return switch(facing){case "east"->dz;case "south"->-dx;case "west"->-dz;default->dx;};
        }
        public int localZ(int wx,int wz) {
            int dx=wx-x,dz=wz-z;
            return switch(facing){case "east"->-dx;case "south"->-dz;case "west"->dx;default->dz;};
        }
        public Point point(int u,int v) {
            return switch(facing){
                case "east"->new Point(x-v,groundY+1,z+u);
                case "south"->new Point(x-u,groundY+1,z-v);
                case "west"->new Point(x+v,groundY+1,z-u);
                default->new Point(x+u,groundY+1,z+v);
            };
        }
        public Point gate(){return point(0,-halfDepth-4);}
        public boolean contains(int wx,int wz,int margin) {
            int u=localX(wx,wz),v=localZ(wx,wz);
            return Math.abs(u)<=halfWidth+4+margin && v>=-halfDepth-5-margin && v<=halfDepth+6+margin;
        }
        public double padWeight(int wx,int wz) {
            int u=localX(wx,wz),v=localZ(wx,wz);
            int outside=Math.max(Math.max(0,Math.abs(u)-halfWidth-4),Math.max(0,Math.max(-halfDepth-5-v,v-halfDepth-6)));
            double t=Math.min(1,outside/6.0);
            return 1-t*t*(3-2*t);
        }
    }
    public record Site(int id,Kind kind,Group group,int x,int z,int radius,int groundY,
                       List<Plot> plots,List<Point> lane,List<Post> posts,String name) {
        public Site { plots=List.copyOf(plots);lane=List.copyOf(lane);posts=List.copyOf(posts); }
        public Site(int id,Kind kind,Group group,int x,int z,int radius,int groundY,List<Plot> plots,List<Point> lane,List<Post> posts) {
            this(id,kind,group,x,z,radius,groundY,plots,lane,posts,null);
        }
        public String label(){if(name!=null)return name;return switch(kind){
            case HAMLET->"Forest village";case HOMESTEAD->"Isolated homestead";
            case ROADSIDE->"Roadside houses";case DERELICT->"Abandoned hamlet";
        }+" #"+id;}
        public int feetY(){return groundY+1;}
        public boolean contains(int wx,int wz){return Math.abs(wx-x)<=radius && Math.abs(wz-z)<=radius;}
        public boolean intersectsChunk(int minX,int minZ){return minX+15>=x-radius && minX<=x+radius && minZ+15>=z-radius && minZ<=z+radius;}
    }
    private static final ResidentialPlan EMPTY=new ResidentialPlan(List.of());
    private final List<Site> sites;
    private ResidentialPlan(List<Site> sites){this.sites=List.copyOf(sites);}
    public List<Site> sites(){return sites;}
    public static ResidentialPlan empty(){return EMPTY;}
    public static ResidentialPlan fromSites(List<Site> sites){return new ResidentialPlan(sites);}

    public static ResidentialPlan forTerrain(WarzoneTerrain terrain) {
        if(terrain.isAuthoredZone())return terrain.zonePlan().residents();
        if(!terrain.hasResidentialSettlements())return EMPTY;
        List<Site> sites=new ArrayList<>();
        for(var reservation:terrain.naturalLandscape().reservations()) {
            Kind kind=switch(reservation.type){
                case COMPOUND,SETTLEMENT->Kind.HAMLET;
                case INDUSTRY->Kind.DERELICT;case CHECKPOINT->Kind.ROADSIDE;case FOREST_OUTPOST->Kind.HOMESTEAD;
            };
            Site chosen=tryNear(terrain,sites,sites.size()+1,kind,reservation.x,reservation.z);
            if(chosen!=null)sites.add(chosen);
        }
        int minimum=terrain.size>=1024?6:terrain.size>=512?3:1;
        for(int round=0;round<4 && (sites.size()<minimum || sites.stream().noneMatch(s->s.kind==Kind.HAMLET));round++) {
            Kind kind=sites.stream().noneMatch(s->s.kind==Kind.HAMLET)?Kind.HAMLET:round%2==0?Kind.HOMESTEAD:Kind.ROADSIDE;
            Site best=null;double score=Double.MAX_VALUE;
            int stride=terrain.size>=1024?64:28;
            for(int z=terrain.min+90;z<terrain.max-90;z+=stride)for(int x=terrain.min+90;x<terrain.max-90;x+=stride) {
                Site candidate=make(terrain,sites,sites.size()+1,kind,x,z);
                if(candidate==null)continue;
                double quality=terrain.naturalLandscape().forest(x,z)*-10+WarzoneNoise.unit(WarzoneNoise.hash(terrain.seed+93,x,z));
                if(quality<score){best=candidate;score=quality;}
            }
            if(best==null)break;
            sites.add(best);
        }
        return new ResidentialPlan(sites);
    }

    private static Site tryNear(WarzoneTerrain t,List<Site> prior,int id,Kind kind,int x,int z) {
        int[][] offsets={{0,0},{24,0},{-24,0},{0,24},{0,-24},{24,24},{-24,-24},{48,0},{0,48}};
        for(int[] offset:offsets){Site site=make(t,prior,id,kind,x+offset[0],z+offset[1]);if(site!=null)return site;}
        return null;
    }
    private static int rotateX(int x,int z,int rot){return switch(rot&3){case 1->-z;case 2->-x;case 3->z;default->x;};}
    private static int rotateZ(int x,int z,int rot){return switch(rot&3){case 1->x;case 2->-z;case 3->-x;default->z;};}
    private static WarzoneTerrain.Sample raw(WarzoneTerrain t,int x,int z){var s=new WarzoneTerrain.Sample();t.naturalLandscape().sample(x,z,s);return s;}
    private static boolean dry(WarzoneTerrain.Sample s){return s.height>=64 && s.height<112 && s.channel<.08 && s.pool<.1 && s.edge>35;}

    private static Site make(WarzoneTerrain t,List<Site> prior,int id,Kind kind,int cx,int cz) {
        long hash=WarzoneNoise.hash(t.seed+83591,cx,cz);
        int count=switch(kind){case HAMLET->4+(int)Math.floorMod(hash,3);case HOMESTEAD->1+(int)(hash&1);
            case ROADSIDE->2+(int)(hash&1);case DERELICT->3+(int)(hash&1);};
        if(t.size<512)count=Math.min(count,2);
        int rows=(count+1)/2,span=rows*12+8,rot=(int)Math.floorMod(hash>>3,4);
        int radius=Math.max(span+5,49);
        if(Math.abs(cx)+radius+16>=t.half-22 || Math.abs(cz)+radius+16>=t.half-22
                || Math.hypot(cx,cz-t.landingZ())<radius+90)return null;
        for(Site s:prior)if(Math.hypot(cx-s.x,cz-s.z)<radius+s.radius+40)return null;
        if(!dry(raw(t,cx,cz)))return null;
        List<Point> lane=new ArrayList<>();
        double phase=WarzoneNoise.unit(hash)*6.28;
        int previous=-1;
        for(int u=-span;u<=span;u++) {
            int bend=(int)Math.round(Math.sin(u/17.0+phase)*2+Math.sin(u/8.0)*.5);
            int x=cx+rotateX(bend,u,rot),z=cz+rotateZ(bend,u,rot);
            var sample=raw(t,x,z);
            if(!dry(sample) || previous>=0 && Math.abs(previous-sample.height)>1)return null;
            for(int v=-3;v<=3;v++)if(!dry(raw(t,x+rotateX(v,0,rot),z+rotateZ(v,0,rot))))return null;
            lane.add(new Point(x,sample.height+1,z));previous=sample.height;
        }
        List<Plot> plots=new ArrayList<>();
        for(int i=0;i<count;i++) {
            long choice=WarzoneNoise.mix(hash+i*137);
            int side=i%2==0?-1:1,along=(i/2)*24-(rows-1)*12+(side<0?-3:3)+(int)Math.floorMod(choice,5)-2;
            int across=side*(19+(int)Math.floorMod(choice>>4,4));
            int x=cx+rotateX(across,along,rot),z=cz+rotateZ(across,along,rot);
            int hw=4+(int)(choice&1),hd=5+(int)((choice>>1)&1);
            String facing=new String[]{"north","east","south","west"}[Math.floorMod((side<0?1:3)+rot,4)];
            var placeholder=new Plot(i,x,z,70,hw,hd,facing,(int)Math.floorMod(choice>>8,6));
            List<Integer> heights=new ArrayList<>();int low=255,high=0;
            for(int u=-hw-4;u<=hw+4;u+=2)for(int v=-hd-5;v<=hd+6;v+=2) {
                Point p=placeholder.point(u,v);var sample=raw(t,p.x,p.z);
                if(!dry(sample))return null;
                low=Math.min(low,sample.height);high=Math.max(high,sample.height);heights.add(sample.height);
            }
            if(high-low>6)return null;
            heights.sort(Comparator.naturalOrder());int y=heights.get(heights.size()/2);
            Plot plot=new Plot(i,x,z,y,hw,hd,facing,placeholder.variant);
            Point gate=plot.gate(),nearest=nearest(lane,gate.x,gate.z);
            double distance=Math.hypot(gate.x-nearest.x,gate.z-nearest.z);
            if(Math.abs(y-nearest.y+1)>Math.max(1,distance-1))return null;
            plots.add(plot);
        }
        int centerY=lane.get(lane.size()/2).y-1;
        var posts=posts(plots,lane,rot);
        Group group=Group.values()[Math.floorMod(id-1,3)];
        return new Site(id,kind,group,cx,cz,radius,centerY,plots,lane,posts);
    }

    private static List<Post> posts(List<Plot> plots,List<Point> lane,int rot) {
        var posts=new ArrayList<Post>();
        List<Point> patrol=List.of(lane.get(4),lane.get(lane.size()/4),lane.get(lane.size()/2),lane.get(lane.size()*3/4),lane.get(lane.size()-5));
        Point watch=lane.get(lane.size()/5);
        posts.add(new Post(posts.size(),Duty.SENTRY,watch,lane.get(0),List.of()));
        if(plots.size()>=2)posts.add(new Post(posts.size(),Duty.PATROL,lane.get(lane.size()*3/4),lane.get(lane.size()-1),patrol));
        Duty[] specialists={Duty.QUARTERMASTER,Duty.MECHANIC,Duty.RADIO,Duty.REST};
        for(int i=0;i<Math.min(plots.size(),4);i++) {
            Plot plot=plots.get(i);Point gate=plot.gate(),near=nearest(lane,gate.x,gate.z);
            posts.add(new Post(posts.size(),specialists[i],gate,plot.point(3,-plot.halfDepth-2),List.of(gate,near,patrol.get(2),near)));
        }
        return List.copyOf(posts);
    }
    public static Point nearest(List<Point> points,int x,int z) {
        Point best=points.get(0);long distance=Long.MAX_VALUE;
        for(Point p:points){long d=(long)(p.x-x)*(p.x-x)+(long)(p.z-z)*(p.z-z);if(d<distance){distance=d;best=p;}}
        return best;
    }
    private static double segmentDistance(Point a,Point b,int x,int z) {
        double dx=b.x-a.x,dz=b.z-a.z,length=dx*dx+dz*dz;
        double t=length==0?0:Math.max(0,Math.min(1,((x-a.x)*dx+(z-a.z)*dz)/length));
        return Math.hypot(x-a.x-dx*t,z-a.z-dz*t);
    }
    public boolean isReserved(int x,int z) {
        for(Site site:sites) {
            if(!site.contains(x,z))continue;
            for(Plot p:site.plots)if(p.contains(x,z,1))return true;
            if(site.name()!=null)for(var post:site.posts())for(var access:post.route())if(Math.abs(access.x()-x)<=2&&Math.abs(access.z()-z)<=2)return true;
            Point road=nearest(site.lane,x,z);
            if(Math.abs(road.x-x)<=3 && Math.abs(road.z-z)<=3)return true;
            for(Plot p:site.plots)if(segmentDistance(p.gate(),nearest(site.lane,p.gate().x,p.gate().z),x,z)<=2)return true;
        }
        return false;
    }
    public double padWeight(int x,int z) {
        double weight=0;
        for(Site site:sites)if(site.contains(x,z))for(Plot p:site.plots)weight=Math.max(weight,p.padWeight(x,z));
        return weight;
    }
    public int targetHeight(int x,int z,int naturalHeight) {
        double height=naturalHeight;
        for(Site site:sites) {
            if(!site.contains(x,z))continue;
            double strongest=0;
            for(Plot p:site.plots) {
                double weight=p.padWeight(x,z);
                if(weight>strongest){height=naturalHeight+(p.groundY-naturalHeight)*weight;strongest=weight;}
            }
            Point road=nearest(site.lane,x,z);
            if(Math.abs(road.x-x)<=2 && Math.abs(road.z-z)<=2)return road.y-1;
            for(Plot p:site.plots) {
                Point a=p.gate(),b=nearest(site.lane,a.x,a.z);
                if(segmentDistance(a,b,x,z)>1.1)continue;
                double dx=b.x-a.x,dz=b.z-a.z,length=dx*dx+dz*dz;
                double f=length==0?0:Math.max(0,Math.min(1,((x-a.x)*dx+(z-a.z)*dz)/length));
                return (int)Math.round(a.y-1+(b.y-a.y)*f);
            }
            break;
        }
        return (int)Math.round(height);
    }
}
