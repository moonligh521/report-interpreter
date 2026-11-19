package com.moonligh.report.infrastructure.parser;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ParsedStructure;
import com.moonligh.report.domain.parse.ParsedSubReport;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;

/**
 * CHECKUP_BOOK 文本夹具解析器。
 * <pre>
 * REPORT_TYPE=CHECKUP_BOOK
 * SUB|subId|title|reportTimeIso|pageIndex
 * INDICATOR|subId|rawName|rawValue|rawUnit|refLow|refHigh|rawText|pageIndex
 * </pre>
 */
@Component
public class CheckupBookTextParser implements ReportParser {

    private static final String TYPE_PREFIX = "REPORT_TYPE=";

    @Override
    public boolean supports(ParseContext context) {
        if (context.reportType() != ReportType.CHECKUP_BOOK) {
            return false;
        }
        String name = context.fileName() == null ? "" : context.fileName().toLowerCase(Locale.ROOT);
        return "text/plain".equalsIgnoreCase(context.contentType()) || name.endsWith(".checkup.txt");
    }

    @Override
    public ParseResult parse(ParseContext context) {
        String text = new String(context.content(), StandardCharsets.UTF_8);
        List<ParsedSubReport> subs = new ArrayList<>();
        Map<String, ParsedSubReport> byId = new HashMap<>();
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
            if (trimmed.startsWith("SUB|")) {
                ParsedSubReport sub = parseSub(trimmed);
                subs.add(sub);
                byId.put(sub.subReportId(), sub);
                continue;
            }
            if (trimmed.startsWith("INDICATOR|")) {
                indicators.add(parseIndicator(trimmed, byId));
                continue;
            }
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "无法识别的体检册行");
        }
        if (!headerSeen) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        double overall = indicators.stream().mapToDouble(i -> i.confidence().min()).min().orElse(0.0);
        return new ParseResult(
                ReportType.CHECKUP_BOOK, List.copyOf(indicators), overall, null,
                new ParsedStructure(subs, List.of(), List.of(), List.of()));
    }

    private static void checkHeader(String line) {
        if (!line.startsWith(TYPE_PREFIX)) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        String declared = line.substring(TYPE_PREFIX.length()).strip();
        if (!ReportType.CHECKUP_BOOK.name().equals(declared)) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "夹具类型与声明不符");
        }
    }

    private static ParsedSubReport parseSub(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length < 5) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "SUB 行格式不合法");
        }
        Instant time;
        try {
            time = Instant.parse(f[3].strip());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "SUB reportTime 不合法");
        }
        return new ParsedSubReport(f[1].strip(), f[2].strip(), time, parsePage(f[4]));
    }

    private static ParsedIndicator parseIndicator(String line, Map<String, ParsedSubReport> byId) {
        String[] f = line.split("\\|", -1);
        if (f.length < 9) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "INDICATOR 行格式不合法");
        }
        String subId = f[1].strip();
        ParsedSubReport sub = byId.get(subId);
        if (sub == null) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "指标缺少对应 SUB: " + subId);
        }
        String rawName = f[2].strip();
        String rawValue = f[3].strip();
        String rawUnit = f[4].strip();
        BigDecimal refLow = LabNormalizer.parseNumber(f[5]);
        BigDecimal refHigh = LabNormalizer.parseNumber(f[6]);
        String rawText = f[7].strip();
        Integer pageIndex = parsePage(f[8]);
        LabNormalizer.Outcome n = LabNormalizer.normalize(rawName, rawValue, rawUnit);
        FieldConfidence conf = confidence(n, rawUnit, refLow, refHigh);
        SourceEvidence evidence = new SourceEvidence(pageIndex, rawText, null);
        return new ParsedIndicator(
                rawName, rawValue, rawUnit, n.canonicalCode(), n.specimenType(),
                n.value(), n.unit(), n.status(), refLow, refHigh, conf, evidence,
                subId, sub.reportTime());
    }

    private static Integer parsePage(String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

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
