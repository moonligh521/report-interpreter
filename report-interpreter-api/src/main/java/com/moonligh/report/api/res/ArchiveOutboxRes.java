package com.moonligh.report.api.res;

import com.moonligh.report.api.msg.ArchiveOutboxMessageV2;

/** 内部查询档案 Outbox 详情（含强类型 payload）。 */
public record ArchiveOutboxRes(
        String outboxId,
        String reportId,
        String eventId,
        String schemaVersion,
        String status,
        int attemptCount,
        String lastErrorCode,
        ArchiveOutboxMessageV2 payload) {
}
