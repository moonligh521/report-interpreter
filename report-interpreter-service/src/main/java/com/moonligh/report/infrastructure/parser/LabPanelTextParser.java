package com.moonligh.report.infrastructure.parser;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;

/**
 * LAB_PANEL 文本解析器（P0，金标夹具格式，未接真实 OCR）。
 * <pre>
 * REPORT_TYPE=LAB_PANEL
 * rawName|rawValue|rawUnit|refLow|refHigh|rawText|pageIndex
 * </pre>
 */
@Component
public class LabPanelTextParser implements ReportParser {

    static final String TYPE_PREFIX = "REPORT_TYPE=";

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
            indicators.add(parseLine(trimmed));
        }
        if (!headerSeen) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        double overall = indicators.stream()
                .mapToDouble(i -> i.confidence().min())
                .min()
                .orElse(0.0);
        return new ParseResult(ReportType.LAB_PANEL, List.copyOf(indicators), overall);
    }

    private void checkHeader(String line) {
        if (!line.startsWith(TYPE_PREFIX)) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        String declared = line.substring(TYPE_PREFIX.length()).strip();
        if (!ReportType.LAB_PANEL.name().equals(declared)) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "仅支持 LAB_PANEL 报告");
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

        LabNormalizer.Outcome n = LabNormalizer.normalize(rawName, rawValue, rawUnit);
        FieldConfidence conf = confidence(n, rawUnit, refLow, refHigh);
        SourceEvidence evidence = new SourceEvidence(pageIndex, rawText, null);
        return new ParsedIndicator(
                rawName, rawValue, rawUnit, n.canonicalCode(), n.specimenType(),
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