package com.moonligh.report.api.res;

import java.util.List;

/**
 * 报告的危急事件列表（独立于解读生命周期）。
 */
public record CriticalEventsRes(String reportId, List<CriticalHitRes> events) {
}