package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.infrastructure.parser.DischargeSummaryTextParser;

/** 出院小结金标解析。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DischargeParserGoldenTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void parserExtractsSectionsFindingsAssessments() throws Exception {
        byte[] bytes = ReportTestSupport.golden("discharge-basic.discharge.txt").getBytes(StandardCharsets.UTF_8);
        ParseResult result = new DischargeSummaryTextParser().parse(
                new ParseContext("r1", ReportType.DISCHARGE_SUMMARY, "x.discharge.txt", "text/plain", bytes));
        assertEquals(ReportType.DISCHARGE_SUMMARY, result.reportType());
        assertEquals(2, result.structure().sections().size());
        assertEquals(2, result.structure().findings().size());
        assertEquals(1, result.structure().assessments().size());
    }

    @Test
    void detailReturnsDischargeStructure() throws Exception {
        String account = ReportTestSupport.uniq("acc-dis");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.discharge.txt", "text/plain",
                ReportTestSupport.golden("discharge-basic.discharge.txt").getBytes(StandardCharsets.UTF_8));
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "DISCHARGE_SUMMARY", file);
        assertEquals(200, created.path("code").asInt(), created.toString());
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals("DISCHARGE_SUMMARY", detail.path("data").path("reportType").asText());
        assertEquals(2, detail.path("data").path("sections").size());
        assertEquals(2, detail.path("data").path("findings").size());
        assertEquals(1, detail.path("data").path("assessments").size());
    }
}
