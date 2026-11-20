package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.PdfTextLayerProbe;
import com.moonligh.report.domain.parse.ProbeDecision;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.infrastructure.parser.NativePdfParser;
import com.moonligh.report.infrastructure.parser.NativePdfTextStructurer;

class NativePdfParserTest {

    @Test
    void supportsOnlyUseNativePdf() throws Exception {
        ReportProperties props = new ReportProperties();
        NativePdfParser parser = new NativePdfParser(
                new PdfTextLayerProbe(props), new NativePdfTextStructurer(), props);
        byte[] good = PdfTextLayerProbeTest.asciiPdf(PdfTextLayerProbeTest.asciiLabText());
        ParseContext goodCtx = new ParseContext("r1", ReportType.LAB_PANEL, "lab.pdf", "application/pdf", good);
        assertTrue(parser.supports(goodCtx));
        assertEquals(ProbeDecision.USE_NATIVE, parser.lastProbe(goodCtx).decision());

        byte[] sparse = PdfTextLayerProbeTest.asciiPdf("x");
        ParseContext sparseCtx = new ParseContext("r2", ReportType.LAB_PANEL, "lab.pdf", "application/pdf", sparse);
        assertFalse(parser.supports(sparseCtx));
    }

    @Test
    void parseProducesIndicatorsWithEvidence() throws Exception {
        ReportProperties props = new ReportProperties();
        NativePdfParser parser = new NativePdfParser(
                new PdfTextLayerProbe(props), new NativePdfTextStructurer(), props);
        byte[] good = PdfTextLayerProbeTest.asciiPdf(PdfTextLayerProbeTest.asciiLabText());
        ParseResult result = parser.parse(
                new ParseContext("r1", ReportType.LAB_PANEL, "lab.pdf", "application/pdf", good));
        assertEquals(ReportType.LAB_PANEL, result.reportType());
        assertEquals(1, result.indicators().size());
        assertEquals("K", result.indicators().get(0).rawName());
        assertEquals(0, result.indicators().get(0).evidence().pageIndex());
        assertTrue(result.indicators().get(0).evidence().rawText().contains("K 4.2"));
    }
}
