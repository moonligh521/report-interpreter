package com.moonligh.report.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.report.api.res.AbnormalHitRes;
import com.moonligh.report.api.res.CriticalCoverageEvidence;
import com.moonligh.report.api.res.CriticalCoverageRes;
import com.moonligh.report.api.res.CriticalHitRes;
import com.moonligh.report.api.res.IdentityCheckRes;
import com.moonligh.report.domain.critical.CriticalDetection;
import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.CriticalNotificationManager;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.rule.ApplicabilityResult;
import com.moonligh.report.domain.rule.CoverageStatus;
import com.moonligh.report.domain.rule.CriticalCoverageChecker;
import com.moonligh.report.domain.rule.CriticalCoverageResult;
import com.moonligh.report.domain.rule.CriticalRule;
import com.moonligh.report.domain.rule.CriticalStatus;
import com.moonligh.report.domain.rule.CriticalValueEngine;
import com.moonligh.report.domain.rule.CriticalValueResult;
import com.moonligh.report.domain.rule.IdentifiedIndicator;
import com.moonligh.report.domain.rule.IdentityCheckResult;
import com.moonligh.report.domain.rule.IdentityStatus;
import com.moonligh.report.domain.rule.IndicatorOutcome;
import com.moonligh.report.domain.rule.PatientFacts;
import com.moonligh.report.domain.rule.ReferenceRangeEngine;
import com.moonligh.report.domain.rule.ReferenceRangeResult;
import com.moonligh.report.domain.rule.ReferenceStatus;
import com.moonligh.report.domain.rule.ReportIdentityConsistencyChecker;
import com.moonligh.report.domain.rule.RuleContext;
import com.moonligh.report.domain.rule.RuleContextResolver;
import com.moonligh.report.domain.rule.RuleExecutionStore;
import com.moonligh.report.domain.rule.RuleSetVersion;
import com.moonligh.report.domain.rule.RuleSummaryStore;
import com.moonligh.report.domain.rule.SafetyGate;
import com.moonligh.report.domain.rule.SafetyVerdict;
import com.moonligh.report.infrastructure.persistence.CriticalNotificationOutboxRow;
import com.moonligh.report.infrastructure.persistence.ReportCriticalEventRow;
import com.moonligh.report.infrastructure.persistence.ReportRuleExecutionRow;
import com.moonligh.report.infrastructure.persistence.ReportRuleSummaryRow;

/**
 * 规则执行编排：身份 → 覆盖 → RuleContext → 参考区间 / 适用性 / 危急 → 持久化 → 报告状态。
 * 危急事件仅依赖规则表，不依赖 RAG/LLM；任何一步异常都会让整个事务回滚（fail-closed）。
 */
@Service
public class ReportRuleService {

    private final ReportJobStore jobs;
    private final RuleExecutionStore executions;
    private final RuleSummaryStore summaries;
    private final CriticalEventStore events;
    private final CriticalNotificationManager notifications;
    private final ReportIdentityConsistencyChecker identityChecker;
    private final CriticalCoverageChecker coverageChecker;
    private final RuleContextResolver resolver;
    private final ReferenceRangeEngine referenceEngine;
    private final CriticalValueEngine criticalEngine;
    private final SafetyGate gate;
    private final ObjectMapper json = new ObjectMapper();

    public ReportRuleService(
            ReportJobStore jobs,
            RuleExecutionStore executions,
            RuleSummaryStore summaries,
            CriticalEventStore events,
            CriticalNotificationManager notifications,
            ReportIdentityConsistencyChecker identityChecker,
            CriticalCoverageChecker coverageChecker,
            RuleContextResolver resolver,
            ReferenceRangeEngine referenceEngine,
            CriticalValueEngine criticalEngine,
            SafetyGate gate) {
        this.jobs = jobs;
        this.executions = executions;
        this.summaries = summaries;
        this.events = events;
        this.notifications = notifications;
        this.identityChecker = identityChecker;
        this.coverageChecker = coverageChecker;
        this.resolver = resolver;
        this.referenceEngine = referenceEngine;
        this.criticalEngine = criticalEngine;
        this.gate = gate;
    }

    /** 对某解析版本执行规则，写入快照 / 危急事件 / 汇总，并把报告状态推进到规则出口状态。 */
    @Transactional
    public SafetyVerdict run(
            String reportId, int parseVersion, ParseResult parse,
            List<IdentifiedIndicator> indicators, String expectedPatientName) {
        Instant now = Instant.now();
        IdentityCheckResult identity = identityChecker.check(parse.meta().patientName(), expectedPatientName);
        CriticalCoverageResult coverage = coverageChecker.check(parse.indicators(), parse.meta());

        List<IndicatorOutcome> outcomes = new ArrayList<>();
        for (IdentifiedIndicator ii : indicators) {
            IndicatorOutcome outcome = evaluate(ii);
            persistExecution(reportId, parseVersion, outcome);
            outcomes.add(outcome);
        }
        SafetyVerdict verdict = gate.verdict(identity, coverage, outcomes);

        boolean deliverable = identity.status() == IdentityStatus.MATCHED
                && coverage.status() == CoverageStatus.COMPLETE;
        registerCriticalEvents(reportId, parseVersion, outcomes, deliverable, now);

        summaries.insert(new ReportRuleSummaryRow(
                reportId, parseVersion, RuleSetVersion.CURRENT,
                identity.status().name(), identity.detail(),
                coverage.status().name(), writeEvidence(coverage.evidence()),
                verdict.status().name(), verdict.archiveBlocked(), verdict.safeNormalAllowed(),
                verdict.blockCode(), null));
        jobs.updateStatus(reportId, verdict.status().name());
        return verdict;
    }

    /** 读取某解析版本的规则视图；该版本未执行过规则时为空。 */
    @Transactional(readOnly = true)
    public Optional<RuleView> view(String reportId, int parseVersion) {
        return summaries.find(reportId, parseVersion).map(s -> new RuleView(
                new IdentityCheckRes(s.identityStatus(), s.identityDetail()),
                new CriticalCoverageRes(s.coverageStatus(), readEvidence(s.coverageEvidenceJson())),
                abnormals(reportId, parseVersion),
                criticalEvents(reportId),
                s.archiveBlocked(),
                s.safeNormalAllowed(),
                s.blockCode(),
                s.ruleVersion()));
    }

    /** 报告的全部危急事件（含通知状态），独立于解读生命周期。 */
    @Transactional(readOnly = true)
    public List<CriticalHitRes> criticalEvents(String reportId) {
        List<CriticalHitRes> hits = new ArrayList<>();
        for (ReportCriticalEventRow e : events.listEventsByReport(reportId)) {
            String notification = events.findOutboxByEvent(e.eventId())
                    .map(CriticalNotificationOutboxRow::notificationStatus)
                    .orElse(null);
            hits.add(new CriticalHitRes(
                    e.eventId(), e.canonicalCode(), "CRITICAL_" + e.criticalLevel(), e.eventStatus(), notification));
        }
        return hits;
    }

    // ---- 内部 ----

    private IndicatorOutcome evaluate(IdentifiedIndicator ii) {
        RuleContext ctx = resolver.resolve(ii.indicatorId(), ii.indicator(), PatientFacts.UNKNOWN);
        List<CriticalRule> rules = criticalEngine.table().rulesFor(ctx.canonicalCode());
        ApplicabilityResult applicability = gate.applicability(ctx, rules);
        ReferenceRangeResult reference = referenceEngine.evaluate(ctx);
        CriticalValueResult critical = criticalEngine.evaluate(ctx, applicability);
        return new IndicatorOutcome(ctx, applicability, reference, critical);
    }

    private void persistExecution(String reportId, int parseVersion, IndicatorOutcome o) {
        executions.insert(new ReportRuleExecutionRow(
                "rex_" + UUID.randomUUID().toString().replace("-", ""),
                reportId, parseVersion, RuleSetVersion.CURRENT,
                o.context().indicatorId(), o.context().canonicalCode(),
                o.reference().ruleId(), o.critical().ruleId(),
                RuleContextJson.write(o.context()),
                o.reference().status().name(), o.critical().code(), o.applicability().status().name(),
                null));
    }

    private void registerCriticalEvents(
            String reportId, int parseVersion, List<IndicatorOutcome> outcomes, boolean deliverable, Instant now) {
        for (IndicatorOutcome o : outcomes) {
            CriticalValueResult c = o.critical();
            if (c.status() != CriticalStatus.CRITICAL) {
                continue;
            }
            notifications.register(new CriticalDetection(
                    reportId, parseVersion, o.context().canonicalCode(), c.level().name(),
                    o.context().value().stripTrailingZeros().toPlainString(), now), deliverable);
        }
    }

    private List<AbnormalHitRes> abnormals(String reportId, int parseVersion) {
        return executions.listByReport(reportId).stream()
                .filter(r -> r.parseVersion() == parseVersion)
                .filter(r -> ReferenceStatus.HIGH.name().equals(r.referenceResult())
                        || ReferenceStatus.LOW.name().equals(r.referenceResult()))
                .map(r -> new AbnormalHitRes(r.canonicalCode(), r.referenceResult(), r.referenceRuleId(), r.ruleVersion()))
                .toList();
    }

    private String writeEvidence(CriticalCoverageEvidence evidence) {
        try {
            return json.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private CriticalCoverageEvidence readEvidence(String text) {
        try {
            return json.readValue(text, CriticalCoverageEvidence.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 规则视图（服务层强类型对象）。 */
    public record RuleView(
            IdentityCheckRes identityCheck,
            CriticalCoverageRes criticalCoverage,
            List<AbnormalHitRes> abnormals,
            List<CriticalHitRes> criticalHits,
            boolean archiveBlocked,
            boolean safeNormalAllowed,
            Integer blockCode,
            String ruleVersion) {
    }
}
