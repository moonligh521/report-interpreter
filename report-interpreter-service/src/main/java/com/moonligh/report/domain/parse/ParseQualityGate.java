package com.moonligh.report.domain.parse;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 质量门禁：LAB/CHECKUP 每个指标必须带 pageIndex + rawText；ECG/US 允许以所见/结论代替指标。
 */
public final class ParseQualityGate {

    private ParseQualityGate() {
    }

    public static void check(ParseResult result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "解析结果为空");
        }
        boolean hasIndicators = result.indicators() != null && !result.indicators().isEmpty();
        boolean hasStructure = result.structure() != null && !result.structure().isEmpty();
        if (!hasIndicators && !hasStructure) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "未解析出任何指标或结构内容");
        }
        if (!hasIndicators) {
            return;
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
