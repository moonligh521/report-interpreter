package com.moonligh.report.domain.parse;

import java.util.Optional;

import com.moonligh.report.infrastructure.persistence.ReportParseManifestRow;

/**
 * DocumentParseManifest 持久化端口。
 */
public interface ParseManifestStore {

    void insert(ReportParseManifestRow row);

    Optional<ReportParseManifestRow> find(String reportId, int parseVersion);
}
