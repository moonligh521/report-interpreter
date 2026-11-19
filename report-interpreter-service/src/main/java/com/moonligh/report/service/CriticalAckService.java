package com.moonligh.report.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.req.AckCriticalReq;
import com.moonligh.report.api.res.AckCriticalRes;
import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.CriticalNotificationManager;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportCriticalEventRow;

/**
 * 危急通知患者端 ACK（M-R4）：仅 ACCEPTED → ACKNOWLEDGED，表示已读而非医院制度完成。
 */
@Service
public class CriticalAckService {

    private final ReportJobStore jobs;
    private final CriticalEventStore events;
    private final CriticalNotificationManager notifications;

    public CriticalAckService(
            ReportJobStore jobs, CriticalEventStore events, CriticalNotificationManager notifications) {
        this.jobs = jobs;
        this.events = events;
        this.notifications = notifications;
    }

    @Transactional
    public AckCriticalRes ack(String accountId, String reportId, String eventId, AckCriticalReq req) {
        jobs.findById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        ReportCriticalEventRow event = events.findEventById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "危急事件不存在"));
        if (!reportId.equals(event.reportId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "事件不属于该报告");
        }
        Instant at = req == null ? null : req.acknowledgedAt();
        CriticalNotificationOutboxRow updated = notifications.acknowledge(eventId, at);
        return new AckCriticalRes(eventId, updated.notificationStatus());
    }
}
