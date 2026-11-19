package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportCriticalEventMapper;
import com.moonligh.report.infrastructure.persistence.ReportCriticalEventRow;
import com.moonligh.report.infrastructure.persistence.ReportCriticalNotificationOutboxMapper;
import com.moonligh.report.infrastructure.persistence.ReportReviewTaskMapper;
import com.moonligh.report.infrastructure.persistence.ReportReviewTaskRow;
import com.moonligh.report.infrastructure.persistence.ReportRuleExecutionMapper;
import com.moonligh.report.infrastructure.persistence.ReportRuleExecutionRow;
import com.moonligh.report.infrastructure.persistence.ReportRuleSummaryMapper;
import com.moonligh.report.infrastructure.persistence.ReportRuleSummaryRow;

/** AC-R11：规则域 SQL 仅在 mapper xml、行对象强类型（无 Map）；各 mapper 语句可执行。 */
@SpringBootTest
@ActiveProfiles("test")
class RuleMappersXmlTest {

    private static final List<String> XMLS = List.of(
            "ReportRuleExecutionMapper.xml", "ReportCriticalEventMapper.xml",
            "ReportCriticalNotificationOutboxMapper.xml", "ReportReviewTaskMapper.xml",
            "ReportRuleSummaryMapper.xml");

    @Autowired
    private ReportRuleExecutionMapper executions;

    @Autowired
    private ReportCriticalEventMapper events;

    @Autowired
    private ReportCriticalNotificationOutboxMapper outbox;

    @Autowired
    private ReportReviewTaskMapper reviews;

    @Autowired
    private ReportRuleSummaryMapper summaries;

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Test
    void xmlFilesExistWithoutMapTypesAndWithTypedRows() throws Exception {
        for (String name : XMLS) {
            String text = Files.readString(Path.of("src/main/resources/mapper/" + name));
            assertFalse(text.contains("java.util.HashMap"), name);
            assertFalse(text.contains("java.util.Map"), name);
            assertTrue(text.contains("Row\""), name);
            assertTrue(text.contains("id=\"insert\""), name);
        }
        String outboxXml = Files.readString(
                Path.of("src/main/resources/mapper/ReportCriticalNotificationOutboxMapper.xml"));
        assertTrue(outboxXml.contains("id=\"updateStatus\""));
        assertTrue(outboxXml.contains("id=\"listRetryDue\""));
        String eventXml = Files.readString(Path.of("src/main/resources/mapper/ReportCriticalEventMapper.xml"));
        assertTrue(eventXml.contains("id=\"findByIdempotency\""));
        assertTrue(eventXml.contains("id=\"listByReport\""));
    }

    @Test
    void ruleExecutionInsertAndList() {
        String reportId = "rpt-map-exec";
        executions.insert(new ReportRuleExecutionRow(
                "rex-1", reportId, 1, "rules-v1.0.0", "ind-1", "K", "REF-STD-K", "CRIT-K-HIGH",
                "{\"canonicalCode\":\"K\"}", "HIGH", "CRITICAL_HIGH", "APPLICABLE", null));
        List<ReportRuleExecutionRow> rows = executions.listByReport(reportId);
        assertEquals(1, rows.size());
        assertEquals("{\"canonicalCode\":\"K\"}", rows.get(0).contextSnapshotJson());
        assertEquals("CRITICAL_HIGH", rows.get(0).criticalResult());
        assertEquals(1, rows.get(0).parseVersion());
        assertNotNull(rows.get(0).createdAt());
        assertTrue(executions.listByReport("rpt-none").isEmpty());
    }

    @Test
    void criticalEventInsertListAndIdempotencyUniqueness() {
        String reportId = "rpt-map-evt";
        ReportCriticalEventRow row = new ReportCriticalEventRow(
                "evt-map-1", reportId, 1, "K", "HIGH", "DETECTED", now(), "idem-map-evt-1");
        events.insert(row);
        assertEquals(row, events.findByIdempotency("idem-map-evt-1"));
        assertEquals(row, events.findById("evt-map-1"));
        assertEquals(List.of(row), events.listByReport(reportId));
        assertNull(events.findByIdempotency("nope"));
        assertThrows(DuplicateKeyException.class, () -> events.insert(new ReportCriticalEventRow(
                "evt-map-2", reportId, 2, "K", "HIGH", "DETECTED", now(), "idem-map-evt-1")));
    }

    @Test
    void outboxInsertUpdateAndRetryDueSelect() {
        Instant t = now();
        outbox.insert(new CriticalNotificationOutboxRow(
                "obx-map-1", "evt-x", "RETRYING", "IN_APP", "idem-obx-1", 1, t.minusSeconds(5), "E1", null));
        outbox.insert(new CriticalNotificationOutboxRow(
                "obx-map-2", "evt-y", "RETRYING", "IN_APP", "idem-obx-2", 1, t.plusSeconds(600), "E1", null));
        outbox.insert(new CriticalNotificationOutboxRow(
                "obx-map-3", "evt-z", "ACCEPTED", "IN_APP", "idem-obx-3", 1, null, null, t));

        List<String> due = outbox.listRetryDue(t, 1000).stream().map(CriticalNotificationOutboxRow::outboxId).toList();
        assertTrue(due.contains("obx-map-1"));
        assertFalse(due.contains("obx-map-2"));
        assertFalse(due.contains("obx-map-3"));
        assertEquals(1, outbox.listRetryDue(t, 1).size());

        outbox.updateStatus(new CriticalNotificationOutboxRow(
                "obx-map-1", "evt-x", "ACCEPTED", "IN_APP", "idem-obx-1", 2, null, null, t));
        CriticalNotificationOutboxRow updated = outbox.findById("obx-map-1");
        assertEquals("ACCEPTED", updated.notificationStatus());
        assertEquals(2, updated.attemptCount());
        assertEquals(t, updated.deliveredAt());
        assertEquals("obx-map-2", outbox.findByEvent("evt-y").outboxId());
    }

    @Test
    void reviewTaskInsertAndFind() {
        reviews.insert(new ReportReviewTaskRow(
                "rvw-map-1", "rpt-map-rvw", "覆盖未知", "HIGH", "OPEN", "idem-rvw-map-1", null));
        ReportReviewTaskRow row = reviews.findByIdempotency("idem-rvw-map-1");
        assertEquals("rvw-map-1", row.reviewTaskId());
        assertEquals("OPEN", row.status());
        assertEquals("覆盖未知", row.reason());
        assertNotNull(row.createdAt());
        assertEquals("HIGH", reviews.findById("rvw-map-1").priority());
    }

    @Test
    void ruleSummaryInsertAndFind() {
        summaries.insert(new ReportRuleSummaryRow(
                "rpt-map-sum", 1, "rules-v1.0.0", "MISSING", "报告中未识别到身份信息", "UNKNOWN",
                "{\"pagesProcessed\":[]}", "NEED_MANUAL_REVIEW", true, false, 4110, null));
        ReportRuleSummaryRow row = summaries.find("rpt-map-sum", 1);
        assertEquals("MISSING", row.identityStatus());
        assertEquals("UNKNOWN", row.coverageStatus());
        assertTrue(row.archiveBlocked());
        assertFalse(row.safeNormalAllowed());
        assertEquals(4110, row.blockCode());
        assertNull(summaries.find("rpt-map-sum", 2));
    }
}