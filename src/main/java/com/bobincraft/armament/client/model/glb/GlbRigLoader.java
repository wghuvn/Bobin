package com.bobincraft.armament.client.model.glb;

import com.bobincraft.armament.client.animation.gltf.*;
import com.google.gson.JsonObject;
import de.javagl.jgltf.model.*;
import java.io.IOException;
import java.util.*;
import static com.bobincraft.armament.client.model.glb.GlbPreflight.check;
import static com.bobincraft.armament.client.model.glb.JgltfModelLoader.*;
import static com.bobincraft.armament.client.model.glb.GlbModelAsset.*;

/** JglTF adapter only. Resource ownership and runtime sampling stay outside this importer. */
final class GlbRigLoader {
    private GlbRigLoader(){}
    static RigModel load(GltfModel model,JsonObject json,int scene,List<Material> materials)throws IOException{
        List<NodeModel> source=model.getNodeModels();List<RigModel.Node> nodes=new ArrayList<>();
        for(int i=0;i<source.size();i++){
            NodeModel n=source.get(i);double[] trs={0,0,0,0,0,0,1,1,1,1};
            if(n.getTranslation()!=null)System.arraycopy(n.getTranslation(),0,trs,0,3);
            if(n.getRotation()!=null)System.arraycopy(n.getRotation(),0,trs,3,4);
            if(n.getScale()!=null)System.arraycopy(n.getScale(),0,trs,7,3);
            StaticTransforms.validate(n.computeLocalTransform(null));
            nodes.add(new RigModel.Node(n.getName()==null?"node_"+i:n.getName(),n.getParent()==null?-1:source.indexOf(n.getParent()),trs,n.getMatrix()));
        }
        Set<NodeModel> reachable=Collections.newSetFromMap(new IdentityHashMap<>());
        for(NodeModel n:model.getSceneModels().get(scene).getNodeModels())reachable(n,reachable);
        List<RigModel.Skin> skins=new ArrayList<>();
        for(SkinModel skin:model.getSkinModels()){
            int count=skin.getJoints().size();int[] joints=new int[count];double[] inverse=new double[count*16];
            JsonObject skinJson=json.getAsJsonArray("skins").get(skins.size()).getAsJsonObject();
            NodeModel skeleton=skinJson.has("skeleton")?source.get(skinJson.get("skeleton").getAsInt()):skin.getSkeleton();
            AccessorModel accessor=skin.getInverseBindMatrices();
            if(accessor!=null)check(accessor.getElementType()==ElementType.MAT4&&accessor.getComponentType()==5126&&!accessor.isNormalized()&&accessor.getCount()>=count,"$.skins.inverseBindMatrices","expected FLOAT MAT4, at least one matrix per joint");
            for(int j=0;j<count;j++){
                joints[j]=source.indexOf(skin.getJoints().get(j));
                check(joints[j]>=0&&reachable.contains(skin.getJoints().get(j)),"$.skins.joints","joint must belong to selected scene");
                if(skeleton!=null){NodeModel ancestor=skin.getJoints().get(j);while(ancestor!=null&&ancestor!=skeleton)ancestor=ancestor.getParent();
                    check(ancestor!=null,"$.skins.skeleton","skeleton must be a common ancestor of all joints");}
                if(accessor==null)RigMath.identity(inverse,j*16);
                else {AccessorFloatData data=(AccessorFloatData)accessor.getAccessorData();for(int k=0;k<16;k++)inverse[j*16+k]=data.get(j,k);}
                double[] matrix=Arrays.copyOfRange(inverse,j*16,j*16+16);StaticTransforms.validate(matrix);
            }
            skins.add(new RigModel.Skin(joints,inverse));
        }
        List<RigModel.Mesh> meshes=new ArrayList<>();int vertexTotal=0,skinTotal=0;
        for(int n=0;n<source.size();n++)if(reachable.contains(source.get(n))){
            NodeModel node=source.get(n);int skin=node.getSkinModel()==null?-1:model.getSkinModels().indexOf(node.getSkinModel());
            for(MeshModel mesh:node.getMeshModels())for(MeshPrimitiveModel primitive:mesh.getMeshPrimitiveModels()){
                check(primitive.getMode()==4,"$.meshes.primitives.mode","only TRIANGLES supported");
                var attrs=primitive.getAttributes();
                check(attrs.keySet().stream().allMatch(Set.of("POSITION","NORMAL","TEXCOORD_0","JOINTS_0","WEIGHTS_0")::contains),"$.meshes.primitives.attributes","unsupported attribute/influence set");
                AccessorModel p=attribute(attrs,"POSITION",ElementType.VEC3),normal=attribute(attrs,"NORMAL",ElementType.VEC3);
                AccessorModel uv=attrs.containsKey("TEXCOORD_0")?attribute(attrs,"TEXCOORD_0",ElementType.VEC2):null;
                int count=p.getCount();check(normal.getCount()==count&&(uv==null||uv.getCount()==count),"$.meshes.attributes","attribute counts differ");
                vertexTotal+=count;if(skin>=0)skinTotal+=count;
                check(vertexTotal<=200_000&&skinTotal<=50_000,"$.meshes","CPU animation budget exceeded: 200k vertices / 50k skinned");
                int material=primitive.getMaterialModel()==null?materials.size()-1:model.getMaterialModels().indexOf(primitive.getMaterialModel());
                check(material>=0&&(uv!=null||materials.get(material).texture()<0),"$.meshes.material","unknown material or textured mesh missing UV0");
                double[] vertices=new double[count*8];
                AccessorFloatData pos=(AccessorFloatData)p.getAccessorData(),norm=(AccessorFloatData)normal.getAccessorData(),tex=uv==null?null:(AccessorFloatData)uv.getAccessorData();
                for(int v=0;v<count;v++){
                    for(int k=0;k<3;k++){vertices[v*8+k]=pos.get(v,k);vertices[v*8+3+k]=norm.get(v,k);}
                    if(tex!=null){vertices[v*8+6]=tex.get(v,0);vertices[v*8+7]=tex.get(v,1);}
                }
                int[] joints=null;double[] weights=null;
                if(skin>=0){
                    AccessorModel j=attrs.get("JOINTS_0"),w=attrs.get("WEIGHTS_0");
                    check(j!=null&&w!=null,"$.meshes.attributes","skinned mesh requires JOINTS_0 and WEIGHTS_0");
                    check(j.getElementType()==ElementType.VEC4&&!j.isNormalized()&&Set.of(5121,5123).contains(j.getComponentType())&&j.getCount()==count,"$.JOINTS_0","expected unsigned BYTE/SHORT VEC4 matching vertex count");
                    check(w.getElementType()==ElementType.VEC4&&w.getCount()==count&&((w.getComponentType()==5126&&!w.isNormalized())||(Set.of(5121,5123).contains(w.getComponentType())&&w.isNormalized())),"$.WEIGHTS_0","expected FLOAT or normalized unsigned BYTE/SHORT VEC4");
                    joints=new int[count*4];weights=new double[count*4];
                    for(int v=0;v<count;v++)for(int k=0;k<4;k++){
                        joints[v*4+k]=(int)unsigned(j.getAccessorData(),v,k);
                        weights[v*4+k]=w.getComponentType()==5126?((AccessorFloatData)w.getAccessorData()).get(v,k):unsigned(w.getAccessorData(),v,k)/(w.getComponentType()==5121?255.0:65535.0);
                    }
                    if(w.getComponentType()!=5126)for(int v=0;v<count;v++){
                        double sum=0;for(int k=0;k<4;k++)sum+=weights[v*4+k];
                        double tolerance=2.0/(w.getComponentType()==5121?255:65535);
                        check(sum>0&&Math.abs(sum-1)<=tolerance,"$.WEIGHTS_0","normalized integer weights exceed quantization tolerance");
                        for(int k=0;k<4;k++)weights[v*4+k]/=sum;
                    }
                }else check(!attrs.containsKey("JOINTS_0")&&!attrs.containsKey("WEIGHTS_0"),"$.meshes.attributes","influences require a skin on the mesh node");
                int[] indices=indices(primitive.getIndices(),count).stream().mapToInt(Integer::intValue).toArray();
                meshes.add(new RigModel.Mesh(n,skin,material,vertices,indices,joints,weights));
            }
        }
        List<AnimationClip> clips=new ArrayList<>();int c=0;long animatedScalars=0;
        for(AnimationModel animation:model.getAnimationModels()){
            List<AnimationClip.Channel> channels=new ArrayList<>();
            for(AnimationModel.Channel channel:animation.getChannels()){
                int node=source.indexOf(channel.getNodeModel());check(node>=0,"$.animations.channels.target","unknown node");
                var sampler=channel.getSampler();var path=AnimationClip.Path.valueOf(channel.getPath().toUpperCase(Locale.ROOT));
                var interpolation=sampler.getInterpolation()==null?AnimationClip.Interpolation.LINEAR:AnimationClip.Interpolation.valueOf(sampler.getInterpolation().name());
                var input=sampler.getInput();var output=sampler.getOutput();
                check(input.getElementType()==ElementType.SCALAR&&input.getComponentType()==5126&&!input.isNormalized()&&input.getCount()<=100_000,"$.animations.samplers.input","expected bounded FLOAT SCALAR timestamps");
                check(output.getElementType()==(path==AnimationClip.Path.ROTATION?ElementType.VEC4:ElementType.VEC3)&&output.getComponentType()==5126&&!output.isNormalized(),"$.animations.samplers.output","expected FLOAT TRS output");
                int components=path==AnimationClip.Path.ROTATION?4:3;
                check(output.getCount()==input.getCount()*(interpolation==AnimationClip.Interpolation.CUBICSPLINE?3:1),"$.animations.samplers.output","keyframe output count mismatch");
                animatedScalars+=input.getCount()+(long)output.getCount()*components;
                check(animatedScalars<=2_000_000,"$.animations","expanded sampler data exceeds 2M scalar budget");
                double[] times=new double[input.getCount()],values=new double[output.getCount()*components];
                AccessorFloatData timeData=(AccessorFloatData)input.getAccessorData(),valueData=(AccessorFloatData)output.getAccessorData();
                for(int i=0;i<times.length;i++)times[i]=timeData.get(i);
                for(int i=0;i<output.getCount();i++)for(int k=0;k<components;k++)values[i*components+k]=valueData.get(i,k);
                channels.add(new AnimationClip.Channel(node,path,interpolation,times,values));
            }
            clips.add(new AnimationClip(animation.getName()==null?"clip_"+c:animation.getName(),channels));c++;
        }
        return new RigModel(nodes,skins,meshes,clips);
    }
    private static void reachable(NodeModel node,Set<NodeModel> result){if(!result.add(node))return;for(NodeModel child:node.getChildren())reachable(child,result);}
    private static long unsigned(AccessorData data,int v,int k){return data instanceof AccessorByteData b?b.getInt(v,k):((AccessorShortData)data).getInt(v,k);}
    static List<Primitive> bindPrimitives(RigModel rig){
        RigFrame frame=new RigFrame(rig);List<Primitive> result=new ArrayList<>();
        for(int m=0;m<rig.meshes().size();m++){
            var mesh=rig.meshes().get(m);List<Vertex> vertices=new ArrayList<>();List<Integer> indices=new ArrayList<>();
            for(int v=0;v<mesh.vertexCount();v++)vertices.add(new Vertex((float)frame.vertex(m,v,0),(float)frame.vertex(m,v,1),(float)frame.vertex(m,v,2),
                (float)frame.vertex(m,v,3),(float)frame.vertex(m,v,4),(float)frame.vertex(m,v,5),(float)frame.vertex(m,v,6),(float)frame.vertex(m,v,7)));
            for(int i=0;i<mesh.indexCount();i+=3){indices.add(mesh.index(i));indices.add(mesh.index(i+(frame.reflected(m)?2:1)));indices.add(mesh.index(i+(frame.reflected(m)?1:2)));}
            result.add(new Primitive(rig.nodes().get(mesh.node()).name(),vertices,indices,mesh.material()));
        }
        return result;
    }
}
