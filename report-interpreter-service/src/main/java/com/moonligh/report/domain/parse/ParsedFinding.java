package com.moonligh.report.domain.parse;

/** 解析出的所见。 */
public record ParsedFinding(String id, String sectionId, String text, Integer pageIndex) {
}
