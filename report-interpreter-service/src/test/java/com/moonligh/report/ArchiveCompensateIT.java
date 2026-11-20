package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.api.msg.ArchiveOutboxMessageV2;
import com.moonligh.report.common.PayloadJson;
import com.moonligh.report.domain.archive.ArchiveDeliveryResult;
import com.moonligh.report.domain.archive.ArchiveOutboxManager;
import com.moonligh.report.domain.archive.ArchiveSender;
import com.moonligh.report.service.ArchiveOutboxService;

/** ArchiveCompensateIT：schemaVersion=2 死信补偿后可再投递成功。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchiveCompensateIT {

    private static final ArchiveSender DOWN = m -> ArchiveDeliveryResult.failed("ARCHIVE_DOWN");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ArchiveOutboxService archive;

    @Autowired
    private ArchiveSender defaultSender;

    @Test
    void v2DeadOutboxCompensatesAndDelivers() throws Exception {
        String account = ReportTestSupport.uniq("acc-comp-v2");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());

        String outboxId = jdbc.queryForObject(
                "SELECT outbox_id FROM report_archive_outbox WHERE report_id = ?", String.class, reportId);
        assertEquals("2", jdbc.queryForObject(
                "SELECT schema_version FROM report_archive_outbox WHERE outbox_id = ?", String.class, outboxId));
        ArchiveOutboxMessageV2 msg = PayloadJson.read(jdbc.queryForObject(
                "SELECT payload_json FROM report_archive_outbox WHERE outbox_id = ?", String.class, outboxId),
                ArchiveOutboxMessageV2.class);
        assertEquals(ArchiveOutboxManager.SCHEMA_VERSION, msg.schemaVersion());

        for (int i = 0; i < 5; i++) {
            archive.dispatch(outboxId, DOWN);
        }
        assertEquals("DEAD", jdbc.queryForObject(
                "SELECT status FROM report_archive_outbox WHERE outbox_id = ?", String.class, outboxId));

        JsonNode res = DeliveryTestSupport.compensate(
                mvc, DeliveryTestSupport.INTERNAL_TOKEN, reportId,
                DeliveryTestSupport.compensateBody(outboxId, false));
        assertEquals(200, res.path("code").asInt());
        assertEquals("PENDING", res.path("data").path("status").asText());
        assertEquals("SUCCESS", archive.dispatch(outboxId, defaultSender).status());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_archive_record WHERE report_id = ?", Integer.class, reportId));
    }
}
