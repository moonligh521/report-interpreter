package com.moonligh.report.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.ReportEventPayload;
import com.moonligh.report.api.res.ReportEventType;
import com.moonligh.report.api.res.ReportEventsRes;
import com.moonligh.report.api.res.ReportSseEventRes;
import com.moonligh.report.common.PayloadJson;
import com.moonligh.report.domain.event.ReportEventStore;
import com.moonligh.report.domain.event.StaleEpochException;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.infrastructure.persistence.ReportEventRow;
import com.moonligh.report.infrastructure.persistence.ReportJobRow;

/**
 * 重要 SSE 事件的落库与回放。DB 为准；(jobId, sequence) 唯一；旧 epoch 的写入被拒绝；
 * 每次回放都重新鉴权（重连不沿用旧连接的授权）。
 * 日志只记录报告号 / 序号 / 类型，不记录载荷。
 */
@Service
public class ReportEventService {

    private final ReportEventStore events;
    private final ReportJobStore jobs;
    private final ReportLifecycleService lifecycle;

    public ReportEventService(ReportEventStore events, ReportJobStore jobs, ReportLifecycleService lifecycle) {
        this.events = events;
        this.jobs = jobs;
        this.lifecycle = lifecycle;
    }

    /**
     * 在调用方事务内按序追加事件，返回已落库的事件。会锁 job 行以串行化序号分配；
     * epoch 与任务当前 epoch 不一致 → {@link StaleEpochException}，一条都不写。
     */
    @Transactional
    public List<ReportSseEventRes> append(String reportId, long epoch, List<ReportEventPayload> payloads) {
        ReportJobRow job = jobs.lockById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        if (epoch != job.epoch()) {
            throw new StaleEpochException(epoch, job.epoch());
        }
        long sequence = events.maxSequence(reportId);
        List<ReportSseEventRes> saved = new ArrayList<>();
        for (ReportEventPayload payload : payloads) {
            sequence++;
            String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
            events.insert(new ReportEventRow(
                    eventId, reportId, reportId, sequence, payload.type().name(), epoch,
                    PayloadJson.write(payload), null));
            saved.add(new ReportSseEventRes(
                    eventId, reportId, sequence, payload.type(), null, payload));
        }
        return saved;
    }

    /**
     * 回放 lastEventId 之后的已落库事件（当前 epoch）。lastEventId 可为空 / 序号 / eventId；
     * 不认识的 ID 视为从头回放（客户端按 eventId 去重）。
     */
    @Transactional(readOnly = true)
    public List<ReportSseEventRes> replay(String accountId, String reportId, String lastEventId) {
        ReportJobRow job = ownedJob(accountId, reportId);
        long after = resolveAfter(reportId, lastEventId);
        return events.listAfterSequence(reportId, after, job.epoch()).stream().map(ReportEventService::toRes).toList();
    }

    /** GET /events：当前 epoch 全部事件 + 三条生命周期。 */
    @Transactional(readOnly = true)
    public ReportEventsRes list(String accountId, String reportId) {
        List<ReportSseEventRes> list = replay(accountId, reportId, null);
        return new ReportEventsRes(reportId, list, lifecycle.of(reportId));
    }

    private ReportJobRow ownedJob(String accountId, String reportId) {
        return jobs.findById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
    }

    private long resolveAfter(String reportId, String lastEventId) {
        if (!StringUtils.hasText(lastEventId)) {
            return 0L;
        }
        String id = lastEventId.strip();
        if (id.chars().allMatch(Character::isDigit) && id.length() <= 18) {
            return Long.parseLong(id);
        }
        Optional<ReportEventRow> found = events.findByEventId(reportId, id);
        return found.map(ReportEventRow::sequence).orElse(0L);
    }

    private static ReportSseEventRes toRes(ReportEventRow row) {
        return new ReportSseEventRes(
                row.eventId(), row.reportId(), row.sequence(), ReportEventType.valueOf(row.eventType()),
                row.createdAt() == null ? null : row.createdAt().toString(),
                PayloadJson.read(row.payloadJson(), ReportEventPayload.class));
    }
}
