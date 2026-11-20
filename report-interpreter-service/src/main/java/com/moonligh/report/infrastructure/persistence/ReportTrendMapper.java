package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportTrendMapper {

    List<TrendPointRow> listTrendPoints(
            @Param("accountId") String accountId,
            @Param("patientId") String patientId,
            @Param("canonicalCode") String canonicalCode,
            @Param("specimenType") String specimenType,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
