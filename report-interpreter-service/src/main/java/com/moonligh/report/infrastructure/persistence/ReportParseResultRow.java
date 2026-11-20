package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_parse_result 行映射。structureJson 存子报告/段落/所见/结论骨架；
 * specimenTime 供趋势排序（可空）。仅保留单一规范构造，供 MyBatis 自动映射。
 */
public record ReportParseResultRow(
        String id,
        String reportId,
        int parseVersion,
        boolean active,
        String reportType,
        double parseConfidence,
        String status,
        String requestKey,
        String structureJson,
        Instant specimenTime) {
}
