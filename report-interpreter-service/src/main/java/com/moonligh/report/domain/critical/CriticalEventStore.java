package com.moonligh.report.domain.critical;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportCriticalEventRow;

/**
 * 危急事件与通知 Outbox 仓储端口。
 */
public interface CriticalEventStore {

    void insertEvent(ReportCriticalEventRow row);

    Optional<ReportCriticalEventRow> findEventByIdempotency(String idempotencyKey);

    Optional<ReportCriticalEventRow> findEventById(String eventId);

    List<ReportCriticalEventRow> listEventsByReport(String reportId);

    void insertOutbox(CriticalNotificationOutboxRow row);

    void updateOutbox(CriticalNotificationOutboxRow row);

    Optional<CriticalNotificationOutboxRow> findOutboxByEvent(String eventId);

    Optional<CriticalNotificationOutboxRow> findOutboxById(String outboxId);

    List<CriticalNotificationOutboxRow> listRetryDue(Instant now, int limit);
}