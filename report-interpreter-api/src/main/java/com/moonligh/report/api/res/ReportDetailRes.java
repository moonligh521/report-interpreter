package com.moonligh.report.api.res;

import java.util.List;

/**
 * 报告详情：当前 active 解析版本及其指标。
 */
public record ReportDetailRes(
        String reportId,
        String reportType,
        String status,
        int parseVersion,
        boolean activeParse,
        double parseConfidence,
        List<IndicatorDto> indicators) {
}