package com.moonligh.report.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.event.ReportEventStore;

@Component
public class ReportEventStoreImpl implements ReportEventStore {

    private final ReportEventMapper mapper;

    public ReportEventStoreImpl(ReportEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ReportEventRow row) {
        mapper.insert(row);
    }

    @Override
    public List<ReportEventRow> listAfterSequence(String reportId, long afterSequence, long minEpoch) {
        return mapper.listAfterSequence(reportId, afterSequence, minEpoch);
    }

    @Override
    public long maxSequence(String jobId) {
        return mapper.maxSequence(jobId);
    }

    @Override
    public Optional<ReportEventRow> findByEventId(String reportId, String eventId) {
        return Optional.ofNullable(mapper.findByEventId(reportId, eventId));
    }
}
