package com.bobincraft.armament.client.animation.gltf;

import java.util.Arrays;

/** Per-instance reusable pose/CPU-skinned geometry. Read-only until the owner's next sample. */
public final class RigFrame {
    final RigModel model;
    final double[] localTrs;
    private final double[] local,world,inverseMesh=new double[16],ibm=new double[16],blend=new double[16],normal=new double[3];
    private final double[][] vertices,jointLocal,jointWorld;
    private final boolean[] reflected;
    private long evaluations;
    public RigFrame(RigModel model){
        this.model=model;localTrs=new double[model.nodes().size()*10];local=new double[model.nodes().size()*16];world=new double[local.length];
        int size=model.meshes().size();vertices=new double[size][];jointLocal=new double[size][];jointWorld=new double[size][];reflected=new boolean[size];
        for(int i=0;i<size;i++){
            var mesh=model.meshes().get(i);vertices[i]=new double[mesh.vertexCount()*8];
            int joints=mesh.skin()<0?0:model.skins().get(mesh.skin()).size();jointLocal[i]=new double[joints*16];jointWorld[i]=new double[joints*16];
        }
        model.copyBind(localTrs);update();
    }
    public RigModel model(){return model;}public long evaluations(){return evaluations;}
    public double local(int node,int component){return localTrs[node*10+component];}
    public double global(int node,int component){return world[node*16+component];}
    public double vertex(int mesh,int vertex,int component){return vertices[mesh][vertex*8+component];}
    public boolean reflected(int mesh){return reflected[mesh];}
    /** inverse(meshWorld) * jointWorld * inverseBindMatrix, in the mesh node's space. */
    public double jointMatrix(int mesh,int joint,int component){return jointLocal[mesh][joint*16+component];}
    /** jointWorld * inverseBindMatrix, acting on the exported POSITION coordinate space. */
    public double jointWorldMatrix(int mesh,int joint,int component){return jointWorld[mesh][joint*16+component];}
    public void socket(int node,double[] localOffset,double[] destination){
        if(node<0||node>=model.nodes().size()||localOffset.length!=16||destination.length!=16||localOffset==destination)throw new IllegalArgumentException("socket: invalid node/output");
        RigMath.multiply(world,node*16,localOffset,0,destination,0);
    }
    void update(){
        for(int i=0;i<model.nodes().size();i++){
            int n=model.orderedNode(i),o=n*16;var node=model.nodes().get(n);
            if(node.fixedMatrix())node.copyMatrix(local,o);else RigMath.compose(localTrs,n*10,local,o);
            if(node.parent()<0)System.arraycopy(local,o,world,o,16);
            else RigMath.multiply(world,node.parent()*16,local,o,world,o);
            for(int c=0;c<16;c++)if(!Double.isFinite(world[o+c])||Math.abs(world[o+c])>1e6)throw new IllegalArgumentException("pose: non-finite/excessive global transform");
        }
        for(int m=0;m<model.meshes().size();m++){
            var mesh=model.meshes().get(m);int node=mesh.node()*16;
            if(mesh.skin()>=0){
                var skin=model.skins().get(mesh.skin());RigMath.inverseAffine(world,node,inverseMesh,0);
                for(int j=0;j<skin.size();j++){
                    skin.copyInverseBind(j,ibm);
                    RigMath.multiply(world,skin.joint(j)*16,ibm,0,jointWorld[m],j*16);
                    RigMath.multiply(inverseMesh,0,jointWorld[m],j*16,jointLocal[m],j*16);
                }
            }
            reflected[m]=RigMath.determinant(world,node)<0;
            for(int v=0;v<mesh.vertexCount();v++){
                double[] transform=world;int o=node;
                if(mesh.skin()>=0){
                    Arrays.fill(blend,0);
                    for(int k=0;k<4;k++){
                        double weight=mesh.weight(v,k);int joint=mesh.joint(v,k)*16;
                        for(int c=0;c<16;c++)blend[c]+=weight*jointWorld[m][joint+c];
                    }
                    transform=blend;o=0;
                    // Front-face reversal follows the mesh node transform. Individual joint
                    // deformation must not arbitrarily flip an entire mesh using vertex 0.
                }
                double x=mesh.vertex(v,0),y=mesh.vertex(v,1),z=mesh.vertex(v,2);int p=v*8;
                double[] output=vertices[m];
                output[p]=transform[o]*x+transform[o+4]*y+transform[o+8]*z+transform[o+12];
                output[p+1]=transform[o+1]*x+transform[o+5]*y+transform[o+9]*z+transform[o+13];
                output[p+2]=transform[o+2]*x+transform[o+6]*y+transform[o+10]*z+transform[o+14];
                RigMath.normal(transform,o,mesh.vertex(v,3),mesh.vertex(v,4),mesh.vertex(v,5),normal,0);
                System.arraycopy(normal,0,output,p+3,3);output[p+6]=mesh.vertex(v,6);output[p+7]=mesh.vertex(v,7);
                for(int c=0;c<8;c++)if(!Double.isFinite(output[p+c])||Math.abs(output[p+c])>1e6)throw new IllegalArgumentException("skinning: invalid output vertex");
            }
        }
        evaluations++;
    }
}
