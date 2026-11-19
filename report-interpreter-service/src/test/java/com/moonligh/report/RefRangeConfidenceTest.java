package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.domain.rule.ApplicabilityResult;
import com.moonligh.report.domain.rule.CriticalRuleTable;
import com.moonligh.report.domain.rule.CriticalStatus;
import com.moonligh.report.domain.rule.CriticalValueEngine;
import com.moonligh.report.domain.rule.CriticalValueResult;
import com.moonligh.report.domain.rule.ReferenceRangeEngine;
import com.moonligh.report.domain.rule.ReferenceRangeEvidence;
import com.moonligh.report.domain.rule.ReferenceRangeResult;
import com.moonligh.report.domain.rule.ReferenceRangeResult.ReferenceSource;
import com.moonligh.report.domain.rule.ReferenceStatus;
import com.moonligh.report.domain.rule.RuleContext;
import com.moonligh.report.domain.rule.SafetyGate;

/** AC-R9：低置信（或不合格）原文区间不得用于 P0 判定。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefRangeConfidenceTest {

    private final ReferenceRangeEngine engine = new ReferenceRangeEngine();

    @Autowired
    private MockMvc mvc;

    private static ReferenceRangeEvidence range(String low, String high, String unit, double conf) {
        return new ReferenceRangeEvidence(
                low == null ? null : new BigDecimal(low), high == null ? null : new BigDecimal(high), unit, conf);
    }

    @Test
    void trustedReportRangeIsPreferred() {
        RuleContext ctx = RuleTestSupport.ctx("ALT", "86", "U/L", "SERUM", RuleTestSupport.HIGH_CONF,
                range("7", "40", "U/L", 0.98));
        ReferenceRangeResult r = engine.evaluate(ctx);
        assertEquals(ReferenceSource.REPORT_RANGE, r.source());
        assertEquals(ReferenceStatus.HIGH, r.status());
    }

    @Test
    void lowConfidenceReportRangeIsNotUsed() {
        // 报告区间被 OCR 污染成 0-100，置信度低：K=5.8 若强用则会误判 NORMAL。
        RuleContext ctx = RuleTestSupport.ctx("K", "5.8", "mmol/L", "SERUM", RuleTestSupport.HIGH_CONF,
                range("0", "100", "mmol/L", 0.40));
        ReferenceRangeResult r = engine.evaluate(ctx);
        assertNotEquals(ReferenceSource.REPORT_RANGE, r.source());
        assertEquals(ReferenceSource.BUILT_IN_STANDARD, r.source());
        assertEquals(ReferenceStatus.HIGH, r.status());
    }

    @Test
    void lowConfidenceRangeWithoutStandardIsUnknown() {
        RuleContext ctx = RuleTestSupport.ctx("ALT", "86", "U/L", "SERUM", RuleTestSupport.HIGH_CONF,
                range("7", "40", "U/L", 0.50));
        ReferenceRangeResult r = engine.evaluate(ctx);
        assertEquals(ReferenceStatus.UNKNOWN, r.status());
        assertEquals(ReferenceSource.NONE, r.source());
    }

    @Test
    void malformedOrUnitMismatchedRangeIsNotUsed() {
        RuleContext inverted = RuleTestSupport.ctx("ALT", "86", "U/L", "SERUM", RuleTestSupport.HIGH_CONF,
                range("40", "7", "U/L", 0.98));
        assertEquals(ReferenceStatus.UNKNOWN, engine.evaluate(inverted).status());
        // GLU 已从 mg/dL 换算成 mmol/L，而原文区间仍是 mg/dL：不可直接比较。
        RuleContext converted = RuleTestSupport.ctx("GLU", "5.0", "mmol/L", "SERUM", RuleTestSupport.HIGH_CONF,
                range("70", "110", "mg/dL", 0.98));
        assertEquals(ReferenceStatus.UNKNOWN, engine.evaluate(converted).status());
    }

    @Test
    void criticalJudgementNeverReadsReportRange() {
        CriticalRuleTable table = CriticalRuleTable.builtIn();
        CriticalValueEngine critical = new CriticalValueEngine(table);
        SafetyGate gate = new SafetyGate();
        // 即使报告区间（高置信）声称 0-10 是正常，K=6.8 仍必须命中危急阈值。
        RuleContext wide = RuleTestSupport.ctx("K", "6.8", "mmol/L", "SERUM", RuleTestSupport.HIGH_CONF,
                range("0", "10", "mmol/L", 0.99));
        ApplicabilityResult a = gate.applicability(wide, table.rulesFor("K"));
        CriticalValueResult r = critical.evaluate(wide, a);
        assertEquals(CriticalStatus.CRITICAL, r.status());
        // 区间置信度不同，危急结论相同。
        RuleContext noRange = RuleTestSupport.ctx("K", "6.8", "mmol/L", "SERUM", RuleTestSupport.HIGH_CONF,
                ReferenceRangeEvidence.NONE);
        assertEquals(r, critical.evaluate(noRange, gate.applicability(noRange, table.rulesFor("K"))));
    }

    @Test
    void endToEndLowConfidenceRangeFallsBackToStandardOrUnknown() throws Exception {
        String account = ReportTestSupport.uniq("acc-ref-lowconf");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-ref-lowconf.lab.txt", "张三");
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        JsonNode abnormals = detail.path("data").path("abnormals");
        // K：缺上限 → 原文区间不可信 → 内置标准区间判 HIGH；ALT：无区间且无标准区间 → UNKNOWN，不入 abnormals。
        assertEquals(1, abnormals.size());
        assertEquals("K", abnormals.get(0).path("canonicalCode").asText());
        assertEquals("HIGH", abnormals.get(0).path("referenceResult").asText());
        assertEquals("REF-STD-K", abnormals.get(0).path("ruleId").asText());
        assertEquals(false, detail.path("data").path("safeNormalAllowed").asBoolean());
    }
}