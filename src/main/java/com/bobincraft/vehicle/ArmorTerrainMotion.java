package com.bobincraft.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Compact hull sections follow track contacts smoothly at any heading, bridging narrow trenches and pitching up onto steps without vertical snaps. */
public final class ArmorTerrainMotion {
    private static final double HALF_SECTION=.38,HALF_SIDE=.38;
    private static final double[] SIDES={-1.60,-.80,0,.80,1.60};
    private static final int MAX_BRIDGE_STEPS=4;
    private ArmorTerrainMotion() { }
    public record Step(Vec3d position,float pitch,float roll,boolean grounded,boolean blocked) { }
    private record Contact(int index,double forward,double side,double height,double localBase) { }
    private static Vec3d offset(Vec3d p,float yaw,double forward,double side){double a=Math.toRadians(yaw);return p.add(-Math.sin(a)*forward+Math.cos(a)*side,0,Math.cos(a)*forward+Math.sin(a)*side);}
    private static double cellOffset(double f,double side,double slope,double bank){return slope*f+bank*side-Math.abs(slope)*HALF_SECTION-Math.abs(bank)*HALF_SIDE;}
    public static List<Box> hull(Vec3d p,float yaw,float pitch,float roll){
        List<Box> boxes=new ArrayList<>();double a=Math.toRadians(yaw),slope=Math.tan(Math.toRadians(pitch)),bank=Math.tan(Math.toRadians(roll));
        double hx=Math.abs(Math.cos(a))*HALF_SIDE+Math.abs(Math.sin(a))*HALF_SECTION,hz=Math.abs(Math.cos(a))*HALF_SECTION+Math.abs(Math.sin(a))*HALF_SIDE;
        for(int i=-4;i<=4;i++)for(double side:SIDES){double f=i*.78;Vec3d c=offset(p,yaw,f,side);
            double floor=p.y+cellOffset(f,side,slope,bank);
            double minY=Math.max(p.y+ArmorControls.MAX_STEP+.04,floor+.45);
            boxes.add(new Box(c.x-hx,minY,c.z-hz,c.x+hx,p.y+3.10+slope*f+bank*side+Math.abs(bank)*HALF_SIDE,c.z+hz));
        }return boxes;
    }
    public static boolean clear(World w,Entity vehicle,Vec3d p,float yaw,float pitch,float roll){
        for(var b:hull(p,yaw,pitch,roll))if(w.getBlockCollisions(vehicle,b.contract(.015)).iterator().hasNext())return false;
        return true;
    }
    private static List<Contact> contacts(World w,Entity vehicle,Vec3d p,float yaw,float oldPitch,float oldRoll){
        List<Contact> contacts=new ArrayList<>();
        double a=Math.toRadians(yaw),oldSlope=Math.tan(Math.toRadians(oldPitch)),oldBank=Math.tan(Math.toRadians(oldRoll));
        double hx=Math.abs(Math.cos(a))*HALF_SIDE+Math.abs(Math.sin(a))*HALF_SECTION,hz=Math.abs(Math.cos(a))*HALF_SECTION+Math.abs(Math.sin(a))*HALF_SIDE;
        for(int i=-4;i<=4;i++)for(double side:SIDES){
            double forward=i*.78,localBase=p.y+oldSlope*forward+oldBank*side;
            Vec3d foot=offset(p,yaw,forward,side);double top=Double.NEGATIVE_INFINITY;
            Box probe=new Box(foot.x-hx,Math.min(p.y,localBase)-2.5,foot.z-hz,foot.x+hx,Math.max(p.y,localBase)+ArmorControls.MAX_STEP+.08,foot.z+hz);
            for(var shape:w.getBlockCollisions(vehicle,probe))top=Math.max(top,shape.getMax(Direction.Axis.Y));
            if(Double.isFinite(top))contacts.add(new Contact(i,forward,side,top,localBase));
        }return contacts;
    }
    public static Step advance(World w,Entity vehicle,Vec3d p,Vec3d travel,float yaw,float oldPitch,float oldRoll,double fallVelocity){
        Vec3d next=p.add(travel);var contacts=contacts(w,vehicle,next,yaw,oldPitch,oldRoll);
        if(contacts.stream().anyMatch(c->c.height>Math.max(p.y,c.localBase)+ArmorControls.MAX_STEP+.05))return new Step(p,oldPitch,oldRoll,true,true);
        double[] raw=new double[9];Arrays.fill(raw,Double.NEGATIVE_INFINITY);
        for(var c:contacts){int idx=c.index+4;if(c.height>raw[idx])raw[idx]=c.height;}
        double[] bridged=Arrays.copyOf(raw,9);
        // 1. Two-sided rigid bridging across gaps up to MAX_BRIDGE_STEPS (<= 3 blocks wide).
        for(int a=-4;a<=4;a++){
            double ha=raw[a+4];if(!Double.isFinite(ha))continue;
            for(int b=a+2;b<=Math.min(4,a+MAX_BRIDGE_STEPS);b++){
                double hb=raw[b+4];if(!Double.isFinite(hb))continue;
                double rim=Math.min(ha,hb)-.15;
                boolean isGap=true;
                for(int k=a+1;k<b;k++)if(Double.isFinite(raw[k+4])&&raw[k+4]>=rim){isGap=true;if(raw[k+4]>=rim){isGap=false;break;}}
                if(isGap){
                    for(int k=a+1;k<b;k++){
                        double interp=ha+(hb-ha)*(double)(k-a)/(b-a);
                        if(!Double.isFinite(bridged[k+4])||interp>bridged[k+4])bridged[k+4]=interp;
                    }
                }
            }
        }
        // 2. Cantilever support while the center of mass (i=0) and adjacent track sections rest on solid ground.
        double h0=bridged[4];
        if(Double.isFinite(h0)){
            if(Double.isFinite(bridged[2])&&Double.isFinite(bridged[3])&&bridged[2]>=h0-.15&&bridged[3]>=h0-.15){
                for(int k=1;k<=4;k++)if(!Double.isFinite(bridged[k+4])||bridged[k+4]<h0-.15)bridged[k+4]=h0;
            }
            if(Double.isFinite(bridged[5])&&Double.isFinite(bridged[6])&&bridged[5]>=h0-.15&&bridged[6]>=h0-.15){
                for(int k=-4;k<=-1;k++)if(!Double.isFinite(bridged[k+4])||bridged[k+4]<h0-.15)bridged[k+4]=h0;
            }
        }
        // 3. Wide unbridgeable trench (> 3 blocks): once the center of mass (i=0) drops into the trench while the rear is still on the near bank,
        // the downward-tilted nose is inside the trench and cannot simultaneously rest on the far bank until the rear leaves the near bank.
        int nearBank=-99;
        for(int a=-1;a>=-4;a--)if(Double.isFinite(bridged[a+4])&&(!Double.isFinite(h0)||bridged[a+4]>h0+.15)){nearBank=a;break;}
        if(nearBank>=-4){
            double bankH=bridged[nearBank+4];
            for(int b=1;b<=4;b++)if(b-nearBank>MAX_BRIDGE_STEPS&&Double.isFinite(bridged[b+4])&&bridged[b+4]>bankH-.15)bridged[b+4]=h0;
        }
        double front=stationEnvelope(bridged,1,4,p.y),rear=stationEnvelope(bridged,-4,-1,p.y);
        double right=envelope(contacts,false,true,p.y),left=envelope(contacts,false,false,p.y);
        float targetPitch=(float)MathHelper.clamp(Math.toDegrees(Math.atan2(front-rear,3.4)),-18,18);
        float targetRoll=(float)MathHelper.clamp(Math.toDegrees(Math.atan2(right-left,3.2)),-12,12);
        float pitch=approach(oldPitch,targetPitch,2.2f),roll=approach(oldRoll,targetRoll,1.8f);
        double support=Double.NEGATIVE_INFINITY;
        if(!contacts.isEmpty()){
            double allMean=stationEnvelope(bridged,-4,4,p.y);
            double midMean=stationEnvelope(bridged,-1,1,allMean);
            support=.72*allMean+.28*midMean;
        }
        double dy=Double.isFinite(support)?MathHelper.clamp(support-p.y,-.14,ArmorControls.CLIMB_PER_TICK):Math.max(-.6,fallVelocity-.06);
        Vec3d target=next.add(0,dy,0);
        boolean grounded=Double.isFinite(support)&&Math.abs(support-target.y)<.05;
        if(clear(w,vehicle,target,yaw,pitch,roll))return new Step(target,pitch,roll,grounded,false);
        if(clear(w,vehicle,target,yaw,oldPitch,oldRoll))return new Step(target,oldPitch,oldRoll,grounded,false);
        if(dy<0&&clear(w,vehicle,next,yaw,pitch,roll))return new Step(next,pitch,roll,false,false);
        if(dy<0&&clear(w,vehicle,next,yaw,oldPitch,oldRoll))return new Step(next,oldPitch,oldRoll,false,false);
        if(dy>0){
            for(double frac:new double[]{.5,0}){
                Vec3d lift=p.add(travel.x*frac,dy,travel.z*frac);
                if(clear(w,vehicle,lift,yaw,pitch,roll))return new Step(lift,pitch,roll,false,frac==0);
                if(clear(w,vehicle,lift,yaw,oldPitch,oldRoll))return new Step(lift,oldPitch,oldRoll,false,frac==0);
            }
        }
        if(dy<0){
            Vec3d drop=p.add(0,dy,0);
            if(clear(w,vehicle,drop,yaw,pitch,roll))return new Step(drop,pitch,roll,false,true);
            if(clear(w,vehicle,drop,yaw,oldPitch,oldRoll))return new Step(drop,oldPitch,oldRoll,false,true);
        }
        return new Step(p,oldPitch,oldRoll,true,true);
    }
    private static double stationEnvelope(double[] s,int from,int to,double fallback){
        double sum=0;int n=0;
        for(int i=from;i<=to;i++)if(Double.isFinite(s[i+4])){sum+=s[i+4];n++;}
        return n>0?sum/n:fallback;
    }
    private static double envelope(List<Contact> c,boolean longitudinal,boolean positive,double fallback){
        return c.stream().filter(v->longitudinal?(positive?v.forward>=.75:v.forward<=-.75):(positive?v.side>.4:v.side<-.4)).mapToDouble(Contact::height).average().orElse(fallback);
    }
    private static float approach(float value,float target,float speed){return value+MathHelper.clamp(target-value,-speed,speed);}
}
