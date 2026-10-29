package com.bobincraft.combat;
import java.util.*;
public final class Stage21Contract {
 public static void main(String[] args) {
  int checks=0;
  for(var p:ExplosionVisualProfile.values()) for(int budget:new int[]{0,1,4,12,64}) {
   var layers=WeaponExplosionLayers.create(p,14,budget);
   if(layers.size()>budget || !layers.equals(WeaponExplosionLayers.create(p,14,budget))) throw new AssertionError("budget/determinism");
   if(budget>0 && (layers.get(0).kind()!=WeaponExplosionLayers.Kind.FLASH || layers.get(0).puff().life()!=p.flashTicks)) throw new AssertionError("flash profile");
   for(var l:layers) if(l.puff().life()>101 || l.puff().life()<1 || !Float.isFinite(l.expansion())) throw new AssertionError("invalid envelope");
   if(budget==64) for(var kind:WeaponExplosionLayers.Kind.values()) if(layers.stream().noneMatch(l->l.kind()==kind)) throw new AssertionError("missing layer "+p+kind);
   checks++;
  }
  System.out.println("PASS Stage 2.1: "+checks+" profile/budget scenarios; all five layers, flash duration, lifetimes and determinism");
 }
}
