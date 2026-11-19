package com.moonligh.report.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportIndicatorMapper {

    int batchInsert(@Param("rows") List<ReportIndicatorRow> rows);

    List<ReportIndicatorRow> listByParseResult(@Param("parseResultId") String parseResultId);
}