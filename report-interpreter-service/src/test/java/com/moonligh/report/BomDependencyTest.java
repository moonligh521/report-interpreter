package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 禁止依赖兄弟业务仓 jar / 包。
 */
class BomDependencyTest {

    @Test
    void servicePomHasNoSiblingBusinessJars() throws IOException {
        Path pom = Path.of("pom.xml");
        String text = Files.readString(pom);
        assertFalse(text.contains("nutritionist"), text);
        assertFalse(text.contains("registration-assistant"), text);
        assertFalse(text.contains("clinical-record"), text);
        assertFalse(text.contains("consult-registration"), text);
        assertTrue(text.contains("platform-common"), text);
        assertTrue(text.contains("platform-security"), text);
    }

    @Test
    void sourceHasNoSiblingBusinessPackages() throws IOException {
        Path root = Path.of("src/main/java");
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String text = Files.readString(path);
                    assertFalse(text.contains("com.moonligh.nutritionist"), path.toString());
                    assertFalse(text.contains("com.moonligh.clinicalrecord"), path.toString());
                    assertFalse(text.contains("com.moonligh.consult.agent"), path.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
