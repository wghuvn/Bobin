package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.model.glb.GlbModelAsset;
import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.validation.DefinitionValidationException;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** Loads real exported GLBs. These are CPU/asset tests, not a Minecraft draw test. */
final class PlayerHandSkinTest {
    static final Path DEBUG=Path.of("src/main/resources/assets/bobin_craft/armament/debug");
    static PlayerHandAppearance appearance;
    static final Map<String,FirstPersonAsset> assets=new HashMap<>();
    static final Map<String,JsonObject> references=new HashMap<>();
    @BeforeAll static void load()throws Exception{
        appearance=PlayerHandAppearance.read(Files.readString(DEBUG.resolve("first_person_hands.appearance.json")));
        for(String variant:List.of("default","slim")){
            assets.put(variant,FirstPersonAsset.load(appearance.profiles().get(variant),id->Files.newInputStream(FirstPersonBlenderTest.RESOURCES.resolve(id.namespace()).resolve(id.path()))));
            String stem="first_person_fixture"+(variant.equals("slim")?"_slim":"");
            references.put(variant,JsonParser.parseString(Files.readString(Path.of("art/armament",stem+".reference.json"))).getAsJsonObject());
        }
    }
    @Test void steveAndAlexAreSeparateAuthoredGeometryWithFullJointChains(){
        assertNotEquals(assets.get("default").model().id(),assets.get("slim").model().id());
        assertNotSame(assets.get("default").model().rig(),assets.get("slim").model().rig());
        for(var entry:assets.entrySet()){
            var rig=entry.getValue().model().rig();assertEquals(36,rig.skins().get(0).size());assertEquals(9,rig.meshes().size());
            for(String side:List.of("left","right")){
                for(String prefix:List.of("upper_","forearm_","hand_"))assertTrue(rig.node(prefix+side)>=0);
                for(String finger:List.of("thumb","index","middle","ring","little"))for(int i=1;i<=3;i++)assertTrue(rig.node(finger+"_"+i+"_"+side)>=0);
            }
        }
    }
    @Test void fourDistinctWhiteMaterialsUseLiveSkinRolesAndIndependentSleeveFlags(){
        for(var entry:assets.entrySet()){
            var asset=entry.getValue();var bindings=appearance.bind(asset.model(),entry.getKey());Set<PlayerHandAppearance.SkinMaterials.Role> seen=new HashSet<>();
            for(int i=0;i<asset.model().materials().size();i++){
                var role=bindings.role(i);seen.add(role);
                assertEquals(role!=PlayerHandAppearance.SkinMaterials.Role.EMBEDDED,bindings.playerSkin(i));
                for(boolean right:List.of(false,true))for(boolean left:List.of(false,true)){
                    boolean expected=switch(role){case SLEEVE_RIGHT->right;case SLEEVE_LEFT->left;default->true;};
                    assertEquals(expected,bindings.visible(i,right,left));
                }
            }
            assertEquals(Set.of(PlayerHandAppearance.SkinMaterials.Role.values()),seen);
        }
    }
    @Test void wrongAtlasVariantAndHeadUvsAreRejectedBeforeTextureUpload(){
        var model=assets.get("default").model();assertThrows(DefinitionValidationException.class,()->appearance.bind(model,"slim"));
        List<GlbModelAsset.Primitive> primitives=new ArrayList<>();
        for(var primitive:model.primitives()){
            if(model.materials().get(primitive.material()).name().equals(appearance.materials().get("base_left"))){
                List<GlbModelAsset.Vertex> vertices=new ArrayList<>(primitive.vertices());var v=vertices.get(0);
                vertices.set(0,new GlbModelAsset.Vertex(v.x(),v.y(),v.z(),v.nx(),v.ny(),v.nz(),.125f,.125f));
                primitives.add(new GlbModelAsset.Primitive(primitive.nodeName(),vertices,primitive.indices(),primitive.material()));
            }else primitives.add(primitive);
        }
        var bad=new GlbModelAsset(model.id(),primitives,model.materials(),model.images(),model.textures(),model.bounds(),model.rig());
        var error=assertThrows(DefinitionValidationException.class,()->appearance.bind(bad,"default"));assertTrue(error.getMessage().contains("64x64 skin"));
    }
    @Test void everySkinUvUsesTheCorrect64PixelArmNetIncludingAlexThreePixelFaces(){
        for(var entry:assets.entrySet()){
            var model=entry.getValue().model();var roles=appearance.bind(model);double width=entry.getKey().equals("slim")?3:4;
            for(var primitive:model.primitives()){
                var role=roles.role(primitive.material());if(!roles.playerSkin(primitive.material()))continue;
                boolean left=role==PlayerHandAppearance.SkinMaterials.Role.BASE_LEFT||role==PlayerHandAppearance.SkinMaterials.Role.SLEEVE_LEFT;
                boolean sleeve=role==PlayerHandAppearance.SkinMaterials.Role.SLEEVE_LEFT||role==PlayerHandAppearance.SkinMaterials.Role.SLEEVE_RIGHT;
                double u=left?(sleeve?48:32):40,v=left?48:(sleeve?32:16);
                for(var vertex:primitive.vertices()){
                    double x=vertex.u()*64,y=vertex.v()*64;
                    boolean sides=between(x,u,u+8+2*width)&&between(y,v+4,v+16);
                    boolean caps=between(x,u+4,u+4+2*width)&&between(y,v,v+4);
                    assertTrue(sides||caps,entry.getKey()+" "+role+" maps outside its vanilla arm tile: "+x+","+y);
                }
                var material=model.materials().get(primitive.material());var image=model.images().get(model.textures().get(material.texture()).image());
                assertEquals(64,image.width());assertEquals(64,image.height());
            }
        }
    }
    private static boolean between(double value,double min,double max){return value>=min-1e-4&&value<=max+1e-4;}
    @Test void closedBareHandsAndAuthoredDimensionsAreReportedByBlender(){
        for(var entry:references.entrySet())for(var mesh:entry.getValue().getAsJsonObject("topology").entrySet()){
            var topology=mesh.getValue().getAsJsonObject();
            assertEquals(entry.getKey().equals("slim")?3:4,topology.get("arm_pixels").getAsInt());
            if(mesh.getKey().contains("view_arm_")){
                assertEquals(0,topology.get("nonmanifold_edges").getAsInt(),mesh.getKey());assertEquals(18,topology.getAsJsonArray("weighted_bones").size(),"every shoulder/elbow/wrist/finger bone needs actual weights");
            }else assertEquals(20,topology.get("nonmanifold_edges").getAsInt(),"sleeve has one intentional open cuff");
        }
    }
    @Test void neitherHandNorSleevePenetratesTheObjectOrOtherHandInAuthoredHoldingPoses(){
        for(var ref:references.values())for(var sample:ref.getAsJsonArray("intersections")){
            assertEquals(0,sample.getAsJsonObject().getAsJsonObject("pairs").size(),sample.toString());
            for(var count:sample.getAsJsonObject().getAsJsonObject("self_contacts").entrySet())assertEquals(0,count.getValue().getAsInt(),sample.toString());
        }
    }
    @Test void elbowAndWristStressPosesRemainClosedWithoutSelfIntersectionsOrCollapsedEdges(){
        for(var ref:references.values()){
            assertEquals(7,ref.getAsJsonArray("joint_stress").size());
            for(var sample:ref.getAsJsonArray("joint_stress"))for(var entry:sample.getAsJsonObject().getAsJsonObject("meshes").entrySet()){
                var mesh=entry.getValue().getAsJsonObject();assertEquals(0,mesh.get("nonadjacent_triangle_contacts").getAsInt(),sample.toString());
                assertTrue(mesh.get("min_edge_ratio").getAsDouble()>.5,sample.toString());assertTrue(mesh.get("max_edge_ratio").getAsDouble()<1.9,sample.toString());
            }
        }
    }
    @Test void exportedForearmReallyHasFourOrThreePixelWidthAndFourPixelDepth(){
        for(var entry:assets.entrySet()){
            var rig=entry.getValue().model().rig();var frame=new RigFrame(rig);int forearm=rig.node("forearm_left");
            double[] world=new double[16],inverse=new double[16];for(int i=0;i<16;i++)world[i]=frame.global(forearm,i);RigMath.inverseAffine(world,0,inverse,0);
            double minX=100,maxX=-100,minZ=100,maxZ=-100;int found=0;
            for(int m=0;m<rig.meshes().size();m++)if(rig.nodes().get(rig.meshes().get(m).node()).name().equals("view_arm_left"))for(int v=0;v<rig.meshes().get(m).vertexCount();v++){
                double px=frame.vertex(m,v,0),py=frame.vertex(m,v,1),pz=frame.vertex(m,v,2);
                double x=inverse[0]*px+inverse[4]*py+inverse[8]*pz+inverse[12],y=inverse[1]*px+inverse[5]*py+inverse[9]*pz+inverse[13],z=inverse[2]*px+inverse[6]*py+inverse[10]*pz+inverse[14];
                if(Math.abs(y-.125)<2e-5){found++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
            }
            assertTrue(found>=4);assertEquals((entry.getKey().equals("slim")?3:4)/16.0,maxX-minX,3e-5);assertEquals(4/16.0,maxZ-minZ,3e-5);
        }
    }
    @Test void blenderPreviewUsesTheSameMetricPlacementAndPerspectiveAsThePresenter()throws Exception{
        for(var entry:assets.entrySet()){
            var asset=entry.getValue();var p=new FirstPersonWeaponPresenter(asset);p.animation("idle",0);p.inertia(false);
            double[] projection=new double[16],identity=FirstPersonMathTest.identity();PresentationMath.perspective(70,16.0/9,.05,100,projection);assertTrue(p.sample(0,0,identity,projection,identity));
            float[] matrix=new float[16];p.copyProjection(matrix);double minX=100,minY=100,maxX=-100,maxY=-100;
            for(int m=0;m<asset.model().rig().meshes().size();m++)if(asset.model().rig().meshes().get(m).skin()<0)for(int v=0;v<asset.model().rig().meshes().get(m).vertexCount();v++){
                double x=p.pose().vertex(m,v,0),y=p.pose().vertex(m,v,1),z=p.pose().vertex(m,v,2),w=matrix[3]*x+matrix[7]*y+matrix[11]*z+matrix[15];
                double screenX=.5+.5*(matrix[0]*x+matrix[4]*y+matrix[8]*z+matrix[12])/w,screenY=.5-.5*(matrix[1]*x+matrix[5]*y+matrix[9]*z+matrix[13])/w;
                minX=Math.min(minX,screenX);maxX=Math.max(maxX,screenX);minY=Math.min(minY,screenY);maxY=Math.max(maxY,screenY);
            }
            String stem="first_person_fixture"+(entry.getKey().equals("slim")?"_slim":"");
            var expected=JsonParser.parseString(Files.readString(Path.of("art/armament",stem+".framing.json"))).getAsJsonObject().getAsJsonObject("bounds_top_left_normalized").getAsJsonArray("technical_object");
            double[] actual={minX,minY,maxX,maxY};for(int k=0;k<4;k++)assertEquals(expected.get(k).getAsDouble(),actual[k],2e-5);
            assertTrue(minY>.18&&minY<.24,"reference top is about .195; keep the technical sample near that line");assertTrue(minX>.54,"keep the technical object out of the left half of the view");
        }
    }
    @TestFactory Stream<DynamicTest> alexPosesAndSocketsMatchIndependentBlenderEvaluation(){
        var asset=assets.get("slim");var rig=asset.model().rig();var reference=references.get("slim");var bind=new RigFrame(rig);
        var rest=reference.getAsJsonObject("clips").getAsJsonArray("idle").get(0).getAsJsonObject().getAsJsonObject("vertices");
        int[][] mapping=new int[rig.meshes().size()][];
        for(int m=0;m<rig.meshes().size();m++){
            var mesh=rig.meshes().get(m);var points=rest.getAsJsonArray(rig.nodes().get(mesh.node()).name());mapping[m]=new int[mesh.vertexCount()];
            for(int v=0;v<mesh.vertexCount();v++){
                double best=Double.POSITIVE_INFINITY;
                for(int p=0;p<points.size();p++){
                    double distance=0;for(int k=0;k<3;k++)distance=Math.max(distance,Math.abs(points.get(p).getAsJsonArray().get(k).getAsDouble()-bind.vertex(m,v,k)));
                    if(distance<best){best=distance;mapping[m][v]=p;}
                }
                assertTrue(best<4e-5,"Alex bind pose differs from Blender");
            }
        }
        return reference.getAsJsonObject("clips").entrySet().stream().flatMap(clip->StreamSupport.stream(clip.getValue().getAsJsonArray().spliterator(),false).map(value->DynamicTest.dynamicTest("Alex "+clip.getKey()+"@"+value.getAsJsonObject().get("seconds"),()->{
            var expected=value.getAsJsonObject();var controller=new GltfAnimationController(rig);controller.play(clip.getKey(),GltfAnimationController.Mode.HOLD,0,0);var frame=controller.sampleSeconds(expected.get("seconds").getAsDouble());
            for(int m=0;m<rig.meshes().size();m++){
                var mesh=rig.meshes().get(m);var points=expected.getAsJsonObject("vertices").getAsJsonArray(rig.nodes().get(mesh.node()).name());
                for(int v=0;v<mesh.vertexCount();v++)for(int k=0;k<3;k++)assertEquals(points.get(mapping[m][v]).getAsJsonArray().get(k).getAsDouble(),frame.vertex(m,v,k),8e-5);
            }
            var contacts=new GripAttachments(asset.profile(),asset.sockets());assertTrue(contacts.matches(frame));
        })));
    }
    @Test void missingOrTintedMaterialsCannotSilentlyRenderFixedColorHands(){
        var model=assets.get("default").model();
        for(String mode:List.of("missing","tint","alpha")){
            List<GlbModelAsset.Material> materials=new ArrayList<>();
            for(var material:model.materials()){
                if(material.name().equals(appearance.materials().get("base_left")))materials.add(new GlbModelAsset.Material(mode.equals("missing")?"old_untextured_hand":material.name(),mode.equals("tint")?new GlbModelAsset.Color(.4f,.3f,.2f,1):material.baseColor(),material.texture(),mode.equals("alpha")?GlbModelAsset.Alpha.BLEND:material.alpha(),material.cutoff(),material.doubleSided(),material.metallic(),material.roughness()));
                else materials.add(material);
            }
            var bad=new GlbModelAsset(model.id(),model.primitives(),materials,model.images(),model.textures(),model.bounds(),model.rig());
            assertThrows(DefinitionValidationException.class,()->appearance.bind(bad),mode);
        }
    }
    @Test void appearanceRejectsUnknownFieldsDuplicateKeysAliasedVariantsAndRoles()throws Exception{
        String json=Files.readString(DEBUG.resolve("first_person_hands.appearance.json"));
        assertThrows(DefinitionValidationException.class,()->PlayerHandAppearance.read(json.replace("\"schema_version\": 1","\"schema_version\": 1, \"scale\": 0.5")));
        assertThrows(DefinitionValidationException.class,()->PlayerHandAppearance.read(json.replace("\"schema_version\": 1","\"schema_version\": 1, \"schema_version\": 1")));
        assertThrows(DefinitionValidationException.class,()->PlayerHandAppearance.read(json.replace("first_person_fixture_slim.profile.json","first_person_fixture.profile.json")));
        assertThrows(DefinitionValidationException.class,()->PlayerHandAppearance.read(json.replace("player_sleeve_left","player_skin_left")));
        assertThrows(UnsupportedOperationException.class,()->appearance.materials().clear());
    }
}
