package com.bobincraft.armament.client.animation.gltf;

import java.util.*;

/** Shared immutable rig/geometry. No Minecraft, JglTF objects or mutable pose are retained. */
public final class RigModel {
    public static final class Node {
        private final String name; private final int parent; private final double[] bind,matrix;
        public Node(String name,int parent,double[] bind,double[] matrix){
            this.name=Objects.requireNonNull(name);this.parent=parent;this.bind=bind.clone();this.matrix=matrix==null?null:matrix.clone();
            if(bind.length!=10||(matrix!=null&&matrix.length!=16))throw new IllegalArgumentException("node: invalid transform length");
            for(double v:bind)if(!Double.isFinite(v))throw new IllegalArgumentException("node: non-finite TRS");
            double norm=0;for(int i=3;i<7;i++)norm+=bind[i]*bind[i];
            if(Math.abs(norm-1)>1e-5)throw new IllegalArgumentException("node: invalid quaternion");
            for(int i=7;i<10;i++)if(Math.abs(bind[i])<1e-12)throw new IllegalArgumentException("node: zero bind scale has no invertible normal transform");
            if(matrix!=null)for(double v:matrix)if(!Double.isFinite(v))throw new IllegalArgumentException("node: non-finite matrix");
            if(matrix!=null&&(Math.abs(matrix[3])+Math.abs(matrix[7])+Math.abs(matrix[11])+Math.abs(matrix[15]-1)>1e-8||Math.abs(RigMath.determinant(matrix,0))<1e-12))throw new IllegalArgumentException("node: matrix must be invertible and affine");
        }
        public String name(){return name;} public int parent(){return parent;} public boolean fixedMatrix(){return matrix!=null;}
        void copyBind(double[] dest,int o){System.arraycopy(bind,0,dest,o,10);}
        void copyMatrix(double[] dest,int o){System.arraycopy(matrix,0,dest,o,16);}
    }
    public static final class Skin {
        private final int[] joints; private final double[] inverseBind;
        public Skin(int[] joints,double[] inverseBind){
            if(joints.length<1||joints.length>128||inverseBind.length!=joints.length*16)throw new IllegalArgumentException("skin: invalid joint count");
            this.joints=joints.clone();this.inverseBind=inverseBind.clone();
            Set<Integer> unique=new HashSet<>();for(int j:joints)if(j<0||!unique.add(j))throw new IllegalArgumentException("skin: invalid/duplicate joint");
            for(double v:inverseBind)if(!Double.isFinite(v))throw new IllegalArgumentException("skin: non-finite inverse bind matrix");
        }
        public int size(){return joints.length;} public int joint(int i){return joints[i];}
        void copyInverseBind(int i,double[] dest){System.arraycopy(inverseBind,i*16,dest,0,16);}
    }
    public static final class Mesh {
        private final int node,skin,material;private final double[] vertices,weights;private final int[] indices,joints;
        public Mesh(int node,int skin,int material,double[] vertices,int[] indices,int[] joints,double[] weights){
            if(node<0||skin< -1||material<0||vertices.length==0||vertices.length%8!=0||indices.length==0||indices.length%3!=0)
                throw new IllegalArgumentException("mesh: invalid geometry");
            this.node=node;this.skin=skin;this.material=material;this.vertices=vertices.clone();this.indices=indices.clone();
            this.joints=joints==null?null:joints.clone();this.weights=weights==null?null:weights.clone();
            for(double v:vertices)if(!Double.isFinite(v)||Math.abs(v)>1e6)throw new IllegalArgumentException("mesh: invalid vertex");
            for(int i:indices)if(i<0||i>=vertices.length/8)throw new IllegalArgumentException("mesh: invalid index");
            if(skin>=0){
                if(joints==null||weights==null||joints.length!=vertices.length/2||weights.length!=joints.length)throw new IllegalArgumentException("mesh: four influences required");
                for(int v=0;v<vertices.length/8;v++){
                    double sum=0;for(int k=0;k<4;k++){int p=v*4+k;if(joints[p]<0||!Double.isFinite(weights[p])||weights[p]<0||weights[p]>1)throw new IllegalArgumentException("mesh: invalid influence");sum+=weights[p];}
                    if(Math.abs(sum-1)>1e-3)throw new IllegalArgumentException("mesh: weights must sum to one");
                    for(int k=0;k<4;k++)this.weights[v*4+k]/=sum;
                }
            }else if(joints!=null||weights!=null)throw new IllegalArgumentException("mesh: influences without a skin");
        }
        public int node(){return node;}public int skin(){return skin;}public int material(){return material;}
        public int vertexCount(){return vertices.length/8;}public int indexCount(){return indices.length;}public int index(int i){return indices[i];}
        public double vertex(int v,int component){return vertices[v*8+component];}
        int joint(int v,int k){return joints[v*4+k];}double weight(int v,int k){return weights[v*4+k];}
    }
    private final List<Node> nodes;private final List<Skin> skins;private final List<Mesh> meshes;private final List<AnimationClip> clips;
    private final int[] order;private final double[] bind;
    public RigModel(List<Node> nodes,List<Skin> skins,List<Mesh> meshes,List<AnimationClip> clips){
        this.nodes=List.copyOf(nodes);this.skins=List.copyOf(skins);this.meshes=List.copyOf(meshes);this.clips=List.copyOf(clips);
        if(nodes.isEmpty()||nodes.size()>256||skins.size()>32||clips.size()>128||meshes.size()>256)throw new IllegalArgumentException("rig: resource limits exceeded");
        bind=new double[nodes.size()*10];order=new int[nodes.size()];int[] state=new int[nodes.size()],cursor={0};
        for(int i=0;i<nodes.size();i++){nodes.get(i).copyBind(bind,i*10);visit(i,state,cursor,0);}
        for(Skin s:skins)for(int j=0;j<s.size();j++)if(s.joint(j)>=nodes.size())throw new IllegalArgumentException("skin: unknown joint node");
        int vertices=0,skinned=0,indexCount=0;
        for(Mesh m:meshes){
            if(m.node()>=nodes.size()||m.skin()>=skins.size())throw new IllegalArgumentException("mesh: unknown node/skin");
            vertices+=m.vertexCount();indexCount+=m.indexCount();
            if(m.skin()>=0){skinned+=m.vertexCount();for(int v=0;v<m.vertexCount();v++)for(int k=0;k<4;k++)if(m.joint(v,k)>=skins.get(m.skin()).size())throw new IllegalArgumentException("mesh: joint index outside skin palette");}
        }
        if(vertices>200_000||skinned>50_000||indexCount>1_500_000)throw new IllegalArgumentException("rig: CPU budget exceeded (200k vertices, 50k skinned, 1.5M indices)");
        Set<String> names=new HashSet<>();
        for(AnimationClip clip:clips){if(!names.add(clip.name()))throw new IllegalArgumentException("rig: duplicate clip name");
            for(var c:clip.channels())if(c.node()>=nodes.size()||nodes.get(c.node()).fixedMatrix())throw new IllegalArgumentException("clip: unknown node or animation targeting a matrix node");}
    }
    private void visit(int i,int[] state,int[] cursor,int depth){
        if(state[i]==2)return;if(state[i]==1||depth>64)throw new IllegalArgumentException("rig: cycle/depth limit");state[i]=1;
        int parent=nodes.get(i).parent();if(parent>=nodes.size()||parent< -1)throw new IllegalArgumentException("rig: unknown parent");
        if(parent>=0)visit(parent,state,cursor,depth+1);state[i]=2;order[cursor[0]++]=i;
    }
    public List<Node> nodes(){return nodes;}public List<Skin> skins(){return skins;}public List<Mesh> meshes(){return meshes;}public List<AnimationClip> clips(){return clips;}
    public AnimationClip clip(String name){for(var clip:clips)if(clip.name().equals(name))return clip;throw new IllegalArgumentException("unknown GLB clip: "+name);}
    public int node(String name){int found=-1;for(int i=0;i<nodes.size();i++)if(nodes.get(i).name().equals(name)){if(found>=0)throw new IllegalArgumentException("ambiguous node name: "+name);found=i;}if(found<0)throw new IllegalArgumentException("unknown node: "+name);return found;}
    public void copyBind(double[] dest){if(dest.length!=bind.length)throw new IllegalArgumentException("pose size");System.arraycopy(bind,0,dest,0,bind.length);}
    int orderedNode(int i){return order[i];} double bind(int i){return bind[i];}
}
