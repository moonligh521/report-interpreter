package com.moonligh.report.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * report_indicator 行映射：raw_* 与 normalized_* 分列存储。
 */
public record ReportIndicatorRow(
        String id,
        String parseResultId,
        String reportId,
        int parseVersion,
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
        double confOcr,
        double confStructure,
        double confExtraction,
        double confNormalization,
        double confName,
        double confValue,
        double confUnit,
        double confRefRange,
        String subReportId,
        Instant reportTime) {
}
