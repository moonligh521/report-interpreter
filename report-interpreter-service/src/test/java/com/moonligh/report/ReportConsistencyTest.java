package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.ALT_NORMAL;
import static com.moonligh.report.GuardFixtures.K_NORMAL;
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
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationTemplates;
import com.moonligh.report.domain.interpret.InterpretationValidator;
import com.moonligh.report.domain.interpret.ValidationStatus;

/** AC-G6 / G-R5：与 Java 规则结论冲突的「正常」/「偏高」等表述 → CONFLICT。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class ReportConsistencyTest {

    private final InterpretationValidator validator = new InterpretationValidator();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SwitchableInterpretationModel model;

    @Test
    void sayingNormalOnJavaHighIsConflict() {
        InterpretationContext c = ctx(true, List.of(ALT_HIGH));
        BlockVerdict v = validator.validate(
                fact(ALT_HIGH, ClaimedStatus.NORMAL, "ALT 检测值 85 U/L，处于参考范围内（7–40 U/L）。"), c);
        assertEquals(ValidationStatus.CONFLICT, v.status());
        assertEquals("NORMAL_CLAIM_CONFLICT", v.reason());
    }

    @Test
    void unstructuredNormalWordingOnJavaHighIsConflict() {
        InterpretationContext c = ctx(true, List.of(ALT_HIGH));
        BlockVerdict v = validator.validate(
                fact(ALT_HIGH, ClaimedStatus.NONE, "ALT 检测值 85 U/L，结果正常。"), c);
        assertEquals(ValidationStatus.CONFLICT, v.status());
        assertEquals("NORMAL_TEXT_CONFLICT", v.reason());
    }

    @Test
    void saidHighButJavaSaysNormalIsConflict() {
        InterpretationContext c = ctx(true, List.of(ALT_NORMAL));
        BlockVerdict v = validator.validate(
                fact(ALT_NORMAL, ClaimedStatus.HIGH, "ALT 检测值 30 U/L，高于参考范围（7–40 U/L）。"), c);
        assertEquals(ValidationStatus.CONFLICT, v.status());
        assertEquals("HIGH_CLAIM_CONFLICT", v.reason());
    }

    @Test
    void saidLowButJavaSaysHighIsConflict() {
        InterpretationContext c = ctx(true, List.of(ALT_HIGH));
        BlockVerdict v = validator.validate(
                fact(ALT_HIGH, ClaimedStatus.LOW, "ALT 检测值 85 U/L，低于参考范围（7–40 U/L）。"), c);
        assertEquals(ValidationStatus.CONFLICT, v.status());
        assertEquals("LOW_CLAIM_CONFLICT", v.reason());
    }

    @Test
    void normalClaimNotAllowedWhenSafeNormalIsFalse() {
        InterpretationContext c = ctx(false, List.of(K_NORMAL));
        BlockVerdict v = validator.validate(
                fact(K_NORMAL, ClaimedStatus.NORMAL, "K 检测值 4.2 mmol/L，处于参考范围内（3.5–5.3 mmol/L）。"), c);
        assertEquals(ValidationStatus.CONFLICT, v.status());
    }

    @Test
    void consistentStatementsAreValid() {
        InterpretationContext c = ctx(true, List.of(ALT_HIGH, K_NORMAL));
        assertEquals(ValidationStatus.VALID, validator.validate(
                fact(ALT_HIGH, ClaimedStatus.HIGH, InterpretationTemplates.factText(ALT_HIGH, true)), c).status());
        assertEquals(ValidationStatus.VALID, validator.validate(
                fact(K_NORMAL, ClaimedStatus.NORMAL, InterpretationTemplates.factText(K_NORMAL, true)), c).status());
    }

    @Test
    void aboveNormalPhrasingIsNotMistakenForNormalClaim() {
        InterpretationContext c = ctx(true, List.of(ALT_HIGH));
        BlockVerdict v = validator.validate(
                fact(ALT_HIGH, ClaimedStatus.HIGH, "ALT 检测值 85 U/L，高于正常上限（40 U/L）。"), c);
        assertEquals(ValidationStatus.VALID, v.status());
    }

    @Test
    void unsafeReportNeverSaysWithinRangeEndToEnd() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        // 覆盖未知：K 4.2 本身在区间内，但报告级不允许「安全正常」。
        String account = ReportTestSupport.uniq("acc-consist");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rules-coverage-unknown.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        List<String> texts = InterpretationTestSupport.texts(res);
        assertFalse(texts.stream().anyMatch(t -> t.contains("范围内") || t.contains("正常")), texts.toString());
        assertTrue(texts.contains(InterpretationTemplates.PENDING_REVIEW_NOTICE), texts.toString());
        assertTrue(texts.stream().anyMatch(t -> t.contains("待人工复核，暂不下结论")));
    }

    @Test
    void conflictingModelOutputIsBlockedEndToEnd() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        String account = ReportTestSupport.uniq("acc-consist-model");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        try {
            model.use((c, attempt) -> List.of(
                    fact(ALT_HIGH, ClaimedStatus.NORMAL, "ALT 检测值 85 U/L，处于参考范围内（7–40 U/L）。")));
            JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

            assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), res.path("code").asInt());
            assertEquals("CONFLICT", res.path("data").path("status").asText());
            assertFalse(InterpretationTestSupport.texts(res).stream().anyMatch(t -> t.contains("范围内")));
        } finally {
            model.reset();
        }
    }
}
