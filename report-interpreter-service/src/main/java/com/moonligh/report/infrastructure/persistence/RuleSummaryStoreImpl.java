package com.moonligh.report.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.rule.RuleSummaryStore;

@Component
public class RuleSummaryStoreImpl implements RuleSummaryStore {

    private final ReportRuleSummaryMapper mapper;

    public RuleSummaryStoreImpl(ReportRuleSummaryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ReportRuleSummaryRow row) {
        mapper.insert(row);
    }

    @Override
    public Optional<ReportRuleSummaryRow> find(String reportId, int parseVersion) {
        return Optional.ofNullable(mapper.find(reportId, parseVersion));
    }
}