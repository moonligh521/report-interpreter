package com.moonligh.report.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportRuleExecutionMapper {

    int insert(ReportRuleExecutionRow row);

    List<ReportRuleExecutionRow> listByReport(@Param("reportId") String reportId);
}