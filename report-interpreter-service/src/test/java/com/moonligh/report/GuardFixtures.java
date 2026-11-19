package com.moonligh.report;

import java.math.BigDecimal;
import java.util.List;

import com.moonligh.report.domain.interpret.ClaimType;
import com.moonligh.report.domain.interpret.ClaimedStatus;
import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.EvidenceItem;
import com.moonligh.report.domain.interpret.IndicatorFact;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.rule.ReferenceStatus;

/** Validator / Guard 单测共用的事实、依据与草稿块构造。 */
final class GuardFixtures {

    static final IndicatorFact K_CRITICAL = new IndicatorFact(
            "ind-k", "K", "K", new BigDecimal("6.800000"), "mmol/L",
            new BigDecimal("3.5"), new BigDecimal("5.3"), ReferenceStatus.HIGH, true);

    static final IndicatorFact K_NORMAL = new IndicatorFact(
            "ind-k2", "K", "K", new BigDecimal("4.2"), "mmol/L",
            new BigDecimal("3.5"), new BigDecimal("5.3"), ReferenceStatus.NORMAL, false);

    static final IndicatorFact K_HIGH = new IndicatorFact(
            "ind-k3", "K", "K", new BigDecimal("5.8"), "mmol/L",
            new BigDecimal("3.5"), new BigDecimal("5.3"), ReferenceStatus.HIGH, false);

    static final IndicatorFact ALT_HIGH = new IndicatorFact(
            "ind-alt", "ALT", "ALT", new BigDecimal("85"), "U/L",
            new BigDecimal("7"), new BigDecimal("40"), ReferenceStatus.HIGH, false);

    static final IndicatorFact ALT_NORMAL = new IndicatorFact(
            "ind-alt2", "ALT", "ALT", new BigDecimal("30"), "U/L",
            new BigDecimal("7"), new BigDecimal("40"), ReferenceStatus.NORMAL, false);

    static final EvidenceItem ALT_EVIDENCE = new EvidenceItem(
            "evd-chunk-alt-1", "doc-alt", "转氨酶阅读要点", "转氨酶高于参考范围时，请由医生结合复查评估。",
            "https://kb.example.invalid/alt", "ALT");

    static final EvidenceItem K_EVIDENCE = new EvidenceItem(
            "evd-chunk-k-1", "doc-k-guide", "血钾结果阅读要点", "血钾高于参考范围时，应结合复查结果由医生评估。",
            "https://kb.example.invalid/k", "K");

    private GuardFixtures() {
    }

    static InterpretationContext ctx(boolean safeNormal, List<IndicatorFact> facts, EvidenceItem... evidence) {
        return new InterpretationContext(facts, List.of(evidence), safeNormal);
    }

    static DraftBlock fact(IndicatorFact f, ClaimedStatus status, String text) {
        return new DraftBlock("b", ClaimType.FACT, f.canonicalCode(), status, f.value(), f.unit(), text, List.of());
    }

    static DraftBlock factWith(
            IndicatorFact f, ClaimedStatus status, BigDecimal claimedValue, String claimedUnit, String text) {
        return new DraftBlock("b", ClaimType.FACT, f.canonicalCode(), status, claimedValue, claimedUnit, text, List.of());
    }

    static DraftBlock guideline(IndicatorFact f, String text, String... evidenceIds) {
        return new DraftBlock(
                "g", ClaimType.GUIDELINE, f.canonicalCode(), ClaimedStatus.NONE, null, null, text, List.of(evidenceIds));
    }

    static DraftBlock plain(String text) {
        return new DraftBlock("p", ClaimType.FACT, null, ClaimedStatus.NONE, null, null, text, List.of());
    }
}
