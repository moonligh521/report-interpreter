package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_critical_notification_outbox 行。与档案 Outbox 不共用状态机。
 */
public record CriticalNotificationOutboxRow(
        String outboxId,
        String eventId,
        String notificationStatus,
        String deliveryChannel,
        String idempotencyKey,
        int attemptCount,
        Instant nextRetryAt,
        String lastErrorCode,
        Instant deliveredAt) {
}