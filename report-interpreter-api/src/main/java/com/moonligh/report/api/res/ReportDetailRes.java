package com.moonligh.report.api.res;

/**
 * 报告详情最小集（foundation 占位，后续分支扩展字段）。
 */
public record ReportDetailRes(
        String reportId,
        String reportType,
        String status,
        int parseVersion,
        boolean activeParse) {
}
