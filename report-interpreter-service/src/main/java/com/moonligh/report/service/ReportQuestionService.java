package com.moonligh.report.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.req.AskQuestionReq;
import com.moonligh.report.api.res.AskQuestionRes;
import com.moonligh.report.api.res.EvidenceRes;
import com.moonligh.report.api.res.InterpretationBlockRes;
import com.moonligh.report.api.res.PinnedVersions;
import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.interpret.ClaimType;
import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.EvidenceItem;
import com.moonligh.report.domain.interpret.GuardOutcome;
import com.moonligh.report.domain.interpret.IndicatorFact;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationModel;
import com.moonligh.report.domain.interpret.InterpretationOutputGuard;
import com.moonligh.report.domain.interpret.InterpretationTemplates;
import com.moonligh.report.domain.interpret.InterpretationVersions;
import com.moonligh.report.domain.interpret.ValidationStatus;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.domain.kb.HybridRecall;
import com.moonligh.report.domain.kb.KbChunkStore;
import com.moonligh.report.domain.kb.KnowledgeChunk;
import com.moonligh.report.domain.kb.RecallFilter;
import com.moonligh.report.domain.kb.RecallQuery;
import com.moonligh.report.domain.kb.RecallResult;
import com.moonligh.report.domain.parse.ReportParseStore;
import com.moonligh.report.domain.rule.CriticalStatus;
import com.moonligh.report.domain.rule.ReferenceStatus;
import com.moonligh.report.domain.rule.RuleExecutionStore;
import com.moonligh.report.infrastructure.persistence.ReportIndicatorRow;
import com.moonligh.report.infrastructure.persistence.ReportJobRow;
import com.moonligh.report.infrastructure.persistence.ReportParseResultRow;
import com.moonligh.report.infrastructure.persistence.ReportQuestionMapper;
import com.moonligh.report.infrastructure.persistence.ReportQuestionRow;
import com.moonligh.report.infrastructure.persistence.ReportRuleExecutionRow;

/**
 * 追问：基于已解析结果 + Guard/Validator；不信任前端改数（M-R3）。
 */
@Service
public class ReportQuestionService {

    private static final String EVIDENCE_PREFIX = "evd-";
    private static final int SNIPPET_MAX = 1000;

    private final ReportJobStore jobs;
    private final ReportParseStore parses;
    private final RuleExecutionStore executions;
    private final ReportRuleService rules;
    private final HybridRecall recall;
    private final KbChunkStore kbStore;
    private final InterpretationModel model;
    private final InterpretationOutputGuard guard;
    private final ReportProperties properties;
    private final ReportQuestionMapper questions;
    private final ObjectMapper json = new ObjectMapper();

    public ReportQuestionService(
            ReportJobStore jobs, ReportParseStore parses, RuleExecutionStore executions, ReportRuleService rules,
            HybridRecall recall, KbChunkStore kbStore, InterpretationModel model, InterpretationOutputGuard guard,
            ReportProperties properties, ReportQuestionMapper questions) {
        this.jobs = jobs;
        this.parses = parses;
        this.executions = executions;
        this.rules = rules;
        this.recall = recall;
        this.kbStore = kbStore;
        this.model = model;
        this.guard = guard;
        this.properties = properties;
        this.questions = questions;
    }

    /** code 为 null 表示 200，否则为 4107 等。 */
    public record QuestionView(AskQuestionRes data, Integer code) {
    }

    @Transactional
    public QuestionView ask(String accountId, String reportId, String idempotencyKey, AskQuestionReq req) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少或非法 Idempotency-Key");
        }
        if (req == null || !StringUtils.hasText(req.question())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "question 不能为空");
        }
        ReportJobRow job = jobs.lockById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        Optional<ReportQuestionRow> replay = Optional.ofNullable(questions.findByIdempotency(reportId, idempotencyKey));
        if (replay.isPresent()) {
            return replayOf(replay.get());
        }

        ReportParseResultRow parse = parses.findActive(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "报告尚无解析结果"));
        ReportRuleService.RuleView rule = rules.view(reportId, parse.parseVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_RULE_PENDING_REVIEW, "报告规则尚未执行"));
        if ("CONFLICT".equals(rule.identityCheck().status())) {
            throw new BusinessException(ErrorCode.REPORT_IDENTITY_CONFLICT, "报告身份与就诊人冲突，不予追问");
        }
        if ("MISSING".equals(rule.identityCheck().status())) {
            throw new BusinessException(ErrorCode.REPORT_IDENTITY_MISSING, "报告缺少身份信息，不予追问");
        }

        List<IndicatorFact> facts = selectFacts(buildFacts(reportId, parse), req);
        List<EvidenceItem> kbEvidence = recallEvidence(facts);
        if (kbEvidence.isEmpty() && !properties.getInterpretation().isAllowDegraded()) {
            throw new BusinessException(ErrorCode.REPORT_KB_UNAVAILABLE, "知识库无可引用依据，且不允许无依据解读");
        }
        boolean normalClaimsAllowed = rule.blockCode() == null;
        InterpretationContext ctx = new InterpretationContext(facts, kbEvidence, normalClaimsAllowed);
        GuardOutcome outcome = guard.guard(model, ctx, properties.getInterpretation().getMaxAttempts());

        Integer code = outcome.overall() == ValidationStatus.VALID
                ? null
                : Integer.valueOf(ErrorCode.REPORT_INTERPRETATION_INVALID.getCode());
        // 仅外发通过 Guard 的块；改数拦截时仍可能有空列表。
        List<DraftBlock> passed = outcome.passed();
        List<InterpretationBlockRes> blocks = new ArrayList<>();
        for (DraftBlock b : passed) {
            blocks.add(new InterpretationBlockRes(
                    b.blockId(), b.claimType().name(), b.text(), b.evidenceIds(), ValidationStatus.VALID.name()));
        }
        if (code == null) {
            blocks.add(new InterpretationBlockRes(
                    "blk_" + UUID.randomUUID().toString().replace("-", ""), ClaimType.DISCLAIMER.name(),
                    InterpretationTemplates.DISCLAIMER, List.of(), ValidationStatus.VALID.name()));
        }
        List<EvidenceRes> evidence = toEvidenceRes(passed, kbEvidence);
        PinnedVersions pinned = new PinnedVersions(
                pinnedKbVersion(), rule.ruleVersion(), InterpretationVersions.PROMPT_VERSION,
                InterpretationVersions.PARSER_VERSION, InterpretationVersions.SCHEMA_VERSION);
        String answerId = "ans_" + UUID.randomUUID().toString().replace("-", "");
        AskQuestionRes res = new AskQuestionRes(answerId, blocks, evidence, pinned);
        ReportQuestionRow row = new ReportQuestionRow(
                answerId, reportId, req.question().strip(), focusJson(req), idempotencyKey, write(res), code, null);
        try {
            questions.insert(row);
        } catch (DuplicateKeyException e) {
            return replayOf(questions.findByIdempotency(reportId, idempotencyKey));
        }
        return new QuestionView(res, code);
    }

    private QuestionView replayOf(ReportQuestionRow row) {
        try {
            AskQuestionRes res = json.readValue(row.responseJson(), AskQuestionRes.class);
            return new QuestionView(res, row.resultCode());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(AskQuestionRes res) {
        try {
            return json.writeValueAsString(res);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String focusJson(AskQuestionReq req) {
        List<String> codes = req.focusCanonicalCodes() == null ? List.of() : req.focusCanonicalCodes();
        try {
            return new ObjectMapper().writeValueAsString(codes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
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

    private static List<IndicatorFact> selectFacts(List<IndicatorFact> all, AskQuestionReq req) {
        List<String> focus = req.focusCanonicalCodes() == null ? List.of() : req.focusCanonicalCodes();
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
            throw new BusinessException(ErrorCode.PARAM_INVALID, "没有可追问的指标");
        }
        return picked;
    }

    private List<EvidenceItem> recallEvidence(List<IndicatorFact> facts) {
        Set<String> codes = new LinkedHashSet<>();
        facts.forEach(f -> codes.add(f.canonicalCode()));
        String specialty = properties.getInterpretation().getLabSpecialty();
        List<EvidenceItem> evidence = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            RecallResult r = recall.searchWithReport(RecallQuery.byFilterOnly(), RecallFilter.ofIndicator(code, specialty));
            for (KnowledgeChunk c : r.chunks()) {
                if (seen.add(c.chunkId())) {
                    String snippet = c.content().length() > SNIPPET_MAX
                            ? c.content().substring(0, SNIPPET_MAX) : c.content();
                    evidence.add(new EvidenceItem(
                            EVIDENCE_PREFIX + c.chunkId(), c.docId(), c.title(), snippet, c.url(), c.indicatorCode()));
                }
            }
        }
        return evidence;
    }

    private String pinnedKbVersion() {
        try {
            return kbStore.currentKbVersion(java.time.Instant.now()).orElse(InterpretationVersions.KB_EMPTY);
        } catch (RuntimeException e) {
            return InterpretationVersions.KB_UNAVAILABLE;
        }
    }

    private static List<EvidenceRes> toEvidenceRes(List<DraftBlock> blocks, List<EvidenceItem> pool) {
        Set<String> cited = new LinkedHashSet<>();
        blocks.forEach(b -> cited.addAll(b.evidenceIds()));
        List<EvidenceRes> out = new ArrayList<>();
        for (String id : cited) {
            pool.stream().filter(e -> e.evidenceId().equals(id)).findFirst().ifPresent(e ->
                    out.add(new EvidenceRes(e.evidenceId(), e.docId(), e.title(), e.snippet(), e.url())));
        }
        return out;
    }
}
