package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.rule.ApplicabilityResult;
import com.moonligh.report.domain.rule.ApplicabilityStatus;
import com.moonligh.report.domain.rule.ClinicalCatalog;
import com.moonligh.report.domain.rule.CriticalStatus;
import com.moonligh.report.domain.rule.CriticalValueEngine;
import com.moonligh.report.domain.rule.CriticalValueResult;
import com.moonligh.report.domain.rule.PatientFacts;
import com.moonligh.report.domain.rule.RuleCondition;
import com.moonligh.report.domain.rule.RuleContext;
import com.moonligh.report.domain.rule.RuleContextResolver;
import com.moonligh.report.domain.rule.SafetyGate;
import com.moonligh.report.infrastructure.catalog.FakeClinicalCatalogAdapter;

/** CF-2 / CF-4：UNKNOWN 不得假 NOT_CRITICAL；缺目录版本 → 4106。 */
class ClinicalFactsUnknownSafetyTest {

    @Test
    void missingAgeWithAgeGatedCatalogYieldsUnknownNotNotCritical() {
        ClinicalCatalog catalog =
                new FakeClinicalCatalogAdapter().load(FakeClinicalCatalogAdapter.VERSION_V2);
        CriticalValueEngine engine = new CriticalValueEngine(catalog.criticalRuleTable());
        SafetyGate gate = new SafetyGate();
        RuleContext ctx = new RuleContextResolver().resolve(
                "i1",
                k("4.2"),
                PatientFacts.UNKNOWN);

        ApplicabilityResult applicability = gate.applicability(ctx, catalog.criticalRuleTable().rulesFor("K"));
        assertEquals(ApplicabilityStatus.UNKNOWN, applicability.status());

        CriticalValueResult critical = engine.evaluate(ctx, applicability);
        assertEquals(CriticalStatus.UNKNOWN, critical.status());
        assertNotEquals(CriticalStatus.NOT_CRITICAL, critical.status());
    }

    @Test
    void unknownCatalogVersionMapsTo4106() {
        FakeClinicalCatalogAdapter adapter = new FakeClinicalCatalogAdapter();
        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.load("missing-v9"));
        assertEquals(ErrorCode.REPORT_RULE_PENDING_REVIEW.getCode(), ex.getCode());
    }

    @Test
    void versionsAreSwitchable() {
        FakeClinicalCatalogAdapter adapter = new FakeClinicalCatalogAdapter();
        assertEquals(FakeClinicalCatalogAdapter.VERSION_V1, adapter.load(FakeClinicalCatalogAdapter.VERSION_V1).version());
        assertEquals(FakeClinicalCatalogAdapter.VERSION_V2, adapter.load(FakeClinicalCatalogAdapter.VERSION_V2).version());
        assertTrue(adapter.load(FakeClinicalCatalogAdapter.VERSION_V2)
                .criticalRules().get(0).required().contains(RuleCondition.AGE));
    }

    private static ParsedIndicator k(String value) {
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
                new FieldConfidence(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99),
                null);
    }
}
