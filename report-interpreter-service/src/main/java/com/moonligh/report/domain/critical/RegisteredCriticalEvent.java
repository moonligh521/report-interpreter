package com.moonligh.report.domain.critical;

import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportCriticalEventRow;

/**
 * 登记结果；created=false 表示命中幂等键，复用既有事件。
 */
public record RegisteredCriticalEvent(
        ReportCriticalEventRow event, CriticalNotificationOutboxRow outbox, boolean created) {
}