package com.moonligh.report.domain.parse;

/**
 * 文档级质量门禁结论。仅 PASSED 可 active 并触发规则链。
 */
public enum DocumentQualityStatus {
    PASSED,
    NEED_MANUAL_REVIEW,
    FAILED
}
