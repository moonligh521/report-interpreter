package com.moonligh.report.domain.rule;

import java.util.List;

/**
 * 版本化临床目录：危急规则 + 分层参考区间。禁 Map。
 */
public record ClinicalCatalog(
        String version,
        List<CriticalRule> criticalRules,
        List<ReferenceRangeRule> referenceRules) {

    public ClinicalCatalog {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        criticalRules = criticalRules == null ? List.of() : List.copyOf(criticalRules);
        referenceRules = referenceRules == null ? List.of() : List.copyOf(referenceRules);
    }

    public CriticalRuleTable criticalRuleTable() {
        return new CriticalRuleTable(criticalRules);
    }
}
