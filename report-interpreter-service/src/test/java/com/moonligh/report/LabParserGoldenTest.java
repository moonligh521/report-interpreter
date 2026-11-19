package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.infrastructure.parser.LabPanelTextParser;

/** AC-P1：金标 Exact Match（ALT=86）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabParserGoldenTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void parserExactMatchAlt86() throws Exception {
        byte[] bytes = ReportTestSupport.golden("lab-panel-basic.lab.txt").getBytes(StandardCharsets.UTF_8);
        ParseResult result = new LabPanelTextParser().parse(
                new ParseContext("r1", ReportType.LAB_PANEL, "x.lab.txt", "text/plain", bytes));

        assertEquals(2, result.indicators().size());
        ParsedIndicator alt = result.indicators().get(0);
        assertEquals("ALT", alt.rawName());
        assertEquals("86", alt.rawValue());
        assertEquals("U/L", alt.rawUnit());
        assertEquals(0, new BigDecimal("86").compareTo(alt.normalizedValue()));
        assertEquals("U/L", alt.normalizedUnit());
        assertEquals(0, new BigDecimal("7").compareTo(alt.refLow()));
        assertEquals(0, new BigDecimal("40").compareTo(alt.refHigh()));
        ParsedIndicator k = result.indicators().get(1);
        assertEquals("5.8", k.rawValue());
        assertEquals(0, new BigDecimal("5.8").compareTo(k.normalizedValue()));
    }

    @Test
    void endToEndDetailReturnsAlt86() throws Exception {
        String account = ReportTestSupport.uniq("acc-golden");
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        assertEquals(200, created.path("code").asInt());
        assertEquals("PARSED", created.path("data").path("status").asText());
        assertEquals(1, created.path("data").path("parseVersion").asInt());

        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals("LAB_PANEL", detail.path("data").path("reportType").asText());
        assertTrue(detail.path("data").path("activeParse").asBoolean());
        JsonNode alt = ReportTestSupport.indicator(detail, "ALT");
        assertEquals("86", alt.path("rawValue").asText());
        assertEquals(0, new BigDecimal("86").compareTo(alt.path("normalizedValue").decimalValue()));
        assertEquals("U/L", alt.path("normalizedUnit").asText());
    }
}