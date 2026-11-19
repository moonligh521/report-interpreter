package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;

/** AC-M / M-R5 / M-R6：人工修订产生新 parseVersion 并重跑规则；与危急复核分离。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrectionVersionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void correctionCreatesNewParseVersionAndRerunsRules() throws Exception {
        String account = ReportTestSupport.uniq("acc-corr");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        JsonNode before = ReportTestSupport.detail(mvc, account, reportId);
        assertEquals(1, before.path("data").path("parseVersion").asInt());
        String indicatorId = ReportTestSupport.indicator(before, "ALT").path("indicatorId").asText();

        String body = """
                {"reportId":"%s","parseVersionBase":1,"reviewerId":"rvw-doc-1",
                 "patches":[{"indicatorId":"%s","normalizedValue":80,"reason":"OCR 校正"}]}
                """.formatted(reportId, indicatorId);
        JsonNode corr = ReportTestSupport.JSON.readTree(mvc.perform(post("/internal/report/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthTokens.INTERNAL_HEADER, "consult-internal")
                        .header("Idempotency-Key", ReportTestSupport.uniq("corr"))
                        .content(body))
                .andReturn().getResponse().getContentAsString());
        assertEquals(200, corr.path("code").asInt(), corr.toString());
        assertEquals(2, corr.path("data").path("newParseVersion").asInt());
        assertTrue(corr.path("data").path("reRuleRequired").asBoolean());

        JsonNode after = ReportTestSupport.detail(mvc, account, reportId);
        assertEquals(2, after.path("data").path("parseVersion").asInt());
        assertEquals(0, new BigDecimal("80").compareTo(
                ReportTestSupport.indicator(after, "ALT").path("normalizedValue").decimalValue()));
    }

    @Test
    void reviewResolveIsSeparateFromCorrection() throws Exception {
        String account = ReportTestSupport.uniq("acc-rvw");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-coverage-unknown.lab.txt");
        JsonNode created = ReportTestSupport.JSON.readTree(mvc.perform(post("/internal/report/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthTokens.INTERNAL_HEADER, "consult-internal")
                        .header("Idempotency-Key", ReportTestSupport.uniq("rvw"))
                        .content("{\"reportId\":\"" + reportId + "\",\"reason\":\"覆盖未知复核\",\"priority\":\"HIGH\"}"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(200, created.path("code").asInt(), created.toString());
        String reviewId = created.path("data").path("reviewTaskId").asText();

        JsonNode resolved = ReportTestSupport.JSON.readTree(mvc.perform(
                        post("/internal/report/reviews/" + reviewId + "/resolve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(AuthTokens.INTERNAL_HEADER, "consult-internal")
                                .header("Idempotency-Key", ReportTestSupport.uniq("rvw-res"))
                                .content("{\"resolution\":\"CONFIRMED\",\"note\":\"人工确认覆盖\"}"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(200, resolved.path("code").asInt(), resolved.toString());
        assertEquals("RESOLVED", resolved.path("data").path("status").asText());

        // 修订仍须走 corrections，resolve 不得偷偷升 parseVersion。
        JsonNode detail = ReportTestSupport.detail(mvc, account, reportId);
        assertEquals(1, detail.path("data").path("parseVersion").asInt());
    }

    @Test
    void correctionRequiresInternalToken() throws Exception {
        JsonNode res = ReportTestSupport.JSON.readTree(mvc.perform(post("/internal/report/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", ReportTestSupport.uniq("corr"))
                        .content("{\"reportId\":\"x\",\"parseVersionBase\":1,\"reviewerId\":\"r\",\"patches\":[]}"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(ErrorCode.LOGIN_REQUIRED.getCode(), res.path("code").asInt());
    }
}
