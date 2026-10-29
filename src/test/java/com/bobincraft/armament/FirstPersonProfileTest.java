package com.bobincraft.armament;

import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.validation.DefinitionValidationException;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

final class FirstPersonProfileTest {
    private static JsonObject base()throws IOException{return JsonParser.parseString(Files.readString(FirstPersonBlenderTest.RESOURCES.resolve("bobin_craft/armament/debug/first_person_fixture.profile.json"))).getAsJsonObject();}
    @TestFactory Stream<DynamicTest> strictProfileCorpus()throws IOException{
        var cases=JsonParser.parseString(Files.readString(Path.of("src/test/resources/armament/first-person-cases.json"))).getAsJsonArray();
        return StreamSupport.stream(cases.spliterator(),false).map(entry->DynamicTest.dynamicTest(entry.getAsJsonObject().get("name").getAsString(),()->{
            JsonObject test=entry.getAsJsonObject(),document=base();
            for(var element:test.getAsJsonArray("patches")){
                var edit=element.getAsJsonObject();String[] parts=edit.get("field").getAsString().split("\\.");JsonElement parent=document;
                for(int i=0;i<parts.length-1;i++)parent=parent.isJsonArray()?parent.getAsJsonArray().get(Integer.parseInt(parts[i])):parent.getAsJsonObject().get(parts[i]);
                if(parent.isJsonArray())parent.getAsJsonArray().set(Integer.parseInt(parts[parts.length-1]),edit.get("value").deepCopy());
                else if(edit.has("remove"))parent.getAsJsonObject().remove(parts[parts.length-1]);else parent.getAsJsonObject().add(parts[parts.length-1],edit.get("value").deepCopy());
            }
            if(test.get("valid").getAsBoolean())assertDoesNotThrow(()->FirstPersonProfile.read(document.toString()));
            else{var error=assertThrows(DefinitionValidationException.class,()->FirstPersonProfile.read(document.toString()));assertTrue(error.getMessage().contains("$"));}
        }));
    }
    @Test void immutableProfileAndDuplicateJsonKeys()throws Exception{
        var profile=FirstPersonProfile.read(base().toString());assertThrows(UnsupportedOperationException.class,()->profile.cameraOffset().set(0,1.0));assertThrows(UnsupportedOperationException.class,()->profile.clips().clear());
        assertThrows(DefinitionValidationException.class,()->FirstPersonProfile.read(base().toString().replace("\"schema_version\":1","\"schema_version\":1,\"schema_version\":1")));
    }
    @Test void bundleRejectsMissingClipsAndUnknownSocketsBeforeAnyGpuWork()throws Exception{
        for(String mode:List.of("clip","socket","rig")){
            var source=(com.bobincraft.armament.client.model.WeaponModelLoader.ResourceSource)(id->{
                String path=id.path();
                if(mode.equals("clip")&&id.equals(FirstPersonBlenderTest.PROFILE)){var json=base();json.getAsJsonObject("clips").addProperty("idle","missing_clip");return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));}
                Path file=FirstPersonBlenderTest.RESOURCES.resolve(id.namespace()).resolve(path);
                if(mode.equals("socket")&&path.endsWith(".sockets.json")){var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();json.getAsJsonArray("sockets").get(0).getAsJsonObject().addProperty("node","missing_node");return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));}
                if(mode.equals("rig")&&path.endsWith(".sockets.json")){var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();json.addProperty("rig_id","test:wrong");return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));}
                return Files.newInputStream(file);
            });
            assertThrows(IOException.class,()->FirstPersonAsset.load(FirstPersonBlenderTest.PROFILE,source),mode);
        }
    }
    @Test void metadataAndModelStreamsAreClosed()throws Exception{
        int[] opened={0},closed={0};
        FirstPersonAsset.load(FirstPersonBlenderTest.PROFILE,id->{opened[0]++;return new FilterInputStream(Files.newInputStream(FirstPersonBlenderTest.RESOURCES.resolve(id.namespace()).resolve(id.path()))){@Override public void close()throws IOException{closed[0]++;super.close();}};});
        assertEquals(4,opened[0]);assertEquals(opened[0],closed[0]);
    }
}
