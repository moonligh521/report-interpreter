package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.domain.rule.ReferenceRangeEvidence;
import com.moonligh.report.domain.rule.RuleContext;
import com.moonligh.report.domain.rule.RuleExecutionStore;
import com.moonligh.report.domain.rule.RuleSetVersion;
import com.moonligh.report.infrastructure.persistence.ReportRuleExecutionRow;
import com.moonligh.report.service.RuleContextJson;

/** AC-R8：report_rule_execution 保存 contextSnapshot + 三类结果 + 规则版本；快照读回为 RuleContext。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuleExecutionSnapshotTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RuleExecutionStore executions;

    @Test
    void snapshotRoundTripsAsRuleContextRecord() {
        RuleContext original = new RuleContext(
                "ind-1", "K", new BigDecimal("6.8"), "mmol/L", "SERUM", 40, "F", null, "H1", null,
                Instant.parse("2026-11-19T01:02:03Z"), RuleTestSupport.HIGH_CONF,
                new ReferenceRangeEvidence(new BigDecimal("3.5"), new BigDecimal("5.3"), "mmol/L", 0.98));
        RuleContext back = RuleContextJson.read(RuleContextJson.write(original));
        assertEquals(original, back);
    }

    @Test
    void everyIndicatorGetsOneSnapshotRowWithThreeResults() throws Exception {
        String account = ReportTestSupport.uniq("acc-snap");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-critical-k.lab.txt", "张三");
        String reportId = created.path("data").path("reportId").asText();

        List<ReportRuleExecutionRow> rows = executions.listByReport(reportId);
        assertEquals(1, rows.size());
        ReportRuleExecutionRow row = rows.get(0);
        assertEquals(1, row.parseVersion());
        assertEquals(RuleSetVersion.CURRENT, row.ruleVersion());
        assertEquals("K", row.canonicalCode());
        assertEquals("HIGH", row.referenceResult());
        assertEquals("CRITICAL_HIGH", row.criticalResult());
        assertEquals("APPLICABLE", row.applicabilityResult());
        assertEquals("CRIT-K-HIGH", row.criticalRuleId());
        assertEquals("REF-REPORT-RANGE", row.referenceRuleId());
        assertNotNull(row.createdAt());

        RuleContext ctx = RuleContextJson.read(row.contextSnapshotJson());
        assertEquals(row.indicatorId(), ctx.indicatorId());
        assertEquals("K", ctx.canonicalCode());
        assertEquals(0, new BigDecimal("6.8").compareTo(ctx.value()));
        assertEquals("mmol/L", ctx.unit());
        assertEquals("SERUM", ctx.specimenType());
        assertNull(ctx.age());
        assertNull(ctx.specimenTime());
        assertNotNull(ctx.fieldConfidence());
        assertEquals(0, new BigDecimal("3.5").compareTo(ctx.referenceRangeEvidence().low()));
        assertEquals(0, new BigDecimal("5.3").compareTo(ctx.referenceRangeEvidence().high()));
        // 快照不含姓名等身份信息。
        assertFalse(row.contextSnapshotJson().contains("张三"));
    }

    @Test
    void reparseAppendsNewVersionedSnapshotsAndKeepsHistory() throws Exception {
        String account = ReportTestSupport.uniq("acc-snap-v");
        JsonNode created = ReportTestSupport.createRules(mvc, account, "rules-matched-normal.lab.txt", "张三");
        String reportId = created.path("data").path("reportId").asText();
        ReportTestSupport.reparse(mvc, account, ReportTestSupport.uniq("re"), reportId);

        List<ReportRuleExecutionRow> rows = executions.listByReport(reportId);
        assertEquals(4, rows.size());
        assertEquals(2, rows.stream().filter(r -> r.parseVersion() == 1).count());
        assertEquals(2, rows.stream().filter(r -> r.parseVersion() == 2).count());
        assertTrue(rows.stream().anyMatch(r -> "NO_RULE".equals(r.criticalResult())));
        assertTrue(rows.stream().anyMatch(r -> "NOT_CRITICAL".equals(r.criticalResult())));
    }
}