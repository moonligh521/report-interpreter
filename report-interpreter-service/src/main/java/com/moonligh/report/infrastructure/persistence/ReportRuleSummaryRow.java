package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_rule_summary 行：某解析版本的身份、覆盖与报告级安全结论。
 * identityDetail 不含姓名；coverageEvidenceJson 由 CriticalCoverageEvidence 序列化。
 */
public record ReportRuleSummaryRow(
        String reportId,
        int parseVersion,
        String ruleVersion,
        String identityStatus,
        String identityDetail,
        String coverageStatus,
        String coverageEvidenceJson,
        String outcomeStatus,
        boolean archiveBlocked,
        boolean safeNormalAllowed,
        Integer blockCode,
        Instant createdAt) {
}