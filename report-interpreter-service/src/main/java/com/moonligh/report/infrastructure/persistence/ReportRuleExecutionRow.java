package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_rule_execution 行：一条指标的规则执行快照（RuleContext JSON + 三类结果）。
 */
public record ReportRuleExecutionRow(
        String id,
        String reportId,
        int parseVersion,
        String ruleVersion,
        String indicatorId,
        String canonicalCode,
        String referenceRuleId,
        String criticalRuleId,
        String contextSnapshotJson,
        String referenceResult,
        String criticalResult,
        String applicabilityResult,
        Instant createdAt) {
}