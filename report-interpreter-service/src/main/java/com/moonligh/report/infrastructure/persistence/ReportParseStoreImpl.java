package com.moonligh.report.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.parse.ReportParseStore;

@Component
public class ReportParseStoreImpl implements ReportParseStore {

    private final ReportParseResultMapper results;
    private final ReportIndicatorMapper indicators;
    private final ReportSourceEvidenceMapper evidences;

    public ReportParseStoreImpl(
            ReportParseResultMapper results,
            ReportIndicatorMapper indicators,
            ReportSourceEvidenceMapper evidences) {
        this.results = results;
        this.indicators = indicators;
        this.evidences = evidences;
    }

    @Override
    public int maxVersion(String reportId) {
        return results.maxVersion(reportId);
    }

    @Override
    public void deactivatePrevious(String reportId) {
        results.deactivatePrevious(reportId);
    }

    @Override
    public void insertParseResult(ReportParseResultRow row) {
        results.insert(row);
    }

    @Override
    public Optional<ReportParseResultRow> findActive(String reportId) {
        return Optional.ofNullable(results.findActive(reportId));
    }

    @Override
    public Optional<ReportParseResultRow> findByRequestKey(String reportId, String requestKey) {
        return Optional.ofNullable(results.findByRequestKey(reportId, requestKey));
    }

    @Override
    public void insertIndicators(List<ReportIndicatorRow> rows) {
        if (!rows.isEmpty()) {
            indicators.batchInsert(rows);
        }
    }

    @Override
    public List<ReportIndicatorRow> listIndicators(String parseResultId) {
        return indicators.listByParseResult(parseResultId);
    }

    @Override
    public void insertEvidence(ReportSourceEvidenceRow row) {
        evidences.insert(row);
    }

    @Override
    public Optional<ReportSourceEvidenceRow> findEvidence(String indicatorId) {
        return Optional.ofNullable(evidences.findByIndicator(indicatorId));
    }
}