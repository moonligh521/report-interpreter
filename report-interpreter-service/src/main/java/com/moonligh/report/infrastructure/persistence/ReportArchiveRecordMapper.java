package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportArchiveRecordMapper {

    int insert(ReportArchiveRecordRow row);

    ReportArchiveRecordRow findByEventId(@Param("eventId") String eventId);

    int countByReport(@Param("reportId") String reportId);
}
