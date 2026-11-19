package com.moonligh.report.domain.rule;

/** 规则必要条件（R-R10）。缺失即适用性 UNKNOWN。 */
public enum RuleCondition {
    CANONICAL,
    VALUE,
    UNIT,
    SPECIMEN_TYPE,
    /** value/unit/name/normalization 字段置信度达标（不含 refRange）。 */
    FIELD_CONFIDENCE,
    AGE,
    SEX,
    SPECIMEN_TIME
}
