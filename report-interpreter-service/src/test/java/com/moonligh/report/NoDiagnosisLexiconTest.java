package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_EVIDENCE;
import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.ALT_NORMAL;
import static com.moonligh.report.GuardFixtures.K_CRITICAL;
import static com.moonligh.report.GuardFixtures.K_EVIDENCE;
import static com.moonligh.report.GuardFixtures.K_HIGH;
import static com.moonligh.report.GuardFixtures.K_NORMAL;
import static com.moonligh.report.GuardFixtures.ctx;
import static com.moonligh.report.GuardFixtures.plain;
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
import com.moonligh.report.domain.interpret.ClaimType;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.DiagnosisLexicon;
import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.IndicatorFact;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationTemplates;
import com.moonligh.report.domain.interpret.InterpretationValidator;
import com.moonligh.report.domain.interpret.ValidationStatus;
import com.moonligh.report.infrastructure.model.FakeInterpretationModel;

/** AC-G8 / G-R6：不下确诊、不开方；模板与 Fake 模型产物不含诊断词；命中即 SAFETY_VIOLATION。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class NoDiagnosisLexiconTest {

    private final InterpretationValidator validator = new InterpretationValidator();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SwitchableInterpretationModel model;

    @Test
    void lexiconIsNotEmptyAndEveryTermIsBlocked() {
        assertTrue(DiagnosisLexicon.size() >= 15);
        InterpretationContext c = ctx(true, List.of(ALT_HIGH));
        for (String term : DiagnosisLexicon.samples()) {
            assertTrue(DiagnosisLexicon.containsForbidden("可能" + term + "某病"), term);
            var v = validator.validate(plain("ALT 偏高，" + term + "。"), c);
            assertEquals(ValidationStatus.SAFETY_VIOLATION, v.status(), term);
            assertEquals("DIAGNOSIS_OR_PRESCRIPTION", v.reason(), term);
        }
    }

    @Test
    void negatedDisclaimerWordingIsNotForbidden() {
        assertFalse(DiagnosisLexicon.containsForbidden(InterpretationTemplates.DISCLAIMER));
        assertFalse(DiagnosisLexicon.containsForbidden("本解读不构成诊断，请咨询医生。"));
        assertFalse(DiagnosisLexicon.containsForbidden(null));
    }

    @Test
    void fixedTemplatesContainNoDiagnosisWordsAndPassTheValidator() {
        for (String t : List.of(
                InterpretationTemplates.DISCLAIMER, InterpretationTemplates.DEGRADE_NOTICE,
                InterpretationTemplates.PENDING_REVIEW_NOTICE,
                InterpretationTemplates.historicalCriticalNotice("K"),
                InterpretationTemplates.criticalAlertText(K_CRITICAL))) {
            assertFalse(DiagnosisLexicon.containsForbidden(t), t);
        }
        InterpretationContext c = ctx(true, List.of(K_CRITICAL, ALT_NORMAL), K_EVIDENCE);
        for (ClaimType type : List.of(ClaimType.DISCLAIMER, ClaimType.NOTICE)) {
            for (String t : List.of(
                    InterpretationTemplates.DISCLAIMER, InterpretationTemplates.DEGRADE_NOTICE,
                    InterpretationTemplates.PENDING_REVIEW_NOTICE,
                    InterpretationTemplates.historicalCriticalNotice("K"))) {
                var v = validator.validate(
                        new DraftBlock("t", type, null, ClaimedStatus.NONE, null, null, t, List.of()), c);
                assertEquals(ValidationStatus.VALID, v.status(), t + " / " + v.reason());
            }
        }
        var alert = validator.validate(new DraftBlock(
                "a", ClaimType.CRITICAL_ALERT, "K", ClaimedStatus.CRITICAL, K_CRITICAL.value(), K_CRITICAL.unit(),
                InterpretationTemplates.criticalAlertText(K_CRITICAL), List.of()), c);
        assertEquals(ValidationStatus.VALID, alert.status(), alert.reason());
    }

    @Test
    void fakeModelOutputIsDeterministicLexiconFreeAndValid() {
        FakeInterpretationModel fake = new FakeInterpretationModel();
        List<List<IndicatorFact>> scenarios = List.of(
                List.of(K_CRITICAL, ALT_HIGH), List.of(K_HIGH, ALT_NORMAL), List.of(K_NORMAL), List.of(ALT_HIGH));
        for (List<IndicatorFact> facts : scenarios) {
            for (boolean safeNormal : new boolean[] {true, false}) {
                for (boolean withEvidence : new boolean[] {true, false}) {
                    InterpretationContext c = withEvidence
                            ? ctx(safeNormal, facts, K_EVIDENCE, ALT_EVIDENCE)
                            : ctx(safeNormal, facts);
                    List<DraftBlock> a = fake.generate(c, 1);
                    List<DraftBlock> b = fake.generate(c, 2);
                    assertEquals(a, b, "同一输入必须得到同一输出");
                    for (DraftBlock block : a) {
                        assertFalse(DiagnosisLexicon.containsForbidden(block.text()), block.text());
                        var v = validator.validate(block, c);
                        assertEquals(ValidationStatus.VALID, v.status(), block.text() + " / " + v.reason());
                    }
                }
            }
        }
    }

    @Test
    void diagnosingModelIsTerminatedEndToEnd() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        String account = ReportTestSupport.uniq("acc-lexicon");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");
        try {
            model.use((c, attempt) -> List.of(
                    GuardFixtures.fact(ALT_HIGH, ClaimedStatus.HIGH, "ALT 检测值 85 U/L，高于参考范围（7–40 U/L）。"),
                    plain("你得了肝炎，建议服用保肝药。")));
            JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

            assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), res.path("code").asInt());
            assertEquals("SAFETY_VIOLATION", res.path("data").path("status").asText());
            List<String> texts = InterpretationTestSupport.texts(res);
            assertFalse(texts.stream().anyMatch(t -> t.contains("肝炎") || t.contains("服用")), texts.toString());
            // 终止后连同本该通过的块也不外发。
            assertFalse(texts.stream().anyMatch(t -> t.contains("85")), texts.toString());
        } finally {
            model.reset();
        }
    }
}
