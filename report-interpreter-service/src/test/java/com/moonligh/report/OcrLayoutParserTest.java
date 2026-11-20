package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.PdfTextLayerProbe;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.infrastructure.extraction.FakeDocumentExtractionAdapter;
import com.moonligh.report.infrastructure.parser.NativePdfParser;
import com.moonligh.report.infrastructure.parser.NativePdfTextStructurer;
import com.moonligh.report.infrastructure.parser.OcrExtractedStructurer;
import com.moonligh.report.infrastructure.parser.OcrLayoutParser;

class OcrLayoutParserTest {

    @Test
    void imageGoesThroughOcrAndStructuresIndicators() {
        OcrLayoutParser ocr = newParser();
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        ParseContext ctx = new ParseContext("r1", ReportType.LAB_PANEL, "a.png", "image/png", png);
        assertTrue(ocr.supports(ctx));
        ParseResult result = ocr.parse(ctx);
        assertEquals(1, result.indicators().size());
        assertEquals("K", result.indicators().get(0).rawName());
        assertEquals(0, result.indicators().get(0).evidence().pageIndex());
    }

    @Test
    void nativeAndOcrAreMutexOnSamePdf() throws Exception {
        ReportProperties props = new ReportProperties();
        PdfTextLayerProbe probe = new PdfTextLayerProbe(props);
        NativePdfParser nativeParser = new NativePdfParser(probe, new NativePdfTextStructurer(), props);
        OcrLayoutParser ocr = new OcrLayoutParser(new FakeDocumentExtractionAdapter(), new OcrExtractedStructurer(), probe);

        byte[] good = PdfTextLayerProbeTest.asciiPdf(PdfTextLayerProbeTest.asciiLabText());
        ParseContext goodCtx = new ParseContext("r", ReportType.LAB_PANEL, "a.pdf", "application/pdf", good);
        assertTrue(nativeParser.supports(goodCtx));
        assertFalse(ocr.supports(goodCtx));

        byte[] sparse = PdfTextLayerProbeTest.asciiPdf("x");
        ParseContext sparseCtx = new ParseContext("r", ReportType.LAB_PANEL, "b.pdf", "application/pdf", sparse);
        assertFalse(nativeParser.supports(sparseCtx));
        assertTrue(ocr.supports(sparseCtx));
    }

    private static OcrLayoutParser newParser() {
        return new OcrLayoutParser(
                new FakeDocumentExtractionAdapter(),
                new OcrExtractedStructurer(),
                new PdfTextLayerProbe(new ReportProperties()));
    }
}
