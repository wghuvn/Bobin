package com.bobincraft.armament;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

final class ApiBoundaryTest {
    @Test void commonSourceAndBytecodeHaveNoGameOrClientDependencies() throws Exception {
        Path root = Path.of("src/main/java/com/bobincraft/armament");
        int checked = 0;
        try (var files = Files.walk(root)) {
            for (Path source : files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !root.relativize(p).startsWith("client")).toList()) {
                String sourceText = Files.readString(source);
                assertFalse(sourceText.contains("import net.minecraft"), source.toString());
                assertFalse(sourceText.contains("import net.fabricmc"), source.toString());
                String className = "com/bobincraft/armament/" + root.relativize(source).toString().replace('\\', '/').replace(".java", ".class");
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(className)) {
                    assertNotNull(input, className);
                    String bytecode = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                    for (String forbidden : new String[]{"com/bobincraft/armament/client", "net/minecraft/", "net/fabricmc/", "org/lwjgl/",
                            "com/bobincraft/weapon/", "com/bobincraft/raid/", "com/bobincraft/drone/",
                            "com/bobincraft/vehicle/", "com/bobincraft/destruction/"})
                        assertFalse(bytecode.contains(forbidden), className + " references " + forbidden);
                }
                checked++;
            }
        }
        assertTrue(checked >= 10, "boundary check must inspect actual API classes");
    }
}
