package com.bobincraft.vehicle.client;
import com.bobincraft.vehicle.ArmorShellEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
/** Three-dimensional, longitudinal shell; the pointed nose follows the velocity vector. */
public final class ArmorShellRenderer extends EntityRenderer<ArmorShellEntity> {
    private static final Identifier STEEL=new Identifier("bobin_craft","textures/block/military_steel.png");
    public ArmorShellRenderer(EntityRendererFactory.Context c){super(c);}
    @Override public Identifier getTexture(ArmorShellEntity e){return STEEL;}
    @Override public void render(ArmorShellEntity e,float yaw,float delta,MatrixStack m,VertexConsumerProvider out,int light){
        m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerpAngleDegrees(delta,e.prevYaw,e.getYaw())));m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(delta,e.prevPitch,e.getPitch())));
        VertexConsumer v=out.getBuffer(RenderLayer.getEntitySolid(STEEL));
        // Octagonal case and tapered cap, oriented along local +Z.
        for(int i=0;i<8;i++){double a=i*Math.PI/4,b=(i+1)*Math.PI/4;float x=(float)Math.cos(a)*.065f,y=(float)Math.sin(a)*.065f,X=(float)Math.cos(b)*.065f,Y=(float)Math.sin(b)*.065f;
            face(v,m,new float[][]{{x,y,-.30f},{X,Y,-.30f},{X,Y,.12f},{x,y,.12f}},light);
            face(v,m,new float[][]{{x,y,.12f},{X,Y,.12f},{0,0,.31f},{0,0,.31f}},light);
            face(v,m,new float[][]{{0,0,-.30f},{X,Y,-.30f},{x,y,-.30f},{x,y,-.30f}},light);
        }m.pop();
    }
    private static void face(VertexConsumer v,MatrixStack m,float[][] p,int light){var a=new org.joml.Vector3f(p[1][0]-p[0][0],p[1][1]-p[0][1],p[1][2]-p[0][2]);var b=new org.joml.Vector3f(p[2][0]-p[0][0],p[2][1]-p[0][1],p[2][2]-p[0][2]);var n=a.cross(b);if(n.lengthSquared()<.000001)n.set(0,0,-1);else n.normalize();for(int i=0;i<4;i++)v.vertex(m.peek().getPositionMatrix(),p[i][0],p[i][1],p[i][2]).color(255,255,255,255).texture((i==1||i==2)?1:0,i>=2?1:0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.peek().getNormalMatrix(),n.x,n.y,n.z).next();}
}
