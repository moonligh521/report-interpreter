package com.moonligh.report.domain.parse;

import java.util.List;
import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportIndicatorRow;
import com.moonligh.report.infrastructure.persistence.ReportParseResultRow;
import com.moonligh.report.infrastructure.persistence.ReportSourceEvidenceRow;

/**
 * 解析结果仓储端口。
 */
public interface ReportParseStore {

    int maxVersion(String reportId);

    void deactivatePrevious(String reportId);

    void insertParseResult(ReportParseResultRow row);

    Optional<ReportParseResultRow> findActive(String reportId);

    Optional<ReportParseResultRow> findByRequestKey(String reportId, String requestKey);

    void insertIndicators(List<ReportIndicatorRow> rows);

    List<ReportIndicatorRow> listIndicators(String parseResultId);

    void insertEvidence(ReportSourceEvidenceRow row);

    Optional<ReportSourceEvidenceRow> findEvidence(String indicatorId);
}