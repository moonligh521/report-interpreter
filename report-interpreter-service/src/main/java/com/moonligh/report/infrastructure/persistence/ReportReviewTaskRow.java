package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_review_task 行：待人工复核任务。
 */
public record ReportReviewTaskRow(
        String reviewTaskId,
        String reportId,
        String reason,
        String priority,
        String status,
        String idempotencyKey,
        Instant createdAt) {
}