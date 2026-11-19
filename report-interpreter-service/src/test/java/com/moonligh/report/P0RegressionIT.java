package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.DeliveryTestSupport.SseEvent;

/** P0 冒烟：LAB_PANEL + 危急 + 解读 + SSE 仍绿。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class P0RegressionIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void labPanelCriticalInterpretAndSseStillGreen() throws Exception {
        String account = ReportTestSupport.uniq("acc-p0");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");

        JsonNode detail = ReportTestSupport.detail(mvc, account, reportId);
        assertEquals("LAB_PANEL", detail.path("data").path("reportType").asText());
        assertFalse(detail.path("data").path("criticalHits").isEmpty());
        assertTrue(detail.path("data").path("subReports").isArray());
        assertEquals(0, detail.path("data").path("subReports").size());
        assertEquals(0, detail.path("data").path("findings").size());

        JsonNode itp = InterpretationTestSupport.trigger(mvc, account, reportId);
        assertTrue(itp.path("code").asInt() == 200 || itp.path("code").asInt() == 4105, itp.toString());

        var events = DeliveryTestSupport.stream(mvc, account, reportId, null);
        assertTrue(events.stream().anyMatch(e -> "CRITICAL_ALERT".equals(e.event())
                || "INTERPRETATION_DELTA".equals(e.event())
                || "DONE".equals(e.event())));
        for (SseEvent e : events) {
            assertTrue(e.data().path("payload").has("payloadType"), e.data().toString());
        }
    }
}
