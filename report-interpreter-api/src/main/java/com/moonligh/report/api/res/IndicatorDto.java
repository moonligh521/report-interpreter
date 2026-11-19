package com.moonligh.report.api.res;

import java.math.BigDecimal;

/**
 * 指标：raw 与 normalized 双轨，normalized 不得覆盖 raw。
 */
public record IndicatorDto(
        String indicatorId,
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