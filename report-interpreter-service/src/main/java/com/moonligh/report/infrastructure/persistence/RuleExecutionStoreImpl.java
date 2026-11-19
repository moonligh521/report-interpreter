package com.moonligh.report.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.rule.RuleExecutionStore;

@Component
public class RuleExecutionStoreImpl implements RuleExecutionStore {

    private final ReportRuleExecutionMapper mapper;

    public RuleExecutionStoreImpl(ReportRuleExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ReportRuleExecutionRow row) {
        mapper.insert(row);
    }

    @Override
    public List<ReportRuleExecutionRow> listByReport(String reportId) {
        return mapper.listByReport(reportId);
    }
}