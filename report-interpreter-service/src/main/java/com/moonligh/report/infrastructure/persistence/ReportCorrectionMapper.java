package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportCorrectionMapper {

    int insert(ReportCorrectionRow row);

    ReportCorrectionRow findByIdempotency(@Param("idempotencyKey") String idempotencyKey);
}
