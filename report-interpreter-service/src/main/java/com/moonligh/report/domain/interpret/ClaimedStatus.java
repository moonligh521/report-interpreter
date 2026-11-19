package com.moonligh.report.domain.interpret;

/**
 * 解读块对某指标状态的结构化声明，由 Validator 与 Java 规则结论逐项比对。
 */
public enum ClaimedStatus {
    NORMAL,
    HIGH,
    LOW,
    CRITICAL,
    /** 不对指标状态下结论。 */
    NONE
}
