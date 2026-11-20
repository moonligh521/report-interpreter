package com.moonligh.report.domain.parse;

/**
 * 页级解析状态。任一关键 FAILED 页时文档不得 PASSED。
 */
public enum PageParseStatus {
    SUCCESS,
    LOW_CONFIDENCE,
    EMPTY,
    FAILED,
    NEED_MANUAL_REVIEW
}
