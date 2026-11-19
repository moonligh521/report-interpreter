package com.moonligh.report.domain.interpret;

import java.math.BigDecimal;

import com.moonligh.report.domain.rule.ReferenceStatus;

/**
 * 解读事实：由 Java 解析与规则引擎产出的指标事实，是 Validator 的唯一真值来源。
 */
public record IndicatorFact(
        String indicatorId,
        String canonicalCode,
        String name,
        BigDecimal value,
        String unit,
        BigDecimal refLow,
        BigDecimal refHigh,
        ReferenceStatus reference,
        boolean critical) {
}
