package com.moonligh.report.infrastructure.persistence;

/**
 * report_parse_result 行映射。structureJson 存子报告/段落/所见/结论骨架。
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
        String structureJson) {
}
