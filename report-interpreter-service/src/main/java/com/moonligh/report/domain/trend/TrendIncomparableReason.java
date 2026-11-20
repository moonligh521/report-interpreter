package com.moonligh.report.domain.trend;

/** 趋势不可比原因码（API 原样透出，不做诊断叙述）。 */
public final class TrendIncomparableReason {

    public static final String SPECIMEN_MISMATCH = "SPECIMEN_MISMATCH";
    public static final String UNIT_NOT_CONVERTIBLE = "UNIT_NOT_CONVERTIBLE";
    public static final String MISSING_SPECIMEN_TIME = "MISSING_SPECIMEN_TIME";

    private TrendIncomparableReason() {
    }
}
