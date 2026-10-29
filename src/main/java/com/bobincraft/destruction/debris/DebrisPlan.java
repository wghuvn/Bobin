package com.bobincraft.destruction.debris;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Finite connected source patches: roof plates up to 4x4, shell/attachment strips at most two blocks high. */
public final class DebrisPlan {
    public record Source(int x,int y,int z,String state) {
        public long packed(){return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|((long)y&0xfffL);}
    }
    /** Relative gameplay mass; registry identity is retained, and unknown materials stay conservative. */
    public static double materialMass(String state) {
        if(state.contains("iron")||state.contains("metal")||state.contains("copper")||state.contains("andesite"))return 4;
        if(state.contains("oak")||state.contains("wood")||state.contains("plank"))return .7;
        if(state.contains("glass"))return 2;
        return 2.4;
    }
    private DebrisPlan(){}
    public static List<DebrisFragment> create(String dimension,String structure,UUID operation,Collection<Source> source) {
        if(source.size()>65536)throw new IllegalArgumentException("Operation source-cell limit");
        var remaining=new TreeMap<Long,Source>();for(var c:source)if(remaining.put(c.packed(),c)!=null)throw new IllegalArgumentException("Duplicate source cell");
        var result=new ArrayList<DebrisFragment>();
        while(!remaining.isEmpty()) {
            Source seed=remaining.firstEntry().getValue();var queue=new ArrayDeque<Source>();queue.add(seed);var group=new ArrayList<Source>();var scheduled=new HashSet<Long>();scheduled.add(seed.packed());
            while(!queue.isEmpty()&&group.size()<MAX_CELLS) {
                Source c=queue.remove();if(!remaining.containsKey(c.packed()))continue;remaining.remove(c.packed());group.add(c);
                for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
                    Source probe=new Source(c.x+d[0],c.y+d[1],c.z+d[2],"");Source next=remaining.get(probe.packed());
                    if(next!=null&&Math.floorDiv(next.x,4)==Math.floorDiv(seed.x,4)&&Math.floorDiv(next.y,2)==Math.floorDiv(seed.y,2)
                            &&Math.floorDiv(next.z,4)==Math.floorDiv(seed.z,4)&&scheduled.add(next.packed()))queue.add(next);
                }
            }
            double x=group.stream().mapToDouble(c->c.x+.5).average().orElseThrow(),y=group.stream().mapToDouble(c->c.y+.5).average().orElseThrow(),z=group.stream().mapToDouble(c->c.z+.5).average().orElseThrow();
            V pivot=new V(x,y,z);var cells=new ArrayList<Cell>();for(var c:group)cells.add(new Cell(c.packed(),new V(c.x-x,c.y-y,c.z-z),c.state));
            int index=result.size();UUID id=UUID.nameUUIDFromBytes((dimension+"/"+structure+"/"+operation+"/"+index).getBytes(StandardCharsets.UTF_8));
            result.add(new DebrisFragment(id,operation,dimension,structure,index,cells,pivot));
        }
        return result;
    }
}
