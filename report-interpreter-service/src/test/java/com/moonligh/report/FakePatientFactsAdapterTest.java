package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.rule.FactsSource;
import com.moonligh.report.domain.rule.PatientFactsResolution;
import com.moonligh.report.domain.rule.RuleContext;
import com.moonligh.report.domain.rule.RuleContextResolver;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.infrastructure.facts.FakePatientFactsAdapter;

import java.math.BigDecimal;

/** Task2：Fake 人口学进 RuleContext；未知 patientId → UNKNOWN。 */
class FakePatientFactsAdapterTest {

    private final FakePatientFactsAdapter fake = new FakePatientFactsAdapter();
    private final RuleContextResolver resolver = new RuleContextResolver();
    private final Instant asOf = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    void adultPrefixProvidesAgeAndSex() {
        PatientFactsResolution r = fake.resolve("demo-adult-p1", asOf);
        assertEquals(FactsSource.FAKE, r.source());
        assertFalse(r.incomplete());
        assertEquals(42, r.facts().age());
        assertEquals("M", r.facts().sex());

        RuleContext ctx = resolver.resolve("ind1", sampleIndicator(), r.facts());
        assertEquals(42, ctx.age());
        assertEquals("M", ctx.sex());
    }

    @Test
    void unknownPatientIdYieldsIncompleteUnknown() {
        PatientFactsResolution r = fake.resolve("p-random", asOf);
        assertTrue(r.incomplete());
        assertNull(r.facts().age());
        assertNull(r.facts().sex());
        RuleContext ctx = resolver.resolve("ind1", sampleIndicator(), r.facts());
        assertNull(ctx.age());
        assertNull(ctx.sex());
    }

    private static ParsedIndicator sampleIndicator() {
        return new ParsedIndicator(
                "钾",
                "4.2",
                "mmol/L",
                "K",
                "SERUM",
                new BigDecimal("4.2"),
                "mmol/L",
                "OK",
                new BigDecimal("3.5"),
                new BigDecimal("5.5"),
                new FieldConfidence(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99),
                null);
    }
}
