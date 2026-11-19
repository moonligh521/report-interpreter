package com.moonligh.report.domain.parse;

import java.math.BigDecimal;
import java.time.Instant;

import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;

/**
 * 解析出的单个指标。raw* 为原文抄录，normalized* 为规范化结果，二者互不覆盖。
 * subReportId / reportTime 为体检册等可选归属。
 */
public record ParsedIndicator(
        String rawName,
        String rawValue,
        String rawUnit,
        String canonicalCode,
        String specimenType,
        BigDecimal normalizedValue,
        String normalizedUnit,
        String normalizationStatus,
        BigDecimal refLow,
        BigDecimal refHigh,
        FieldConfidence confidence,
        SourceEvidence evidence,
        String subReportId,
        Instant reportTime) {

    /** LAB_PANEL 等无子报告归属时的便捷构造。 */
    public ParsedIndicator(
            String rawName,
            String rawValue,
            String rawUnit,
            String canonicalCode,
            String specimenType,
            BigDecimal normalizedValue,
            String normalizedUnit,
            String normalizationStatus,
            BigDecimal refLow,
            BigDecimal refHigh,
            FieldConfidence confidence,
            SourceEvidence evidence) {
        this(
                rawName, rawValue, rawUnit, canonicalCode, specimenType, normalizedValue, normalizedUnit,
                normalizationStatus, refLow, refHigh, confidence, evidence, null, null);
    }
}
