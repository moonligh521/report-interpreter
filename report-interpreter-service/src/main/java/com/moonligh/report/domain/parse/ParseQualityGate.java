package com.moonligh.report.domain.parse;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 质量门禁：每个指标必须带 pageIndex + rawText 证据，且不得仅凭单一分数放行。
 */
public final class ParseQualityGate {

    private ParseQualityGate() {
    }

    public static void check(ParseResult result) {
        if (result == null || result.indicators() == null || result.indicators().isEmpty()) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "未解析出任何指标");
        }
        for (ParsedIndicator ind : result.indicators()) {
            var ev = ind.evidence();
            if (ev == null || ev.pageIndex() == null || ev.pageIndex() < 0
                    || ev.rawText() == null || ev.rawText().isBlank()) {
                throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "指标缺少 pageIndex/rawText 原文证据");
            }
            if (ind.rawName() == null || ind.rawName().isBlank()
                    || ind.rawValue() == null || ind.rawValue().isBlank()) {
                throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "指标缺少原始名称或原始值");
            }
        }
    }
}