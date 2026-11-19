package com.moonligh.report.domain.kb;

import java.util.List;

/**
 * 召回结果：结果为空时 emptyReason 必非 NONE。
 */
public record RecallResult(
        List<KnowledgeChunk> chunks, RecallEmptyReason emptyReason, int recalledCount, String filterExpr) {

    public static RecallResult empty(RecallEmptyReason reason, String filterExpr) {
        return new RecallResult(List.of(), reason, 0, filterExpr);
    }
}
