package com.moonligh.report.api.res;

import java.time.Instant;

/** 体检册子报告。 */
public record SubReportDto(String subReportId, String title, Instant reportTime, Integer pageIndex) {
}
