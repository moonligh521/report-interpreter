package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_archive_outbox 行。与危急通知 Outbox 分表、分状态机；event_id 唯一（同一事件只入队一次）。
 * payloadJson 是 ArchiveOutboxMessage 的序列化形态，含完整指标，禁止写入日志。
 */
public record ReportArchiveOutboxRow(
        String outboxId,
        String reportId,
        String eventId,
        String schemaVersion,
        String status,
        int attemptCount,
        String lastErrorCode,
        String payloadJson,
        Instant createdAt,
        Instant updatedAt) {
}
