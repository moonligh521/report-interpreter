package com.moonligh.report.domain.critical;

import java.time.Instant;

/**
 * 一次危急检出（强类型入参）。valueText 为规范化数值的字符串形式，仅用于构造「同一危急事实」幂等键。
 */
public record CriticalDetection(
        String reportId,
        int parseVersion,
        String canonicalCode,
        String criticalLevel,
        String valueText,
        Instant detectionTime) {
}