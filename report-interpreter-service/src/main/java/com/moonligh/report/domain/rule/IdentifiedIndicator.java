package com.moonligh.report.domain.rule;

import com.moonligh.report.domain.parse.ParsedIndicator;

/**
 * 已分配落库 id 的解析指标，规则快照用 indicatorId 回溯到原始指标行。
 */
public record IdentifiedIndicator(String indicatorId, ParsedIndicator indicator) {
}