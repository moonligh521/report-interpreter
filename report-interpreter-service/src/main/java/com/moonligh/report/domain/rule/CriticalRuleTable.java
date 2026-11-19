package com.moonligh.report.domain.rule;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 版本化危急规则表（代码内置，非 Map）。
 * 当前仅收录血清钾 K：HIGH >= 6.0 mmol/L、LOW < 2.5 mmol/L。
 * 内置阈值为工程默认，上线前须由合作机构审核替换；未审核的其它项目一律不内置（见 NO_RULE）。
 */
public final class CriticalRuleTable {

    /** K 判定所需的必要条件：身份项、数值、单位、标本、关键字段置信度。 */
    private static final Set<RuleCondition> K_REQUIRED = EnumSet.of(
            RuleCondition.CANONICAL, RuleCondition.VALUE, RuleCondition.UNIT,
            RuleCondition.SPECIMEN_TYPE, RuleCondition.FIELD_CONFIDENCE);

    private final List<CriticalRule> rules;

    public CriticalRuleTable(List<CriticalRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** 当前内置规则集。 */
    public static CriticalRuleTable builtIn() {
        String v = RuleSetVersion.CURRENT;
        return new CriticalRuleTable(List.of(
                new CriticalRule("CRIT-K-HIGH", v, "K", "SERUM", "mmol/L", CriticalLevel.HIGH,
                        new BigDecimal("6.0"), true, K_REQUIRED),
                new CriticalRule("CRIT-K-LOW", v, "K", "SERUM", "mmol/L", CriticalLevel.LOW,
                        new BigDecimal("2.5"), false, K_REQUIRED)));
    }

    /** 指定规范码的全部规则；无规则返回空列表。 */
    public List<CriticalRule> rulesFor(String canonicalCode) {
        if (canonicalCode == null) {
            return List.of();
        }
        return rules.stream().filter(r -> r.canonicalCode().equals(canonicalCode)).toList();
    }

    public List<CriticalRule> all() {
        return rules;
    }
}
