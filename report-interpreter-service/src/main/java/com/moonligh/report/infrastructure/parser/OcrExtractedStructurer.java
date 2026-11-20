package com.moonligh.report.infrastructure.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.parse.ExtractedDocument;
import com.moonligh.report.domain.parse.ExtractedPage;
import com.moonligh.report.domain.parse.PageParseStatus;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ReportMeta;
import com.moonligh.report.domain.parse.ReportType;

/**
 * 将外置 ExtractedDocument 结构化为本仓 ParseResult（本版 LAB_PANEL）。
 */
@Component
public class OcrExtractedStructurer {

    public ParseResult structure(ReportType reportType, ExtractedDocument doc) {
        if (reportType != ReportType.LAB_PANEL) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "OCR 结构化本版仅支持 LAB_PANEL");
        }
        if (doc == null || doc.pages().isEmpty()) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "抽取结果为空");
        }
        List<ParsedIndicator> indicators = new ArrayList<>();
        List<Integer> failedPages = new ArrayList<>();
        List<Integer> lowConfidencePages = new ArrayList<>();
        String patientName = null;
        for (ExtractedPage page : doc.pages()) {
            if (page.status() == PageParseStatus.FAILED || page.status() == PageParseStatus.EMPTY) {
                failedPages.add(page.pageIndex());
            } else if (page.status() == PageParseStatus.LOW_CONFIDENCE
                    || page.status() == PageParseStatus.NEED_MANUAL_REVIEW) {
                lowConfidencePages.add(page.pageIndex());
            }
            for (String line : page.rawText().split("\\r?\\n")) {
                String trimmed = line.strip();
                if (trimmed.startsWith("PATIENT_NAME=")) {
                    patientName = trimmed.substring("PATIENT_NAME=".length()).strip();
                    continue;
                }
                if (!trimmed.contains("|") || trimmed.startsWith("REPORT_TYPE=") || trimmed.startsWith("STATUS=")) {
                    continue;
                }
                if (trimmed.startsWith("PAGE_COUNT=")) {
                    continue;
                }
                indicators.add(parseLine(trimmed, page.pageIndex(), doc.ocrConfidence(), doc.layoutConfidence()));
            }
        }
        ReportMeta meta = new ReportMeta(
                patientName, doc.totalPages(), failedPages, List.of(), null, lowConfidencePages);
        // 即使无指标也返回结构，交由质量门禁按页状态裁决（避免静默跳过坏页）。
        double overall = Math.min(doc.ocrConfidence(), doc.layoutConfidence());
        if (!indicators.isEmpty()) {
            overall = Math.min(overall, indicators.stream().mapToDouble(i -> i.confidence().min()).min().orElse(overall));
        }
        return new ParseResult(ReportType.LAB_PANEL, List.copyOf(indicators), overall, meta);
    }

    private ParsedIndicator parseLine(String line, int pageIndex, double ocr, double layout) {
        String[] f = line.split("\\|", -1);
        if (f.length < 3) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "OCR 指标行格式不合法");
        }
        String rawName = f[0].strip();
        String rawValue = f[1].strip();
        String rawUnit = f[2].strip();
        BigDecimal refLow = f.length > 3 ? LabNormalizer.parseNumber(f[3]) : null;
        BigDecimal refHigh = f.length > 4 ? LabNormalizer.parseNumber(f[4]) : null;
        String rawText = f.length > 5 ? f[5].strip() : line;
        Integer page = f.length > 6 ? parsePage(f[6]) : pageIndex;
        String specimenOverride = f.length > 7 && !f[7].isBlank() ? f[7].strip() : null;
        LabNormalizer.Outcome n = LabNormalizer.normalize(rawName, rawValue, rawUnit);
        String specimen = specimenOverride != null ? specimenOverride : n.specimenType();
        FieldConfidence conf = new FieldConfidence(
                ocr, layout, 0.90,
                switch (n.status()) {
                    case LabNormalizer.NORMALIZED -> 0.95;
                    case LabNormalizer.CONVERTED -> 0.88;
                    default -> 0.30;
                },
                n.canonicalCode() != null ? 0.95 : 0.55,
                n.status().equals(LabNormalizer.VALUE_NOT_NUMERIC) ? 0.45 : 0.95,
                rawUnit.isBlank() ? 0.45 : 0.95,
                refLow != null && refHigh != null ? 0.95 : 0.45);
        return new ParsedIndicator(
                rawName, rawValue, rawUnit, n.canonicalCode(), specimen,
                n.value(), n.unit(), n.status(), refLow, refHigh, conf,
                new SourceEvidence(page, rawText, null));
    }

    private static Integer parsePage(String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
