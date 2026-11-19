package com.moonligh.report.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportCriticalEventMapper {

    int insert(ReportCriticalEventRow row);

    List<ReportCriticalEventRow> listByReport(@Param("reportId") String reportId);

    ReportCriticalEventRow findByIdempotency(@Param("idempotencyKey") String idempotencyKey);

    ReportCriticalEventRow findById(@Param("eventId") String eventId);
}