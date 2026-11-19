package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.K_CRITICAL;
import static com.moonligh.report.GuardFixtures.ctx;
import static com.moonligh.report.GuardFixtures.fact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.moonligh.report.domain.interpret.ClaimType;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationTemplates;
import com.moonligh.report.domain.interpret.InterpretationValidator;
import com.moonligh.report.domain.interpret.ValidationStatus;

/** AC-G4 / G-R3：把危急值降级表述（高 / 低 / 正常 / 不严重）→ SAFETY_VIOLATION，危急提醒仍由 Java 给出。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class ValidatorCriticalDowngradeTest {

    private final InterpretationValidator validator = new InterpretationValidator();
    private final InterpretationContext context = ctx(true, List.of(K_CRITICAL));

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
    void claimingMerelyHighOnCriticalIsSafetyViolation() {
        BlockVerdict v = check(fact(K_CRITICAL, ClaimedStatus.HIGH, "K 检测值 6.8 mmol/L，高于参考范围（3.5–5.3 mmol/L）。"));
        assertEquals(ValidationStatus.SAFETY_VIOLATION, v.status());
        assertEquals("CRITICAL_DOWNGRADE", v.reason());
    }

    @Test
    void claimingNormalOnCriticalIsSafetyViolation() {
        BlockVerdict v = check(fact(K_CRITICAL, ClaimedStatus.NORMAL, "K 检测值 6.8 mmol/L，处于参考范围内。"));
        assertEquals(ValidationStatus.SAFETY_VIOLATION, v.status());
    }

    @Test
    void factBlockWithoutCriticalClaimIsSafetyViolation() {
        BlockVerdict v = check(fact(K_CRITICAL, ClaimedStatus.NONE, "K 检测值 6.8 mmol/L。"));
        assertEquals(ValidationStatus.SAFETY_VIOLATION, v.status());
    }

    @Test
    void softeningPhrasesOnCriticalAreSafetyViolation() {
        for (String phrase : List.of("问题不大", "无需紧急处理", "不严重", "轻度升高", "可继续观察")) {
            BlockVerdict v = check(new DraftBlock(
                    "b", ClaimType.GUIDELINE, "K", ClaimedStatus.NONE, null, null,
                    "K 检测值 6.8 mmol/L，" + phrase + "。", List.of()));
            assertEquals(ValidationStatus.SAFETY_VIOLATION, v.status(), phrase);
        }
    }

    @Test
    void honestCriticalStatementIsValid() {
        DraftBlock b = new DraftBlock(
                "b", ClaimType.CRITICAL_ALERT, "K", ClaimedStatus.CRITICAL, K_CRITICAL.value(), "mmol/L",
                InterpretationTemplates.criticalAlertText(K_CRITICAL), List.of());
        assertEquals(ValidationStatus.VALID, check(b).status());
    }

    @Test
    void claimingCriticalOnNonCriticalIsConflict() {
        InterpretationContext normal = ctx(true, List.of(GuardFixtures.K_NORMAL));
        DraftBlock b = new DraftBlock(
                "b", ClaimType.FACT, "K", ClaimedStatus.CRITICAL, GuardFixtures.K_NORMAL.value(), "mmol/L",
                "K 检测值 4.2 mmol/L，达到危急值水平。", List.of());
        assertEquals(ValidationStatus.CONFLICT, validator.validate(b, normal).status());
    }

    @Test
    void downgradingModelIsBlockedAndJavaAlertSurvivesEndToEnd() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
        String account = ReportTestSupport.uniq("acc-downgrade");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-critical-k.lab.txt");
        try {
            model.use((c, attempt) -> List.of(
                    fact(K_CRITICAL, ClaimedStatus.HIGH, "K 检测值 6.8 mmol/L，略高，问题不大。")));
            JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

            assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), res.path("code").asInt());
            assertEquals("SAFETY_VIOLATION", res.path("data").path("status").asText());
            List<String> texts = InterpretationTestSupport.texts(res);
            assertFalse(texts.stream().anyMatch(t -> t.contains("问题不大")), texts.toString());
            // Java 模板的危急提醒不依赖模型，仍然外发。
            assertEquals("CRITICAL_ALERT", InterpretationTestSupport.claimTypes(res).get(0));
            assertTrue(texts.get(0).contains("危急值"), texts.toString());
        } finally {
            model.reset();
        }
    }
}
