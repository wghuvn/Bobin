package com.bobincraft.armament;

import com.bobincraft.armament.client.animation.gltf.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static com.bobincraft.armament.client.animation.gltf.AnimationClip.*;

final class GltfMathTest {
    static double[] bind(double x){return new double[]{x,0,0,0,0,0,1,1,1,1};}
    static Channel translation(int node,double x){return new Channel(node,Path.TRANSLATION,Interpolation.LINEAR,new double[]{0,1},new double[]{x,0,0,x,0,0});}
    static RigModel model(){return new RigModel(List.of(new RigModel.Node("root",-1,bind(0),null),new RigModel.Node("child",0,bind(1),null)),List.of(),List.of(),List.of(
        new AnimationClip("a",List.of(translation(0,2),translation(1,3))),new AnimationClip("b",List.of(translation(0,6),translation(1,7))),
        new AnimationClip("ramp",List.of(new Channel(0,Path.TRANSLATION,Interpolation.LINEAR,new double[]{0,1},new double[]{0,0,0,2,0,0})))));}

    @Test void linearAndClampedTime(){
        var c=new Channel(0,Path.TRANSLATION,Interpolation.LINEAR,new double[]{1,3},new double[]{0,0,0,2,4,6});double[] out=new double[3];
        c.sample(2,out,0);assertArrayEquals(new double[]{1,2,3},out,1e-12);
        c.sample(0,out,0);assertArrayEquals(new double[3],out);c.sample(4,out,0);assertArrayEquals(new double[]{2,4,6},out);
    }
    @Test void stepChangesExactlyAtKey(){
        var c=new Channel(0,Path.SCALE,Interpolation.STEP,new double[]{0,1},new double[]{1,1,1,2,3,4});double[] out=new double[3];
        c.sample(Math.nextDown(1.0),out,0);assertArrayEquals(new double[]{1,1,1},out);c.sample(1,out,0);assertArrayEquals(new double[]{2,3,4},out);
    }
    @Test void slerpUsesShortestPathAndKeepsInputs(){
        double a=Math.toRadians(170)/2;double[] first={0,0,0,1},last={0,0,-Math.sin(a),-Math.cos(a)},out=new double[4];
        RigMath.slerp(first,0,last,0,0.5,out,0);assertEquals(Math.sin(a/2),out[2],1e-12);assertEquals(Math.cos(a/2),out[3],1e-12);
        assertArrayEquals(new double[]{0,0,0,1},first);assertEquals(-Math.sin(a),last[2]);
    }
    @Test void antipodalQuaternionHasNoSpin(){double[] q={0,0,0,1},r={0,0,0,-1},out=new double[4];RigMath.slerp(q,0,r,0,0.5,out,0);assertArrayEquals(q,out);}
    @Test void cubicHermiteMultipliesTangentsByInterval(){
        double[] values={0,0,0, 0,0,0, 1,0,0, 0,0,0, 2,0,0, 0,0,0};
        var c=new Channel(0,Path.TRANSLATION,Interpolation.CUBICSPLINE,new double[]{1,3},values);double[] out=new double[3];
        c.sample(1.5,out,0);assertEquals(0.59375,out[0],1e-12);
    }
    @Test void cubicQuaternionNormalizesWithoutChangingExportedSigns(){
        double s=Math.sqrt(0.5);double[] values={0,0,0,0, 0,0,0,1, 0,0,0,0, 0,0,0,0, 0,0,-s,-s, 0,0,0,0};
        var c=new Channel(0,Path.ROTATION,Interpolation.CUBICSPLINE,new double[]{0,1},values);double[] out=new double[4];c.sample(0.25,out,0);
        double z=-0.15625*s,w=0.84375-0.15625*s,n=Math.hypot(z,w);
        assertEquals(z/n,out[2],1e-12);assertEquals(w/n,out[3],1e-12);assertTrue(out[2]<0,"cubic is not shortest-path SLERP");
    }
    @Test void oneKeyAndZeroDurationAreValidForHold(){var c=new Channel(0,Path.TRANSLATION,Interpolation.LINEAR,new double[]{0},new double[]{2,3,4});double[] out=new double[3];c.sample(100,out,0);assertArrayEquals(new double[]{2,3,4},out);}
    @Test void channelsDefensivelyCopy(){double[] t={0,1},v={0,0,0,2,0,0};var c=new Channel(0,Path.TRANSLATION,Interpolation.LINEAR,t,v);t[1]=0;v[3]=100;double[] out=new double[3];c.sample(0.5,out,0);assertEquals(1,out[0]);}
    @TestFactory Stream<DynamicTest> malformedChannels(){
        return Stream.of(new double[]{0,0},new double[]{1,0},new double[]{-1,2},new double[]{0,Double.NaN},new double[]{0,Double.POSITIVE_INFINITY})
            .map(t->DynamicTest.dynamicTest(Arrays.toString(t),()->assertThrows(IllegalArgumentException.class,()->new Channel(0,Path.TRANSLATION,Interpolation.LINEAR,t,new double[6]))));
    }
    @Test void rejectsWrongOutputAndNonUnitKeys(){
        assertThrows(IllegalArgumentException.class,()->new Channel(0,Path.SCALE,Interpolation.CUBICSPLINE,new double[]{0,1},new double[6]));
        assertThrows(IllegalArgumentException.class,()->new Channel(0,Path.ROTATION,Interpolation.LINEAR,new double[]{0},new double[4]));
        assertThrows(IllegalArgumentException.class,()->new AnimationClip("bad",List.of(translation(0,1),translation(0,2))));
    }
    @Test void affineCompositionAndInverseRespectHierarchy(){
        double[] parent=bind(3);parent[7]=2;parent[8]=3;parent[9]=4;
        double[] m=new double[16],inverse=new double[16],identity=new double[16];RigMath.compose(parent,0,m,0);RigMath.inverseAffine(m,0,inverse,0);RigMath.multiply(m,0,inverse,0,identity,0);
        for(int i=0;i<16;i++)assertEquals(i%5==0?1:0,identity[i],1e-12);
        var model=new RigModel(List.of(new RigModel.Node("parent",-1,parent,null),new RigModel.Node("child",0,bind(1),null)),List.of(),List.of(),List.of());
        assertEquals(5,new RigFrame(model).global(1,12));
    }
    @Test void skinPaletteUsesMeshSpaceAndInverseTransposeNormals(){
        double[] meshBind=bind(10),boneBind=bind(3);boneBind[7]=2;boneBind[8]=3;boneBind[9]=4;
        double[] identity=new double[16];RigMath.identity(identity,0);
        double[] v={0,0,0,1,1,0,0,0, 1,0,0,1,1,0,0,0, 0,1,0,1,1,0,0,0};
        var mesh=new RigModel.Mesh(0,0,0,v,new int[]{0,1,2},new int[]{0,0,0,0,0,0,0,0,0,0,0,0},new double[]{1,0,0,0,1,0,0,0,1,0,0,0});
        var model=new RigModel(List.of(new RigModel.Node("mesh",-1,meshBind,null),new RigModel.Node("joint",-1,boneBind,null)),List.of(new RigModel.Skin(new int[]{1},identity)),List.of(mesh),List.of());
        var frame=new RigFrame(model);assertEquals(-7,frame.jointMatrix(0,0,12));assertEquals(3,frame.vertex(0,0,0));assertEquals(5,frame.vertex(0,1,0));
        double length=Math.hypot(0.5,1.0/3);assertEquals(0.5/length,frame.vertex(0,0,3),1e-12);assertEquals((1.0/3)/length,frame.vertex(0,0,4),1e-12);
    }
    @Test void invalidHierarchyAndSkinFailBeforeSampling(){
        assertThrows(IllegalArgumentException.class,()->new RigModel(List.of(new RigModel.Node("a",1,bind(0),null),new RigModel.Node("b",0,bind(0),null)),List.of(),List.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->new RigModel.Skin(new int[]{0,0},new double[32]));
        assertThrows(IllegalArgumentException.class,()->new RigModel.Mesh(0,0,0,new double[24],new int[]{0,1,2},new int[12],new double[12]));
    }
}
