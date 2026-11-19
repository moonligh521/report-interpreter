package com.moonligh.report.domain.parse;

import java.time.Instant;

/** 解析出的子报告。 */
public record ParsedSubReport(String subReportId, String title, Instant reportTime, Integer pageIndex) {
}
