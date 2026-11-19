package com.moonligh.report.domain.rule;

/** 规则适用性：APPLICABLE 可判定；UNKNOWN 必要条件缺失；NOT_REQUIRED 该指标没有危急规则。 */
public enum ApplicabilityStatus {
    APPLICABLE,
    UNKNOWN,
    NOT_REQUIRED
}
