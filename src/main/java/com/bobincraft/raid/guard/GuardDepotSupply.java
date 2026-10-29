package com.bobincraft.raid.guard;

/** A finite reserve issued only after five calm seconds physically spent at the depot. */
public final class GuardDepotSupply {
    public record Snapshot(boolean consumed,int workTicks) { }
    private boolean consumed;
    private int workTicks;
    public boolean consumed() { return consumed; }
    public boolean tick(boolean calm,boolean atDepot,boolean empty) {
        if(consumed || !empty) return false;
        if(!calm) return false;
        if(!atDepot){workTicks=0;return false;}
        if(++workTicks<100)return false;
        consumed=true;workTicks=0;return true;
    }
    public Snapshot snapshot(){return new Snapshot(consumed,workTicks);}
    public void restore(Snapshot saved){consumed=saved.consumed;workTicks=Math.max(0,Math.min(99,saved.workTicks));}
}
