package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.glb.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.*;

final class GlbLoaderTest {
    private static final WeaponId ID=WeaponId.parse("test:fixture.glb");
    private static byte[] exported()throws IOException{
        return Files.readAllBytes(Path.of("src/main/resources/assets/bobin_craft/armament/debug/technical_fixture.glb"));
    }
    private static GlbModelAsset load(byte[] bytes)throws IOException{
        return new JgltfModelLoader().load(ID,id->new ByteArrayInputStream(bytes));
    }
    private static JsonObject json(byte[] bytes){int size=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12);return JsonParser.parseString(new String(bytes,20,size,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
    private static byte[] modified(Consumer<JsonObject> action)throws IOException{
        byte[] original=exported();JsonObject root=json(original);action.accept(root);
        byte[] text=root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);int padded=(text.length+3)&~3;
        int offset=20+ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        ByteBuffer output=ByteBuffer.allocate(20+padded+original.length-offset).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(0x46546c67).putInt(2).putInt(output.capacity()).putInt(padded).putInt(0x4e4f534a).put(text);
        while(output.position()<20+padded)output.put((byte)' ');
        output.put(original,offset,original.length-offset);return output.array();
    }
    private static JsonObject firstPrimitive(JsonObject root){return root.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject();}

    @Test void genuineBlenderExportMatchesIndependentBlenderWorldVertices()throws Exception{
        GlbModelAsset asset=load(exported());
        JsonObject reference=JsonParser.parseString(Files.readString(Path.of("art/armament/technical_fixture.reference.json"))).getAsJsonObject().getAsJsonObject("nodes");
        assertEquals(5,asset.primitives().size());assertEquals(172,asset.triangles());
        assertEquals(1.0,asset.bounds().width(),0.00001);assertEquals(1.0,asset.bounds().depth(),0.00001);
        for(var primitive:asset.primitives()){
            JsonArray expected=reference.getAsJsonObject(primitive.nodeName()).getAsJsonArray("vertices");
            for(var v:primitive.vertices()){
                assertTrue(StreamSupport.stream(expected.spliterator(),false).map(JsonElement::getAsJsonArray).anyMatch(p->
                    Math.abs(p.get(0).getAsDouble()-v.x())<0.00003 && Math.abs(p.get(1).getAsDouble()-v.y())<0.00003 && Math.abs(p.get(2).getAsDouble()-v.z())<0.00003),primitive.nodeName()+" "+v);
                assertEquals(1,Math.sqrt(v.nx()*v.nx()+v.ny()*v.ny()+v.nz()*v.nz()),0.00001);
            }
        }
        assertTrue(asset.primitives().stream().anyMatch(p->p.nodeName().equals("NestedPolygonRing")));
        assertTrue(asset.primitives().stream().flatMap(p->p.vertices().stream()).anyMatch(v->Math.abs(v.nx())>0.1&&Math.abs(v.nz())>0.1),"must contain non-axis-aligned normals");
    }

    @Test void textureAndMaterialDataAreActuallyRead()throws Exception{
        GlbModelAsset asset=load(exported());assertEquals(1,asset.images().size());assertEquals(1,asset.textures().size());
        var image=asset.images().get(0);assertEquals(64,image.width());assertEquals(64,image.height());
        var decoded=ImageIO.read(new ByteArrayInputStream(image.encoded()));
        assertEquals(0x00ff0000,decoded.getRGB(63,0)&0xffffff,"red top stripe is kept; no extra V/image flip");
        assertEquals(0x0000ff00,decoded.getRGB(0,63)&0xffffff,"green left stripe is kept");
        assertEquals(10497,asset.textures().get(0).wrapS());assertEquals(9984,asset.textures().get(0).minFilter());
        assertTrue(asset.materials().stream().anyMatch(m->m.texture()==0));
        assertTrue(asset.materials().stream().anyMatch(m->m.baseColor().b()>m.baseColor().r()*2));
        byte[] copy=image.encoded();copy[0]=0;assertNotEquals(0,image.encoded()[0]);
        assertThrows(UnsupportedOperationException.class,()->asset.primitives().clear());
        assertThrows(UnsupportedOperationException.class,()->asset.primitives().get(0).indices().clear());
    }

    @Test void inverseTransposeAndReflectionsHaveCorrectOrientation()throws Exception{
        double[] matrix=StaticTransforms.identity();matrix[0]=-2;matrix[5]=3;matrix[10]=4;
        float[] normal=StaticTransforms.normal(matrix,1,1,0);double length=Math.sqrt(0.25+1.0/9);
        assertEquals(-0.5/length,normal[0],1e-6);assertEquals((1.0/3)/length,normal[1],1e-6);
        GlbModelAsset reflected=load(modified(root->{JsonObject top=root.getAsJsonArray("nodes").get(root.getAsJsonArray("nodes").size()-1).getAsJsonObject();
            // Select the actual scene root, not an assumed Blender ordering.
            int index=root.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsInt();
            root.getAsJsonArray("nodes").get(index).getAsJsonObject().add("scale",JsonParser.parseString("[-1,1,1]"));}));
        for(var p:reflected.primitives())for(int i=0;i<p.indices().size();i+=3){
            var a=p.vertices().get(p.indices().get(i));var b=p.vertices().get(p.indices().get(i+1));var c=p.vertices().get(p.indices().get(i+2));
            double x=(b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y());
            double y=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
            double z=(b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
            assertTrue(x*a.nx()+y*a.ny()+z*a.nz()>0,"reflection must repair winding and preserve outward normals");
        }
    }

    @TestFactory Stream<DynamicTest> malformedOrUnsupportedMetadataFailsSafely(){
        Map<String,Consumer<JsonObject>> cases=new LinkedHashMap<>();
        cases.put("external buffer URI",r->r.getAsJsonArray("buffers").get(0).getAsJsonObject().addProperty("uri","https://example.invalid/x.bin"));
        cases.put("embedded data URI",r->r.getAsJsonArray("images").get(0).getAsJsonObject().addProperty("uri","data:image/png;base64,AAAA"));
        cases.put("accessor outside buffer",r->r.getAsJsonArray("accessors").get(0).getAsJsonObject().addProperty("byteOffset",9999999));
        cases.put("accessor allocation limit",r->r.getAsJsonArray("accessors").get(0).getAsJsonObject().addProperty("count",Integer.MAX_VALUE));
        cases.put("fractional count",r->r.getAsJsonArray("accessors").get(0).getAsJsonObject().addProperty("count",1.2));
        cases.put("invalid component",r->r.getAsJsonArray("accessors").get(0).getAsJsonObject().addProperty("componentType",5124));
        cases.put("sparse not supported",r->r.getAsJsonArray("accessors").get(0).getAsJsonObject().add("sparse",new JsonObject()));
        cases.put("missing POSITION",r->firstPrimitive(r).getAsJsonObject("attributes").remove("POSITION"));
        cases.put("missing NORMAL",r->firstPrimitive(r).getAsJsonObject("attributes").remove("NORMAL"));
        cases.put("wrong mode",r->firstPrimitive(r).addProperty("mode",1));
        cases.put("unsupported required extension",r->r.add("extensionsRequired",JsonParser.parseString("[\"KHR_draco_mesh_compression\"]")));
        cases.put("skinning not supported",r->r.add("skins",JsonParser.parseString("[{}]")));
        cases.put("animations not supported",r->r.add("animations",JsonParser.parseString("[{}]")));
        cases.put("bad scene",r->r.addProperty("scene",1000));
        cases.put("cycle",r->{int index=r.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsInt();r.getAsJsonArray("nodes").get(index).getAsJsonObject().add("children",JsonParser.parseString("["+index+"]"));});
        cases.put("zero scale",r->{int index=r.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsInt();r.getAsJsonArray("nodes").get(index).getAsJsonObject().add("scale",JsonParser.parseString("[0,0,0]"));});
        cases.put("bad image MIME",r->r.getAsJsonArray("images").get(0).getAsJsonObject().addProperty("mimeType","image/svg+xml"));
        cases.put("non-unit quaternion",r->{int i=r.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsInt();r.getAsJsonArray("nodes").get(i).getAsJsonObject().add("rotation",JsonParser.parseString("[0,0,0,0]"));});
        cases.put("string translation",r->{int i=r.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsInt();r.getAsJsonArray("nodes").get(i).getAsJsonObject().add("translation",JsonParser.parseString("[\"1\",0,0]"));});
        return cases.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            IOException error=assertThrows(IOException.class,()->load(modified(e.getValue())));assertTrue(error.getMessage().contains(ID.toString()));
        }));
    }

    @Test void truncatedHeaderAndLengthMismatchAreRejected()throws Exception{
        byte[] good=exported();
        for(int size:new int[]{0,4,12,20,good.length-1})assertThrows(IOException.class,()->load(Arrays.copyOf(good,size)));
        byte[] wrong=good.clone();wrong[0]=0;assertThrows(IOException.class,()->load(wrong));
        byte[] version=good.clone();version[4]=1;assertThrows(IOException.class,()->load(version));
    }

    @Test void corruptBinaryAccessorsAndImagePayloadAreRejected()throws Exception{
        byte[] original=exported();JsonObject root=json(original);
        int bin=28+ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        JsonObject primitive=firstPrimitive(root);
        for(String attribute:List.of("POSITION","NORMAL","TEXCOORD_0")){
            int index=primitive.getAsJsonObject("attributes").get(attribute).getAsInt();
            JsonObject a=root.getAsJsonArray("accessors").get(index).getAsJsonObject();
            JsonObject view=root.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
            int offset=bin+(view.has("byteOffset")?view.get("byteOffset").getAsInt():0)+(a.has("byteOffset")?a.get("byteOffset").getAsInt():0);
            byte[] invalid=original.clone();ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putFloat(offset,Float.NaN);
            assertThrows(IOException.class,()->load(invalid),attribute);
        }
        int index=primitive.get("indices").getAsInt();JsonObject a=root.getAsJsonArray("accessors").get(index).getAsJsonObject();
        JsonObject view=root.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
        int offset=bin+(view.has("byteOffset")?view.get("byteOffset").getAsInt():0)+(a.has("byteOffset")?a.get("byteOffset").getAsInt():0);
        assertEquals(5123,a.get("componentType").getAsInt());
        byte[] invalidIndex=original.clone();ByteBuffer.wrap(invalidIndex).order(ByteOrder.LITTLE_ENDIAN).putShort(offset,(short)65535);
        assertThrows(IOException.class,()->load(invalidIndex));
        JsonObject image=root.getAsJsonArray("images").get(0).getAsJsonObject();
        JsonObject imageView=root.getAsJsonArray("bufferViews").get(image.get("bufferView").getAsInt()).getAsJsonObject();
        byte[] invalidImage=original.clone();invalidImage[bin+imageView.get("byteOffset").getAsInt()]=0;
        assertThrows(IOException.class,()->load(invalidImage));
    }

    @Test void sourceStreamsCloseOnSuccessAndFailure()throws Exception{
        for(byte[] bytes:List.of(exported(),new byte[0])){
            boolean[] closed={false};InputStream stream=new ByteArrayInputStream(bytes){@Override public void close(){closed[0]=true;}};
            try{new JgltfModelLoader().load(ID,id->stream);}catch(IOException expected){assertEquals(0,bytes.length);}
            assertTrue(closed[0]);
        }
    }
}
