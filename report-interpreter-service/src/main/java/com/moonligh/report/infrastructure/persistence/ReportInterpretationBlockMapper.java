package com.moonligh.report.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportInterpretationBlockMapper {

    int batchInsert(@Param("rows") List<ReportInterpretationBlockRow> rows);

    List<ReportInterpretationBlockRow> listByInterpretation(@Param("interpretationId") String interpretationId);
}
