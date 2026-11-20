package com.moonligh.report.domain.parse;

import java.util.List;

/**
 * 文本层探针可审计输出。禁 Map；reasons 为简要说明列表。
 */
public record ProbeReport(
        ProbeDecision decision,
        int totalPages,
        int nonEmptyPageCount,
        double nonEmptyPageRatio,
        double extractableCharRatio,
        double averageCharsPerPage,
        int totalChars,
        boolean nonEmptyPageRatioOk,
        boolean extractableCharRatioOk,
        boolean textDensityOk,
        boolean totalCharsOk,
        List<String> reasons) {

    public ProbeReport {
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
