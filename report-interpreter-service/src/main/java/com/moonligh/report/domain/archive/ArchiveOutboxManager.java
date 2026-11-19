package com.moonligh.report.domain.archive;

import java.util.List;
import java.util.UUID;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.msg.ArchiveOutboxMessage;
import com.moonligh.report.common.PayloadJson;
import com.moonligh.report.domain.rule.IdentityStatus;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;

/**
 * 档案 Outbox 驱动：入队（按 eventId 幂等）→ 投递（PENDING/FAILED → SUCCESS | FAILED | DEAD）→ 补偿（DEAD → PENDING）。
 * 事务由应用服务负责：入队必须与「解读完成」落库在同一事务里。
 * 日志与异常信息不带指标内容。
 */
public final class ArchiveOutboxManager {

    public static final String SCHEMA_VERSION = "archive-schema-v1";
    public static final String ERR_IDENTITY = "IDENTITY_NOT_MATCHED";
    public static final String ERR_SENDER = "SENDER_ERROR";

    private final ArchiveOutboxStore store;
    private final int maxAttempts;

    public ArchiveOutboxManager(ArchiveOutboxStore store, int maxAttempts) {
        this.store = store;
        this.maxAttempts = maxAttempts;
    }

    /** 入队：同一 eventId 只落一行，重复入队返回已有行。 */
    public ReportArchiveOutboxRow enqueue(ArchiveOutboxMessage message) {
        return store.findByEventId(message.eventId()).orElseGet(() -> {
            ReportArchiveOutboxRow row = new ReportArchiveOutboxRow(
                    "aob_" + UUID.randomUUID().toString().replace("-", ""), message.reportId(), message.eventId(),
                    message.schemaVersion(), ArchiveStatus.PENDING.name(), 0, null,
                    PayloadJson.write(message), null, null);
            store.insert(row);
            return row;
        });
    }

    public List<ReportArchiveOutboxRow> dispatchDue(int limit, ArchiveSender sender) {
        return store.listDue(limit).stream().map(r -> dispatchOne(r, sender)).toList();
    }

    /** 派发指定 Outbox（须 PENDING / FAILED）。 */
    public ReportArchiveOutboxRow dispatch(String outboxId, ArchiveSender sender) {
        return dispatchOne(require(outboxId), sender);
    }

    private ReportArchiveOutboxRow dispatchOne(ReportArchiveOutboxRow row, ArchiveSender sender) {
        ArchiveStatus current = ArchiveStatus.valueOf(row.status());
        if (current != ArchiveStatus.PENDING && current != ArchiveStatus.FAILED) {
            throw new IllegalStateException("档案 Outbox 当前状态不可投递：" + current);
        }
        ArchiveOutboxMessage message = PayloadJson.read(row.payloadJson(), ArchiveOutboxMessage.class);
        int attempts = row.attemptCount() + 1;
        if (!IdentityStatus.MATCHED.name().equals(message.identityStatus())) {
            // 非 MATCHED 永不投递：直接终止，不占用重试。
            return save(row, ArchiveStatus.DEAD, attempts, ERR_IDENTITY);
        }
        ArchiveDeliveryResult result;
        try {
            result = sender.send(message);
        } catch (RuntimeException e) {
            result = ArchiveDeliveryResult.failed(ERR_SENDER);
        }
        if (result.accepted()) {
            return save(row, ArchiveStatus.SUCCESS, attempts, null);
        }
        ArchiveStatus target = attempts >= maxAttempts ? ArchiveStatus.DEAD : ArchiveStatus.FAILED;
        return save(row, target, attempts, result.errorCode());
    }

    /**
     * 补偿：DEAD → PENDING（重置投递次数）。FAILED 仅 force 时可拉回；PENDING 无需补偿原样返回；SUCCESS 永不补偿。
     * 调用方须先确认身份门禁仍然放行。
     */
    public ReportArchiveOutboxRow compensate(String reportId, String outboxId, boolean force) {
        ReportArchiveOutboxRow row = require(outboxId);
        if (!row.reportId().equals(reportId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "档案 Outbox 不属于该任务");
        }
        ArchiveStatus current = ArchiveStatus.valueOf(row.status());
        switch (current) {
            case SUCCESS -> throw new BusinessException(ErrorCode.PARAM_INVALID, "档案已成功，无需补偿");
            case PENDING -> {
                return row;
            }
            case FAILED -> {
                if (!force) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "仍在重试中，需 force 才能补偿");
                }
            }
            case DEAD -> {
                // 允许
            }
        }
        return save(row, ArchiveStatus.PENDING, 0, row.lastErrorCode());
    }

    private ReportArchiveOutboxRow require(String outboxId) {
        return store.findById(outboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "档案 Outbox 不存在"));
    }

    private ReportArchiveOutboxRow save(
            ReportArchiveOutboxRow row, ArchiveStatus target, int attempts, String lastErrorCode) {
        ArchiveStatus from = ArchiveStatus.valueOf(row.status());
        if (!from.canTransitionTo(target)) {
            throw new IllegalStateException("非法档案状态迁移：" + from + " -> " + target);
        }
        store.updateStatus(row.outboxId(), target.name(), attempts, lastErrorCode);
        return new ReportArchiveOutboxRow(
                row.outboxId(), row.reportId(), row.eventId(), row.schemaVersion(), target.name(), attempts,
                lastErrorCode, row.payloadJson(), row.createdAt(), row.updatedAt());
    }
}
