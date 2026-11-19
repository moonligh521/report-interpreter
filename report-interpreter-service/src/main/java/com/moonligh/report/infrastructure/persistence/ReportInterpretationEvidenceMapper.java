package com.moonligh.report.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportInterpretationEvidenceMapper {

    int batchInsert(@Param("rows") List<ReportInterpretationEvidenceRow> rows);

    List<ReportInterpretationEvidenceRow> listByInterpretation(@Param("interpretationId") String interpretationId);
}
