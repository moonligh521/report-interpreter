package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.moonligh.report.DeliveryTestSupport.SseEvent;
import com.moonligh.report.api.msg.ArchiveOutboxMessage;
import com.moonligh.report.common.PayloadJson;
import com.moonligh.report.domain.archive.ArchiveConsumer;
import com.moonligh.report.domain.archive.ArchiveDeliveryResult;
import com.moonligh.report.domain.archive.ArchiveOutboxManager;
import com.moonligh.report.domain.archive.ArchiveRecordStore;
import com.moonligh.report.domain.archive.ArchiveSender;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.service.ArchiveOutboxService;

/**
 * AC-D8 / D-R6：同一 eventId 入队只一行、消费只落一条；同键重放不重复写事件 / 档案。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxIdempotencyTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ArchiveOutboxManager manager;

    @Autowired
    private ArchiveOutboxService archive;

    @Autowired
    private ArchiveConsumer consumer;

    @Autowired
    private ArchiveRecordStore records;

    @Autowired
    private ArchiveSender defaultSender;

    private int count(String table, String reportId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE report_id = ?", Integer.class, reportId);
    }

    private static ArchiveOutboxMessage message(String eventId, String reportId) {
        return new ArchiveOutboxMessage(
                eventId, ArchiveOutboxManager.SCHEMA_VERSION, reportId, "p-1", 1, "itp_idem", List.of(), "MATCHED");
    }

    @Test
    void replayingTheSameIdempotencyKeyWritesNothingNew() throws Exception {
        String account = ReportTestSupport.uniq("acc-idem");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        String key = ReportTestSupport.uniq("itp");

        JsonNode first = InterpretationTestSupport.trigger(mvc, account, reportId, key, null);
        int events = count("report_event", reportId);
        JsonNode second = InterpretationTestSupport.trigger(mvc, account, reportId, key, null);

        assertEquals(first.path("data").path("interpretationId").asText(),
                second.path("data").path("interpretationId").asText());
        assertEquals(1, count("report_interpretation", reportId));
        assertEquals(1, count("report_archive_outbox", reportId));
        assertEquals(events, count("report_event", reportId));
    }

    @Test
    void anotherInterpretationGetsItsOwnArchiveEventAndACorrectionEvent() throws Exception {
        String account = ReportTestSupport.uniq("acc-idem-2");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        String a = InterpretationTestSupport.trigger(mvc, account, reportId).path("data").path("interpretationId").asText();
        String b = InterpretationTestSupport.trigger(mvc, account, reportId).path("data").path("interpretationId").asText();
        assertNotEquals(a, b);

        assertEquals(2, count("report_archive_outbox", reportId));
        Integer distinct = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT event_id) FROM report_archive_outbox WHERE report_id = ?", Integer.class, reportId);
        assertEquals(2, distinct);

        List<SseEvent> events = DeliveryTestSupport.stream(mvc, account, reportId, null);
        List<SseEvent> corrections = events.stream().filter(e -> "CORRECTION".equals(e.event())).toList();
        assertEquals(1, corrections.size());
        assertEquals(a, corrections.get(0).data().path("payload").path("supersededInterpretationId").asText());
        assertEquals(b, corrections.get(0).data().path("payload").path("interpretationId").asText());
        // 序号全程连续，没有因为第二次解读产生空洞或重复。
        for (int i = 0; i < events.size(); i++) {
            assertEquals(i + 1, events.get(i).data().path("sequence").asLong());
        }
    }

    @Test
    void enqueueingTheSameEventTwiceKeepsOneRow() {
        String reportId = ReportTestSupport.uniq("rpt-idem-q");
        String eventId = "arc_" + ReportTestSupport.uniq("q");
        ReportArchiveOutboxRow first = manager.enqueue(message(eventId, reportId));
        ReportArchiveOutboxRow second = manager.enqueue(message(eventId, reportId));

        assertEquals(first.outboxId(), second.outboxId());
        assertEquals(1, count("report_archive_outbox", reportId));
        assertEquals("PENDING", second.status());
    }

    @Test
    void consumingTheSameEventTwiceAppliesOnce() {
        String reportId = ReportTestSupport.uniq("rpt-idem-c");
        ArchiveOutboxMessage msg = message("arc_" + ReportTestSupport.uniq("c"), reportId);

        assertEquals(ArchiveConsumer.Outcome.APPLIED, consumer.consume(msg));
        assertEquals(ArchiveConsumer.Outcome.DUPLICATE, consumer.consume(msg));
        assertEquals(ArchiveConsumer.Outcome.DUPLICATE, consumer.consume(msg));

        assertEquals(1, records.countByReport(reportId));
        assertTrue(consumer.deliver(msg).accepted());
        assertEquals(1, count("report_archive_record", reportId));
    }

    @Test
    void redeliveryAfterTheConsumerAlreadyAppliedStillSucceedsWithoutADuplicateRecord() throws Exception {
        String account = ReportTestSupport.uniq("acc-idem-3");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);
        String payloadJson = jdbc.queryForObject(
                "SELECT payload_json FROM report_archive_outbox WHERE report_id = ?", String.class, reportId);
        ArchiveOutboxMessage msg = PayloadJson.read(payloadJson, ArchiveOutboxMessage.class);
        // 消费方先收到一次（例如上次 ACK 丢失），随后 Outbox 再投递：仍然 SUCCESS，档案只有一条。
        assertEquals(ArchiveConsumer.Outcome.APPLIED, consumer.consume(msg));
        String outboxId = jdbc.queryForObject(
                "SELECT outbox_id FROM report_archive_outbox WHERE report_id = ?", String.class, reportId);

        ReportArchiveOutboxRow after = archive.dispatch(outboxId, defaultSender);

        assertEquals("SUCCESS", after.status());
        assertEquals(1, count("report_archive_record", reportId));
    }

    @Test
    void aSuccessfulOutboxIsNeverSentAgain() throws Exception {
        String account = ReportTestSupport.uniq("acc-idem-4");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);
        String outboxId = jdbc.queryForObject(
                "SELECT outbox_id FROM report_archive_outbox WHERE report_id = ?", String.class, reportId);
        List<ArchiveOutboxMessage> sent = new ArrayList<>();
        ArchiveSender recording = m -> {
            sent.add(m);
            return ArchiveDeliveryResult.ok();
        };

        assertEquals("SUCCESS", archive.dispatch(outboxId, recording).status());
        assertThrows(IllegalStateException.class, () -> archive.dispatch(outboxId, recording));
        assertEquals(1, sent.size());
    }
}
