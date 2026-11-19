package com.moonligh.report.domain.trend;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.moonligh.report.infrastructure.persistence.TrendPointRow;

/**
 * 趋势可比性判定（T-R1 / T-R2）：
 * 同 canonical 不同标本 → 默认不可自动比；
 * 规范化单位不一致 → 视为不可换算。
 */
public final class TrendComparability {

    public record Decision(boolean comparable, String reason) {
    }

    private TrendComparability() {
    }

    public static Decision evaluate(List<TrendPointRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Decision(true, null);
        }
        Set<String> specimens = new HashSet<>();
        Set<String> units = new HashSet<>();
        boolean missingTime = false;
        for (TrendPointRow r : rows) {
            if (r.specimenTime() == null) {
                missingTime = true;
            }
            if (r.specimenType() != null && !r.specimenType().isBlank()) {
                specimens.add(r.specimenType().trim().toUpperCase(Locale.ROOT));
            } else {
                specimens.add("");
            }
            if (r.normalizedUnit() != null && !r.normalizedUnit().isBlank()) {
                units.add(r.normalizedUnit().trim().toUpperCase(Locale.ROOT));
            } else if (r.normalizedValue() != null) {
                units.add("");
            }
        }
        if (specimens.size() > 1) {
            return new Decision(false, TrendIncomparableReason.SPECIMEN_MISMATCH);
        }
        if (units.size() > 1) {
            return new Decision(false, TrendIncomparableReason.UNIT_NOT_CONVERTIBLE);
        }
        if (missingTime && rows.size() > 1) {
            return new Decision(false, TrendIncomparableReason.MISSING_SPECIMEN_TIME);
        }
        return new Decision(true, null);
    }

    /** 是否同一标本类型（忽略大小写；双方皆空视为同）。 */
    public static boolean sameSpecimen(String a, String b) {
        return Objects.equals(norm(a), norm(b));
    }

    private static String norm(String s) {
        return s == null || s.isBlank() ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
