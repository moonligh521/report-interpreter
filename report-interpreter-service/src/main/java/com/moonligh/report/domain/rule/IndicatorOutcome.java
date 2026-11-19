package com.moonligh.report.domain.rule;

/**
 * 单个指标的三类规则结果（互相独立）：适用性、参考区间、危急值。
 */
public record IndicatorOutcome(
        RuleContext context,
        ApplicabilityResult applicability,
        ReferenceRangeResult reference,
        CriticalValueResult critical) {
}
