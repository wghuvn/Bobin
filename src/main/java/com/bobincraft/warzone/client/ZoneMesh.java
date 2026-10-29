package com.bobincraft.warzone.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

/** Small mesh primitives with continuous coordinates; shared by cables and the sliding cover. */
final class ZoneMesh {
    private ZoneMesh() { }
    static void vertex(VertexConsumer out,MatrixStack.Entry m,double x,double y,double z,float u,float v,Vector3f normal,int light){
        out.vertex(m.getPositionMatrix(),(float)x,(float)y,(float)z).color(255,255,255,255).texture(u,v)
            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.getNormalMatrix(),normal.x,normal.y,normal.z).next();
    }
    static void tube(VertexConsumer out,MatrixStack.Entry m,Vector3f a,Vector3f b,float radius,int light){
        Vector3f axis=new Vector3f(b).sub(a);if(axis.lengthSquared()<.000001)return;axis.normalize();
        Vector3f u=new Vector3f(axis).cross(Math.abs(axis.y)>.95f?new Vector3f(1,0,0):new Vector3f(0,1,0)).normalize();
        Vector3f v=new Vector3f(axis).cross(u).normalize();
        for(int i=0;i<6;i++){
            double angle=i*Math.PI/3,next=(i+1)*Math.PI/3;
            Vector3f n1=new Vector3f(u).mul((float)Math.cos(angle)).fma((float)Math.sin(angle),v),n2=new Vector3f(u).mul((float)Math.cos(next)).fma((float)Math.sin(next),v);
            Vector3f p=new Vector3f(a).fma(radius,n1),q=new Vector3f(b).fma(radius,n1),r=new Vector3f(b).fma(radius,n2),s=new Vector3f(a).fma(radius,n2);
            vertex(out,m,p.x,p.y,p.z,0,0,n1,light);vertex(out,m,q.x,q.y,q.z,0,1,n1,light);vertex(out,m,r.x,r.y,r.z,1,1,n2,light);vertex(out,m,s.x,s.y,s.z,1,0,n2,light);
        }
    }
    static void lid(VertexConsumer out,MatrixStack.Entry m,int light){
        double radius=.49,top=.998,bottom=.916;Vector3f up=new Vector3f(0,1,0),down=new Vector3f(0,-1,0);
        for(int i=0;i<32;i++){
            double a=i*Math.PI/16,b=(i+1)*Math.PI/16,x1=.5+radius*Math.cos(a),z1=.5+radius*Math.sin(a),x2=.5+radius*Math.cos(b),z2=.5+radius*Math.sin(b);
            vertex(out,m,.5,top,.5,.5f,.5f,up,light);vertex(out,m,x2,top,z2,(float)x2,(float)z2,up,light);vertex(out,m,x1,top,z1,(float)x1,(float)z1,up,light);vertex(out,m,.5,top,.5,.5f,.5f,up,light);
            vertex(out,m,.5,bottom,.5,.5f,.5f,down,light);vertex(out,m,x1,bottom,z1,(float)x1,(float)z1,down,light);vertex(out,m,x2,bottom,z2,(float)x2,(float)z2,down,light);vertex(out,m,.5,bottom,.5,.5f,.5f,down,light);
            Vector3f n=new Vector3f((float)Math.cos(a),0,(float)Math.sin(a));
            vertex(out,m,x1,bottom,z1,0,0,n,light);vertex(out,m,x1,top,z1,0,.1f,n,light);vertex(out,m,x2,top,z2,1,.1f,n,light);vertex(out,m,x2,bottom,z2,1,0,n,light);
        }
    }
}
