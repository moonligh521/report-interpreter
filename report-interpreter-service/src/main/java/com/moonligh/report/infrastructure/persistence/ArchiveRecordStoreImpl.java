package com.moonligh.report.infrastructure.persistence;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.moonligh.report.domain.archive.ArchiveRecordStore;

@Component
public class ArchiveRecordStoreImpl implements ArchiveRecordStore {

    private final ReportArchiveRecordMapper mapper;

    public ArchiveRecordStoreImpl(ReportArchiveRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean insertIfAbsent(ReportArchiveRecordRow row) {
        if (mapper.findByEventId(row.eventId()) != null) {
            return false;
        }
        try {
            mapper.insert(row);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public Optional<ReportArchiveRecordRow> findByEventId(String eventId) {
        return Optional.ofNullable(mapper.findByEventId(eventId));
    }

    @Override
    public int countByReport(String reportId) {
        return mapper.countByReport(reportId);
    }
}
