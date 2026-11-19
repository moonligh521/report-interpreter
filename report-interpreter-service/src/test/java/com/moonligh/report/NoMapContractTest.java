package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** AC-P10：无 Map<String,Object> 契约；SQL 仅在 mapper xml。 */
class NoMapContractTest {

    private static final Pattern MAP_OBJECT = Pattern.compile("Map\\s*<\\s*String\\s*,\\s*Object\\s*>");
    private static final Pattern SQL_LITERAL = Pattern.compile(
            "\"\\s*(?i:select\\s+.+\\s+from\\s|insert\\s+into\\s|update\\s+\\w+\\s+set\\s|delete\\s+from\\s)");

    private static List<Path> javaFiles(String root) throws IOException {
        try (Stream<Path> s = Files.walk(Path.of(root))) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void noMapStringObjectInMainOrApi() throws IOException {
        for (String root : List.of("src/main/java", "../report-interpreter-api/src/main/java")) {
            for (Path p : javaFiles(root)) {
                assertFalse(MAP_OBJECT.matcher(Files.readString(p)).find(), p.toString());
            }
        }
    }

    @Test
    void noSqlLiteralsInJavaMain() throws IOException {
        for (Path p : javaFiles("src/main/java")) {
            assertFalse(SQL_LITERAL.matcher(Files.readString(p)).find(), p.toString());
        }
    }

    @Test
    void mapperXmlHasNoMapResultTypes() throws IOException {
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources/mapper"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".xml")).toList()) {
                String text = Files.readString(p);
                assertFalse(text.contains("java.util.HashMap"), p.toString());
                assertFalse(text.contains("java.util.Map"), p.toString());
            }
        }
    }
}