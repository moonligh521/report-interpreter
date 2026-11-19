package com.moonligh.report;

import java.math.BigDecimal;

import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.domain.rule.ReferenceRangeEvidence;
import com.moonligh.report.domain.rule.RuleContext;

/**
 * 规则域单测共用：构造 RuleContext。
 */
final class RuleTestSupport {

    static final FieldConfidence HIGH_CONF = new FieldConfidence(1.0, 0.95, 0.95, 0.98, 0.98, 0.98, 0.98, 0.98);

    private RuleTestSupport() {
    }

    static RuleContext ctx(
            String code, String value, String unit, String specimen,
            FieldConfidence conf, ReferenceRangeEvidence range) {
        return new RuleContext(
                "ind-test", code, value == null ? null : new BigDecimal(value), unit, specimen,
                null, null, null, null, null, null, conf, range);
    }

    static RuleContext k(String value) {
        return ctx("K", value, "mmol/L", "SERUM", HIGH_CONF,
                new ReferenceRangeEvidence(new BigDecimal("3.5"), new BigDecimal("5.3"), "mmol/L", 0.98));
    }

    static FieldConfidence withRefRange(double refRange) {
        FieldConfidence c = HIGH_CONF;
        return new FieldConfidence(c.ocr(), c.structure(), c.extraction(), c.normalization(),
                c.name(), c.value(), c.unit(), refRange);
    }
}