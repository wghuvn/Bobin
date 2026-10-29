package com.bobincraft.armament;

import com.bobincraft.armament.client.animation.gltf.RigMath;
import com.bobincraft.armament.client.presentation.GripFrameConvention;
import org.junit.jupiter.api.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

final class GripFrameConventionTest {
    private static double[] identity(){double[] m=new double[16];RigMath.identity(m,0);return m;}

    @Test void identityAxesGiveOpposedPalmNormalAndAnatomicallyMirroredThumbs(){
        double[] frame=identity(),primary=new double[3],normal=new double[3],rightThumb=new double[3],leftThumb=new double[3];
        GripFrameConvention.primary(frame,primary);GripFrameConvention.palmNormal(frame,normal);
        GripFrameConvention.thumbDirection(frame,GripFrameConvention.Side.RIGHT,rightThumb);GripFrameConvention.thumbDirection(frame,GripFrameConvention.Side.LEFT,leftThumb);
        assertArrayEquals(new double[]{0,1,0},primary,0);assertArrayEquals(new double[]{0,0,-1},normal,0);
        assertArrayEquals(new double[]{-1,0,0},rightThumb,0);assertArrayEquals(new double[]{1,0,0},leftThumb,0);
        assertEquals(-1,normal[2],0,"palm points against the outward +Z contact-surface normal");
        assertEquals(1,RigMath.determinant(frame,0),0,"mirroring thumb semantics never reflects a grip frame");
    }

    @Test void rigidRotationCarriesAllSemanticAxesAndTranslationDoesNotAffectDirections(){
        double[] frame=new double[16],trs={4,-3,.8,0,0,Math.sin(Math.PI/4),Math.cos(Math.PI/4),1,1,1};RigMath.compose(trs,0,frame,0);
        double[] before=frame.clone(),primary=new double[3],normal=new double[3],thumb=new double[3];
        GripFrameConvention.validate(frame,"primary grip");GripFrameConvention.primary(frame,primary);GripFrameConvention.palmNormal(frame,normal);GripFrameConvention.thumbDirection(frame,GripFrameConvention.Side.RIGHT,thumb);
        assertArrayEquals(new double[]{-1,0,0},primary,1e-12);assertArrayEquals(new double[]{0,0,-1},normal,1e-12);assertArrayEquals(new double[]{0,-1,0},thumb,1e-12);
        assertArrayEquals(before,frame,0,"checking/reading a frame must not silently fix it");
    }

    @Test void blenderToGltfConversionPreservesHandednessAndSemanticDirections(){
        double[] c={1,0,0,0,0,0,-1,0,0,1,0,0,0,0,0,1};
        GripFrameConvention.validate(c,"Blender to glTF");double[] primary=new double[3],palm=new double[3];
        GripFrameConvention.primary(c,primary);GripFrameConvention.palmNormal(c,palm);
        assertArrayEquals(new double[]{0,0,-1},primary,0);assertArrayEquals(new double[]{0,-1,0},palm,0);assertEquals(1,RigMath.determinant(c,0),0);
    }

    @Test void reflectionIsRejectedEvenWithUnitOrthogonalAxes(){
        double[] reflected=identity();reflected[0]=-1;
        var error=assertThrows(IllegalArgumentException.class,()->GripFrameConvention.validate(reflected,"left support"));
        assertTrue(error.getMessage().contains("left support"));assertTrue(error.getMessage().contains("right-handed"));
    }

    @Test void scaleAndShearCannotMasqueradeAsGripRotation(){
        for(double scale:new double[]{0,.9,1.001,2}){
            double[] frame=identity();frame[0]=scale;assertThrows(IllegalArgumentException.class,()->GripFrameConvention.validate(frame,"scaled grip"));
        }
        double[] shear=identity();shear[4]=.2;assertThrows(IllegalArgumentException.class,()->GripFrameConvention.validate(shear,"sheared grip"));
        // Equal column lengths alone do not establish orthogonality.
        double[] oblique=identity();oblique[4]=Math.sin(.01);oblique[5]=Math.cos(.01);
        var error=assertThrows(IllegalArgumentException.class,()->GripFrameConvention.validate(oblique,"oblique grip"));assertTrue(error.getMessage().contains("orthogonal"));
    }

    @Test void ordinaryFloatExportNoiseIsAllowedWithoutNormalizingTheInput(){
        double[] frame=identity();frame[0]=.99999994;frame[5]=1.00000012;frame[10]=.99999994;double[] before=frame.clone();
        assertDoesNotThrow(()->GripFrameConvention.validate(frame,"float GLB"));assertArrayEquals(before,frame,0);
    }

    @TestFactory Stream<DynamicTest> allMatrixValuesMustBeFinite(){
        return java.util.stream.IntStream.range(0,16).boxed().flatMap(index->Stream.of(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY).map(value->DynamicTest.dynamicTest("component "+index+"="+value,()->{
            double[] frame=identity();frame[index]=value;assertThrows(IllegalArgumentException.class,()->GripFrameConvention.validate(frame,"corrupt grip"));
        })));
    }

    @Test void perspectiveAndMalformedMatricesAreNotGripFrames(){
        for(int index:new int[]{3,7,11,15}){
            double[] frame=identity();frame[index]=.1;assertThrows(IllegalArgumentException.class,()->GripFrameConvention.validate(frame,"non-affine"));
        }
        assertThrows(IllegalArgumentException.class,()->GripFrameConvention.validate(null,"absent grip"));
        assertThrows(IllegalArgumentException.class,()->GripFrameConvention.validate(new double[15],"short grip"));
        assertThrows(IllegalArgumentException.class,()->GripFrameConvention.primary(identity(),new double[4]));
        assertThrows(IllegalArgumentException.class,()->GripFrameConvention.palmNormal(identity(),null));
        assertThrows(IllegalArgumentException.class,()->GripFrameConvention.thumbDirection(identity(),null,new double[3]));
    }
}
