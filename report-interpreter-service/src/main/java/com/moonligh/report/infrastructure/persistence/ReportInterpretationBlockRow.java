package com.moonligh.report.infrastructure.persistence;

/**
 * report_interpretation_block 行：仅保存通过 Guard 的块。evidenceIds 以英文逗号分隔。
 */
public record ReportInterpretationBlockRow(
        String id,
        String interpretationId,
        int seq,
        String blockId,
        String claimType,
        String content,
        String evidenceIds,
        String validationStatus) {
}
