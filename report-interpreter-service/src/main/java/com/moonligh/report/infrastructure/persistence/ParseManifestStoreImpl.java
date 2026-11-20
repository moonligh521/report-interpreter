package com.moonligh.report.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.parse.ParseManifestStore;

@Component
public class ParseManifestStoreImpl implements ParseManifestStore {

    private final ReportParseManifestMapper mapper;

    public ParseManifestStoreImpl(ReportParseManifestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ReportParseManifestRow row) {
        mapper.insert(row);
    }

    @Override
    public Optional<ReportParseManifestRow> find(String reportId, int parseVersion) {
        return Optional.ofNullable(mapper.findByReportVersion(reportId, parseVersion));
    }
}
