package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.presentation.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** Anatomical lengths and actual proximal skin/sleeve ends, not a hand scale workaround. */
final class FirstPersonCompositionTest {
    private record Cap(String name,int mesh,int[] vertices) {}
    private record Fixture(String name,FirstPersonAsset asset,JsonObject reference,List<Cap> caps) {}
    private static final List<Fixture> fixtures=new ArrayList<>();
    @BeforeAll static void load()throws Exception{
        for(String suffix:List.of("","_slim")){
            String stem="first_person_fixture"+suffix;
            var asset=FirstPersonAsset.load(WeaponId.parse("bobin_craft:armament/debug/"+stem+".profile.json"),id->Files.newInputStream(FirstPersonBlenderTest.RESOURCES.resolve(id.namespace()).resolve(id.path())));
            var rig=asset.model().rig();var rest=new RigFrame(rig);
            var reference=JsonParser.parseString(Files.readString(Path.of("art/armament",stem+".reference.json"))).getAsJsonObject();
            var points=reference.getAsJsonObject("clips").getAsJsonArray("idle").get(0).getAsJsonObject().getAsJsonObject("vertices");
            List<Cap> caps=new ArrayList<>();
            for(String side:List.of("left","right"))for(String part:List.of("view_arm_","view_sleeve_")){
                String name=part+side;var golden=points.getAsJsonArray(name);
                var indices=reference.getAsJsonObject("topology").getAsJsonObject(name).getAsJsonArray("proximal_cap_vertices");
                assertNotNull(indices,"Explicit Blender cap topology required: "+name);assertEquals(20,indices.size());
                int mesh=-1;for(int m=0;m<rig.meshes().size();m++)if(rig.nodes().get(rig.meshes().get(m).node()).name().equals(name))mesh=m;
                assertTrue(mesh>=0,"Actual exported mesh required: "+name);Set<Integer> mapped=new LinkedHashSet<>();
                // Blender indices refer to unsplit mesh vertices. Include every
                // actual GLB UV/normal split at each authored proximal position.
                for(var index:indices){
                    var point=golden.get(index.getAsInt()).getAsJsonArray();boolean matched=false;
                    for(int v=0;v<rig.meshes().get(mesh).vertexCount();v++){
                        double squared=0;for(int k=0;k<3;k++){double d=rest.vertex(mesh,v,k)-point.get(k).getAsDouble();squared+=d*d;}
                        if(squared<1e-9){mapped.add(v);matched=true;}
                    }
                    assertTrue(matched,"Bind geometry must map Blender proximal vertex "+index+" in "+name);
                }
                assertTrue(mapped.size()>=20,"All authored proximal positions must be exported");
                caps.add(new Cap(name,mesh,mapped.stream().mapToInt(Integer::intValue).toArray()));
            }
            fixtures.add(new Fixture(stem,asset,reference,List.copyOf(caps)));
        }
    }
    @TestFactory Stream<DynamicTest> anatomicalBoneLengthsRemainFullSize(){
        return fixtures.stream().flatMap(f->Stream.of("left","right").map(side->DynamicTest.dynamicTest(f.name()+" "+side,()->{
            var rest=new RigFrame(f.asset().model().rig());
            assertEquals(.375,distance(rest,"upper_"+side,"forearm_"+side),2e-5);
            assertEquals(.25,distance(rest,"forearm_"+side,"hand_"+side),2e-5);
        })));
    }
    @TestFactory Stream<DynamicTest> skinAndSleeveProximalCapsStayOutsideAcrossAllClipSamplesAndProjections(){
        return fixtures.stream().flatMap(f->f.reference().getAsJsonObject("clips").entrySet().stream().flatMap(clip->Stream.of(70.0,90.0,110.0).flatMap(fov->Stream.of(4.0/3,16.0/9,21.0/9).map(aspect->DynamicTest.dynamicTest(f.name()+" "+clip.getKey()+" "+fov+"deg @"+aspect,()->{
            assertEquals(5,f.reference().getAsJsonObject("clips").size());assertEquals(9,clip.getValue().getAsJsonArray().size());
            var presenter=new FirstPersonWeaponPresenter(f.asset());presenter.animation(clip.getKey(),0);
            var identity=FirstPersonMathTest.identity();double[] projection=new double[16];PresentationMath.perspective(fov,aspect,.05,256,projection);
            float[] matrix=new float[16];
            for(var value:clip.getValue().getAsJsonArray()){
                double seconds=value.getAsJsonObject().get("seconds").getAsDouble(),ticks=seconds*20;
                long tick=(long)Math.floor(ticks);float delta=(float)(ticks-tick);if(delta>=1){tick++;delta=0;}
                String label=f.name()+" "+clip.getKey()+" @"+seconds+"s "+fov+"deg aspect="+aspect;
                assertTrue(presenter.sample(tick,delta,identity,projection,identity),label+" "+presenter.rejection()+" depth="+presenter.minimumDepth());
                assertEquals(0,presenter.contactMetres(),1e-5,label+" grip distance");
                assertEquals(0,presenter.contactDegrees(),.001,label+" grip angle");
                presenter.copyProjection(matrix);
                for(var cap:f.caps()){
                    double maxY=-Double.MAX_VALUE;
                    for(int vertex:cap.vertices()){
                        double x=presenter.pose().vertex(cap.mesh(),vertex,0),y=presenter.pose().vertex(cap.mesh(),vertex,1),z=presenter.pose().vertex(cap.mesh(),vertex,2);
                        double w=matrix[3]*x+matrix[7]*y+matrix[11]*z+matrix[15];assertTrue(w>.075,label+" "+cap.name()+" cap camera clearance");
                        maxY=Math.max(maxY,(matrix[1]*x+matrix[5]*y+matrix[9]*z+matrix[13])/w);
                    }
                    assertTrue(maxY< -1,label+" "+cap.name()+" proximal skin/sleeve edge must remain below the whole viewport; maxNdcY="+maxY);
                }
            }
        })))));
    }
    private static double distance(RigFrame pose,String a,String b){double squared=0;for(int k=0;k<3;k++)squared+=Math.pow(pose.global(pose.model().node(a),12+k)-pose.global(pose.model().node(b),12+k),2);return Math.sqrt(squared);}
}
