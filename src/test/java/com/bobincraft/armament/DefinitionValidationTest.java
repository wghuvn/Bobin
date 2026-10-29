package com.bobincraft.armament;

import com.bobincraft.armament.client.resource.*;
import com.bobincraft.armament.serialization.*;
import com.bobincraft.armament.validation.DefinitionValidationException;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

final class DefinitionValidationTest {
    static JsonArray cases() throws IOException {
        try (InputStream input = DefinitionValidationTest.class.getResourceAsStream("/armament/metadata-cases.json")) {
            assertNotNull(input, "metadata fixture is missing");
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }

    @TestFactory Stream<DynamicTest> definitionsAcceptValidDataAndRejectInvalidData() throws IOException {
        return StreamSupport.stream(cases().spliterator(), false).map(JsonElement::getAsJsonObject).map(c ->
                DynamicTest.dynamicTest(c.get("name").getAsString(), () -> {
                    String type = c.get("schema").getAsString();
                    String text = c.get("document").toString();
                    if (c.get("valid").getAsBoolean()) assertNotNull(parse(type, text));
                    else {
                        var error = assertThrows(DefinitionValidationException.class, () -> parse(type, text));
                        assertTrue(error.getMessage().startsWith("$"), error.getMessage());
                        assertTrue(error.getMessage().contains(": "), error.getMessage());
                    }
                }));
    }

    @TestFactory Stream<DynamicTest> validMetadataRoundTrips() throws IOException {
        return StreamSupport.stream(cases().spliterator(), false).map(JsonElement::getAsJsonObject)
                .filter(c -> c.get("valid").getAsBoolean()).map(c -> DynamicTest.dynamicTest(c.get("name").getAsString(), () -> {
                    String type = c.get("schema").getAsString();
                    Object value = parse(type, c.get("document").toString());
                    String encoded = write(type, value);
                    assertEquals(value, parse(type, encoded));
                    assertEquals(encoded, write(type, parse(type, encoded)), "serialization must be stable");
                }));
    }

    @TestFactory Stream<DynamicTest> malformedJsonIsNeverSilentlyAccepted() {
        return Stream.of("", "null", "[]", "{} {}", "{\"x\":1,}", "{unquoted:1}", "{'x':1}",
                "{\"x\":NaN}", "{\"x\":Infinity}", "// comment\n{}", "/*comment*/{}", "{\"x\":01}",
                "{\"x\":1,\"x\":2}", "{\"a\":{\"x\":1,\"x\":2}}", "{\"x\":\"a\ntext\"}")
                .map(text -> DynamicTest.dynamicTest("reject " + text, () ->
                        assertThrows(DefinitionValidationException.class, () -> WeaponJson.readDefinition(text))));
    }

    @Test void deeplyNestedAndOversizedDocumentsAreRejected() {
        assertThrows(DefinitionValidationException.class, () -> StrictJson.parse("[".repeat(66) + "0" + "]".repeat(66)));
        assertThrows(DefinitionValidationException.class, () -> StrictJson.parse(" ".repeat(1_048_577)));
    }

    @Test void errorIdentifiesNestedField() throws IOException {
        JsonObject c = StreamSupport.stream(cases().spliterator(), false).map(JsonElement::getAsJsonObject)
                .filter(v -> v.get("name").getAsString().equals("string vector component")).findFirst().orElseThrow();
        var e = assertThrows(DefinitionValidationException.class, () -> WeaponVisualJson.readVisual(c.get("document").toString()));
        assertTrue(e.getMessage().startsWith("$.views.npc.translation[0]:"), e.getMessage());
    }

    static Object parse(String schema, String text) {
        return switch (schema) {
            case "weapon" -> WeaponJson.readDefinition(text);
            case "visual" -> WeaponVisualJson.readVisual(text);
            case "rig" -> WeaponVisualJson.readRig(text);
            case "sockets" -> WeaponVisualJson.readSockets(text);
            case "animation-events" -> WeaponVisualJson.readEvents(text);
            default -> throw new AssertionError(schema);
        };
    }

    private static String write(String schema, Object value) {
        return switch (schema) {
            case "weapon" -> WeaponJson.writeDefinition((com.bobincraft.armament.definition.WeaponDefinition) value);
            case "visual" -> WeaponVisualJson.writeVisual((WeaponVisualDefinition) value);
            case "rig" -> WeaponVisualJson.writeRig((WeaponRigDefinition) value);
            case "sockets" -> WeaponVisualJson.writeSockets((WeaponSocketsDefinition) value);
            case "animation-events" -> WeaponVisualJson.writeEvents((AnimationEventsDefinition) value);
            default -> throw new AssertionError(schema);
        };
    }
}
