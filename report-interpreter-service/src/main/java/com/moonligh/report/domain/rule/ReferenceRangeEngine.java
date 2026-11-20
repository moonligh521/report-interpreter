package com.moonligh.report.domain.rule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.moonligh.report.domain.rule.ReferenceRangeResult.ReferenceSource;

/**
 * 参考区间引擎。优先级：
 * 1. 报告原文区间，且 refRange 置信度、格式、单位均合格；
 * 2. 目录中带人群条件且匹配的规则；
 * 3. 目录中无人群条件的标准规则；
 * 4. UNKNOWN。
 * 低置信原文区间不得使用（R-R9）。
 */
public final class ReferenceRangeEngine {

    /** 原文区间可信所需的最低 refRange 置信度。 */
    public static final double MIN_REPORT_RANGE_CONFIDENCE = 0.90;

    static final String REPORT_RANGE_RULE_ID = "REF-REPORT-RANGE";

    private final List<ReferenceRangeRule> catalogRules;

    /** 默认内置标准区间（无目录注入时，兼容既有单测）。 */
    public ReferenceRangeEngine() {
        this(defaultStandardRules());
    }

    public ReferenceRangeEngine(List<ReferenceRangeRule> catalogRules) {
        this.catalogRules = catalogRules == null ? List.of() : List.copyOf(catalogRules);
    }

    public ReferenceRangeResult evaluate(RuleContext ctx) {
        if (ctx.value() == null || ctx.unit() == null) {
            return unknown(RuleSetVersion.CURRENT);
        }
        if (reportRangeUsable(ctx)) {
            ReferenceRangeEvidence r = ctx.referenceRangeEvidence();
            return compare(
                    ctx.value(), r.low(), r.high(), ReferenceSource.REPORT_RANGE,
                    REPORT_RANGE_RULE_ID, RuleSetVersion.CURRENT);
        }
        ReferenceRangeRule stratified = firstMatch(ctx, true);
        if (stratified != null) {
            return compare(
                    ctx.value(), stratified.low(), stratified.high(), ReferenceSource.BUILT_IN_STANDARD,
                    stratified.ruleId(), stratified.version());
        }
        ReferenceRangeRule standard = firstMatch(ctx, false);
        if (standard != null) {
            return compare(
                    ctx.value(), standard.low(), standard.high(), ReferenceSource.BUILT_IN_STANDARD,
                    standard.ruleId(), standard.version());
        }
        return unknown(RuleSetVersion.CURRENT);
    }

    /** 原文区间可信：置信度达标、上下限完整且 0<=low&lt;high、区间单位与规范化单位一致。 */
    static boolean reportRangeUsable(RuleContext ctx) {
        ReferenceRangeEvidence r = ctx.referenceRangeEvidence();
        if (r == null || r.confidence() < MIN_REPORT_RANGE_CONFIDENCE) {
            return false;
        }
        if (r.low() == null || r.high() == null || r.low().signum() < 0 || r.low().compareTo(r.high()) >= 0) {
            return false;
        }
        return r.unit() != null && r.unit().trim().equalsIgnoreCase(ctx.unit());
    }

    private ReferenceRangeRule firstMatch(RuleContext ctx, boolean requireDemographics) {
        for (ReferenceRangeRule rule : catalogRules) {
            boolean hasDemo = rule.minAgeInclusive() != null
                    || rule.maxAgeExclusive() != null
                    || rule.sex() != null;
            if (requireDemographics != hasDemo) {
                continue;
            }
            if (!codeMatch(rule, ctx)) {
                continue;
            }
            if (requireDemographics && !rule.matchesDemographics(ctx.age(), ctx.sex())) {
                continue;
            }
            return rule;
        }
        return null;
    }

    private static boolean codeMatch(ReferenceRangeRule rule, RuleContext ctx) {
        return rule.canonicalCode().equals(ctx.canonicalCode())
                && rule.specimenType().equalsIgnoreCase(ctx.specimenType())
                && rule.unit().equalsIgnoreCase(ctx.unit());
    }

    private static ReferenceRangeResult compare(
            BigDecimal value, BigDecimal low, BigDecimal high, ReferenceSource source,
            String ruleId, String ruleVersion) {
        ReferenceStatus status = value.compareTo(low) < 0
                ? ReferenceStatus.LOW
                : value.compareTo(high) > 0 ? ReferenceStatus.HIGH : ReferenceStatus.NORMAL;
        return new ReferenceRangeResult(status, source, low, high, ruleId, ruleVersion);
    }

    private static ReferenceRangeResult unknown(String ruleVersion) {
        return new ReferenceRangeResult(
                ReferenceStatus.UNKNOWN, ReferenceSource.NONE, null, null, null, ruleVersion);
    }

    static List<ReferenceRangeRule> defaultStandardRules() {
        List<ReferenceRangeRule> rules = new ArrayList<>();
        rules.add(new ReferenceRangeRule(
                "REF-STD-K", RuleSetVersion.CURRENT, "K", "SERUM", "mmol/L",
                new BigDecimal("3.5"), new BigDecimal("5.3"), null, null, null));
        rules.add(new ReferenceRangeRule(
                "REF-STD-NA", RuleSetVersion.CURRENT, "NA", "SERUM", "mmol/L",
                new BigDecimal("135"), new BigDecimal("145"), null, null, null));
        return List.copyOf(rules);
    }
}
