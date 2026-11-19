package com.moonligh.report.api.res;

/**
 * 指标原文证据：pageIndex 与 rawText 为关键指标必填。
 */
public record SourceEvidence(Integer pageIndex, String rawText, BoundingBox region) {
}