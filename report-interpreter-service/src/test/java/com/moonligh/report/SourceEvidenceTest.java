package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.parse.ParseQualityGate;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ReportType;

/** AC-P2/P5：关键指标 pageIndex + rawText 必填。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourceEvidenceTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void indicatorsCarryPageIndexAndRawText() throws Exception {
        String account = ReportTestSupport.uniq("acc-evidence");
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());

        JsonNode alt = ReportTestSupport.indicator(detail, "ALT");
        assertEquals(0, alt.path("evidence").path("pageIndex").asInt());
        assertEquals("ALT 86 U/L 7-40", alt.path("evidence").path("rawText").asText());
        JsonNode k = ReportTestSupport.indicator(detail, "K");
        assertEquals("K 5.8 mmol/L 3.5-5.3", k.path("evidence").path("rawText").asText());
    }

    @Test
    void missingRawTextFailsQualityGate() throws Exception {
        JsonNode res = ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc-noev"), ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-no-evidence.lab.txt")));
        assertEquals(ErrorCode.REPORT_PARSE_FAILED.getCode(), res.path("code").asInt());
    }

    @Test
    void gateRejectsMissingPageIndex() {
        FieldConfidence c = new FieldConfidence(1, 1, 1, 1, 1, 1, 1, 1);
        ParsedIndicator ind = new ParsedIndicator(
                "ALT", "86", "U/L", "ALT", "SERUM", new BigDecimal("86"), "U/L", "NORMALIZED",
                null, null, c, new SourceEvidence(null, "ALT 86", null));
        ParseResult result = new ParseResult(ReportType.LAB_PANEL, List.of(ind), 1.0);
        BusinessException ex = assertThrows(BusinessException.class, () -> ParseQualityGate.check(result));
        assertEquals(ErrorCode.REPORT_PARSE_FAILED.getCode(), ex.getCode());
    }
}