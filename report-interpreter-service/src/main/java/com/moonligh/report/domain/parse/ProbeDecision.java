package com.moonligh.report.domain.parse;

/**
 * 文本层探针整份路由决策（本版不做页级混合）。
 */
public enum ProbeDecision {
    USE_NATIVE,
    USE_OCR,
    NEED_REVIEW
}
