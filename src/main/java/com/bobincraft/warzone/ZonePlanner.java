package com.bobincraft.warzone;

import java.util.*;
import static com.bobincraft.warzone.ZonePlan.*;

/** Offline planner. Uses the surveyed dry districts, then bakes the expanded, graded network once. */
final class ZonePlanner {
    private final WarzoneTerrain terrain;
    private final List<Site> sites=new ArrayList<>(),homes=new ArrayList<>(),areas=new ArrayList<>();
    private final List<Crossing> bridges;
    private final Map<Long,WarzoneTerrain.Sample> samples=new HashMap<>();
    private final Map<Long,Boolean> edgeMemo=new HashMap<>(),columnMemo=new HashMap<>();
    private final int step=8,n;
    private int[] height;
    private boolean[] dry;
    private ZoneGeography geography;
    private record Open(int index,double cost,double estimate) implements Comparable<Open>{public int compareTo(Open b){int c=Double.compare(estimate,b.estimate);return c==0?Integer.compare(index,b.index):c;}}
    static Layout build(WarzoneTerrain terrain){return new ZonePlanner(terrain).build();}
    private ZonePlanner(WarzoneTerrain terrain){
        this.terrain=terrain;n=terrain.size/step+1;var old=ZonePlan.readLegacy();sites.addAll(old.sites());bridges=old.crossings();
        addIndustry(10,"Black Alder oil refinery",Theme.REFINERY,-498,-876,78);
        addIndustry(11,"Eastern distribution substation",Theme.SUBSTATION,692,-138,58);
        areas.addAll(sites);geography=new ZoneGeography(terrain,areas);
    }
    private Layout build(){
        System.out.println("ZONE_BAKE surveying homesteads");makeGrid(false);placeHomes();areas.addAll(homes);geography=new ZoneGeography(terrain,areas);
        edgeMemo.clear();columnMemo.clear();makeGrid(true);
        System.out.println("ZONE_BAKE routing 11 districts and "+homes.size()+" homesteads");
        List<Road> roads=roads();System.out.println("ZONE_BAKE grading complete; collectors");
        List<Drain> drains=drains(roads);
        return new Layout(1,terrain.seed,terrain.size,terrain.generationVersion(),sites,homes,bridges,roads,drains);
    }
    private WarzoneTerrain.Sample raw(int x,int z){long key=key(x,z);var s=samples.get(key);if(s!=null)return s;
        s=new WarzoneTerrain.Sample();terrain.naturalLandscape().sample(x,z,s);if(samples.size()<900_000)samples.put(key,s);return s;}
    private Point ground(int x,int z){return new Point(x,geography.height(x,z,raw(x,z).height)+1,z);}
    private Crossing bridge(int x,int z,int margin){for(var b:bridges)if(b.contains(x,z,margin))return b;return null;}
    private void addIndustry(int id,String name,Theme theme,int x,int z,int radius){
        int sum=0,count=0;for(int dz=-48;dz<=48;dz+=12)for(int dx=-48;dx<=48;dx+=12){sum+=raw(x+dx,z+dz).height;count++;}int y=(int)Math.round(sum/(double)count);
        List<Building> buildings=theme==Theme.REFINERY?List.of(
            new Building(0,Use.HALL,x-28,z-25,y,10,17,1,31),new Building(1,Use.OFFICE,x+35,z+30,y,8,6,0,32),new Building(2,Use.WORKSHOP,x-29,z+35,y,9,8,0,33)):
            List.of(new Building(0,Use.PUMP,x-27,z-24,y,10,6,1,34),new Building(1,Use.OFFICE,x+24,z+27,y,7,5,0,35));
        sites.add(new Site(id,name,theme,x,z,y,radius,buildings,List.of(),new Point(x,raw(x,z+radius-6).height+1,z+radius-6)));
    }
    private void makeGrid(boolean obstacles){
        dry=new boolean[n*n];height=new int[n*n];
        for(int j=0;j<n;j++)for(int i=0;i<n;i++){
            int x=terrain.min+i*step,z=terrain.min+j*step,index=j*n+i;var s=raw(x,z);
            boolean clear=s.edge>58&&s.height>=64&&s.channel<.13;
            if(obstacles)for(Site site:areas)if(site.contains(x,z,-4)&&z<site.z()+site.radius()-13)clear=false;
            Crossing b=bridge(x,z,0);if(b!=null)clear=true;
            dry[index]=clear;height[index]=b==null?geography.height(x,z,s.height):b.floor(b.u(x,z));
        }
    }
    private void placeHomes(){
        boolean[] reachable=new boolean[n*n];ArrayDeque<Integer> open=new ArrayDeque<>();open.add(nearest(ground(0,terrain.landingZ())));
        while(!open.isEmpty()){int a=open.remove();if(a<0||reachable[a]||!dry[a])continue;reachable[a]=true;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int i=a%n+d[0],j=a/n+d[1];if(i>=0&&j>=0&&i<n&&j<n&&!reachable[j*n+i]&&edge(a,j*n+i))open.add(j*n+i);}}
        List<Point> candidates=new ArrayList<>();for(int z=-820;z<=820;z+=164)for(int x=-820;x<=820;x+=164)candidates.add(new Point(x,0,z));
        candidates.sort(Comparator.comparingLong(p->WarzoneNoise.hash(terrain.seed+802,p.x(),p.z())));
        for(Point p:candidates){
            int x=p.x()+(int)Math.floorMod(WarzoneNoise.hash(terrain.seed,p.x(),p.z()),35)-17,z=p.z();
            int near=nearest(ground(x,z+27));if(near<0||!reachable[near]||Math.hypot(x,z-terrain.landingZ())<145||bridge(x,z,48)!=null)continue;
            boolean clash=false;for(Site s:areas)if(Math.hypot(x-s.x(),z-s.z())<s.radius()+88)clash=true;
            for(Site s:homes)if(Math.hypot(x-s.x(),z-s.z())<155)clash=true;
            if(clash)continue;
            int lo=256,hi=0;boolean wet=false;for(int dz=-28;dz<=28;dz+=7)for(int dx=-28;dx<=28;dx+=7){var s=raw(x+dx,z+dz);lo=Math.min(lo,s.height);hi=Math.max(hi,s.height);if(s.height<65||s.channel>.04||s.pool>.04)wet=true;}
            if(wet||hi-lo>6)continue;
            int index=homes.size(),y=raw(x-12,z-4).height,variant=40+index;
            Theme theme=new Theme[]{Theme.ALDER,Theme.BRICK,Theme.PINE,Theme.SHACKS}[index%4];
            var house=new Building(0,index%5==4?Use.SHACK:Use.HOUSE,x-12,z-4,y,5+index%2,6+index%3,0,variant);
            var shed=new Building(1,Use.SHACK,x+14,z-12,raw(x+14,z-12).height,3,4,3,variant+80);
            var garden=new Field(x-15,z+14,5,4,raw(x-15,z+14).height,index%2);
            homes.add(new Site(101+index,"Abandoned homestead "+(index+1),theme,x,z,y,32,List.of(house,shed),List.of(garden),ground(x,z+27)));
            if(homes.size()==18)break;
        }
        if(homes.size()<12)throw new IllegalStateException("Not enough reachable homesteads: "+homes.size());
    }
    private List<Road> roads(){
        List<Road> result=new ArrayList<>();List<Point> pending=new ArrayList<>(),joined=new ArrayList<>();
        for(Site s:sites)pending.add(s.entrance());for(Crossing b:bridges){pending.add(approach(b,-1));pending.add(approach(b,1));}
        joined.add(new Point(0,terrain.landingY()+1,terrain.landingZ()+20));Set<Integer> attached=new HashSet<>();
        while(!pending.isEmpty()){
            int target=0,from=0;double best=Double.POSITIVE_INFINITY;
            for(int a=0;a<joined.size();a++)for(int b=0;b<pending.size();b++){double d=distance(joined.get(a),pending.get(b));if(d<best){best=d;target=b;from=a;}}
            Point goal=pending.remove(target);List<Point> path=route(joined.get(from),goal);
            if(path.isEmpty())path=routeFromNetwork(joined,goal);
            if(path.isEmpty())throw new IllegalStateException("Unreachable district approach "+goal);
            path=grade(trimAtLastJunction(path,result),8);add(result,"District road "+(result.size()+1),true,path,3);join(joined,path);
            for(Crossing b:bridges)if(!attached.contains(b.id())&&(goal.equals(approach(b,-1))||goal.equals(approach(b,1)))){
                attached.add(b.id());Point other=goal.equals(approach(b,-1))?approach(b,1):approach(b,-1);pending.remove(other);
                List<Point> spine=grade(line(onExisting(result,approach(b,-1)),onExisting(result,approach(b,1))),8);add(result,"Bridge "+b.id()+" alignment",true,spine,3);join(joined,spine);
            }
        }
        for(Site home:homes){List<Point> path=routeFromNetwork(joined,home.entrance());
            if(path.isEmpty())throw new IllegalStateException("Unreachable homestead "+home.id());
            add(result,home.name()+" cart track",false,grade(path,6),2);
        }
        for(Site s:areas){
            int north=s.z()-12;for(Building b:s.buildings())north=Math.min(north,b.door().z());
            List<Point> spine=grade(line(onExisting(result,s.entrance()),ground(s.x(),north)),5);add(result,s.name()+" lane",false,spine,2);
            for(Building b:s.buildings()){
                Point junction=on(spine,s.x(),b.door().z());add(result,s.name()+" footpath",false,grade(line(junction,b.door()),4),1);
            }
            Point park=parking(s);if(park!=null){int laneZ=s.theme()==Theme.SUBSTATION?park.z():s.z()+58;
                Point junction=on(spine,s.x(),laneZ),corner=ground(park.x(),laneZ),end=ground(park.x(),park.z());
                List<Point> access=new ArrayList<>(line(junction,corner));if(laneZ!=park.z())access.addAll(line(corner,end).subList(1,Math.abs(laneZ-park.z())+1));
                add(result,s.name()+" loading access",false,grade(access,5),2);
            }
        }return List.copyOf(result);
    }
    private List<Point> trimAtLastJunction(List<Point> path,List<Road> existing){
        Map<Long,Point> centers=new HashMap<>();for(Road r:existing)if(r.paved())for(Point p:r.points())centers.putIfAbsent(key(p.x(),p.z()),p);
        for(int i=path.size()-1;i>0;i--){Point p=path.get(i);if(bridge(p.x(),p.z(),24)!=null)continue;
            Point join=null;double best=7;
            for(int dz=-6;dz<=6;dz++)for(int dx=-6;dx<=6;dx++){
                Point candidate=centers.get(key(p.x()+dx,p.z()+dz));if(candidate==null||bridge(candidate.x(),candidate.z(),24)!=null)continue;
                double d=Math.hypot(dx,dz);if(d<best){best=d;join=candidate;}
            }
            if(join!=null){Point end=i==path.size()-1?new Point(p.x(),join.y(),p.z()):p;List<Point> result=new ArrayList<>(line(join,end));result.addAll(path.subList(i+1,path.size()));return result;}
        }return path;
    }
    private static void add(List<Road> list,String name,boolean paved,List<Point> points,int width){list.add(new Road(list.size()+1,name,paved,points,width));}
    private Point on(List<Point> points,int x,int z){for(Point p:points)if(p.x()==x&&p.z()==z)return p;return ground(x,z);}
    private Point onExisting(List<Road> roads,Point point){for(Road r:roads)for(Point p:r.points())if(p.x()==point.x()&&p.z()==point.z())return p;return point;}
    private Point approach(Crossing b,int side){
        int u=side<0?-b.negative()-48:b.positive()+48,bank=side<0?b.leftY():b.rightY();Point natural=ground(b.wx(u,0),b.wz(u,0));
        return new Point(natural.x(),Math.max(bank-5,Math.min(bank+5,natural.y()-1))+1,natural.z());
    }
    private void join(List<Point> joined,List<Point> path){for(int i=0;i<path.size();i+=8){Point p=path.get(i);if(bridge(p.x(),p.z(),23)==null)joined.add(p);}joined.add(path.get(path.size()-1));}
    private List<Point> routeFromNetwork(List<Point> network,Point goal){
        List<Point> candidates=new ArrayList<>(network);candidates.sort(Comparator.comparingDouble(p->distance(p,goal)));
        int tried=0;for(Point p:candidates){List<Point> route=route(p,goal);if(!route.isEmpty())return route;if(++tried==12)break;}return List.of();
    }
    private List<Point> route(Point source,Point goal){
        int a=nearest(source),b=nearest(goal);if(a<0||b<0)return List.of();
        double[] cost=new double[n*n];Arrays.fill(cost,Double.POSITIVE_INFINITY);int[] parent=new int[n*n];Arrays.fill(parent,-1);
        PriorityQueue<Open> queue=new PriorityQueue<>();cost[a]=0;queue.add(new Open(a,0,0));
        int[] dx={1,1,0,-1,-1,-1,0,1},dz={0,1,1,1,0,-1,-1,-1};
        while(!queue.isEmpty()){
            Open node=queue.remove();int u=node.index;if(node.cost!=cost[u])continue;if(u==b)break;
            for(int d=0;d<8;d++){int i=u%n+dx[d],j=u/n+dz[d];if(i<0||j<0||i>=n||j>=n)continue;int v=j*n+i;
                if(!dry[v]||Math.abs(height[v]-height[u])>6||!edge(u,v))continue;
                double c=cost[u]+step*Math.hypot(dx[d],dz[d])+Math.abs(height[v]-height[u])*2.4;
                if(parent[u]>=0){int old=parent[u];if(u%n-old%n!=dx[d]||u/n-old/n!=dz[d])c+=5;}
                if(c<cost[v]){cost[v]=c;parent[v]=u;double estimate=Math.hypot(i-b%n,j-b/n)*step;queue.add(new Open(v,c,c+estimate));}
            }
        }
        if(!Double.isFinite(cost[b]))return List.of();List<Point> reverse=new ArrayList<>();
        for(int u=b;u!=-1;u=parent[u])reverse.add(new Point(terrain.min+(u%n)*step,height[u]+1,terrain.min+(u/n)*step));Collections.reverse(reverse);
        List<Point> simple=new ArrayList<>();simple.add(source);
        for(int i=0;i<reverse.size();){int far=i+1;while(far<reverse.size()&&distance(reverse.get(i),reverse.get(far))<=96&&clearLine(reverse.get(i),reverse.get(far)))far++;
            simple.add(reverse.get(i));i=Math.max(i+1,far-1);}
        simple.add(goal);List<Point> path=new ArrayList<>();for(int i=1;i<simple.size();i++)append(path,line(simple.get(i-1),simple.get(i)));
        return List.copyOf(path);
    }
    private static void append(List<Point> path,List<Point> segment){path.addAll(path.isEmpty()?segment:segment.subList(1,segment.size()));}
    private boolean clearLine(Point a,Point b){int length=(int)Math.ceil(distance(a,b));
        for(int k=0;k<=length;k+=2){double f=length==0?0:k/(double)length;int x=(int)Math.round(a.x()+(b.x()-a.x())*f),z=(int)Math.round(a.z()+(b.z()-a.z())*f);
            int gx=(int)Math.round((x-terrain.min)/(double)step),gz=(int)Math.round((z-terrain.min)/(double)step);
            if(gx<0||gz<0||gx>=n||gz>=n||!dry[gz*n+gx]||!column(x,z))return false;
            for(Crossing bridge:bridges)if(bridge.contains(x,z,23)&&Math.abs(bridge.alongX()?z-bridge.z():x-bridge.x())>0)return false;
        }return true;
    }
    private boolean edge(int a,int b){long key=key(Math.min(a,b),Math.max(a,b));Boolean cached=edgeMemo.get(key);if(cached!=null)return cached;
        boolean clear=edgeClear(a,b);edgeMemo.put(key,clear);return clear;}
    private boolean edgeClear(int a,int b){
        int x=terrain.min+a%n*step,z=terrain.min+a/n*step,ex=terrain.min+b%n*step,ez=terrain.min+b/n*step;
        for(int k=0;k<=step;k+=2){int px=x+(ex-x)*k/step,pz=z+(ez-z)*k/step;
            for(Crossing bridge:bridges)if(bridge.contains(px,pz,22)){
                int v=bridge.alongX()?pz-bridge.z():px-bridge.x();if(v!=0||(bridge.alongX()?ez!=z:ex!=x))return false;}
            if(!column(px,pz))return false;
        }return true;
    }
    private boolean column(int x,int z){long key=key(x,z);Boolean cached=columnMemo.get(key);if(cached!=null)return cached;
        boolean valid=columnClear(x,z);columnMemo.put(key,valid);return valid;}
    private boolean columnClear(int x,int z){
        for(int[] offset:new int[][]{{0,0},{3,0},{-3,0},{0,3},{0,-3}}){int px=x+offset[0],pz=z+offset[1];
            if(bridge(px,pz,0)!=null)continue;var s=raw(px,pz);if(s.height<63||s.channel>.18)return false;
            for(Site site:areas)if(site.contains(px,pz,4)){for(Building b:site.buildings())if(b.contains(px,pz,3))return false;for(Field f:site.fields())if(f.contains(px,pz,3))return false;}
        }return true;
    }
    private int nearest(Point p){int i=(int)Math.round((p.x()-terrain.min)/(double)step),j=(int)Math.round((p.z()-terrain.min)/(double)step),best=-1;double distance=Double.POSITIVE_INFINITY;
        for(int dz=-3;dz<=3;dz++)for(int dx=-3;dx<=3;dx++){int x=i+dx,z=j+dz;if(x<0||z<0||x>=n||z>=n||!dry[z*n+x])continue;
            double d=Math.hypot(terrain.min+x*step-p.x(),terrain.min+z*step-p.z());if(d<distance){best=z*n+x;distance=d;}}
        return best;
    }
    private List<Point> line(Point a,Point b){List<Point> list=new ArrayList<>();int length=Math.max(Math.abs(a.x()-b.x()),Math.abs(a.z()-b.z()));
        for(int i=0;i<=length;i++){double f=length==0?0:i/(double)length;int x=(int)Math.round(a.x()+(b.x()-a.x())*f),z=(int)Math.round(a.z()+(b.z()-a.z())*f);Crossing bridge=bridge(x,z,0);
            int y=bridge==null?geography.height(x,z,raw(x,z).height)+1:bridge.floor(bridge.u(x,z))+1;
            list.add(new Point(x,i==0?a.y():i==length?b.y():y,z));}return list;
    }
    private List<Point> grade(List<Point> path,int run){
        int count=path.size();if(count<2)return path;double[] y=new double[count],length=new double[count];boolean[] fixed=new boolean[count];
        for(int i=1;i<count;i++)length[i]=length[i-1]+distance(path.get(i-1),path.get(i));
        double slope=Math.max(1.0/run,Math.abs(path.get(count-1).y()-path.get(0).y())/Math.max(1,length[count-1]));
        for(int i=0;i<count;i++){double sum=0,weight=0;for(int j=Math.max(0,i-24);j<=Math.min(count-1,i+24);j++){double w=25-Math.abs(i-j);sum+=path.get(j).y()*w;weight+=w;}y[i]=sum/weight;}
        for(int i=0;i<count;i++){Point p=path.get(i);fixed[i]=i==0||i==count-1||bridge(p.x(),p.z(),0)!=null;if(fixed[i])y[i]=p.y();}
        double[] limits=new double[count];int anchor=0;
        for(int i=1;i<count;i++)if(fixed[i]){
            double required=Math.max(1.0/run,Math.abs(path.get(i).y()-path.get(anchor).y())/Math.max(1,length[i]-length[anchor]));
            for(int j=anchor+1;j<=i;j++)limits[j]=(length[j]-length[j-1])*required;anchor=i;
        }
        for(int pass=0;pass<12;pass++){
            for(int i=1;i<count;i++){double limit=limits[i];y[i]=fixed[i]?path.get(i).y():Math.max(y[i-1]-limit,Math.min(y[i-1]+limit,y[i]));}
            y[count-1]=path.get(count-1).y();for(int i=count-2;i>=0;i--){double limit=limits[i+1];y[i]=fixed[i]?path.get(i).y():Math.max(y[i+1]-limit,Math.min(y[i+1]+limit,y[i]));}y[0]=path.get(0).y();
        }
        List<Point> result=new ArrayList<>();for(int i=0;i<count;i++){Point p=path.get(i);Crossing b=bridge(p.x(),p.z(),0);result.add(new Point(p.x(),b==null?(int)Math.round(y[i]):b.floor(b.u(p.x(),p.z()))+1,p.z()));}return List.copyOf(result);
    }
    private static double distance(Point a,Point b){return Math.hypot(a.x()-b.x(),a.z()-b.z());}
    private List<Drain> drains(List<Road> roads){
        Map<Long,Integer> roadHeight=new HashMap<>();for(Road road:roads)for(Point p:road.points())roadHeight.putIfAbsent(key(p.x(),p.z()),p.y()-1);
        List<Drain> result=new ArrayList<>();for(Site s:sites){
            int sx=s.x(),sz=s.z()+3,start=roadHeight.getOrDefault(key(sx,sz),ground(sx,sz).y()-1)-6;
            int ex=sx-48,ez=sz+24,end=start-2;boolean outfall=false;double best=Double.POSITIVE_INFINITY;
            for(int dz=-160;dz<=160;dz+=8)for(int dx=-160;dx<=160;dx+=8){int x=sx+dx,z=sz+dz;
                if(Math.hypot(dx,dz)<30||terrain.edgeDistance(x,z)<65)continue;var sample=raw(x,z);if(sample.height>start-2)continue;
                boolean clash=false;for(Site area:areas)if(area.id()!=s.id()&&area.contains(x,z,12))clash=true;if(clash)continue;
                double score=Math.hypot(dx,dz)+(sample.height<62?-70:10);if(score<best){best=score;ex=x;ez=z;end=Math.min(start-2,sample.height-1);outfall=sample.height<62;}
            }
            List<Point> points=new ArrayList<>();int steps=Math.max(Math.abs(ex-sx),Math.abs(ez-sz)),invert=start;
            for(int i=0;i<=steps;i++){double f=i/(double)steps;int x=(int)Math.round(sx+(ex-sx)*f),z=(int)Math.round(sz+(ez-sz)*f);
                invert=Math.min(invert,(int)Math.floor(start+(end-start)*f));if(i<steps)invert=Math.min(invert,roadHeight.getOrDefault(key(x,z),ground(x,z).y()-1)-5);points.add(new Point(x,invert,z));}
            result.add(new Drain(s.id(),s.name()+" storm collector",points,outfall));
        }return List.copyOf(result);
    }
}
