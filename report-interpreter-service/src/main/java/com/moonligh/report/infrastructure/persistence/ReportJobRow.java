package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_job 行映射，禁止 Map。
 */
public record ReportJobRow(
        String reportId,
        String accountId,
        String patientId,
        String idempotencyKey,
        String status,
        long epoch,
        Instant createdAt,
        Instant updatedAt) {
}
