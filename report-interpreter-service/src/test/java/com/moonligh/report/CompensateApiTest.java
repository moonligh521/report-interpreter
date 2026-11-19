package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.domain.archive.ArchiveDeliveryResult;
import com.moonligh.report.domain.archive.ArchiveSender;
import com.moonligh.report.service.ArchiveOutboxService;

/** AC-D10 / D-R7：POST /internal/report/jobs/{id}/compensate 把 DEAD 拉回 PENDING（策略允许时）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompensateApiTest {

    private static final ArchiveSender DOWN = m -> ArchiveDeliveryResult.failed("ARCHIVE_DOWN");
    private static final ArchiveSender OK = m -> ArchiveDeliveryResult.ok();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ArchiveOutboxService archive;

    @Autowired
    private ArchiveSender defaultSender;

    private String newInterpretedReport() throws Exception {
        String account = ReportTestSupport.uniq("acc-comp");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());
        return reportId;
    }

    private String outboxOf(String reportId) {
        return jdbc.queryForObject(
                "SELECT outbox_id FROM report_archive_outbox WHERE report_id = ?", String.class, reportId);
    }

    private String statusOf(String outboxId) {
        return jdbc.queryForObject(
                "SELECT status FROM report_archive_outbox WHERE outbox_id = ?", String.class, outboxId);
    }

    private String deadOutbox(String reportId) {
        String outboxId = outboxOf(reportId);
        for (int i = 0; i < 5; i++) {
            archive.dispatch(outboxId, DOWN);
        }
        assertEquals("DEAD", statusOf(outboxId));
        return outboxId;
    }

    @Test
    void requiresInternalToken() throws Exception {
        String reportId = newInterpretedReport();
        JsonNode res = DeliveryTestSupport.compensate(
                mvc, null, reportId, DeliveryTestSupport.compensateBody(outboxOf(reportId), false));
        assertEquals(ErrorCode.LOGIN_REQUIRED.getCode(), res.path("code").asInt());
        JsonNode wrong = DeliveryTestSupport.compensate(
                mvc, "not-the-token", reportId, DeliveryTestSupport.compensateBody(outboxOf(reportId), false));
        assertNotEquals(200, wrong.path("code").asInt());
    }

    @Test
    void deadOutboxIsPulledBackToPendingAndCanThenSucceed() throws Exception {
        String reportId = newInterpretedReport();
        String outboxId = deadOutbox(reportId);

        JsonNode res = DeliveryTestSupport.compensate(
                mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId, DeliveryTestSupport.compensateBody(outboxId, false));

        assertEquals(200, res.path("code").asInt());
        assertEquals(outboxId, res.path("data").path("outboxId").asText());
        assertEquals("PENDING", res.path("data").path("status").asText());
        assertEquals(0, res.path("data").path("attemptCount").asInt());
        assertEquals("PENDING", statusOf(outboxId));

        // 默认通道直连幂等消费方：成功后档案落地记录恰好一条。
        assertEquals("SUCCESS", archive.dispatch(outboxId, defaultSender).status());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_archive_record WHERE report_id = ?", Integer.class, reportId));
    }

    @Test
    void compensatingAPendingOutboxIsAnIdempotentNoOp() throws Exception {
        String reportId = newInterpretedReport();
        String outboxId = outboxOf(reportId);

        JsonNode res = DeliveryTestSupport.compensate(
                mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId, DeliveryTestSupport.compensateBody(outboxId, false));

        assertEquals(200, res.path("code").asInt());
        assertEquals("PENDING", res.path("data").path("status").asText());
    }

    @Test
    void successfulOutboxCannotBeCompensatedEvenWithForce() throws Exception {
        String reportId = newInterpretedReport();
        String outboxId = outboxOf(reportId);
        archive.dispatch(outboxId, OK);

        for (boolean force : new boolean[] {false, true}) {
            JsonNode res = DeliveryTestSupport.compensate(mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId,
                    DeliveryTestSupport.compensateBody(outboxId, force));
            assertEquals(ErrorCode.PARAM_INVALID.getCode(), res.path("code").asInt());
        }
        assertEquals("SUCCESS", statusOf(outboxId));
    }

    @Test
    void failedOutboxStillRetryingNeedsForce() throws Exception {
        String reportId = newInterpretedReport();
        String outboxId = outboxOf(reportId);
        assertEquals("FAILED", archive.dispatch(outboxId, DOWN).status());

        JsonNode plain = DeliveryTestSupport.compensate(mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId,
                DeliveryTestSupport.compensateBody(outboxId, false));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), plain.path("code").asInt());
        assertEquals("FAILED", statusOf(outboxId));

        JsonNode forced = DeliveryTestSupport.compensate(mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId,
                DeliveryTestSupport.compensateBody(outboxId, true));
        assertEquals(200, forced.path("code").asInt());
        assertEquals("PENDING", forced.path("data").path("status").asText());
    }

    @Test
    void identityGateStillAppliesToCompensationAndForceCannotOverrideIt() throws Exception {
        String reportId = newInterpretedReport();
        String outboxId = deadOutbox(reportId);

        jdbc.update("UPDATE report_rule_summary SET identity_status = 'CONFLICT' WHERE report_id = ?", reportId);
        JsonNode conflict = DeliveryTestSupport.compensate(mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId,
                DeliveryTestSupport.compensateBody(outboxId, true));
        assertEquals(ErrorCode.REPORT_IDENTITY_CONFLICT.getCode(), conflict.path("code").asInt());

        jdbc.update("UPDATE report_rule_summary SET identity_status = 'MISSING' WHERE report_id = ?", reportId);
        JsonNode missing = DeliveryTestSupport.compensate(mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId,
                DeliveryTestSupport.compensateBody(outboxId, true));
        assertEquals(ErrorCode.REPORT_IDENTITY_MISSING.getCode(), missing.path("code").asInt());

        jdbc.update("UPDATE report_rule_summary SET identity_status = 'MATCHED', archive_blocked = 1 "
                + "WHERE report_id = ?", reportId);
        JsonNode blocked = DeliveryTestSupport.compensate(mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId,
                DeliveryTestSupport.compensateBody(outboxId, true));
        assertEquals(ErrorCode.REPORT_RULE_PENDING_REVIEW.getCode(), blocked.path("code").asInt());
        assertEquals("DEAD", statusOf(outboxId));

        jdbc.update("UPDATE report_rule_summary SET archive_blocked = 0 WHERE report_id = ?", reportId);
        JsonNode ok = DeliveryTestSupport.compensate(mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId,
                DeliveryTestSupport.compensateBody(outboxId, false));
        assertEquals(200, ok.path("code").asInt());
        assertEquals("PENDING", statusOf(outboxId));
    }

    @Test
    void badInputIsRejected() throws Exception {
        String reportId = newInterpretedReport();
        String otherReport = newInterpretedReport();
        String outboxId = outboxOf(reportId);
        String token = DeliveryTestSupport.INTERNAL_TOKEN;

        // 空 outboxId / 未知 outbox / 未知任务 / 别的任务的 outbox
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), DeliveryTestSupport.compensate(
                mvc, token, reportId, "{\"outboxId\":\" \",\"force\":false}").path("code").asInt());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), DeliveryTestSupport.compensate(
                mvc, token, reportId, DeliveryTestSupport.compensateBody("aob_nope", false)).path("code").asInt());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), DeliveryTestSupport.compensate(
                mvc, token, "rpt_not_exist", DeliveryTestSupport.compensateBody(outboxId, false)).path("code").asInt());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), DeliveryTestSupport.compensate(
                mvc, token, otherReport, DeliveryTestSupport.compensateBody(outboxId, false)).path("code").asInt());
        assertEquals("PENDING", statusOf(outboxId));
    }
}
