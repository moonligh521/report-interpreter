package com.moonligh.report.infrastructure.persistence;

/**
 * report_parse_result 行映射。
 */
public record ReportParseResultRow(
        String id,
        String reportId,
        int parseVersion,
        boolean active,
        String reportType,
        double parseConfidence,
        String status,
        String requestKey) {
}