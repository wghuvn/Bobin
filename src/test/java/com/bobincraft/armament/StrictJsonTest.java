package com.bobincraft.armament;

import com.bobincraft.armament.serialization.StrictJson;
import com.bobincraft.armament.validation.DefinitionValidationException;
import org.junit.jupiter.api.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

final class StrictJsonTest {
    @TestFactory Stream<DynamicTest> invalidSyntaxIsRejectedByParserItself() {
        return Stream.of("", "{} {}", "{\"x\":1,}", "{unquoted:1}", "{'x':1}",
                "{\"x\":NaN}", "{\"x\":Infinity}", "// comment\n{}", "/*comment*/{}", "{\"x\":01}",
                "{\"x\":1,\"x\":2}", "{\"a\":{\"x\":1,\"x\":2}}", "{\"x\":\"a\ntext\"}",
                "{\"x\":\"a\ttext\"}", "{\"x\":\"a\u0000text\"}",
                "{\"x\":\"a\\'text\"}", "TRUE", "FALSE", "NULL", "truE", "falsE", "nulL")
                .map(text -> DynamicTest.dynamicTest("strict syntax " + text, () ->
                        assertThrows(DefinitionValidationException.class, () -> StrictJson.parse(text))));
    }

    @Test void validJsonIsNotConfusedWithInvalidDefinitions() {
        for (String value : new String[]{"null", "true", "false", "[]", "{}", "1.5e+2", "1E2", "{\"text\":\"a\\n\\t\\u0000\\\"text\"}"})
            assertNotNull(StrictJson.parse(value));
    }
}
