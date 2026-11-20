package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.rule.ClinicalCatalog;
import com.moonligh.report.domain.rule.PatientFacts;
import com.moonligh.report.domain.rule.ReferenceRangeEngine;
import com.moonligh.report.domain.rule.ReferenceRangeResult;
import com.moonligh.report.domain.rule.ReferenceStatus;
import com.moonligh.report.domain.rule.RuleContext;
import com.moonligh.report.domain.rule.RuleContextResolver;
import com.moonligh.report.infrastructure.catalog.FakeClinicalCatalogAdapter;

/** CF-1：Fake 目录分层参考区间随年龄命中不同规则。 */
class ClinicalFactsStratifiedRangeTest {

    private final ClinicalCatalog catalog =
            new FakeClinicalCatalogAdapter().load(FakeClinicalCatalogAdapter.VERSION_V1);
    private final ReferenceRangeEngine engine = new ReferenceRangeEngine(catalog.referenceRules());
    private final RuleContextResolver resolver = new RuleContextResolver();

    @Test
    void adultAndChildHitDifferentRangesForSameValue() {
        // 低 refRange 置信度 → 走目录；5.2 对儿童偏高、对成人正常。
        ParsedIndicator indicator = kWithoutUsableReportRange("5.2");
        RuleContext adult = resolver.resolve("i1", indicator, new PatientFacts(42, "M", null));
        RuleContext child = resolver.resolve("i2", indicator, new PatientFacts(10, "F", null));

        ReferenceRangeResult adultResult = engine.evaluate(adult);
        ReferenceRangeResult childResult = engine.evaluate(child);

        assertEquals(ReferenceStatus.NORMAL, adultResult.status());
        assertEquals("REF-AGE-K-ADULT", adultResult.ruleId());
        assertEquals(ReferenceStatus.HIGH, childResult.status());
        assertEquals("REF-AGE-K-CHILD", childResult.ruleId());
        assertNotEquals(adultResult.ruleId(), childResult.ruleId());
    }

    private static ParsedIndicator kWithoutUsableReportRange(String value) {
        return new ParsedIndicator(
                "钾",
                value,
                "mmol/L",
                "K",
                "SERUM",
                new BigDecimal(value),
                "mmol/L",
                "OK",
                new BigDecimal("3.5"),
                new BigDecimal("5.5"),
                new FieldConfidence(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.50),
                null);
    }
}
