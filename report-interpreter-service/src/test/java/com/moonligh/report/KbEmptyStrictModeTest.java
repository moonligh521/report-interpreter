package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;

/** AC-G1（严格模式）：不允许降级时，KB 无依据 → 4104 且不落库任何解读。 */
@SpringBootTest(properties = "report.interpretation.allow-degraded=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KbEmptyStrictModeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyKb() {
        InterpretationTestSupport.clearKb(jdbc);
    }

    @Test
    void emptyKbIsRejectedWith4104AndNothingPersisted() throws Exception {
        String account = ReportTestSupport.uniq("acc-kb-strict");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(ErrorCode.REPORT_KB_UNAVAILABLE.getCode(), res.path("code").asInt());
        assertEquals(4104, res.path("code").asInt());
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_interpretation WHERE report_id = ?", Integer.class, reportId);
        assertEquals(0, n);
    }

    @Test
    void seededKbWorksInStrictModeWhenEvidenceExists() throws Exception {
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
        String account = ReportTestSupport.uniq("acc-kb-strict-ok");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        assertEquals("VALID", res.path("data").path("status").asText());
        assertTrue(res.path("data").path("evidence").size() >= 1);
    }
}
