package com.bobincraft.combat.client;

import com.bobincraft.combat.GuardInjuryPose;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.math.MathHelper;

/** Slow alternating shoulder pulls when alive, and distinct crawl / blast / fallen death poses when dead. */
public final class InjuryCrawlPose {
    private InjuryCrawlPose() { }
    public static void lockMissing(BipedEntityModel<?> m,int mask){
        var parts=new net.minecraft.client.model.ModelPart[]{m.leftArm,m.rightArm,m.leftLeg,m.rightLeg};
        for(int i=0;i<4;i++)if((mask&(1<<i))!=0){var p=parts[i];p.pitch=p.yaw=p.roll=0;p.pivotZ=0;p.pivotY=i<2?2:12;p.pivotX=i==0?5:i==1?-5:i==2?1.9f:-1.9f;}
    }
    public static void apply(BipedEntityModel<?> m,int missing,float prone,float phase,float movement){
        if(prone<=0)return;
        int dPose=GuardInjuryPose.deathPose(missing);
        if(dPose==GuardInjuryPose.POSE_BLAST){
            // Killed instantly by explosion: sprawled on back with surviving limbs flung wide
            m.body.pitch=0f;m.body.yaw=.06f;m.body.roll=-.05f;
            m.head.pitch=.38f;m.head.yaw=.28f;m.hat.copyTransform(m.head);
            if((missing&1)==0){m.leftArm.pitch=-2.35f;m.leftArm.yaw=.65f;m.leftArm.roll=-.45f;m.leftArm.pivotY=2;m.leftArm.pivotZ=0;}
            if((missing&2)==0){m.rightArm.pitch=-2.15f;m.rightArm.yaw=-.75f;m.rightArm.roll=.50f;m.rightArm.pivotY=2;m.rightArm.pivotZ=0;}
            if((missing&4)==0){m.leftLeg.pitch=.22f;m.leftLeg.yaw=-.38f;m.leftLeg.roll=-.18f;}
            if((missing&8)==0){m.rightLeg.pitch=-.18f;m.rightLeg.yaw=.44f;m.rightLeg.roll=.22f;}
            return;
        }
        if(dPose==GuardInjuryPose.POSE_FALLEN){
            // Killed while standing: collapsed face/side down with one arm tucked and one relaxed
            m.body.pitch=.08f;m.body.yaw=-.08f;m.body.roll=.06f;
            m.head.pitch=-.45f;m.head.yaw=-.48f;m.hat.copyTransform(m.head);
            if((missing&1)==0){m.leftArm.pitch=-1.25f;m.leftArm.yaw=.35f;m.leftArm.roll=-.22f;m.leftArm.pivotY=2;m.leftArm.pivotZ=0;}
            if((missing&2)==0){m.rightArm.pitch=-.35f;m.rightArm.yaw=-.25f;m.rightArm.roll=.28f;m.rightArm.pivotY=2;m.rightArm.pivotZ=0;}
            if((missing&4)==0){m.leftLeg.pitch=-.28f;m.leftLeg.yaw=.16f;m.leftLeg.roll=-.08f;}
            if((missing&8)==0){m.rightLeg.pitch=.14f;m.rightLeg.yaw=-.22f;m.rightLeg.roll=.08f;}
            return;
        }
        if(dPose==GuardInjuryPose.POSE_CRAWL){
            // Died while crawling: head dropped mid-crawl, one surviving arm outstretched forward
            m.body.pitch=0f;m.body.yaw=.05f;m.body.roll=.04f;
            m.head.pitch=-.22f;m.head.yaw=.32f;m.hat.copyTransform(m.head);
            if((missing&1)==0){m.leftArm.pitch=-2.92f;m.leftArm.yaw=.22f;m.leftArm.roll=.14f;m.leftArm.pivotY=2.2f;m.leftArm.pivotZ=.8f;}
            if((missing&2)==0){m.rightArm.pitch=-1.85f;m.rightArm.yaw=-.32f;m.rightArm.roll=-.18f;m.rightArm.pivotY=2f;m.rightArm.pivotZ=-.4f;}
            if((missing&4)==0){m.leftLeg.pitch=.12f;m.leftLeg.yaw=.08f;m.leftLeg.roll=0f;}
            if((missing&8)==0){m.rightLeg.pitch=.05f;m.rightLeg.yaw=-.12f;m.rightLeg.roll=0f;}
            return;
        }
        float effort=MathHelper.clamp(movement*7,0,1),s=MathHelper.sin(phase),c=MathHelper.cos(phase);
        m.body.pitch=MathHelper.lerp(prone,m.body.pitch,0);
        m.body.yaw=MathHelper.lerp(prone,m.body.yaw,s*.07f*effort);
        m.body.roll=MathHelper.lerp(prone,m.body.roll,s*.055f*effort);
        m.head.pitch=MathHelper.lerp(prone,m.head.pitch,-1.25f);
        m.head.yaw=MathHelper.clamp(m.head.yaw,-.55f,.55f);m.hat.copyTransform(m.head);
        var arms=new net.minecraft.client.model.ModelPart[]{m.leftArm,m.rightArm};
        for(int i=0;i<2;i++){
            var a=arms[i];float side=i==0?1:-1,stroke=s*side;
            if((missing&(1<<i))!=0){a.pitch=a.yaw=a.roll=0;continue;}
            a.pitch=MathHelper.lerp(prone,a.pitch,-3.08f+stroke*.16f*effort);
            a.yaw=MathHelper.lerp(prone,a.yaw,side*(.14f+Math.max(0,c*side)*.24f*effort));
            a.roll=side*.13f*prone;a.pivotY=2+stroke*.3f*effort*prone;a.pivotZ=stroke*1.1f*effort*prone;
        }
        m.leftLeg.pitch=(missing&4)==0?.08f-s*.08f*effort:0;m.rightLeg.pitch=(missing&8)==0?.08f+s*.08f*effort:0;
        m.leftLeg.yaw=.035f;m.rightLeg.yaw=-.035f;m.leftLeg.roll=m.rightLeg.roll=0;
    }
}
