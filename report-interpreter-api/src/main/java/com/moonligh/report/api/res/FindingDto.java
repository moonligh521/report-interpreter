package com.moonligh.report.api.res;

/** 所见。sectionId 可空（如心电图无分区）。 */
public record FindingDto(String id, String sectionId, String text, Integer pageIndex) {
}
