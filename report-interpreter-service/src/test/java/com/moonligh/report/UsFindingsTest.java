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
import com.moonligh.report.infrastructure.parser.UsReportTextParser;

/** AC-M3：超声 sections / findings / assessments 可解析并可出现在详情。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsFindingsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void parserExtractsSectionsFindingsAssessments() throws Exception {
        byte[] bytes = ReportTestSupport.golden("us-basic.us.txt").getBytes(StandardCharsets.UTF_8);
        ParseResult result = new UsReportTextParser().parse(
                new ParseContext("r1", ReportType.US_REPORT, "x.us.txt", "text/plain", bytes));
        assertEquals(ReportType.US_REPORT, result.reportType());
        assertEquals(1, result.structure().sections().size());
        assertEquals("sec-liver", result.structure().sections().get(0).id());
        assertEquals(2, result.structure().findings().size());
        assertEquals("sec-liver", result.structure().findings().get(0).sectionId());
        assertEquals(1, result.structure().assessments().size());
    }

    @Test
    void detailReturnsUsStructure() throws Exception {
        String account = ReportTestSupport.uniq("acc-us");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.us.txt", "text/plain",
                ReportTestSupport.golden("us-basic.us.txt").getBytes(StandardCharsets.UTF_8));
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "US_REPORT", file);
        assertEquals(200, created.path("code").asInt(), created.toString());
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals("US_REPORT", detail.path("data").path("reportType").asText());
        assertEquals(1, detail.path("data").path("sections").size());
        assertEquals(2, detail.path("data").path("findings").size());
        assertEquals(1, detail.path("data").path("assessments").size());
        assertEquals(0, detail.path("data").path("subReports").size());
    }
}
