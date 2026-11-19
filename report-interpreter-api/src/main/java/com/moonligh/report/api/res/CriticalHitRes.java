package com.moonligh.report.api.res;

/**
 * 危急命中。eventStatus 为事件状态（DETECTED），notificationStatus 为通知 Outbox 状态，二者分离。
 */
public record CriticalHitRes(
        String eventId,
        String canonicalCode,
        String criticalResult,
        String eventStatus,
        String notificationStatus) {
}