package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.report.api.res.CriticalCoverageEvidence;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ReportMeta;
import com.moonligh.report.domain.rule.CoverageStatus;
import com.moonligh.report.domain.rule.CriticalCoverageChecker;
import com.moonligh.report.domain.rule.CriticalCoverageResult;

/** AC-R4：COMPLETE 必须带齐 evidence，否则非法；覆盖状态由证据推导。 */
class CoverageEvidenceRequiredTest {

    private static ParsedIndicator ind(String name, String code, String value, int page) {
        return new ParsedIndicator(
                name, "1", "mmol/L", code, code == null ? null : "SERUM",
                value == null ? null : new BigDecimal(value), value == null ? null : "mmol/L",
                value == null ? "NAME_UNMAPPED" : "NORMALIZED", null, null,
                new FieldConfidence(1, 1, 1, 1, 1, 1, 1, 1), new SourceEvidence(page, name + " raw", null));
    }

    @Test
    void completeWithoutEvidenceIsIllegal() {
        assertThrows(IllegalArgumentException.class, () -> new CriticalCoverageResult(CoverageStatus.COMPLETE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CriticalCoverageResult(CoverageStatus.COMPLETE, CriticalCoverageEvidence.EMPTY));
    }

    @Test
    void completeWithFailedPageOrUnmappedFlagIsIllegal() {
        assertThrows(IllegalArgumentException.class, () -> new CriticalCoverageResult(
                CoverageStatus.COMPLETE,
                new CriticalCoverageEvidence(List.of(0), List.of(1), List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> new CriticalCoverageResult(
                CoverageStatus.COMPLETE,
                new CriticalCoverageEvidence(List.of(0), List.of(), List.of("XYZ"), List.of())));
        assertThrows(IllegalArgumentException.class, () -> new CriticalCoverageResult(
                CoverageStatus.COMPLETE,
                new CriticalCoverageEvidence(List.of(0), List.of(), List.of(), List.of("region"))));
    }

    @Test
    void completeWithFullEvidenceIsLegal() {
        CriticalCoverageResult r = new CriticalCoverageResult(
                CoverageStatus.COMPLETE, new CriticalCoverageEvidence(List.of(0, 1), List.of(), List.of(), List.of()));
        assertEquals(List.of(0, 1), r.evidence().pagesProcessed());
    }

    @Test
    void checkerAlwaysAttachesEvidence() {
        CriticalCoverageChecker checker = new CriticalCoverageChecker();
        CriticalCoverageResult complete = checker.check(
                List.of(ind("K", "K", "4.2", 0), ind("NA", "NA", "140", 1)), new ReportMeta(null, 2, List.of(), List.of()));
        assertEquals(CoverageStatus.COMPLETE, complete.status());
        assertEquals(List.of(0, 1), complete.evidence().pagesProcessed());

        CriticalCoverageResult unknown = checker.check(
                List.of(ind("K", "K", "4.2", 0)), new ReportMeta(null, 3, List.of(), List.of()));
        assertEquals(CoverageStatus.UNKNOWN, unknown.status());
        assertEquals(List.of(0), unknown.evidence().pagesProcessed());

        CriticalCoverageResult unmapped = checker.check(
                List.of(ind("K", "K", "4.2", 0), ind("XYZ", null, null, 0)), ReportMeta.EMPTY);
        assertEquals(CoverageStatus.PARTIAL, unmapped.status());
        assertEquals(List.of("XYZ"), unmapped.evidence().unmappedFlags());

        CriticalCoverageResult none = checker.check(List.of(), ReportMeta.EMPTY);
        assertEquals(CoverageStatus.UNKNOWN, none.status());
    }
}