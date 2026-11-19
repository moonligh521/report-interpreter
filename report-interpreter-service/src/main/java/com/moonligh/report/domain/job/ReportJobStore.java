package com.moonligh.report.domain.job;

import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportJobRow;

/**
 * 报告任务仓储端口。
 */
public interface ReportJobStore {

    void insert(ReportJobRow row);

    Optional<ReportJobRow> findById(String reportId);

    /** 加行锁读取，用于串行化重解析。 */
    Optional<ReportJobRow> lockById(String reportId);

    Optional<ReportJobRow> findByIdempotency(String accountId, String idempotencyKey);

    void updateStatus(String reportId, String status);
}