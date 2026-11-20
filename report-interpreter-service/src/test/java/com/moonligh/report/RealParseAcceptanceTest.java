package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.domain.parse.DocumentExtractionPort;
import com.moonligh.report.domain.parse.DocumentQualityStatus;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseQualityGate;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParserRegistry;
import com.moonligh.report.domain.parse.QualityVerdict;
import com.moonligh.report.domain.parse.ReportMeta;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.infrastructure.extraction.FakeDocumentExtractionAdapter;
import com.moonligh.report.infrastructure.parser.NativePdfParser;
import com.moonligh.report.infrastructure.parser.OcrLayoutParser;

/**
 * RP-1～RP-8：真实解析闭环验收。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RealParseAcceptanceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ParserRegistry registry;

    @Autowired
    private DocumentExtractionPort extractionPort;

    @Autowired
    private List<com.moonligh.report.domain.parse.ReportParser> parsers;

    @Test
    void rp1_nativePdfProducesIndicatorsEvidenceAndManifest() throws Exception {
        String account = ReportTestSupport.uniq("acc-rp1");
        byte[] pdf = PdfTextLayerProbeTest.asciiPdf(PdfTextLayerProbeTest.asciiLabText());
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "native-lab.pdf", "application/pdf", pdf);
        JsonNode created = ReportTestSupport.create(mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL", file);
        assertEquals(200, created.path("code").asInt(), created.toString());
        String reportId = created.path("data").path("reportId").asText();
        assertTrue(created.path("data").path("parseVersion").asInt() >= 1);

        JsonNode detail = ReportTestSupport.detail(mvc, account, reportId);
        assertTrue(detail.path("data").path("activeParse").asBoolean(detail.path("data").path("active").asBoolean()));
        assertFalse(detail.path("data").path("indicators").isEmpty());
        assertNotNull(detail.path("data").path("indicators").get(0).path("evidence").path("rawText").asText(null));

        Map<String, Object> manifest = jdbc.queryForMap(
                "SELECT document_hash, parser_type, quality_status, total_page_count, successful_page_count "
                        + "FROM report_parse_manifest WHERE report_id = ? AND parse_version > 0",
                reportId);
        assertEquals(NativePdfParser.PARSER_TYPE, manifest.get("parser_type"));
        assertEquals(DocumentQualityStatus.PASSED.name(), String.valueOf(manifest.get("quality_status")));
        assertNotNull(manifest.get("document_hash"));
    }

    @Test
    void rp2_fakeImageClosedLoopAndDefaultPortIsFake() throws Exception {
        assertTrue(extractionPort instanceof FakeDocumentExtractionAdapter);
        String account = ReportTestSupport.uniq("acc-rp2");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5};
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "scan.png", "image/png", png);
        JsonNode created = ReportTestSupport.create(mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL", file);
        assertEquals(200, created.path("code").asInt(), created.toString());
        String reportId = created.path("data").path("reportId").asText();
        JsonNode detail = ReportTestSupport.detail(mvc, account, reportId);
        assertTrue(detail.path("data").path("activeParse").asBoolean(detail.path("data").path("active").asBoolean()));
        Map<String, Object> manifest = jdbc.queryForMap(
                "SELECT extraction_engine, extraction_schema_version FROM report_parse_manifest "
                        + "WHERE report_id = ? AND parse_version > 0",
                reportId);
        assertEquals(FakeDocumentExtractionAdapter.ENGINE, manifest.get("extraction_engine"));
        assertEquals("1.0", String.valueOf(manifest.get("extraction_schema_version")));
    }

    @Test
    void rp3_corruptPdfReturns4102NotFakeActive() throws Exception {
        String account = ReportTestSupport.uniq("acc-rp3");
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "bad.pdf", "application/pdf", "%PDF-1.4".getBytes());
        JsonNode created = ReportTestSupport.create(mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL", file);
        assertEquals(ErrorCode.REPORT_PARSE_FAILED.getCode(), created.path("code").asInt());
    }

    @Test
    void rp4_singleRouteAndImagingFilm4101() throws Exception {
        byte[] goodPdf = PdfTextLayerProbeTest.asciiPdf(PdfTextLayerProbeTest.asciiLabText());
        ParseContext pdf = new ParseContext("r", ReportType.LAB_PANEL, "a.pdf", "application/pdf", goodPdf);
        assertEquals(1, parsers.stream().filter(p -> p.supports(pdf)).count());
        assertTrue(registry.find(pdf).orElseThrow() instanceof NativePdfParser);

        JsonNode rejected = ReportTestSupport.create(
                mvc, ReportTestSupport.uniq("acc-rp4"), ReportTestSupport.uniq("k"), "IMAGING_FILM",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        assertEquals(4101, rejected.path("code").asInt());
    }

    @Test
    void rp5_confidenceLayersPresentOnParsedIndicators() throws Exception {
        String account = ReportTestSupport.uniq("acc-rp5");
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        assertEquals(200, created.path("code").asInt());
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        JsonNode conf = detail.path("data").path("indicators").get(0).path("confidence");
        assertTrue(conf.has("ocr"));
        assertTrue(conf.has("structure"));
        assertTrue(conf.has("extraction"));
        assertTrue(conf.has("normalization"));
        assertTrue(conf.has("name"));
        assertTrue(conf.has("value"));
        assertTrue(conf.has("unit"));
        assertTrue(conf.has("refRange"));
    }

    @Test
    void rp6_failedPageBlocksDocumentPassed() {
        var ind = new com.moonligh.report.domain.parse.ParsedIndicator(
                "K", "4.2", "mmol/L", "K", "SERUM", new java.math.BigDecimal("4.2"), "mmol/L", "NORMALIZED",
                null, null,
                new com.moonligh.report.api.res.FieldConfidence(1, 1, 1, 1, 1, 1, 1, 1),
                new com.moonligh.report.api.res.SourceEvidence(0, "K 4.2", null));
        QualityVerdict v = ParseQualityGate.evaluate(new ParseResult(
                ReportType.LAB_PANEL, List.of(ind), 0.99,
                new ReportMeta(null, 2, List.of(), List.of(), null, List.of(), List.of(1))));
        assertEquals(DocumentQualityStatus.FAILED, v.status());
    }

    @Test
    void rp7_manifestContainsHashEngineSchemaPageStats() throws Exception {
        String account = ReportTestSupport.uniq("acc-rp7");
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7};
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "m.png", "image/png", png);
        JsonNode created = ReportTestSupport.create(mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL", file);
        String reportId = created.path("data").path("reportId").asText();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT document_hash, extraction_engine, extraction_schema_version, "
                        + "total_page_count, successful_page_count, failed_page_count, quality_status, parser_type "
                        + "FROM report_parse_manifest WHERE report_id = ? AND parse_version > 0",
                reportId);
        assertFalse(String.valueOf(row.get("document_hash")).isBlank());
        assertEquals(FakeDocumentExtractionAdapter.ENGINE, row.get("extraction_engine"));
        assertEquals("1.0", String.valueOf(row.get("extraction_schema_version")));
        assertEquals(OcrLayoutParser.PARSER_TYPE, row.get("parser_type"));
        assertNotNull(row.get("total_page_count"));
        assertNotNull(row.get("successful_page_count"));
        assertNotNull(row.get("failed_page_count"));
        assertEquals(DocumentQualityStatus.PASSED.name(), String.valueOf(row.get("quality_status")));
    }

    @Test
    void rp8_textFixtureRegressionStillWorks() throws Exception {
        String account = ReportTestSupport.uniq("acc-rp8");
        JsonNode created = ReportTestSupport.create(
                mvc, account, ReportTestSupport.uniq("k"), "LAB_PANEL",
                ReportTestSupport.labFile(ReportTestSupport.golden("lab-panel-basic.lab.txt")));
        assertEquals(200, created.path("code").asInt());
        assertTrue(created.path("data").path("parseVersion").asInt() >= 1);
        JsonNode detail = ReportTestSupport.detail(mvc, account, created.path("data").path("reportId").asText());
        assertFalse(detail.path("data").path("indicators").isEmpty());
    }
}
