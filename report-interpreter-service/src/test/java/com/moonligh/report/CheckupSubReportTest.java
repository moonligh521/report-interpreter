package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

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
import com.moonligh.report.infrastructure.parser.CheckupBookTextParser;

/** AC-M1 / M-R1：体检册子报告归属可测，不同 SUB 不得强绑同一 reportTime。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckupSubReportTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void parserKeepsDistinctReportTimesPerSubReport() throws Exception {
        byte[] bytes = ReportTestSupport.golden("checkup-basic.checkup.txt").getBytes(StandardCharsets.UTF_8);
        ParseResult result = new CheckupBookTextParser().parse(
                new ParseContext("r1", ReportType.CHECKUP_BOOK, "x.checkup.txt", "text/plain", bytes));

        assertEquals(ReportType.CHECKUP_BOOK, result.reportType());
        assertEquals(2, result.structure().subReports().size());
        assertEquals(2, result.indicators().size());
        Set<String> times = new HashSet<>();
        result.structure().subReports().forEach(s -> times.add(s.reportTime().toString()));
        assertEquals(2, times.size());
        assertNotEquals(
                result.indicators().get(0).reportTime(),
                result.indicators().get(1).reportTime());
        assertEquals("sub-blood", result.indicators().get(0).subReportId());
        assertEquals("sub-urine", result.indicators().get(1).subReportId());
    }

    @Test
    void detailExposesSubReportsAndIndicatorOwnership() throws Exception {
        String account = ReportTestSupport.uniq("acc-checkup");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.checkup.txt", "text/plain",
                ReportTestSupport.golden("checkup-basic.checkup.txt").getBytes(StandardCharsets.UTF_8));
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "CHECKUP_BOOK", file);
        assertEquals(200, created.path("code").asInt(), created.toString());

        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals("CHECKUP_BOOK", detail.path("data").path("reportType").asText());
        assertEquals(2, detail.path("data").path("subReports").size());
        JsonNode alt = ReportTestSupport.indicator(detail, "ALT");
        JsonNode glu = ReportTestSupport.indicator(detail, "GLU");
        assertEquals("sub-blood", alt.path("subReportId").asText());
        assertEquals("sub-urine", glu.path("subReportId").asText());
        assertTrue(alt.hasNonNull("reportTime"));
        assertTrue(glu.hasNonNull("reportTime"));
        assertNotEquals(alt.path("reportTime").asText(), glu.path("reportTime").asText());
    }
}
