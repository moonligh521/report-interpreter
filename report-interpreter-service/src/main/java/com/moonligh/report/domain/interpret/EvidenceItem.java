package com.moonligh.report.domain.interpret;

/**
 * 本次解读允许引用的知识库依据（来自 HybridRecall，且仅 PUBLISHED）。
 */
public record EvidenceItem(
        String evidenceId,
        String docId,
        String title,
        String snippet,
        String url,
        String indicatorCode) {
}
