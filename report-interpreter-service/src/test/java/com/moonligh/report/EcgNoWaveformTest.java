package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.infrastructure.parser.EcgReportTextParser;

/** AC-M2 / M-R2：心电路径不依赖波形文件；WAVEFORM_REQUIRED 且无所见 → 解析失败。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EcgNoWaveformTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void parserWorksWithoutWaveformFile() throws Exception {
        byte[] bytes = ReportTestSupport.golden("ecg-basic.ecg.txt").getBytes(StandardCharsets.UTF_8);
        ParseResult result = new EcgReportTextParser().parse(
                new ParseContext("r1", ReportType.ECG_REPORT, "x.ecg.txt", "text/plain", bytes));
        assertEquals(ReportType.ECG_REPORT, result.reportType());
        assertTrue(result.indicators().isEmpty());
        assertEquals(2, result.structure().findings().size());
        assertEquals(1, result.structure().assessments().size());
    }

    @Test
    void waveformRequiredWithoutFindingsIsRejected() {
        byte[] bytes;
        try {
            bytes = ReportTestSupport.golden("ecg-waveform-required.ecg.txt").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        BusinessException ex = assertThrows(BusinessException.class, () -> new EcgReportTextParser().parse(
                new ParseContext("r1", ReportType.ECG_REPORT, "x.ecg.txt", "text/plain", bytes)));
        assertEquals(ErrorCode.REPORT_PARSE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void endToEndAcceptsEcgWithoutWaveform() throws Exception {
        String account = ReportTestSupport.uniq("acc-ecg");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.ecg.txt", "text/plain",
                ReportTestSupport.golden("ecg-basic.ecg.txt").getBytes(StandardCharsets.UTF_8));
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "ECG_REPORT", file);
        assertEquals(200, created.path("code").asInt(), created.toString());
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertEquals("ECG_REPORT", detail.path("data").path("reportType").asText());
        assertEquals(2, detail.path("data").path("findings").size());
        assertEquals(1, detail.path("data").path("assessments").size());
        assertTrue(detail.path("data").path("indicators").isEmpty()
                || detail.path("data").path("indicators").isArray());
    }
}
