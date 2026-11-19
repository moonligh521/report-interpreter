package com.moonligh.report.domain.archive;

import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportArchiveRecordRow;

/**
 * 档案落地记录仓储端口。event_id 主键保证消费幂等。
 */
public interface ArchiveRecordStore {

    /** 按 eventId 幂等写入；已存在（含并发先写）返回 false。 */
    boolean insertIfAbsent(ReportArchiveRecordRow row);

    Optional<ReportArchiveRecordRow> findByEventId(String eventId);

    int countByReport(String reportId);
}
