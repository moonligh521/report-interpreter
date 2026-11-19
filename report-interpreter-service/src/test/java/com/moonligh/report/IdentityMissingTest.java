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
/** AC-R2：身份 MISSING → 4110，默认阻断归档；MATCHED 才允许归档。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityMissingTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void noIdentityInReportReturns4110() throws Exception {
        String account = ReportTestSupport.uniq("acc-missing");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-critical-k-no-identity.lab.txt", "张三");
        assertEquals("NEED_MANUAL_REVIEW", created.path("data").path("status").asText());

        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals(4110, detail.path("code").asInt());
        assertEquals(ErrorCode.REPORT_IDENTITY_MISSING.getCode(), detail.path("code").asInt());
        JsonNode data = detail.path("data");
        assertEquals("MISSING", data.path("identityCheck").path("status").asText());
        assertTrue(data.path("archiveBlocked").asBoolean());
        assertFalse(data.path("safeNormalAllowed").asBoolean());
        // MISSING 可展示待确认：指标仍返回。
        assertEquals(1, data.path("indicators").size());
    }

    @Test
    void noExpectedNameCannotVerifyIdentity() throws Exception {
        String account = ReportTestSupport.uniq("acc-missing-exp");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", null);
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals(4110, detail.path("code").asInt());
        assertTrue(detail.path("data").path("archiveBlocked").asBoolean());
    }

    @Test
    void matchedIdentityWithCompleteCoverageAllowsArchive() throws Exception {
        String account = ReportTestSupport.uniq("acc-matched");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", "张三");
        assertEquals("RULE_CHECKED", created.path("data").path("status").asText());

        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals(200, detail.path("code").asInt());
        JsonNode data = detail.path("data");
        assertEquals("MATCHED", data.path("identityCheck").path("status").asText());
        assertEquals("COMPLETE", data.path("criticalCoverage").path("status").asText());
        assertFalse(data.path("archiveBlocked").asBoolean());
        assertTrue(data.path("safeNormalAllowed").asBoolean());
        assertTrue(data.path("blockCode").isNull());
        assertEquals(0, data.path("criticalHits").size());
        assertEquals(0, data.path("abnormals").size());
    }
}