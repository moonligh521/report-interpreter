package com.moonligh.report.domain.parse;

import java.math.BigDecimal;

import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;

/**
 * 解析出的单个指标。raw* 为原文抄录，normalized* 为规范化结果，二者互不覆盖。
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
        SourceEvidence evidence) {
}