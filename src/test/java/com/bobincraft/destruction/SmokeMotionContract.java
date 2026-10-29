package com.bobincraft.destruction;
public final class SmokeMotionContract {
 public static void main(String[] args) {
  for(int i=0;i<10000;i++) {
   double a=IndustrialSmokeMotion.curl(i*.1, i*.07, -i*.03, i, .4);
   if(!Double.isFinite(a)||Math.abs(a)>.00580001) throw new AssertionError("unbounded turbulence");
   if(a!=IndustrialSmokeMotion.curl(i*.1,i*.07,-i*.03,i,.4)) throw new AssertionError("nondeterministic");
   double v=IndustrialSmokeMotion.variation(i*.1,i);
   if(v<.8749||v>1.1251)throw new AssertionError("scale runaway");
   if(Math.abs(IndustrialSmokeMotion.windX(i+1)-IndustrialSmokeMotion.windX(i))>.00002)throw new AssertionError("wind discontinuity");
  }
  System.out.println("PASS Stage 3: 10000 bounded deterministic wind/turbulence/envelope samples");
 }
}
