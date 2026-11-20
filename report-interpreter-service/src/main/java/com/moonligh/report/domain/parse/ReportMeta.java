package com.moonligh.report.domain.parse;

import java.time.Instant;
import java.util.List;

/**
 * 报告级元信息（解析器产出，强类型）。
 * patientName 为报告自带身份（仅用于一致性比对，不落库、不进日志）；
 * totalPages 为声明的总页数，未知为 null；
 * failedPages / unrecognizedRegions 供 CriticalCoverage 证据（文本夹具沿用）；
 * parseFailedPages / lowConfidencePages 供解析质量门禁（OCR 页状态）；
 * specimenTime 为趋势排序用的标本/报告时间（可空）。
 */
public record ReportMeta(
        String patientName,
        Integer totalPages,
        List<Integer> failedPages,
        List<String> unrecognizedRegions,
        Instant specimenTime,
        List<Integer> lowConfidencePages,
        List<Integer> parseFailedPages) {

    public static final ReportMeta EMPTY =
            new ReportMeta(null, null, List.of(), List.of(), null, List.of(), List.of());

    public ReportMeta(
            String patientName,
            Integer totalPages,
            List<Integer> failedPages,
            List<String> unrecognizedRegions,
            Instant specimenTime) {
        this(patientName, totalPages, failedPages, unrecognizedRegions, specimenTime, List.of(), List.of());
    }

    public ReportMeta(
            String patientName,
            Integer totalPages,
            List<Integer> failedPages,
            List<String> unrecognizedRegions,
            Instant specimenTime,
            List<Integer> lowConfidencePages) {
        this(patientName, totalPages, failedPages, unrecognizedRegions, specimenTime, lowConfidencePages, List.of());
    }

    public ReportMeta {
        failedPages = failedPages == null ? List.of() : List.copyOf(failedPages);
        unrecognizedRegions = unrecognizedRegions == null ? List.of() : List.copyOf(unrecognizedRegions);
        lowConfidencePages = lowConfidencePages == null ? List.of() : List.copyOf(lowConfidencePages);
        parseFailedPages = parseFailedPages == null ? List.of() : List.copyOf(parseFailedPages);
    }
}
