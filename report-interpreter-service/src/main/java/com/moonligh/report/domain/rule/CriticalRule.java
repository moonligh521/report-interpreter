package com.moonligh.report.domain.rule;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 一条版本化危急规则。阈值只来自规则表，绝不读取报告自带参考区间。
 */
public record CriticalRule(
        String ruleId,
        String version,
        String canonicalCode,
        String specimenType,
        String unit,
        CriticalLevel level,
        BigDecimal threshold,
        boolean inclusive,
        Set<RuleCondition> required) {

    public CriticalRule {
        required = Set.copyOf(required);
    }

    /** 数值是否触发本规则。HIGH：>= / >；LOW：<= / <。 */
    public boolean hit(BigDecimal value) {
        int cmp = value.compareTo(threshold);
        if (level == CriticalLevel.HIGH) {
            return inclusive ? cmp >= 0 : cmp > 0;
        }
        return inclusive ? cmp <= 0 : cmp < 0;
    }
}
