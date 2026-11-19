package com.moonligh.report.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportEventMapper {

    int insert(ReportEventRow row);

    /** 只返回 epoch 不低于 minEpoch 的事件，旧 epoch 事件不再对外回放。 */
    List<ReportEventRow> listAfterSequence(
            @Param("reportId") String reportId,
            @Param("afterSequence") long afterSequence,
            @Param("minEpoch") long minEpoch);

    /** 该任务当前最大序号；无事件时为 0。 */
    long maxSequence(@Param("jobId") String jobId);

    ReportEventRow findByEventId(@Param("reportId") String reportId, @Param("eventId") String eventId);
}
