package com.bobincraft.destruction;

/** Coordinate mask and deterministic removal limits; materials always come from the actual site blueprint. */
public final class RefineryGeometryRules {
    private RefineryGeometryRules() {}
    public static boolean includes(int r,int height,int a,int dy,int b) {
        if(dy==0)return a*a+b*b<=(r+2)*(r+2);
        if(dy>=1&&dy<=height&&a*a+b*b<=r*r)return true;
        return (a==r&&b==0&&dy>=1&&dy<=height) || (a==0&&b==r&&dy==2)
            || (a==0&&b==r+1&&dy==1) || (a==0&&b==0&&dy==height+1);
    }
    public static int survivingTop(int r,int height,int variant,int a,int b,int stage) {
        int v=variant&3,dx=(v==0||v==2)?1:-1,dz=v<2?1:-1;
        double proj=(a*dx+b*dz)/(double)Math.max(1,r);
        if(stage==0)return height+1;
        if(stage==1)return proj>.65?height-1:height;
        if(stage==2)return Math.max(2,height-(proj>.25?4:2));
        double angle=Math.atan2(b,a)-Math.atan2(dz,dx);
        double shoulder=(1-Math.cos(angle))*.5;
        return Math.max(1,Math.min(height-3,1+(int)Math.round(4*shoulder)));
    }
}
