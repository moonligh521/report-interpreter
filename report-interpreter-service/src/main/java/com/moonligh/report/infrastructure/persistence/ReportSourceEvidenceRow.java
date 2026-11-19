package com.moonligh.report.infrastructure.persistence;

/**
 * report_source_evidence 行映射。
 */
public record ReportSourceEvidenceRow(
        String id, String indicatorId, int pageIndex, String rawText, String regionJson) {
}