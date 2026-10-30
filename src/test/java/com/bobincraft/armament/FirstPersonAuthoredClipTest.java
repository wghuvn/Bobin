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

/** Check visible clip behavior and independent Blender times between GLB baked keys. */
final class FirstPersonAuthoredClipTest {
    private record Fixture(String name, FirstPersonAsset asset, JsonObject golden, int[][] mapping) {}
    private static final List<Fixture> fixtures = new ArrayList<>();

    @BeforeAll static void load() throws Exception {
        for (String suffix : List.of("", "_slim")) {
            String name = "first_person_fixture" + suffix;
            var asset = FirstPersonAsset.load(WeaponId.parse("bobin_craft:armament/debug/" + name + ".profile.json"),
                    id -> Files.newInputStream(FirstPersonBlenderTest.RESOURCES.resolve(id.namespace()).resolve(id.path())));
            var golden = JsonParser.parseString(Files.readString(Path.of("art/armament", name + ".reference.json"))).getAsJsonObject();
            var rest = new RigFrame(asset.model().rig());
            var bind = golden.getAsJsonObject("clips").getAsJsonArray("idle").get(0).getAsJsonObject().getAsJsonObject("vertices");
            int[][] mapping = new int[rest.model().meshes().size()][];
            for (int m = 0; m < mapping.length; m++) {
                var mesh = rest.model().meshes().get(m);
                var points = bind.getAsJsonArray(rest.model().nodes().get(mesh.node()).name());
                mapping[m] = new int[mesh.vertexCount()];
                for (int v = 0; v < mesh.vertexCount(); v++) {
                    double best = Double.POSITIVE_INFINITY;
                    for (int p = 0; p < points.size(); p++) {
                        double error = 0;
                        for (int k = 0; k < 3; k++) error = Math.max(error, Math.abs(rest.vertex(m, v, k) - points.get(p).getAsJsonArray().get(k).getAsDouble()));
                        if (error < best) { best = error; mapping[m][v] = p; }
                    }
                    assertTrue(best < 4e-5, "bind mesh must match Blender independently");
                }
            }
            fixtures.add(new Fixture(name, asset, golden, mapping));
        }
    }

    @TestFactory Stream<DynamicTest> offGridBlenderSamplesMatchImportedGlbWithinExistingTolerance() {
        return fixtures.stream().flatMap(f -> f.golden().getAsJsonObject("subframe_checks").entrySet().stream().flatMap(clip ->
                StreamSupport.stream(clip.getValue().getAsJsonArray().spliterator(), false).map(value ->
                        DynamicTest.dynamicTest(f.name() + " " + clip.getKey() + " @" + value.getAsJsonObject().get("seconds"), () -> {
                            var expected = value.getAsJsonObject();
                            var pose = controller(f, clip.getKey()).sampleSeconds(expected.get("seconds").getAsDouble());
                            var contacts = new GripAttachments(f.asset().profile(), f.asset().sockets());
                            assertTrue(contacts.matches(pose));
                            for (int m = 0; m < f.mapping().length; m++) {
                                var mesh = pose.model().meshes().get(m);
                                var points = expected.getAsJsonObject("vertices").getAsJsonArray(pose.model().nodes().get(mesh.node()).name());
                                for (int v = 0; v < mesh.vertexCount(); v++) for (int k = 0; k < 3; k++)
                                    assertEquals(points.get(f.mapping()[m][v]).getAsJsonArray().get(k).getAsDouble(), pose.vertex(m, v, k), 8e-5);
                            }
                        }))));
    }

    @TestFactory Stream<DynamicTest> breathingAndGaitAreVisibleLoopsWithStationarySeams() {
        return fixtures.stream().flatMap(f -> Stream.of("idle", "move").map(clip -> DynamicTest.dynamicTest(f.name() + " " + clip, () -> {
            var c = controller(f, clip);
            double duration = f.asset().model().rig().clip(clip).duration(), dt = 1.0 / 192;
            double[] start = positions(c.sampleSeconds(0));
            assertArrayEquals(start, positions(c.sampleSeconds(dt)), 1e-8);
            assertArrayEquals(start, positions(c.sampleSeconds(duration - dt)), 1e-8);
            assertArrayEquals(start, positions(c.sampleSeconds(duration)), 1e-8);
            double max = difference(start, positions(c.sampleSeconds(duration * (clip.equals("idle") ? .5 : .25))));
            assertTrue(max > .0005 && max < .03, "authored motion must be visible but restrained: " + max);
            c.play(clip, GltfAnimationController.Mode.LOOP, 0, 0);
            double[] first = positions(c.sampleSeconds(duration * .37));
            assertArrayEquals(first, positions(c.sampleSeconds(duration * 1.37)), 1e-12);
        })));
    }

    @TestFactory Stream<DynamicTest> drawAndStowAreFastAsymmetricAndMeetAtTheSameHeldPose() {
        return fixtures.stream().map(f -> DynamicTest.dynamicTest(f.name(), () -> {
            var rig = f.asset().model().rig();
            double raise = rig.clip("raise").duration(), lower = rig.clip("lower").duration();
            assertTrue(raise > .3 && raise < .8 && lower > .2 && lower < .65 && lower < raise);
            assertArrayEquals(positions(controller(f, "raise").sampleSeconds(0)), positions(controller(f, "lower").sampleSeconds(lower)), 2e-6);
            assertArrayEquals(positions(controller(f, "idle").sampleSeconds(0)), positions(controller(f, "raise").sampleSeconds(raise)), 2e-6);
        }));
    }

    @TestFactory Stream<DynamicTest> inspectionHasDistinctReadableHoldsAndReturnsToBind() {
        return fixtures.stream().map(f -> DynamicTest.dynamicTest(f.name(), () -> {
            var c = controller(f, "inspect");
            double[] first = positions(c.sampleSeconds(.7));
            assertArrayEquals(first, positions(c.sampleSeconds(.8)), 2e-6);
            double[] second = positions(c.sampleSeconds(1.5));
            assertArrayEquals(second, positions(c.sampleSeconds(1.65)), 2e-6);
            assertTrue(difference(first, second) > .01);
            assertArrayEquals(positions(controller(f, "idle").sampleSeconds(0)), positions(c.sampleSeconds(f.asset().model().rig().clip("inspect").duration())), 2e-6);
        }));
    }

    private static GltfAnimationController controller(Fixture f, String clip) {
        var c = new GltfAnimationController(f.asset().model().rig());
        c.play(clip, GltfAnimationController.Mode.HOLD, 0, 0);
        return c;
    }
    private static double[] positions(RigFrame pose) {
        int count = 0;
        for (var mesh : pose.model().meshes()) count += mesh.vertexCount() * 3;
        double[] values = new double[count];
        int at = 0;
        for (int m = 0; m < pose.model().meshes().size(); m++)
            for (int v = 0; v < pose.model().meshes().get(m).vertexCount(); v++)
                for (int k = 0; k < 3; k++) values[at++] = pose.vertex(m, v, k);
        return values;
    }
    private static double difference(double[] a, double[] b) {
        double max = 0;
        for (int i = 0; i < a.length; i++) max = Math.max(max, Math.abs(a[i] - b[i]));
        return max;
    }
}
