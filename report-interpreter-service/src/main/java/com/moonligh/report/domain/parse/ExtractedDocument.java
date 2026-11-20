package com.moonligh.report.domain.parse;

import java.util.List;

/**
 * 版本化文档抽取契约（外置 OCR/版面引擎 → 本仓）。禁 Map；schemaVersion 必填。
 */
public record ExtractedDocument(
        int totalPages,
        List<ExtractedPage> pages,
        double ocrConfidence,
        double layoutConfidence,
        String schemaVersion,
        String engineVersion) {

    public static final String SCHEMA_VERSION = "1.0";

    public ExtractedDocument {
        pages = pages == null ? List.of() : List.copyOf(pages);
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion is required");
        }
        engineVersion = engineVersion == null ? "" : engineVersion;
    }
}
