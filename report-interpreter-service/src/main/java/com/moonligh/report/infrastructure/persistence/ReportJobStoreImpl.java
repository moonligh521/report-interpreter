package com.moonligh.report.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.job.ReportJobStore;

@Component
public class ReportJobStoreImpl implements ReportJobStore {

    private final ReportJobMapper mapper;

    public ReportJobStoreImpl(ReportJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ReportJobRow row) {
        mapper.insert(row);
    }

    @Override
    public Optional<ReportJobRow> findById(String reportId) {
        return Optional.ofNullable(mapper.findById(reportId));
    }

    @Override
    public Optional<ReportJobRow> findByIdempotency(String accountId, String idempotencyKey) {
        return Optional.ofNullable(mapper.findByIdempotency(accountId, idempotencyKey));
    }

    @Override
    public Optional<ReportJobRow> lockById(String reportId) {
        return Optional.ofNullable(mapper.lockById(reportId));
    }

    @Override
    public void bumpEpoch(String reportId) {
        mapper.bumpEpoch(reportId);
    }

    @Override
    public void updateStatus(String reportId, String status) {
        mapper.updateStatus(reportId, status);
    }
}