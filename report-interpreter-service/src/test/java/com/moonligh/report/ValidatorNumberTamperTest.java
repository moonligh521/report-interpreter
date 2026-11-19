package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.ALT_NORMAL;
import static com.moonligh.report.GuardFixtures.ctx;
import static com.moonligh.report.GuardFixtures.fact;
import static com.moonligh.report.GuardFixtures.factWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.SwitchableModelConfig.SwitchableInterpretationModel;
import com.moonligh.report.domain.interpret.BlockVerdict;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationValidator;
import com.moonligh.report.domain.interpret.ValidationStatus;

/** AC-G3 / G-R3：改数（值 / 单位 / 区间）生成 → 拦截（RETRYABLE，重试耗尽后不外发）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class ValidatorNumberTamperTest {

    private final InterpretationValidator validator = new InterpretationValidator();
    private final InterpretationContext context = ctx(true, List.of(ALT_HIGH));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SwitchableInterpretationModel model;

    private BlockVerdict check(DraftBlock b) {
        return validator.validate(b, context);
    }

    @Test
    void faithfulBlockIsValid() {
        DraftBlock b = fact(ALT_HIGH, ClaimedStatus.HIGH, "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。");
        assertEquals(ValidationStatus.VALID, check(b).status());
    }

    @Test
    void structuredValueTamperIsRetryable() {
        DraftBlock b = factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("58"), "U/L",
                "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。");
        BlockVerdict v = check(b);
        assertEquals(ValidationStatus.RETRYABLE, v.status());
        assertEquals("NUMBER_TAMPER", v.reason());
    }

    @Test
    void textValueTamperIsRetryable() {
        DraftBlock b = fact(ALT_HIGH, ClaimedStatus.HIGH, "ALT 检测值 58 U/L，高于参考范围（7–40 U/L）。");
        assertEquals(ValidationStatus.RETRYABLE, check(b).status());
    }

    @Test
    void referenceRangeTamperIsRetryable() {
        DraftBlock b = fact(ALT_HIGH, ClaimedStatus.HIGH, "ALT 检测值 85 U/L，高于参考范围（7–90 U/L）。");
        assertEquals(ValidationStatus.RETRYABLE, check(b).status());
    }

    @Test
    void unitTamperIsRetryable() {
        DraftBlock b = factWith(ALT_HIGH, ClaimedStatus.HIGH, ALT_HIGH.value(), "mg/dL",
                "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。");
        BlockVerdict v = check(b);
        assertEquals(ValidationStatus.RETRYABLE, v.status());
        assertEquals("UNIT_TAMPER", v.reason());
    }

    @Test
    void numericallyEqualFormattingIsNotTamper() {
        DraftBlock b = factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("85.000000"), "u/l",
                "ALT 检测值 85.0 U/L，高于参考范围（7.0–40 U/L）。");
        assertEquals(ValidationStatus.VALID, check(b).status());
    }

    @Test
    void invertedUnknownNumberInPlainBlockIsRetryable() {
        assertEquals(ValidationStatus.RETRYABLE, check(GuardFixtures.plain("近期复查约 3 次即可。")).status());
    }

    @Test
    void numberOfAnotherIndicatorDoesNotCountAsAllowed() {
        InterpretationContext two = ctx(true, List.of(ALT_NORMAL, GuardFixtures.K_NORMAL));
        // 声明的是 ALT，但文本里写了 K 的数值 4.2 且没有提到 K。
        DraftBlock b = fact(ALT_NORMAL, ClaimedStatus.NORMAL, "ALT 检测值 4.2 U/L，处于参考范围内（7–40 U/L）。");
        assertEquals(ValidationStatus.RETRYABLE, validator.validate(b, two).status());
    }

    @Test
    void tamperedModelOutputIsNeverEmittedEndToEnd() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
        String account = ReportTestSupport.uniq("acc-tamper");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        try {
            model.use((c, attempt) -> List.of(
                    factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("58"), "U/L",
                            "ALT 检测值 58 U/L，高于参考范围（7–40 U/L）。")));
            JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

            assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), res.path("code").asInt());
            assertEquals("RETRYABLE", res.path("data").path("status").asText());
            for (String t : InterpretationTestSupport.texts(res)) {
                assertEquals(false, t.contains("58"), t);
            }
        } finally {
            model.reset();
        }
    }

    @Test
    void tamperOnFirstAttemptThenFaithfulRetryIsAccepted() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        String account = ReportTestSupport.uniq("acc-tamper-retry");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        try {
            model.use((c, attempt) -> List.of(attempt == 1
                    ? factWith(ALT_HIGH, ClaimedStatus.HIGH, new BigDecimal("58"), "U/L",
                            "ALT 检测值 58 U/L，高于参考范围（7–40 U/L）。")
                    : fact(ALT_HIGH, ClaimedStatus.HIGH, "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。")));
            JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

            assertEquals(200, res.path("code").asInt());
            assertEquals("DEGRADED", res.path("data").path("status").asText());
            assertEquals(true, InterpretationTestSupport.texts(res).stream().anyMatch(t -> t.contains("85")));
        } finally {
            model.reset();
        }
    }
}
