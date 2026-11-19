package com.moonligh.report.domain.rule;

import java.math.BigDecimal;
import java.util.List;

import com.moonligh.report.domain.rule.ReferenceRangeResult.ReferenceSource;

/**
 * 参考区间引擎。优先级：
 * 1. 报告原文区间，且 refRange 置信度、格式、单位均合格；
 * 2. 内置标准区间（无人群条件依赖的项目，版本化）；
 * 3. UNKNOWN。
 * 低置信原文区间不得使用（R-R9）。
 */
public final class ReferenceRangeEngine {

    /** 原文区间可信所需的最低 refRange 置信度。 */
    public static final double MIN_REPORT_RANGE_CONFIDENCE = 0.90;

    static final String REPORT_RANGE_RULE_ID = "REF-REPORT-RANGE";

    /** 内置标准区间：仅收录不依赖性别/年龄/空腹等条件的项目。 */
    private record StandardRange(
            String ruleId, String canonicalCode, String specimenType, String unit, BigDecimal low, BigDecimal high) {
    }

    private static final List<StandardRange> STANDARD_RANGES = List.of(
            new StandardRange("REF-STD-K", "K", "SERUM", "mmol/L", new BigDecimal("3.5"), new BigDecimal("5.3")),
            new StandardRange("REF-STD-NA", "NA", "SERUM", "mmol/L", new BigDecimal("135"), new BigDecimal("145")));

    public ReferenceRangeResult evaluate(RuleContext ctx) {
        if (ctx.value() == null || ctx.unit() == null) {
            return unknown();
        }
        if (reportRangeUsable(ctx)) {
            ReferenceRangeEvidence r = ctx.referenceRangeEvidence();
            return compare(ctx.value(), r.low(), r.high(), ReferenceSource.REPORT_RANGE, REPORT_RANGE_RULE_ID);
        }
        for (StandardRange s : STANDARD_RANGES) {
            if (matches(s, ctx)) {
                return compare(ctx.value(), s.low(), s.high(), ReferenceSource.BUILT_IN_STANDARD, s.ruleId());
            }
        }
        return unknown();
    }

    /** 原文区间可信：置信度达标、上下限完整且 0<=low<high、区间单位与规范化单位一致。 */
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

    private static boolean matches(StandardRange s, RuleContext ctx) {
        return s.canonicalCode().equals(ctx.canonicalCode())
                && s.specimenType().equalsIgnoreCase(ctx.specimenType())
                && s.unit().equalsIgnoreCase(ctx.unit());
    }

    private static ReferenceRangeResult compare(
            BigDecimal value, BigDecimal low, BigDecimal high, ReferenceSource source, String ruleId) {
        ReferenceStatus status = value.compareTo(low) < 0
                ? ReferenceStatus.LOW
                : value.compareTo(high) > 0 ? ReferenceStatus.HIGH : ReferenceStatus.NORMAL;
        return new ReferenceRangeResult(status, source, low, high, ruleId, RuleSetVersion.CURRENT);
    }

    private static ReferenceRangeResult unknown() {
        return new ReferenceRangeResult(
                ReferenceStatus.UNKNOWN, ReferenceSource.NONE, null, null, null, RuleSetVersion.CURRENT);
    }
}
