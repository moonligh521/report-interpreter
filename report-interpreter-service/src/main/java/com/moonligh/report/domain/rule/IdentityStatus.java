package com.moonligh.report.domain.rule;

/** 报告身份与期望就诊人的一致性状态。 */
public enum IdentityStatus {
    MATCHED,
    MISSING,
    CONFLICT
}
