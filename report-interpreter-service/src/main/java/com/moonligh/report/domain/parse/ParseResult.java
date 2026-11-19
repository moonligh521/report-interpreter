package com.moonligh.report.domain.parse;

import java.util.List;

/**
 * 解析结果。parseConfidence 为整体置信度；meta 为报告级元信息；structure 为多类型结构。
 */
public record ParseResult(
        ReportType reportType,
        List<ParsedIndicator> indicators,
        double parseConfidence,
        ReportMeta meta,
        ParsedStructure structure) {

    public ParseResult(ReportType reportType, List<ParsedIndicator> indicators, double parseConfidence) {
        this(reportType, indicators, parseConfidence, ReportMeta.EMPTY, ParsedStructure.EMPTY);
    }

    public ParseResult(
            ReportType reportType, List<ParsedIndicator> indicators, double parseConfidence, ReportMeta meta) {
        this(reportType, indicators, parseConfidence, meta, ParsedStructure.EMPTY);
    }

    public ParseResult {
        meta = meta == null ? ReportMeta.EMPTY : meta;
        structure = structure == null ? ParsedStructure.EMPTY : structure;
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
    }
}
