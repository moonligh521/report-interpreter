package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_interpretation 行：一次解读及其钉死的五类版本。resultCode 为 null 表示 Result.code=200。
 */
public record ReportInterpretationRow(
        String interpretationId,
        String reportId,
        int parseVersion,
        String idempotencyKey,
        String status,
        Integer resultCode,
        String kbVersion,
        String ruleVersion,
        String promptVersion,
        String parserVersion,
        String schemaVersion,
        Instant createdAt) {
}
