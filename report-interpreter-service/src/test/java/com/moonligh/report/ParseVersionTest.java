package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;

/** AC-P6：重解析 version=max+1，旧 active=false。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParseVersionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void reparseIncrementsVersionAndDeactivatesPrevious() throws Exception {
        String account = ReportTestSupport.uniq("acc-ver");
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        String reportId = created.path("data").path("reportId").asText();
        assertEquals(1, created.path("data").path("parseVersion").asInt());

        JsonNode second = ReportTestSupport.reparse(mvc, account, "re-1-" + reportId.substring(4, 12), reportId);
        assertEquals(200, second.path("code").asInt());
        assertEquals(2, second.path("data").path("parseVersion").asInt());
        assertEquals(reportId, second.path("data").path("reportId").asText());

        JsonNode third = ReportTestSupport.reparse(mvc, account, "re-2-" + reportId.substring(4, 12), reportId);
        assertEquals(3, third.path("data").path("parseVersion").asInt());

        JsonNode detail = ReportTestSupport.detail(mvc, account, reportId);
        assertEquals(3, detail.path("data").path("parseVersion").asInt());
        assertTrue(detail.path("data").path("activeParse").asBoolean());
        assertEquals(2, detail.path("data").path("indicators").size());

        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_parse_result WHERE report_id = ? AND active = 1",
                Integer.class, reportId);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_parse_result WHERE report_id = ?", Integer.class, reportId);
        assertEquals(1, active);
        assertEquals(3, total);
    }

    @Test
    void reparseSameKeyReplaysSameVersion() throws Exception {
        String account = ReportTestSupport.uniq("acc-ver-replay");
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        String reportId = created.path("data").path("reportId").asText();
        String key = ReportTestSupport.uniq("re");
        int v1 = ReportTestSupport.reparse(mvc, account, key, reportId).path("data").path("parseVersion").asInt();
        int v2 = ReportTestSupport.reparse(mvc, account, key, reportId).path("data").path("parseVersion").asInt();
        assertEquals(2, v1);
        assertEquals(v1, v2);
    }

    @Test
    void reparseOtherAccountIsRejected() throws Exception {
        JsonNode created = ReportTestSupport.create(
                mvc, "acc-owner", ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        String reportId = created.path("data").path("reportId").asText();
        JsonNode res = ReportTestSupport.reparse(mvc, "acc-intruder", ReportTestSupport.uniq("re"), reportId);
        assertTrue(res.path("code").asInt() != 200);
    }
}