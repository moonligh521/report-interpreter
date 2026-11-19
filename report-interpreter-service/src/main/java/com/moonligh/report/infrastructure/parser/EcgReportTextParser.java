package com.moonligh.report.infrastructure.parser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedAssessment;
import com.moonligh.report.domain.parse.ParsedFinding;
import com.moonligh.report.domain.parse.ParsedStructure;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;

/**
 * ECG_REPORT 文本夹具解析器（不依赖波形文件）。
 * <pre>
 * REPORT_TYPE=ECG_REPORT
 * [WAVEFORM_REQUIRED=true]   若声明且无 FINDING → 解析失败
 * FINDING|id|text|pageIndex
 * ASSESSMENT|id|text
 * </pre>
 */
@Component
public class EcgReportTextParser implements ReportParser {

    private static final String TYPE_PREFIX = "REPORT_TYPE=";
    private static final String WAVEFORM_PREFIX = "WAVEFORM_REQUIRED=";

    @Override
    public boolean supports(ParseContext context) {
        if (context.reportType() != ReportType.ECG_REPORT) {
            return false;
        }
        String name = context.fileName() == null ? "" : context.fileName().toLowerCase(Locale.ROOT);
        return "text/plain".equalsIgnoreCase(context.contentType()) || name.endsWith(".ecg.txt");
    }

    @Override
    public ParseResult parse(ParseContext context) {
        String text = new String(context.content(), StandardCharsets.UTF_8);
        List<ParsedFinding> findings = new ArrayList<>();
        List<ParsedAssessment> assessments = new ArrayList<>();
        boolean headerSeen = false;
        boolean waveformRequired = false;
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
            if (trimmed.startsWith(WAVEFORM_PREFIX)) {
                waveformRequired = Boolean.parseBoolean(trimmed.substring(WAVEFORM_PREFIX.length()).strip());
                continue;
            }
            if (trimmed.startsWith("FINDING|")) {
                findings.add(parseFinding(trimmed));
                continue;
            }
            if (trimmed.startsWith("ASSESSMENT|")) {
                assessments.add(parseAssessment(trimmed));
                continue;
            }
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "无法识别的心电行");
        }
        if (!headerSeen) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        if (waveformRequired && findings.isEmpty()) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "声明 WAVEFORM_REQUIRED 但缺少所见");
        }
        double conf = findings.isEmpty() && assessments.isEmpty() ? 0.0 : 0.95;
        return new ParseResult(
                ReportType.ECG_REPORT, List.of(), conf, null,
                new ParsedStructure(List.of(), List.of(), findings, assessments));
    }

    private static void checkHeader(String line) {
        if (!line.startsWith(TYPE_PREFIX)) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        String declared = line.substring(TYPE_PREFIX.length()).strip();
        if (!ReportType.ECG_REPORT.name().equals(declared)) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "夹具类型与声明不符");
        }
    }

    private static ParsedFinding parseFinding(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length < 3) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "FINDING 行格式不合法");
        }
        Integer page = f.length > 3 ? parsePage(f[3]) : null;
        return new ParsedFinding(f[1].strip(), null, f[2].strip(), page);
    }

    private static ParsedAssessment parseAssessment(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length < 3) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "ASSESSMENT 行格式不合法");
        }
        return new ParsedAssessment(f[1].strip(), f[2].strip());
    }

    private static Integer parsePage(String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
