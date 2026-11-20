package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.parse.PdfTextLayerProbe;
import com.moonligh.report.domain.parse.ProbeDecision;
import com.moonligh.report.domain.parse.ProbeReport;

/** Task2 金标：合格文本 PDF → USE_NATIVE；乱码/过稀 → USE_OCR 或 NEED_REVIEW。 */
class PdfTextLayerProbeTest {

    @Test
    void goodTextLayerPdfRoutesNativeWithMetrics() throws Exception {
        byte[] pdf = asciiPdf(asciiLabText());
        ProbeReport report = newProbe().probe(pdf);
        assertEquals(ProbeDecision.USE_NATIVE, report.decision());
        assertTrue(report.nonEmptyPageRatioOk());
        assertTrue(report.extractableCharRatioOk());
        assertTrue(report.textDensityOk());
        assertTrue(report.totalCharsOk());
        assertTrue(report.totalChars() > 30);
        assertTrue(report.reasons().stream().anyMatch(r -> r.contains("Native")));
    }

    @Test
    void sparseTextRoutesNeedReviewOrOcr() {
        ProbeReport report = newProbe().evaluate(List.of("x"));
        assertTrue(
                report.decision() == ProbeDecision.USE_OCR || report.decision() == ProbeDecision.NEED_REVIEW,
                report.decision() + " " + report.reasons());
        assertTrue(!report.totalCharsOk() || !report.textDensityOk());
    }

    @Test
    void garbTextRoutesToOcr() {
        String garbled = "\uFFFD".repeat(80);
        ProbeReport report = newProbe().evaluate(List.of(garbled));
        assertEquals(ProbeDecision.USE_OCR, report.decision());
        assertTrue(!report.extractableCharRatioOk());
        assertTrue(report.reasons().stream().anyMatch(r -> r.contains("乱码") || r.contains("可提取")));
    }

    static PdfTextLayerProbe newProbe() {
        return new PdfTextLayerProbe(new ReportProperties());
    }

    static String asciiLabText() {
        return """
                REPORT_TYPE=LAB_PANEL
                PATIENT_NAME=Zhang
                PAGE_COUNT=1
                K|4.2|mmol/L|3.5|5.5|K 4.2 mmol/L|0|SERUM
                """;
    }

    static byte[] asciiPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(40, 750);
                for (String line : text.split("\\r?\\n")) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -14);
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
