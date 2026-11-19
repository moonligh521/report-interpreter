package com.moonligh.report.domain.parse;

import java.util.List;

/**
 * 报告级元信息（解析器产出，强类型）。
 * patientName 为报告自带身份（仅用于一致性比对，不落库、不进日志）；
 * totalPages 为声明的总页数，未知为 null；failedPages / unrecognizedRegions 为解析器上报的失败页与未识别区。
 */
public record ReportMeta(
        String patientName,
        Integer totalPages,
        List<Integer> failedPages,
        List<String> unrecognizedRegions) {

    public static final ReportMeta EMPTY = new ReportMeta(null, null, List.of(), List.of());

    public ReportMeta {
        failedPages = failedPages == null ? List.of() : List.copyOf(failedPages);
        unrecognizedRegions = unrecognizedRegions == null ? List.of() : List.copyOf(unrecognizedRegions);
    }
}
