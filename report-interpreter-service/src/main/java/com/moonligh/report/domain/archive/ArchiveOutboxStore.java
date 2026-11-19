package com.moonligh.report.domain.archive;

import java.util.List;
import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;

/**
 * 档案 Outbox 仓储端口（与危急通知 Outbox 的 CriticalEventStore 完全分离）。
 */
public interface ArchiveOutboxStore {

    void insert(ReportArchiveOutboxRow row);

    void updateStatus(String outboxId, String status, int attemptCount, String lastErrorCode);

    Optional<ReportArchiveOutboxRow> findByEventId(String eventId);

    Optional<ReportArchiveOutboxRow> findById(String outboxId);

    Optional<ReportArchiveOutboxRow> findLatestByReport(String reportId);

    List<ReportArchiveOutboxRow> listDue(int limit);
}
