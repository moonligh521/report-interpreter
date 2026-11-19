package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportInterpretationMapper {

    int insert(ReportInterpretationRow row);

    ReportInterpretationRow findById(@Param("interpretationId") String interpretationId);

    ReportInterpretationRow findByIdempotency(
            @Param("reportId") String reportId, @Param("idempotencyKey") String idempotencyKey);

    ReportInterpretationRow findLatestByReport(@Param("reportId") String reportId);
}
