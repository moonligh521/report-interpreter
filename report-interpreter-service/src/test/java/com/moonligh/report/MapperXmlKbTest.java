package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.moonligh.report.infrastructure.persistence.KbChunkRow;
import com.moonligh.report.infrastructure.persistence.KbDocumentRow;
import com.moonligh.report.infrastructure.persistence.ReportKbChunkMapper;
import com.moonligh.report.infrastructure.persistence.ReportKbDocumentMapper;

/** AC-G10：KB mapper 的 SQL 仅在 xml、行对象强类型（无 Map）；PUBLISHED / 生效期 / 结构化过滤由 SQL 下推。 */
@SpringBootTest
@ActiveProfiles("test")
class MapperXmlKbTest {

    private static final Pattern MAP_TYPE = Pattern.compile("\\bMap\\s*<|java\\.util\\.(Hash)?Map|import\\s+java\\.util\\.Map");

    @Autowired
    private ReportKbDocumentMapper documents;

    @Autowired
    private ReportKbChunkMapper chunks;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        InterpretationTestSupport.clearKb(jdbc);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private void doc(String id, String version, String status, Instant from, Instant to) {
        documents.insert(new KbDocumentRow(id, "标题-" + id, version, status, "A", "https://x.invalid/" + id, from, to, null, null));
    }

    private void chunk(String id, String docId, String code, String specialty) {
        chunks.insert(new KbChunkRow(id, docId, "内容-" + id, "标题", code, specialty, null, null, null));
    }

    @Test
    void xmlDeclaresExpectedStatementsWithTypedRowsAndNoMap() throws Exception {
        String d = Files.readString(Path.of("src/main/resources/mapper/ReportKbDocumentMapper.xml"));
        String c = Files.readString(Path.of("src/main/resources/mapper/ReportKbChunkMapper.xml"));
        for (String id : List.of("insert", "update", "findById", "maxPublishedKbVersion")) {
            assertTrue(d.contains("id=\"" + id + "\""), id);
        }
        for (String id : List.of("insert", "deleteByDoc", "listPublishedChunks")) {
            assertTrue(c.contains("id=\"" + id + "\""), id);
        }
        assertTrue(d.contains("KbDocumentRow"));
        assertTrue(c.contains("KbChunkRow"));
        for (String text : List.of(d, c)) {
            assertFalse(text.contains("java.util.HashMap"));
            assertFalse(text.contains("java.util.Map"));
        }
        // 规格中的结构化过滤与 PUBLISHED 约束必须在 xml 内。
        assertTrue(c.contains("d.status = 'PUBLISHED'"));
        assertTrue(c.contains("c.indicator_code = #{indicatorCode}"));
        assertTrue(c.contains("c.specialty = #{specialty}"));
        assertTrue(c.contains("d.effective_from &lt;= #{now}"));
    }

    @Test
    void onlyPublishedAndEffectiveChunksAreListed() {
        Instant t = now();
        doc("d-pub", "v1", "PUBLISHED", t.minus(1, ChronoUnit.DAYS), null);
        doc("d-draft", "v2", "DRAFT", t.minus(1, ChronoUnit.DAYS), null);
        doc("d-retired", "v3", "RETIRED", t.minus(1, ChronoUnit.DAYS), null);
        doc("d-future", "v4", "PUBLISHED", t.plus(1, ChronoUnit.DAYS), null);
        doc("d-expired", "v5", "PUBLISHED", t.minus(10, ChronoUnit.DAYS), t.minus(1, ChronoUnit.DAYS));
        for (String d : List.of("d-pub", "d-draft", "d-retired", "d-future", "d-expired")) {
            chunk("c-" + d, d, "K", "LAB");
        }
        List<String> ids = chunks.listPublishedChunks(t, null, null).stream().map(KbChunkRow::chunkId).toList();
        assertEquals(List.of("c-d-pub"), ids);
        assertEquals("v1", documents.maxPublishedKbVersion(t));
    }

    @Test
    void filtersArePushedIntoSqlAndNeverFallOpen() {
        Instant t = now();
        doc("d1", "v1", "PUBLISHED", t.minus(1, ChronoUnit.DAYS), null);
        chunk("c-k", "d1", "K", "LAB");
        chunk("c-glu", "d1", "GLU", "LAB");
        chunk("c-img", "d1", "K", "IMAGING");

        assertEquals(List.of("c-img", "c-k"),
                chunks.listPublishedChunks(t, "K", null).stream().map(KbChunkRow::chunkId).toList());
        assertEquals(List.of("c-k"),
                chunks.listPublishedChunks(t, "K", "LAB").stream().map(KbChunkRow::chunkId).toList());
        assertEquals(3, chunks.listPublishedChunks(t, null, null).size());
        assertEquals(3, chunks.listPublishedChunks(t, "", "").size());
        assertTrue(chunks.listPublishedChunks(t, "ZZZ", "LAB").isEmpty());
        assertTrue(chunks.listPublishedChunks(t, "K", "OTHER").isEmpty());
        KbChunkRow row = chunks.listPublishedChunks(t, "GLU", null).get(0);
        assertEquals("v1", row.kbVersion());
        assertEquals("A", row.authorityLevel());
    }

    @Test
    void documentUpdateAndChunkReplaceRoundTrip() {
        Instant t = now();
        doc("d-up", "v1", "DRAFT", t, null);
        assertEquals(1, documents.update(new KbDocumentRow(
                "d-up", "新标题", "v2", "PUBLISHED", "B", null, t, null, null, null)));
        KbDocumentRow found = documents.findById("d-up");
        assertEquals("新标题", found.title());
        assertEquals("v2", found.kbVersion());
        assertEquals("PUBLISHED", found.status());
        assertNotNull(found.createdAt());
        assertEquals(0, documents.update(new KbDocumentRow(
                "d-none", "x", "v", "DRAFT", "A", null, t, null, null, null)));
        assertNull(documents.findById("d-none"));

        chunk("c-a", "d-up", "K", "LAB");
        chunk("c-b", "d-up", "K", "LAB");
        assertEquals(2, chunks.countByDoc("d-up"));
        assertEquals(2, chunks.deleteByDoc("d-up"));
        assertEquals(0, chunks.countByDoc("d-up"));
        assertEquals(1, documents.countAll());
    }

    @Test
    void newRagGuardSourcesDoNotUseMapAndHoldNoSqlLiterals() throws IOException {
        Pattern sql = Pattern.compile(
                "\"\\s*(?i:select\\s+.+\\s+from\\s|insert\\s+into\\s|update\\s+\\w+\\s+set\\s|delete\\s+from\\s)");
        List<String> roots = List.of(
                "src/main/java/com/moonligh/report/domain/kb", "src/main/java/com/moonligh/report/domain/interpret",
                "src/main/java/com/moonligh/report/infrastructure/model",
                "../report-interpreter-api/src/main/java/com/moonligh/report/api");
        for (String root : roots) {
            try (Stream<Path> s = Files.walk(Path.of(root))) {
                for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(p);
                    assertFalse(MAP_TYPE.matcher(text).find(), p.toString());
                    assertFalse(sql.matcher(text).find(), p.toString());
                }
            }
        }
        for (String f : List.of(
                "service/ReportInterpretationService.java", "service/KbAdminService.java",
                "controller/InterpretationController.java", "infrastructure/persistence/KbStoreImpl.java",
                "infrastructure/persistence/InterpretationStoreImpl.java")) {
            String text = Files.readString(Path.of("src/main/java/com/moonligh/report/" + f));
            assertFalse(MAP_TYPE.matcher(text).find(), f);
            assertFalse(sql.matcher(text).find(), f);
        }
    }
}
