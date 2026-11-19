package com.moonligh.report.api.res;

import java.math.BigDecimal;

/**
 * 结构化结果事件里的指标摘要：只含归一化后的码 / 数值 / 单位 / 区间结论，不含原文。
 */
public record IndicatorSummary(
        String canonicalCode,
        BigDecimal value,
        String unit,
        String referenceStatus,
        boolean critical) {
}
