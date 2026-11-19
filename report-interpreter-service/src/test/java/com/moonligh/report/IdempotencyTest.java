package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;

/** AC-P3：同键同请求 → 同 reportId；同键异请求 → 2604。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void sameKeySameBodyReturnsSameReport() throws Exception {
        String account = ReportTestSupport.uniq("acc-idem");
        String key = ReportTestSupport.uniq("k");
        String text = ReportTestSupport.golden("lab-panel-basic.lab.txt");
        JsonNode a = ReportTestSupport.create(mvc, account, key, "LAB_PANEL", ReportTestSupport.labFile(text));
        JsonNode b = ReportTestSupport.create(mvc, account, key, "LAB_PANEL", ReportTestSupport.labFile(text));
        assertEquals(200, a.path("code").asInt());
        assertEquals(200, b.path("code").asInt());
        assertEquals(a.path("data").path("reportId").asText(), b.path("data").path("reportId").asText());
        assertEquals(1, b.path("data").path("parseVersion").asInt());
        assertEquals("NEED_MANUAL_REVIEW", b.path("data").path("status").asText());
    }

    @Test
    void sameKeyDifferentBodyConflicts() throws Exception {
        String account = ReportTestSupport.uniq("acc-idem-c");
        String key = ReportTestSupport.uniq("k");
        ReportTestSupport.create(mvc, account, key, "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        JsonNode conflict = ReportTestSupport.create(mvc, account, key, "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-normalize.lab.txt")));
        assertEquals(2604, conflict.path("code").asInt());
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT.getCode(), conflict.path("code").asInt());
    }

    @Test
    void sameKeyDifferentAccountIsIndependent() throws Exception {
        String key = ReportTestSupport.uniq("k");
        String text = ReportTestSupport.golden("lab-panel-basic.lab.txt");
        JsonNode a = ReportTestSupport.create(mvc, ReportTestSupport.uniq("acc-x"), key, "LAB_PANEL",
                ReportTestSupport.labFile(text));
        JsonNode b = ReportTestSupport.create(mvc, ReportTestSupport.uniq("acc-y"), key, "LAB_PANEL",
                ReportTestSupport.labFile(text));
        assertNotEquals(a.path("data").path("reportId").asText(), b.path("data").path("reportId").asText());
    }
}