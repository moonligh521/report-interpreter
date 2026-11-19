package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_event 行。jobId 即报告任务 ID（report_job.report_id）；(jobId, sequence) 唯一；
 * payloadJson 是强类型 ReportEventPayload 的序列化形态。
 */
public record ReportEventRow(
        String eventId,
        String reportId,
        String jobId,
        long sequence,
        String eventType,
        long epoch,
        String payloadJson,
        Instant createdAt) {
}
