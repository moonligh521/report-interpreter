package com.moonligh.report.domain.archive;

import com.moonligh.report.api.msg.ArchiveOutboxMessageV2;
import com.moonligh.report.domain.rule.IdentityStatus;
import com.moonligh.report.infrastructure.persistence.ReportArchiveRecordRow;

/**
 * 档案消费方：按 eventId 幂等落地「可投递档案」记录（T-R4 schemaVersion=2 同样按 eventId 幂等）。
 * 防御性再校验身份：非 MATCHED 的消息即使被误投也不落地（T-R5）。
 */
public final class ArchiveConsumer {

    public enum Outcome {
        /** 本次新落地。 */
        APPLIED,
        /** eventId 已消费过，不重复落地。 */
        DUPLICATE,
        /** 身份非 MATCHED，拒绝落地。 */
        REJECTED
    }

    private final ArchiveRecordStore records;

    public ArchiveConsumer(ArchiveRecordStore records) {
        this.records = records;
    }

    public Outcome consume(ArchiveOutboxMessageV2 message) {
        if (!IdentityStatus.MATCHED.name().equals(message.identityStatus())) {
            return Outcome.REJECTED;
        }
        boolean applied = records.insertIfAbsent(new ReportArchiveRecordRow(
                message.eventId(), message.reportId(), message.interpretationId(), message.patientId(),
                message.parseVersion(), message.identityStatus(), message.indicators().size(), null));
        return applied ? Outcome.APPLIED : Outcome.DUPLICATE;
    }

    /** 作为投递通道使用：APPLIED / DUPLICATE 均视为已接受。 */
    public ArchiveDeliveryResult deliver(ArchiveOutboxMessageV2 message) {
        return switch (consume(message)) {
            case APPLIED, DUPLICATE -> ArchiveDeliveryResult.ok();
            case REJECTED -> ArchiveDeliveryResult.failed(ArchiveOutboxManager.ERR_IDENTITY);
        };
    }
}
