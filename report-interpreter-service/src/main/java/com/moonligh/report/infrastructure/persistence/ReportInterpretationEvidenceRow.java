package com.moonligh.report.infrastructure.persistence;

/**
 * report_interpretation_evidence 行：解读块实际引用的 KB 依据快照。
 */
public record ReportInterpretationEvidenceRow(
        String id,
        String interpretationId,
        String evidenceId,
        String docId,
        String title,
        String snippet,
        String url) {
}
