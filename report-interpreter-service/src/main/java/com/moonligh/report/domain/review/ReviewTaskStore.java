package com.moonligh.report.domain.review;

import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportReviewTaskRow;

/**
 * 人工复核任务仓储端口。
 */
public interface ReviewTaskStore {

    void insert(ReportReviewTaskRow row);

    Optional<ReportReviewTaskRow> findByIdempotency(String idempotencyKey);
}