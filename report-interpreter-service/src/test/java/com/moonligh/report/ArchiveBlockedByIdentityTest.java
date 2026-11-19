package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.msg.ArchiveOutboxMessageV2;
import com.moonligh.report.domain.archive.ArchiveConsumer;
import com.moonligh.report.domain.archive.ArchiveDeliveryResult;
import com.moonligh.report.domain.archive.ArchiveGate;
import com.moonligh.report.domain.archive.ArchiveGate.Decision;
import com.moonligh.report.domain.archive.ArchiveOutboxManager;
import com.moonligh.report.domain.archive.ArchiveRecordStore;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.domain.parse.ReportParseStore;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.service.ArchiveOutboxService;

/**
 * AC-D7 / D-R5：只有 MATCHED 身份才写可投递档案；CONFLICT / MISSING 不写；规则阻断（覆盖未知）也不写。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchiveBlockedByIdentityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ArchiveOutboxService archive;

    @Autowired
    private ArchiveOutboxManager manager;

    @Autowired
    private ArchiveConsumer consumer;

    @Autowired
    private ArchiveRecordStore records;

    @Autowired
    private ReportJobStore jobs;

    @Autowired
    private ReportParseStore parses;

    private int count(String table, String reportId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE report_id = ?", Integer.class, reportId);
    }

    private void assertNothingWritten(String reportId) {
        assertEquals(0, count("report_interpretation", reportId));
        assertEquals(0, count("report_archive_outbox", reportId));
        assertEquals(0, count("report_event", reportId));
        assertEquals(0, count("report_archive_record", reportId));
    }

    @Test
    void gateAllowsOnlyMatchedUnblockedAndCompleted() {
        assertEquals(Decision.ALLOWED, ArchiveGate.decide("MATCHED", false, true));
        assertEquals(Decision.BLOCKED_IDENTITY, ArchiveGate.decide("CONFLICT", false, true));
        assertEquals(Decision.BLOCKED_IDENTITY, ArchiveGate.decide("MISSING", false, true));
        assertEquals(Decision.BLOCKED_IDENTITY, ArchiveGate.decide(null, false, true));
        assertEquals(Decision.BLOCKED_RULES, ArchiveGate.decide("MATCHED", true, true));
        assertEquals(Decision.INTERPRETATION_NOT_COMPLETED, ArchiveGate.decide("MATCHED", false, false));
    }

    @Test
    void identityConflictWritesNoInterpretationNoEventNoArchive() throws Exception {
        String account = ReportTestSupport.uniq("acc-arc-conflict");
        String reportId = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", "李四")
                .path("data").path("reportId").asText();

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(ErrorCode.REPORT_IDENTITY_CONFLICT.getCode(), res.path("code").asInt());
        assertNothingWritten(reportId);
        assertTrue(DeliveryTestSupport.stream(mvc, account, reportId, null).isEmpty());
    }

    @Test
    void identityMissingWritesNoInterpretationNoEventNoArchive() throws Exception {
        String account = ReportTestSupport.uniq("acc-arc-missing");
        String reportId = ReportTestSupport.createRules(mvc, account, "rules-critical-k-no-identity.lab.txt", "张三")
                .path("data").path("reportId").asText();

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(ErrorCode.REPORT_IDENTITY_MISSING.getCode(), res.path("code").asInt());
        assertNothingWritten(reportId);
    }

    @Test
    void enqueueIsRefusedForNonMatchedIdentityEvenWhenCalledDirectly() throws Exception {
        String account = ReportTestSupport.uniq("acc-arc-direct");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");
        var job = jobs.findById(reportId).orElseThrow();
        var parse = parses.findActive(reportId).orElseThrow();

        for (String identity : List.of("CONFLICT", "MISSING")) {
            assertTrue(archive.enqueueIfAllowed(job, parse, identity, true, "itp_direct_" + identity, true).isEmpty());
            assertTrue(archive.enqueueIfAllowed(job, parse, identity, false, "itp_direct_" + identity, true).isEmpty());
        }
        assertEquals(0, count("report_archive_outbox", reportId));
    }

    @Test
    void coverageUnknownInterpretsButNeverWritesDeliverableArchive() throws Exception {
        String account = ReportTestSupport.uniq("acc-arc-coverage");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-coverage-unknown.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        assertEquals(1, count("report_interpretation", reportId));
        assertEquals(0, count("report_archive_outbox", reportId));
        JsonNode life = DeliveryTestSupport.events(mvc, account, reportId).path("data").path("lifecycle");
        assertEquals("COMPLETED", life.path("interpretationStatus").asText());
        assertEquals("BLOCKED", life.path("archiveStatus").asText());
        List<DeliveryTestSupport.SseEvent> events = DeliveryTestSupport.stream(mvc, account, reportId, null);
        assertEquals("BLOCKED", events.get(events.size() - 1).data().path("payload").path("archiveStatus").asText());
    }

    @Test
    void outboxHoldingANonMatchedMessageIsNeverSentAndDiesImmediately() {
        String reportId = ReportTestSupport.uniq("rpt-arc-nm");
        ArchiveOutboxMessageV2 msg = new ArchiveOutboxMessageV2(
                "arc_" + ReportTestSupport.uniq("nm"), ArchiveOutboxManager.SCHEMA_VERSION, reportId, "p-1", 1,
                "itp_nm", List.of(), "CONFLICT", "LAB_PANEL", List.of(), null);
        ReportArchiveOutboxRow row = manager.enqueue(msg);
        List<ArchiveOutboxMessageV2> sent = new ArrayList<>();

        ReportArchiveOutboxRow after = manager.dispatch(row.outboxId(), m -> {
            sent.add(m);
            return ArchiveDeliveryResult.ok();
        });

        assertTrue(sent.isEmpty());
        assertEquals("DEAD", after.status());
        assertEquals(ArchiveOutboxManager.ERR_IDENTITY, after.lastErrorCode());
    }

    @Test
    void consumerRejectsNonMatchedAndWritesNoRecord() {
        String eventId = "arc_" + ReportTestSupport.uniq("rej");
        for (String identity : List.of("CONFLICT", "MISSING")) {
            ArchiveOutboxMessageV2 msg = new ArchiveOutboxMessageV2(
                    eventId, ArchiveOutboxManager.SCHEMA_VERSION, "rpt-rej", "p-1", 1, "itp_rej", List.of(), identity,
                    "LAB_PANEL", List.of(), null);
            assertEquals(ArchiveConsumer.Outcome.REJECTED, consumer.consume(msg));
            assertEquals(false, consumer.deliver(msg).accepted());
        }
        assertTrue(records.findByEventId(eventId).isEmpty());
    }
}
