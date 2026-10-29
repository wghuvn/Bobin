package com.bobincraft.armament.client.model.glb;

import com.bobincraft.armament.serialization.StrictJson;
import com.google.gson.*;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Bounded GLB envelope/reference validation before JglTF constructs accessor views. */
final class GlbPreflight {
    private GlbPreflight() {}
    static JsonObject inspect(byte[] bytes) throws IOException {
        check(bytes.length >= 28 && bytes.length <= 64*1024*1024, "$", "GLB must be 28 bytes..64 MiB");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        check(b.getInt() == 0x46546c67, "$", "not a GLB file");
        check(b.getInt() == 2, "$", "expected GLB version 2");
        check(b.getInt() == bytes.length, "$", "declared length does not match file");
        int jsonLength = b.getInt();
        check(b.getInt() == 0x4e4f534a, "$", "first chunk must be JSON");
        check(jsonLength > 0 && jsonLength <= 1_048_576 && jsonLength % 4 == 0 && jsonLength <= b.remaining(), "$", "invalid JSON chunk length");
        byte[] json = new byte[jsonLength]; b.get(json);
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(json)).toString(); }
        catch (CharacterCodingException e) { throw new IOException("$: JSON chunk is not UTF-8", e); }
        JsonElement parsed = StrictJson.parse(text);
        check(parsed.isJsonObject(), "$", "expected glTF object");
        JsonObject root = parsed.getAsJsonObject();
        check(root.has("asset") && root.get("asset").isJsonObject() && "2.0".equals(root.getAsJsonObject("asset").get("version").getAsString()), "$.asset.version", "expected glTF 2.0");
        int binLength = 0;
        if (b.hasRemaining()) {
            check(b.remaining() >= 8, "$", "truncated BIN chunk");
            binLength = b.getInt();
            check(b.getInt() == 0x004e4942 && binLength >= 0 && binLength % 4 == 0 && binLength == b.remaining(), "$", "expected a single complete BIN chunk");
        }
        noUris(root, "$");
        for (String feature : List.of("extensionsRequired","extensionsUsed","skins","animations"))
            check(array(root,feature,0,0).isEmpty(), "$."+feature, "not supported by the static GLB profile");
        JsonArray buffers = array(root,"buffers",1,1);
        int declared = integer(buffers.get(0).getAsJsonObject(),"byteLength",0,Integer.MAX_VALUE,0);
        check(declared > 0 && declared <= binLength && binLength-declared <= 3, "$.buffers[0]", "BIN length mismatch");
        JsonArray views = array(root,"bufferViews",1,1024);
        for (int i=0;i<views.size();i++) {
            JsonObject v = views.get(i).getAsJsonObject();
            check(integer(v,"buffer",0,0,0)==0,"$.bufferViews["+i+"]","only buffer 0 is supported");
            long start = integer(v,"byteOffset",0,declared,0), size = integer(v,"byteLength",1,declared,-1);
            check(start+size <= declared,"$.bufferViews["+i+"]","view extends beyond BIN");
            if(v.has("byteStride")) { int stride=integer(v,"byteStride",4,252,0); check(stride%4==0,"$.bufferViews["+i+"].byteStride","must be a multiple of 4"); }
        }
        JsonArray accessors = array(root,"accessors",1,1024);
        for (int i=0;i<accessors.size();i++) {
            JsonObject a=accessors.get(i).getAsJsonObject();String path="$.accessors["+i+"]";
            check(!a.has("sparse"),path,"sparse accessors are not supported");
            int view=integer(a,"bufferView",0,views.size()-1,-1);
            int count=integer(a,"count",1,500_000,-1);
            int component=integer(a,"componentType",5120,5126,-1);
            int size=switch(component){case 5120,5121->1;case 5122,5123->2;case 5125,5126->4;default->throw new IOException(path+": unsupported component type");};
            String type=a.get("type").getAsString();
            int components=switch(type){case "SCALAR"->1;case "VEC2"->2;case "VEC3"->3;case "VEC4"->4;default->throw new IOException(path+": unsupported accessor type");};
            JsonObject v=views.get(view).getAsJsonObject();
            int offset=integer(a,"byteOffset",0,declared,0), element=size*components;
            int stride=integer(v,"byteStride",element,252,element);
            int viewOffset=integer(v,"byteOffset",0,declared,0);
            check(offset%size==0 && (viewOffset+offset)%size==0 && stride%size==0,path,"misaligned accessor");
            long end=(long)offset+(long)(count-1)*stride+element;
            check(end<=integer(v,"byteLength",1,declared,-1),path,"accessor extends beyond bufferView");
        }
        JsonArray nodes=array(root,"nodes",1,256);int[] parents=new int[nodes.size()];
        for(int i=0;i<nodes.size();i++){
            JsonObject node=nodes.get(i).getAsJsonObject();
            check(!node.has("skin") && !node.has("weights"),"$.nodes["+i+"]","skinning/morph weights are not supported");
            check(!node.has("matrix") || (!node.has("translation")&&!node.has("rotation")&&!node.has("scale")),"$.nodes["+i+"]","matrix and TRS are mutually exclusive");
            for(String key:List.of("matrix","translation","rotation","scale"))if(node.has(key)){
                int size=key.equals("matrix")?16:key.equals("rotation")?4:3;
                JsonArray values=array(node,key,size,size);double norm=0;
                for(JsonElement e:values){
                    check(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber(),"$.nodes["+i+"]."+key,"expected numeric vector");
                    double value=e.getAsDouble();check(Double.isFinite(value),"$.nodes["+i+"]."+key,"non-finite component");norm+=value*value;
                }
                if(key.equals("rotation"))check(Math.abs(norm-1)<1e-5,"$.nodes["+i+"].rotation","quaternion must be normalized");
            }
            for(JsonElement child:array(node,"children",0,256)){
                int n=exactInt(child,0,nodes.size()-1,"$.nodes["+i+"].children");
                check(++parents[n]<=1,"$.nodes["+i+"].children","node has multiple parents or duplicate references");
            }
        }
        for(int i=0;i<nodes.size();i++)visit(nodes,i,new HashSet<>(),0);
        JsonArray scenes=array(root,"scenes",1,32);
        for(JsonElement element:scenes){
            Set<Integer> roots=new HashSet<>();
            for(JsonElement n:array(element.getAsJsonObject(),"nodes",1,256)){
                int index=exactInt(n,0,nodes.size()-1,"$.scenes.nodes");
                check(roots.add(index) && parents[index]==0,"$.scenes.nodes","scene root is duplicated or has a parent");
            }
        }
        integer(root,"scene",0,scenes.size()-1,0);
        JsonArray meshes=array(root,"meshes",1,128);
        for(JsonElement e:meshes) for(JsonElement primitive:array(e.getAsJsonObject(),"primitives",1,256)){
            JsonObject pr=primitive.getAsJsonObject();check(array(pr,"targets",0,0).isEmpty(),"$.meshes.primitives.targets","morph targets are not supported");
        }
        array(root,"images",0,32);array(root,"textures",0,64);array(root,"materials",0,128);
        return root;
    }
    private static void visit(JsonArray nodes,int index,Set<Integer> path,int depth)throws IOException{
        check(depth<=64 && path.add(index),"$.nodes["+index+"]","cycle or hierarchy depth above 64");
        for(JsonElement e:array(nodes.get(index).getAsJsonObject(),"children",0,256))visit(nodes,e.getAsInt(),path,depth+1);
        path.remove(index);
    }
    private static void noUris(JsonElement value,String path)throws IOException{
        if(value.isJsonObject()) for(var e:value.getAsJsonObject().entrySet()){
            check(!e.getKey().equals("uri"),path+".uri","URI references are forbidden; export embedded GLB buffers/images");noUris(e.getValue(),path+"."+e.getKey());
        } else if(value.isJsonArray())for(JsonElement e:value.getAsJsonArray())noUris(e,path+"[]");
    }
    static JsonArray array(JsonObject o,String key,int min,int max)throws IOException{
        if(!o.has(key)){check(min==0,"$."+key,"required array is missing");return new JsonArray();}
        check(o.get(key).isJsonArray(),"$."+key,"expected array");JsonArray a=o.getAsJsonArray(key);
        check(a.size()>=min && a.size()<=max,"$."+key,"expected "+min+".."+max+" entries");return a;
    }
    static int integer(JsonObject o,String key,int min,int max,int fallback)throws IOException{
        if(!o.has(key)){check(fallback>=min && fallback<=max,"$."+key,"required integer is missing");return fallback;}
        return exactInt(o.get(key),min,max,"$."+key);
    }
    private static int exactInt(JsonElement e,int min,int max,String path)throws IOException{
        check(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber(),path,"expected integer");
        try{int v=e.getAsBigDecimal().intValueExact();check(v>=min&&v<=max,path,"integer outside allowed range");return v;}
        catch(ArithmeticException ex){throw new IOException(path+": expected exact integer",ex);}
    }
    static void check(boolean ok,String path,String message)throws IOException{if(!ok)throw new IOException(path+": "+message);}
}
