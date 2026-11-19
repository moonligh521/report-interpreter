package com.moonligh.report.domain.rule;

/**
 * 规则可用的人群事实（来自 clinical-record 只读快照；当前分支尚未接入，使用 UNKNOWN）。
 */
public record PatientFacts(Integer age, String sex, String pregnancyStatus) {

    public static final PatientFacts UNKNOWN = new PatientFacts(null, null, null);
}
