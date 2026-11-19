package com.moonligh.report.api.res;

import java.math.BigDecimal;
import java.time.Instant;

/** 趋势序列中的单点（已规范化数值）。 */
public record TrendPoint(
        String reportId,
        Instant specimenTime,
        BigDecimal normalizedValue,
        String normalizedUnit,
        int parseVersion) {
}
