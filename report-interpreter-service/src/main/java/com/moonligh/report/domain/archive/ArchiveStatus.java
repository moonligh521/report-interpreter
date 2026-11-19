package com.moonligh.report.domain.archive;

import java.util.Set;

/**
 * 档案 Outbox 状态机（与危急通知 NotificationStatus 不共用）。
 * SUCCESS 仅表示档案消费方已落地；与解读 COMPLETED、危急已读互相独立。
 * DEAD 为投递次数用尽，只能经补偿接口拉回 PENDING。
 */
public enum ArchiveStatus {
    PENDING,
    FAILED,
    SUCCESS,
    DEAD;

    public Set<ArchiveStatus> next() {
        return switch (this) {
            case PENDING -> Set.of(SUCCESS, FAILED, DEAD);
            case FAILED -> Set.of(SUCCESS, FAILED, DEAD, PENDING);
            case DEAD -> Set.of(PENDING);
            case SUCCESS -> Set.of();
        };
    }

    public boolean canTransitionTo(ArchiveStatus target) {
        return next().contains(target);
    }
}
