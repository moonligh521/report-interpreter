package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.parse.DocumentQualityStatus;
import com.moonligh.report.domain.parse.ParseQualityGate;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.QualityVerdict;
import com.moonligh.report.domain.parse.ReportMeta;
import com.moonligh.report.domain.parse.ReportType;

/** Task5 / RP-6：页级状态影响文档级门禁；坏页不静默放行。 */
class ParseQualityGatePageTest {

    @Test
    void anyFailedPagePreventsPassed() {
        ParsedIndicator ind = okIndicator();
        ReportMeta meta = new ReportMeta(null, 2, List.of(1), List.of(), null, List.of());
        QualityVerdict v = ParseQualityGate.evaluate(
                new ParseResult(ReportType.LAB_PANEL, List.of(ind), 0.95, meta));
        assertEquals(DocumentQualityStatus.FAILED, v.status());
        assertFalse(v.passed());
    }

    @Test
    void lowConfidencePageNeedsManualReview() {
        ParsedIndicator ind = okIndicator();
        ReportMeta meta = new ReportMeta(null, 1, List.of(), List.of(), null, List.of(0));
        QualityVerdict v = ParseQualityGate.evaluate(
                new ParseResult(ReportType.LAB_PANEL, List.of(ind), 0.90, meta));
        assertEquals(DocumentQualityStatus.NEED_MANUAL_REVIEW, v.status());
    }

    @Test
    void evidenceBeatsHighOcrScore() {
        FieldConfidence highOcr = new FieldConfidence(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99);
        ParsedIndicator ind = new ParsedIndicator(
                "K", "4.2", "mmol/L", "K", "SERUM", new BigDecimal("4.2"), "mmol/L", "NORMALIZED",
                null, null, highOcr, new SourceEvidence(null, "", null));
        QualityVerdict v = ParseQualityGate.evaluate(
                new ParseResult(ReportType.LAB_PANEL, List.of(ind), 0.99));
        assertEquals(DocumentQualityStatus.FAILED, v.status());
    }

    private static ParsedIndicator okIndicator() {
        FieldConfidence c = new FieldConfidence(1, 1, 1, 1, 1, 1, 1, 1);
        return new ParsedIndicator(
                "K", "4.2", "mmol/L", "K", "SERUM", new BigDecimal("4.2"), "mmol/L", "NORMALIZED",
                null, null, c, new SourceEvidence(0, "K 4.2 mmol/L", null));
    }
}
