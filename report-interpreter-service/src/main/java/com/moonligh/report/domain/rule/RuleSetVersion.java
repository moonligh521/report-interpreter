package com.moonligh.report.domain.rule;

/**
 * 规则集版本。内置表升级时递增版本；历史判定保留当时的 ruleVersion，不因新规改写。
 */
public final class RuleSetVersion {

    /** 参考区间与危急规则共用的当前规则集版本。 */
    public static final String CURRENT = "rules-v1.0.0";

    private RuleSetVersion() {
    }
}
