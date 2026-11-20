package com.moonligh.report.domain.parse;

import java.time.Instant;
import java.util.List;

/**
 * 解析可追溯清单：原始文件 → 抽取 → 结构化 → 门禁摘要。产物 ref 本分支可空。
 */
public record DocumentParseManifest(
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
        DocumentQualityStatus qualityStatus,
        List<QualityIssue> qualityIssues,
        String extractedArtifactRef,
        String structuredArtifactRef,
        ProbeDecision probeDecision,
        List<String> probeReasons,
        String failureStage,
        String detailCode,
        Instant createdAt) {

    public DocumentParseManifest {
        qualityIssues = qualityIssues == null ? List.of() : List.copyOf(qualityIssues);
        probeReasons = probeReasons == null ? List.of() : List.copyOf(probeReasons);
        extractedArtifactRef = extractedArtifactRef == null ? "" : extractedArtifactRef;
        structuredArtifactRef = structuredArtifactRef == null ? "" : structuredArtifactRef;
        failureStage = failureStage == null ? "" : failureStage;
        detailCode = detailCode == null ? "" : detailCode;
        if (documentHash == null || documentHash.isBlank()) {
            throw new IllegalArgumentException("documentHash is required");
        }
        if (qualityStatus == null) {
            throw new IllegalArgumentException("qualityStatus is required");
        }
    }
}
