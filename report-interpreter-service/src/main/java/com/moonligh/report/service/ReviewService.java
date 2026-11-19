package com.moonligh.report.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.req.CreateReviewReq;
import com.moonligh.report.api.req.ResolveReviewReq;
import com.moonligh.report.api.res.CreateReviewRes;
import com.moonligh.report.api.res.ResolveReviewRes;
import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.CriticalNotificationManager;
import com.moonligh.report.domain.critical.NotificationStatus;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.domain.review.ReviewTaskStore;
import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportReviewTaskMapper;
import com.moonligh.report.infrastructure.persistence.ReportReviewTaskRow;

/**
 * 人工复核任务。创建与结案（resolve）属于危急/覆盖复核通道，与普通字段修订分离（M-R6）。
 */
@Service
public class ReviewService {

    static final String STATUS_OPEN = "OPEN";
    static final String STATUS_RESOLVED = "RESOLVED";
    private static final Set<String> PRIORITIES = Set.of("NORMAL", "HIGH", "URGENT");
    private static final int MAX_KEY = 64;
    private static final int MAX_REASON = 512;

    private final ReviewTaskStore tasks;
    private final ReportReviewTaskMapper reviewMapper;
    private final ReportJobStore jobs;
    private final CriticalEventStore criticalEvents;
    private final CriticalNotificationManager notifications;

    public ReviewService(
            ReviewTaskStore tasks, ReportReviewTaskMapper reviewMapper, ReportJobStore jobs,
            CriticalEventStore criticalEvents, CriticalNotificationManager notifications) {
        this.tasks = tasks;
        this.reviewMapper = reviewMapper;
        this.jobs = jobs;
        this.criticalEvents = criticalEvents;
        this.notifications = notifications;
    }

    @Transactional
    public CreateReviewRes create(CreateReviewReq req, String idempotencyKey) {
        validate(req, idempotencyKey);
        String priority = req.priority().strip().toUpperCase(java.util.Locale.ROOT);
        String reason = req.reason().strip();
        Optional<ReportReviewTaskRow> existing = tasks.findByIdempotency(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), req.reportId(), reason, priority);
        }
        jobs.findById(req.reportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        ReportReviewTaskRow row = new ReportReviewTaskRow(
                "rvw_" + UUID.randomUUID().toString().replace("-", ""),
                req.reportId(), reason, priority, STATUS_OPEN, idempotencyKey, null);
        try {
            tasks.insert(row);
        } catch (DuplicateKeyException e) {
            return replay(
                    tasks.findByIdempotency(idempotencyKey).orElseThrow(() -> e), req.reportId(), reason, priority);
        }
        return new CreateReviewRes(row.reviewTaskId(), row.status());
    }

    /**
     * 结案危急/覆盖复核：标记 RESOLVED，并将该报告 PENDING_REVIEW 通知放行为 PENDING。
     * 不产生新 parseVersion（普通修订走 /internal/report/corrections）。
     */
    @Transactional
    public ResolveReviewRes resolve(String reviewTaskId, ResolveReviewReq req, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > MAX_KEY) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少或非法 Idempotency-Key");
        }
        if (req == null || !StringUtils.hasText(req.resolution())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "resolution 不能为空");
        }
        ReportReviewTaskRow row = reviewMapper.findById(reviewTaskId);
        if (row == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "复核任务不存在");
        }
        if (STATUS_RESOLVED.equals(row.status())) {
            return new ResolveReviewRes(row.reviewTaskId(), STATUS_RESOLVED);
        }
        reviewMapper.updateStatus(reviewTaskId, STATUS_RESOLVED);
        for (var event : criticalEvents.listEventsByReport(row.reportId())) {
            Optional<CriticalNotificationOutboxRow> outbox = criticalEvents.findOutboxByEvent(event.eventId());
            if (outbox.isPresent()
                    && NotificationStatus.PENDING_REVIEW.name().equals(outbox.get().notificationStatus())) {
                notifications.releaseAfterReview(outbox.get().outboxId());
            }
        }
        return new ResolveReviewRes(reviewTaskId, STATUS_RESOLVED);
    }

    private static CreateReviewRes replay(ReportReviewTaskRow row, String reportId, String reason, String priority) {
        boolean same = row.reportId().equals(reportId) && row.reason().equals(reason) && row.priority().equals(priority);
        if (!same) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "同一 Idempotency-Key 的请求内容不一致");
        }
        return new CreateReviewRes(row.reviewTaskId(), row.status());
    }

    private static void validate(CreateReviewReq req, String key) {
        if (!StringUtils.hasText(key) || key.length() > MAX_KEY) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少或非法 Idempotency-Key");
        }
        if (req == null || !StringUtils.hasText(req.reportId())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "reportId 不能为空");
        }
        if (!StringUtils.hasText(req.reason()) || req.reason().strip().length() > MAX_REASON) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "reason 不能为空且不超过 512 字");
        }
        if (!StringUtils.hasText(req.priority())
                || !PRIORITIES.contains(req.priority().strip().toUpperCase(java.util.Locale.ROOT))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "priority 仅支持 NORMAL / HIGH / URGENT");
        }
    }
}
