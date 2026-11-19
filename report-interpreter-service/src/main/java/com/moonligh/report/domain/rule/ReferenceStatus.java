package com.moonligh.report.domain.rule;

/** 参考区间判定：LOW/HIGH 为超区间（≠ 危急），UNKNOWN 为无可用区间。 */
public enum ReferenceStatus {
    LOW,
    NORMAL,
    HIGH,
    UNKNOWN
}
