package com.moonligh.report.domain.rule;

/**
 * 报告级安全结论。
 * archiveBlocked：默认禁止自动归档；safeNormalAllowed：是否允许对外表述为「安全正常」；
 * blockCode：4106/4108/4109/4110，无阻断为 null。
 */
public record SafetyVerdict(
        RuleOutcomeStatus status, boolean archiveBlocked, boolean safeNormalAllowed, Integer blockCode) {
}
