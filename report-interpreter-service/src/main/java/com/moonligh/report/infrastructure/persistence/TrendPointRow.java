package com.moonligh.report.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;

/** 趋势查询行（Mapper 强类型结果）。 */
public record TrendPointRow(
        String reportId,
        int parseVersion,
        BigDecimal normalizedValue,
        String normalizedUnit,
        String specimenType,
        Instant specimenTime) {
}
