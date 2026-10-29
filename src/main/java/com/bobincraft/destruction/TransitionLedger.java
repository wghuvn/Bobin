package com.bobincraft.destruction;

import java.util.*;

/** Persistent expected/target records plus rebuildable, non-persistent runnable/chunk indexes.
 * Version-1 journals retain their existing representation; scheduling never establishes ownership. */
public final class TransitionLedger {
    public final Map<Long,String> owned=new LinkedHashMap<>();
    public final Map<Long,String> pending=new LinkedHashMap<>();
    public final Set<Long> protectedCells=new HashSet<>();
    public final Set<Long> conflicts=new HashSet<>();
    public UUID operation=UUID.randomUUID();
    public int revision=2, variant, stage;
    private final ArrayDeque<Long> ready=new ArrayDeque<>();
    private final Set<Long> queued=new HashSet<>();
    private final Map<Long,LinkedHashSet<Long>> unloaded=new LinkedHashMap<>();
    private long mutations;

    public long mutations(){return mutations;}
    public static long chunk(long packed) {
        int x=(int)(packed>>38),z=(int)(packed<<26>>38);
        return ((long)(x>>4)&0xffffffffL)|((long)(z>>4)<<32);
    }
    public void rebuildScheduling() {
        ready.clear();queued.clear();unloaded.clear();
        var ordered=new ArrayList<>(pending.keySet());
        ordered.sort(Comparator.comparingInt(p->-(int)(p<<52>>52)));
        for(long p:ordered)wake(p);
    }
    private void wake(long pos) {
        if(pending.containsKey(pos)&&owned.containsKey(pos)&&!protectedCells.contains(pos)&&queued.add(pos))ready.add(pos);
    }
    public boolean hasReady(){return !ready.isEmpty();}
    /** A stale candidate costs one check, rather than an unbounded discard loop. */
    public Long poll(){Long p=ready.poll();if(p!=null)queued.remove(p);return p;}
    public boolean actionable(long p){return pending.containsKey(p)&&owned.containsKey(p)&&!protectedCells.contains(p);}
    public void defer(long pos){unloaded.computeIfAbsent(chunk(pos),k->new LinkedHashSet<>()).add(pos);}
    public Set<Long> deferredChunks(){return Collections.unmodifiableSet(unloaded.keySet());}
    public int wakeChunk(long key,int budget) {
        var cells=unloaded.get(key);if(cells==null)return 0;
        int work=0;var it=cells.iterator();
        while(it.hasNext()&&work<budget){long pos=it.next();it.remove();wake(pos);work++;}
        if(cells.isEmpty())unloaded.remove(key);return work;
    }
    public void retry(long pos){wake(pos);}
    public boolean noteOwned(long pos,String state) {
        if(protectedCells.contains(pos)||owned.containsKey(pos))return false;
        owned.put(pos,state);conflicts.remove(pos);mutations++;wake(pos);return true;
    }
    public void retarget(Map<Long,String> target) {
        // Full target maps retain unfinished removals. Protected targets are retained for diagnosis/recovery.
        for(var e:target.entrySet()) {
            long p=e.getKey();String next=e.getValue();
            if(!protectedCells.contains(p)&&owned.containsKey(p)&&Objects.equals(owned.get(p),next)) {
                if(pending.remove(p)!=null)mutations++;
                if(conflicts.remove(p))mutations++;
            } else {
                if(!Objects.equals(pending.put(p,next),next))mutations++;
                if((protectedCells.contains(p)||!owned.containsKey(p))&&conflicts.add(p))mutations++;
            }
        }
        rebuildScheduling(); // Once per retarget, never per tick.
    }
    public boolean canApply(long pos,String current) {
        if(!pending.containsKey(pos))return false;
        if(protectedCells.contains(pos)||!owned.containsKey(pos)) {
            if(conflicts.add(pos))mutations++;return false;
        }
        if(Objects.equals(current,pending.get(pos))) {applied(pos);return false;}
        if(!Objects.equals(current,owned.get(pos))) {protect(pos);return false;}
        return true;
    }
    public void applied(long pos) {
        String result=pending.remove(pos);if(result==null)return;
        owned.put(pos,result);conflicts.remove(pos);queued.remove(pos);mutations++;
    }
    public void protect(long pos) {
        if(protectedCells.add(pos))mutations++;
        queued.remove(pos);
        if(pending.containsKey(pos)&&conflicts.add(pos))mutations++;
    }
    public String counts() {
        int provenance=0,protectedCount=0,completed=0,deferred=0,actionable=0;
        for(long p:pending.keySet())if(protectedCells.contains(p))protectedCount++;
            else if(!owned.containsKey(p))provenance++;else actionable++;
        for(var cells:unloaded.values())for(long p:cells)if(actionable(p))deferred++;
        for(long p:owned.keySet())if(!pending.containsKey(p))completed++;
        return " active="+Math.max(0,actionable-deferred)+" deferred="+deferred+" awaiting_provenance="+provenance
                +" protected_conflict="+protectedCount+" completed="+completed;
    }
}
