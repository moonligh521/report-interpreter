package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportRuleSummaryMapper {

    int insert(ReportRuleSummaryRow row);

    ReportRuleSummaryRow find(@Param("reportId") String reportId, @Param("parseVersion") int parseVersion);
}