package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_job 行映射，禁止 Map。
 * reportType/requestHash/sourceText 由 parse 分支引入：请求哈希用于幂等冲突判定，
 * sourceText 仅在文本夹具场景保存，供重解析使用。
 */
public record ReportJobRow(
        String reportId,
        String accountId,
        String patientId,
        String idempotencyKey,
        String status,
        long epoch,
        String reportType,
        String requestHash,
        String sourceText,
        Instant createdAt,
        Instant updatedAt) {
}