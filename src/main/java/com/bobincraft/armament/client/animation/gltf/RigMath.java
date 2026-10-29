package com.bobincraft.armament.client.animation.gltf;

/** Column-major, double precision math. Caller owns all output/scratch storage. */
public final class RigMath {
    private RigMath() {}
    public static void identity(double[] m, int o) {
        java.util.Arrays.fill(m, o, o + 16, 0); m[o]=m[o+5]=m[o+10]=m[o+15]=1;
    }
    public static void normalizeQuaternion(double[] q, int o) {
        double n=Math.sqrt(q[o]*q[o]+q[o+1]*q[o+1]+q[o+2]*q[o+2]+q[o+3]*q[o+3]);
        if (!Double.isFinite(n) || n<1e-12) throw new IllegalArgumentException("rotation: zero/non-finite quaternion");
        for(int i=0;i<4;i++) q[o+i]/=n;
    }
    public static void slerp(double[] a,int ao,double[] b,int bo,double t,double[] out,int o) {
        double ax=a[ao],ay=a[ao+1],az=a[ao+2],aw=a[ao+3];
        double bx=b[bo],by=b[bo+1],bz=b[bo+2],bw=b[bo+3];
        double dot=ax*bx+ay*by+az*bz+aw*bw;
        if(dot<0){dot=-dot;bx=-bx;by=-by;bz=-bz;bw=-bw;}
        double x=1-t,y=t;
        if(dot<0.9995){double angle=Math.acos(Math.min(1,dot)),sin=Math.sin(angle);x=Math.sin((1-t)*angle)/sin;y=Math.sin(t*angle)/sin;}
        out[o]=x*ax+y*bx;out[o+1]=x*ay+y*by;out[o+2]=x*az+y*bz;out[o+3]=x*aw+y*bw;
        normalizeQuaternion(out,o);
    }
    public static void quaternionMultiply(double[] a,int ao,double[] b,int bo,double[] out,int o) {
        double x=a[ao],y=a[ao+1],z=a[ao+2],w=a[ao+3],X=b[bo],Y=b[bo+1],Z=b[bo+2],W=b[bo+3];
        out[o]=w*X+x*W+y*Z-z*Y;out[o+1]=w*Y-x*Z+y*W+z*X;
        out[o+2]=w*Z+x*Y-y*X+z*W;out[o+3]=w*W-x*X-y*Y-z*Z;
    }
    /** TRS layout: translation xyz, quaternion xyzw, scale xyz. */
    public static void compose(double[] t,int o,double[] m,int p) {
        double x=t[o+3],y=t[o+4],z=t[o+5],w=t[o+6],sx=t[o+7],sy=t[o+8],sz=t[o+9];
        m[p]=(1-2*(y*y+z*z))*sx;m[p+1]=2*(x*y+z*w)*sx;m[p+2]=2*(x*z-y*w)*sx;m[p+3]=0;
        m[p+4]=2*(x*y-z*w)*sy;m[p+5]=(1-2*(x*x+z*z))*sy;m[p+6]=2*(y*z+x*w)*sy;m[p+7]=0;
        m[p+8]=2*(x*z+y*w)*sz;m[p+9]=2*(y*z-x*w)*sz;m[p+10]=(1-2*(x*x+y*y))*sz;m[p+11]=0;
        m[p+12]=t[o];m[p+13]=t[o+1];m[p+14]=t[o+2];m[p+15]=1;
    }
    /** Output must not overlap either input matrix. */
    public static void multiply(double[] a,int ao,double[] b,int bo,double[] out,int o) {
        for(int c=0;c<4;c++)for(int r=0;r<4;r++){
            double v=0;for(int k=0;k<4;k++)v+=a[ao+k*4+r]*b[bo+c*4+k];out[o+c*4+r]=v;
        }
    }
    public static double determinant(double[] m,int o) {
        return m[o]*(m[o+5]*m[o+10]-m[o+9]*m[o+6])-m[o+4]*(m[o+1]*m[o+10]-m[o+9]*m[o+2])+m[o+8]*(m[o+1]*m[o+6]-m[o+5]*m[o+2]);
    }
    public static void inverseAffine(double[] m,int o,double[] out,int p) {
        double a=m[o],b=m[o+4],c=m[o+8],d=m[o+1],e=m[o+5],f=m[o+9],g=m[o+2],h=m[o+6],i=m[o+10];
        double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if(!Double.isFinite(det)||Math.abs(det)<1e-12)throw new IllegalArgumentException("pose: singular affine transform");
        identity(out,p);
        out[p]=(e*i-f*h)/det;out[p+4]=(c*h-b*i)/det;out[p+8]=(b*f-c*e)/det;
        out[p+1]=(f*g-d*i)/det;out[p+5]=(a*i-c*g)/det;out[p+9]=(c*d-a*f)/det;
        out[p+2]=(d*h-e*g)/det;out[p+6]=(b*g-a*h)/det;out[p+10]=(a*e-b*d)/det;
        for(int r=0;r<3;r++)out[p+12+r]=-(out[p+r]*m[o+12]+out[p+4+r]*m[o+13]+out[p+8+r]*m[o+14]);
    }
    public static void mixTrs(double[] a,int ao,double[] b,int bo,double t,double[] out,int o) {
        for(int k=0;k<10;k++)if(k<3||k>6)out[o+k]=a[ao+k]+(b[bo+k]-a[ao+k])*t;
        slerp(a,ao+3,b,bo+3,t,out,o+3);
    }
    public static void normal(double[] m,int o,double x,double y,double z,double[] out,int p) {
        double d=determinant(m,o);
        if(!Double.isFinite(d)||Math.abs(d)<1e-12)throw new IllegalArgumentException("skinning: singular normal transform");
        double nx=((m[o+5]*m[o+10]-m[o+9]*m[o+6])*x+(m[o+9]*m[o+2]-m[o+1]*m[o+10])*y+(m[o+1]*m[o+6]-m[o+5]*m[o+2])*z)/d;
        double ny=((m[o+8]*m[o+6]-m[o+4]*m[o+10])*x+(m[o]*m[o+10]-m[o+8]*m[o+2])*y+(m[o+4]*m[o+2]-m[o]*m[o+6])*z)/d;
        double nz=((m[o+4]*m[o+9]-m[o+8]*m[o+5])*x+(m[o+8]*m[o+1]-m[o]*m[o+9])*y+(m[o]*m[o+5]-m[o+4]*m[o+1])*z)/d;
        double n=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(!Double.isFinite(n)||n<1e-12)throw new IllegalArgumentException("skinning: invalid normal");
        out[p]=nx/n;out[p+1]=ny/n;out[p+2]=nz/n;
    }
}
