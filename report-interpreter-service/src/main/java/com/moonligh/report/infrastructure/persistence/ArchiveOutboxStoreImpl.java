package com.moonligh.report.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.archive.ArchiveOutboxStore;

@Component
public class ArchiveOutboxStoreImpl implements ArchiveOutboxStore {

    private final ReportArchiveOutboxMapper mapper;

    public ArchiveOutboxStoreImpl(ReportArchiveOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ReportArchiveOutboxRow row) {
        mapper.insert(row);
    }

    @Override
    public void updateStatus(String outboxId, String status, int attemptCount, String lastErrorCode) {
        mapper.updateStatus(outboxId, status, attemptCount, lastErrorCode);
    }

    @Override
    public Optional<ReportArchiveOutboxRow> findByEventId(String eventId) {
        return Optional.ofNullable(mapper.findByEventId(eventId));
    }

    @Override
    public Optional<ReportArchiveOutboxRow> findById(String outboxId) {
        return Optional.ofNullable(mapper.findById(outboxId));
    }

    @Override
    public Optional<ReportArchiveOutboxRow> findLatestByReport(String reportId) {
        return Optional.ofNullable(mapper.findLatestByReport(reportId));
    }

    @Override
    public List<ReportArchiveOutboxRow> listDue(int limit) {
        return mapper.listDue(limit);
    }
}
