package com.moonligh.report.api.res;

/**
 * 超参考区间命中（≠ 危急）。referenceResult 取 LOW / HIGH。
 */
public record AbnormalHitRes(String canonicalCode, String referenceResult, String ruleId, String ruleVersion) {
}