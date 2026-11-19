package com.moonligh.report.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.report.api.res.BoundingBox;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.IndicatorDto;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.parse.ReportParseStore;
import com.moonligh.report.infrastructure.persistence.ReportIndicatorRow;

/**
 * 指标行 → IndicatorDto（含原文证据与置信度）。报告详情与档案 Outbox 消息共用，保证两处口径一致。
 */
@Component
public class IndicatorDtoAssembler {

    private final ReportParseStore parses;
    private final ObjectMapper json = new ObjectMapper();

    public IndicatorDtoAssembler(ReportParseStore parses) {
        this.parses = parses;
    }

    public List<IndicatorDto> forParse(String parseResultId) {
        return parses.listIndicators(parseResultId).stream().map(this::toDto).toList();
    }

    public IndicatorDto toDto(ReportIndicatorRow r) {
        FieldConfidence conf = new FieldConfidence(
                r.confOcr(), r.confStructure(), r.confExtraction(), r.confNormalization(),
                r.confName(), r.confValue(), r.confUnit(), r.confRefRange());
        SourceEvidence evidence = parses.findEvidence(r.id())
                .map(e -> new SourceEvidence(e.pageIndex(), e.rawText(), readRegion(e.regionJson())))
                .orElse(null);
        return new IndicatorDto(
                r.id(), r.rawName(), r.rawValue(), r.rawUnit(), r.canonicalCode(), r.specimenType(),
                stripScale(r.normalizedValue()), r.normalizedUnit(), r.normalizationStatus(),
                stripScale(r.refLow()), stripScale(r.refHigh()), conf, evidence,
                r.subReportId(), r.reportTime());
    }

    private BoundingBox readRegion(String regionJson) {
        if (!StringUtils.hasText(regionJson)) {
            return null;
        }
        try {
            return json.readValue(regionJson, BoundingBox.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** DECIMAL(20,6) 读回带尾零，对外去掉以保持数值干净（86.000000 → 86）。 */
    private static BigDecimal stripScale(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }
}
