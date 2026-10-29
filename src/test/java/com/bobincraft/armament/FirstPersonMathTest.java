package com.bobincraft.armament;

import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.client.animation.gltf.RigMath;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

final class FirstPersonMathTest {
    static double[] identity(){double[] m=new double[16];RigMath.identity(m,0);return m;}
    static FirstPersonProfile.Inertia settings(){return new FirstPersonProfile.Inertia(true,4,0.016,1.5,1,0.0015);}
    @TestFactory Stream<DynamicTest> perspectiveUsesActualFovAndAspect(){
        return Stream.of(30.0,70.0,90.0,110.0).flatMap(fov->Stream.of(4.0/3,16.0/9,21.0/9,9.0/16).map(aspect->DynamicTest.dynamicTest(fov+"deg@"+aspect,()->{
            double[] m=new double[16];PresentationMath.perspective(fov,aspect,0.05,256,m);
            assertEquals(0.05,PresentationMath.nearPlane(m),1e-12);assertEquals(aspect,m[5]/m[0],1e-12);
            double half=Math.tan(Math.toRadians(fov)/2);assertEquals(1,m[5]*half,1e-12);
            assertEquals(1,m[0]*half*aspect,1e-12);assertEquals(-1,m[11]);
        })));
    }
    @Test void attachmentAlignmentPreservesSocketOrientationAndMetres(){
        double[] target=identity(),child=identity(),inverse=new double[16],root=new double[16],actual=new double[16];
        double[] trs={0.25,-0.2,-0.7,0,Math.sin(0.3),0,Math.cos(0.3),1,1,1};RigMath.compose(trs,0,target,0);child[12]=0.12;child[13]=0.03;
        PresentationMath.attach(target,child,inverse,root);RigMath.multiply(root,0,child,0,actual,0);assertArrayEquals(target,actual,1e-12);assertEquals(1,RigMath.determinant(root,0),1e-12);
    }
    @Test void normalsFollowCameraAndModelRotation(){
        double[] trs={1,2,3,0,Math.sin(Math.PI/4),0,Math.cos(Math.PI/4),1,1,1};double[] m=new double[16],inverse=new double[16],normal=new double[16];RigMath.compose(trs,0,m,0);PresentationMath.normalMatrix(m,inverse,normal);
        assertEquals(1,normal[8],1e-12);assertEquals(0,normal[12]);assertEquals(0,normal[13]);assertEquals(0,normal[14]);
    }
    @Test void inertiaIsBoundedAndTakesShortestYawDelta(){
        var a=new DampedViewInertia(settings());a.tick(0,179,0);a.tick(1,-179,4);
        assertTrue(a.yaw(0.5f)<0,"+2 degree turn, not a -358 degree jump");
        for(int i=2;i<100;i++){a.tick(i,i%2==0?170:-170,i%2==0?80:-80);assertTrue(Math.abs(a.yaw(0.75f))<=1.5);assertTrue(Math.abs(a.pitch(0.75f))<=1);}
    }
    @Test void renderFrequencyCannotChangeInertiaState(){
        var slow=new DampedViewInertia(settings());var fast=new DampedViewInertia(settings());
        for(int tick=0;tick<80;tick++){
            double yaw=tick<20?tick*2:tick<40?40-(tick-20)*3:tick<60?-20+(tick-40)*1.5:10;
            slow.tick(tick,yaw,yaw/3);fast.tick(tick,yaw,yaw/3);
            for(int frame=0;frame<30;frame++){fast.yaw(frame/30f);fast.pitch(frame/30f);}
            assertEquals(slow.yaw(0.5f),fast.yaw(0.5f),0);assertEquals(slow.pitch(0.5f),fast.pitch(0.5f),0);
        }
    }
    @Test void inertiaCanBeDisabledAndSettlesWithoutOscillation(){
        var a=new DampedViewInertia(settings());a.tick(0,0,0);a.tick(1,10,3);double maximum=Math.abs(a.yaw(0.99f));assertTrue(maximum>0);
        for(int i=2;i<120;i++)a.tick(i,10,3);assertTrue(Math.abs(a.yaw(0.5f))<1e-10);
        a.enabled(false);a.tick(121,100,80);assertEquals(0,a.yaw(0.5f));assertEquals(0,a.pitch(0.5f));
    }
    @Test void invalidProjectionAndClockAreRejected(){
        assertThrows(IllegalArgumentException.class,()->PresentationMath.perspective(0,1,0.05,100,new double[16]));
        assertThrows(IllegalArgumentException.class,()->PresentationMath.perspective(70,0,0.05,100,new double[16]));
        assertThrows(IllegalArgumentException.class,()->new DampedViewInertia(settings()).tick(-1,0,0));
    }
    static FirstPersonEligibility.View ready(){return new FirstPersonEligibility.View(true,true,true,true,false,true,true,true,true,true,false,false,false,true,true);}
    @Test void onlyCompatibleEmptyHandsCanEnterDiagnostic(){assertEquals("ready",FirstPersonEligibility.reason(ready()));}
    @TestFactory Stream<DynamicTest> eachCompatibilityGatePreservesVanilla(){
        boolean[] flags={true,true,true,true,false,true,true,true,true,true,false,false,false,true,true};
        return java.util.stream.IntStream.range(0,flags.length).mapToObj(i->DynamicTest.dynamicTest("gate "+i,()->{
            boolean[] a=flags.clone();a[i]=!a[i];var v=new FirstPersonEligibility.View(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10],a[11],a[12],a[13],a[14]);
            assertNotEquals("ready",FirstPersonEligibility.reason(v));
        }));
    }
}
