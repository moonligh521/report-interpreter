package com.moonligh.report.domain.parse;

import java.util.List;

/**
 * 解析结果。parseConfidence 为整体置信度（各指标分层置信度的最小值聚合）。
 */
public record ParseResult(ReportType reportType, List<ParsedIndicator> indicators, double parseConfidence) {
}