package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.report.domain.archive.ArchiveOutboxManager;
import com.moonligh.report.domain.trend.TrendDisclaimer;

/**
 * FullRegressionIT：V0.3 关键路径串联（趋势可比性 / 出院内镜 / 档案 v2 / IMAGING 仍拒）。
 * 不等同于 clinical-record 真实联调；档案仍经本仓 Outbox 幂等消费。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullRegressionIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void p2HappyPathKeepsP0P1Invariants() throws Exception {
        // IMAGING_FILM 仍 4101
        JsonNode film = ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc-reg"), ReportTestSupport.uniq("k"), "IMAGING_FILM",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        assertEquals(ErrorCode.REPORT_TYPE_UNSUPPORTED.getCode(), film.path("code").asInt());

        // 出院 / 内镜可解析
        MockMultipartFile discharge = new MockMultipartFile(
                "file", "d.discharge.txt", "text/plain",
                ReportTestSupport.golden("discharge-basic.discharge.txt").getBytes(StandardCharsets.UTF_8));
        assertEquals(200, ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc-reg"), ReportTestSupport.uniq("k"), "DISCHARGE_SUMMARY", discharge)
                .path("code").asInt());
        MockMultipartFile endo = new MockMultipartFile(
                "file", "e.endo.txt", "text/plain",
                ReportTestSupport.golden("endoscopy-basic.endo.txt").getBytes(StandardCharsets.UTF_8));
        assertEquals(200, ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc-reg"), ReportTestSupport.uniq("k"), "ENDOSCOPY_REPORT", endo)
                .path("code").asInt());

        // 解读 → 档案 v2 → 趋势免责声明
        String account = ReportTestSupport.uniq("acc-reg-itp");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        assertEquals(200, InterpretationTestSupport.trigger(mvc, account, reportId).path("code").asInt());
        assertEquals("2", jdbc.queryForObject(
                "SELECT schema_version FROM report_archive_outbox WHERE report_id = ?", String.class, reportId));
        assertEquals(ArchiveOutboxManager.SCHEMA_VERSION, "2");

        jdbc.update("UPDATE report_parse_result SET specimen_time = TIMESTAMP '2026-05-01 08:00:00' "
                + "WHERE report_id = ? AND active = 1", reportId);
        JsonNode trend = ReportTestSupport.JSON.readTree(mvc.perform(get(
                        "/api/report/patients/p-rules/indicators/ALT/trend")
                        .header(AuthTokens.ACCOUNT_HEADER, account))
                .andReturn().getResponse().getContentAsString());
        assertEquals(200, trend.path("code").asInt());
        assertEquals(TrendDisclaimer.TEXT, trend.path("data").path("disclaimer").asText());
        assertFalse(TrendDisclaimer.looksDiagnostic(trend.path("data").path("disclaimer").asText()));
        assertTrue(trend.path("data").path("points").size() >= 1);
    }
}
