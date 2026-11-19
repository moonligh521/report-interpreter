package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_archive_record 行：档案消费方落地的「可投递档案」记录，event_id 为主键即消费幂等键。
 * 只保存摘要字段，不保存指标原文。
 */
public record ReportArchiveRecordRow(
        String eventId,
        String reportId,
        String interpretationId,
        String patientId,
        int parseVersion,
        String identityStatus,
        int indicatorCount,
        Instant createdAt) {
}
