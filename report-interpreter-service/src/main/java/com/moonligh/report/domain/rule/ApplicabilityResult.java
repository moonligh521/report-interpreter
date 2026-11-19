package com.moonligh.report.domain.rule;

import java.util.List;

/**
 * 适用性结果；missing 列出缺失的必要条件。
 */
public record ApplicabilityResult(ApplicabilityStatus status, List<RuleCondition> missing) {

    public ApplicabilityResult {
        missing = List.copyOf(missing);
    }
}
