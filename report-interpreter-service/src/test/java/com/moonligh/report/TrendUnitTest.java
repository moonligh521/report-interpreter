package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.report.domain.trend.TrendComparability;
import com.moonligh.report.domain.trend.TrendIncomparableReason;
import com.moonligh.report.infrastructure.persistence.TrendPointRow;

/** T-R2：单位不可换算 → comparable=false + 原因码。 */
class TrendUnitTest {

    @Test
    void mismatchedNormalizedUnitsAreNotComparable() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        List<TrendPointRow> rows = List.of(
                new TrendPointRow("r1", 1, new BigDecimal("40"), "U/L", "SERUM", t),
                new TrendPointRow("r2", 1, new BigDecimal("1.2"), "ukat/L", "SERUM", t.plusSeconds(10)));
        TrendComparability.Decision d = TrendComparability.evaluate(rows);
        assertFalse(d.comparable());
        assertEquals(TrendIncomparableReason.UNIT_NOT_CONVERTIBLE, d.reason());
    }

    @Test
    void sameNormalizedUnitIsComparable() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        List<TrendPointRow> rows = List.of(
                new TrendPointRow("r1", 1, new BigDecimal("40"), "U/L", "SERUM", t),
                new TrendPointRow("r2", 1, new BigDecimal("42"), "u/l", "SERUM", t.plusSeconds(10)));
        assertTrue(TrendComparability.evaluate(rows).comparable());
    }

    @Test
    void requireComparableClearsPointsWhenNotComparable() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        List<TrendPointRow> rows = List.of(
                new TrendPointRow("r1", 1, new BigDecimal("40"), "U/L", "SERUM", t),
                new TrendPointRow("r2", 1, new BigDecimal("1.2"), "ukat/L", "SERUM", t.plusSeconds(10)));
        assertFalse(TrendComparability.evaluate(rows).comparable());
    }
}
