package com.moonligh.report.domain.critical;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportCriticalEventRow;

/**
 * 危急事件登记与通知状态机驱动。
 * 事件一旦登记（DETECTED）即独立存在，不依赖 RAG/LLM，也不被后续解读覆盖；
 * 通知失败进入 FAILED → RETRYING（指数退避），超过上限升级为 ESCALATED。
 * 事务由应用服务负责。
 */
public final class CriticalNotificationManager {

    public static final String EVENT_DETECTED = "DETECTED";
    public static final String DEFAULT_CHANNEL = "IN_APP";

    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final CriticalEventStore store;
    private final int maxAttempts;

    public CriticalNotificationManager(CriticalEventStore store, int maxAttempts) {
        this.store = store;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 登记危急事件与通知 Outbox（幂等：同一危急事实只落一次）。
     * deliverable=false（身份/覆盖/适用性未确认）→ PENDING_REVIEW，否则 PENDING。
     */
    public RegisteredCriticalEvent register(CriticalDetection d, boolean deliverable) {
        String key = idempotencyKey(d);
        Optional<ReportCriticalEventRow> existing = store.findEventByIdempotency(key);
        if (existing.isPresent()) {
            return reuse(existing.get());
        }
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        ReportCriticalEventRow event = new ReportCriticalEventRow(
                eventId, d.reportId(), d.parseVersion(), d.canonicalCode(), d.criticalLevel(),
                EVENT_DETECTED, d.detectionTime(), key);
        store.insertEvent(event);
        NotificationStatus initial = deliverable ? NotificationStatus.PENDING : NotificationStatus.PENDING_REVIEW;
        CriticalNotificationOutboxRow outbox = new CriticalNotificationOutboxRow(
                "obx_" + UUID.randomUUID().toString().replace("-", ""), eventId, initial.name(),
                DEFAULT_CHANNEL, "notify:" + key + ":" + DEFAULT_CHANNEL, 0, null, null, null);
        store.insertOutbox(outbox);
        return new RegisteredCriticalEvent(event, outbox, true);
    }

    /** 人工确认后放行：PENDING_REVIEW → PENDING。 */
    public CriticalNotificationOutboxRow releaseAfterReview(String outboxId) {
        CriticalNotificationOutboxRow row = require(outboxId);
        return save(row, NotificationStatus.PENDING, row.attemptCount(), null, row.lastErrorCode(), null);
    }

    /**
     * 派发到期通知：PENDING/RETRYING/FAILED → SENDING → ACCEPTED，或 FAILED → RETRYING/ESCALATED。
     * 返回本次处理后的 Outbox 行。
     */
    public List<CriticalNotificationOutboxRow> dispatchDue(Instant now, int limit, CriticalNotificationSender sender) {
        return store.listRetryDue(now, limit).stream().map(row -> dispatchOne(row, now, sender)).toList();
    }

    /** 派发指定通知（须处于 PENDING/RETRYING/FAILED 且已到期），用于定向重试与测试。 */
    public CriticalNotificationOutboxRow dispatch(String outboxId, Instant now, CriticalNotificationSender sender) {
        CriticalNotificationOutboxRow row = require(outboxId);
        if (row.nextRetryAt() != null && row.nextRetryAt().isAfter(now)) {
            throw new IllegalStateException("未到重试时间");
        }
        return dispatchOne(row, now, sender);
    }

    private CriticalNotificationOutboxRow dispatchOne(
            CriticalNotificationOutboxRow row, Instant now, CriticalNotificationSender sender) {
        ReportCriticalEventRow event = store.findEventById(row.eventId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "危急事件不存在"));
        NotificationStatus current = NotificationStatus.valueOf(row.notificationStatus());
        CriticalNotificationOutboxRow sending = row;
        if (current == NotificationStatus.FAILED) {
            // FAILED 且已到期：先回到 RETRYING 再发送，保持状态机合法。
            sending = save(sending, NotificationStatus.RETRYING, row.attemptCount(), now, row.lastErrorCode(), null);
        }
        sending = save(sending, NotificationStatus.SENDING, sending.attemptCount(), null, sending.lastErrorCode(), null);
        DeliveryResult result = sender.send(new CriticalNotificationMessage(
                sending.outboxId(), event.eventId(), event.reportId(), event.canonicalCode(),
                event.criticalLevel(), sending.deliveryChannel(), sending.idempotencyKey()));
        int attempts = sending.attemptCount() + 1;
        if (result.accepted()) {
            return save(sending, NotificationStatus.ACCEPTED, attempts, null, null, now);
        }
        CriticalNotificationOutboxRow failed =
                save(sending, NotificationStatus.FAILED, attempts, null, result.errorCode(), null);
        if (attempts >= maxAttempts) {
            return save(failed, NotificationStatus.ESCALATED, attempts, null, result.errorCode(), null);
        }
        return save(failed, NotificationStatus.RETRYING, attempts, now.plus(backoff(attempts)), result.errorCode(), null);
    }

    /** 第 n 次失败后的退避：30s * 2^(n-1)，封顶 1 小时。 */
    public static Duration backoff(int attempts) {
        long factor = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration d = BASE_BACKOFF.multipliedBy(factor);
        return d.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : d;
    }

    private RegisteredCriticalEvent reuse(ReportCriticalEventRow event) {
        CriticalNotificationOutboxRow outbox = store.findOutboxByEvent(event.eventId())
                .orElseThrow(() -> new IllegalStateException("危急事件缺少通知 Outbox"));
        return new RegisteredCriticalEvent(event, outbox, false);
    }

    private CriticalNotificationOutboxRow require(String outboxId) {
        return store.findOutboxById(outboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "通知记录不存在"));
    }

    /** 校验迁移合法后落库并返回新行。 */
    private CriticalNotificationOutboxRow save(
            CriticalNotificationOutboxRow row, NotificationStatus target, int attempts,
            Instant nextRetryAt, String lastErrorCode, Instant deliveredAt) {
        NotificationStatus from = NotificationStatus.valueOf(row.notificationStatus());
        if (!from.canTransitionTo(target)) {
            throw new IllegalStateException("非法通知状态迁移：" + from + " -> " + target);
        }
        CriticalNotificationOutboxRow updated = new CriticalNotificationOutboxRow(
                row.outboxId(), row.eventId(), target.name(), row.deliveryChannel(), row.idempotencyKey(),
                attempts, nextRetryAt, lastErrorCode, deliveredAt);
        store.updateOutbox(updated);
        return updated;
    }

    /** 同一危急事实：同报告、同项目、同方向、同数值。跨 parseVersion 重解析不重复建事件。 */
    public static String idempotencyKey(CriticalDetection d) {
        return "crit:" + d.reportId() + ":" + d.canonicalCode() + ":" + d.criticalLevel() + ":" + d.valueText();
    }
}