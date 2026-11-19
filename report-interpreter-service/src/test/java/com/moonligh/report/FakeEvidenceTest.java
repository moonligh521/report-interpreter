package com.moonligh.report;

import static com.moonligh.report.GuardFixtures.ALT_EVIDENCE;
import static com.moonligh.report.GuardFixtures.ALT_HIGH;
import static com.moonligh.report.GuardFixtures.K_EVIDENCE;
import static com.moonligh.report.GuardFixtures.K_NORMAL;
import static com.moonligh.report.GuardFixtures.ctx;
import static com.moonligh.report.GuardFixtures.guideline;
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

/** AC-G5 / G-R4：假 evidenceId、无依据的指南口吻、跨指标引用 → UNSUPPORTED，且不外发。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SwitchableModelConfig.class)
class FakeEvidenceTest {

    private final InterpretationValidator validator = new InterpretationValidator();
    private final InterpretationContext context = ctx(true, List.of(ALT_HIGH), ALT_EVIDENCE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SwitchableInterpretationModel model;

    @Test
    void fabricatedEvidenceIdIsUnsupported() {
        BlockVerdict v = validator.validate(
                guideline(ALT_HIGH, "参考资料《伪造指南》：转氨酶需要立刻处理。", "evd-does-not-exist"), context);
        assertEquals(ValidationStatus.UNSUPPORTED, v.status());
        assertEquals("UNKNOWN_EVIDENCE_ID", v.reason());
    }

    @Test
    void guidelineBlockWithoutAnyEvidenceIsUnsupported() {
        BlockVerdict v = validator.validate(guideline(ALT_HIGH, "转氨酶偏高需要进一步检查。"), context);
        assertEquals(ValidationStatus.UNSUPPORTED, v.status());
        assertEquals("GUIDELINE_WITHOUT_EVIDENCE", v.reason());
    }

    @Test
    void guidelineToneWithoutEvidenceIsUnsupported() {
        BlockVerdict v = validator.validate(GuardFixtures.plain("根据最新指南，建议复查。"), context);
        assertEquals(ValidationStatus.UNSUPPORTED, v.status());
        assertEquals("GUIDELINE_TONE_WITHOUT_EVIDENCE", v.reason());
    }

    @Test
    void evidenceOfAnotherIndicatorIsUnsupported() {
        InterpretationContext two = ctx(true, List.of(ALT_HIGH, K_NORMAL), ALT_EVIDENCE, K_EVIDENCE);
        BlockVerdict v = validator.validate(
                guideline(ALT_HIGH, InterpretationTemplates.guidelineText(K_EVIDENCE), K_EVIDENCE.evidenceId()), two);
        assertEquals(ValidationStatus.UNSUPPORTED, v.status());
        assertEquals("EVIDENCE_INDICATOR_MISMATCH", v.reason());
    }

    @Test
    void genuineRecalledEvidenceIsValid() {
        BlockVerdict v = validator.validate(
                guideline(ALT_HIGH, InterpretationTemplates.guidelineText(ALT_EVIDENCE), ALT_EVIDENCE.evidenceId()),
                context);
        assertEquals(ValidationStatus.VALID, v.status());
    }

    @Test
    void guidelineToneWithRealEvidenceIsAllowed() {
        BlockVerdict v = validator.validate(
                new com.moonligh.report.domain.interpret.DraftBlock(
                        "g", com.moonligh.report.domain.interpret.ClaimType.GUIDELINE, "ALT", ClaimedStatus.NONE,
                        null, null, "参考资料《转氨酶阅读要点》（含指南摘要）：" + ALT_EVIDENCE.snippet(),
                        List.of(ALT_EVIDENCE.evidenceId())),
                context);
        assertEquals(ValidationStatus.VALID, v.status());
    }

    @Test
    void fakeEvidenceNeverReachesTheClientEndToEnd() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
        String account = ReportTestSupport.uniq("acc-fakeevd");
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-k-high.lab.txt");
        try {
            model.use((c, attempt) -> List.of(
                    guideline(GuardFixtures.K_HIGH, "参考资料《伪造指南》：血钾偏高必须立刻处理。", "evd-fake-1"),
                    GuardFixtures.fact(GuardFixtures.K_HIGH, ClaimedStatus.HIGH,
                            "K 检测值 5.8 mmol/L，高于参考范围（3.5–5.3 mmol/L）。")));
            JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

            assertEquals(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), res.path("code").asInt());
            assertEquals("UNSUPPORTED", res.path("data").path("status").asText());
            for (JsonNode b : res.path("data").path("blocks")) {
                for (JsonNode id : b.path("evidenceIds")) {
                    assertFalse(id.asText().contains("fake"), id.asText());
                }
            }
            for (JsonNode e : res.path("data").path("evidence")) {
                assertFalse(e.path("evidenceId").asText().contains("fake"));
            }
            assertFalse(InterpretationTestSupport.texts(res).stream().anyMatch(t -> t.contains("伪造")));
            Integer fakeRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM report_interpretation_evidence WHERE evidence_id LIKE '%fake%'", Integer.class);
            assertEquals(0, fakeRows);
            // 通过校验的真实块仍然保留，只丢弃造假块。
            assertTrue(InterpretationTestSupport.texts(res).stream().anyMatch(t -> t.contains("5.8")));
        } finally {
            model.reset();
        }
    }
}
