package com.moonligh.report.api.res;

/** 规范码到 LOINC 的可选映射提示（非权威主数据）。 */
public record LoincMapping(String canonicalCode, String loincCode, String display) {
}
