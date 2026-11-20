package com.moonligh.report.domain.rule;

import java.math.BigDecimal;

/**
 * 版本化分层参考区间（可含年龄/性别条件；null 表示不限制）。
 */
public record ReferenceRangeRule(
        String ruleId,
        String version,
        String canonicalCode,
        String specimenType,
        String unit,
        BigDecimal low,
        BigDecimal high,
        Integer minAgeInclusive,
        Integer maxAgeExclusive,
        String sex) {

    public ReferenceRangeRule {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        if (canonicalCode == null || canonicalCode.isBlank()) {
            throw new IllegalArgumentException("canonicalCode is required");
        }
        if (specimenType == null || specimenType.isBlank()) {
            throw new IllegalArgumentException("specimenType is required");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit is required");
        }
        if (low == null || high == null || low.compareTo(high) >= 0) {
            throw new IllegalArgumentException("low < high required");
        }
        sex = sex == null || sex.isBlank() ? null : sex.trim();
    }

    /** 人群条件是否匹配；缺年龄/性别且规则要求时返回 false。 */
    public boolean matchesDemographics(Integer age, String patientSex) {
        if (minAgeInclusive != null || maxAgeExclusive != null) {
            if (age == null) {
                return false;
            }
            if (minAgeInclusive != null && age < minAgeInclusive) {
                return false;
            }
            if (maxAgeExclusive != null && age >= maxAgeExclusive) {
                return false;
            }
        }
        if (sex != null) {
            if (patientSex == null || patientSex.isBlank()) {
                return false;
            }
            return sex.equalsIgnoreCase(patientSex.trim());
        }
        return true;
    }
}
