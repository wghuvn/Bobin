package com.bobincraft.armament;

import com.bobincraft.armament.client.animation.gltf.*;
import org.junit.jupiter.api.Test;
import static com.bobincraft.armament.client.animation.gltf.GltfAnimationController.*;
import static org.junit.jupiter.api.Assertions.*;

final class GltfSmoothBlendTest {
    private static GltfAnimationController constantBlend() {
        var c=new GltfAnimationController(GltfMathTest.model());
        c.play("a",Mode.HOLD,0,0);
        c.play("b",Mode.HOLD,1,1,BlendCurve.SMOOTHSTEP);
        return c;
    }
    @Test void smoothWeightEasesBothEndsWithoutOvershootOrChangingLegacyLinearDefault() {
        var c=constantBlend();
        assertEquals(2,c.sampleSeconds(1).local(0,0),1e-12);
        assertEquals(2.625,c.sampleSeconds(1.25).local(0,0),1e-12);
        assertEquals(4,c.sampleSeconds(1.5).local(0,0),1e-12);
        assertEquals(5.375,c.sampleSeconds(1.75).local(0,0),1e-12);
        assertEquals(6,c.sampleSeconds(2).local(0,0),1e-12);
        double epsilon=1e-4;
        assertEquals(0,(c.sampleSeconds(1+epsilon).local(0,0)-2)/epsilon,.002);
        assertEquals(0,(6-c.sampleSeconds(2-epsilon).local(0,0))/epsilon,.002);
        for(int i=0;i<=100;i++){double p=c.sampleSeconds(1+i/100.0).local(0,0);assertTrue(p>=2&&p<=6);}
        var legacy=new GltfAnimationController(GltfMathTest.model());
        legacy.play("a",Mode.HOLD,0,0);legacy.play("b",Mode.HOLD,1,1);
        assertEquals(3,legacy.sampleSeconds(1.25).local(0,0),1e-12);
    }
    @Test void smoothBlendStillAdvancesTheOutgoingClip() {
        var c=new GltfAnimationController(GltfMathTest.model());
        c.play("ramp",Mode.HOLD,0,0);c.play("b",Mode.HOLD,.25,1,BlendCurve.SMOOTHSTEP);
        assertEquals(1.78125,c.sampleSeconds(.5).local(0,0),1e-12);
    }
    @Test void interruptionCapturesTheActualEasedPoseAndConverges() {
        var c=constantBlend();double before=c.sampleSeconds(1.25).local(0,0);
        c.play("a",Mode.HOLD,1.25,.5,BlendCurve.SMOOTHSTEP);
        assertEquals(before,c.sampleSeconds(1.25).local(0,0),1e-12);
        assertEquals((before+2)/2,c.sampleSeconds(1.5).local(0,0),1e-12);
        assertEquals(2,c.sampleSeconds(1.75).local(0,0),1e-12);
    }
    @Test void easedInterruptedBlendsAreIndependentOfIntermediateRenderSamples() {
        var sparse=constantBlend();var dense=constantBlend();
        for(double t:new double[]{1.08,1.19,1.31}){
            for(int f=0;f<30;f++)dense.sampleSeconds(t-f/1000.0);
            assertEquals(sparse.sampleSeconds(t).local(0,0),dense.sampleSeconds(t).local(0,0),1e-12);
            sparse.play("a",Mode.HOLD,t,.2,BlendCurve.SMOOTHSTEP);
            dense.play("a",Mode.HOLD,t,.2,BlendCurve.SMOOTHSTEP);
        }
        assertEquals(sparse.sampleSeconds(1.4).local(0,0),dense.sampleSeconds(1.4).local(0,0),1e-12);
    }
}
