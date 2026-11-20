package com.moonligh.report.domain.rule;

/**
 * 人口学来源（审计用）。IDENTITY_FAILED 表示身份调用失败后降级为 UNKNOWN。
 */
public enum FactsSource {
    FAKE,
    IDENTITY,
    IDENTITY_FAILED,
    NONE
}
