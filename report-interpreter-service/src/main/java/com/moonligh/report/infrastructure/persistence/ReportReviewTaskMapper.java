package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportReviewTaskMapper {

    int insert(ReportReviewTaskRow row);

    ReportReviewTaskRow findByIdempotency(@Param("idempotencyKey") String idempotencyKey);

    ReportReviewTaskRow findById(@Param("reviewTaskId") String reviewTaskId);

    int updateStatus(@Param("reviewTaskId") String reviewTaskId, @Param("status") String status);
}