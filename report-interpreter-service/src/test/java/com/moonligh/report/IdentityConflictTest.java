package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
/** AC-R1：身份 CONFLICT → 4108，阻断归档，报告状态 BLOCKED，且不返回指标明细。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityConflictTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void mismatchedNameReturns4108AndBlocksArchive() throws Exception {
        String account = ReportTestSupport.uniq("acc-conflict");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", "李四");
        assertEquals(200, created.path("code").asInt());
        assertEquals("BLOCKED", created.path("data").path("status").asText());

        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals(4108, detail.path("code").asInt());
        assertEquals(ErrorCode.REPORT_IDENTITY_CONFLICT.getCode(), detail.path("code").asInt());
        JsonNode data = detail.path("data");
        assertEquals("CONFLICT", data.path("identityCheck").path("status").asText());
        assertEquals(4108, data.path("blockCode").asInt());
        assertTrue(data.path("archiveBlocked").asBoolean());
        assertFalse(data.path("safeNormalAllowed").asBoolean());
        // 报告可能属于他人：不向调用方暴露指标明细，也不回显任何姓名。
        assertEquals(0, data.path("indicators").size());
        assertFalse(detail.toString().contains("张三"));
        assertFalse(detail.toString().contains("李四"));
    }

    @Test
    void whitespaceAndWidthDifferencesStillMatch() throws Exception {
        String account = ReportTestSupport.uniq("acc-conflict-norm");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", " 张 三 ");
        assertEquals("RULE_CHECKED", created.path("data").path("status").asText());
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals("MATCHED", detail.path("data").path("identityCheck").path("status").asText());
        assertTrue(detail.path("data").path("blockCode").isNull());
    }

    @Test
    void conflictDoesNotChangeOtherAccountsView() throws Exception {
        JsonNode created = ReportTestSupport.createRules(
                mvc, ReportTestSupport.uniq("acc-own"), "rules-matched-normal.lab.txt", "李四");
        JsonNode other = ReportTestSupport.detail(
                mvc, ReportTestSupport.uniq("acc-other"), created.path("data").path("reportId").asText());
        assertNotEquals(200, other.path("code").asInt());
        assertNotEquals(4108, other.path("code").asInt());
    }
}