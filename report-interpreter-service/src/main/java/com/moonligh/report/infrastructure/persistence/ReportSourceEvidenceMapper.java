package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportSourceEvidenceMapper {

    int insert(ReportSourceEvidenceRow row);

    ReportSourceEvidenceRow findByIndicator(@Param("indicatorId") String indicatorId);
}