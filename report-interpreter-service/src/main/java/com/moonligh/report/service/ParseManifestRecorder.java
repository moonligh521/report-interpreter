package com.moonligh.report.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.report.domain.parse.DocumentQualityStatus;
import com.moonligh.report.domain.parse.ParseManifestStore;
import com.moonligh.report.domain.parse.ProbeDecision;
import com.moonligh.report.domain.parse.QualityIssue;
import com.moonligh.report.infrastructure.persistence.ReportParseManifestRow;

/**
 * Manifest 写入；失败路径用新事务，避免随 4102 回滚丢失审计摘要。
 */
@Service
public class ParseManifestRecorder {

    private final ParseManifestStore store;
    private final ObjectMapper json = new ObjectMapper();

    public ParseManifestRecorder(ParseManifestStore store) {
        this.store = store;
    }

    public void record(ReportParseManifestRow row) {
        store.insert(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureNewTx(ReportParseManifestRow row) {
        store.insert(row);
    }

    public ReportParseManifestRow row(
            String reportId,
            String documentHash,
            int parseVersion,
            String parserType,
            String parserVersion,
            String extractionEngine,
            String extractionEngineVersion,
            String extractionSchemaVersion,
            int totalPages,
            int successfulPages,
            int failedPages,
            DocumentQualityStatus qualityStatus,
            List<QualityIssue> issues,
            ProbeDecision probeDecision,
            List<String> probeReasons,
            String failureStage,
            String detailCode) {
        return new ReportParseManifestRow(
                "mnf_" + UUID.randomUUID().toString().replace("-", ""),
                reportId,
                documentHash,
                parseVersion,
                parserType,
                parserVersion,
                nullToEmpty(extractionEngine),
                nullToEmpty(extractionEngineVersion),
                nullToEmpty(extractionSchemaVersion),
                totalPages,
                successfulPages,
                failedPages,
                qualityStatus.name(),
                writeJson(issues),
                "",
                "",
                probeDecision == null ? "" : probeDecision.name(),
                writeJson(probeReasons == null ? List.of() : probeReasons),
                nullToEmpty(failureStage),
                nullToEmpty(detailCode),
                null);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
