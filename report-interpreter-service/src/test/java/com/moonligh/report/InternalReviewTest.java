package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;

/** POST /internal/report/reviews：待复核出口，内部令牌 + 幂等键。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalReviewTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode call(String token, String key, String body) throws Exception {
        var b = post("/internal/report/reviews").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            b.header(AuthTokens.INTERNAL_HEADER, token);
        }
        if (key != null) {
            b.header("Idempotency-Key", key);
        }
        return ReportTestSupport.JSON.readTree(mvc.perform(b).andReturn().getResponse().getContentAsString());
    }

    private static String body(String reportId, String reason, String priority) {
        return "{\"reportId\":\"" + reportId + "\",\"reason\":\"" + reason + "\",\"priority\":\"" + priority + "\"}";
    }

    private String newReport() throws Exception {
        String account = ReportTestSupport.uniq("acc-review");
        return ReportTestSupport.createRules(mvc, account, "rules-coverage-unknown.lab.txt", "张三")
                .path("data").path("reportId").asText();
    }

    @Test
    void requiresInternalToken() throws Exception {
        JsonNode res = call(null, "k-1", body("rpt-x", "r", "HIGH"));
        assertEquals(ErrorCode.LOGIN_REQUIRED.getCode(), res.path("code").asInt());
    }

    @Test
    void createsOpenReviewTaskAndPersistsIt() throws Exception {
        String reportId = newReport();
        JsonNode res = call("consult-internal", ReportTestSupport.uniq("rv"), body(reportId, "覆盖未知待人工确认", "HIGH"));
        assertEquals(200, res.path("code").asInt());
        String taskId = res.path("data").path("reviewTaskId").asText();
        assertTrue(taskId.startsWith("rvw_"));
        assertEquals("OPEN", res.path("data").path("status").asText());

        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_review_task WHERE review_task_id = ? AND report_id = ? AND priority = 'HIGH'",
                Integer.class, taskId, reportId);
        assertEquals(1, n);
    }

    @Test
    void sameKeySameBodyReplaysAndDifferentBodyConflicts() throws Exception {
        String reportId = newReport();
        String key = ReportTestSupport.uniq("rv");
        JsonNode a = call("consult-internal", key, body(reportId, "原因", "NORMAL"));
        JsonNode b = call("consult-internal", key, body(reportId, "原因", "NORMAL"));
        assertEquals(a.path("data").path("reviewTaskId").asText(), b.path("data").path("reviewTaskId").asText());
        JsonNode c = call("consult-internal", key, body(reportId, "另一个原因", "NORMAL"));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT.getCode(), c.path("code").asInt());
    }

    @Test
    void rejectsMissingKeyUnknownReportAndBadPriority() throws Exception {
        String reportId = newReport();
        assertNotEquals(200, call("consult-internal", null, body(reportId, "r", "HIGH")).path("code").asInt());
        assertNotEquals(200, call("consult-internal", ReportTestSupport.uniq("rv"),
                body("rpt_not_exist", "r", "HIGH")).path("code").asInt());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), call("consult-internal", ReportTestSupport.uniq("rv"),
                body(reportId, "r", "SUPER")).path("code").asInt());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), call("consult-internal", ReportTestSupport.uniq("rv"),
                body(reportId, " ", "HIGH")).path("code").asInt());
    }
}