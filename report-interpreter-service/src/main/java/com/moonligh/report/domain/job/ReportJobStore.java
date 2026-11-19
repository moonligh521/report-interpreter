package com.moonligh.report.domain.job;

import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportJobRow;

/**
 * 报告任务仓储端口。
 */
public interface ReportJobStore {

    void insert(ReportJobRow row);

    Optional<ReportJobRow> findById(String reportId);

    Optional<ReportJobRow> findByIdempotency(String accountId, String idempotencyKey);
}
