package com.moonligh.report.api.res;

/**
 * 创建报告任务的响应。parseVersion=0 表示尚未产生解析结果。
 */
public record CreateReportRes(String reportId, String status, int parseVersion) {
}