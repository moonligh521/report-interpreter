package com.moonligh.report.api.res;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 指标：raw 与 normalized 双轨，normalized 不得覆盖 raw。
 * subReportId / reportTime 为体检册等多类型可选归属字段。
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
        SourceEvidence evidence,
        String subReportId,
        Instant reportTime) {
}
