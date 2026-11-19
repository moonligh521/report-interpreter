package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

import com.moonligh.report.infrastructure.persistence.ReportInterpretationBlockMapper;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationBlockRow;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationEvidenceMapper;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationEvidenceRow;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationMapper;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationRow;

/** AC-G10：解读 / 块 / 依据 mapper 的 SQL 仅在 xml、行对象强类型；五类版本随解读落库。 */
@SpringBootTest
@ActiveProfiles("test")
class MapperXmlInterpretationTest {

    private static final List<String> XMLS = List.of(
            "ReportInterpretationMapper.xml", "ReportInterpretationBlockMapper.xml",
            "ReportInterpretationEvidenceMapper.xml");

    @Autowired
    private ReportInterpretationMapper interpretations;

    @Autowired
    private ReportInterpretationBlockMapper blocks;

    @Autowired
    private ReportInterpretationEvidenceMapper evidence;

    private static ReportInterpretationRow row(String id, String reportId, String key, Integer code) {
        return new ReportInterpretationRow(
                id, reportId, 1, key, "VALID", code, "kb-v1", "rules-v1.0.0", "prompt-v1", "parser-v1", "schema-v1", null);
    }

    @Test
    void xmlFilesExistWithoutMapTypesAndWithTypedRows() throws Exception {
        for (String name : XMLS) {
            String text = Files.readString(Path.of("src/main/resources/mapper/" + name));
            assertFalse(text.contains("java.util.HashMap"), name);
            assertFalse(text.contains("java.util.Map"), name);
            assertTrue(text.contains("Row\""), name);
        }
        String main = Files.readString(Path.of("src/main/resources/mapper/ReportInterpretationMapper.xml"));
        for (String id : List.of("insert", "findById", "findByIdempotency", "findLatestByReport")) {
            assertTrue(main.contains("id=\"" + id + "\""), id);
        }
        for (String col : List.of("kb_version", "rule_version", "prompt_version", "parser_version", "schema_version")) {
            assertTrue(main.contains(col), col);
        }
        assertTrue(Files.readString(Path.of("src/main/resources/mapper/ReportInterpretationEvidenceMapper.xml"))
                .contains("id=\"batchInsert\""));
    }

    @Test
    void interpretationRoundTripKeepsAllFivePinnedVersions() {
        interpretations.insert(row("itp-map-1", "rpt-itp-map", "k-1", 4105));
        ReportInterpretationRow found = interpretations.findById("itp-map-1");
        assertEquals("kb-v1", found.kbVersion());
        assertEquals("rules-v1.0.0", found.ruleVersion());
        assertEquals("prompt-v1", found.promptVersion());
        assertEquals("parser-v1", found.parserVersion());
        assertEquals("schema-v1", found.schemaVersion());
        assertEquals(4105, found.resultCode());
        assertNotNull(found.createdAt());
        assertEquals("itp-map-1", interpretations.findByIdempotency("rpt-itp-map", "k-1").interpretationId());
        assertNull(interpretations.findByIdempotency("rpt-itp-map", "nope"));
        assertNull(interpretations.findById("none"));
    }

    @Test
    void nullResultCodeMeansSuccess() {
        interpretations.insert(row("itp-map-ok", "rpt-itp-ok", "k-ok", null));
        assertNull(interpretations.findById("itp-map-ok").resultCode());
    }

    @Test
    void idempotencyKeyIsUniquePerReport() {
        interpretations.insert(row("itp-map-u1", "rpt-itp-u", "k-u", null));
        assertThrows(DuplicateKeyException.class,
                () -> interpretations.insert(row("itp-map-u2", "rpt-itp-u", "k-u", null)));
        interpretations.insert(row("itp-map-u3", "rpt-itp-other", "k-u", null));
    }

    @Test
    void latestByReportReturnsNewest() throws Exception {
        interpretations.insert(row("itp-map-l1", "rpt-itp-latest", "k-l1", null));
        Thread.sleep(5);
        interpretations.insert(row("itp-map-l2", "rpt-itp-latest", "k-l2", null));
        assertEquals("itp-map-l2", interpretations.findLatestByReport("rpt-itp-latest").interpretationId());
        assertNull(interpretations.findLatestByReport("rpt-none"));
    }

    @Test
    void blocksAreReturnedInSequenceOrder() {
        blocks.batchInsert(List.of(
                new ReportInterpretationBlockRow("ibk-2", "itp-blk", 1, "b2", "FACT", "第二块", "evd-a,evd-b", "VALID"),
                new ReportInterpretationBlockRow("ibk-1", "itp-blk", 0, "b1", "CRITICAL_ALERT", "第一块", "", "VALID")));
        List<ReportInterpretationBlockRow> list = blocks.listByInterpretation("itp-blk");
        assertEquals(List.of("b1", "b2"), list.stream().map(ReportInterpretationBlockRow::blockId).toList());
        assertEquals("evd-a,evd-b", list.get(1).evidenceIds());
        assertTrue(blocks.listByInterpretation("none").isEmpty());
    }

    @Test
    void evidenceBatchInsertAndUniquePerInterpretation() {
        evidence.batchInsert(List.of(
                new ReportInterpretationEvidenceRow("iev-1", "itp-evd", "evd-1", "doc-1", "标题", "片段", "https://x.invalid/1"),
                new ReportInterpretationEvidenceRow("iev-2", "itp-evd", "evd-2", "doc-2", "标题2", "片段2", null)));
        List<ReportInterpretationEvidenceRow> list = evidence.listByInterpretation("itp-evd");
        assertEquals(2, list.size());
        assertEquals("doc-1", list.get(0).docId());
        assertThrows(DuplicateKeyException.class, () -> evidence.batchInsert(List.of(
                new ReportInterpretationEvidenceRow("iev-3", "itp-evd", "evd-1", "doc-1", "t", "s", null))));
    }
}
