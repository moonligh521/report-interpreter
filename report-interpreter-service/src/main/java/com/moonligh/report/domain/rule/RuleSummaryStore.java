package com.moonligh.report.domain.rule;

import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportRuleSummaryRow;

/**
 * 报告级规则结论仓储端口。
 */
public interface RuleSummaryStore {

    void insert(ReportRuleSummaryRow row);

    Optional<ReportRuleSummaryRow> find(String reportId, int parseVersion);
}