package com.bobincraft.armament;

import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.presentation.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** Active assets contain only the gauge. Minecraft owns the actual arm/sleeve geometry and UVs. */
final class VanillaArmAttachmentsTest {
    private record Fixture(String name, FirstPersonAsset asset, VanillaArmAttachments arms, JsonObject golden) {}
    private static final List<Fixture> fixtures = new ArrayList<>();
    @BeforeAll static void load() throws Exception {
        for (String variant : List.of("default", "slim")) {
            var asset = FirstPersonAsset.load(VanillaArmAttachments.PROFILES.get(variant),
                    id -> Files.newInputStream(FirstPersonBlenderTest.RESOURCES.resolve(id.namespace()).resolve(id.path())));
            var arms = new VanillaArmAttachments(asset, variant);
            String name = "vanilla_first_person_fixture" + (variant.equals("slim") ? "_slim" : "");
            var golden = JsonParser.parseString(Files.readString(Path.of("art/armament", name + ".reference.json"))).getAsJsonObject();
            fixtures.add(new Fixture(name, asset, arms, golden));
        }
    }
    @TestFactory Stream<DynamicTest> activeGaugeAssetsCannotRenderCustomHands() {
        return fixtures.stream().map(f -> DynamicTest.dynamicTest(f.name(), () -> {
            var model = f.asset().model().rig();
            assertTrue(model.skins().isEmpty());assertEquals(7, model.meshes().size());
            for (var mesh : model.meshes()) { assertEquals(-1, mesh.skin());assertTrue(model.nodes().get(mesh.node()).name().startsWith("gauge_")); }
            for (var material : f.asset().model().materials()) {
                assertFalse(material.name().contains("skin"));assertFalse(material.name().contains("sleeve"));
            }
            assertEquals(.75, VanillaArmAttachments.LENGTH);assertEquals(.25, VanillaArmAttachments.DEPTH);
            assertEquals(f.arms().slim() ? 3.0/16 : 4.0/16, f.arms().width());
        }));
    }
    @Test void oldSkinnedFixtureIsRejectedByTheNativeArmPath() throws Exception {
        var old = FirstPersonAsset.load(FirstPersonBlenderTest.PROFILE,
                id -> Files.newInputStream(FirstPersonBlenderTest.RESOURCES.resolve(id.namespace()).resolve(id.path())));
        assertThrows(IllegalArgumentException.class, () -> new VanillaArmAttachments(old, "default"));
    }
    @TestFactory Stream<DynamicTest> rigidMinecraftCuboidsAndAnchorsMatchIndependentBlenderCalibration() {
        return fixtures.stream().flatMap(f -> f.golden().getAsJsonObject("clips").entrySet().stream().flatMap(clip ->
                StreamSupport.stream(clip.getValue().getAsJsonArray().spliterator(), false).map(value -> DynamicTest.dynamicTest(
                        f.name() + " " + clip.getKey() + "@" + value.getAsJsonObject().get("seconds"), () -> {
                            var expected = value.getAsJsonObject();
                            var c = new GltfAnimationController(f.asset().model().rig());
                            c.play(clip.getKey(), GltfAnimationController.Mode.HOLD, 0, 0);
                            var frame = c.sampleSeconds(expected.get("seconds").getAsDouble());
                            double[] anchor = new double[16], part = new double[16];
                            for (var side : VanillaArmAttachments.Side.values()) {
                                String name = side.name().toLowerCase(Locale.ROOT);
                                f.arms().anchor(frame, side, anchor);
                                GripFrameConvention.validate(anchor, "native anchor");
                                var values = expected.getAsJsonObject("anchors").getAsJsonArray(name);
                                for (int i = 0; i < 16; i++) assertEquals(values.get(i).getAsDouble(), anchor[i], 8e-5);
                                f.arms().modelPartTransform(frame, side, part);
                                for (boolean sleeve : List.of(false, true)) {
                                    var points = corners(f.arms(), side, sleeve, part);
                                    var golden = expected.getAsJsonObject("proxy_corners").getAsJsonArray("vanilla_reference_" + name + (sleeve ? "_sleeve" : "_arm"));
                                    for (int p = 0; p < 8; p++) for (int k = 0; k < 3; k++) assertEquals(golden.get(p).getAsJsonArray().get(k).getAsDouble(), points[p][k], 8e-5);
                                    double d = sleeve ? 2*VanillaArmAttachments.SLEEVE_DILATION : 0;
                                    assertEquals(f.arms().width()+d, distance(points[0],points[1]), 2e-6);
                                    assertEquals(VanillaArmAttachments.LENGTH+d, distance(points[0],points[3]), 2e-6);
                                    assertEquals(VanillaArmAttachments.DEPTH+d, distance(points[0],points[4]), 2e-6);
                                }
                            }
                        }))));
    }
    @TestFactory Stream<DynamicTest> activePresenterAndNativeProximalEndsStaySafeAcrossFovsAndActions() {
        return fixtures.stream().flatMap(f -> Stream.of(70.0,90.0,110.0).map(fov -> DynamicTest.dynamicTest(f.name()+" FOV "+fov, () -> {
            for (String action : List.of("idle","inspect","raise","lower","move")) {
                var p = new FirstPersonWeaponPresenter(f.asset());p.animation(action,0);
                double duration = f.asset().model().rig().clip(action).duration();
                for (int sample=0;sample<=16;sample++) {
                    double seconds = duration*sample/16, ticks = seconds*20;
                    long tick=(long)Math.floor(ticks);float delta=(float)(ticks-tick);if(delta>=1){tick++;delta=0;}
                    double[] projection=new double[16],identity=FirstPersonMathTest.identity();
                    PresentationMath.perspective(fov,16.0/9,.05,256,projection);
                    assertTrue(p.sample(tick,delta,identity,projection,identity),p.rejection());
                    assertTrue(p.minimumDepth()>.075);
                    double[] placement=new double[16],part=new double[16],view=new double[16];p.copyModelPlacement(placement);
                    for (var side : VanillaArmAttachments.Side.values()) {
                        f.arms().modelPartTransform(p.pose(),side,part);RigMath.multiply(placement,0,part,0,view,0);
                        var points=corners(f.arms(),side,true,view);
                        for (int index : new int[]{0,1,4,5}) {
                            // Body-side caps can be behind the camera: normal near-plane clipping.
                            // Every visible cap point must remain below the entire viewport.
                            var point=points[index];double depth=-point[2];
                            if(depth>.05)assertTrue(projection[5]*point[1]/depth < -1,
                                    f.name()+" "+action+" "+side+" cap in frame at "+seconds+"s / "+fov);
                        }
                    }
                }
            }
        })));
    }
    @TestFactory Stream<DynamicTest> ordinaryCuboidEndsReachTheRealGaugePlanesWithoutSkinPenetration() {
        return fixtures.stream().map(f -> DynamicTest.dynamicTest(f.name(), () -> {
            for (String clip : List.of("idle","inspect","raise","lower","move")) {
                var c=new GltfAnimationController(f.asset().model().rig());c.play(clip,GltfAnimationController.Mode.HOLD,0,0);
                for(int sample=0;sample<=8;sample++) {
                    var pose=c.sampleSeconds(f.asset().model().rig().clip(clip).duration()*sample/8);
                    for(var side:VanillaArmAttachments.Side.values()) {
                        boolean right=side==VanillaArmAttachments.Side.RIGHT;
                        double[] target=new double[16],part=new double[16],inverse=new double[16];
                        f.asset().sockets().sample(right?"grip_primary":"grip_secondary",pose,target);
                        f.arms().modelPartTransform(pose,side,part);RigMath.inverseAffine(part,0,inverse,0);
                        double[] local=new double[3];
                        for(int k=0;k<3;k++)local[k]=inverse[k]*target[12]+inverse[4+k]*target[13]+inverse[8+k]*target[14]+inverse[12+k];
                        assertEquals(.625,local[1],3e-5,"target must be on the native ModelPart end cap");
                        assertTrue(Math.abs(local[0]-f.arms().endX(side))<=f.arms().width()/2+3e-5);
                        assertTrue(Math.abs(local[2])<=.125+3e-5);
                        double nearest=Double.POSITIVE_INFINITY;
                        for(var p:corners(f.arms(),side,false,part)) {
                            double distance=0;for(int k=0;k<3;k++)distance+=(p[k]-target[12+k])*target[8+k];
                            assertTrue(distance>=-3e-5,"native skin must stay outside the gauge contact plane");nearest=Math.min(nearest,distance);
                        }
                        assertEquals(0,nearest,3e-5);
                        String panel=right?"gauge_primary_handle":"gauge_forward_support";int found=0;
                        for(int m=0;m<pose.model().meshes().size();m++) {
                            var mesh=pose.model().meshes().get(m);if(!pose.model().nodes().get(mesh.node()).name().equals(panel))continue;
                            for(int v=0;v<mesh.vertexCount();v++) {
                                double alignment=0,distance=0;
                                for(int k=0;k<3;k++){alignment+=pose.vertex(m,v,3+k)*target[8+k];distance+=(target[12+k]-pose.vertex(m,v,k))*pose.vertex(m,v,3+k);}
                                if(alignment>.999){assertEquals(.001,distance,3e-5);found++;}
                                else assertTrue(distance<=3e-5,"contact must fall within the actual finite panel");
                            }
                        }
                        assertTrue(found>0);
                    }
                }
            }
        }));
    }
    private static double[][] corners(VanillaArmAttachments arms, VanillaArmAttachments.Side side, boolean sleeve, double[] matrix) {
        double d=sleeve?VanillaArmAttachments.SLEEVE_DILATION:0,x=arms.endX(side),w=arms.width()/2;
        double[][] points={{x-w-d,-.125-d,-.125-d},{x+w+d,-.125-d,-.125-d},{x+w+d,.625+d,-.125-d},{x-w-d,.625+d,-.125-d},
                {x-w-d,-.125-d,.125+d},{x+w+d,-.125-d,.125+d},{x+w+d,.625+d,.125+d},{x-w-d,.625+d,.125+d}};
        for(var p:points){double a=p[0],b=p[1],c=p[2];for(int k=0;k<3;k++)p[k]=matrix[k]*a+matrix[4+k]*b+matrix[8+k]*c+matrix[12+k];}
        return points;
    }
    private static double distance(double[] a,double[] b){double sum=0;for(int k=0;k<3;k++)sum+=Math.pow(a[k]-b[k],2);return Math.sqrt(sum);}
}
