package com.moonligh.report.domain.parse;

import java.util.ArrayList;
import java.util.List;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 质量门禁：证据完整性优先，置信度为辅；页级状态汇总为文档级。
 * 任一关键 FAILED 页 → 文档不得 PASSED；LOW_CONFIDENCE → NEED_MANUAL_REVIEW。
 */
public final class ParseQualityGate {

    /** 文档整体置信度低于此值时倾向人工复核（辅条件）。 */
    static final double LOW_CONFIDENCE_THRESHOLD = 0.55;

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

        if (!meta.failedPages().isEmpty()) {
            issues.add(new QualityIssue(
                    "FAILED_PAGES",
                    "存在失败页: " + meta.failedPages()));
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
        boolean hasFailedPages = !meta.failedPages().isEmpty();
        boolean hasLowConfPages = !meta.lowConfidencePages().isEmpty();

        if (!hasIndicators && !hasStructure) {
            // 有失败页时仍视为 FAILED（不得因「无指标」假装通过）。
            issues.add(new QualityIssue("NO_CONTENT", "未解析出任何指标或结构内容"));
            return new QualityVerdict(DocumentQualityStatus.FAILED, issues);
        }

        if (evidenceBroken || hasFailedPages) {
            // 证据不全或关键页失败：不得 PASSED；失败页优先 FAILED。
            DocumentQualityStatus status = hasFailedPages || evidenceBroken
                    ? DocumentQualityStatus.FAILED
                    : DocumentQualityStatus.NEED_MANUAL_REVIEW;
            if (hasFailedPages) {
                status = DocumentQualityStatus.FAILED;
            }
            return new QualityVerdict(status, issues);
        }

        if (hasLowConfPages || result.parseConfidence() < LOW_CONFIDENCE_THRESHOLD) {
            if (result.parseConfidence() < LOW_CONFIDENCE_THRESHOLD) {
                issues.add(new QualityIssue(
                        "LOW_PARSE_CONFIDENCE",
                        "整体置信度过低: " + result.parseConfidence()));
            }
            return new QualityVerdict(DocumentQualityStatus.NEED_MANUAL_REVIEW, issues);
        }

        return new QualityVerdict(DocumentQualityStatus.PASSED, issues);
    }
}
