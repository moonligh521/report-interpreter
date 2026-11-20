package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportParseManifestMapper {

    int insert(ReportParseManifestRow row);

    ReportParseManifestRow findByReportVersion(
            @Param("reportId") String reportId, @Param("parseVersion") int parseVersion);
}
