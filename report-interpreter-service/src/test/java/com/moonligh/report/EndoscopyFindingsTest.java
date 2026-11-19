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
import com.moonligh.report.infrastructure.parser.EndoscopyReportTextParser;

/** 内镜所见解析。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndoscopyFindingsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void parserExtractsFindings() throws Exception {
        byte[] bytes = ReportTestSupport.golden("endoscopy-basic.endo.txt").getBytes(StandardCharsets.UTF_8);
        ParseResult result = new EndoscopyReportTextParser().parse(
                new ParseContext("r1", ReportType.ENDOSCOPY_REPORT, "x.endo.txt", "text/plain", bytes));
        assertEquals(ReportType.ENDOSCOPY_REPORT, result.reportType());
        assertEquals(1, result.structure().sections().size());
        assertEquals(2, result.structure().findings().size());
        assertEquals("sec-stomach", result.structure().findings().get(0).sectionId());
        assertEquals(1, result.structure().assessments().size());
    }

    @Test
    void detailReturnsEndoscopyStructure() throws Exception {
        String account = ReportTestSupport.uniq("acc-endo");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.endo.txt", "text/plain",
                ReportTestSupport.golden("endoscopy-basic.endo.txt").getBytes(StandardCharsets.UTF_8));
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "ENDOSCOPY_REPORT", file);
        assertEquals(200, created.path("code").asInt(), created.toString());
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals("ENDOSCOPY_REPORT", detail.path("data").path("reportType").asText());
        assertEquals(2, detail.path("data").path("findings").size());
        assertEquals(1, detail.path("data").path("assessments").size());
    }
}
