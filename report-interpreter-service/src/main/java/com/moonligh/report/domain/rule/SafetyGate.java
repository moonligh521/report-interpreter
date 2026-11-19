package com.moonligh.report.domain.rule;

import java.util.ArrayList;
import java.util.List;

import com.moonligh.consult.framework.ErrorCode;

/**
 * 适用性与安全闸门。
 * <ul>
 *   <li>{@link #applicability}：必要条件缺失 → UNKNOWN，绝不当作 NOT_CRITICAL</li>
 *   <li>{@link #verdict}：身份 / 覆盖 / 规则结果汇总成报告级结论，决定状态、阻断归档与「安全正常」资格</li>
 * </ul>
 */
public final class SafetyGate {

    /** 关键字段（name/value/unit/normalization）最低置信度。 */
    public static final double MIN_FIELD_CONFIDENCE = 0.90;

    public ApplicabilityResult applicability(RuleContext ctx, List<CriticalRule> rules) {
        if (rules.isEmpty()) {
            return new ApplicabilityResult(ApplicabilityStatus.NOT_REQUIRED, List.of());
        }
        List<RuleCondition> missing = new ArrayList<>();
        for (CriticalRule rule : rules) {
            for (RuleCondition c : rule.required()) {
                if (!missing.contains(c) && !satisfied(c, ctx, rule)) {
                    missing.add(c);
                }
            }
        }
        return new ApplicabilityResult(
                missing.isEmpty() ? ApplicabilityStatus.APPLICABLE : ApplicabilityStatus.UNKNOWN, missing);
    }

    private static boolean satisfied(RuleCondition c, RuleContext ctx, CriticalRule rule) {
        return switch (c) {
            case CANONICAL -> ctx.canonicalCode() != null && ctx.canonicalCode().equals(rule.canonicalCode());
            case VALUE -> ctx.value() != null;
            case UNIT -> ctx.unit() != null && ctx.unit().equalsIgnoreCase(rule.unit());
            case SPECIMEN_TYPE -> ctx.specimenType() != null && ctx.specimenType().equalsIgnoreCase(rule.specimenType());
            case FIELD_CONFIDENCE -> fieldConfidenceOk(ctx);
            case AGE -> ctx.age() != null;
            case SEX -> ctx.sex() != null && !ctx.sex().isBlank();
            case SPECIMEN_TIME -> ctx.specimenTime() != null;
        };
    }

    /** 危急判定只看 name/value/unit/normalization，不看 refRange（原文区间不参与 P0 判定）。 */
    private static boolean fieldConfidenceOk(RuleContext ctx) {
        var f = ctx.fieldConfidence();
        if (f == null) {
            return false;
        }
        double min = Math.min(Math.min(f.name(), f.value()), Math.min(f.unit(), f.normalization()));
        return min >= MIN_FIELD_CONFIDENCE;
    }

    public SafetyVerdict verdict(
            IdentityCheckResult identity, CriticalCoverageResult coverage, List<IndicatorOutcome> outcomes) {
        boolean archiveBlocked = identity.status() != IdentityStatus.MATCHED
                || coverage.status() == CoverageStatus.UNKNOWN;
        boolean safeNormal = identity.status() == IdentityStatus.MATCHED
                && coverage.status() == CoverageStatus.COMPLETE
                && outcomes.stream().allMatch(SafetyGate::indicatorSafe);

        if (identity.status() == IdentityStatus.CONFLICT) {
            return new SafetyVerdict(RuleOutcomeStatus.BLOCKED, true, false, ErrorCode.REPORT_IDENTITY_CONFLICT.getCode());
        }
        if (identity.status() == IdentityStatus.MISSING) {
            return new SafetyVerdict(
                    RuleOutcomeStatus.NEED_MANUAL_REVIEW, true, false, ErrorCode.REPORT_IDENTITY_MISSING.getCode());
        }
        if (coverage.status() == CoverageStatus.UNKNOWN) {
            return new SafetyVerdict(
                    RuleOutcomeStatus.NEED_MANUAL_REVIEW, archiveBlocked, false,
                    ErrorCode.REPORT_CRITICAL_COVERAGE_UNKNOWN.getCode());
        }
        boolean criticalUnknown = outcomes.stream().anyMatch(o -> o.critical().status() == CriticalStatus.UNKNOWN);
        if (coverage.status() == CoverageStatus.PARTIAL || criticalUnknown) {
            return new SafetyVerdict(
                    RuleOutcomeStatus.NEED_MANUAL_REVIEW, archiveBlocked, false,
                    ErrorCode.REPORT_RULE_PENDING_REVIEW.getCode());
        }
        return new SafetyVerdict(RuleOutcomeStatus.RULE_CHECKED, archiveBlocked, safeNormal, null);
    }

    /** 指标可被表述为正常：区间判定 NORMAL 且危急判定不是 CRITICAL / UNKNOWN。 */
    private static boolean indicatorSafe(IndicatorOutcome o) {
        return o.reference().status() == ReferenceStatus.NORMAL
                && o.critical().status() != CriticalStatus.CRITICAL
                && o.critical().status() != CriticalStatus.UNKNOWN;
    }
}
