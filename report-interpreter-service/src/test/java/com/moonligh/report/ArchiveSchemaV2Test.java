package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.report.api.msg.ArchiveOutboxMessageV2;
import com.moonligh.report.common.PayloadJson;
import com.moonligh.report.domain.archive.ArchiveConsumer;
import com.moonligh.report.domain.archive.ArchiveOutboxManager;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.service.ArchiveOutboxService;

/** T-R4：schemaVersion=2 入队字段完整；同 eventId 消费幂等。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchiveSchemaV2Test {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ArchiveOutboxService archive;

    @Autowired
    private ArchiveConsumer consumer;

    @Test
    void enqueueWritesSchemaVersion2WithTypedHints() throws Exception {
        String account = ReportTestSupport.uniq("acc-v2");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);
        assertEquals(200, res.path("code").asInt());

        String outboxId = jdbc.queryForObject(
                "SELECT outbox_id FROM report_archive_outbox WHERE report_id = ?", String.class, reportId);
        String schema = jdbc.queryForObject(
                "SELECT schema_version FROM report_archive_outbox WHERE outbox_id = ?", String.class, outboxId);
        assertEquals(ArchiveOutboxManager.SCHEMA_VERSION, schema);
        assertEquals("2", schema);

        String payloadJson = jdbc.queryForObject(
                "SELECT payload_json FROM report_archive_outbox WHERE outbox_id = ?", String.class, outboxId);
        ArchiveOutboxMessageV2 msg = PayloadJson.read(payloadJson, ArchiveOutboxMessageV2.class);
        assertEquals("2", msg.schemaVersion());
        assertEquals("LAB_PANEL", msg.reportType());
        assertNotNull(msg.sections());
        assertNotNull(msg.fhirHints());
        assertEquals("Observation", msg.fhirHints().resourceType());
        assertTrue(msg.fhirHints().loincMappings().stream().anyMatch(m -> "ALT".equals(m.canonicalCode())));

        JsonNode get = ReportTestSupport.JSON.readTree(mvc.perform(get("/internal/report/archive/outbox/" + outboxId)
                        .header(AuthTokens.INTERNAL_HEADER, DeliveryTestSupport.INTERNAL_TOKEN))
                .andReturn().getResponse().getContentAsString());
        assertEquals(200, get.path("code").asInt(), get.toString());
        assertEquals("2", get.path("data").path("schemaVersion").asText());
        assertEquals("LAB_PANEL", get.path("data").path("payload").path("reportType").asText());
    }

    @Test
    void schemaV2ConsumeIsIdempotentByEventId() throws Exception {
        String account = ReportTestSupport.uniq("acc-v2-idem");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());
        ReportArchiveOutboxRow row = archive.dispatch(
                jdbc.queryForObject("SELECT outbox_id FROM report_archive_outbox WHERE report_id = ?",
                        String.class, reportId),
                consumer::deliver);
        assertEquals("SUCCESS", row.status());
        ArchiveOutboxMessageV2 msg = PayloadJson.read(row.payloadJson(), ArchiveOutboxMessageV2.class);
        assertEquals(ArchiveConsumer.Outcome.DUPLICATE, consumer.consume(msg));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_archive_record WHERE event_id = ?", Integer.class, msg.eventId()));
    }
}
