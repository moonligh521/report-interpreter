package com.moonligh.report.domain.parse;

/**
 * 质量问题条目（强类型，禁 Map）。
 */
public record QualityIssue(String code, String message) {

    public QualityIssue {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        message = message == null ? "" : message;
    }
}
