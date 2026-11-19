package com.moonligh.report.domain.rule;

import java.util.List;

/**
 * 危急值引擎：只依赖规则表与已通过适用性检查的上下文，不使用报告参考区间，也不依赖 RAG/LLM。
 */
public final class CriticalValueEngine {

    private final CriticalRuleTable table;

    public CriticalValueEngine(CriticalRuleTable table) {
        this.table = table;
    }

    public CriticalRuleTable table() {
        return table;
    }

    /** 判定。适用性 UNKNOWN 直接得 UNKNOWN，绝不降级成 NOT_CRITICAL。 */
    public CriticalValueResult evaluate(RuleContext ctx, ApplicabilityResult applicability) {
        List<CriticalRule> rules = table.rulesFor(ctx.canonicalCode());
        return switch (applicability.status()) {
            case NOT_REQUIRED -> new CriticalValueResult(CriticalStatus.NO_RULE, null, null, RuleSetVersion.CURRENT);
            case UNKNOWN -> new CriticalValueResult(CriticalStatus.UNKNOWN, null, null, RuleSetVersion.CURRENT);
            case APPLICABLE -> judge(ctx, rules);
        };
    }

    private static CriticalValueResult judge(RuleContext ctx, List<CriticalRule> rules) {
        for (CriticalRule rule : rules) {
            if (rule.hit(ctx.value())) {
                return new CriticalValueResult(CriticalStatus.CRITICAL, rule.level(), rule.ruleId(), rule.version());
            }
        }
        CriticalRule first = rules.get(0);
        return new CriticalValueResult(CriticalStatus.NOT_CRITICAL, null, first.ruleId(), first.version());
    }
}
