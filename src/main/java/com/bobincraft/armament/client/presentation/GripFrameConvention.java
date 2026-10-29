package com.bobincraft.armament.client.presentation;

/**
 * Surface grip frame, in the common glTF model space (column-major metres).
 * +Y is the primary tangent along the held surface; -Z is the palm normal
 * pointing into that surface. X = Y cross Z, so X cross Y = Z and det = +1.
 * The anatomical thumb is on -X for the right hand and +X for the left hand.
 * These are semantic grip axes: a wrist-bone frame may need an authored local
 * Hand Target offset. Never reflect a frame to mirror a hand.
 */
public final class GripFrameConvention {
    private static final double AXIS_TOLERANCE=1e-4;
    private static final double AFFINE_TOLERANCE=1e-6;

    public enum Side { RIGHT(-1),LEFT(1);
        private final int thumbSign;
        Side(int thumbSign){this.thumbSign=thumbSign;}
    }

    private GripFrameConvention() {}

    /** Rejects non-rigid frames instead of hiding scale, shear or reflection by normalization. */
    public static void validate(double[] frame,String label){
        if(frame==null||frame.length!=16)throw error(label,"expected a 16-value column-major matrix");
        for(double value:frame)if(!Double.isFinite(value))throw error(label,"non-finite frame value");
        if(Math.abs(frame[3])>AFFINE_TOLERANCE||Math.abs(frame[7])>AFFINE_TOLERANCE||Math.abs(frame[11])>AFFINE_TOLERANCE||Math.abs(frame[15]-1)>AFFINE_TOLERANCE)
            throw error(label,"expected affine last row [0,0,0,1]");
        for(int a=0;a<3;a++){
            double lengthSquared=dot(frame,a,frame,a);
            if(Math.abs(lengthSquared-1)>AXIS_TOLERANCE)throw error(label,"axis "+a+" must have unit length; scale/degenerate axes are not grip orientations");
            for(int b=a+1;b<3;b++)if(Math.abs(dot(frame,a,frame,b))>AXIS_TOLERANCE)
                throw error(label,"axes "+a+" and "+b+" are not orthogonal");
        }
        double determinant=frame[0]*(frame[5]*frame[10]-frame[9]*frame[6])-frame[4]*(frame[1]*frame[10]-frame[9]*frame[2])+frame[8]*(frame[1]*frame[6]-frame[5]*frame[2]);
        if(Math.abs(determinant-1)>3*AXIS_TOLERANCE)
            throw error(label,"expected right-handed frame with determinant +1; reflections are not hand mirroring");
    }

    public static void primary(double[] frame,double[] xyz){axis(frame,1,1,xyz);}
    public static void palmNormal(double[] frame,double[] xyz){axis(frame,2,-1,xyz);}
    public static void thumbDirection(double[] frame,Side side,double[] xyz){
        if(side==null)throw error("grip","hand side is required");
        axis(frame,0,side.thumbSign,xyz);
    }

    private static void axis(double[] frame,int column,int sign,double[] xyz){
        validate(frame,"grip");
        if(xyz==null||xyz.length!=3)throw error("grip","expected a three-value direction output");
        for(int k=0;k<3;k++)xyz[k]=sign*frame[column*4+k];
    }
    private static double dot(double[] a,int ac,double[] b,int bc){return a[ac*4]*b[bc*4]+a[ac*4+1]*b[bc*4+1]+a[ac*4+2]*b[bc*4+2];}
    private static IllegalArgumentException error(String label,String message){return new IllegalArgumentException((label==null?"grip":label)+": "+message);}
}
