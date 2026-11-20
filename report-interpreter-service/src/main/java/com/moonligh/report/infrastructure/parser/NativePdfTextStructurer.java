package com.moonligh.report.infrastructure.parser;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ReportMeta;
import com.moonligh.report.domain.parse.ReportType;

/**
 * 将 Native PDF 提取的纯文本结构化为 LAB_PANEL 指标（夹具兼容 pipe 行）。
 */
@Component
public class NativePdfTextStructurer {

    private static final String TYPE_PREFIX = "REPORT_TYPE=";

    public ParseResult structure(ReportType reportType, String text) {
        if (reportType != ReportType.LAB_PANEL) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "NativePdf 本版仅支持 LAB_PANEL");
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "PDF 文本层为空");
        }
        List<ParsedIndicator> indicators = new ArrayList<>();
        String patientName = null;
        Integer totalPages = null;
        boolean headerSeen = false;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                if (!trimmed.startsWith(TYPE_PREFIX)) {
                    // 允许页首噪声，继续找头。
                    continue;
                }
                String declared = trimmed.substring(TYPE_PREFIX.length()).strip();
                if (!ReportType.LAB_PANEL.name().equals(declared)) {
                    throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "夹具类型与声明不符");
                }
                headerSeen = true;
                continue;
            }
            if (trimmed.startsWith("PATIENT_NAME=")) {
                patientName = trimmed.substring("PATIENT_NAME=".length()).strip();
                continue;
            }
            if (trimmed.startsWith("PAGE_COUNT=")) {
                try {
                    totalPages = Integer.valueOf(trimmed.substring("PAGE_COUNT=".length()).strip());
                } catch (NumberFormatException ignored) {
                    totalPages = null;
                }
                continue;
            }
            if (trimmed.contains("|")) {
                indicators.add(parseLine(trimmed));
            }
        }
        if (!headerSeen) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        if (indicators.isEmpty()) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "未解析出任何指标");
        }
        double overall = indicators.stream().mapToDouble(i -> i.confidence().min()).min().orElse(0.0);
        ReportMeta meta = new ReportMeta(patientName, totalPages, List.of(), List.of(), null);
        return new ParseResult(ReportType.LAB_PANEL, List.copyOf(indicators), overall, meta);
    }

    /** 从 UTF-8 字节构造（测试辅助）。 */
    public ParseResult structureBytes(ReportType reportType, byte[] utf8) {
        return structure(reportType, new String(utf8, StandardCharsets.UTF_8));
    }

    private ParsedIndicator parseLine(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length < 3) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "指标行格式不合法");
        }
        String rawName = f[0].strip();
        String rawValue = f[1].strip();
        String rawUnit = f[2].strip();
        BigDecimal refLow = f.length > 3 ? LabNormalizer.parseNumber(f[3]) : null;
        BigDecimal refHigh = f.length > 4 ? LabNormalizer.parseNumber(f[4]) : null;
        String rawText = f.length > 5 ? f[5].strip() : line;
        Integer pageIndex = f.length > 6 ? parsePage(f[6]) : 0;
        String specimenOverride = f.length > 7 && !f[7].isBlank() ? f[7].strip() : null;
        LabNormalizer.Outcome n = LabNormalizer.normalize(rawName, rawValue, rawUnit);
        String specimen = specimenOverride != null ? specimenOverride : n.specimenType();
        FieldConfidence conf = new FieldConfidence(
                1.0, 0.95, 0.95,
                switch (n.status()) {
                    case LabNormalizer.NORMALIZED -> 0.98;
                    case LabNormalizer.CONVERTED -> 0.90;
                    default -> 0.30;
                },
                n.canonicalCode() != null ? 0.98 : 0.60,
                n.status().equals(LabNormalizer.VALUE_NOT_NUMERIC) ? 0.50 : 0.98,
                rawUnit.isBlank() ? 0.50 : 0.98,
                refLow != null && refHigh != null ? 0.98 : 0.50);
        return new ParsedIndicator(
                rawName, rawValue, rawUnit, n.canonicalCode(), specimen,
                n.value(), n.unit(), n.status(), refLow, refHigh, conf,
                new SourceEvidence(pageIndex, rawText, null));
    }

    private static Integer parsePage(String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
