package com.moonligh.report.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.req.TriggerInterpretationReq;
import com.moonligh.report.api.res.CorrectionPayload;
import com.moonligh.report.api.res.CriticalAlertPayload;
import com.moonligh.report.api.res.CriticalHitRes;
import com.moonligh.report.api.res.DonePayload;
import com.moonligh.report.api.res.ErrorPayload;
import com.moonligh.report.api.res.EvidenceRes;
import com.moonligh.report.api.res.IndicatorSummary;
import com.moonligh.report.api.res.InterpretationBlockRes;
import com.moonligh.report.api.res.InterpretationDeltaPayload;
import com.moonligh.report.api.res.InterpretationRes;
import com.moonligh.report.api.res.PinnedVersions;
import com.moonligh.report.api.res.ReportEventPayload;
import com.moonligh.report.api.res.StructuredResultPayload;
import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.interpret.BlockVerdict;
import com.moonligh.report.domain.interpret.ClaimType;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.EvidenceItem;
import com.moonligh.report.domain.interpret.GuardOutcome;
import com.moonligh.report.domain.interpret.IndicatorFact;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationModel;
import com.moonligh.report.domain.interpret.InterpretationOutputGuard;
import com.moonligh.report.domain.interpret.InterpretationStatus;
import com.moonligh.report.domain.interpret.InterpretationStore;
import com.moonligh.report.domain.interpret.InterpretationTemplates;
import com.moonligh.report.domain.interpret.InterpretationVersions;
import com.moonligh.report.domain.interpret.ValidationStatus;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.domain.kb.HybridRecall;
import com.moonligh.report.domain.kb.KbChunkStore;
import com.moonligh.report.domain.kb.KnowledgeChunk;
import com.moonligh.report.domain.kb.RecallEmptyReason;
import com.moonligh.report.domain.kb.RecallFilter;
import com.moonligh.report.domain.kb.RecallQuery;
import com.moonligh.report.domain.kb.RecallResult;
import com.moonligh.report.domain.parse.ReportParseStore;
import com.moonligh.report.domain.rule.CriticalStatus;
import com.moonligh.report.domain.rule.ReferenceStatus;
import com.moonligh.report.domain.rule.RuleExecutionStore;
import com.moonligh.report.infrastructure.persistence.ReportArchiveOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportIndicatorRow;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationBlockRow;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationEvidenceRow;
import com.moonligh.report.infrastructure.persistence.ReportInterpretationRow;
import com.moonligh.report.infrastructure.persistence.ReportJobRow;
import com.moonligh.report.infrastructure.persistence.ReportParseResultRow;
import com.moonligh.report.infrastructure.persistence.ReportRuleExecutionRow;

/**
 * 报告解读编排：规则出口 → 事实 → Hybrid RAG（结构化过滤、fail-closed）→ 模型草稿 → OutputGuard → 钉死版本落库。
 *
 * <p>安全边界：危急提醒、复核提示、免责声明由 Java 模板产出，不依赖模型；模型块只有通过 Guard 才外发；
 * 解读与纠正流程从不删除 / 改写 critical_event（本服务不依赖也不引用危急事件的写接口）。
 */
@Service
public class ReportInterpretationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportInterpretationService.class);
    private static final String EVIDENCE_PREFIX = "evd-";
    private static final int SNIPPET_MAX = 1000;
    private static final String CORRECTION_REINTERPRETED = "REINTERPRETED";

    private final ReportJobStore jobs;
    private final ReportParseStore parses;
    private final RuleExecutionStore executions;
    private final ReportRuleService rules;
    private final HybridRecall recall;
    private final KbChunkStore kbStore;
    private final InterpretationModel model;
    private final InterpretationOutputGuard guard;
    private final InterpretationStore store;
    private final ReportProperties properties;
    private final Clock clock;
    private final ArchiveOutboxService archive;
    private final ReportEventService events;
    private final ReportLifecycleService lifecycle;

    public ReportInterpretationService(
            ReportJobStore jobs, ReportParseStore parses, RuleExecutionStore executions, ReportRuleService rules,
            HybridRecall recall, KbChunkStore kbStore, InterpretationModel model, InterpretationOutputGuard guard,
            InterpretationStore store, ReportProperties properties, Clock clock,
            ArchiveOutboxService archive, ReportEventService events, ReportLifecycleService lifecycle) {
        this.archive = archive;
        this.events = events;
        this.lifecycle = lifecycle;
        this.jobs = jobs;
        this.parses = parses;
        this.executions = executions;
        this.rules = rules;
        this.recall = recall;
        this.kbStore = kbStore;
        this.model = model;
        this.guard = guard;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /** 解读视图：code 为 null 表示 Result.code=200，否则为 4105 / 4107。 */
    public record InterpretationView(InterpretationRes data, Integer code) {
    }

    @Transactional
    public InterpretationView trigger(
            String accountId, String reportId, String idempotencyKey, TriggerInterpretationReq req) {
        // 锁 job 行：串行化同一报告的解读与事件序号分配，并拿到当前 epoch。
        ReportJobRow job = jobs.lockById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        Optional<ReportInterpretationRow> replay = store.findByIdempotency(job.reportId(), idempotencyKey);
        if (replay.isPresent()) {
            return toView(replay.get());
        }

        ReportParseResultRow parse = parses.findActive(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "报告尚无解析结果"));
        ReportRuleService.RuleView rule = rules.view(reportId, parse.parseVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_RULE_PENDING_REVIEW, "报告规则尚未执行"));
        // 身份冲突 / 缺失：不输出任何解读（报告可能属于他人，或无法确认归属）。
        if ("CONFLICT".equals(rule.identityCheck().status())) {
            throw new BusinessException(ErrorCode.REPORT_IDENTITY_CONFLICT, "报告身份与就诊人冲突，不予解读");
        }
        if ("MISSING".equals(rule.identityCheck().status())) {
            throw new BusinessException(ErrorCode.REPORT_IDENTITY_MISSING, "报告缺少身份信息，不予解读");
        }

        List<IndicatorFact> facts = selectFacts(buildFacts(reportId, parse), req);
        List<EvidenceItem> kbEvidence = recallEvidence(facts, job);
        if (kbEvidence.isEmpty() && !properties.getInterpretation().isAllowDegraded()) {
            throw new BusinessException(ErrorCode.REPORT_KB_UNAVAILABLE, "知识库无可引用依据，且不允许无依据解读");
        }
        // 报告级无阻断（身份匹配、覆盖完整、无待复核）才允许出现「正常」结论；
        // 单项是否正常仍由每个指标的规则结论逐项校验。safeNormalAllowed 是「整份报告全部正常」，不能用在这里。
        boolean normalClaimsAllowed = rule.blockCode() == null;
        InterpretationContext ctx = new InterpretationContext(facts, kbEvidence, normalClaimsAllowed);

        GuardOutcome outcome = guard.guard(model, ctx, properties.getInterpretation().getMaxAttempts());
        logRejected(reportId, outcome);

        List<DraftBlock> emitted = new ArrayList<>(javaLeadBlocks(ctx, rule));
        emitted.addAll(outcome.passed());
        emitted.add(javaBlock(ClaimType.DISCLAIMER, null, InterpretationTemplates.DISCLAIMER));

        InterpretationStatus status = outcome.overall() == ValidationStatus.VALID
                ? (ctx.kbEmpty() ? InterpretationStatus.DEGRADED : InterpretationStatus.VALID)
                : InterpretationStatus.from(outcome.overall());
        Integer code = status.passed()
                ? (rule.criticalHits().isEmpty() ? null : Integer.valueOf(ErrorCode.REPORT_CRITICAL_HIT.getCode()))
                : Integer.valueOf(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode());

        ReportInterpretationRow row = new ReportInterpretationRow(
                "itp_" + UUID.randomUUID().toString().replace("-", ""), reportId, parse.parseVersion(),
                idempotencyKey, status.name(), code, pinnedKbVersion(), rule.ruleVersion(),
                InterpretationVersions.PROMPT_VERSION, InterpretationVersions.PARSER_VERSION,
                InterpretationVersions.SCHEMA_VERSION, null);
        List<ReportInterpretationBlockRow> blockRows = toBlockRows(row.interpretationId(), emitted);
        List<ReportInterpretationEvidenceRow> evidenceRows = toEvidenceRows(row.interpretationId(), emitted, kbEvidence);
        Optional<ReportInterpretationRow> previous = store.findLatest(reportId);
        try {
            store.save(row, blockRows, evidenceRows);
        } catch (DuplicateKeyException e) {
            // 并发同键：以先入库者为准回放。
            return toView(store.findByIdempotency(reportId, idempotencyKey).orElseThrow(() -> e));
        }
        // 与解读落库同一事务：档案 Outbox（门禁放行时）+ 重要 SSE 事件。任一失败整体回滚。
        publish(job, parse, rule, row, status, facts, blockRows, previous);
        return toView(row, blockRows, evidenceRows);
    }

    /** 解读完成后的同事务副作用：档案 Outbox 入队（仅 MATCHED 且放行）与事件落库。 */
    private void publish(
            ReportJobRow job, ReportParseResultRow parse, ReportRuleService.RuleView rule,
            ReportInterpretationRow row, InterpretationStatus status, List<IndicatorFact> facts,
            List<ReportInterpretationBlockRow> blockRows, Optional<ReportInterpretationRow> previous) {
        String identityStatus = rule.identityCheck().status();
        Optional<ReportArchiveOutboxRow> outbox = archive.enqueueIfAllowed(
                job, parse, identityStatus, rule.archiveBlocked(), row.interpretationId(), status.passed());

        List<ReportEventPayload> payloads = new ArrayList<>();
        previous.ifPresent(p -> payloads.add(new CorrectionPayload(
                p.interpretationId(), row.interpretationId(), CORRECTION_REINTERPRETED)));
        payloads.add(new StructuredResultPayload(
                parse.parseVersion(), identityStatus,
                facts.stream().map(f -> new IndicatorSummary(
                        f.canonicalCode(), f.value() == null ? null : f.value().stripTrailingZeros(), f.unit(),
                        f.reference().name(), f.critical())).toList()));
        List<String> criticalCodes = facts.stream().filter(IndicatorFact::critical)
                .map(IndicatorFact::canonicalCode).toList();
        int alertIndex = 0;
        for (ReportInterpretationBlockRow b : blockRows) {
            // 只有已校验（VALID）的块才会成为事件；非 VALID 永不外发。
            if (!ValidationStatus.VALID.name().equals(b.validationStatus())) {
                continue;
            }
            if (ClaimType.CRITICAL_ALERT.name().equals(b.claimType())) {
                payloads.add(new CriticalAlertPayload(
                        row.interpretationId(), b.blockId(), criticalCodeAt(criticalCodes, alertIndex++),
                        b.content()));
            } else {
                payloads.add(new InterpretationDeltaPayload(
                        row.interpretationId(), b.blockId(), b.claimType(), b.content(), splitIds(b.evidenceIds()),
                        b.validationStatus()));
            }
        }
        if (status.passed()) {
            payloads.add(new DonePayload(
                    row.interpretationId(), ReportLifecycleService.COMPLETED,
                    outbox.map(ReportArchiveOutboxRow::status).orElse(ReportLifecycleService.BLOCKED),
                    lifecycle.criticalReadStatus(job.reportId())));
        } else {
            payloads.add(new ErrorPayload(
                    row.interpretationId(), ErrorCode.REPORT_INTERPRETATION_INVALID.getCode(), status.name()));
        }
        events.append(job.reportId(), job.epoch(), payloads);
    }

    /** 危急提醒块与危急事实按 javaLeadBlocks 的产出顺序一一对应；对不上时为空串（不影响外发）。 */
    private static String criticalCodeAt(List<String> criticalCodes, int index) {
        return index < criticalCodes.size() ? criticalCodes.get(index) : "";
    }

    @Transactional(readOnly = true)
    public InterpretationView latest(String accountId, String reportId) {
        ownedJob(accountId, reportId);
        ReportInterpretationRow row = store.findLatest(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "该报告尚无解读"));
        return toView(row);
    }

    // ---- 事实 ----

    private ReportJobRow ownedJob(String accountId, String reportId) {
        return jobs.findById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
    }

    private List<IndicatorFact> buildFacts(String reportId, ReportParseResultRow parse) {
        List<ReportRuleExecutionRow> execs = executions.listByReport(reportId).stream()
                .filter(r -> r.parseVersion() == parse.parseVersion())
                .toList();
        List<IndicatorFact> facts = new ArrayList<>();
        for (ReportIndicatorRow ind : parses.listIndicators(parse.id())) {
            if (ind.canonicalCode() == null || ind.normalizedValue() == null) {
                continue;
            }
            ReportRuleExecutionRow exec = execs.stream()
                    .filter(e -> ind.id().equals(e.indicatorId()))
                    .findFirst()
                    .orElse(null);
            facts.add(new IndicatorFact(
                    ind.id(), ind.canonicalCode(), ind.rawName(), ind.normalizedValue(), ind.normalizedUnit(),
                    ind.refLow(), ind.refHigh(), referenceOf(exec), isCritical(exec)));
        }
        return facts;
    }

    private static ReferenceStatus referenceOf(ReportRuleExecutionRow exec) {
        if (exec == null) {
            return ReferenceStatus.UNKNOWN;
        }
        try {
            return ReferenceStatus.valueOf(exec.referenceResult());
        } catch (IllegalArgumentException e) {
            return ReferenceStatus.UNKNOWN;
        }
    }

    private static boolean isCritical(ReportRuleExecutionRow exec) {
        return exec != null && exec.criticalResult() != null
                && exec.criticalResult().startsWith(CriticalStatus.CRITICAL.name() + "_");
    }

    /** focus 非空时只保留点名指标；危急指标无论是否被点名都保留，不可被隐藏。 */
    private static List<IndicatorFact> selectFacts(List<IndicatorFact> all, TriggerInterpretationReq req) {
        List<String> focus = req == null || req.focusCanonicalCodes() == null ? List.of() : req.focusCanonicalCodes();
        Set<String> wanted = new HashSet<>();
        for (String c : focus) {
            if (StringUtils.hasText(c)) {
                wanted.add(c.strip().toUpperCase(Locale.ROOT));
            }
        }
        List<IndicatorFact> picked = wanted.isEmpty()
                ? all
                : all.stream()
                        .filter(f -> f.critical() || wanted.contains(f.canonicalCode().toUpperCase(Locale.ROOT)))
                        .toList();
        if (picked.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "没有可解读的指标");
        }
        return picked;
    }

    // ---- 召回 ----

    /** 按指标码逐个做结构化过滤召回；任何一个指标召回为空都不会退回全库。 */
    private List<EvidenceItem> recallEvidence(List<IndicatorFact> facts, ReportJobRow job) {
        Set<String> codes = new LinkedHashSet<>();
        facts.forEach(f -> codes.add(f.canonicalCode()));
        String specialty = properties.getInterpretation().getLabSpecialty();
        List<EvidenceItem> evidence = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            RecallResult r = recall.searchWithReport(RecallQuery.byFilterOnly(), RecallFilter.ofIndicator(code, specialty));
            if (r.emptyReason() == RecallEmptyReason.STORE_UNAVAILABLE) {
                LOG.warn("kb recall store unavailable report={} filter={}", job.reportId(), r.filterExpr());
            }
            for (KnowledgeChunk c : r.chunks()) {
                if (seen.add(c.chunkId())) {
                    evidence.add(toEvidence(c));
                }
            }
        }
        return evidence;
    }

    private static EvidenceItem toEvidence(KnowledgeChunk c) {
        String snippet = c.content().length() > SNIPPET_MAX ? c.content().substring(0, SNIPPET_MAX) : c.content();
        return new EvidenceItem(EVIDENCE_PREFIX + c.chunkId(), c.docId(), c.title(), snippet, c.url(), c.indicatorCode());
    }

    private String pinnedKbVersion() {
        try {
            return kbStore.currentKbVersion(clock.instant()).orElse(InterpretationVersions.KB_EMPTY);
        } catch (RuntimeException e) {
            return InterpretationVersions.KB_UNAVAILABLE;
        }
    }

    // ---- Java 模板块 ----

    private List<DraftBlock> javaLeadBlocks(InterpretationContext ctx, ReportRuleService.RuleView rule) {
        List<DraftBlock> out = new ArrayList<>();
        Set<String> currentCritical = new HashSet<>();
        for (IndicatorFact f : ctx.facts()) {
            if (f.critical()) {
                currentCritical.add(f.canonicalCode());
                out.add(new DraftBlock(
                        newBlockId(), ClaimType.CRITICAL_ALERT, f.canonicalCode(), ClaimedStatus.CRITICAL,
                        f.value(), f.unit(), InterpretationTemplates.criticalAlertText(f), List.of()));
            }
        }
        for (CriticalHitRes hit : rule.criticalHits()) {
            if (!currentCritical.contains(hit.canonicalCode())) {
                // 纠正 / 重解析后当前版本不再危急，但已登记的危急事件不撤销：仍提示，不降级为“已解除”。
                out.add(javaBlock(ClaimType.NOTICE, null, InterpretationTemplates.historicalCriticalNotice(hit.canonicalCode())));
                currentCritical.add(hit.canonicalCode());
            }
        }
        if (ctx.kbEmpty()) {
            out.add(javaBlock(ClaimType.NOTICE, null, InterpretationTemplates.DEGRADE_NOTICE));
        }
        if (!ctx.safeNormalAllowed()) {
            out.add(javaBlock(ClaimType.NOTICE, null, InterpretationTemplates.PENDING_REVIEW_NOTICE));
        }
        return out;
    }

    private static DraftBlock javaBlock(ClaimType type, String code, String text) {
        return new DraftBlock(newBlockId(), type, code, ClaimedStatus.NONE, null, null, text, List.of());
    }

    private static String newBlockId() {
        return "blk_" + UUID.randomUUID().toString().replace("-", "");
    }

    // ---- 落库 / 视图 ----

    private static List<ReportInterpretationBlockRow> toBlockRows(String interpretationId, List<DraftBlock> blocks) {
        List<ReportInterpretationBlockRow> rows = new ArrayList<>();
        int seq = 0;
        for (DraftBlock b : blocks) {
            rows.add(new ReportInterpretationBlockRow(
                    "ibk_" + UUID.randomUUID().toString().replace("-", ""), interpretationId, seq++,
                    newBlockId(), b.claimType().name(), b.text(), String.join(",", b.evidenceIds()),
                    ValidationStatus.VALID.name()));
        }
        return rows;
    }

    private static List<ReportInterpretationEvidenceRow> toEvidenceRows(
            String interpretationId, List<DraftBlock> blocks, List<EvidenceItem> pool) {
        Set<String> cited = new LinkedHashSet<>();
        blocks.forEach(b -> cited.addAll(b.evidenceIds()));
        List<ReportInterpretationEvidenceRow> rows = new ArrayList<>();
        for (String id : cited) {
            pool.stream().filter(e -> e.evidenceId().equals(id)).findFirst().ifPresent(e -> rows.add(
                    new ReportInterpretationEvidenceRow(
                            "iev_" + UUID.randomUUID().toString().replace("-", ""), interpretationId,
                            e.evidenceId(), e.docId(), e.title(), e.snippet(), e.url())));
        }
        return rows;
    }

    private InterpretationView toView(ReportInterpretationRow row) {
        return toView(row, store.listBlocks(row.interpretationId()), store.listEvidence(row.interpretationId()));
    }

    private static InterpretationView toView(
            ReportInterpretationRow row, List<ReportInterpretationBlockRow> blocks,
            List<ReportInterpretationEvidenceRow> evidence) {
        InterpretationRes res = new InterpretationRes(
                row.interpretationId(), row.status(),
                new PinnedVersions(
                        row.kbVersion(), row.ruleVersion(), row.promptVersion(), row.parserVersion(),
                        row.schemaVersion()),
                blocks.stream()
                        .map(b -> new InterpretationBlockRes(
                                b.blockId(), b.claimType(), b.content(), splitIds(b.evidenceIds()), b.validationStatus()))
                        .toList(),
                evidence.stream()
                        .map(e -> new EvidenceRes(e.evidenceId(), e.docId(), e.title(), e.snippet(), e.url()))
                        .toList());
        return new InterpretationView(res, row.resultCode());
    }

    private static List<String> splitIds(String joined) {
        if (!StringUtils.hasText(joined)) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).filter(StringUtils::hasText).toList();
    }

    private static void logRejected(String reportId, GuardOutcome outcome) {
        for (BlockVerdict v : outcome.rejected()) {
            // 仅记录机器码与报告号，不记录文本（可能含患者数据 / 被篡改的内容）。
            LOG.warn("interpretation guard rejected report={} status={} reason={} attempts={}",
                    reportId, v.status(), v.reason(), outcome.attempts());
        }
    }
}
