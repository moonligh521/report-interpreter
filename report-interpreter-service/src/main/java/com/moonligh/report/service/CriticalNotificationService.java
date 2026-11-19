package com.moonligh.report.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moonligh.report.domain.critical.CriticalNotificationManager;
import com.moonligh.report.domain.critical.CriticalNotificationSender;
import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;

/**
 * 危急通知派发与放行的事务边界。通道由调用方（调度器 / 适配器）以 CriticalNotificationSender 传入。
 */
@Service
public class CriticalNotificationService {

    private final CriticalNotificationManager manager;

    public CriticalNotificationService(CriticalNotificationManager manager) {
        this.manager = manager;
    }

    /** 派发到期（PENDING / RETRYING / FAILED）的通知。 */
    @Transactional
    public List<CriticalNotificationOutboxRow> dispatchDue(Instant now, int limit, CriticalNotificationSender sender) {
        return manager.dispatchDue(now, limit, sender);
    }

    /** 定向派发一条已到期的通知。 */
    @Transactional
    public CriticalNotificationOutboxRow dispatch(String outboxId, Instant now, CriticalNotificationSender sender) {
        return manager.dispatch(outboxId, now, sender);
    }

    /** 人工复核确认后放行 PENDING_REVIEW 通知。 */
    @Transactional
    public CriticalNotificationOutboxRow releaseAfterReview(String outboxId) {
        return manager.releaseAfterReview(outboxId);
    }
}