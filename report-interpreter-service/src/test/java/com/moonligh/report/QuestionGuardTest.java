package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.factWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.report.SwitchableModelConfig.SwitchableInterpretationModel;
import com.moonligh.report.domain.interpret.ClaimedStatus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** AC-M / M-R3：追问基于已解析事实；改数 → 4107。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class QuestionGuardTest {

    private static final String TAMPERED = "ALT 检测值 58 U/L，高于参考范围（7–40 U/L）。";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SwitchableInterpretationModel model;

    @AfterEach
    void reset() {
        model.reset();
    }

    @Test
    void happyPathAnswersFromParsedFacts() throws Exception {
        String account = ReportTestSupport.uniq("acc-q-ok");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        JsonNode res = ask(account, reportId, "{\"question\":\"ALT 偏高说明什么？\",\"focusCanonicalCodes\":[\"ALT\"]}");
        assertEquals(200, res.path("code").asInt(), res.toString());
        assertTrue(res.path("data").path("answerId").asText().startsWith("ans_"));
        assertFalse(res.path("data").path("blocks").isEmpty());
        assertTrue(res.path("data").has("pinnedVersions"));
    }

    @Test
    void tamperedNumbersReturn4107() throws Exception {
        String account = ReportTestSupport.uniq("acc-q-bad");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        model.use((c, attempt) -> List.of(
                factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("58"), "U/L", TAMPERED)));
        JsonNode res = ask(account, reportId, "{\"question\":\"ALT 是多少？\",\"focusCanonicalCodes\":[\"ALT\"]}");
        assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), res.path("code").asInt(), res.toString());
        assertFalse(res.toString().contains("58 U/L"));
    }

    private JsonNode ask(String account, String reportId, String body) throws Exception {
        return ReportTestSupport.JSON.readTree(mvc.perform(post("/api/report/reports/" + reportId + "/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AuthTokens.ACCOUNT_HEADER, account)
                        .header("Idempotency-Key", ReportTestSupport.uniq("q"))
                        .content(body))
                .andReturn().getResponse().getContentAsString());
    }
}
