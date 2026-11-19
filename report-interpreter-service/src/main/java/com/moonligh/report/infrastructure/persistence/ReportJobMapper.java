package com.moonligh.report.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportJobMapper {

    int insert(ReportJobRow row);

    ReportJobRow findById(@Param("reportId") String reportId);

    /** 重解析时锁行，串行化 parseVersion 分配。 */
    ReportJobRow lockById(@Param("reportId") String reportId);

    ReportJobRow findByIdempotency(
            @Param("accountId") String accountId, @Param("idempotencyKey") String idempotencyKey);

    int updateStatus(@Param("reportId") String reportId, @Param("status") String status);
}