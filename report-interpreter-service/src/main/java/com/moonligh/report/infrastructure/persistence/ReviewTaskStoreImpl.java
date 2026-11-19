package com.moonligh.report.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.review.ReviewTaskStore;

@Component
public class ReviewTaskStoreImpl implements ReviewTaskStore {

    private final ReportReviewTaskMapper mapper;

    public ReviewTaskStoreImpl(ReportReviewTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ReportReviewTaskRow row) {
        mapper.insert(row);
    }

    @Override
    public Optional<ReportReviewTaskRow> findByIdempotency(String idempotencyKey) {
        return Optional.ofNullable(mapper.findByIdempotency(idempotencyKey));
    }
}