package com.bobincraft.armament.client.thirdperson;

/** Current vanilla lower-body support in model metres and normalized stride phase.
 * Read from leg pivots/limbAnimator, never camera position or FPS transforms.
 */
public record ThirdPersonBodyFrame(double pelvisX,double pelvisY,double pelvisZ,double stridePhase) {
    public ThirdPersonBodyFrame {
        if(!Double.isFinite(pelvisX)||!Double.isFinite(pelvisY)||!Double.isFinite(pelvisZ)||!Double.isFinite(stridePhase))throw new IllegalArgumentException("invalid lower-body frame");
    }
    public static ThirdPersonBodyFrame standing(double phase){return new ThirdPersonBodyFrame(0,.75,0,phase);}
    public static ThirdPersonBodyFrame crouching(double phase){return new ThirdPersonBodyFrame(0,12.2/16,4.0/16,phase);}
    public double[] gltfPelvis(){return new double[]{-pelvisX,-pelvisY,pelvisZ};}
}
