package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
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
import com.moonligh.report.domain.archive.ArchiveDeliveryResult;
import com.moonligh.report.domain.archive.ArchiveSender;
import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.CriticalNotificationSender;
import com.moonligh.report.domain.critical.DeliveryResult;
import com.moonligh.report.domain.critical.NotificationStatus;
import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.service.ArchiveOutboxService;
import com.moonligh.report.service.CriticalNotificationService;

/**
 * AC-D5 / D-R3：解读 COMPLETED ≠ 档案 SUCCESS ≠ 危急已读。三条生命周期各走各的状态机，互不推导。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobCompletedVsArchiveTest {

    private static final ArchiveSender DOWN = m -> ArchiveDeliveryResult.failed("ARCHIVE_DOWN");
    private static final ArchiveSender OK = m -> ArchiveDeliveryResult.ok();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ArchiveOutboxService archive;

    @Autowired
    private CriticalNotificationService criticalService;

    @Autowired
    private CriticalEventStore criticalStore;

    private JsonNode lifecycle(String account, String reportId) throws Exception {
        return DeliveryTestSupport.events(mvc, account, reportId).path("data").path("lifecycle");
    }

    private String archiveOutboxId(String reportId) {
        return jdbc.queryForObject(
                "SELECT outbox_id FROM report_archive_outbox WHERE report_id = ?", String.class, reportId);
    }

    @Test
    void interpretationCompletedWhileArchivePendingAndCriticalUnread() throws Exception {
        String account = ReportTestSupport.uniq("acc-life");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);
        assertEquals(4105, res.path("code").asInt());

        JsonNode life = lifecycle(account, reportId);
        assertEquals("COMPLETED", life.path("interpretationStatus").asText());
        assertEquals("PENDING", life.path("archiveStatus").asText());
        assertEquals("UNREAD", life.path("criticalReadStatus").asText());

        // DONE 事件携带的是完成时刻的三态快照，同样互相独立。
        List<SseEvent> events = DeliveryTestSupport.stream(mvc, account, reportId, null);
        JsonNode done = events.get(events.size() - 1).data().path("payload");
        assertEquals("DONE", events.get(events.size() - 1).event());
        assertEquals("COMPLETED", done.path("interpretationStatus").asText());
        assertEquals("PENDING", done.path("archiveStatus").asText());
        assertEquals("UNREAD", done.path("criticalReadStatus").asText());
    }

    @Test
    void archiveDeadDoesNotUndoInterpretationCompletedNorTouchCriticalReads() throws Exception {
        String account = ReportTestSupport.uniq("acc-life-dead");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);
        String outboxId = archiveOutboxId(reportId);

        ReportArchiveOutboxRow row = null;
        for (int i = 0; i < 5; i++) {
            row = archive.dispatch(outboxId, DOWN);
        }
        assertEquals("DEAD", row.status());

        JsonNode life = lifecycle(account, reportId);
        assertEquals("COMPLETED", life.path("interpretationStatus").asText());
        assertEquals("DEAD", life.path("archiveStatus").asText());
        assertEquals("UNREAD", life.path("criticalReadStatus").asText());
        // 解读本身仍可读取、内容不变。
        assertEquals(4105, InterpretationTestSupport.latest(mvc, account, reportId).path("code").asInt());
    }

    @Test
    void archiveSuccessIsNotACriticalRead() throws Exception {
        String account = ReportTestSupport.uniq("acc-life-ok");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals("SUCCESS", archive.dispatch(archiveOutboxId(reportId), OK).status());
        JsonNode life = lifecycle(account, reportId);
        assertEquals("COMPLETED", life.path("interpretationStatus").asText());
        assertEquals("SUCCESS", life.path("archiveStatus").asText());
        assertEquals("UNREAD", life.path("criticalReadStatus").asText());

        // 危急通知被通道接受（ACCEPTED）也不等于已读。
        String criticalOutbox = criticalStore.listEventsByReport(reportId).stream()
                .map(e -> criticalStore.findOutboxByEvent(e.eventId()).orElseThrow().outboxId()).findFirst().orElseThrow();
        CriticalNotificationSender accept = m -> DeliveryResult.ok();
        assertEquals("ACCEPTED", criticalService.dispatch(criticalOutbox, Instant.now(), accept).notificationStatus());
        assertEquals("UNREAD", lifecycle(account, reportId).path("criticalReadStatus").asText());
    }

    @Test
    void criticalReadIsDrivenOnlyByUserAcknowledgementAndLeavesArchiveAlone() throws Exception {
        String account = ReportTestSupport.uniq("acc-life-read");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);
        String criticalOutbox = criticalStore.listEventsByReport(reportId).stream()
                .map(e -> criticalStore.findOutboxByEvent(e.eventId()).orElseThrow().outboxId()).findFirst().orElseThrow();
        criticalService.dispatch(criticalOutbox, Instant.now(), m -> DeliveryResult.ok());

        CriticalNotificationOutboxRow accepted = criticalStore.findOutboxById(criticalOutbox).orElseThrow();
        assertTrue(NotificationStatus.ACCEPTED.canTransitionTo(NotificationStatus.ACKNOWLEDGED));
        criticalStore.updateOutbox(new CriticalNotificationOutboxRow(
                accepted.outboxId(), accepted.eventId(), NotificationStatus.ACKNOWLEDGED.name(),
                accepted.deliveryChannel(), accepted.idempotencyKey(), accepted.attemptCount(), null, null,
                accepted.deliveredAt()));

        JsonNode life = lifecycle(account, reportId);
        assertEquals("READ", life.path("criticalReadStatus").asText());
        assertEquals("PENDING", life.path("archiveStatus").asText());
        assertEquals("COMPLETED", life.path("interpretationStatus").asText());
    }

    @Test
    void archiveAndCriticalOutboxesAreSeparateTables() throws Exception {
        String account = ReportTestSupport.uniq("acc-life-sep");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);

        String archiveId = archiveOutboxId(reportId);
        Integer inCritical = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_critical_notification_outbox WHERE outbox_id = ?", Integer.class, archiveId);
        assertEquals(0, inCritical);
        assertTrue(archiveId.startsWith("aob_"));
        assertNotEquals(archiveId, criticalStore.listEventsByReport(reportId).stream()
                .map(e -> criticalStore.findOutboxByEvent(e.eventId()).orElseThrow().outboxId()).findFirst().orElseThrow());
    }
}
