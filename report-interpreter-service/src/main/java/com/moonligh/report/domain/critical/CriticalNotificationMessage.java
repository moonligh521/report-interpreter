package com.moonligh.report.domain.critical;

/**
 * 交给通道的通知消息。不含指标全文与个人信息。
 */
public record CriticalNotificationMessage(
        String outboxId,
        String eventId,
        String reportId,
        String canonicalCode,
        String criticalLevel,
        String deliveryChannel,
        String idempotencyKey) {
}