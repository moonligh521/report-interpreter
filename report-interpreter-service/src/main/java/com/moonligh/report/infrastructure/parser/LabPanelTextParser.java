package com.moonligh.report.infrastructure.parser;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ReportMeta;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;

/**
 * LAB_PANEL 文本解析器（P0，金标夹具格式，未接真实 OCR）。
 * <pre>
 * REPORT_TYPE=LAB_PANEL
 * [PATIENT_NAME=姓名]            可选，报告自带身份（金标夹具用）
 * [PAGE_COUNT=N]                 可选，声明总页数
 * [FAILED_PAGES=1,2]             可选，解析失败页
 * [UNRECOGNIZED_REGIONS=a;b]     可选，未识别文本区
 * [SPECIMEN_TIME=ISO-8601]       可选，标本时间（趋势排序）
 * rawName|rawValue|rawUnit|refLow|refHigh|rawText|pageIndex[|specimenType]
 * </pre>
 */
@Component
public class LabPanelTextParser implements ReportParser {

    static final String TYPE_PREFIX = "REPORT_TYPE=";
    static final String NAME_PREFIX = "PATIENT_NAME=";
    static final String PAGE_COUNT_PREFIX = "PAGE_COUNT=";
    static final String FAILED_PAGES_PREFIX = "FAILED_PAGES=";
    static final String UNRECOGNIZED_PREFIX = "UNRECOGNIZED_REGIONS=";
    static final String SPECIMEN_TIME_PREFIX = "SPECIMEN_TIME=";

    @Override
    public boolean supports(ParseContext context) {
        if (context.reportType() != ReportType.LAB_PANEL) {
            return false;
        }
        String name = context.fileName() == null ? "" : context.fileName().toLowerCase(Locale.ROOT);
        return "text/plain".equalsIgnoreCase(context.contentType()) || name.endsWith(".lab.txt");
    }

    @Override
    public ParseResult parse(ParseContext context) {
        String text = new String(context.content(), StandardCharsets.UTF_8);
        List<ParsedIndicator> indicators = new ArrayList<>();
        MetaBuilder meta = new MetaBuilder();
        boolean headerSeen = false;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                checkHeader(trimmed);
                headerSeen = true;
                continue;
            }
            if (meta.accept(trimmed)) {
                continue;
            }
            indicators.add(parseLine(trimmed));
        }
        if (!headerSeen) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        double overall = indicators.stream()
                .mapToDouble(i -> i.confidence().min())
                .min()
                .orElse(0.0);
        return new ParseResult(ReportType.LAB_PANEL, List.copyOf(indicators), overall, meta.build());
    }

    /** 收集报告级指令行（身份、页数、失败页、未识别区）。 */
    private static final class MetaBuilder {

        private String patientName;
        private Integer totalPages;
        private Instant specimenTime;
        private final List<Integer> failedPages = new ArrayList<>();
        private final List<String> regions = new ArrayList<>();

        /** 命中指令行返回 true，否则交给指标行解析。 */
        boolean accept(String line) {
            if (line.startsWith(NAME_PREFIX)) {
                patientName = line.substring(NAME_PREFIX.length()).strip();
                return true;
            }
            if (line.startsWith(PAGE_COUNT_PREFIX)) {
                totalPages = parsePage(line.substring(PAGE_COUNT_PREFIX.length()));
                return true;
            }
            if (line.startsWith(FAILED_PAGES_PREFIX)) {
                for (String p : line.substring(FAILED_PAGES_PREFIX.length()).split(",")) {
                    Integer page = parsePage(p);
                    if (page != null) {
                        failedPages.add(page);
                    }
                }
                return true;
            }
            if (line.startsWith(UNRECOGNIZED_PREFIX)) {
                for (String r : line.substring(UNRECOGNIZED_PREFIX.length()).split(";")) {
                    if (!r.isBlank()) {
                        regions.add(r.strip());
                    }
                }
                return true;
            }
            if (line.startsWith(SPECIMEN_TIME_PREFIX)) {
                String raw = line.substring(SPECIMEN_TIME_PREFIX.length()).strip();
                try {
                    specimenTime = Instant.parse(raw);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "SPECIMEN_TIME 不合法");
                }
                return true;
            }
            return false;
        }

        ReportMeta build() {
            return new ReportMeta(patientName, totalPages, failedPages, regions, specimenTime);
        }
    }

    private void checkHeader(String line) {
        if (!line.startsWith(TYPE_PREFIX)) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        String declared = line.substring(TYPE_PREFIX.length()).strip();
        if (!ReportType.LAB_PANEL.name().equals(declared)) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "夹具类型与声明不符");
        }
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
        String rawText = f.length > 5 ? f[5].strip() : null;
        Integer pageIndex = f.length > 6 ? parsePage(f[6]) : null;

        String specimenOverride = f.length > 7 && !f[7].isBlank() ? f[7].strip() : null;
        LabNormalizer.Outcome n = LabNormalizer.normalize(rawName, rawValue, rawUnit);
        String specimen = specimenOverride != null ? specimenOverride : n.specimenType();
        FieldConfidence conf = confidence(n, rawUnit, refLow, refHigh);
        SourceEvidence evidence = new SourceEvidence(pageIndex, rawText, null);
        return new ParsedIndicator(
                rawName, rawValue, rawUnit, n.canonicalCode(), specimen,
                n.value(), n.unit(), n.status(), refLow, refHigh, conf, evidence);
    }

    private static Integer parsePage(String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 文本夹具无 OCR 损耗，ocr=1.0；其余层按实际可解析性给分。 */
    private static FieldConfidence confidence(
            LabNormalizer.Outcome n, String rawUnit, BigDecimal refLow, BigDecimal refHigh) {
        double normalization = switch (n.status()) {
            case LabNormalizer.NORMALIZED -> 0.98;
            case LabNormalizer.CONVERTED -> 0.90;
            default -> 0.30;
        };
        double name = n.canonicalCode() != null ? 0.98 : 0.60;
        double value = n.status().equals(LabNormalizer.VALUE_NOT_NUMERIC) ? 0.50 : 0.98;
        double unit = rawUnit.isBlank() ? 0.50 : 0.98;
        double ref = refLow != null && refHigh != null ? 0.98 : 0.50;
        return new FieldConfidence(1.0, 0.95, 0.95, normalization, name, value, unit, ref);
    }
}