package com.moonligh.report.domain.interpret;

import java.util.List;

/**
 * 一次解读的校验与生成上下文。safeNormalAllowed 为报告级闸门：false（身份 / 覆盖 / 规则待复核）时
 * 任何「正常」结论都不允许；为 true 时，单个指标能否说正常仍由其自身规则结论逐项校验。
 */
public record InterpretationContext(
        List<IndicatorFact> facts,
        List<EvidenceItem> evidence,
        boolean safeNormalAllowed) {

    public InterpretationContext {
        facts = facts == null ? List.of() : List.copyOf(facts);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public boolean kbEmpty() {
        return evidence.isEmpty();
    }
}
