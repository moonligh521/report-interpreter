package com.moonligh.report.domain.rule;

import java.util.Objects;

import com.moonligh.report.api.res.CriticalCoverageEvidence;

/**
 * 危急覆盖结果。COMPLETE 必须带齐 evidence：至少处理过一页，且无失败页、无未归属标记、无未识别区；
 * 否则构造即抛 IllegalArgumentException（COMPLETE 无 evidence 非法）。
 */
public record CriticalCoverageResult(CoverageStatus status, CriticalCoverageEvidence evidence) {

    public CriticalCoverageResult {
        Objects.requireNonNull(status, "status");
        if (status == CoverageStatus.COMPLETE && !supportsComplete(evidence)) {
            throw new IllegalArgumentException("COMPLETE 必须附带完整 evidence");
        }
        evidence = evidence == null ? CriticalCoverageEvidence.EMPTY : evidence;
    }

    /** evidence 是否足以支撑 COMPLETE。 */
    static boolean supportsComplete(CriticalCoverageEvidence e) {
        return e != null
                && !e.pagesProcessed().isEmpty()
                && e.failedPages().isEmpty()
                && e.unmappedFlags().isEmpty()
                && e.unrecognizedRegions().isEmpty();
    }
}
