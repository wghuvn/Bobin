package com.bobincraft.armament;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.resource.*;
import com.bobincraft.armament.validation.DefinitionValidationException;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class VisualContractTest {
    private static final WeaponId RIG = WeaponId.parse("test:rig");

    private static String fixture(String type) throws IOException {
        for (JsonElement element : DefinitionValidationTest.cases()) {
            JsonObject c = element.getAsJsonObject();
            if (c.get("name").getAsString().equals(type + " valid")) return c.get("document").toString();
        }
        throw new AssertionError(type);
    }

    @Test void bundleChecksAllReferencesAndSocketNodes() throws IOException {
        WeaponVisualDefinition visual = WeaponVisualJson.readVisual(fixture("visual"));
        WeaponRigDefinition rig = WeaponVisualJson.readRig(fixture("rig"));
        WeaponSocketsDefinition sockets = WeaponVisualJson.readSockets(fixture("sockets"));
        AnimationEventsDefinition events = WeaponVisualJson.readEvents(fixture("animation-events"));
        assertDoesNotThrow(() -> WeaponVisualValidator.validate(visual, rig, sockets, events));
        assertThrows(DefinitionValidationException.class, () -> WeaponVisualValidator.validate(visual,
                new WeaponRigDefinition(1, WeaponId.parse("test:wrong"), rig.nodes(), rig.roles()), sockets, events));
        assertThrows(DefinitionValidationException.class, () -> WeaponVisualValidator.validate(visual, rig,
                new WeaponSocketsDefinition(1, sockets.id(), WeaponId.parse("test:wrong"), sockets.sockets()), events));
        assertThrows(DefinitionValidationException.class, () -> WeaponVisualValidator.validate(visual, rig,
                new WeaponSocketsDefinition(1, WeaponId.parse("test:wrong"), RIG, sockets.sockets()), events));
        assertThrows(DefinitionValidationException.class, () -> WeaponVisualValidator.validate(visual, rig, sockets,
                new AnimationEventsDefinition(1, WeaponId.parse("test:wrong"), List.of())));
        assertThrows(DefinitionValidationException.class, () -> WeaponVisualValidator.validate(visual, rig,
                new WeaponSocketsDefinition(1, sockets.id(), RIG,
                        List.of(new WeaponSocketsDefinition.Socket("grip", "unknown", sockets.sockets().get(0).transform()))), events));
    }

    @Test void nestedCollectionsAreDefensivelyCopied() {
        List<Double> translation = new ArrayList<>(List.of(0.0, 0.0, 0.0));
        TransformDefinition t = new TransformDefinition(translation, List.of(0.0, 0.0, 0.0, 1.0), List.of(1.0, 1.0, 1.0));
        translation.set(0, 50.0);
        assertEquals(0.0, t.translation().get(0));
        assertThrows(UnsupportedOperationException.class, () -> t.translation().clear());
        List<WeaponRigDefinition.Node> nodes = new ArrayList<>(List.of(new WeaponRigDefinition.Node("root", 0, Optional.empty())));
        Map<String, String> roles = new HashMap<>(Map.of("weapon_root", "root"));
        WeaponRigDefinition rig = new WeaponRigDefinition(1, RIG, nodes, roles);
        nodes.clear(); roles.clear();
        assertEquals(1, rig.nodes().size());
        assertEquals("root", rig.roles().get("weapon_root"));
        assertThrows(UnsupportedOperationException.class, () -> rig.roles().clear());
        assertThrows(UnsupportedOperationException.class, () -> rig.nodes().clear());
    }

    @Test void constructorsAlsoRejectNonFiniteDataAndInvalidReferences() {
        assertThrows(DefinitionValidationException.class, () -> new TransformDefinition(List.of(Double.NaN, 0.0, 0.0),
                List.of(0.0, 0.0, 0.0, 1.0), List.of(1.0, 1.0, 1.0)));
        assertThrows(DefinitionValidationException.class, () -> new AnimationEventsDefinition.Marker("cue", Double.POSITIVE_INFINITY, RIG));
        assertThrows(DefinitionValidationException.class, () -> new WeaponRigDefinition.Node("root", 0, null));
        assertThrows(DefinitionValidationException.class, () -> new WeaponSocketsDefinition(1, RIG, RIG, Arrays.asList((WeaponSocketsDefinition.Socket) null)));
        assertThrows(DefinitionValidationException.class, () -> new AnimationEventsDefinition(1, RIG, Arrays.asList((AnimationEventsDefinition.Clip) null)));
    }
}
