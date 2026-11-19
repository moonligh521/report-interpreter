package com.moonligh.report.domain.rule;

/**
 * 危急值结果（≠ ReferenceRangeResult ≠ 解读）。level 仅在 CRITICAL 时非空。
 */
public record CriticalValueResult(
        CriticalStatus status, CriticalLevel level, String ruleId, String ruleVersion) {

    /** 持久化与对外展示用：CRITICAL_HIGH / CRITICAL_LOW / NOT_CRITICAL / UNKNOWN / NO_RULE。 */
    public String code() {
        return status == CriticalStatus.CRITICAL ? "CRITICAL_" + level.name() : status.name();
    }
}
