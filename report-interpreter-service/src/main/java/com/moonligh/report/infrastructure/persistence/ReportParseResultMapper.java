package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportParseResultMapper {

    int insert(ReportParseResultRow row);

    int deactivatePrevious(@Param("reportId") String reportId);

    ReportParseResultRow findActive(@Param("reportId") String reportId);

    ReportParseResultRow findByRequestKey(
            @Param("reportId") String reportId, @Param("requestKey") String requestKey);

    int maxVersion(@Param("reportId") String reportId);
}