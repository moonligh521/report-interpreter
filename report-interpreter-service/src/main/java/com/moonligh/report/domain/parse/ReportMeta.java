package com.moonligh.report.domain.parse;

import java.time.Instant;
import java.util.List;

/**
 * 报告级元信息（解析器产出，强类型）。
 * patientName 为报告自带身份（仅用于一致性比对，不落库、不进日志）；
 * totalPages 为声明的总页数，未知为 null；failedPages / lowConfidencePages / unrecognizedRegions
 * 为解析器上报的失败页、低置信页与未识别区；specimenTime 为趋势排序用的标本/报告时间（可空）。
 */
public record ReportMeta(
        String patientName,
        Integer totalPages,
        List<Integer> failedPages,
        List<String> unrecognizedRegions,
        Instant specimenTime,
        List<Integer> lowConfidencePages) {

    public static final ReportMeta EMPTY = new ReportMeta(null, null, List.of(), List.of(), null, List.of());

    public ReportMeta(
            String patientName,
            Integer totalPages,
            List<Integer> failedPages,
            List<String> unrecognizedRegions,
            Instant specimenTime) {
        this(patientName, totalPages, failedPages, unrecognizedRegions, specimenTime, List.of());
    }

    public ReportMeta {
        failedPages = failedPages == null ? List.of() : List.copyOf(failedPages);
        unrecognizedRegions = unrecognizedRegions == null ? List.of() : List.copyOf(unrecognizedRegions);
        lowConfidencePages = lowConfidencePages == null ? List.of() : List.copyOf(lowConfidencePages);
    }
}
