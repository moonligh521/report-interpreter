package com.moonligh.report.api.res;

import java.util.List;

/**
 * 危急覆盖证据（类型化，非 Map）。
 * pagesProcessed 已处理页；failedPages 处理失败页；unmappedFlags 无法归属/规范化的指标名；
 * unrecognizedRegions 未识别的文本区。
 */
public record CriticalCoverageEvidence(
        List<Integer> pagesProcessed,
        List<Integer> failedPages,
        List<String> unmappedFlags,
        List<String> unrecognizedRegions) {

    public static final CriticalCoverageEvidence EMPTY =
            new CriticalCoverageEvidence(List.of(), List.of(), List.of(), List.of());

    public CriticalCoverageEvidence {
        pagesProcessed = pagesProcessed == null ? List.of() : List.copyOf(pagesProcessed);
        failedPages = failedPages == null ? List.of() : List.copyOf(failedPages);
        unmappedFlags = unmappedFlags == null ? List.of() : List.copyOf(unmappedFlags);
        unrecognizedRegions = unrecognizedRegions == null ? List.of() : List.copyOf(unrecognizedRegions);
    }
}