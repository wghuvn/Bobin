package com.bobincraft.destruction.debris;

import java.util.*;

/** Per-connection visibility generation and sequence ordering, separate from persistent fragment revisions. */
public final class DebrisRevisionWindow {
    private UUID epoch;
    private String dimension="";
    private long sequence;
    private final Map<UUID,Long> revisions=new HashMap<>();
    public void clear(){epoch=null;dimension="";sequence=0;revisions.clear();}
    public boolean reset(String dimension,UUID epoch,long sequence) {
        if(dimension==null||dimension.length()>128||epoch==null||sequence<=0)return false;
        this.epoch=epoch;this.dimension=dimension;this.sequence=sequence;revisions.clear();return true;
    }
    public String dimension(){return dimension;}
    public boolean accept(String dimension,UUID epoch,long sequence,UUID id,long revision,boolean removed) {
        if(!Objects.equals(this.epoch,epoch)||!this.dimension.equals(dimension)||sequence<=this.sequence||id==null)return false;
        this.sequence=sequence;
        if(removed){revisions.remove(id);return true;}
        if(revision<1||revision<revisions.getOrDefault(id,0L)||!revisions.containsKey(id)&&revisions.size()>=8192)return false;
        revisions.put(id,revision);return true;
    }
}
