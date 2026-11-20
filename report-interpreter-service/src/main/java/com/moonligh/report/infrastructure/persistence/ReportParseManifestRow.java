package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;

/**
 * report_parse_manifest 行映射。
 */
public record ReportParseManifestRow(
        String id,
        String reportId,
        String documentHash,
        int parseVersion,
        String parserType,
        String parserVersion,
        String extractionEngine,
        String extractionEngineVersion,
        String extractionSchemaVersion,
        int totalPageCount,
        int successfulPageCount,
        int failedPageCount,
        String qualityStatus,
        String qualityIssuesJson,
        String extractedArtifactRef,
        String structuredArtifactRef,
        String probeDecision,
        String probeReasonsJson,
        String failureStage,
        String detailCode,
        Instant createdAt) {
}
