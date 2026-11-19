package com.moonligh.report.domain.critical;

import java.util.Set;

/**
 * 危急通知状态机（与档案 Outbox 不共用）。
 * ACCEPTED 仅表示下游通道已接受，不等于用户已读；ACKNOWLEDGED（用户确认）为 P1。
 */
public enum NotificationStatus {
    DETECTED,
    PENDING_REVIEW,
    PENDING,
    SENDING,
    ACCEPTED,
    FAILED,
    RETRYING,
    ACKNOWLEDGED,
    ESCALATED;

    /** 该状态允许迁移到的下一状态。 */
    public Set<NotificationStatus> next() {
        return switch (this) {
            case DETECTED -> Set.of(PENDING_REVIEW, PENDING);
            case PENDING_REVIEW -> Set.of(PENDING, ESCALATED);
            case PENDING -> Set.of(SENDING);
            case SENDING -> Set.of(ACCEPTED, FAILED);
            case FAILED -> Set.of(RETRYING, ESCALATED);
            case RETRYING -> Set.of(SENDING, ESCALATED);
            case ACCEPTED -> Set.of(ACKNOWLEDGED, ESCALATED);
            case ACKNOWLEDGED, ESCALATED -> Set.of();
        };
    }

    public boolean canTransitionTo(NotificationStatus target) {
        return next().contains(target);
    }
}