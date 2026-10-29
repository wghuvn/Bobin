package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.model.glb.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** Mutations of a real Blender GLB exercise loader boundaries, not a mock reader. */
final class GltfImportTest {
    private static final WeaponId ID=WeaponId.parse("test:animation.glb");
    private static final class Document {
        final JsonObject json;final byte[] binary;
        Document()throws IOException{
            byte[] bytes=Files.readAllBytes(GltfBlenderTest.ASSETS.resolve("animation_fixture.glb"));int length=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
            json=JsonParser.parseString(new String(bytes,20,length,StandardCharsets.UTF_8)).getAsJsonObject();binary=Arrays.copyOfRange(bytes,28+length,bytes.length);
        }
        JsonObject accessor(int i){return json.getAsJsonArray("accessors").get(i).getAsJsonObject();}
        int offset(int i){var a=accessor(i);var v=json.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();return (v.has("byteOffset")?v.get("byteOffset").getAsInt():0)+(a.has("byteOffset")?a.get("byteOffset").getAsInt():0);}
        JsonObject skin(){return json.getAsJsonArray("skins").get(0).getAsJsonObject();}
        JsonObject animation(){return json.getAsJsonArray("animations").get(0).getAsJsonObject();}
        JsonObject sampler(){return animation().getAsJsonArray("samplers").get(0).getAsJsonObject();}
        JsonObject primitive(){return json.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject();}
        int attr(String name){return primitive().getAsJsonObject("attributes").get(name).getAsInt();}
        ByteBuffer bin(){return ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);}
        byte[] bytes(){
            byte[] text=json.toString().getBytes(StandardCharsets.UTF_8);int length=(text.length+3)&~3;
            ByteBuffer b=ByteBuffer.allocate(28+length+binary.length).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(0x46546c67).putInt(2).putInt(b.capacity()).putInt(length).putInt(0x4e4f534a).put(text);
            while(b.position()<20+length)b.put((byte)' ');b.putInt(binary.length).putInt(0x004e4942).put(binary);return b.array();
        }
        GlbModelAsset load()throws IOException{return new JgltfModelLoader().load(ID,id->new ByteArrayInputStream(bytes()));}
    }
    @TestFactory Stream<DynamicTest> damagedAnimationAndSkinDataAreRejected(){
        Map<String,Consumer<Document>> cases=new LinkedHashMap<>();
        cases.put("duplicate joint",d->d.skin().getAsJsonArray("joints").set(1,d.skin().getAsJsonArray("joints").get(0)));
        cases.put("unknown joint",d->d.skin().getAsJsonArray("joints").set(0,new JsonPrimitive(10000)));
        cases.put("empty joints",d->d.skin().add("joints",new JsonArray()));
        cases.put("bad inverse bind type",d->d.accessor(d.skin().get("inverseBindMatrices").getAsInt()).addProperty("type","VEC4"));
        cases.put("short inverse bind count",d->d.accessor(d.skin().get("inverseBindMatrices").getAsInt()).addProperty("count",1));
        cases.put("non-finite inverse bind",d->d.bin().putFloat(d.offset(d.skin().get("inverseBindMatrices").getAsInt()),Float.NaN));
        cases.put("projective inverse bind",d->d.bin().putFloat(d.offset(d.skin().get("inverseBindMatrices").getAsInt())+12,1));
        cases.put("wrong skeleton ancestor",d->{int node=IntStream.range(0,d.json.getAsJsonArray("nodes").size()).filter(i->d.json.getAsJsonArray("nodes").get(i).getAsJsonObject().get("name").getAsString().equals("slider")).findFirst().orElseThrow();d.skin().addProperty("skeleton",node);});
        cases.put("input type",d->d.accessor(d.sampler().get("input").getAsInt()).addProperty("type","VEC2"));
        cases.put("input normalized",d->d.accessor(d.sampler().get("input").getAsInt()).addProperty("normalized",true));
        cases.put("input time NaN",d->d.bin().putFloat(d.offset(d.sampler().get("input").getAsInt()),Float.NaN));
        cases.put("duplicate timestamps",d->d.bin().putFloat(d.offset(d.sampler().get("input").getAsInt())+4,0));
        cases.put("negative timestamp",d->d.bin().putFloat(d.offset(d.sampler().get("input").getAsInt()),-1));
        cases.put("output count",d->d.accessor(d.sampler().get("output").getAsInt()).addProperty("count",1));
        cases.put("non-unit rotation key",d->{int p=d.offset(d.sampler().get("output").getAsInt());for(int k=0;k<4;k++)d.bin().putFloat(p+k*4,0);});
        cases.put("duplicate channel",d->{var channels=d.animation().getAsJsonArray("channels");channels.add(channels.get(0).deepCopy());});
        cases.put("unsupported weights channel",d->d.animation().getAsJsonArray("channels").get(0).getAsJsonObject().getAsJsonObject("target").addProperty("path","weights"));
        cases.put("animation matrix node",d->{int n=d.animation().getAsJsonArray("channels").get(0).getAsJsonObject().getAsJsonObject("target").get("node").getAsInt();var node=d.json.getAsJsonArray("nodes").get(n).getAsJsonObject();node.remove("translation");node.remove("rotation");node.remove("scale");node.add("matrix",JsonParser.parseString("[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]"));});
        cases.put("missing weights",d->d.primitive().getAsJsonObject("attributes").remove("WEIGHTS_0"));
        cases.put("signed joints",d->d.accessor(d.attr("JOINTS_0")).addProperty("componentType",5120));
        cases.put("normalized joints",d->d.accessor(d.attr("JOINTS_0")).addProperty("normalized",true));
        cases.put("joint outside palette",d->{int p=d.offset(d.attr("JOINTS_0"));d.bin().put(p,(byte)127);});
        cases.put("zero weights",d->{int p=d.offset(d.attr("WEIGHTS_0"));for(int i=0;i<4;i++)d.bin().putFloat(p+i*4,0);});
        cases.put("negative weights",d->d.bin().putFloat(d.offset(d.attr("WEIGHTS_0")),-1));
        cases.put("non-finite weights",d->d.bin().putFloat(d.offset(d.attr("WEIGHTS_0")),Float.NaN));
        cases.put("extra influence set",d->d.primitive().getAsJsonObject("attributes").addProperty("JOINTS_1",d.attr("JOINTS_0")));
        return cases.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            Document document=new Document();e.getValue().accept(document);IOException error=assertThrows(IOException.class,document::load);assertTrue(error.getMessage().contains(ID.toString()));
        }));
    }
    @TestFactory Stream<DynamicTest> normalizedUnsignedWeightsAreDecoded(){
        return Stream.of(5121,5123).map(component->DynamicTest.dynamicTest("weights component "+component,()->{
            Document original=new Document();var expected=original.load();Document d=new Document();int id=d.attr("WEIGHTS_0"),p=d.offset(id),count=d.accessor(id).get("count").getAsInt();
            float[] values=new float[count*4];for(int i=0;i<values.length;i++)values[i]=d.bin().getFloat(p+i*4);
            int size=component==5121?1:2,max=component==5121?255:65535;
            for(int i=0;i<values.length;i++){int value=Math.round(values[i]*max);if(size==1)d.bin().put(p+i,(byte)value);else d.bin().putShort(p+i*2,(short)value);}
            d.accessor(id).addProperty("componentType",component);d.accessor(id).addProperty("normalized",true);
            var actual=d.load();var a=new GltfAnimationController(actual.rig());var b=new GltfAnimationController(expected.rig());
            a.play("bend",GltfAnimationController.Mode.HOLD,0,0);b.play("bend",GltfAnimationController.Mode.HOLD,0,0);
            var af=a.sampleSeconds(0.75);var bf=b.sampleSeconds(0.75);
            for(int m=0;m<actual.rig().meshes().size();m++)for(int v=0;v<actual.rig().meshes().get(m).vertexCount();v++)for(int k=0;k<3;k++)assertEquals(bf.vertex(m,v,k),af.vertex(m,v,k),0.003);
        }));
    }
    @Test void missingInverseBindDefaultsToIdentity()throws Exception{
        Document d=new Document();d.skin().remove("inverseBindMatrices");var rig=d.load().rig();var frame=new RigFrame(rig);
        for(int m=0;m<rig.meshes().size();m++)if(rig.meshes().get(m).skin()>=0){var skin=rig.skins().get(rig.meshes().get(m).skin());for(int j=0;j<skin.size();j++)for(int c=0;c<16;c++)assertEquals(frame.global(skin.joint(j),c),frame.jointWorldMatrix(m,j,c),1e-12);}
    }
    @Test void reloadingProducesIndependentRigAndPoseStorage()throws Exception{
        var a=new Document().load();var b=new Document().load();assertNotSame(a.rig(),b.rig());
        var first=new GltfAnimationController(a.rig());first.play("bend",GltfAnimationController.Mode.HOLD,0,0);var old=first.sampleSeconds(1);double point=old.vertex(0,0,0);
        var second=new GltfAnimationController(b.rig());second.play("slide",GltfAnimationController.Mode.LOOP,0,0);second.sampleSeconds(0.5);
        assertEquals(point,old.vertex(0,0,0));
    }
}
