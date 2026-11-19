package com.moonligh.report.domain.rule;

/**
 * 危急判定。NO_RULE 表示该指标没有内置危急规则（不等于安全）；UNKNOWN 表示适用性不足，待复核。
 * 只有适用且数值未触发阈值时才会是 NOT_CRITICAL。
 */
public enum CriticalStatus {
    CRITICAL,
    NOT_CRITICAL,
    UNKNOWN,
    NO_RULE
}
