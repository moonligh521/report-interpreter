package com.moonligh.report.domain.event;

import java.util.List;
import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportEventRow;

/**
 * SSE 事件仓储端口（DB 为准，Redis 仅加速，不在本端口内）。
 */
public interface ReportEventStore {

    void insert(ReportEventRow row);

    List<ReportEventRow> listAfterSequence(String reportId, long afterSequence, long minEpoch);

    long maxSequence(String jobId);

    Optional<ReportEventRow> findByEventId(String reportId, String eventId);
}
