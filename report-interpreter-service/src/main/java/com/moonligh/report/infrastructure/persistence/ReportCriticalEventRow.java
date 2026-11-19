package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_critical_event 行。事件生命周期独立于解读。
 */
public record ReportCriticalEventRow(
        String eventId,
        String reportId,
        int parseVersion,
        String canonicalCode,
        String criticalLevel,
        String eventStatus,
        Instant detectionTime,
        String idempotencyKey) {
}