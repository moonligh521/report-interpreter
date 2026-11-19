package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.DeliveryResult;
import com.moonligh.report.domain.critical.NotificationStatus;
import com.moonligh.report.service.CriticalNotificationService;

/** AC-M / M-R4：ACK 仅患者端已读语义，ACCEPTED → ACKNOWLEDGED。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CriticalAckTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CriticalEventStore criticalStore;

    @Autowired
    private CriticalNotificationService criticalService;

    @Test
    void ackMovesAcceptedToAcknowledged() throws Exception {
        String account = ReportTestSupport.uniq("acc-ack");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        InterpretationTestSupport.trigger(mvc, account, reportId);

        var event = criticalStore.listEventsByReport(reportId).get(0);
        String outboxId = criticalStore.findOutboxByEvent(event.eventId()).orElseThrow().outboxId();
        criticalService.dispatch(outboxId, Instant.now(), m -> DeliveryResult.ok());
        assertEquals(NotificationStatus.ACCEPTED.name(),
                criticalStore.findOutboxById(outboxId).orElseThrow().notificationStatus());

        JsonNode res = ReportTestSupport.JSON.readTree(mvc.perform(
                        post("/api/report/reports/" + reportId + "/critical-events/" + event.eventId() + "/ack")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(AuthTokens.ACCOUNT_HEADER, account)
                                .header("Idempotency-Key", ReportTestSupport.uniq("ack"))
                                .content("{\"acknowledgedAt\":null}"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(200, res.path("code").asInt(), res.toString());
        assertEquals(event.eventId(), res.path("data").path("eventId").asText());
        assertEquals(NotificationStatus.ACKNOWLEDGED.name(), res.path("data").path("notificationStatus").asText());

        var row = criticalStore.findOutboxByEvent(event.eventId()).orElseThrow();
        assertEquals(NotificationStatus.ACKNOWLEDGED.name(), row.notificationStatus());
        assertNotNull(row.acknowledgedAt());
    }
}
