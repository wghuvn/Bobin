package com.bobincraft.armament.client.model.glb;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.WeaponModelLoader;
import com.google.gson.JsonObject;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

import static com.bobincraft.armament.client.model.glb.GlbPreflight.check;
import static com.bobincraft.armament.client.model.glb.GlbModelAsset.*;

/** Static embedded GLB 2 loader. JglTF does parsing; this adapter validates the supported profile. */
public final class JgltfModelLoader implements WeaponModelLoader {
    @Override public GlbModelAsset load(WeaponId resource, ResourceSource source) throws IOException {
        if (!resource.path().endsWith(".glb")) throw new IOException(resource+": expected .glb resource");
        try (InputStream input = source.open(resource)) {
            byte[] bytes = input.readNBytes(64*1024*1024+1);
            JsonObject document = GlbPreflight.inspect(bytes);
            GltfModelReader reader = new GltfModelReader();
            List<String> errors = new ArrayList<>();
            reader.setJsonErrorConsumer(error -> errors.add(error.toString()));
            // Never use read(URI): no network, filesystem or data URI resolution.
            GltfModel model = reader.readWithoutReferences(new ByteArrayInputStream(bytes));
            check(errors.isEmpty(), "$", "JglTF validation: "+String.join("; ",errors));
            check(model.getSkinModels().isEmpty() && model.getAnimationModels().isEmpty(), "$", "static scene only");
            List<Image> images = images(model);
            List<Texture> textures = textures(model, images);
            List<Material> materials = materials(model);
            List<Primitive> primitives = new ArrayList<>();
            int scene = GlbPreflight.integer(document,"scene",0,model.getSceneModels().size()-1,0);
            int[] totals = {0,0};
            for (NodeModel node : model.getSceneModels().get(scene).getNodeModels())
                collect(node, StaticTransforms.identity(), primitives, materials, model, totals);
            check(!primitives.isEmpty(),"$.scenes","selected scene contains no triangle meshes");
            float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for(Primitive p:primitives)for(Vertex v:p.vertices()){
                minX=Math.min(minX,v.x());minY=Math.min(minY,v.y());minZ=Math.min(minZ,v.z());
                maxX=Math.max(maxX,v.x());maxY=Math.max(maxY,v.y());maxZ=Math.max(maxZ,v.z());
            }
            return new GlbModelAsset(resource,primitives,materials,images,textures,new Bounds(minX,minY,minZ,maxX,maxY,maxZ));
        } catch (IOException e) { throw new IOException(resource+": "+e.getMessage(),e); }
        catch (RuntimeException e) { throw new IOException(resource+": malformed GLB: "+e.getMessage(),e); }
    }

    private static void collect(NodeModel node,double[] parent,List<Primitive> output,List<Material> materials,
            GltfModel model,int[] totals)throws IOException {
        double[] local=node.computeLocalTransform(null);StaticTransforms.validate(local);
        double[] world=StaticTransforms.multiply(parent,local);StaticTransforms.validate(world);
        for(MeshModel mesh:node.getMeshModels())for(MeshPrimitiveModel primitive:mesh.getMeshPrimitiveModels()){
            check(primitive.getMode()==4,"$.meshes.primitives.mode","only TRIANGLES (4) is supported");
            Map<String,AccessorModel> attributes=primitive.getAttributes();
            check(attributes.keySet().stream().allMatch(Set.of("POSITION","NORMAL","TEXCOORD_0")::contains),
                    "$.meshes.primitives.attributes","unsupported attribute (static POSITION/NORMAL/UV0 profile)");
            AccessorModel position=attribute(attributes,"POSITION",ElementType.VEC3);
            AccessorModel normal=attribute(attributes,"NORMAL",ElementType.VEC3);
            AccessorModel uv=attributes.containsKey("TEXCOORD_0")?attribute(attributes,"TEXCOORD_0",ElementType.VEC2):null;
            int count=position.getCount();
            check(normal.getCount()==count && (uv==null || uv.getCount()==count),"$.meshes.primitives.attributes","attribute counts differ");
            totals[0]+=count;check(totals[0]<=500_000,"$.meshes","scene exceeds 500000 expanded vertices");
            int material=primitive.getMaterialModel()==null?materials.size()-1:model.getMaterialModels().indexOf(primitive.getMaterialModel());
            check(material>=0,"$.meshes.primitives.material","unknown material");
            check(uv!=null || materials.get(material).texture()<0,"$.meshes.primitives.TEXCOORD_0","textured primitive requires UV0");
            AccessorFloatData positions=(AccessorFloatData)position.getAccessorData(),normals=(AccessorFloatData)normal.getAccessorData();
            AccessorFloatData uvs=uv==null?null:(AccessorFloatData)uv.getAccessorData();
            List<Vertex> vertices=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                float[] p=StaticTransforms.position(world,positions.get(i,0),positions.get(i,1),positions.get(i,2));
                float[] n=StaticTransforms.normal(world,normals.get(i,0),normals.get(i,1),normals.get(i,2));
                float u=uvs==null?0:uvs.get(i,0),v=uvs==null?0:uvs.get(i,1);
                check(Float.isFinite(u)&&Float.isFinite(v),"$.TEXCOORD_0","non-finite UV");
                vertices.add(new Vertex(p[0],p[1],p[2],n[0],n[1],n[2],u,v));
            }
            List<Integer> indices=indices(primitive.getIndices(),count);
            totals[1]+=indices.size();check(totals[1]<=1_500_000,"$.meshes","scene exceeds 1500000 indices");
            // A reflected node reverses winding. Match glTF front-face semantics and transformed normals.
            if(StaticTransforms.determinant(world)<0)for(int i=0;i<indices.size();i+=3)Collections.swap(indices,i+1,i+2);
            output.add(new Primitive(node.getName()==null?"unnamed":node.getName(),vertices,indices,material));
        }
        for(NodeModel child:node.getChildren())collect(child,world,output,materials,model,totals);
    }

    private static AccessorModel attribute(Map<String,AccessorModel> attributes,String name,ElementType type)throws IOException{
        AccessorModel result=attributes.get(name);
        check(result!=null,"$."+name,"required attribute is missing; export normals/UVs from Blender");
        check(result.getElementType()==type && result.getComponentType()==5126 && !result.isNormalized(),"$."+name,"expected non-normalized FLOAT "+type);
        return result;
    }
    private static List<Integer> indices(AccessorModel accessor,int vertexCount)throws IOException{
        int count=accessor==null?vertexCount:accessor.getCount();
        check(count>0 && count%3==0,"$.indices","triangle index count must be positive and divisible by 3");
        List<Integer> result=new ArrayList<>(count);
        if(accessor!=null)check(accessor.getElementType()==ElementType.SCALAR && !accessor.isNormalized() && Set.of(5121,5123,5125).contains(accessor.getComponentType()),"$.indices","expected unsigned byte/short/int SCALAR");
        AccessorData data=accessor==null?null:accessor.getAccessorData();
        for(int i=0;i<count;i++){
            long index=data==null?i:data instanceof AccessorByteData b?b.getInt(i):data instanceof AccessorShortData s?s.getInt(i):((AccessorIntData)data).getLong(i);
            check(index>=0 && index<vertexCount,"$.indices["+i+"]","index outside POSITION accessor");result.add((int)index);
        }
        return result;
    }
    private static List<Image> images(GltfModel model)throws IOException{
        List<Image> result=new ArrayList<>();
        long pixels=0;
        for(ImageModel image:model.getImageModels()){
            check(image.getUri()==null && image.getBufferViewModel()!=null,"$.images","only embedded bufferView images are supported");
            check(Set.of("image/png","image/jpeg").contains(image.getMimeType()),"$.images.mimeType","expected PNG or JPEG");
            ByteBuffer data=image.getImageData().duplicate();check(data.remaining()<=16*1024*1024,"$.images","image exceeds 16 MiB");
            byte[] bytes=new byte[data.remaining()];data.get(bytes);
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                var readers=ImageIO.getImageReaders(stream);check(readers.hasNext(),"$.images","undecodable image");
                var reader=readers.next();
                try{
                    reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);
                    check(w>0&&h>0&&w<=4096&&h<=4096,"$.images","image dimensions must be 1..4096");
                    pixels+=(long)w*h;check(pixels<=16_777_216,"$.images","scene exceeds 16M decoded pixels");
                    String format=reader.getFormatName();
                    check(image.getMimeType().equals("image/png")?format.equalsIgnoreCase("png"):format.equalsIgnoreCase("jpeg"),"$.images.mimeType","does not match encoded image");
                    reader.read(0).flush(); // Verify payload too; a plausible header alone is insufficient.
                    result.add(new Image(bytes,w,h,image.getMimeType()));
                }finally{reader.dispose();}
            }
        }
        return result;
    }
    private static List<Texture> textures(GltfModel model,List<Image> images)throws IOException{
        List<Texture> result=new ArrayList<>();
        long pixels=0;
        for(TextureModel texture:model.getTextureModels()){
            int image=model.getImageModels().indexOf(texture.getImageModel());check(image>=0,"$.textures.source","missing image");
            pixels+=(long)images.get(image).width()*images.get(image).height();
            check(pixels<=16_777_216,"$.textures","scene exceeds 16M uploaded texture pixels");
            int s=texture.getWrapS()==null?10497:texture.getWrapS(),t=texture.getWrapT()==null?10497:texture.getWrapT();
            int min=texture.getMinFilter()==null?9729:texture.getMinFilter(),mag=texture.getMagFilter()==null?9729:texture.getMagFilter();
            check(Set.of(10497,33071,33648).contains(s)&&Set.of(10497,33071,33648).contains(t),"$.samplers","unsupported wrap mode");
            check(Set.of(9728,9729,9984,9985,9986,9987).contains(min)&&Set.of(9728,9729).contains(mag),"$.samplers","unsupported filtering");
            result.add(new Texture(image,s,t,min,mag));
        }
        return result;
    }
    private static List<Material> materials(GltfModel model)throws IOException{
        List<Material> result=new ArrayList<>();
        for(MaterialModel source:model.getMaterialModels()){
            check(source instanceof PbrMaterialModel,"$.materials","expected glTF 2 PBR material");PbrMaterialModel m=(PbrMaterialModel)source;
            check(m.getNormalTextureModel()==null && m.getEmissiveTextureModel()==null && m.getOcclusionTextureModel()==null,"$.materials","normal/emissive/occlusion maps are outside the minimal material profile");
            double[] emissive=m.getEmissiveFactor();if(emissive!=null)for(double e:emissive)check(e==0,"$.materials.emissiveFactor","emissive shading is not supported");
            PbrMetallicRoughnessModel pbr=m.getPbrMetallicRoughnessModel();
            double[] color=pbr==null||pbr.getBaseColorFactor()==null?new double[]{1,1,1,1}:pbr.getBaseColorFactor();
            check(color.length==4,"$.materials.baseColorFactor","expected four components");for(double x:color)unit(x,"baseColorFactor");
            check(m.getMetallicRoughnessTextureModel()==null,"$.materials","metallic/roughness maps are outside the minimal material profile");
            TextureModel texture=m.getBaseColorTextureModel();int textureIndex=texture==null?-1:model.getTextureModels().indexOf(texture);
            check(texture==null || (textureIndex>=0 && (m.getBaseColorTexcoord()==null||m.getBaseColorTexcoord()==0)),"$.materials.baseColorTexture","only valid TEXCOORD_0 textures are supported");
            double metallic=pbr==null||pbr.getMetallicFactor()==null?1:pbr.getMetallicFactor(),roughness=pbr==null||pbr.getRoughnessFactor()==null?1:pbr.getRoughnessFactor();
            unit(metallic,"metallicFactor");unit(roughness,"roughnessFactor");
            double cutoff=m.getAlphaCutoff()==null?0.5:m.getAlphaCutoff();unit(cutoff,"alphaCutoff");
            result.add(new Material(m.getName()==null?"unnamed":m.getName(),new Color((float)color[0],(float)color[1],(float)color[2],(float)color[3]),textureIndex,
                    m.getAlphaMode()==null?Alpha.OPAQUE:Alpha.valueOf(m.getAlphaMode().name()),(float)cutoff,Boolean.TRUE.equals(m.isDoubleSided()),(float)metallic,(float)roughness));
        }
        result.add(new Material("default",new Color(1,1,1,1),-1,Alpha.OPAQUE,0.5f,false,1,1));
        return result;
    }
    private static void unit(double v,String field)throws IOException{check(Double.isFinite(v)&&v>=0&&v<=1,"$.materials."+field,"expected finite value in 0..1");}
}
