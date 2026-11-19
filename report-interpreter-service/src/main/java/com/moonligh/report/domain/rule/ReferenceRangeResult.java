package com.moonligh.report.domain.rule;

import java.math.BigDecimal;

/**
 * 参考区间结果（≠ CriticalValueResult ≠ 解读）。source 说明区间来源。
 */
public record ReferenceRangeResult(
        ReferenceStatus status,
        ReferenceSource source,
        BigDecimal low,
        BigDecimal high,
        String ruleId,
        String ruleVersion) {

    /** 区间来源，按优先级：原文可信区间 → 内置标准区间 → 无。 */
    public enum ReferenceSource {
        REPORT_RANGE,
        BUILT_IN_STANDARD,
        NONE
    }
}
