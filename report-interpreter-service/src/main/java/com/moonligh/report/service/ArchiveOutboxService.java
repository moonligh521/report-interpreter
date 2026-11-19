package com.moonligh.report.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.msg.ArchiveOutboxMessage;
import com.moonligh.report.api.req.CompensateReq;
import com.moonligh.report.api.res.CompensateRes;
import com.moonligh.report.domain.archive.ArchiveGate;
import com.moonligh.report.domain.archive.ArchiveOutboxManager;
import com.moonligh.report.domain.archive.ArchiveSender;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.domain.parse.ReportParseStore;
import com.moonligh.report.domain.rule.IdentityStatus;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportJobRow;
import com.moonligh.report.infrastructure.persistence.ReportParseResultRow;

/**
 * 档案 Outbox 的事务边界：解读完成时同事务入队、派发、补偿。
 * 与危急通知 Outbox（CriticalNotificationService）完全独立。
 */
@Service
public class ArchiveOutboxService {

    private final ArchiveOutboxManager manager;
    private final ArchiveSender defaultSender;
    private final ReportJobStore jobs;
    private final ReportParseStore parses;
    private final ReportRuleService rules;
    private final IndicatorDtoAssembler assembler;

    public ArchiveOutboxService(
            ArchiveOutboxManager manager, ArchiveSender defaultSender, ReportJobStore jobs, ReportParseStore parses,
            ReportRuleService rules, IndicatorDtoAssembler assembler) {
        this.manager = manager;
        this.defaultSender = defaultSender;
        this.jobs = jobs;
        this.parses = parses;
        this.rules = rules;
        this.assembler = assembler;
    }

    /**
     * 解读落库后、同一事务内调用：门禁放行（MATCHED + 规则未阻断 + 解读完成）才入队，否则什么都不写。
     * eventId 由 interpretationId 派生，重复调用幂等。
     */
    @Transactional
    public Optional<ReportArchiveOutboxRow> enqueueIfAllowed(
            ReportJobRow job, ReportParseResultRow parse, String identityStatus, boolean archiveBlocked,
            String interpretationId, boolean interpretationCompleted) {
        ArchiveGate.Decision decision = ArchiveGate.decide(identityStatus, archiveBlocked, interpretationCompleted);
        if (decision != ArchiveGate.Decision.ALLOWED) {
            return Optional.empty();
        }
        ArchiveOutboxMessage message = new ArchiveOutboxMessage(
                eventIdOf(interpretationId), ArchiveOutboxManager.SCHEMA_VERSION, job.reportId(), job.patientId(),
                parse.parseVersion(), interpretationId, assembler.forParse(parse.id()), identityStatus);
        return Optional.of(manager.enqueue(message));
    }

    /** 档案事件 ID：由解读 ID 稳定派生。 */
    public static String eventIdOf(String interpretationId) {
        return "arc_" + interpretationId;
    }

    /** 以默认通道派发到期（PENDING / FAILED）档案。 */
    @Transactional
    public List<ReportArchiveOutboxRow> dispatchDue(int limit) {
        return manager.dispatchDue(limit, defaultSender);
    }

    @Transactional
    public List<ReportArchiveOutboxRow> dispatchDue(int limit, ArchiveSender sender) {
        return manager.dispatchDue(limit, sender);
    }

    @Transactional
    public ReportArchiveOutboxRow dispatch(String outboxId, ArchiveSender sender) {
        return manager.dispatch(outboxId, sender);
    }

    /**
     * 补偿：策略允许时把 DEAD 拉回 PENDING。策略 = 该报告当前生效解析的身份仍为 MATCHED 且规则未阻断归档；
     * force 只放宽 FAILED 的补偿，不能越过身份门禁。
     */
    @Transactional
    public CompensateRes compensate(String jobId, CompensateReq req) {
        if (req == null || !StringUtils.hasText(req.outboxId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "outboxId 不能为空");
        }
        jobs.findById(jobId).orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        requireArchiveAllowed(jobId);
        ReportArchiveOutboxRow row = manager.compensate(jobId, req.outboxId(), req.force());
        return new CompensateRes(row.outboxId(), row.status(), row.attemptCount());
    }

    private void requireArchiveAllowed(String reportId) {
        ReportParseResultRow parse = parses.findActive(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "报告尚无解析结果"));
        ReportRuleService.RuleView view = rules.view(reportId, parse.parseVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_RULE_PENDING_REVIEW, "报告规则尚未执行"));
        String identity = view.identityCheck().status();
        if (IdentityStatus.CONFLICT.name().equals(identity)) {
            throw new BusinessException(ErrorCode.REPORT_IDENTITY_CONFLICT, "身份冲突，不得补偿档案");
        }
        if (IdentityStatus.MISSING.name().equals(identity)) {
            throw new BusinessException(ErrorCode.REPORT_IDENTITY_MISSING, "身份缺失，不得补偿档案");
        }
        if (ArchiveGate.decide(identity, view.archiveBlocked(), true) != ArchiveGate.Decision.ALLOWED) {
            throw new BusinessException(ErrorCode.REPORT_RULE_PENDING_REVIEW, "规则阻断归档，不得补偿档案");
        }
    }
}
