package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;

/** POST/GET /api/report/reports/{id}/interpretation 的接口契约：Result.code、阻断、聚焦指标、强类型响应。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterpretationApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
    }

    @Test
    void abnormalReportGetsFactAndEvidenceBackedGuidelineBlocks() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-high");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        assertEquals("VALID", res.path("data").path("status").asText());
        assertTrue(res.path("data").path("interpretationId").asText().startsWith("itp_"));
        List<String> types = InterpretationTestSupport.claimTypes(res);
        assertEquals("FACT", types.get(0));
        assertTrue(types.contains("GUIDELINE"));
        assertEquals("DISCLAIMER", types.get(types.size() - 1));

        JsonNode fact = res.path("data").path("blocks").get(0);
        assertEquals("K 检测值 5.8 mmol/L，高于参考范围（3.5–5.3 mmol/L）。", fact.path("text").asText());
        assertEquals("VALID", fact.path("validationStatus").asText());

        // 每个 evidenceId 都能在 evidence 列表里找到，且来自已发布文档（不含 DRAFT）。
        JsonNode evidence = res.path("data").path("evidence");
        assertTrue(evidence.size() >= 1);
        for (JsonNode b : res.path("data").path("blocks")) {
            for (JsonNode id : b.path("evidenceIds")) {
                boolean found = false;
                for (JsonNode e : evidence) {
                    found |= e.path("evidenceId").asText().equals(id.asText());
                }
                assertTrue(found, id.asText());
            }
        }
        for (JsonNode e : evidence) {
            assertNotEquals("doc-k-draft", e.path("docId").asText());
            assertFalse(e.path("snippet").asText().isBlank());
        }
    }

    @Test
    void criticalReportReturns4105WithAlertFirstAndStillCarriesData() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-crit");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(ErrorCode.REPORT_CRITICAL_HIT.getCode(), res.path("code").asInt());
        assertEquals(4105, res.path("code").asInt());
        assertEquals("VALID", res.path("data").path("status").asText());
        assertEquals("CRITICAL_ALERT", InterpretationTestSupport.claimTypes(res).get(0));
        assertTrue(InterpretationTestSupport.texts(res).get(0).contains("请立即联系医生"));

        JsonNode read = InterpretationTestSupport.latest(mvc, account, reportId);
        assertEquals(4105, read.path("code").asInt());
        assertEquals(res.path("data").path("interpretationId").asText(), read.path("data").path("interpretationId").asText());
        assertEquals(InterpretationTestSupport.texts(res), InterpretationTestSupport.texts(read));
    }

    @Test
    void normalReportReturns200AndWithinRangeWording() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-normal");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        List<String> texts = InterpretationTestSupport.texts(res);
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("ALT") && t.contains("处于参考范围内")), texts.toString());
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("K") && t.contains("处于参考范围内")), texts.toString());
    }

    @Test
    void abnormalIndicatorDoesNotStopANormalIndicatorFromBeingCalledNormal() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-mixed");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-mixed.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        assertEquals("VALID", res.path("data").path("status").asText());
        List<String> texts = InterpretationTestSupport.texts(res);
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("ALT") && t.contains("高于参考范围")), texts.toString());
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("K 检测值") && t.contains("处于参考范围内")), texts.toString());
        assertFalse(texts.contains("本报告的规则判定尚待人工复核，暂不下结论。"), texts.toString());
    }

    @Test
    void focusCanonicalCodesNarrowTheInterpretation() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-focus");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(
                mvc, account, reportId, ReportTestSupport.uniq("itp"), "{\"focusCanonicalCodes\":[\"alt\"]}");

        assertEquals(200, res.path("code").asInt());
        List<String> texts = InterpretationTestSupport.texts(res);
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("ALT")), texts.toString());
        assertFalse(texts.stream().anyMatch(t -> t.startsWith("K 检测值")), texts.toString());
    }

    @Test
    void focusCannotHideACriticalIndicator() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-focus-crit");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");

        // 点名一个报告里不存在的指标，危急指标仍被纳入。
        JsonNode res = InterpretationTestSupport.trigger(
                mvc, account, reportId, ReportTestSupport.uniq("itp"), "{\"focusCanonicalCodes\":[\"ALT\"]}");

        assertEquals(4105, res.path("code").asInt());
        assertEquals("CRITICAL_ALERT", InterpretationTestSupport.claimTypes(res).get(0));
    }

    @Test
    void focusMatchingNothingIsRejected() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-focus-none");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(
                mvc, account, reportId, ReportTestSupport.uniq("itp"), "{\"focusCanonicalCodes\":[\"ZZZ\"]}");

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), res.path("code").asInt());
    }

    @Test
    void identityConflictIsBlockedWith4108AndNoInterpretation() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-conflict");
        String reportId = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", "李四")
                .path("data").path("reportId").asText();

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(ErrorCode.REPORT_IDENTITY_CONFLICT.getCode(), res.path("code").asInt());
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_interpretation WHERE report_id = ?", Integer.class, reportId);
        assertEquals(0, n);
        assertNotEquals(200, InterpretationTestSupport.latest(mvc, account, reportId).path("code").asInt());
    }

    @Test
    void getBeforeAnyInterpretationIsRejected() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-none");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");
        assertEquals(ErrorCode.PARAM_INVALID.getCode(),
                InterpretationTestSupport.latest(mvc, account, reportId).path("code").asInt());
    }

    @Test
    void otherAccountAndUnknownReportAreRejected() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-own");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");

        assertEquals(ErrorCode.PARAM_INVALID.getCode(),
                InterpretationTestSupport.trigger(mvc, ReportTestSupport.uniq("acc-other"), reportId).path("code").asInt());
        assertEquals(ErrorCode.PARAM_INVALID.getCode(),
                InterpretationTestSupport.trigger(mvc, account, "rpt_not_exist").path("code").asInt());
    }

    @Test
    void writeRequiresIdempotencyKeyAndLogin() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-hdr");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-matched-normal.lab.txt");

        mvc.perform(post("/api/report/reports/" + reportId + "/interpretation")
                        .header(AuthTokens.ACCOUNT_HEADER, account))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_MISSING.getCode()));
        mvc.perform(post("/api/report/reports/" + reportId + "/interpretation")
                        .header("Idempotency-Key", "k-1"))
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_REQUIRED.getCode()));
    }

    @Test
    void blocksPersistedExactlyAsReturned() throws Exception {
        String account = ReportTestSupport.uniq("acc-itp-persist");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);
        String id = res.path("data").path("interpretationId").asText();

        Integer blockCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_interpretation_block WHERE interpretation_id = ?", Integer.class, id);
        Integer evidenceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_interpretation_evidence WHERE interpretation_id = ?", Integer.class, id);
        assertEquals(res.path("data").path("blocks").size(), blockCount);
        assertEquals(res.path("data").path("evidence").size(), evidenceCount);
        Integer nonValid = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_interpretation_block WHERE interpretation_id = ? "
                        + "AND validation_status <> 'VALID'", Integer.class, id);
        assertEquals(0, nonValid);
    }
}
