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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.report.domain.trend.TrendDisclaimer;

/** T-R3：趋势接口含免责声明，无诊断话术。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrendDisclaimerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void disclaimerConstantIsNonDiagnostic() {
        assertFalse(TrendDisclaimer.looksDiagnostic(TrendDisclaimer.TEXT));
        assertTrue(TrendDisclaimer.looksDiagnostic("确诊为糖尿病"));
    }

    @Test
    void trendApiReturnsFixedDisclaimerWithoutDiagnosisLexicon() throws Exception {
        String account = ReportTestSupport.uniq("acc-trend-d");
        String patient = "p-trend-d";
        String body = """
                REPORT_TYPE=LAB_PANEL
                PATIENT_NAME=张三
                SPECIMEN_TIME=2026-03-01T08:00:00Z
                ALT|86|U/L|7|40|ALT 86 U/L|0
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.lab.txt", "text/plain", body.getBytes(StandardCharsets.UTF_8));
        JsonNode created = ReportTestSupport.JSON.readTree(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/report/reports")
                                .file(file)
                                .param("patientId", patient)
                                .param("expectedPatientName", "张三")
                                .header(AuthTokens.ACCOUNT_HEADER, account)
                                .header("Idempotency-Key", ReportTestSupport.uniq("k")))
                .andReturn().getResponse().getContentAsString());
        assertEquals(200, created.path("code").asInt(), created.toString());

        JsonNode res = ReportTestSupport.JSON.readTree(mvc.perform(get(
                        "/api/report/patients/" + patient + "/indicators/ALT/trend")
                        .header(AuthTokens.ACCOUNT_HEADER, account))
                .andReturn().getResponse().getContentAsString());
        assertEquals(200, res.path("code").asInt());
        String disclaimer = res.path("data").path("disclaimer").asText();
        assertEquals(TrendDisclaimer.TEXT, disclaimer);
        assertFalse(TrendDisclaimer.looksDiagnostic(disclaimer));
        assertFalse(disclaimer.contains("确诊"));
        assertFalse(disclaimer.contains("诊断为"));
    }
}
