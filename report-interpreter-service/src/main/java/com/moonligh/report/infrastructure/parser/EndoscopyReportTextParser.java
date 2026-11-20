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
import com.moonligh.report.domain.parse.ParsedSection;
import com.moonligh.report.domain.parse.ParsedStructure;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;

/**
 * ENDOSCOPY_REPORT 文本夹具解析器。
 * <pre>
 * REPORT_TYPE=ENDOSCOPY_REPORT
 * SECTION|id|title
 * FINDING|id|sectionId|text
 * ASSESSMENT|id|text
 * </pre>
 */
@Component
public class EndoscopyReportTextParser implements ReportParser {

    private static final String TYPE_PREFIX = "REPORT_TYPE=";

    @Override
    public boolean supports(ParseContext context) {
        if (context.reportType() != ReportType.ENDOSCOPY_REPORT) {
            return false;
        }
        String name = context.fileName() == null ? "" : context.fileName().toLowerCase(Locale.ROOT);
        return "text/plain".equalsIgnoreCase(context.contentType()) || name.endsWith(".endo.txt");
    }

    @Override
    public ParseResult parse(ParseContext context) {
        String text = new String(context.content(), StandardCharsets.UTF_8);
        List<ParsedSection> sections = new ArrayList<>();
        List<ParsedFinding> findings = new ArrayList<>();
        List<ParsedAssessment> assessments = new ArrayList<>();
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
            if (trimmed.startsWith("SECTION|")) {
                sections.add(parseSection(trimmed));
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
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "无法识别的内镜行");
        }
        if (!headerSeen) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        double conf = sections.isEmpty() && findings.isEmpty() && assessments.isEmpty() ? 0.0 : 0.95;
        return new ParseResult(
                ReportType.ENDOSCOPY_REPORT, List.of(), conf, null,
                new ParsedStructure(List.of(), sections, findings, assessments));
    }

    private static void checkHeader(String line) {
        if (!line.startsWith(TYPE_PREFIX)) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "缺少 REPORT_TYPE 头");
        }
        String declared = line.substring(TYPE_PREFIX.length()).strip();
        if (!ReportType.ENDOSCOPY_REPORT.name().equals(declared)) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "夹具类型与声明不符");
        }
    }

    private static ParsedSection parseSection(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length < 3) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "SECTION 行格式不合法");
        }
        return new ParsedSection(f[1].strip(), f[2].strip());
    }

    private static ParsedFinding parseFinding(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length < 4) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "FINDING 行格式不合法");
        }
        return new ParsedFinding(f[1].strip(), f[2].strip(), f[3].strip(), null);
    }

    private static ParsedAssessment parseAssessment(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length < 3) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "ASSESSMENT 行格式不合法");
        }
        return new ParsedAssessment(f[1].strip(), f[2].strip());
    }
}
