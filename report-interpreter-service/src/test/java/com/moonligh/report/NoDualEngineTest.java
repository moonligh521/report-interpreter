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
import com.moonligh.report.infrastructure.parser.LabPanelTextParser;

/** AC-P9：无条件多引擎串行路径不存在，LAB_PANEL 仅一个解析器。 */
@SpringBootTest
@ActiveProfiles("test")
class NoDualEngineTest {

    @Autowired
    private List<ReportParser> parsers;

    @Autowired
    private ParserRegistry registry;

    @Test
    void exactlyOneParserRegistered() {
        assertEquals(1, parsers.size());
        assertEquals(1, registry.size());
        assertTrue(parsers.get(0) instanceof LabPanelTextParser);
    }

    @Test
    void registrySelectsSingleParserAndNoneForBinary() {
        ParseContext text = new ParseContext("r", ReportType.LAB_PANEL, "a.lab.txt", "text/plain", new byte[] {1});
        ParseContext pdf = new ParseContext("r", ReportType.LAB_PANEL, "a.pdf", "application/pdf", new byte[] {1});
        assertTrue(registry.find(text).isPresent());
        assertTrue(registry.find(pdf).isEmpty());
    }
}