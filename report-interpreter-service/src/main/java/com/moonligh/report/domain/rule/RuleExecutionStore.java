package com.moonligh.report.domain.rule;

import java.util.List;

import com.moonligh.report.infrastructure.persistence.ReportRuleExecutionRow;

/**
 * 规则执行快照仓储端口。
 */
public interface RuleExecutionStore {

    void insert(ReportRuleExecutionRow row);

    List<ReportRuleExecutionRow> listByReport(String reportId);
}