package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
/** AC-R3：Coverage UNKNOWN / PARTIAL → 不得「安全正常」；UNKNOWN 默认阻断归档（4109）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoverageUnknownSafeBanTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void unaccountedPagesMakeCoverageUnknownAndBanSafeNormal() throws Exception {
        String account = ReportTestSupport.uniq("acc-cov-unknown");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-coverage-unknown.lab.txt", "张三");
        assertEquals("NEED_MANUAL_REVIEW", created.path("data").path("status").asText());

        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals(4109, detail.path("code").asInt());
        assertEquals(ErrorCode.REPORT_CRITICAL_COVERAGE_UNKNOWN.getCode(), detail.path("code").asInt());
        JsonNode data = detail.path("data");
        assertEquals("UNKNOWN", data.path("criticalCoverage").path("status").asText());
        assertEquals("MATCHED", data.path("identityCheck").path("status").asText());
        // K=4.2 本身正常，但覆盖未知时仍不得表述为安全正常。
        assertFalse(data.path("safeNormalAllowed").asBoolean());
        assertTrue(data.path("archiveBlocked").asBoolean());
    }

    @Test
    void failedPageAndUnrecognizedRegionMakeCoveragePartial() throws Exception {
        String account = ReportTestSupport.uniq("acc-cov-partial");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-coverage-partial.lab.txt", "张三");
        assertEquals("NEED_MANUAL_REVIEW", created.path("data").path("status").asText());

        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals(ErrorCode.REPORT_RULE_PENDING_REVIEW.getCode(), detail.path("code").asInt());
        JsonNode cov = detail.path("data").path("criticalCoverage");
        assertEquals("PARTIAL", cov.path("status").asText());
        assertEquals(1, cov.path("evidence").path("failedPages").get(0).asInt());
        assertEquals("page1-table", cov.path("evidence").path("unrecognizedRegions").get(0).asText());
        assertFalse(detail.path("data").path("safeNormalAllowed").asBoolean());
    }

    @Test
    void unparsedReportIsNeverSafe() throws Exception {
        String account = ReportTestSupport.uniq("acc-unparsed");
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "lab.pdf", "application/pdf", "%PDF-1.4".getBytes());
        JsonNode created = ReportTestSupport.create(mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL", file);
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertFalse(detail.path("data").path("safeNormalAllowed").asBoolean());
        assertTrue(detail.path("data").path("archiveBlocked").asBoolean());
    }
}