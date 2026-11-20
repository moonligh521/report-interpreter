package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParserRegistry;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.infrastructure.parser.CheckupBookTextParser;
import com.moonligh.report.infrastructure.parser.DischargeSummaryTextParser;
import com.moonligh.report.infrastructure.parser.EcgReportTextParser;
import com.moonligh.report.infrastructure.parser.EndoscopyReportTextParser;
import com.moonligh.report.infrastructure.parser.LabPanelTextParser;
import com.moonligh.report.infrastructure.parser.NativePdfParser;
import com.moonligh.report.infrastructure.parser.OcrLayoutParser;
import com.moonligh.report.infrastructure.parser.UsReportTextParser;

/** AC-P9 / RP-4：每种类型单路；Native 与 OCR 互斥，无条件多引擎串行路径不存在。 */
@SpringBootTest
@ActiveProfiles("test")
class NoDualEngineTest {

    @Autowired
    private List<ReportParser> parsers;

    @Autowired
    private ParserRegistry registry;

    @Test
    void registeredParsersIncludeTextAndBinaryRoutes() {
        assertEquals(8, parsers.size());
        assertEquals(8, registry.size());
        assertTrue(parsers.stream().anyMatch(p -> p instanceof LabPanelTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof CheckupBookTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof EcgReportTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof UsReportTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof DischargeSummaryTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof EndoscopyReportTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof NativePdfParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof OcrLayoutParser));
    }

    @Test
    void registrySelectsSingleParserPerContext() throws Exception {
        ParseContext text = new ParseContext("r", ReportType.LAB_PANEL, "a.lab.txt", "text/plain", new byte[] {1});
        assertTrue(registry.find(text).isPresent());
        assertEquals(1, parsers.stream().filter(p -> p.supports(text)).count());

        byte[] goodPdf = PdfTextLayerProbeTest.asciiPdf(PdfTextLayerProbeTest.asciiLabText());
        ParseContext pdf = new ParseContext("r", ReportType.LAB_PANEL, "a.pdf", "application/pdf", goodPdf);
        assertEquals(1, parsers.stream().filter(p -> p.supports(pdf)).count());
        assertTrue(registry.find(pdf).get() instanceof NativePdfParser);

        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        ParseContext image = new ParseContext("r", ReportType.LAB_PANEL, "a.png", "image/png", png);
        assertEquals(1, parsers.stream().filter(p -> p.supports(image)).count());
        assertTrue(registry.find(image).get() instanceof OcrLayoutParser);
    }
}
