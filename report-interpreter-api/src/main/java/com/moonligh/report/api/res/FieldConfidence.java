package com.moonligh.report.api.res;

/**
 * 分层置信度（ocr/structure/extraction/normalization）+ 字段级置信度。
 */
public record FieldConfidence(
        double ocr,
        double structure,
        double extraction,
        double normalization,
        double name,
        double value,
        double unit,
        double refRange) {

    /** 各层与字段中的最小值，禁止以单一 model score 放行。 */
    public double min() {
        return Math.min(
                Math.min(Math.min(ocr, structure), Math.min(extraction, normalization)),
                Math.min(Math.min(name, value), Math.min(unit, refRange)));
    }
}