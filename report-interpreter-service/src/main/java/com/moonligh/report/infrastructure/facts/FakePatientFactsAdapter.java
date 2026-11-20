package com.moonligh.report.infrastructure.facts;

import java.time.Instant;

import com.moonligh.report.domain.rule.FactsSource;
import com.moonligh.report.domain.rule.PatientFacts;
import com.moonligh.report.domain.rule.PatientFactsPort;
import com.moonligh.report.domain.rule.PatientFactsResolution;

/**
 * 测试用人口学：约定 patientId 前缀；未知 id → UNKNOWN（与历史写死行为兼容）。
 * <ul>
 *   <li>{@code demo-adult-*} → 42 岁 M</li>
 *   <li>{@code demo-child-*} → 10 岁 F</li>
 * </ul>
 */
public final class FakePatientFactsAdapter implements PatientFactsPort {

    public static final String ADULT_PREFIX = "demo-adult-";
    public static final String CHILD_PREFIX = "demo-child-";

    @Override
    public PatientFactsResolution resolve(String patientId, Instant asOf) {
        if (patientId == null || patientId.isBlank()) {
            return PatientFactsResolution.unknown(FactsSource.FAKE, "MISSING_PATIENT_ID");
        }
        if (patientId.startsWith(ADULT_PREFIX)) {
            return new PatientFactsResolution(new PatientFacts(42, "M", null), FactsSource.FAKE, false, null);
        }
        if (patientId.startsWith(CHILD_PREFIX)) {
            return new PatientFactsResolution(new PatientFacts(10, "F", null), FactsSource.FAKE, false, null);
        }
        return PatientFactsResolution.unknown(FactsSource.FAKE, "UNKNOWN_PATIENT");
    }
}
