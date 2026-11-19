package com.moonligh.report.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportArchiveOutboxMapper {

    int insert(ReportArchiveOutboxRow row);

    int updateStatus(
            @Param("outboxId") String outboxId,
            @Param("status") String status,
            @Param("attemptCount") int attemptCount,
            @Param("lastErrorCode") String lastErrorCode);

    ReportArchiveOutboxRow findByEventId(@Param("eventId") String eventId);

    ReportArchiveOutboxRow findById(@Param("outboxId") String outboxId);

    ReportArchiveOutboxRow findLatestByReport(@Param("reportId") String reportId);

    /** 待投递：PENDING 或 FAILED（未 DEAD / SUCCESS）。 */
    List<ReportArchiveOutboxRow> listDue(@Param("limit") int limit);
}
