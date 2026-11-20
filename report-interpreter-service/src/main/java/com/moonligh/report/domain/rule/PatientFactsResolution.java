package com.moonligh.report.domain.rule;

/**
 * 人口学解析结果。facts 全空时 incomplete 必须为 true；禁止用 Map 扩展。
 */
public record PatientFactsResolution(
        PatientFacts facts,
        FactsSource source,
        boolean incomplete,
        String detailCode) {

    public PatientFactsResolution {
        if (facts == null) {
            throw new IllegalArgumentException("facts is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (isBlankFacts(facts) && !incomplete) {
            throw new IllegalArgumentException("UNKNOWN facts must be incomplete");
        }
        detailCode = detailCode == null || detailCode.isBlank() ? null : detailCode;
    }

    public static PatientFactsResolution unknown(FactsSource source, String detailCode) {
        return new PatientFactsResolution(PatientFacts.UNKNOWN, source, true, detailCode);
    }

    private static boolean isBlankFacts(PatientFacts f) {
        return f.age() == null
                && (f.sex() == null || f.sex().isBlank())
                && (f.pregnancyStatus() == null || f.pregnancyStatus().isBlank());
    }
}
