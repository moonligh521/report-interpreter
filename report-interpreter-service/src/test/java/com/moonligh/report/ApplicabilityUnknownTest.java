package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.domain.rule.ApplicabilityResult;
import com.moonligh.report.domain.rule.ApplicabilityStatus;
import com.moonligh.report.domain.rule.CriticalRuleTable;
import com.moonligh.report.domain.rule.CriticalStatus;
import com.moonligh.report.domain.rule.CriticalValueEngine;
import com.moonligh.report.domain.rule.CriticalValueResult;
import com.moonligh.report.domain.rule.ReferenceRangeEvidence;
import com.moonligh.report.domain.rule.RuleCondition;
import com.moonligh.report.domain.rule.RuleContext;
import com.moonligh.report.domain.rule.SafetyGate;

/** AC-R5：必要条件缺失 → UNKNOWN / 待复核，绝不是 NOT_CRITICAL。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicabilityUnknownTest {

    private final SafetyGate gate = new SafetyGate();
    private final CriticalRuleTable table = CriticalRuleTable.builtIn();
    private final CriticalValueEngine engine = new CriticalValueEngine(table);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private CriticalValueResult judge(RuleContext ctx) {
        ApplicabilityResult a = gate.applicability(ctx, table.rulesFor(ctx.canonicalCode()));
        return engine.evaluate(ctx, a);
    }

    @Test
    void missingSpecimenTypeIsUnknownNotNormal() {
        RuleContext ctx = RuleTestSupport.ctx(
                "K", "7.0", "mmol/L", null, RuleTestSupport.HIGH_CONF, ReferenceRangeEvidence.NONE);
        ApplicabilityResult a = gate.applicability(ctx, table.rulesFor("K"));
        assertEquals(ApplicabilityStatus.UNKNOWN, a.status());
        assertTrue(a.missing().contains(RuleCondition.SPECIMEN_TYPE));
        CriticalValueResult r = judge(ctx);
        assertEquals(CriticalStatus.UNKNOWN, r.status());
        assertNotEquals(CriticalStatus.NOT_CRITICAL, r.status());
    }

    @Test
    void unitMismatchAndMissingValueAreUnknown() {
        RuleContext badUnit = RuleTestSupport.ctx(
                "K", "7.0", "g/L", "SERUM", RuleTestSupport.HIGH_CONF, ReferenceRangeEvidence.NONE);
        assertEquals(CriticalStatus.UNKNOWN, judge(badUnit).status());
        RuleContext noValue = RuleTestSupport.ctx(
                "K", null, null, "SERUM", RuleTestSupport.HIGH_CONF, ReferenceRangeEvidence.NONE);
        ApplicabilityResult a = gate.applicability(noValue, table.rulesFor("K"));
        assertTrue(a.missing().containsAll(List.of(RuleCondition.VALUE, RuleCondition.UNIT)));
        assertEquals(CriticalStatus.UNKNOWN, judge(noValue).status());
    }

    @Test
    void lowFieldConfidenceIsUnknown() {
        FieldConfidence lowValue = new FieldConfidence(1, 1, 1, 0.98, 0.98, 0.55, 0.98, 0.98);
        RuleContext ctx = RuleTestSupport.ctx(
                "K", "7.0", "mmol/L", "SERUM", lowValue, ReferenceRangeEvidence.NONE);
        ApplicabilityResult a = gate.applicability(ctx, table.rulesFor("K"));
        assertTrue(a.missing().contains(RuleCondition.FIELD_CONFIDENCE));
        assertEquals(CriticalStatus.UNKNOWN, judge(ctx).status());
    }

    @Test
    void indicatorWithoutRuleIsNoRuleNotNotCritical() {
        RuleContext alt = RuleTestSupport.ctx(
                "ALT", "86", "U/L", "SERUM", RuleTestSupport.HIGH_CONF, ReferenceRangeEvidence.NONE);
        CriticalValueResult r = judge(alt);
        assertEquals(CriticalStatus.NO_RULE, r.status());
        assertNotEquals(CriticalStatus.NOT_CRITICAL, r.status());
    }

    @Test
    void endToEndUnmappedUnitNeedsReviewAndPersistsUnknown() throws Exception {
        String account = ReportTestSupport.uniq("acc-app-unknown");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-unit-unmapped.lab.txt", "张三");
        String reportId = created.path("data").path("reportId").asText();
        assertEquals("NEED_MANUAL_REVIEW", created.path("data").path("status").asText());

        JsonNode detail = ReportTestSupport.detail(mvc, account, reportId);
        assertEquals(4106, detail.path("code").asInt());
        assertEquals(0, detail.path("data").path("criticalHits").size());
        assertEquals(false, detail.path("data").path("safeNormalAllowed").asBoolean());

        String critical = jdbc.queryForObject(
                "SELECT critical_result FROM report_rule_execution WHERE report_id = ?", String.class, reportId);
        String applicability = jdbc.queryForObject(
                "SELECT applicability_result FROM report_rule_execution WHERE report_id = ?", String.class, reportId);
        assertEquals("UNKNOWN", critical);
        assertEquals("UNKNOWN", applicability);
    }
}