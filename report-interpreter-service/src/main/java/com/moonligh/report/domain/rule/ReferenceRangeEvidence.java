package com.moonligh.report.domain.rule;

import java.math.BigDecimal;

/**
 * 报告原文参考区间证据。unit 为区间自身的单位（原文单位），与规范化后的指标单位比对后才可使用；
 * confidence 为 refRange 字段置信度。
 */
public record ReferenceRangeEvidence(BigDecimal low, BigDecimal high, String unit, double confidence) {

    public static final ReferenceRangeEvidence NONE = new ReferenceRangeEvidence(null, null, null, 0.0);
}
