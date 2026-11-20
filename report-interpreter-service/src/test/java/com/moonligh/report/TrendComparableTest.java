package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.report.domain.trend.TrendComparability;
import com.moonligh.report.domain.trend.TrendIncomparableReason;
import com.moonligh.report.infrastructure.persistence.TrendPointRow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** T-R1 / AC：同码不同标本默认不可自动比。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrendComparableTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void differentSpecimenTypesAreNotComparable() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        List<TrendPointRow> rows = List.of(
                new TrendPointRow("r1", 1, new BigDecimal("5.0"), "mmol/L", "SERUM", t),
                new TrendPointRow("r2", 1, new BigDecimal("5.1"), "mmol/L", "URINE", t.plusSeconds(86400)));
        TrendComparability.Decision d = TrendComparability.evaluate(rows);
        assertFalse(d.comparable());
        assertEquals(TrendIncomparableReason.SPECIMEN_MISMATCH, d.reason());
    }

    @Test
    void sameSpecimenIsComparable() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        List<TrendPointRow> rows = List.of(
                new TrendPointRow("r1", 1, new BigDecimal("5.0"), "mmol/L", "SERUM", t),
                new TrendPointRow("r2", 1, new BigDecimal("5.1"), "mmol/L", "serum", t.plusSeconds(86400)));
        assertTrue(TrendComparability.evaluate(rows).comparable());
    }

    @Test
    void apiMarksMixedSpecimenIncomparableAndStillReturnsPoints() throws Exception {
        String account = ReportTestSupport.uniq("acc-trend-sp");
        String patient = "p-trend-sp";
        createLab(account, patient, "2026-01-01T08:00:00Z", "SERUM", "5.0");
        createLab(account, patient, "2026-01-02T08:00:00Z", "URINE", "5.2");

        JsonNode res = ReportTestSupport.JSON.readTree(mvc.perform(get(
                        "/api/report/patients/" + patient + "/indicators/K/trend")
                        .header(AuthTokens.ACCOUNT_HEADER, account))
                .andReturn().getResponse().getContentAsString());

        assertEquals(200, res.path("code").asInt(), res.toString());
        assertFalse(res.path("data").path("comparable").asBoolean());
        assertEquals(TrendIncomparableReason.SPECIMEN_MISMATCH, res.path("data").path("incomparableReason").asText());
        assertEquals(2, res.path("data").path("points").size());
    }

    private void createLab(String account, String patient, String specimenTime, String specimen, String value)
            throws Exception {
        String body = """
                REPORT_TYPE=LAB_PANEL
                PATIENT_NAME=张三
                SPECIMEN_TIME=%s
                K|%s|mmol/L|3.5|5.3|K %s mmol/L|0|%s
                """.formatted(specimenTime, value, value, specimen);
        MockMultipartFile file = new MockMultipartFile(
                "file", "k.lab.txt", "text/plain", body.getBytes(StandardCharsets.UTF_8));
        var b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/report/reports")
                .file(file)
                .param("patientId", patient)
                .param("expectedPatientName", "张三")
                .header(AuthTokens.ACCOUNT_HEADER, account)
                .header("Idempotency-Key", ReportTestSupport.uniq("k"));
        JsonNode created = ReportTestSupport.JSON.readTree(
                mvc.perform(b).andReturn().getResponse().getContentAsString());
        assertEquals(200, created.path("code").asInt(), created.toString());
    }
}
