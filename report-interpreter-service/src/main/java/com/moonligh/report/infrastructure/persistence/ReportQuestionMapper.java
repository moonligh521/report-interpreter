package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportQuestionMapper {

    int insert(ReportQuestionRow row);

    ReportQuestionRow findByIdempotency(@Param("reportId") String reportId, @Param("idempotencyKey") String idempotencyKey);
}
