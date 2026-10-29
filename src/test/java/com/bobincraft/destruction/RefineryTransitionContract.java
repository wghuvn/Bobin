package com.bobincraft.destruction;
import java.util.*;
public final class RefineryTransitionContract {
 static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args) {
  for(int variant=0;variant<4;variant++) for(int[] size:new int[][]{{8,10},{6,8},{7,11},{5,9}}) {
   int r=size[0],h=size[1];
   require(RefineryGeometryRules.includes(r,h,0,h+1,0),"roof hopper is included");
   require(RefineryGeometryRules.includes(r,h,0,h-1,0),"explicit hollow air is included");
   require(!RefineryGeometryRules.includes(r,h,r+2,h+1,0),"outside upper ring not guessed");
   for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(x*x+z*z<=r*r) {
    require(RefineryGeometryRules.survivingTop(r,h,variant,x,z,4)<=h-3,"destroyed top band must be removed");
   }
  }
  var l=new TransitionLedger();l.owned.put(1L,"roof");l.owned.put(2L,"wall");
  l.retarget(Map.of(1L,"air",2L,"rust")); // first transition hasn't run yet
  l.retarget(Map.of(1L,"air",2L,"air")); // destination still includes the unprocessed roof
  require(l.pending.size()==2 && l.pending.get(1L).equals("air"),"retarget lost pending roof removal");
  require(l.canApply(1,"roof"),"expected original state");l.applied(1);
  require(!l.canApply(1,"air"),"recovery must not repeat completed work");
  require(!l.canApply(2,"player_block") && l.protectedCells.contains(2L),"preserve player mismatch");
  l.retarget(Map.of(2L,"air"));require(l.conflicts.contains(2L),"retarget must preserve conflict");
  var afterCrash=new TransitionLedger();afterCrash.owned.put(1L,"roof");afterCrash.pending.put(1L,"air");
  require(!afterCrash.canApply(1,"air") && afterCrash.pending.isEmpty(),"reconcile world saved before journal");
  var legacy=new TransitionLedger();legacy.retarget(Map.of(1L,"air"));
  require(legacy.pending.containsKey(1L) && legacy.conflicts.contains(1L) && !legacy.canApply(1,"roof"),"ambiguous legacy must not be deleted");
  legacy.owned.put(1L,"roof");legacy.conflicts.remove(1L);
  require(legacy.canApply(1,"roof"),"newly generated chunk must retain its pending target");legacy.applied(1);
  var replacement=new TransitionLedger();replacement.owned.put(1L,"metal");replacement.protect(1L);
  replacement.retarget(Map.of(1L,"air"));require(replacement.pending.containsKey(1L) && !replacement.canApply(1,"metal"),"same-material replacement target retained but protected");
  System.out.println("PASS Stage 4: all 4 tank sizes x 4 variants; roof bounds, interrupted retarget, crash reconciliation, idempotence, legacy and player conflicts");
 }
}
