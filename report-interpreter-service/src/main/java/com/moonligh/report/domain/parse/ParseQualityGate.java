package com.moonligh.report.domain.parse;

import java.util.ArrayList;
import java.util.List;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 质量门禁：证据完整性优先，置信度为辅；页级解析失败状态汇总为文档级。
 * 任一 parseFailed 页 → 文档不得 PASSED；LOW_CONFIDENCE → NEED_MANUAL_REVIEW。
 * 注意：ReportMeta.failedPages 仍留给 CriticalCoverage，不在此当作解析硬失败。
 */
public final class ParseQualityGate {

    private ParseQualityGate() {
    }

    /** 兼容旧调用：未 PASSED 则抛 4102。 */
    public static void check(ParseResult result) {
        QualityVerdict verdict = evaluate(result);
        if (!verdict.passed()) {
            String detail = verdict.issues().isEmpty()
                    ? "质量门禁未通过"
                    : verdict.issues().get(0).message();
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, detail);
        }
    }

    public static QualityVerdict evaluate(ParseResult result) {
        List<QualityIssue> issues = new ArrayList<>();
        if (result == null) {
            issues.add(new QualityIssue("EMPTY_RESULT", "解析结果为空"));
            return new QualityVerdict(DocumentQualityStatus.FAILED, issues);
        }
        boolean hasIndicators = result.indicators() != null && !result.indicators().isEmpty();
        boolean hasStructure = result.structure() != null && !result.structure().isEmpty();
        ReportMeta meta = result.meta() == null ? ReportMeta.EMPTY : result.meta();

        if (!meta.parseFailedPages().isEmpty()) {
            issues.add(new QualityIssue(
                    "PARSE_FAILED_PAGES",
                    "存在解析失败页: " + meta.parseFailedPages()));
        }
        if (!meta.lowConfidencePages().isEmpty()) {
            issues.add(new QualityIssue(
                    "LOW_CONFIDENCE_PAGES",
                    "存在低置信页: " + meta.lowConfidencePages()));
        }

        if (hasIndicators) {
            for (ParsedIndicator ind : result.indicators()) {
                var ev = ind.evidence();
                if (ev == null || ev.pageIndex() == null || ev.pageIndex() < 0
                        || ev.rawText() == null || ev.rawText().isBlank()) {
                    issues.add(new QualityIssue("MISSING_EVIDENCE", "指标缺少 pageIndex/rawText 原文证据"));
                }
                if (ind.rawName() == null || ind.rawName().isBlank()
                        || ind.rawValue() == null || ind.rawValue().isBlank()) {
                    issues.add(new QualityIssue("MISSING_RAW_FIELDS", "指标缺少原始名称或原始值"));
                }
            }
        }

        boolean evidenceBroken = issues.stream().anyMatch(i ->
                "MISSING_EVIDENCE".equals(i.code()) || "MISSING_RAW_FIELDS".equals(i.code()));
        boolean hasParseFailedPages = !meta.parseFailedPages().isEmpty();
        boolean hasLowConfPages = !meta.lowConfidencePages().isEmpty();

        if (!hasIndicators && !hasStructure) {
            issues.add(new QualityIssue("NO_CONTENT", "未解析出任何指标或结构内容"));
            return new QualityVerdict(DocumentQualityStatus.FAILED, issues);
        }

        if (evidenceBroken || hasParseFailedPages) {
            return new QualityVerdict(DocumentQualityStatus.FAILED, issues);
        }

        // 页级 LOW_CONFIDENCE → 文档 NEED_MANUAL_REVIEW；字段级低置信留给规则层（如适用性 UNKNOWN）。
        if (hasLowConfPages) {
            return new QualityVerdict(DocumentQualityStatus.NEED_MANUAL_REVIEW, issues);
        }

        return new QualityVerdict(DocumentQualityStatus.PASSED, issues);
    }
}
