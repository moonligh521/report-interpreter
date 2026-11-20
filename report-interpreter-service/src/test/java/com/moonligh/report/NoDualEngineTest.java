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
import com.moonligh.report.infrastructure.parser.UsReportTextParser;

/** AC-P9：每种类型仅一个解析器，无条件多引擎串行路径不存在。 */
@SpringBootTest
@ActiveProfiles("test")
class NoDualEngineTest {

    @Autowired
    private List<ReportParser> parsers;

    @Autowired
    private ParserRegistry registry;

    @Test
    void oneParserPerSupportedType() {
        assertEquals(6, parsers.size());
        assertEquals(6, registry.size());
        assertTrue(parsers.stream().anyMatch(p -> p instanceof LabPanelTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof CheckupBookTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof EcgReportTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof UsReportTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof DischargeSummaryTextParser));
        assertTrue(parsers.stream().anyMatch(p -> p instanceof EndoscopyReportTextParser));
    }

    @Test
    void registrySelectsSingleParserAndNoneForBinary() {
        ParseContext text = new ParseContext("r", ReportType.LAB_PANEL, "a.lab.txt", "text/plain", new byte[] {1});
        ParseContext pdf = new ParseContext("r", ReportType.LAB_PANEL, "a.pdf", "application/pdf", new byte[] {1});
        assertTrue(registry.find(text).isPresent());
        assertTrue(registry.find(pdf).isEmpty());
        assertEquals(1, parsers.stream().filter(p -> p.supports(text)).count());
    }
}
