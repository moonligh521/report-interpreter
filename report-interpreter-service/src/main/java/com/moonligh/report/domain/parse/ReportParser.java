package com.moonligh.report.domain.parse;

/**
 * 报告解析 SPI。每种报告类型至多一个实现；禁止无条件多引擎串行。
 */
public interface ReportParser {

    boolean supports(ParseContext context);

    ParseResult parse(ParseContext context);
}