package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.critical.CriticalEventStore;

@Component
public class CriticalEventStoreImpl implements CriticalEventStore {

    private final ReportCriticalEventMapper events;
    private final ReportCriticalNotificationOutboxMapper outbox;

    public CriticalEventStoreImpl(ReportCriticalEventMapper events, ReportCriticalNotificationOutboxMapper outbox) {
        this.events = events;
        this.outbox = outbox;
    }

    @Override
    public void insertEvent(ReportCriticalEventRow row) {
        events.insert(row);
    }

    @Override
    public Optional<ReportCriticalEventRow> findEventByIdempotency(String idempotencyKey) {
        return Optional.ofNullable(events.findByIdempotency(idempotencyKey));
    }

    @Override
    public Optional<ReportCriticalEventRow> findEventById(String eventId) {
        return Optional.ofNullable(events.findById(eventId));
    }

    @Override
    public List<ReportCriticalEventRow> listEventsByReport(String reportId) {
        return events.listByReport(reportId);
    }

    @Override
    public void insertOutbox(CriticalNotificationOutboxRow row) {
        outbox.insert(row);
    }

    @Override
    public void updateOutbox(CriticalNotificationOutboxRow row) {
        outbox.updateStatus(row);
    }

    @Override
    public Optional<CriticalNotificationOutboxRow> findOutboxByEvent(String eventId) {
        return Optional.ofNullable(outbox.findByEvent(eventId));
    }

    @Override
    public Optional<CriticalNotificationOutboxRow> findOutboxById(String outboxId) {
        return Optional.ofNullable(outbox.findById(outboxId));
    }

    @Override
    public List<CriticalNotificationOutboxRow> listRetryDue(Instant now, int limit) {
        return outbox.listRetryDue(now, limit);
    }
}